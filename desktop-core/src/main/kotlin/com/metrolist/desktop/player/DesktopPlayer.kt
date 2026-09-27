package com.metrolist.desktop.player

import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.flow.StateFlow

enum class RepeatMode {
    OFF,
    ALL,
    ONE,
}

data class PlayerState(
    val currentSong: SongItem? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long? = null,
    val volume: Int = 100,
    val queue: List<SongItem> = emptyList(),
    val queueIndex: Int = -1,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val ended: Boolean = false,
    val canSkipNext: Boolean = false,
    val error: String? = null,
    val audioFormat: PlaybackAudioFormat? = null,
)

interface DesktopPlayer : AutoCloseable {
    val state: StateFlow<PlayerState>

    suspend fun play(
        song: SongItem,
        offlineOnly: Boolean = false,
    )

    suspend fun playQueue(
        songs: List<SongItem>,
        startIndex: Int = 0,
        offlineOnly: Boolean = false,
    )

    suspend fun next()
    suspend fun previous()
    suspend fun playNext(song: SongItem)
    suspend fun addToQueue(song: SongItem)
    suspend fun moveQueueItem(fromIndex: Int, toIndex: Int)
    suspend fun removeQueueItem(index: Int)
    suspend fun clearUpcoming()
    suspend fun toggleShuffle()
    suspend fun cycleRepeatMode()
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
