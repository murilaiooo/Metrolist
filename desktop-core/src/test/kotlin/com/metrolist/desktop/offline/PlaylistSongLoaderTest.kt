package com.metrolist.desktop.offline

import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaylistSongLoaderTest {
    @Test
    fun followsContinuationsAndDeduplicatesByVideoId() = runBlocking {
        val source = object : PlaylistPageSource {
            override suspend fun first(playlistId: String) =
                PlaylistChunk(listOf(song("a"), song("b")), "next-1")

            override suspend fun next(continuation: String) =
                when (continuation) {
                    "next-1" -> PlaylistChunk(listOf(song("b"), song("c")), "next-2")
                    else -> PlaylistChunk(listOf(song("d")), null)
                }
        }

        val songs = PlaylistSongLoader(source).loadAll("playlist")

        assertEquals(listOf("a", "b", "c", "d"), songs.map { it.id })
    }

    @Test
    fun repeatedContinuationStopsInsteadOfLoopingForever() = runBlocking {
        val source = object : PlaylistPageSource {
            override suspend fun first(playlistId: String) =
                PlaylistChunk(listOf(song("a")), "same")

            override suspend fun next(continuation: String) =
                PlaylistChunk(listOf(song("b")), "same")
        }

        val songs = PlaylistSongLoader(source).loadAll("playlist")

        assertEquals(listOf("a", "b"), songs.map { it.id })
    }

    private fun song(id: String) = SongItem(
        id = id,
        title = id,
        artists = emptyList(),
        thumbnail = "",
    )
}
