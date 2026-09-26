package com.metrolist.desktop.player

import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.flow.StateFlow

data class PlayerState(
    val currentSong: SongItem? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long? = null,
    val volume: Int = 100,
    val error: String? = null,
)

interface DesktopPlayer : AutoCloseable {
    val state: StateFlow<PlayerState>

    suspend fun play(
        song: SongItem,
        offlineOnly: Boolean = false,
    )

    fun pause()
    fun resume()
    fun seek(positionMs: Long)
    fun setVolume(volume: Int)
    fun stop()
}

interface MpvBackend : AutoCloseable {
    fun load(
        source: String,
        headers: Map<String, String> = emptyMap(),
    )

    fun setPaused(paused: Boolean)
    fun seek(seconds: Double)
    fun setVolume(volume: Int)
    fun property(name: String): String?
    fun stop()
}
