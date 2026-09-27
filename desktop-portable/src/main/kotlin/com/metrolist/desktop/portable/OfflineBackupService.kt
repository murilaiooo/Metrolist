package com.metrolist.desktop.portable

import com.metrolist.desktop.offline.OfflineRecord
import com.metrolist.desktop.offline.OfflineStore
import com.metrolist.desktop.stream.ResolvedAudio
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime

class OfflineBackupService(
    private val store: OfflineStore,
    private val writer: PortableBackupWriter = PortableBackupWriter(),
    private val artworkProvider: (String) -> Path? = { null },
) {
    fun estimateAll(): PortableExportEstimate {
        val items = store.listCompleted().map(::toPortableItem)
        require(items.isNotEmpty()) { "No completed offline media is available for export" }

        val uniqueArtwork =
            items
                .mapNotNull(PortableItem::artworkPath)
                .filter(Files::isRegularFile)
                .distinctBy { it.toAbsolutePath().normalize().toString() }

        return PortableExportEstimate(
            songCount = items.size,
            audioBytes = items.sumOf { Files.size(it.mediaPath) },
            artworkBytes = uniqueArtwork.sumOf(Files::size),
        )
    }

    fun exportAll(
        destination: Path,
        createdAt: LocalDateTime = LocalDateTime.now(),
        onProgress: (PortableExportProgress) -> Unit = {},
    ): Path {
        val items = store.listCompleted().map(::toPortableItem)
        require(items.isNotEmpty()) { "No completed offline media is available for export" }

        return writer.write(
            items = items,
            destination = destination,
            createdAt = createdAt,
            onProgress = onProgress,
        )
    }

    private fun toPortableItem(record: OfflineRecord): PortableItem {
        val localPath = requireNotNull(record.localPath) {
            "Completed offline item is missing local media path"
        }
        val mediaPath = Path.of(localPath)
        require(Files.exists(mediaPath)) {
            "Completed offline media file is missing"
        }

        val expectedLength = requireNotNull(record.contentLength) {
            "Completed offline item is missing content length"
        }
        val actualLength = Files.size(mediaPath)
        require(actualLength == expectedLength) {
            "Completed offline media length mismatch"
        }

        val format = requireNotNull(record.format) {
            "Completed offline item is missing format metadata"
        }

        val resolved =
            ResolvedAudio(
                videoId = record.videoId,
                url = "",
                headers = emptyMap(),
                clientName = "OFFLINE_EXPORT",
                profileId = "OFFLINE_EXPORT",
                expiresAtEpochMs = null,
                requireBoundedRange = false,
                rangeChunkSizeBytes = 0L,
                useRangeChunks = false,
                itag = format.itag,
                mimeType = format.mimeType,
                codecs = format.codecs,
                bitrate = format.bitrate,
                sampleRate = format.sampleRate,
                contentLength = expectedLength,
                loudnessDb = format.loudnessDb,
                perceptualLoudnessDb = format.perceptualLoudnessDb,
            )

        val artworkPath =
            record.thumbnail
                .takeIf(String::isNotBlank)
                ?.let { thumbnail ->
                    runCatching { artworkProvider(thumbnail) }.getOrNull()
                }

        return PortableItem(
            song = record.toSongItem(),
            resolved = resolved,
            mediaPath = mediaPath,
            artworkPath = artworkPath,
            downloadedAt =
                record.downloadedAt
                    ?.let(LocalDateTime::parse)
                    ?: LocalDateTime.now(),
            startTimeMs = record.startTimeMs,
            updateTimeMs = record.updateTimeMs,
        )
    }
}
