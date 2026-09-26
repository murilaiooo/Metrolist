package com.metrolist.desktop.offline

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem

data class PlaylistChunk(
    val songs: List<SongItem>,
    val continuation: String?,
)

interface PlaylistPageSource {
    suspend fun first(playlistId: String): PlaylistChunk
    suspend fun next(continuation: String): PlaylistChunk
}

class YouTubePlaylistPageSource : PlaylistPageSource {
    override suspend fun first(playlistId: String): PlaylistChunk {
        val page = YouTube.playlist(playlistId).getOrThrow()
        return PlaylistChunk(
            songs = page.songs,
            continuation = page.songsContinuation,
        )
    }

    override suspend fun next(continuation: String): PlaylistChunk {
        val page = YouTube.playlistContinuation(continuation).getOrThrow()
        return PlaylistChunk(
            songs = page.songs,
            continuation = page.continuation,
        )
    }
}

class PlaylistSongLoader(
    private val source: PlaylistPageSource = YouTubePlaylistPageSource(),
) {
    suspend fun loadAll(playlistId: String): List<SongItem> {
        val result = linkedMapOf<String, SongItem>()
        val seenContinuations = mutableSetOf<String>()

        var chunk = source.first(playlistId)
        while (true) {
            chunk.songs.forEach { song -> result.putIfAbsent(song.id, song) }

            val continuation = chunk.continuation ?: break
            if (!seenContinuations.add(continuation)) break
            chunk = source.next(continuation)
        }

        return result.values.toList()
    }
}
