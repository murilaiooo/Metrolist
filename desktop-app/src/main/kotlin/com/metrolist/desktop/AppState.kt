package com.metrolist.desktop

import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.pages.HomePage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AppDestination {
    HOME,
    SEARCH,
    LIBRARY,
    DOWNLOADS,
}

data class DesktopUiState(
    val destination: AppDestination = AppDestination.HOME,
    val loading: Boolean = false,
    val error: String? = null,
    val homeSections: List<HomePage.Section> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<SongItem> = emptyList(),
    val libraryPlaylists: List<PlaylistItem> = emptyList(),
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
    val ui: StateFlow<DesktopUiState> = mutableUi.asStateFlow()

    fun loadInitial() {
        refreshHome()
    }

    fun navigate(destination: AppDestination) {
        mutableUi.value =
            mutableUi.value.copy(
                destination = destination,
                showingPlaylist = false,
                playlistId = null,
                playlistTitle = null,
                playlistSongs = emptyList(),
                error = null,
            )

        when (destination) {
            AppDestination.HOME -> refreshHome()
            AppDestination.LIBRARY -> refreshLibrary()
            AppDestination.SEARCH,
            AppDestination.DOWNLOADS,
            -> Unit
        }
    }

    fun refreshHome() {
        launchLoad {
            val sections = catalog.homeSections()
            mutableUi.value =
                mutableUi.value.copy(
                    homeSections = sections,
                    loading = false,
                    error = null,
                )
        }
    }

    fun search(query: String) {
        val normalized = query.trim()
        mutableUi.value =
            mutableUi.value.copy(
                destination = AppDestination.SEARCH,
                searchQuery = query,
                showingPlaylist = false,
                playlistSongs = emptyList(),
                error = null,
            )

        if (normalized.isEmpty()) {
            mutableUi.value =
                mutableUi.value.copy(
                    searchResults = emptyList(),
                    loading = false,
                )
            return
        }

        launchLoad {
            val results = catalog.searchSongs(normalized)
            mutableUi.value =
                mutableUi.value.copy(
                    searchResults = results,
                    loading = false,
                    error = null,
                )
        }
    }

    fun refreshLibrary() {
        launchLoad {
            val playlists = catalog.libraryPlaylists()
            mutableUi.value =
                mutableUi.value.copy(
                    libraryPlaylists = playlists,
                    loading = false,
                    error = null,
                )
        }
    }

    fun openPlaylist(playlist: PlaylistItem) {
        mutableUi.value =
            mutableUi.value.copy(
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
                mutableUi.value =
                    mutableUi.value.copy(
                        playlistSongs = songs,
                        loading = false,
                        error = null,
                    )
            }.onFailure(::setError)
        }
    }

    fun closePlaylist() {
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

    private fun launchLoad(block: suspend () -> Unit) {
        mutableUi.value = mutableUi.value.copy(loading = true, error = null)

        scope.launch {
            runCatching {
                block()
            }.onFailure(::setError)
        }
    }

    private fun setError(error: Throwable) {
        mutableUi.value =
            mutableUi.value.copy(
                loading = false,
                error = error.message ?: error::class.simpleName ?: "Unknown error",
            )
    }

    override fun close() {
        mutableUi.value = mutableUi.value.copy(loading = false)
        scope.cancel()
    }
}
