package com.metrolist.desktop

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.metrolist.desktop.offline.OfflineRecord
import com.metrolist.desktop.offline.OfflineState
import com.metrolist.desktop.player.PlayerState
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MetroListDesktopApp(
    runtime: DesktopRuntime,
) {
    val appState = remember { DesktopAppState(MetroListCatalogGateway()) }
    val ui by appState.ui.collectAsState()
    val account by runtime.account.collectAsState()
    val downloads by runtime.downloads.collectAsState()
    val scope = rememberCoroutineScope()

    var actionMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            appState.close()
        }
    }

    LaunchedEffect(account.signedIn) {
        if (account.signedIn) {
            appState.loadInitial()
        }
    }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            if (!account.signedIn) {
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
                    message = actionMessage,
                )
            } else {
                Scaffold(
                    bottomBar = {
                        PlayerBar(runtime = runtime)
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
                            onNavigate = appState::navigate,
                            onLogout = runtime::logout,
                        )

                        HorizontalDivider(
                            modifier =
                                Modifier
                                    .fillMaxHeight()
                                    .width(1.dp),
                        )

                        Box(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(20.dp),
                        ) {
                            if (ui.showingPlaylist) {
                                PlaylistView(
                                    title = ui.playlistTitle.orEmpty(),
                                    songs = ui.playlistSongs,
                                    loading = ui.loading,
                                    error = ui.error,
                                    onBack = appState::closePlaylist,
                                    onPlay = { song ->
                                        scope.launch {
                                            runCatching { runtime.play(song) }
                                                .onFailure {
                                                    actionMessage = it.message ?: "Playback failed"
                                                }
                                        }
                                    },
                                    onDownload = runtime::enqueue,
                                    onDownloadAll = {
                                        ui.playlistId?.let { playlistId ->
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
                                )
                            } else {
                                when (ui.destination) {
                                    AppDestination.HOME ->
                                        HomeView(
                                            ui = ui,
                                            onOpenPlaylist = appState::openPlaylist,
                                            onPlay = { song ->
                                                scope.launch {
                                                    runCatching { runtime.play(song) }
                                                        .onFailure {
                                                            actionMessage = it.message ?: "Playback failed"
                                                        }
                                                }
                                            },
                                            onDownload = runtime::enqueue,
                                        )

                                    AppDestination.SEARCH ->
                                        SearchView(
                                            ui = ui,
                                            onSearch = appState::search,
                                            onPlay = { song ->
                                                scope.launch {
                                                    runCatching { runtime.play(song) }
                                                        .onFailure {
                                                            actionMessage = it.message ?: "Playback failed"
                                                        }
                                                }
                                            },
                                            onDownload = runtime::enqueue,
                                        )

                                    AppDestination.LIBRARY ->
                                        LibraryView(
                                            ui = ui,
                                            onRefresh = appState::refreshLibrary,
                                            onOpenPlaylist = appState::openPlaylist,
                                            onDownloadPlaylist = { playlist ->
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
                                            downloads = downloads,
                                            message = actionMessage,
                                            onRetry = runtime::retry,
                                            onRemove = runtime::remove,
                                            onExport = {
                                                val destination = chooseBackupDestination()
                                                if (destination != null) {
                                                    scope.launch {
                                                        actionMessage = "Exporting offline library..."
                                                        runCatching {
                                                            withContext(Dispatchers.IO) {
                                                                runtime.exportAll(destination)
                                                            }
                                                        }.onSuccess { path ->
                                                            actionMessage = "Backup ready: " + path
                                                        }.onFailure {
                                                            actionMessage = it.message ?: "Export failed"
                                                        }
                                                    }
                                                }
                                            },
                                        )
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

@Composable
private fun LoginPanel(
    accountError: String?,
    onLogin: () -> Unit,
    message: String?,
) {
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
        Text("Sign in to YouTube Music to browse, play, and download your library.")
        Spacer(Modifier.height(20.dp))
        Button(onClick = onLogin) {
            Text("Sign in with YouTube Music")
        }

        accountError?.let {
            Spacer(Modifier.height(12.dp))
            Text("Session error: $it")
        }
        message?.let {
            Spacer(Modifier.height(8.dp))
            Text(it)
        }
    }
}

@Composable
private fun NavigationPanel(
    accountLabel: String,
    destination: AppDestination,
    onNavigate: (AppDestination) -> Unit,
    onLogout: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .width(190.dp)
                .fillMaxHeight()
                .padding(14.dp),
    ) {
        Text(
            "MetroList",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            accountLabel,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(24.dp))

        AppDestination.entries.forEach { item ->
            val label =
                when (item) {
                    AppDestination.HOME -> "Home"
                    AppDestination.SEARCH -> "Search"
                    AppDestination.LIBRARY -> "Library"
                    AppDestination.DOWNLOADS -> "Downloads"
                }

            if (destination == item) {
                Button(
                    onClick = { onNavigate(item) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(label)
                }
            } else {
                OutlinedButton(
                    onClick = { onNavigate(item) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(label)
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.weight(1f))
        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Sign out")
        }
    }
}

@Composable
private fun HomeView(
    ui: DesktopUiState,
    onOpenPlaylist: (PlaylistItem) -> Unit,
    onPlay: (SongItem) -> Unit,
    onDownload: (SongItem) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("Home", style = MaterialTheme.typography.headlineMedium)
            ui.error?.let { Text("Error: $it") }
        }

        ui.homeSections.forEach { section ->
            item {
                Spacer(Modifier.height(8.dp))
                Text(section.title, style = MaterialTheme.typography.titleLarge)
            }

            items(section.items, key = { section.title + ":" + it.id }) { item ->
                CatalogItemRow(
                    item = item,
                    onOpenPlaylist = onOpenPlaylist,
                    onPlay = onPlay,
                    onDownload = onDownload,
                )
            }
        }
    }
}

@Composable
private fun SearchView(
    ui: DesktopUiState,
    onSearch: (String) -> Unit,
    onPlay: (SongItem) -> Unit,
    onDownload: (SongItem) -> Unit,
) {
    var query by remember(ui.searchQuery) { mutableStateOf(ui.searchQuery) }

    Column(Modifier.fillMaxSize()) {
        Text("Search", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Songs") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { onSearch(query) }) {
                Text("Search")
            }
        }

        ui.error?.let {
            Spacer(Modifier.height(8.dp))
            Text("Error: $it")
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(ui.searchResults, key = SongItem::id) { song ->
                SongRow(
                    song = song,
                    onPlay = { onPlay(song) },
                    onDownload = { onDownload(song) },
                )
            }
        }
    }
}

@Composable
private fun LibraryView(
    ui: DesktopUiState,
    onRefresh: () -> Unit,
    onOpenPlaylist: (PlaylistItem) -> Unit,
    onDownloadPlaylist: (PlaylistItem) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Library · Playlists",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onRefresh) {
                Text("Refresh")
            }
        }

        ui.error?.let {
            Spacer(Modifier.height(8.dp))
            Text("Error: $it")
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(ui.libraryPlaylists, key = PlaylistItem::id) { playlist ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(playlist.title, style = MaterialTheme.typography.titleMedium)
                        playlist.songCountText?.let { Text(it) }
                    }
                    OutlinedButton(onClick = { onOpenPlaylist(playlist) }) {
                        Text("Open")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onDownloadPlaylist(playlist) }) {
                        Text("Download")
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun PlaylistView(
    title: String,
    songs: List<SongItem>,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onPlay: (SongItem) -> Unit,
    onDownload: (SongItem) -> Unit,
    onDownloadAll: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onBack) {
                Text("Back")
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineMedium)
                Text("${songs.size} songs")
            }
            Button(
                onClick = onDownloadAll,
                enabled = !loading,
            ) {
                Text("Download all")
            }
        }

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text("Error: $it")
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(songs, key = SongItem::id) { song ->
                SongRow(
                    song = song,
                    onPlay = { onPlay(song) },
                    onDownload = { onDownload(song) },
                )
            }
        }
    }
}

@Composable
private fun CatalogItemRow(
    item: YTItem,
    onOpenPlaylist: (PlaylistItem) -> Unit,
    onPlay: (SongItem) -> Unit,
    onDownload: (SongItem) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium)
            Text(item::class.simpleName.orEmpty(), style = MaterialTheme.typography.bodySmall)
        }

        when (item) {
            is SongItem -> {
                OutlinedButton(onClick = { onPlay(item) }) {
                    Text("Play")
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onDownload(item) }) {
                    Text("Download")
                }
            }

            is PlaylistItem -> {
                OutlinedButton(onClick = { onOpenPlaylist(item) }) {
                    Text("Open")
                }
            }

            else -> Unit
        }
    }
    HorizontalDivider()
}

@Composable
private fun SongRow(
    song: SongItem,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.titleMedium)
            Text(
                song.artists.joinToString(", ") { it.name }.ifBlank { "YouTube Music" },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        OutlinedButton(onClick = onPlay) {
            Text("Play")
        }
        Spacer(Modifier.width(8.dp))
        Button(onClick = onDownload) {
            Text("Download")
        }
    }
    HorizontalDivider()
}

@Composable
private fun DownloadsView(
    downloads: List<OfflineRecord>,
    message: String?,
    onRetry: (String) -> Unit,
    onRemove: (String) -> Unit,
    onExport: () -> Unit,
) {
    val completed = downloads.count { it.state == OfflineState.COMPLETED }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Downloads", style = MaterialTheme.typography.headlineMedium)
                Text("$completed available offline · ${downloads.size} total")
            }
            Button(
                onClick = onExport,
                enabled = completed > 0,
            ) {
                Text("Export all offline downloads")
            }
        }

        message?.let {
            Spacer(Modifier.height(8.dp))
            Text(it)
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(downloads, key = OfflineRecord::videoId) { record ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(record.title, style = MaterialTheme.typography.titleMedium)
                        Text(record.state.name)
                        if (record.state == OfflineState.COMPLETED) {
                            record.contentLength?.let { length ->
                                Text(formatBytes(length))
                            }
                        }
                        record.lastError?.let { Text("Error: $it") }
                    }

                    if (record.state == OfflineState.FAILED) {
                        OutlinedButton(onClick = { onRetry(record.videoId) }) {
                            Text("Retry")
                        }
                        Spacer(Modifier.width(8.dp))
                    }

                    OutlinedButton(onClick = { onRemove(record.videoId) }) {
                        Text("Remove")
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun PlayerBar(
    runtime: DesktopRuntime,
) {
    val playerFlow = runtime.playerState
    if (playerFlow == null) {
        Surface {
            Text(
                "Player runtime unavailable",
                modifier = Modifier.padding(12.dp),
            )
        }
        return
    }

    val state by playerFlow.collectAsState()

    Surface {
        if (state.currentSong == null) {
            Text(
                "Nothing playing",
                modifier = Modifier.padding(12.dp),
            )
        } else {
            PlayerControls(
                state = state,
                onPause = runtime::pause,
                onResume = runtime::resume,
                onSeek = runtime::seek,
                onVolume = runtime::setVolume,
            )
        }
    }
}

@Composable
private fun PlayerControls(
    state: PlayerState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSeek: (Long) -> Unit,
    onVolume: (Int) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(220.dp)) {
            Text(
                state.currentSong?.title.orEmpty(),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                state.currentSong
                    ?.artists
                    ?.joinToString(", ") { it.name }
                    .orEmpty(),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Button(
            onClick = if (state.isPlaying) onPause else onResume,
        ) {
            Text(if (state.isPlaying) "Pause" else "Play")
        }

        Spacer(Modifier.width(12.dp))

        val duration = state.durationMs?.coerceAtLeast(1L) ?: 1L
        Slider(
            value = state.positionMs.coerceIn(0L, duration).toFloat(),
            onValueChange = { onSeek(it.toLong()) },
            valueRange = 0f..duration.toFloat(),
            modifier = Modifier.weight(1f),
        )

        Spacer(Modifier.width(12.dp))

        Text("${state.volume}%")
        Slider(
            value = state.volume.toFloat(),
            onValueChange = { onVolume(it.toInt()) },
            valueRange = 0f..100f,
            modifier = Modifier.width(120.dp),
        )
    }
}

private fun chooseBackupDestination(): Path? {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm")
    val defaultName =
        "Metrolist_Offline_Downloads_" +
            LocalDateTime.now().format(formatter) +
            ".metrolistbackup"

    val dialog =
        FileDialog(
            null as Frame?,
            "Export Offline Downloads",
            FileDialog.SAVE,
        )
    dialog.file = defaultName
    dialog.isVisible = true

    val directory = dialog.directory ?: return null
    val file = dialog.file ?: return null
    return Path.of(directory, file)
}

private fun formatBytes(bytes: Long): String =
    when {
        bytes >= 1_073_741_824L -> String.format("%.2f GB", bytes / 1_073_741_824.0)
        bytes >= 1_048_576L -> String.format("%.1f MB", bytes / 1_048_576.0)
        bytes >= 1_024L -> String.format("%.1f KB", bytes / 1_024.0)
        else -> "$bytes B"
    }
