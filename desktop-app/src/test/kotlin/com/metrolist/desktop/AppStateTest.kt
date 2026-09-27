package com.metrolist.desktop

import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.pages.HomePage
import com.metrolist.innertube.pages.SearchSummary
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

    @Test
    fun newerSearchWinsWhenOlderRequestCompletesLater() = runBlocking {
        val old = SongItem(id = "old", title = "Old", artists = emptyList(), thumbnail = "")
        val fresh = SongItem(id = "fresh", title = "Fresh", artists = emptyList(), thumbnail = "")

        val catalog = object : DesktopCatalogGateway {
            override suspend fun homeSections(): List<HomePage.Section> = emptyList()
            override suspend fun searchSongs(query: String): List<SongItem> {
                if (query == "old") delay(180) else delay(20)
                return if (query == "old") listOf(old) else listOf(fresh)
            }
            override suspend fun libraryPlaylists(): List<PlaylistItem> = emptyList()
            override suspend fun playlistSongs(playlistId: String): List<SongItem> = emptyList()
        }

        val state = DesktopAppState(catalog)
        state.search("old")
        delay(15)
        state.search("fresh")

        waitUntil { !state.ui.value.loading && state.ui.value.searchQuery == "fresh" }
        delay(220)

        assertEquals("fresh", state.ui.value.searchQuery)
        assertEquals(listOf("fresh"), state.ui.value.searchResults.map(YTItem::id))
        state.close()
    }

    @Test
    fun newerPlaylistWinsWhenOlderRequestCompletesLater() = runBlocking {
        val songA = SongItem(id = "a", title = "A", artists = emptyList(), thumbnail = "")
        val songB = SongItem(id = "b", title = "B", artists = emptyList(), thumbnail = "")
        val playlistA = playlist("playlist-a", "Playlist A")
        val playlistB = playlist("playlist-b", "Playlist B")

        val catalog = object : DesktopCatalogGateway {
            override suspend fun homeSections(): List<HomePage.Section> = emptyList()
            override suspend fun searchSongs(query: String): List<SongItem> = emptyList()
            override suspend fun libraryPlaylists(): List<PlaylistItem> = emptyList()
            override suspend fun playlistSongs(playlistId: String): List<SongItem> {
                if (playlistId == playlistA.id) delay(180) else delay(20)
                return if (playlistId == playlistA.id) listOf(songA) else listOf(songB)
            }
        }

        val state = DesktopAppState(catalog)
        state.openPlaylist(playlistA)
        delay(15)
        state.openPlaylist(playlistB)

        waitUntil { !state.ui.value.loading && state.ui.value.playlistId == playlistB.id }
        delay(220)

        assertEquals(playlistB.id, state.ui.value.playlistId)
        assertEquals(listOf("b"), state.ui.value.playlistSongs.map(YTItem::id))
        state.close()
    }

    @Test
    fun libraryLoadsPlaylistsSongsAlbumsAndArtistsAndSwitchesTabs() = runBlocking {
        val song = SongItem(
            id = "liked-song",
            title = "Liked Song",
            artists = emptyList(),
            thumbnail = "",
        )
        val album = AlbumItem(
            browseId = "liked-album",
            playlistId = "liked-album-playlist",
            title = "Liked Album",
            artists = emptyList(),
            thumbnail = "",
        )
        val artist = ArtistItem(
            id = "library-artist",
            title = "Library Artist",
            thumbnail = null,
            shuffleEndpoint = null,
            radioEndpoint = null,
        )
        val playlist = playlist("library-playlist", "Library Playlist")

        val catalog = object : DesktopCatalogGateway {
            override suspend fun homeSections(): List<HomePage.Section> = emptyList()
            override suspend fun searchSongs(query: String): List<SongItem> = emptyList()
            override suspend fun libraryPlaylists(): List<PlaylistItem> = listOf(playlist)
            override suspend fun librarySongs(): List<SongItem> = listOf(song)
            override suspend fun libraryAlbums(): List<AlbumItem> = listOf(album)
            override suspend fun libraryArtists(): List<ArtistItem> = listOf(artist)
            override suspend fun playlistSongs(playlistId: String): List<SongItem> = emptyList()
        }

        val state = DesktopAppState(catalog)
        state.navigate(AppDestination.LIBRARY)
        waitUntil { !state.ui.value.loading }

        assertEquals(listOf("library-playlist"), state.ui.value.libraryPlaylists.map(YTItem::id))
        assertEquals(listOf("liked-song"), state.ui.value.librarySongs.map(YTItem::id))
        assertEquals(listOf("liked-album"), state.ui.value.libraryAlbums.map(YTItem::id))
        assertEquals(listOf("library-artist"), state.ui.value.libraryArtists.map(YTItem::id))
        assertEquals(LibraryTab.PLAYLISTS, state.ui.value.libraryTab)

        state.selectLibraryTab(LibraryTab.SONGS)
        assertEquals(LibraryTab.SONGS, state.ui.value.libraryTab)
        state.selectLibraryTab(LibraryTab.ALBUMS)
        assertEquals(LibraryTab.ALBUMS, state.ui.value.libraryTab)
        state.selectLibraryTab(LibraryTab.ARTISTS)
        assertEquals(LibraryTab.ARTISTS, state.ui.value.libraryTab)

        state.close()
    }

    @Test
    fun mixedSearchAndAlbumArtistDetailsAreExposed() = runBlocking {
        val song = SongItem(
            id = "song-1",
            title = "Song One",
            artists = emptyList(),
            thumbnail = "",
        )
        val album = AlbumItem(
            browseId = "album-1",
            playlistId = "playlist-album-1",
            title = "Album One",
            artists = emptyList(),
            thumbnail = "",
        )
        val artist = ArtistItem(
            id = "artist-1",
            title = "Artist One",
            thumbnail = null,
            shuffleEndpoint = null,
            radioEndpoint = null,
        )

        val catalog = object : DesktopCatalogGateway {
            override suspend fun homeSections(): List<HomePage.Section> = emptyList()
            override suspend fun searchSongs(query: String): List<SongItem> = listOf(song)
            override suspend fun searchSections(query: String): List<SearchSummary> =
                listOf(
                    SearchSummary("Songs", listOf(song)),
                    SearchSummary("Albums", listOf(album)),
                    SearchSummary("Artists", listOf(artist)),
                )
            override suspend fun libraryPlaylists(): List<PlaylistItem> = emptyList()
            override suspend fun playlistSongs(playlistId: String): List<SongItem> = emptyList()
            override suspend fun albumDetail(albumId: String): CatalogDetail =
                CatalogDetail(
                    kind = CatalogDetailKind.ALBUM,
                    id = albumId,
                    title = "Album One",
                    thumbnail = "",
                    subtitle = "Artist One",
                    sections = listOf(CatalogDetailSection("Songs", listOf(song))),
                )
            override suspend fun artistDetail(artistId: String): CatalogDetail =
                CatalogDetail(
                    kind = CatalogDetailKind.ARTIST,
                    id = artistId,
                    title = "Artist One",
                    thumbnail = null,
                    sections = listOf(CatalogDetailSection("Top songs", listOf(song))),
                )
        }

        val state = DesktopAppState(catalog)
        state.search("one")
        waitUntil { !state.ui.value.loading }

        assertEquals(listOf("Songs", "Albums", "Artists"), state.ui.value.searchSections.map { it.title })
        assertEquals(listOf("song-1"), state.ui.value.searchResults.map(YTItem::id))

        state.openAlbum(album)
        waitUntil { !state.ui.value.loading }
        assertEquals(CatalogDetailKind.ALBUM, state.ui.value.detail?.kind)
        assertEquals("Album One", state.ui.value.detail?.title)

        state.openArtist(artist)
        waitUntil { !state.ui.value.loading }
        assertEquals(CatalogDetailKind.ARTIST, state.ui.value.detail?.kind)
        assertEquals("Artist One", state.ui.value.detail?.title)

        state.close()
    }

    @Test
    fun exploreFailureStaysSeparateFromGeneralScreenErrors() = runBlocking {
        val catalog = object : DesktopCatalogGateway {
            override suspend fun homeSections(): List<HomePage.Section> = emptyList()
            override suspend fun searchSongs(query: String): List<SongItem> = emptyList()
            override suspend fun libraryPlaylists(): List<PlaylistItem> = emptyList()
            override suspend fun playlistSongs(playlistId: String): List<SongItem> = emptyList()
            override suspend fun explore(): ExploreContent = error("explore failed")
        }

        val state = DesktopAppState(catalog)
        state.navigate(AppDestination.EXPLORE)
        waitUntil { !state.ui.value.exploreLoading }

        assertEquals("explore failed", state.ui.value.exploreError)
        assertEquals(null, state.ui.value.error)
        state.close()
    }

    @Test
    fun latestExploreMoodSelectionWinsWhenOlderRequestFinishesLater() = runBlocking {
        val slowMood =
            com.metrolist.innertube.pages.MoodAndGenres.Item(
                title = "Slow",
                stripeColor = 0,
                endpoint = com.metrolist.innertube.models.BrowseEndpoint("slow"),
            )
        val fastMood =
            com.metrolist.innertube.pages.MoodAndGenres.Item(
                title = "Fast",
                stripeColor = 0,
                endpoint = com.metrolist.innertube.models.BrowseEndpoint("fast"),
            )
        val catalog = object : DesktopCatalogGateway {
            override suspend fun homeSections(): List<HomePage.Section> = emptyList()
            override suspend fun searchSongs(query: String): List<SongItem> = emptyList()
            override suspend fun libraryPlaylists(): List<PlaylistItem> = emptyList()
            override suspend fun playlistSongs(playlistId: String): List<SongItem> = emptyList()
            override suspend fun explore(): ExploreContent =
                ExploreContent(moods = listOf(slowMood, fastMood))

            override suspend fun browseMood(
                endpoint: com.metrolist.innertube.models.BrowseEndpoint,
            ): List<CatalogDetailSection> {
                if (endpoint.browseId == "slow") delay(180) else delay(20)
                val song =
                    SongItem(
                        id = endpoint.browseId,
                        title = endpoint.browseId,
                        artists = emptyList(),
                        thumbnail = "",
                    )
                return listOf(CatalogDetailSection("Results", listOf(song)))
            }
        }

        val state = DesktopAppState(catalog)
        state.navigate(AppDestination.EXPLORE)
        waitUntil { !state.ui.value.exploreLoading }

        state.openExploreMood(slowMood)
        delay(10)
        state.openExploreMood(fastMood)
        waitUntil {
            !state.ui.value.exploreDetailLoading &&
                state.ui.value.exploreSelectedMood?.title == "Fast"
        }
        delay(220)

        assertEquals("Fast", state.ui.value.exploreSelectedMood?.title)
        assertEquals(
            listOf("fast"),
            state.ui.value.exploreDetailSections.flatMap(CatalogDetailSection::items).map(YTItem::id),
        )
        state.close()
    }
    private fun playlist(id: String, title: String) = PlaylistItem(
        id = id,
        title = title,
        author = null,
        songCountText = null,
        thumbnail = null,
        playEndpoint = null,
        shuffleEndpoint = null,
        radioEndpoint = null,
    )

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
