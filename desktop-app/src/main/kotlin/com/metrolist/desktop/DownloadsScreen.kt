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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
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

internal fun shouldShowDownloadActivity(downloads: List<OfflineRecord>): Boolean =
    downloads.any {
        it.state == OfflineState.DOWNLOADING ||
            it.state == OfflineState.QUEUED ||
            it.state == OfflineState.FAILED
    }

internal fun compactDownloadActivitySummary(downloads: List<OfflineRecord>): String {
    val downloading = downloads.count { it.state == OfflineState.DOWNLOADING }
    val queued = downloads.count { it.state == OfflineState.QUEUED }
    val failed = downloads.count { it.state == OfflineState.FAILED }
    val speed =
        downloads
            .filter { it.state == OfflineState.DOWNLOADING }
            .sumOf(OfflineRecord::bytesPerSecond)

    return buildList {
        if (downloading > 0) add("$downloading downloading")
        if (queued > 0) add("$queued queued")
        if (failed > 0) add("$failed failed")
        if (speed > 0L) add(formatBytes(speed) + "/s")
    }.joinToString(" · ")
}

@Composable
internal fun DownloadActivityBar(
    downloads: List<OfflineRecord>,
    onViewDownloads: () -> Unit,
) {
    if (!shouldShowDownloadActivity(downloads)) return

    val failed = downloads.count { it.state == OfflineState.FAILED }
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 3.dp,
            color =
                if (failed > 0) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
        ) {
            Row(
                modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    compactDownloadActivitySummary(downloads),
                    style = MaterialTheme.typography.labelLarge,
                    color =
                        if (failed > 0) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                )
                Spacer(Modifier.width(6.dp))
                TextButton(onClick = onViewDownloads) {
                    Text("Downloads")
                }
            }
        }
    }
}

@Composable
internal fun DownloadInlineStatus(
    record: OfflineRecord,
) {
    Spacer(Modifier.height(4.dp))
    Text(
        downloadStatusText(record),
        style = MaterialTheme.typography.bodySmall,
        color =
            if (record.state == OfflineState.FAILED) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
    )

    if (record.state == OfflineState.DOWNLOADING) {
        Spacer(Modifier.height(4.dp))
        val progress = progressFraction(record)
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp),
            )
        } else {
            LinearProgressIndicator(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp),
            )
        }
    }
}

@Composable
internal fun DownloadsView(
    runtime: DesktopRuntime,
    downloads: List<OfflineRecord>,
    onRetry: (String) -> Unit,
    onRemove: (String) -> Unit,
    onRetryAllFailed: () -> Unit,
    onClearFailed: () -> Unit,
    onCancelPending: () -> Unit,
    onRemoveCompleted: () -> Unit,
    focusSearchRequestKey: Int = 0,
) {
    val scope = rememberCoroutineScope()
    val searchFocusRequester = remember { FocusRequester() }

    var query by remember { mutableStateOf("") }

    LaunchedEffect(focusSearchRequestKey) {
        if (focusSearchRequestKey > 0) searchFocusRequester.requestFocus()
    }
    var filter by remember { mutableStateOf(DownloadFilter.ALL) }
    var confirmCancelPending by remember { mutableStateOf(false) }
    var confirmClearFailed by remember { mutableStateOf(false) }
    var confirmRemoveCompleted by remember { mutableStateOf(false) }

    var exportDestination by remember { mutableStateOf<Path?>(null) }
    var exportEstimate by remember { mutableStateOf<PortableExportEstimate?>(null) }
    var exportAvailableBytes by remember { mutableStateOf<Long?>(null) }
    var exportPreparing by remember { mutableStateOf(false) }
    var exportRunning by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableStateOf<PortableExportProgress?>(null) }
    var exportError by remember { mutableStateOf<String?>(null) }
    var exportResult by remember { mutableStateOf<Path?>(null) }

    val completed = downloads.count { it.state == OfflineState.COMPLETED }
    val queued = downloads.count { it.state == OfflineState.QUEUED }
    val downloading = downloads.count { it.state == OfflineState.DOWNLOADING }
    val failed = downloads.count { it.state == OfflineState.FAILED }
    val offlineBytes =
        downloads
            .filter { it.state == OfflineState.COMPLETED }
            .sumOf { it.contentLength ?: it.bytesDownloaded }
    val filtered = filterDownloads(downloads, filter, query)
    val groups = groupDownloads(filtered)

    fun resetExportDialog() {
        exportDestination = null
        exportEstimate = null
        exportAvailableBytes = null
        exportPreparing = false
        exportRunning = false
        exportProgress = null
        exportError = null
        exportResult = null
    }

    fun chooseAndPrepareExport() {
        val destination = chooseBackupDestination() ?: return
        exportDestination = destination
        exportEstimate = null
        exportAvailableBytes = null
        exportPreparing = true
        exportRunning = false
        exportProgress = null
        exportError = null
        exportResult = null

        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val estimate = runtime.estimateExport()
                    val directory = destination.toAbsolutePath().parent
                    val available =
                        directory?.let {
                            Files.getFileStore(it).usableSpace
                        }
                    estimate to available
                }
            }.onSuccess { (estimate, available) ->
                exportEstimate = estimate
                exportAvailableBytes = available
                exportPreparing = false
            }.onFailure { error ->
                exportError = exportErrorMessage(error)
                exportPreparing = false
            }
        }
    }

    fun beginExport() {
        val destination = exportDestination ?: return
        val estimate = exportEstimate ?: return

        exportRunning = true
        exportError = null
        exportResult = null
        exportProgress =
            PortableExportProgress(
                phase = PortableExportPhase.PREPARING,
                completedItems = 0,
                totalItems = estimate.songCount,
            )

        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    runtime.exportAll(destination) { progress ->
                        scope.launch {
                            exportProgress = progress
                        }
                    }
                }
            }.onSuccess { path ->
                exportRunning = false
                exportProgress =
                    PortableExportProgress(
                        phase = PortableExportPhase.COMPLETE,
                        completedItems = estimate.songCount,
                        totalItems = estimate.songCount,
                        bytesWritten = exportProgress?.bytesWritten ?: estimate.estimatedContentBytes,
                        totalBytes = exportProgress?.totalBytes ?: estimate.estimatedContentBytes,
                    )
                exportResult = path
            }.onFailure { error ->
                exportRunning = false
                exportError = exportErrorMessage(error)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Downloads", style = MaterialTheme.typography.headlineLarge)
                Text(
                    buildList {
                        if (downloading > 0) add("$downloading downloading")
                        if (queued > 0) add("$queued queued")
                        add("$completed offline")
                        if (failed > 0) add("$failed failed")
                        add(formatBytes(offlineBytes))
                    }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = ::chooseAndPrepareExport,
                enabled = completed > 0 && !exportRunning,
            ) {
                Icon(
                    imageVector = Icons.Rounded.FileUpload,
                    contentDescription = null,
                )
                Spacer(Modifier.width(6.dp))
                Text("Export backup")
            }
        }

        if (failed > 0 || downloading + queued > 0 || completed > 0) {
            Spacer(Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (failed > 0) {
                    TextButton(onClick = onRetryAllFailed) {
                        Text("Retry failed $failed")
                    }
                    TextButton(onClick = { confirmClearFailed = true }) {
                        Text("Clear failed")
                    }
                }
                if (downloading + queued > 0) {
                    TextButton(onClick = { confirmCancelPending = true }) {
                        Text("Cancel pending ${downloading + queued}")
                    }
                }
                if (completed > 0) {
                    TextButton(onClick = { confirmRemoveCompleted = true }) {
                        Text("Remove offline $completed")
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search title, artist, or album") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                )
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(searchFocusRequester)
                    .trackTextInputFocus(),
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
        )

        Spacer(Modifier.height(10.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DownloadFilterButton(
                label = "All ${downloads.size}",
                selected = filter == DownloadFilter.ALL,
                onClick = { filter = DownloadFilter.ALL },
            )
            DownloadFilterButton(
                label = "Downloading $downloading",
                selected = filter == DownloadFilter.DOWNLOADING,
                onClick = { filter = DownloadFilter.DOWNLOADING },
            )
            DownloadFilterButton(
                label = "Queued $queued",
                selected = filter == DownloadFilter.QUEUED,
                onClick = { filter = DownloadFilter.QUEUED },
            )
            DownloadFilterButton(
                label = "Offline $completed",
                selected = filter == DownloadFilter.OFFLINE,
                onClick = { filter = DownloadFilter.OFFLINE },
            )
            DownloadFilterButton(
                label = "Failed $failed",
                selected = filter == DownloadFilter.FAILED,
                onClick = { filter = DownloadFilter.FAILED },
            )
        }

        Spacer(Modifier.height(12.dp))

        when {
            downloads.isEmpty() ->
                Text(
                    "No downloads yet. Download a song or playlist to make it available offline.",
                    style = MaterialTheme.typography.bodyLarge,
                )

            groups.isEmpty() ->
                Text(
                    "No downloads match the current search and filter.",
                    style = MaterialTheme.typography.bodyLarge,
                )

            else ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    groups.forEach { (group, records) ->
                        item(key = "download-group:" + group.name) {
                            Text(
                                group.label + " (" + records.size + ")",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        items(
                            items = records,
                            key = OfflineRecord::videoId,
                        ) { record ->
                            DownloadRecordRow(
                                runtime = runtime,
                                record = record,
                                onRetry = { onRetry(record.videoId) },
                                onRemove = { onRemove(record.videoId) },
                            )
                        }
                    }
                }
        }
    }

    if (confirmCancelPending) {
        AlertDialog(
            onDismissRequest = { confirmCancelPending = false },
            title = { Text("Cancel pending downloads?") },
            text = {
                Text(
                    "This cancels $downloading active and $queued queued downloads. " +
                        "Completed offline songs are kept.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmCancelPending = false
                        onCancelPending()
                    },
                ) {
                    Text("Cancel pending")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmCancelPending = false }) {
                    Text("Keep downloading")
                }
            },
        )
    }

    if (confirmClearFailed) {
        AlertDialog(
            onDismissRequest = { confirmClearFailed = false },
            title = { Text("Clear failed downloads?") },
            text = {
                Text(
                    "This clears $failed failed entries and any partial files. " +
                        "You can download those songs again later.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmClearFailed = false
                        onClearFailed()
                    },
                ) {
                    Text("Clear failed")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmClearFailed = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (confirmRemoveCompleted) {
        AlertDialog(
            onDismissRequest = { confirmRemoveCompleted = false },
            title = { Text("Remove all offline downloads?") },
            text = {
                Text(
                    "This deletes $completed downloaded songs (" +
                        formatBytes(offlineBytes) +
                        ") from MetroList offline storage. " +
                        "They remain in your YouTube Music library and playlists.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmRemoveCompleted = false
                        onRemoveCompleted()
                    },
                ) {
                    Text("Remove all offline")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmRemoveCompleted = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    exportDestination?.let { destination ->
        ExportBackupDialog(
            destination = destination,
            estimate = exportEstimate,
            availableBytes = exportAvailableBytes,
            preparing = exportPreparing,
            running = exportRunning,
            progress = exportProgress,
            error = exportError,
            result = exportResult,
            onStart = ::beginExport,
            onRetry = ::beginExport,
            onChooseAnother = {
                resetExportDialog()
                chooseAndPrepareExport()
            },
            onClose = ::resetExportDialog,
        )
    }
}
