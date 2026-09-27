package com.metrolist.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.metrolist.desktop.library.PlaylistSyncState
import com.metrolist.desktop.offline.OfflineRecord
import com.metrolist.innertube.models.SongItem

@Composable
internal fun LocalPlaylistScreen(
    runtime: DesktopRuntime,
    state: PlaylistWorkspaceState,
    downloadsById: Map<String, OfflineRecord>,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onPlayAt: (Int) -> Unit,
    onDownload: (SongItem) -> Unit,
    onDownloadAll: () -> Unit,
    onSync: () -> Unit,
    onSearchChange: (String) -> Unit,
    onRename: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    onDelete: () -> Unit,
    focusSearchRequestKey: Int = 0,
) {
    val playlist = state.selectedLocalPlaylist ?: return
    val searchFocusRequester = remember { FocusRequester() }
    var renameText by remember(playlist.id, playlist.name) { mutableStateOf(playlist.name) }

    LaunchedEffect(focusSearchRequestKey) {
        if (focusSearchRequestKey > 0) searchFocusRequester.requestFocus()
    }
    var confirmDelete by remember(playlist.id) { mutableStateOf(false) }
    val query = state.localSearchQuery.trim()
    val visible =
        state.localPlaylistSongs.withIndex().filter { indexed ->
            query.isEmpty() ||
                sequenceOf(
                    indexed.value.title,
                    indexed.value.artists.joinToString(" ") { it.name },
                    indexed.value.album?.name.orEmpty(),
                ).any { it.contains(query, ignoreCase = true) }
        }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, enabled = !state.busy) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(playlist.name, style = MaterialTheme.typography.headlineLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${state.localPlaylistSongs.size} tracks",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color =
                            if (playlist.youtubePlaylistId == null) {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            },
                    ) {
                        Text(
                            text =
                                when (playlist.syncState) {
                                    PlaylistSyncState.LOCAL_ONLY -> "Local"
                                    PlaylistSyncState.LINKED -> "YouTube linked"
                                    PlaylistSyncState.PARTIAL -> "YouTube partial"
                                    PlaylistSyncState.SYNCED -> "YouTube synced"
                                },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color =
                                if (playlist.youtubePlaylistId == null) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                },
                        )
                    }
                }
            }
        }

        state.syncProgress?.let { sync ->
            val fraction = if (sync.total == 0) 1f else sync.completed.toFloat() / sync.total.toFloat()
            LinearProgressIndicator(
                progress = { fraction.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Syncing ${sync.completed} / ${sync.total} · ${sync.succeeded} added · ${sync.failed} failed",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = onPlayAll, enabled = state.localPlaylistSongs.isNotEmpty()) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Play")
            }
            TextButton(onClick = onShuffle, enabled = state.localPlaylistSongs.isNotEmpty()) {
                Icon(Icons.Rounded.Shuffle, contentDescription = null)
                Spacer(Modifier.width(5.dp))
                Text("Shuffle")
            }
            TextButton(onClick = onDownloadAll, enabled = state.localPlaylistSongs.isNotEmpty()) {
                Icon(Icons.Rounded.Download, contentDescription = null)
                Spacer(Modifier.width(5.dp))
                Text("Download all")
            }
            if (playlist.syncState != PlaylistSyncState.SYNCED) {
                TextButton(onClick = onSync, enabled = !state.busy) {
                    Text(if (playlist.youtubePlaylistId == null) "Sync to YouTube Music" else "Retry sync")
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = renameText,
                onValueChange = { renameText = it },
                label = { Text("Playlist name") },
                modifier = Modifier.weight(1f).trackTextInputFocus(),
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
            )
            TextButton(
                onClick = { onRename(renameText.trim()) },
                enabled = renameText.isNotBlank() && renameText.trim() != playlist.name,
            ) {
                Text("Rename")
            }
            IconButton(onClick = { confirmDelete = true }, enabled = !state.busy) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete playlist")
            }
        }

        OutlinedTextField(
            value = state.localSearchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search in playlist") },
            leadingIcon = {
                Icon(Icons.Rounded.Search, contentDescription = null)
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(searchFocusRequester)
                    .trackTextInputFocus(),
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
        )

        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                items = visible,
                key = { "${it.index}:${it.value.id}" },
            ) { indexed ->
                val position = indexed.index
                val song = indexed.value
                SongRow(
                    runtime = runtime,
                    song = song,
                    downloadRecord = downloadsById[song.id],
                    onPlay = { onPlayAt(position) },
                    onDownload = { onDownload(song) },
                    onRemoveFromPlaylist = { onRemove(position) },
                    onMoveUp =
                        if (position > 0 && query.isEmpty()) {
                            { onMove(position, position - 1) }
                        } else {
                            null
                        },
                    onMoveDown =
                        if (position < state.localPlaylistSongs.lastIndex && query.isEmpty()) {
                            { onMove(position, position + 1) }
                        } else {
                            null
                        },
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete local playlist?") },
            text = {
                Text(
                    if (playlist.youtubePlaylistId == null) {
                        "This removes the playlist from MetroList Windows."
                    } else {
                        "This removes the local MetroList playlist only. It will not delete the YouTube Music playlist."
                    },
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}
