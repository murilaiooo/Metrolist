package com.metrolist.desktop.player

import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MpvPlayer(
    private val sourceSelector: PlayerSourceSelector,
    private val backend: MpvBackend,
) : DesktopPlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableState = MutableStateFlow(PlayerState())
    private var pollJob: Job? = null

    override val state: StateFlow<PlayerState> = mutableState.asStateFlow()

    override suspend fun play(
        song: SongItem,
        offlineOnly: Boolean,
    ) {
        try {
            when (val source = sourceSelector.select(song, offlineOnly)) {
                is PlayerSource.Local ->
                    backend.load(
                        source = source.path.toString(),
                        headers = emptyMap(),
                    )

                is PlayerSource.Remote ->
                    backend.load(
                        source = source.resolved.url,
                        headers = source.resolved.headers,
                    )
            }

            backend.setPaused(false)
            mutableState.value =
                mutableState.value.copy(
                    currentSong = song,
                    isPlaying = true,
                    positionMs = 0L,
                    error = null,
                )
            startPolling()
        } catch (error: Throwable) {
            mutableState.value =
                mutableState.value.copy(
                    currentSong = song,
                    isPlaying = false,
                    error = error::class.simpleName ?: "PlaybackError",
                )
            throw error
        }
    }

    override fun pause() {
        backend.setPaused(true)
        mutableState.value = mutableState.value.copy(isPlaying = false)
    }

    override fun resume() {
        backend.setPaused(false)
        mutableState.value = mutableState.value.copy(isPlaying = true)
    }

    override fun seek(positionMs: Long) {
        backend.seek(positionMs.coerceAtLeast(0L) / 1000.0)
        mutableState.value =
            mutableState.value.copy(positionMs = positionMs.coerceAtLeast(0L))
    }

    override fun setVolume(volume: Int) {
        val safeVolume = volume.coerceIn(0, 100)
        backend.setVolume(safeVolume)
        mutableState.value = mutableState.value.copy(volume = safeVolume)
    }

    override fun stop() {
        backend.stop()
        pollJob?.cancel()
        mutableState.value =
            mutableState.value.copy(
                isPlaying = false,
                positionMs = 0L,
            )
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob =
            scope.launch {
                while (isActive) {
                    val positionMs =
                        backend.property("time-pos")
                            ?.toDoubleOrNull()
                            ?.times(1000.0)
                            ?.toLong()
                            ?: mutableState.value.positionMs
                    val durationMs =
                        backend.property("duration")
                            ?.toDoubleOrNull()
                            ?.times(1000.0)
                            ?.toLong()
                    val paused =
                        backend.property("pause")
                            ?.equals("yes", ignoreCase = true)
                            ?: !mutableState.value.isPlaying
                    val volume =
                        backend.property("volume")
                            ?.toDoubleOrNull()
                            ?.toInt()
                            ?.coerceIn(0, 100)
                            ?: mutableState.value.volume

                    mutableState.value =
                        mutableState.value.copy(
                            positionMs = positionMs,
                            durationMs = durationMs,
                            isPlaying = !paused,
                            volume = volume,
                        )

                    delay(250)
                }
            }
    }

    override fun close() {
        pollJob?.cancel()
        scope.cancel()
        backend.close()
    }
}
