package com.metrolist.desktop

import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.BrowseEndpoint
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.pages.HomePage
import com.metrolist.innertube.pages.MoodAndGenres
import com.metrolist.innertube.pages.SearchSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

enum class AppDestination {
    HOME,
    EXPLORE,
    SEARCH,
    LIBRARY,
    DOWNLOADS,
}

enum class LibraryTab {
    PLAYLISTS,
    SONGS,
    ALBUMS,
    ARTISTS,
}

data class DesktopUiState(
    val destination: AppDestination = AppDestination.HOME,
    val loading: Boolean = false,
    val error: String? = null,
    val homeSections: List<HomePage.Section> = emptyList(),
    val exploreContent: ExploreContent = ExploreContent(),
    val exploreLoading: Boolean = false,
    val exploreError: String? = null,
    val exploreSelectedMood: MoodAndGenres.Item? = null,
    val exploreDetailSections: List<CatalogDetailSection> = emptyList(),
    val exploreDetailLoading: Boolean = false,
    val exploreDetailError: String? = null,
    val searchQuery: String = "",
    val searchSections: List<SearchSummary> = emptyList(),
    val searchResults: List<SongItem> = emptyList(),
    val libraryTab: LibraryTab = LibraryTab.PLAYLISTS,
    val libraryPlaylists: List<PlaylistItem> = emptyList(),
    val librarySongs: List<SongItem> = emptyList(),
    val libraryAlbums: List<AlbumItem> = emptyList(),
    val libraryArtists: List<ArtistItem> = emptyList(),
    val detail: CatalogDetail? = null,
    val showingPlaylist: Boolean = false,
    val playlistId: String? = null,
    val playlistTitle: String? = null,
    val playlistSongs: List<SongItem> = emptyList(),
)

class DesktopAppState(
    private val catalog: DesktopCatalogGateway,
    private val scope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : AutoCloseable {
    private val mutableUi = MutableStateFlow(DesktopUiState())
    private val requestGeneration = AtomicLong()
    val ui: StateFlow<DesktopUiState> = mutableUi.asStateFlow()

    fun loadInitial() {
        refreshHome()
    }

    fun navigate(destination: AppDestination) {
        requestGeneration.incrementAndGet()
        mutableUi.value =
            mutableUi.value.copy(
                destination = destination,
                detail = null,
                showingPlaylist = false,
                playlistId = null,
                playlistTitle = null,
                playlistSongs = emptyList(),
                error = null,
                exploreSelectedMood = null,
                exploreDetailSections = emptyList(),
                exploreDetailLoading = false,
                exploreDetailError = null,
            )

        when (destination) {
            AppDestination.HOME -> refreshHome()
            AppDestination.EXPLORE -> refreshExplore()
            AppDestination.LIBRARY -> refreshLibrary()
            AppDestination.SEARCH,
            AppDestination.DOWNLOADS,
            -> Unit
        }
    }

    fun refreshHome() {
        launchLoad { requestId ->
            val sections = catalog.homeSections()
            if (!isCurrent(requestId)) return@launchLoad
            mutableUi.value =
                mutableUi.value.copy(
                    homeSections = sections,
                    loading = false,
                    error = null,
                )
        }
    }

    fun refreshExplore() {
        val requestId = requestGeneration.incrementAndGet()
        mutableUi.value =
            mutableUi.value.copy(
                exploreLoading = true,
                exploreError = null,
                exploreSelectedMood = null,
                exploreDetailSections = emptyList(),
                exploreDetailLoading = false,
                exploreDetailError = null,
            )

        scope.launch {
            runCatching { catalog.explore() }
                .onSuccess { content ->
                    if (
                        isCurrent(requestId) &&
                        mutableUi.value.destination == AppDestination.EXPLORE
                    ) {
                        mutableUi.value =
                            mutableUi.value.copy(
                                exploreContent = content,
                                exploreLoading = false,
                                exploreError = null,
                            )
                    }
                }
                .onFailure { error ->
                    if (
                        isCurrent(requestId) &&
                        mutableUi.value.destination == AppDestination.EXPLORE
                    ) {
                        mutableUi.value =
                            mutableUi.value.copy(
                                exploreLoading = false,
                                exploreError =
                                    error.message ?: error::class.simpleName ?: "Unknown error",
                            )
                    }
                }
        }
    }

    fun openExploreMood(mood: MoodAndGenres.Item) {
        val requestId = requestGeneration.incrementAndGet()
        mutableUi.value =
            mutableUi.value.copy(
                exploreSelectedMood = mood,
                exploreDetailSections = emptyList(),
                exploreDetailLoading = true,
                exploreDetailError = null,
            )

        scope.launch {
            runCatching { catalog.browseMood(mood.endpoint) }
                .onSuccess { sections ->
                    if (
                        isCurrent(requestId) &&
                        mutableUi.value.destination == AppDestination.EXPLORE &&
                        sameEndpoint(mutableUi.value.exploreSelectedMood?.endpoint, mood.endpoint)
                    ) {
                        mutableUi.value =
                            mutableUi.value.copy(
                                exploreDetailSections = sections,
                                exploreDetailLoading = false,
                                exploreDetailError = null,
                            )
                    }
                }
                .onFailure { error ->
                    if (
                        isCurrent(requestId) &&
                        mutableUi.value.destination == AppDestination.EXPLORE
                    ) {
                        mutableUi.value =
                            mutableUi.value.copy(
                                exploreDetailLoading = false,
                                exploreDetailError =
                                    error.message ?: error::class.simpleName ?: "Unknown error",
                            )
                    }
                }
        }
    }

    fun closeExploreMood() {
        requestGeneration.incrementAndGet()
        mutableUi.value =
            mutableUi.value.copy(
                exploreSelectedMood = null,
                exploreDetailSections = emptyList(),
                exploreDetailLoading = false,
                exploreDetailError = null,
            )
    }

    fun search(query: String) {
        val normalized = query.trim()
        mutableUi.value =
            mutableUi.value.copy(
                destination = AppDestination.SEARCH,
                searchQuery = query,
                detail = null,
                showingPlaylist = false,
                playlistSongs = emptyList(),
                error = null,
            )

        if (normalized.isEmpty()) {
            requestGeneration.incrementAndGet()
            mutableUi.value =
                mutableUi.value.copy(
                    searchSections = emptyList(),
                    searchResults = emptyList(),
                    loading = false,
                )
            return
        }

        launchLoad { requestId ->
            val sections = catalog.searchSections(normalized)
            if (!isCurrent(requestId)) return@launchLoad
            val songs =
                sections
                    .flatMap(SearchSummary::items)
                    .filterIsInstance<SongItem>()
                    .distinctBy(SongItem::id)
            mutableUi.value =
                mutableUi.value.copy(
                    searchSections = sections,
                    searchResults = songs,
                    loading = false,
                    error = null,
                )
        }
    }

    fun refreshLibrary() {
        launchLoad { requestId ->
            val playlists = catalog.libraryPlaylists()
            val songs = catalog.librarySongs()
            val albums = catalog.libraryAlbums()
            val artists = catalog.libraryArtists()
            if (!isCurrent(requestId)) return@launchLoad
            mutableUi.value =
                mutableUi.value.copy(
                    libraryPlaylists = playlists,
                    librarySongs = songs,
                    libraryAlbums = albums,
                    libraryArtists = artists,
                    loading = false,
                    error = null,
                )
        }
    }

    fun selectLibraryTab(tab: LibraryTab) {
        mutableUi.value =
            mutableUi.value.copy(
                libraryTab = tab,
                error = null,
            )
    }

    fun openPlaylist(playlist: PlaylistItem) {
        val requestId = requestGeneration.incrementAndGet()
        mutableUi.value =
            mutableUi.value.copy(
                detail = null,
                showingPlaylist = true,
                playlistId = playlist.id,
                playlistTitle = playlist.title,
                playlistSongs = emptyList(),
                loading = true,
                error = null,
            )

        scope.launch {
            runCatching {
                catalog.playlistSongs(playlist.id)
            }.onSuccess { songs ->
                if (isCurrent(requestId) && mutableUi.value.playlistId == playlist.id) {
                    mutableUi.value =
                        mutableUi.value.copy(
                            playlistSongs = songs,
                            loading = false,
                            error = null,
                        )
                }
            }.onFailure { error ->
                if (isCurrent(requestId)) setError(error)
            }
        }
    }

    fun openAlbum(album: AlbumItem) {
        openDetail(
            placeholder =
                CatalogDetail(
                    kind = CatalogDetailKind.ALBUM,
                    id = album.id,
                    title = album.title,
                    thumbnail = album.thumbnail,
                    subtitle = album.artists?.joinToString(", ") { it.name },
                    sections = emptyList(),
                ),
            loader = { catalog.albumDetail(album.id) },
        )
    }

    fun openArtist(artist: ArtistItem) {
        openDetail(
            placeholder =
                CatalogDetail(
                    kind = CatalogDetailKind.ARTIST,
                    id = artist.id,
                    title = artist.title,
                    thumbnail = artist.thumbnail,
                    sections = emptyList(),
                ),
            loader = { catalog.artistDetail(artist.id) },
        )
    }

    private fun openDetail(
        placeholder: CatalogDetail,
        loader: suspend () -> CatalogDetail,
    ) {
        val itemId = placeholder.id
        val requestId = requestGeneration.incrementAndGet()
        mutableUi.value =
            mutableUi.value.copy(
                detail = placeholder,
                showingPlaylist = false,
                playlistId = null,
                playlistTitle = null,
                playlistSongs = emptyList(),
                loading = true,
                error = null,
            )

        scope.launch {
            runCatching { loader() }
                .onSuccess { detail ->
                    if (isCurrent(requestId) && detail.id == itemId) {
                        mutableUi.value =
                            mutableUi.value.copy(
                                detail = detail,
                                loading = false,
                                error = null,
                            )
                    }
                }
                .onFailure { error ->
                    if (isCurrent(requestId)) setError(error)
                }
        }
    }

    fun closeDetail() {
        requestGeneration.incrementAndGet()
        mutableUi.value =
            mutableUi.value.copy(
                detail = null,
                loading = false,
                error = null,
            )
    }

    fun closePlaylist() {
        requestGeneration.incrementAndGet()
        mutableUi.value =
            mutableUi.value.copy(
                showingPlaylist = false,
                playlistId = null,
                playlistTitle = null,
                playlistSongs = emptyList(),
                loading = false,
                error = null,
            )
    }

    private fun launchLoad(block: suspend (Long) -> Unit) {
        val requestId = requestGeneration.incrementAndGet()
        mutableUi.value = mutableUi.value.copy(loading = true, error = null)

        scope.launch {
            runCatching {
                block(requestId)
            }.onFailure { error ->
                if (isCurrent(requestId)) setError(error)
            }
        }
    }

    private fun sameEndpoint(
        current: BrowseEndpoint?,
        expected: BrowseEndpoint,
    ): Boolean =
        current?.browseId == expected.browseId && current.params == expected.params

    private fun isCurrent(requestId: Long): Boolean =
        requestGeneration.get() == requestId

    private fun setError(error: Throwable) {
        mutableUi.value =
            mutableUi.value.copy(
                loading = false,
                error = error.message ?: error::class.simpleName ?: "Unknown error",
            )
    }

    override fun close() {
        mutableUi.value =
            mutableUi.value.copy(
                loading = false,
                exploreLoading = false,
                exploreDetailLoading = false,
            )
        scope.cancel()
    }
}
