package com.metrolist.desktop

import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.pages.HomePage
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppStateTest {
    @Test
    fun navigatesSearchesAndOpensPlaylist() = runBlocking {
        val song = SongItem(
            id = "song-1",
            title = "Song One",
            artists = emptyList(),
            thumbnail = "",
        )
        val playlist = PlaylistItem(
            id = "playlist-1",
            title = "Playlist One",
            author = null,
            songCountText = "1 song",
            thumbnail = null,
            playEndpoint = null,
            shuffleEndpoint = null,
            radioEndpoint = null,
        )

        val catalog = object : DesktopCatalogGateway {
            override suspend fun homeSections(): List<HomePage.Section> = emptyList()
            override suspend fun searchSongs(query: String): List<SongItem> =
                if (query == "song") listOf(song) else emptyList()
            override suspend fun libraryPlaylists(): List<PlaylistItem> = listOf(playlist)
            override suspend fun playlistSongs(playlistId: String): List<SongItem> = listOf(song)
        }

        val state = DesktopAppState(catalog)

        state.navigate(AppDestination.SEARCH)
        state.search("song")
        waitUntil { !state.ui.value.loading }

        assertEquals(AppDestination.SEARCH, state.ui.value.destination)
        assertEquals(listOf("song-1"), state.ui.value.searchResults.map(YTItem::id))

        state.navigate(AppDestination.LIBRARY)
        state.refreshLibrary()
        waitUntil { !state.ui.value.loading }

        assertEquals(listOf("playlist-1"), state.ui.value.libraryPlaylists.map(YTItem::id))

        state.openPlaylist(playlist)
        waitUntil { !state.ui.value.loading }

        assertTrue(state.ui.value.showingPlaylist)
        assertEquals("Playlist One", state.ui.value.playlistTitle)
        assertEquals(listOf("song-1"), state.ui.value.playlistSongs.map(YTItem::id))

        state.close()
        assertFalse(state.ui.value.loading)
    }

    private suspend fun waitUntil(
        timeoutMs: Long = 2_000,
        condition: () -> Boolean,
    ) {
        val started = System.currentTimeMillis()
        while (!condition()) {
            check(System.currentTimeMillis() - started < timeoutMs) { "Timed out waiting for state" }
            delay(10)
        }
    }
}
