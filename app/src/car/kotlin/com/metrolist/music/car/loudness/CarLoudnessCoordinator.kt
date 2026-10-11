package com.metrolist.music.car.loudness

import android.content.Context
import android.media.MediaCodec
import android.os.Process
import com.metrolist.music.car.library.CarLibraryIndex
import com.metrolist.music.car.library.CarLibrarySession
import com.metrolist.music.car.library.CarLibraryTrackAvailability
import com.metrolist.music.car.library.CarLibraryUnavailableReason
import com.metrolist.music.playback.loudness.CarLoudnessState
import com.metrolist.music.playback.loudness.CarLoudnessStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

data class CarLoudnessProgress(
    val total: Int = 0,
    val analyzed: Int = 0,
    val failed: Int = 0,
    val pending: Int = 0,
    val currentSongId: String? = null,
    val running: Boolean = false,
    val pausedReason: String? = null,
)

class CarLoudnessCoordinator(
    context: Context,
    private val librarySession: CarLibrarySession,
) : AutoCloseable {
    private val applicationContext = context.applicationContext
    private val store = CarLoudnessStore(applicationContext)
    private val analyzer = CarLoudnessAnalyzer(applicationContext)
    private val executor =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "MetroList-Car-Loudness").apply {
                priority = Thread.MIN_PRIORITY
            }
        }
    private val dispatcher = executor.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val lock = Any()

    private var activeIndex: CarLibraryIndex? = null
    private var activeLibraryIdentity: String? = null
    private val pendingIds = linkedSetOf<String>()
    private var analyzedCount = 0
    private var failedCount = 0
    private var priorityIds: List<String> = emptyList()

    private val _progress = MutableStateFlow(CarLoudnessProgress())
    val progress: StateFlow<CarLoudnessProgress> = _progress.asStateFlow()

    init {
        scope.launch {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            for (ignored in wake) {
                if (!isActive) break
                processPending()
            }
        }
    }

    fun updateLibrary(index: CarLibraryIndex?) {
        synchronized(lock) {
            val identity = index?.let { "${it.libraryId}:${it.revision}" }
            if (identity == activeLibraryIdentity) return

            activeIndex = index
            activeLibraryIdentity = identity
            pendingIds.clear()
            analyzedCount = 0
            failedCount = 0
            index?.songs?.forEach { song ->
                when (store.record(song.id, song.sha256)?.state) {
                    CarLoudnessState.DONE -> analyzedCount++
                    CarLoudnessState.FAILED -> failedCount++
                    null -> pendingIds += song.id
                }
            }
            publishProgressLocked(running = false, currentSongId = null, pausedReason = null)
        }
        wake.trySend(Unit)
    }

    fun updatePriority(songIds: List<String>) {
        synchronized(lock) {
            priorityIds = songIds.distinct()
        }
        wake.trySend(Unit)
    }

    fun clearMeasurements() {
        synchronized(lock) {
            store.clear()
            activeLibraryIdentity = null
        }
        updateLibrary(activeIndex)
    }

    private suspend fun processPending() {
        while (scope.isActive) {
            val work =
                synchronized(lock) {
                    val index = activeIndex ?: return
                    val nextId =
                        priorityIds.firstOrNull { it in pendingIds }
                            ?: pendingIds.firstOrNull()
                            ?: run {
                                publishProgressLocked(
                                    running = false,
                                    currentSongId = null,
                                    pausedReason = null,
                                )
                                return
                            }
                    index.song(nextId)?.let { nextId to it.sha256 }
                } ?: return
            val (songId, fingerprint) = work
            synchronized(lock) {
                publishProgressLocked(
                    running = true,
                    currentSongId = songId,
                    pausedReason = null,
                )
            }

            when (val availability = librarySession.repository.trackAvailability(songId)) {
                is CarLibraryTrackAvailability.Available -> {
                    try {
                        val measurement =
                            analyzer.analyze(
                                songId = songId,
                                fingerprint = fingerprint,
                                locator = availability.locator,
                            )
                        store.putMeasurement(measurement)
                        synchronized(lock) {
                            if (pendingIds.remove(songId)) analyzedCount++
                            publishProgressLocked(
                                running = true,
                                currentSongId = null,
                                pausedReason = null,
                            )
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: MediaCodec.CodecException) {
                        if (error.isTransient || error.isRecoverable) {
                            synchronized(lock) {
                                publishProgressLocked(
                                    running = false,
                                    currentSongId = null,
                                    pausedReason = "Audio decoder is busy; analysis will retry automatically",
                                )
                            }
                            delay(TRANSIENT_CODEC_RETRY_MS)
                        } else {
                            markFailed(
                                songId,
                                fingerprint,
                                error.diagnosticInfo,
                            )
                        }
                    } catch (error: Exception) {
                        markFailed(songId, fingerprint, error.message ?: error::class.java.simpleName)
                    }
                }

                is CarLibraryTrackAvailability.Unavailable -> {
                    when (availability.reason) {
                        CarLibraryUnavailableReason.ACCESS_LOST,
                        CarLibraryUnavailableReason.NO_ACTIVE_LIBRARY,
                        -> {
                            synchronized(lock) {
                                publishProgressLocked(
                                    running = false,
                                    currentSongId = null,
                                    pausedReason = "Car Library access is temporarily unavailable",
                                )
                            }
                            return
                        }

                        else ->
                            markFailed(
                                songId,
                                fingerprint,
                                "Media unavailable: ${availability.reason.name}",
                            )
                    }
                }
            }

            delay(INTER_TRACK_YIELD_MS)
        }
    }
    private fun markFailed(
        songId: String,
        fingerprint: String,
        message: String,
    ) {
        store.putFailure(songId, fingerprint, message)
        synchronized(lock) {
            if (pendingIds.remove(songId)) failedCount++
            publishProgressLocked(
                running = true,
                currentSongId = null,
                pausedReason = null,
            )
        }
    }

    private fun publishProgressLocked(
        running: Boolean,
        currentSongId: String?,
        pausedReason: String?,
    ) {
        val total = activeIndex?.songs?.size ?: 0
        _progress.value =
            CarLoudnessProgress(
                total = total,
                analyzed = analyzedCount,
                failed = failedCount,
                pending = pendingIds.size,
                currentSongId = currentSongId,
                running = running && pendingIds.isNotEmpty(),
                pausedReason = pausedReason,
            )
    }

    override fun close() {
        scope.cancel()
        wake.close()
        dispatcher.close()
        executor.shutdownNow()
        store.close()
    }

    private companion object {
        const val INTER_TRACK_YIELD_MS = 75L
        const val TRANSIENT_CODEC_RETRY_MS = 2_000L
    }
}
