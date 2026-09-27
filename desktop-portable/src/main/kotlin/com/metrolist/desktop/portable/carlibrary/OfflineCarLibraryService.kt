package com.metrolist.desktop.portable.carlibrary

import com.metrolist.desktop.library.LocalPlaylist
import com.metrolist.desktop.offline.OfflineRecord
import com.metrolist.desktop.offline.OfflineStore
import com.metrolist.desktop.portable.PortableItem
import com.metrolist.desktop.stream.ResolvedAudio
import com.metrolist.innertube.models.SongItem
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime

class OfflineCarLibraryService(
    private val store: OfflineStore,
    producerVersion: String,
    private val artworkProvider: (String) -> Path? = { null },
    private val playlistsProvider: () -> List<LocalPlaylist> = { emptyList() },
    private val playlistSongsProvider: (String) -> List<SongItem> = { emptyList() },
    private val exporter: CarLibraryExporter =
        CarLibraryExporter(
            producerVersion = producerVersion,
        ),
) {
    fun exportAll(
        destination: Path,
        onProgress: (CarLibraryExportProgress) -> Unit = {},
    ): CarLibraryExportResult {
        val items = store.listCompleted().map(::toPortableItem)
        require(items.isNotEmpty()) {
            "No completed offline media is available for Car Library export"
        }

        val exportedIds = items.map { it.song.id }.toSet()
        val playlists =
            playlistsProvider()
                .mapNotNull { playlist ->
                    val songIds =
                        playlistSongsProvider(playlist.id)
                            .map(SongItem::id)
                            .filter(exportedIds::contains)

                    if (songIds.isEmpty()) {
                        null
                    } else {
                        CarLibraryPlaylist(
                            id = playlist.id,
                            name = playlist.name,
                            songIds = songIds,
                            youtubePlaylistId = playlist.youtubePlaylistId,
                            sourceRevision = playlist.updatedAtMs.toString(),
                        )
                    }
                }

        return exporter.exportCarLibrary(
            destination = destination,
            selection =
                CarLibraryExportSelection(
                    items = items,
                    playlists = playlists,
                ),
            onProgress = onProgress,
        )
    }

    private fun toPortableItem(record: OfflineRecord): PortableItem {
        val localPath = requireNotNull(record.localPath) {
            "Completed offline item is missing local media path"
        }
        val mediaPath = Path.of(localPath)
        require(Files.isRegularFile(mediaPath)) {
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
                clientName = "CAR_LIBRARY_EXPORT",
                profileId = "CAR_LIBRARY_EXPORT",
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
                    ?.let { timestamp ->
                        runCatching { LocalDateTime.parse(timestamp) }.getOrNull()
                    }
                    ?: LocalDateTime.now(),
            startTimeMs = record.startTimeMs,
            updateTimeMs = record.updateTimeMs,
        )
    }
}
