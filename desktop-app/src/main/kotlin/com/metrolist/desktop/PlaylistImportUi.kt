package com.metrolist.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metrolist.desktop.library.ImportRow
import com.metrolist.desktop.library.ImportRowState
import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Path

internal fun choosePlaylistTextFile(): Path? {
    val owner = Frame()
    return try {
        val dialog = FileDialog(owner, "Import playlist TXT", FileDialog.LOAD)
        dialog.setFilenameFilter { _, name -> name.endsWith(".txt", ignoreCase = true) }
        dialog.isVisible = true
        val file = dialog.file ?: return null
        Path.of(dialog.directory, file)
    } finally {
        owner.dispose()
    }
}

@Composable
internal fun PlaylistImportScreen(
    state: PlaylistWorkspaceState,
    onBack: () -> Unit,
    onPlaylistNameChange: (String) -> Unit,
    onSourceTextChange: (String) -> Unit,
    onChooseTxt: () -> Unit,
    onStartMatching: () -> Unit,
    onDestinationChange: (ImportDestination) -> Unit,
    onFilterChange: (ImportRowFilter) -> Unit,
    onChooseCandidate: (Int, String) -> Unit,
    onRetryRow: (Int, String?) -> Unit,
    onFinalize: () -> Unit,
) {
    when (state.screen) {
        PlaylistWorkspaceScreen.IMPORT_EDIT ->
            PlaylistImportEdit(
                state = state,
                onBack = onBack,
                onPlaylistNameChange = onPlaylistNameChange,
                onSourceTextChange = onSourceTextChange,
                onChooseTxt = onChooseTxt,
                onStartMatching = onStartMatching,
                onDestinationChange = onDestinationChange,
            )

        PlaylistWorkspaceScreen.IMPORT_REVIEW ->
            PlaylistImportReview(
                state = state,
                onBack = onBack,
                onDestinationChange = onDestinationChange,
                onFilterChange = onFilterChange,
                onChooseCandidate = onChooseCandidate,
                onRetryRow = onRetryRow,
                onFinalize = onFinalize,
            )

        else -> Unit
    }
}

@Composable
private fun PlaylistImportEdit(
    state: PlaylistWorkspaceState,
    onBack: () -> Unit,
    onPlaylistNameChange: (String) -> Unit,
    onSourceTextChange: (String) -> Unit,
    onChooseTxt: () -> Unit,
    onStartMatching: () -> Unit,
    onDestinationChange: (ImportDestination) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                )
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Create / Import Playlist", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "Paste a list or load a TXT file. Uncertain matches are reviewed before anything is created.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        OutlinedTextField(
            value = state.playlistName,
            onValueChange = onPlaylistNameChange,
            label = { Text("Playlist name") },
            modifier = Modifier.fillMaxWidth().trackTextInputFocus(),
            singleLine = true,
        )

        DestinationSelector(state.destination, onDestinationChange)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onChooseTxt) { Text("Choose TXT") }
            Text(
                "or paste up to 1,000 songs below",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }

        OutlinedTextField(
            value = state.sourceText,
            onValueChange = onSourceTextChange,
            label = { Text("Song list") },
            placeholder = { Text("Artist - Song\nSong - Artist\n...") },
            modifier = Modifier.fillMaxWidth().weight(1f).trackTextInputFocus(),
            minLines = 12,
        )

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Button(
            onClick = onStartMatching,
            enabled = state.playlistName.isNotBlank() && state.sourceText.isNotBlank() && !state.busy,
        ) {
            Text("Find matches")
        }
    }
}

@Composable
private fun PlaylistImportReview(
    state: PlaylistWorkspaceState,
    onBack: () -> Unit,
    onDestinationChange: (ImportDestination) -> Unit,
    onFilterChange: (ImportRowFilter) -> Unit,
    onChooseCandidate: (Int, String) -> Unit,
    onRetryRow: (Int, String?) -> Unit,
    onFinalize: () -> Unit,
) {
    val summary = summarizeImportRows(state.rows)
    val progress = state.importProgress
    val visibleRows = filterImportRows(state.rows, state.rowFilter)

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, enabled = !state.busy) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(state.playlistName, style = MaterialTheme.typography.headlineLarge)
                Text(
                    "${summary.total} tracks · ${summary.ready} ready · ${summary.review} review · " +
                        "${summary.notFound} not found · ${summary.failed} failed",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.matching || progress.running > 0) {
            val fraction = if (progress.total == 0) 0f else progress.completed.toFloat() / progress.total.toFloat()
            LinearProgressIndicator(
                progress = { fraction.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Matching ${progress.completed} / ${progress.total}" +
                    if (progress.running > 0) " · ${progress.running} active" else "",
                style = MaterialTheme.typography.bodySmall,
            )
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

        DestinationSelector(state.destination, onDestinationChange)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ImportRowFilter.entries.forEach { filter ->
                val count =
                    when (filter) {
                        ImportRowFilter.ALL -> summary.total
                        ImportRowFilter.READY -> summary.ready
                        ImportRowFilter.REVIEW -> summary.review
                        ImportRowFilter.NOT_FOUND -> summary.notFound + summary.failed
                    }
                FilterChip(
                    selected = state.rowFilter == filter,
                    onClick = { onFilterChange(filter) },
                    label = { Text("${filterLabel(filter)} ($count)") },
                )
            }
        }

        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(visibleRows, key = { "${it.sessionId}:${it.position}" }) { row ->
                ImportReviewRow(
                    row = row,
                    onChooseCandidate = { videoId -> onChooseCandidate(row.position, videoId) },
                    onRetry = { query -> onRetryRow(row.position, query) },
                )
            }
        }

        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Only Ready tracks will be created. Review and Not found are never silently substituted.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(16.dp))
            Button(
                onClick = onFinalize,
                enabled = !state.busy && !state.matching && summary.ready > 0,
            ) {
                Text(
                    if (state.destination == ImportDestination.LOCAL) {
                        "Create local playlist"
                    } else {
                        "Create & sync to YouTube Music"
                    },
                )
            }
        }
    }
}

@Composable
private fun DestinationSelector(
    destination: ImportDestination,
    onDestinationChange: (ImportDestination) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Destination", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = destination == ImportDestination.LOCAL,
                onClick = { onDestinationChange(ImportDestination.LOCAL) },
                label = { Text("Local only") },
            )
            FilterChip(
                selected = destination == ImportDestination.YOUTUBE_MUSIC,
                onClick = { onDestinationChange(ImportDestination.YOUTUBE_MUSIC) },
                label = { Text("YouTube Music") },
            )
        }
        Text(
            if (destination == ImportDestination.LOCAL) {
                "Stays only in MetroList Windows. You can sync it later."
            } else {
                "Creates the local mirror first, then explicitly syncs the reviewed tracks to YouTube Music."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ImportReviewRow(
    row: ImportRow,
    onChooseCandidate: (String) -> Unit,
    onRetry: (String?) -> Unit,
) {
    var customQuery by remember(row.sessionId, row.position) { mutableStateOf(row.original) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(row.original, style = MaterialTheme.typography.titleSmall)
                    Text(
                        importStateLabel(row.state) +
                            row.score?.let { " · ${formatConfidence(it)}" }.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = stateColor(row.state),
                    )
                }
                row.selectedSong?.let { selected ->
                    Text(
                        selected.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (row.state == ImportRowState.REVIEW || row.state == ImportRowState.NOT_FOUND || row.state == ImportRowState.FAILED) {
                row.candidates.forEach { candidate ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(candidate.song.title, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                candidate.song.artists.joinToString(", ") { it.name } +
                                    candidate.song.album?.let { " · ${it.name}" }.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                buildString {
                                    append(formatConfidence(candidate.score))
                                    candidate.song.explicit.takeIf { it }?.let { append(" · Explicit") }
                                    candidate.versionKind?.let { append(" · ${prettyVersion(it)}") }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedButton(onClick = { onChooseCandidate(candidate.song.videoId) }) {
                            Text("Choose")
                        }
                    }
                }

                OutlinedTextField(
                    value = customQuery,
                    onValueChange = { customQuery = it },
                    label = { Text("Search again") },
                    modifier = Modifier.fillMaxWidth().trackTextInputFocus(),
                    singleLine = true,
                )
                OutlinedButton(
                    onClick = { onRetry(customQuery.takeIf(String::isNotBlank)) },
                    enabled = customQuery.isNotBlank(),
                ) {
                    Text("Retry search")
                }
            }

            row.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

private fun filterLabel(filter: ImportRowFilter): String =
    when (filter) {
        ImportRowFilter.ALL -> "All"
        ImportRowFilter.READY -> "Ready"
        ImportRowFilter.REVIEW -> "Review"
        ImportRowFilter.NOT_FOUND -> "Not found"
    }

private fun importStateLabel(state: ImportRowState): String =
    when (state) {
        ImportRowState.PENDING -> "Pending"
        ImportRowState.READY -> "Ready"
        ImportRowState.REVIEW -> "Review"
        ImportRowState.NOT_FOUND -> "Not found"
        ImportRowState.FAILED -> "Search failed"
    }

@Composable
private fun stateColor(state: ImportRowState) =
    when (state) {
        ImportRowState.READY -> MaterialTheme.colorScheme.primary
        ImportRowState.REVIEW -> MaterialTheme.colorScheme.tertiary
        ImportRowState.NOT_FOUND, ImportRowState.FAILED -> MaterialTheme.colorScheme.error
        ImportRowState.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
    }

private fun formatConfidence(score: Double): String = "${(score * 100).toInt()}% confidence"

private fun prettyVersion(value: String): String =
    value.lowercase().replace('_', ' ').replaceFirstChar { it.titlecase() }
