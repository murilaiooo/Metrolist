package com.metrolist.desktop

import com.metrolist.desktop.offline.PlaylistSongLoader
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.pages.HomePage
import com.metrolist.innertube.utils.completed

interface DesktopCatalogGateway {
    suspend fun homeSections(): List<HomePage.Section>
    suspend fun searchSongs(query: String): List<SongItem>
    suspend fun libraryPlaylists(): List<PlaylistItem>
    suspend fun playlistSongs(playlistId: String): List<SongItem>
}

class MetroListCatalogGateway(
    private val playlistLoader: PlaylistSongLoader = PlaylistSongLoader(),
) : DesktopCatalogGateway {
    override suspend fun homeSections(): List<HomePage.Section> =
        YouTube.home().getOrThrow().sections

    override suspend fun searchSongs(query: String): List<SongItem> =
        YouTube.search(
            query = query,
            filter = YouTube.SearchFilter.FILTER_SONG,
        ).getOrThrow()
            .items
            .filterIsInstance<SongItem>()

    override suspend fun libraryPlaylists(): List<PlaylistItem> =
        YouTube.library("FEmusic_liked_playlists")
            .completed()
            .getOrThrow()
            .items
            .filterIsInstance<PlaylistItem>()

    override suspend fun playlistSongs(playlistId: String): List<SongItem> =
        playlistLoader.loadAll(playlistId)
}
