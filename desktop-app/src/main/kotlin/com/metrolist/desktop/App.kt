package com.metrolist.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.metrolist.desktop.offline.OfflineRecord
import com.metrolist.desktop.offline.OfflineState
import com.metrolist.desktop.player.PlayerState
import com.metrolist.desktop.player.RepeatMode
import com.metrolist.desktop.portable.InsufficientExportSpaceException
import com.metrolist.desktop.portable.PortableExportEstimate
import com.metrolist.desktop.portable.PortableExportPhase
import com.metrolist.desktop.portable.PortableExportProgress
import com.metrolist.desktop.portable.PortableValidationException
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage

@Composable
fun MetroListDesktopApp(
    runtime: DesktopRuntime,
) {
    val appState = remember { DesktopAppState(MetroListCatalogGateway()) }
    val playlistWorkspace = remember { PlaylistWorkspaceController(runtime) }
    val ui by appState.ui.collectAsState()
    val playlistUi by playlistWorkspace.state.collectAsState()
    val account by runtime.account.collectAsState()
    val downloads by runtime.downloads.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var actionMessage by remember { mutableStateOf<String?>(null) }
    var nowPlayingUiState by remember { mutableStateOf(NowPlayingUiState()) }
    var textInputFocused by remember { mutableStateOf(false) }
    var searchFocusRequestKey by remember { mutableStateOf(0) }
    var previousDownloadStates by remember {
        mutableStateOf<Map<String, OfflineState>>(emptyMap())
    }
    val downloadsById = downloads.associateBy(OfflineRecord::videoId)
    val enqueueSong: (SongItem) -> Unit = { song ->
        val existing = downloadsById[song.id]
        runCatching {
            runtime.enqueue(song)
        }.onSuccess {
            actionMessage =
                when (existing?.state) {
                    OfflineState.COMPLETED -> "Already downloaded: " + song.title
                    OfflineState.DOWNLOADING -> "Already downloading: " + song.title
                    OfflineState.QUEUED -> "Already queued: " + song.title
                    OfflineState.FAILED -> "Retrying download: " + song.title
                    null -> "Added to download queue: " + song.title
                }
        }.onFailure {
            actionMessage = it.message ?: "Could not queue download"
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            playlistWorkspace.close()
            appState.close()
        }
    }

    LaunchedEffect(Unit) {
        runtime.initializeSession()
    }

    LaunchedEffect(account.signedIn) {
        if (account.signedIn) {
            appState.loadInitial()
            playlistWorkspace.refreshLocalPlaylists()
        }
    }

    LaunchedEffect(actionMessage, account.signedIn) {
        val message = actionMessage?.takeIf(String::isNotBlank)
        if (account.signedIn && message != null) {
            snackbarHostState.showSnackbar(message)
            if (actionMessage == message) actionMessage = null
        }
    }

    LaunchedEffect(downloads) {
        downloads.forEach { record ->
            when {
                previousDownloadStates[record.videoId] != OfflineState.DOWNLOADING &&
                    record.state == OfflineState.DOWNLOADING ->
                    actionMessage = "Download started: " + record.title

                previousDownloadStates[record.videoId] != OfflineState.COMPLETED &&
                    previousDownloadStates.containsKey(record.videoId) &&
                    record.state == OfflineState.COMPLETED -> {
                    actionMessage = "Download completed: " + record.title
                    launch(Dispatchers.IO) {
                        runtime.ensureArtwork(record.thumbnail)
                    }
                }

                previousDownloadStates[record.videoId] != OfflineState.FAILED &&
                    previousDownloadStates.containsKey(record.videoId) &&
                    record.state == OfflineState.FAILED ->
                    actionMessage =
                        "Download failed: " + record.title +
                            record.lastError?.let { " · " + it }.orEmpty()
            }
        }
        previousDownloadStates = downloads.associate { it.videoId to it.state }
    }

    MetroListTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            if (account.loading) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(48.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "MetroList Windows",
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Checking your YouTube Music session...")
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(Modifier.width(280.dp))
                }
            } else if (!account.signedIn) {
                LoginPanel(
                    accountError = account.error,
                    onLogin = {
                        scope.launch {
                            actionMessage = "Opening YouTube Music sign-in..."
                            runCatching {
                                runtime.loginInteractive()
                            }.onSuccess {
                                actionMessage = "Signed in."
                                appState.loadInitial()
                            }.onFailure {
                                actionMessage = it.message ?: "Sign-in failed"
                            }
                        }
                    },
                    onAdvancedSession = { sessionText ->
                        scope.launch {
                            actionMessage = "Validating Advanced Session..."
                            runCatching {
                                runtime.loginAdvanced(sessionText)
                            }.onSuccess {
                                actionMessage = "Signed in with Advanced Session."
                                appState.loadInitial()
                            }.onFailure {
                                actionMessage = "Advanced Session sign-in failed."
                            }
                        }
                    },
                    message = actionMessage,
                )
            } else {
                CompositionLocalProvider(
                    LocalTextInputFocusReporter provides { focused ->
                        textInputFocused = focused
                    },
                ) {
                    Scaffold(
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        modifier =
                            Modifier.onKeyEvent { keyEvent ->
                                val shortcut =
                                    resolveShortcut(
                                        event = keyEvent.toDesktopShortcutEvent(),
                                        textInputFocused = textInputFocused,
                                    ) ?: return@onKeyEvent false
                                val playerSnapshot = runtime.playerState?.value

                                when (shortcut) {
                                    DesktopShortcut.PlayPause -> {
                                        if (playerSnapshot?.currentSong == null) {
                                            false
                                        } else {
                                            if (playerSnapshot.isPlaying) runtime.pause() else runtime.resume()
                                            true
                                        }
                                    }

                                    is DesktopShortcut.SeekBy -> {
                                        if (playerSnapshot?.currentSong == null) {
                                            false
                                        } else {
                                            runtime.seek(
                                                applySeekShortcut(
                                                    positionMs = playerSnapshot.positionMs,
                                                    durationMs = playerSnapshot.durationMs,
                                                    deltaMs = shortcut.deltaMs,
                                                ),
                                            )
                                            true
                                        }
                                    }

                                    is DesktopShortcut.VolumeBy -> {
                                        if (playerSnapshot == null) {
                                            false
                                        } else {
                                            runtime.setVolume(
                                                applyVolumeShortcut(
                                                    volume = playerSnapshot.volume,
                                                    delta = shortcut.delta,
                                                ),
                                            )
                                            true
                                        }
                                    }

                                    DesktopShortcut.FocusSearch -> {
                                        searchFocusRequestKey += 1
                                        nowPlayingUiState = nowPlayingUiState.copy(expanded = false)
                                        when {
                                            ui.destination == AppDestination.DOWNLOADS -> Unit
                                            ui.destination == AppDestination.LIBRARY &&
                                                playlistUi.screen == PlaylistWorkspaceScreen.LOCAL_PLAYLIST -> Unit

                                            else -> {
                                                if (playlistUi.screen != PlaylistWorkspaceScreen.CLOSED) {
                                                    playlistWorkspace.closeWorkspace()
                                                }
                                                appState.navigate(AppDestination.SEARCH)
                                            }
                                        }
                                        true
                                    }

                                    DesktopShortcut.Escape ->
                                        when {
                                            nowPlayingUiState.expanded -> {
                                                nowPlayingUiState = nowPlayingUiState.copy(expanded = false)
                                                true
                                            }

                                            playlistUi.screen != PlaylistWorkspaceScreen.CLOSED -> {
                                                playlistWorkspace.closeWorkspace()
                                                playlistWorkspace.refreshLocalPlaylists()
                                                true
                                            }

                                            ui.detail != null -> {
                                                appState.closeDetail()
                                                true
                                            }

                                            ui.showingPlaylist -> {
                                                appState.closePlaylist()
                                                true
                                            }

                                            else -> false
                                        }
                                }
                            },
                        topBar = {
                            DownloadActivityBar(
                                downloads = downloads,
                                onViewDownloads = {
                                    nowPlayingUiState = nowPlayingUiState.copy(expanded = false)
                                    appState.navigate(AppDestination.DOWNLOADS)
                                },
                            )
                        },
                    bottomBar = {
                        if (shouldShowMiniPlayer(nowPlayingUiState)) {
                            PlayerBar(
                                runtime = runtime,
                                onExpand = { queueVisible ->
                                    nowPlayingUiState =
                                        nowPlayingUiState.copy(
                                            expanded = true,
                                            queueVisible = queueVisible,
                                        )
                                },
                            )
                        }
                    },
                ) { padding ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(padding),
                    ) {
                        NavigationPanel(
                            accountLabel =
                                account.name
                                    ?: account.email
                                    ?: "YouTube Music",
                            destination = ui.destination,
                            onNavigate = { destination ->
                                nowPlayingUiState = nowPlayingUiState.copy(expanded = false)
                                if (destination != AppDestination.LIBRARY) {
                                    playlistWorkspace.closeWorkspace()
                                }
                                appState.navigate(destination)
                            },
                            onLogout = {
                                nowPlayingUiState = nowPlayingUiState.copy(expanded = false)
                                playlistWorkspace.closeWorkspace()
                                runtime.logout()
                            },
                        )

                        Box(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(horizontal = 24.dp, vertical = 20.dp),
                        ) {
                            if (nowPlayingUiState.expanded) {
                                ExpandedNowPlaying(
                                    runtime = runtime,
                                    uiState = nowPlayingUiState,
                                    onUiStateChange = { nowPlayingUiState = it },
                                )
                            } else {
                                when {
                                ui.detail != null -> {
                                    val detail = requireNotNull(ui.detail)
                                    val detailSongs =
                                        detail.sections
                                            .flatMap(CatalogDetailSection::items)
                                            .filterIsInstance<SongItem>()
                                            .distinctBy(SongItem::id)
                                    CatalogDetailView(
                                        runtime = runtime,
                                        detail = detail,
                                        downloadsById = downloadsById,
                                        loading = ui.loading,
                                        error = ui.error,
                                        onBack = appState::closeDetail,
                                        onOpenPlaylist = appState::openPlaylist,
                                        onOpenAlbum = appState::openAlbum,
                                        onOpenArtist = appState::openArtist,
                                        onPlay = { song ->
                                            scope.launch {
                                                val startIndex =
                                                    detailSongs.indexOfFirst { it.id == song.id }
                                                runCatching {
                                                    if (startIndex >= 0) {
                                                        runtime.playQueue(detailSongs, startIndex)
                                                    } else {
                                                        runtime.play(song)
                                                    }
                                                }.onFailure {
                                                    actionMessage = it.message ?: "Playback failed"
                                                }
                                            }
                                        },
                                        onDownload = enqueueSong,
                                    )
                                }

                                ui.showingPlaylist -> {
                                    PlaylistView(
                                    runtime = runtime,
                                    title = ui.playlistTitle.orEmpty(),
                                    songs = ui.playlistSongs,
                                    downloadsById = downloadsById,
                                    loading = ui.loading,
                                    error = ui.error,
                                    onBack = appState::closePlaylist,
                                    onPlay = { song ->
                                        scope.launch {
                                            val startIndex =
                                                ui.playlistSongs.indexOfFirst { it.id == song.id }
                                            runCatching {
                                                if (startIndex >= 0) {
                                                    runtime.playQueue(ui.playlistSongs, startIndex)
                                                } else {
                                                    runtime.play(song)
                                                }
                                            }.onFailure {
                                                actionMessage = it.message ?: "Playback failed"
                                            }
                                        }
                                    },
                                    onDownload = enqueueSong,
                                    onDownloadAll = {
                                        ui.playlistId?.let { playlistId ->
                                            actionMessage = "Loading playlist and preparing downloads..."
                                            scope.launch {
                                                runCatching {
                                                    runtime.enqueuePlaylist(playlistId)
                                                }.onSuccess { count ->
                                                    actionMessage = "Queued $count songs."
                                                }.onFailure {
                                                    actionMessage = it.message ?: "Playlist download failed"
                                                }
                                            }
                                        }
                                    },
                                    onCancelBatch = {
                                        val ids = ui.playlistSongs.map(SongItem::id).toSet()
                                        scope.launch {
                                            val count =
                                                withContext(Dispatchers.IO) {
                                                    runtime.cancelPending(ids)
                                                }
                                            actionMessage =
                                                if (count == 1) {
                                                    "Cancelled 1 playlist download."
                                                } else {
                                                    "Cancelled $count playlist downloads."
                                                }
                                        }
                                    },
                                    onRetryFailed = {
                                        val ids = ui.playlistSongs.map(SongItem::id).toSet()
                                        scope.launch {
                                            val count =
                                                withContext(Dispatchers.IO) {
                                                    runtime.retryFailed(ids)
                                                }
                                            actionMessage =
                                                if (count == 1) {
                                                    "Retry queued for 1 failed song."
                                                } else {
                                                    "Retry queued for $count failed songs."
                                                }
                                        }
                                    },
                                    onRemoveDownloaded = {
                                        val ids = ui.playlistSongs.map(SongItem::id).toSet()
                                        scope.launch {
                                            val count =
                                                withContext(Dispatchers.IO) {
                                                    runtime.removeCompleted(ids)
                                                }
                                            actionMessage =
                                                if (count == 1) {
                                                    "Removed 1 downloaded song from this playlist."
                                                } else {
                                                    "Removed $count downloaded songs from this playlist."
                                                }
                                        }
                                    },
                                )
                                }

                                ui.destination == AppDestination.LIBRARY &&
                                    (playlistUi.screen == PlaylistWorkspaceScreen.IMPORT_EDIT ||
                                        playlistUi.screen == PlaylistWorkspaceScreen.IMPORT_REVIEW) -> {
                                    PlaylistImportScreen(
                                        state = playlistUi,
                                        onBack = {
                                            playlistWorkspace.closeWorkspace()
                                            playlistWorkspace.refreshLocalPlaylists()
                                        },
                                        onPlaylistNameChange = playlistWorkspace::setPlaylistName,
                                        onSourceTextChange = playlistWorkspace::setSourceText,
                                        onChooseTxt = {
                                            val selected = choosePlaylistTextFile()
                                            if (selected != null) {
                                                scope.launch {
                                                    runCatching {
                                                        withContext(Dispatchers.IO) {
                                                            Files.readString(selected)
                                                        }
                                                    }.onSuccess { text ->
                                                        playlistWorkspace.setSourceText(text)
                                                        actionMessage = "Loaded " + selected.fileName
                                                    }.onFailure {
                                                        actionMessage = it.message ?: "Could not read TXT file"
                                                    }
                                                }
                                            }
                                        },
                                        onStartMatching = playlistWorkspace::startImport,
                                        onDestinationChange = playlistWorkspace::setDestination,
                                        onFilterChange = playlistWorkspace::setRowFilter,
                                        onChooseCandidate = playlistWorkspace::selectCandidate,
                                        onRetryRow = playlistWorkspace::retryRow,
                                        onFinalize = playlistWorkspace::finalizeImport,
                                    )
                                }

                                ui.destination == AppDestination.LIBRARY &&
                                    playlistUi.screen == PlaylistWorkspaceScreen.LOCAL_PLAYLIST -> {
                                    LocalPlaylistScreen(
                                        runtime = runtime,
                                        state = playlistUi,
                                        downloadsById = downloadsById,
                                        onBack = {
                                            playlistWorkspace.closeWorkspace()
                                            playlistWorkspace.refreshLocalPlaylists()
                                        },
                                        onPlayAll = {
                                            val songs = playlistUi.localPlaylistSongs
                                            if (songs.isNotEmpty()) {
                                                scope.launch {
                                                    runCatching { runtime.playQueue(songs, 0) }
                                                        .onFailure { actionMessage = it.message ?: "Playback failed" }
                                                }
                                            }
                                        },
                                        onShuffle = {
                                            val songs = playlistUi.localPlaylistSongs.shuffled()
                                            if (songs.isNotEmpty()) {
                                                scope.launch {
                                                    runCatching { runtime.playQueue(songs, 0) }
                                                        .onFailure { actionMessage = it.message ?: "Playback failed" }
                                                }
                                            }
                                        },
                                        onPlayAt = { position ->
                                            val songs = playlistUi.localPlaylistSongs
                                            if (position in songs.indices) {
                                                scope.launch {
                                                    runCatching { runtime.playQueue(songs, position) }
                                                        .onFailure { actionMessage = it.message ?: "Playback failed" }
                                                }
                                            }
                                        },
                                        onDownload = enqueueSong,
                                        onDownloadAll = {
                                            playlistUi.localPlaylistSongs.forEach(enqueueSong)
                                            actionMessage =
                                                "Queued " + playlistUi.localPlaylistSongs.size +
                                                    " local-playlist songs."
                                        },
                                        onSync = playlistWorkspace::syncSelectedLocalPlaylist,
                                        onSearchChange = playlistWorkspace::setLocalSearchQuery,
                                        onRename = playlistWorkspace::renameLocalPlaylist,
                                        onMove = playlistWorkspace::moveLocalPlaylistTrack,
                                        onRemove = playlistWorkspace::removeLocalPlaylistTrack,
                                        onDelete = playlistWorkspace::deleteSelectedLocalPlaylist,
                                        focusSearchRequestKey = searchFocusRequestKey,
                                    )
                                }

                                else -> {
                                    when (ui.destination) {
                                    AppDestination.HOME ->
                                        HomeView(
                                            runtime = runtime,
                                            ui = ui,
                                            downloadsById = downloadsById,
                                            onOpenPlaylist = appState::openPlaylist,
                                            onOpenAlbum = appState::openAlbum,
                                            onOpenArtist = appState::openArtist,
                                            onPlay = { song ->
                                                scope.launch {
                                                    runCatching { runtime.play(song) }
                                                        .onFailure {
                                                            actionMessage = it.message ?: "Playback failed"
                                                        }
                                                }
                                            },
                                            onDownload = enqueueSong,
                                        )

                                    AppDestination.EXPLORE -> {
                                        val exploreSongs =
                                            ui.exploreDetailSections
                                                .flatMap(CatalogDetailSection::items)
                                                .filterIsInstance<SongItem>()
                                                .distinctBy(SongItem::id)
                                        ExploreView(
                                            runtime = runtime,
                                            ui = ui,
                                            downloadsById = downloadsById,
                                            onRefresh = appState::refreshExplore,
                                            onSelectMood = appState::openExploreMood,
                                            onCloseMood = appState::closeExploreMood,
                                            onOpenPlaylist = appState::openPlaylist,
                                            onOpenAlbum = appState::openAlbum,
                                            onOpenArtist = appState::openArtist,
                                            onPlay = { song ->
                                                scope.launch {
                                                    val startIndex =
                                                        exploreSongs.indexOfFirst { it.id == song.id }
                                                    runCatching {
                                                        if (startIndex >= 0) {
                                                            runtime.playQueue(exploreSongs, startIndex)
                                                        } else {
                                                            runtime.play(song)
                                                        }
                                                    }.onFailure {
                                                        actionMessage = it.message ?: "Playback failed"
                                                    }
                                                }
                                            },
                                            onDownload = enqueueSong,
                                        )
                                    }

                                    AppDestination.SEARCH ->
                                        SearchView(
                                            runtime = runtime,
                                            ui = ui,
                                            downloadsById = downloadsById,
                                            onSearch = appState::search,
                                            onOpenPlaylist = appState::openPlaylist,
                                            onOpenAlbum = appState::openAlbum,
                                            onOpenArtist = appState::openArtist,
                                            onPlay = { song ->
                                                scope.launch {
                                                    val startIndex =
                                                        ui.searchResults.indexOfFirst { it.id == song.id }
                                                    runCatching {
                                                        if (startIndex >= 0) {
                                                            runtime.playQueue(ui.searchResults, startIndex)
                                                        } else {
                                                            runtime.play(song)
                                                        }
                                                    }.onFailure {
                                                        actionMessage = it.message ?: "Playback failed"
                                                    }
                                                }
                                            },
                                            onDownload = enqueueSong,
                                            focusSearchRequestKey = searchFocusRequestKey,
                                        )

                                    AppDestination.LIBRARY ->
                                        LibraryView(
                                            runtime = runtime,
                                            ui = ui,
                                            downloadsById = downloadsById,
                                            localPlaylists = playlistUi.localPlaylists,
                                            onCreateImport = {
                                                appState.navigate(AppDestination.LIBRARY)
                                                playlistWorkspace.openImport()
                                            },
                                            onOpenLocalPlaylist = { playlist ->
                                                appState.navigate(AppDestination.LIBRARY)
                                                playlistWorkspace.openLocalPlaylist(playlist.id)
                                            },
                                            onRefresh = {
                                                appState.refreshLibrary()
                                                playlistWorkspace.refreshLocalPlaylists()
                                            },
                                            onSelectTab = appState::selectLibraryTab,
                                            onOpenPlaylist = appState::openPlaylist,
                                            onOpenAlbum = appState::openAlbum,
                                            onOpenArtist = appState::openArtist,
                                            onPlay = { song ->
                                                scope.launch {
                                                    val startIndex =
                                                        ui.librarySongs.indexOfFirst { it.id == song.id }
                                                    runCatching {
                                                        if (startIndex >= 0) {
                                                            runtime.playQueue(ui.librarySongs, startIndex)
                                                        } else {
                                                            runtime.play(song)
                                                        }
                                                    }.onFailure {
                                                        actionMessage = it.message ?: "Playback failed"
                                                    }
                                                }
                                            },
                                            onDownload = enqueueSong,
                                            onDownloadPlaylist = { playlist ->
                                                actionMessage =
                                                    "Loading playlist: " + playlist.title + "..."
                                                scope.launch {
                                                    runCatching {
                                                        runtime.enqueuePlaylist(playlist.id)
                                                    }.onSuccess { count ->
                                                        actionMessage = "Queued $count songs."
                                                    }.onFailure {
                                                        actionMessage = it.message ?: "Playlist download failed"
                                                    }
                                                }
                                            },
                                        )

                                    AppDestination.DOWNLOADS ->
                                        DownloadsView(
                                            runtime = runtime,
                                            downloads = downloads,
                                            onRetry = { videoId ->
                                                val title =
                                                    downloadsById[videoId]?.title ?: videoId
                                                runCatching {
                                                    runtime.retry(videoId)
                                                }.onSuccess {
                                                    actionMessage = "Retry queued: " + title
                                                }.onFailure {
                                                    actionMessage =
                                                        it.message ?: "Could not retry download"
                                                }
                                            },
                                            onRemove = { videoId ->
                                                val record = downloadsById[videoId]
                                                val title = record?.title ?: videoId
                                                runCatching {
                                                    if (
                                                        record?.state == OfflineState.QUEUED ||
                                                        record?.state == OfflineState.DOWNLOADING
                                                    ) {
                                                        runtime.cancelPending(setOf(videoId))
                                                    } else {
                                                        runtime.remove(videoId)
                                                    }
                                                }.onSuccess {
                                                    actionMessage =
                                                        if (
                                                            record?.state == OfflineState.QUEUED ||
                                                            record?.state == OfflineState.DOWNLOADING
                                                        ) {
                                                            "Cancelled download: " + title
                                                        } else {
                                                            "Removed download: " + title
                                                        }
                                                }.onFailure {
                                                    actionMessage =
                                                        it.message ?: "Could not update download"
                                                }
                                            },
                                            onRetryAllFailed = {
                                                scope.launch {
                                                    val count =
                                                        withContext(Dispatchers.IO) {
                                                            runtime.retryFailed()
                                                        }
                                                    actionMessage =
                                                        if (count == 1) {
                                                            "Retry queued for 1 failed download."
                                                        } else {
                                                            "Retry queued for $count failed downloads."
                                                        }
                                                }
                                            },
                                            onClearFailed = {
                                                scope.launch {
                                                    val count =
                                                        withContext(Dispatchers.IO) {
                                                            runtime.clearFailed()
                                                        }
                                                    actionMessage =
                                                        if (count == 1) {
                                                            "Cleared 1 failed download."
                                                        } else {
                                                            "Cleared $count failed downloads."
                                                        }
                                                }
                                            },
                                            onCancelPending = {
                                                val ids = downloads.map(OfflineRecord::videoId).toSet()
                                                scope.launch {
                                                    val count =
                                                        withContext(Dispatchers.IO) {
                                                            runtime.cancelPending(ids)
                                                        }
                                                    actionMessage =
                                                        if (count == 1) {
                                                            "Cancelled 1 pending download."
                                                        } else {
                                                            "Cancelled $count pending downloads."
                                                        }
                                                }
                                            },
                                            focusSearchRequestKey = searchFocusRequestKey,
                                            onRemoveCompleted = {
                                                scope.launch {
                                                    val count =
                                                        withContext(Dispatchers.IO) {
                                                            runtime.removeCompleted()
                                                        }
                                                    actionMessage =
                                                        if (count == 1) {
                                                            "Removed 1 offline download."
                                                        } else {
                                                            "Removed $count offline downloads."
                                                        }
                                                }
                                            },
                                        )
                                }
                            }
                            }

                                if (ui.loading) {
                                    LinearProgressIndicator(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .align(Alignment.TopCenter),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}
