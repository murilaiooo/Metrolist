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

internal fun chooseBackupDestination(): Path? {
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

internal fun catalogSubtitle(item: YTItem): String =
    when (item) {
        is SongItem ->
            item.artists
                .joinToString(", ") { it.name }
                .ifBlank { "Song" }
        is AlbumItem ->
            buildList {
                add("Album")
                item.artists
                    ?.joinToString(", ") { it.name }
                    ?.takeIf(String::isNotBlank)
                    ?.let(::add)
                item.year?.toString()?.let(::add)
            }.joinToString(" · ")
        is ArtistItem -> "Artist"
        is PlaylistItem ->
            buildList {
                add("Playlist")
                item.author?.name?.takeIf(String::isNotBlank)?.let(::add)
                item.songCountText?.takeIf(String::isNotBlank)?.let(::add)
            }.joinToString(" · ")
        else -> item::class.simpleName.orEmpty()
    }

internal fun formatPlaybackTime(milliseconds: Long?): String {
    val totalSeconds = (milliseconds ?: 0L).coerceAtLeast(0L) / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L

    return if (hours > 0L) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

internal fun exportProgressFraction(progress: PortableExportProgress): Float =
    when (progress.phase) {
        PortableExportPhase.PREPARING -> {
            val total = progress.totalItems.coerceAtLeast(1)
            0.2f * (progress.completedItems.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        }
        PortableExportPhase.WRITING -> {
            val ratio =
                if (progress.totalBytes > 0L) {
                    (progress.bytesWritten.toDouble() / progress.totalBytes.toDouble())
                        .coerceIn(0.0, 1.0)
                        .toFloat()
                } else {
                    0f
                }
            0.2f + (0.7f * ratio)
        }
        PortableExportPhase.VALIDATING -> 0.9f
        PortableExportPhase.FINALIZING -> 0.97f
        PortableExportPhase.COMPLETE -> 1f
    }

internal fun exportErrorMessage(error: Throwable): String =
    when (error) {
        is InsufficientExportSpaceException ->
            "Not enough free space. This export needs about " +
                formatBytes(error.requiredBytes) +
                ", but only " +
                formatBytes(error.availableBytes) +
                " is available. Free up space or choose another drive."

        is PortableValidationException ->
            "Backup validation failed before the final file was saved. " +
                "No invalid backup was promoted. " +
                error.validationErrors.joinToString("; ")

        else -> {
            val detail = error.message.orEmpty()
            when {
                detail.contains("media file is missing", ignoreCase = true) ->
                    "One offline song file is missing. Remove that offline download, download it again, and retry the export."
                detail.contains("length mismatch", ignoreCase = true) ->
                    "One offline song no longer matches its recorded size. Remove and re-download that song before exporting."
                detail.contains("No completed offline media", ignoreCase = true) ->
                    "There are no completed offline downloads to export."
                detail.isNotBlank() -> detail
                else -> error::class.simpleName ?: "Export failed"
            }
        }
    }

internal fun formatBytes(bytes: Long): String =
    when {
        bytes >= 1_073_741_824L -> String.format("%.2f GB", bytes / 1_073_741_824.0)
        bytes >= 1_048_576L -> String.format("%.1f MB", bytes / 1_048_576.0)
        bytes >= 1_024L -> String.format("%.1f KB", bytes / 1_024.0)
        else -> "$bytes B"
    }


internal fun canStartDownload(record: OfflineRecord?): Boolean =
    record == null || record.state == OfflineState.FAILED

internal fun downloadButtonLabel(record: OfflineRecord?): String =
    when (record?.state) {
        null -> "Download"
        OfflineState.QUEUED -> "Queued"
        OfflineState.DOWNLOADING ->
            progressFraction(record)
                ?.let { ((it * 100f).toInt().coerceIn(0, 100)).toString() + "%" }
                ?: "Downloading"
        OfflineState.COMPLETED -> "Downloaded"
        OfflineState.FAILED -> "Retry"
    }

internal fun progressFraction(record: OfflineRecord): Float? {
    val total = record.contentLength ?: return null
    if (total <= 0L) return null

    return (record.bytesDownloaded.toDouble() / total.toDouble())
        .coerceIn(0.0, 1.0)
        .toFloat()
}

internal fun downloadStatusText(record: OfflineRecord): String =
    when (record.state) {
        OfflineState.QUEUED ->
            "Queued · waiting for an available download slot"

        OfflineState.DOWNLOADING -> {
            val total = record.contentLength
            if (total == null && record.bytesDownloaded == 0L) {
                "Preparing download · resolving stream..."
            } else {
                val progress =
                    if (total != null && total > 0L) {
                        val percent =
                            ((record.bytesDownloaded.toDouble() / total.toDouble()) * 100.0)
                                .coerceIn(0.0, 100.0)
                        String.format("%.0f%%", percent) +
                            " · " +
                            formatBytes(record.bytesDownloaded) +
                            " / " +
                            formatBytes(total)
                    } else {
                        formatBytes(record.bytesDownloaded)
                    }

                "Downloading · " +
                    progress +
                    " · " +
                    if (record.bytesPerSecond > 0L) {
                        formatSpeed(record.bytesPerSecond)
                    } else {
                        "calculating speed..."
                    }
            }
        }

        OfflineState.COMPLETED ->
            "Downloaded · " +
                formatBytes(record.contentLength ?: record.bytesDownloaded)

        OfflineState.FAILED -> {
            val partial =
                if (record.bytesDownloaded > 0L) {
                    " · " + formatBytes(record.bytesDownloaded) + " received"
                } else {
                    ""
                }
            val detail =
                record.lastError
                    ?.takeIf(String::isNotBlank)
                    ?.let { " · " + it }
                    .orEmpty()

            "Failed" + partial + detail
        }
    }

internal fun completedFormatText(record: OfflineRecord): String? {
    val format = record.format ?: return null
    val parts = mutableListOf<String>()

    format.codecs
        ?.takeIf(String::isNotBlank)
        ?.let { parts += it.uppercase() }

    format.bitrate
        ?.takeIf { it > 0 }
        ?.let { parts += (it / 1000).toString() + " kbps" }

    format.sampleRate
        ?.takeIf { it > 0 }
        ?.let { parts += String.format("%.1f kHz", it / 1000.0) }

    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

internal fun formatSpeed(bytesPerSecond: Long): String =
    formatBytes(bytesPerSecond.coerceAtLeast(0L)) + "/s"
