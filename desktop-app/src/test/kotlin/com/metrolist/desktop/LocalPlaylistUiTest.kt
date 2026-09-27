package com.metrolist.desktop

import com.metrolist.desktop.library.LocalPlaylist
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocalPlaylistUiTest {
    @Test
    fun openSearchRenameAndDeleteRefreshLocalPlaylistState() = runBlocking {
        val gateway = FakePlaylistWorkspaceGateway()
        val playlist = LocalPlaylist("local-1", "Original", 1L, 1L)
        gateway.playlists += playlist
        gateway.songs[playlist.id] =
            listOf(
                song("a", "First", "Shared Artist"),
                song("b", "Middle", "Other Artist"),
                song("c", "Third", "Shared Artist"),
            )
        val controller = PlaylistWorkspaceController(gateway)

        controller.refreshLocalPlaylists()
        controller.openLocalPlaylist("local-1")
        assertEquals("Original", controller.state.value.selectedLocalPlaylist?.name)
        assertEquals(listOf("a", "b", "c"), controller.state.value.localPlaylistSongs.map { it.id })

        controller.setLocalSearchQuery("shared")
        assertEquals(listOf("a", "c"), controller.filteredLocalSongs().map { it.id })

        controller.renameLocalPlaylist("Renamed")
        assertEquals("Renamed", controller.state.value.selectedLocalPlaylist?.name)

        controller.deleteSelectedLocalPlaylist()
        assertEquals(PlaylistWorkspaceScreen.CLOSED, controller.state.value.screen)
        assertNull(controller.state.value.selectedLocalPlaylist)
        assertEquals(emptyList(), controller.state.value.localPlaylists)
        controller.close()
    }

    private fun song(id: String, title: String, artist: String) =
        SongItem(
            id = id,
            title = title,
            artists = listOf(Artist(artist, "artist-$id")),
            thumbnail = "",
        )
}
