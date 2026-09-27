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
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@Composable
internal fun ExportBackupDialog(
    destination: Path,
    estimate: PortableExportEstimate?,
    availableBytes: Long?,
    preparing: Boolean,
    running: Boolean,
    progress: PortableExportProgress?,
    error: String?,
    result: Path?,
    onStart: () -> Unit,
    onRetry: () -> Unit,
    onChooseAnother: () -> Unit,
    onClose: () -> Unit,
) {
    val progressFraction = progress?.let(::exportProgressFraction)
    val progressPercent =
        progressFraction
            ?.let { (it * 100f).toInt().coerceIn(0, 100) }

    AlertDialog(
        onDismissRequest = {
            if (!preparing && !running) {
                onClose()
            }
        },
        title = {
            Text(
                when {
                    result != null -> "Export complete"
                    error != null -> "Export could not finish"
                    running -> "Exporting offline library"
                    preparing -> "Preparing export summary"
                    else -> "Review export"
                },
            )
        },
        text = {
            Column(
                modifier = Modifier.width(560.dp),
            ) {
                Text(
                    "Destination",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    destination.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(12.dp))

                if (preparing) {
                    Text("Checking completed downloads, artwork, and destination space...")
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }

                estimate?.let { summary ->
                    Text(
                        "Export summary",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        summary.songCount.toString() + " songs",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Audio: " + formatBytes(summary.audioBytes),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Artwork: " + formatBytes(summary.artworkBytes),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Estimated content: " + formatBytes(summary.estimatedContentBytes),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    availableBytes?.let {
                        Text(
                            "Free space at destination: " + formatBytes(it),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Text(
                        "The final .metrolistbackup will be slightly larger because it also includes metadata and ZIP structure.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                if (running && progress != null) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        exportProgressText(progress),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progressFraction ?: 0f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    progressPercent?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            it.toString() + "%",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                error?.let {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                result?.let {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Backup completed and validated successfully.",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        it.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        confirmButton = {
            when {
                result != null ->
                    Button(onClick = onClose) {
                        Text("Close")
                    }

                running || preparing ->
                    Unit

                error?.startsWith("Not enough free space") == true ->
                    Button(onClick = onChooseAnother) {
                        Text("Choose another destination")
                    }

                error != null && estimate != null ->
                    Button(onClick = onRetry) {
                        Text("Retry export")
                    }

                error == null && estimate != null ->
                    Button(onClick = onStart) {
                        Text("Export backup")
                    }

                else ->
                    Unit
            }
        },
        dismissButton = {
            when {
                running || preparing ->
                    Unit

                result != null ->
                    Unit

                error?.startsWith("Not enough free space") == true ->
                    OutlinedButton(onClick = onClose) {
                        Text("Close")
                    }

                error != null ->
                    OutlinedButton(onClick = onClose) {
                        Text("Close")
                    }

                else ->
                    OutlinedButton(onClick = onChooseAnother) {
                        Text("Change destination")
                    }
            }
        },
    )
}

internal fun exportProgressText(progress: PortableExportProgress): String =
    when (progress.phase) {
        PortableExportPhase.PREPARING ->
            "Preparing " +
                progress.completedItems +
                "/" +
                progress.totalItems.coerceAtLeast(1) +
                " songs..."
        PortableExportPhase.WRITING ->
            "Writing backup · " +
                formatBytes(progress.bytesWritten) +
                " / " +
                formatBytes(progress.totalBytes)
        PortableExportPhase.VALIDATING ->
            "Validating backup integrity..."
        PortableExportPhase.FINALIZING ->
            "Finalizing backup file..."
        PortableExportPhase.COMPLETE ->
            "Export complete."
    }

@Composable
internal fun DownloadFilterButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
    )
}

@Composable
internal fun DownloadRecordRow(
    runtime: DesktopRuntime,
    record: OfflineRecord,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        ArtworkThumbnail(
            runtime = runtime,
            thumbnailUrl = record.thumbnail,
            size = 52.dp,
            contentDescription = record.title,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(record.title, style = MaterialTheme.typography.titleMedium)
            Text(
                record.artists
                    .joinToString(", ") { it.name }
                    .ifBlank { "YouTube Music" },
                style = MaterialTheme.typography.bodySmall,
            )
            DownloadInlineStatus(record)
            if (record.state == OfflineState.COMPLETED) {
                completedFormatText(record)?.let { detail ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        if (record.state == OfflineState.FAILED) {
            OutlinedButton(onClick = onRetry) {
                Text("Retry")
            }
            Spacer(Modifier.width(8.dp))
        }

        OutlinedButton(onClick = onRemove) {
            Text(
                when (record.state) {
                    OfflineState.DOWNLOADING,
                    OfflineState.QUEUED,
                    -> "Cancel"
                    OfflineState.COMPLETED -> "Remove offline"
                    OfflineState.FAILED -> "Clear"
                },
            )
        }
        }
    }
}
