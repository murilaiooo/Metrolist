package com.metrolist.desktop

import com.metrolist.desktop.offline.PlaylistSongLoader
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.BrowseEndpoint
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.pages.BrowseResult
import com.metrolist.innertube.pages.ExplorePage
import com.metrolist.innertube.pages.HomePage
import com.metrolist.innertube.pages.MoodAndGenres
import com.metrolist.innertube.pages.SearchSummary
import com.metrolist.innertube.utils.completed

enum class CatalogDetailKind {
    ALBUM,
    ARTIST,
}

data class CatalogDetailSection(
    val title: String,
    val items: List<YTItem>,
)

data class CatalogDetail(
    val kind: CatalogDetailKind,
    val id: String,
    val title: String,
    val thumbnail: String?,
    val subtitle: String? = null,
    val description: String? = null,
    val sections: List<CatalogDetailSection>,
)

data class ExploreContent(
    val newReleaseAlbums: List<AlbumItem> = emptyList(),
    val moods: List<MoodAndGenres.Item> = emptyList(),
)

internal fun isDesktopCatalogItem(item: YTItem): Boolean =
    item is SongItem ||
        item is AlbumItem ||
        item is ArtistItem ||
        item is PlaylistItem

internal fun mapExploreContent(page: ExplorePage): ExploreContent =
    ExploreContent(
        newReleaseAlbums =
            page.newReleaseAlbums
                .filter { it.id.isNotBlank() && it.title.isNotBlank() }
                .distinctBy(AlbumItem::id),
        moods =
            page.moodAndGenres
                .filter { it.title.isNotBlank() && it.endpoint.browseId.isNotBlank() }
                .distinctBy { it.title + "|" + it.endpoint.browseId + "|" + it.endpoint.params.orEmpty() },
    )

internal fun mapBrowseSections(result: BrowseResult): List<CatalogDetailSection> =
    result.items.mapNotNull { section ->
        val items =
            section.items
                .filter(::isDesktopCatalogItem)
                .filter { it.id.isNotBlank() && it.title.isNotBlank() }
        items.takeIf(List<YTItem>::isNotEmpty)?.let {
            CatalogDetailSection(
                title =
                    section.title?.takeIf(String::isNotBlank)
                        ?: result.title?.takeIf(String::isNotBlank)
                        ?: "More",
                items = it,
            )
        }
    }

interface DesktopCatalogGateway {
    suspend fun homeSections(): List<HomePage.Section>
    suspend fun explore(): ExploreContent = ExploreContent()
    suspend fun browseMood(endpoint: BrowseEndpoint): List<CatalogDetailSection> = emptyList()
    suspend fun searchSongs(query: String): List<SongItem>
    suspend fun searchSections(query: String): List<SearchSummary> =
        listOf(SearchSummary("Songs", searchSongs(query)))
    suspend fun libraryPlaylists(): List<PlaylistItem>
    suspend fun librarySongs(): List<SongItem> = emptyList()
    suspend fun libraryAlbums(): List<AlbumItem> = emptyList()
    suspend fun libraryArtists(): List<ArtistItem> = emptyList()
    suspend fun playlistSongs(playlistId: String): List<SongItem>
    suspend fun albumDetail(albumId: String): CatalogDetail =
        error("Album details are unavailable")
    suspend fun artistDetail(artistId: String): CatalogDetail =
        error("Artist details are unavailable")
}

class MetroListCatalogGateway(
    private val playlistLoader: PlaylistSongLoader = PlaylistSongLoader(),
) : DesktopCatalogGateway {
    override suspend fun homeSections(): List<HomePage.Section> =
        YouTube.home().getOrThrow().sections

    override suspend fun explore(): ExploreContent =
        mapExploreContent(YouTube.explore().getOrThrow())

    override suspend fun browseMood(endpoint: BrowseEndpoint): List<CatalogDetailSection> =
        mapBrowseSections(
            YouTube.browse(
                browseId = endpoint.browseId,
                params = endpoint.params,
            ).getOrThrow(),
        )

    override suspend fun searchSongs(query: String): List<SongItem> =
        YouTube.search(
            query = query,
            filter = YouTube.SearchFilter.FILTER_SONG,
        ).getOrThrow()
            .items
            .filterIsInstance<SongItem>()

    override suspend fun searchSections(query: String): List<SearchSummary> =
        YouTube.searchSummary(query)
            .getOrThrow()
            .summaries
            .mapNotNull { summary ->
                val items = summary.items.filter(::isDesktopCatalogItem)
                items.takeIf(List<YTItem>::isNotEmpty)?.let {
                    SearchSummary(summary.title, it)
                }
            }

    override suspend fun albumDetail(albumId: String): CatalogDetail {
        val page = YouTube.album(albumId).getOrThrow()
        val subtitle =
            buildList {
                page.album.artists
                    ?.joinToString(", ") { it.name }
                    ?.takeIf(String::isNotBlank)
                    ?.let(::add)
                page.album.year?.toString()?.let(::add)
            }.joinToString(" · ").ifBlank { null }

        return CatalogDetail(
            kind = CatalogDetailKind.ALBUM,
            id = albumId,
            title = page.album.title,
            thumbnail = page.album.thumbnail,
            subtitle = subtitle,
            sections =
                buildList {
                    if (page.songs.isNotEmpty()) {
                        add(CatalogDetailSection("Songs", page.songs))
                    }
                    if (page.otherVersions.isNotEmpty()) {
                        add(CatalogDetailSection("Other versions", page.otherVersions))
                    }
                },
        )
    }

    override suspend fun artistDetail(artistId: String): CatalogDetail {
        val page = YouTube.artist(artistId).getOrThrow()
        val subtitle =
            listOfNotNull(
                page.monthlyListenerCount,
                page.subscriberCountText,
            ).joinToString(" · ").ifBlank { null }

        return CatalogDetail(
            kind = CatalogDetailKind.ARTIST,
            id = artistId,
            title = page.artist.title,
            thumbnail = page.artist.thumbnail,
            subtitle = subtitle,
            description = page.description,
            sections =
                page.sections.mapNotNull { section ->
                    val items = section.items.filter(::isDesktopCatalogItem)
                    items.takeIf(List<YTItem>::isNotEmpty)?.let {
                        CatalogDetailSection(
                            title = section.title.ifBlank { "More" },
                            items = it,
                        )
                    }
                },
        )
    }

    override suspend fun libraryPlaylists(): List<PlaylistItem> =
        YouTube.library("FEmusic_liked_playlists")
            .completed()
            .getOrThrow()
            .items
            .filterIsInstance<PlaylistItem>()
            .filterNot { it.id == "SE" }

    override suspend fun librarySongs(): List<SongItem> =
        YouTube.playlist("LM")
            .completed()
            .getOrThrow()
            .songs
            .distinctBy(SongItem::id)

    override suspend fun libraryAlbums(): List<AlbumItem> =
        YouTube.library("FEmusic_liked_albums")
            .completed()
            .getOrThrow()
            .items
            .filterIsInstance<AlbumItem>()
            .distinctBy(AlbumItem::id)

    override suspend fun libraryArtists(): List<ArtistItem> =
        YouTube.library("FEmusic_library_corpus_artists")
            .completed()
            .getOrThrow()
            .items
            .filterIsInstance<ArtistItem>()
            .distinctBy(ArtistItem::id)

    override suspend fun playlistSongs(playlistId: String): List<SongItem> =
        playlistLoader.loadAll(playlistId)

}
