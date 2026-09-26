package com.metrolist.desktop.offline

import com.metrolist.innertube.models.SongItem
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

class PersistentOfflineQueue(
    private val store: OfflineStore,
    private val worker: suspend (OfflineRecord) -> OfflineRecord,
    maxConcurrent: Int = 3,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val semaphore = Semaphore(maxConcurrent)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val lock = Any()
    private val mutableDownloads = MutableStateFlow<List<OfflineRecord>>(emptyList())

    val downloads: StateFlow<List<OfflineRecord>> = mutableDownloads.asStateFlow()

    init {
        require(maxConcurrent > 0)
        store.recoverInterrupted()
        refresh()
        store.listByState(OfflineState.QUEUED).forEach { launchIfNeeded(it.videoId) }
    }

    fun enqueue(song: SongItem) {
        val existing = store.get(song.id)
        if (existing?.state == OfflineState.COMPLETED) {
            refresh()
            return
        }

        val record =
            existing?.copy(
                state = OfflineState.QUEUED,
                lastError = null,
                updateTimeMs = System.currentTimeMillis(),
            ) ?: OfflineRecord.queued(song)

        store.upsert(record)
        refresh()
        launchIfNeeded(record.videoId)
    }

    fun enqueueAll(songs: List<SongItem>) {
        songs.distinctBy(SongItem::id).forEach(::enqueue)
    }

    suspend fun enqueuePlaylist(
        playlistId: String,
        loader: PlaylistSongLoader = PlaylistSongLoader(),
    ): Int {
        val songs = loader.loadAll(playlistId)
        enqueueAll(songs)
        return songs.size
    }

    fun retry(videoId: String) {
        val record = store.get(videoId) ?: return
        if (record.state == OfflineState.COMPLETED) return
        store.upsert(
            record.copy(
                state = OfflineState.QUEUED,
                lastError = null,
                updateTimeMs = System.currentTimeMillis(),
            ),
        )
        refresh()
        launchIfNeeded(videoId)
    }

    fun remove(videoId: String) {
        jobs.remove(videoId)?.cancel()
        store.remove(videoId)
        refresh()
    }

    suspend fun awaitIdle(timeoutMs: Long): Boolean =
        withTimeoutOrNull(timeoutMs) {
            while (true) {
                val active = jobs.values.any(Job::isActive)
                val pending =
                    store.listAll().any {
                        it.state == OfflineState.QUEUED || it.state == OfflineState.DOWNLOADING
                    }
                if (!active && !pending) return@withTimeoutOrNull true
                delay(20)
            }
            @Suppress("UNREACHABLE_CODE")
            false
        } ?: false

    private fun launchIfNeeded(videoId: String) {
        synchronized(lock) {
            if (jobs[videoId]?.isActive == true) return

            val job =
                scope.launch {
                    semaphore.withPermit {
                        val queued = store.get(videoId) ?: return@withPermit
                        if (queued.state == OfflineState.COMPLETED) return@withPermit

                        val downloading =
                            queued.copy(
                                state = OfflineState.DOWNLOADING,
                                updateTimeMs = System.currentTimeMillis(),
                                lastError = null,
                            )
                        store.upsert(downloading)
                        refresh()

                        try {
                            val completed = worker(downloading)
                            store.upsert(
                                completed.copy(
                                    state = OfflineState.COMPLETED,
                                    updateTimeMs = System.currentTimeMillis(),
                                    lastError = null,
                                ),
                            )
                        } catch (error: Throwable) {
                            if (error is kotlinx.coroutines.CancellationException) throw error
                            store.upsert(
                                downloading.copy(
                                    state = OfflineState.FAILED,
                                    updateTimeMs = System.currentTimeMillis(),
                                    lastError = error::class.simpleName ?: "TransferError",
                                ),
                            )
                        } finally {
                            refresh()
                        }
                    }
                }

            jobs[videoId] = job
            job.invokeOnCompletion {
                jobs.remove(videoId, job)
                refresh()
            }
        }
    }

    private fun refresh() {
        mutableDownloads.value = store.listAll()
    }

    override fun close() {
        scope.cancel()
    }
}
