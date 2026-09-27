package com.metrolist.desktop.portable

import java.io.BufferedOutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PortableBackupWriter(
    private val json: Json =
        Json {
            encodeDefaults = true
            explicitNulls = true
        },
    private val usableSpace: (Path) -> Long = { path ->
        Files.getFileStore(path).usableSpace
    },
) {
    fun write(
        items: List<PortableItem>,
        destination: Path,
        createdAt: LocalDateTime = LocalDateTime.now(),
        onProgress: (PortableExportProgress) -> Unit = {},
    ): Path {
        require(items.isNotEmpty()) { "At least one offline item is required" }

        destination.parent?.let(Files::createDirectories)
        val temporary = destination.resolveSibling(destination.fileName.toString() + ".part")
        Files.deleteIfExists(temporary)

        val prepared = mutableListOf<PreparedItem>()
        items.forEachIndexed { index, item ->
            prepared += prepareItem(item)
            reportProgress(
                onProgress,
                PortableExportProgress(
                    phase = PortableExportPhase.PREPARING,
                    completedItems = index + 1,
                    totalItems = items.size,
                ),
            )
        }
        val artworks =
            prepared
                .mapNotNull(PreparedItem::artwork)
                .distinctBy(PreparedArtwork::relativePath)
        val metadataEntries = buildMetadataEntries(prepared)
        val metadataFiles =
            metadataEntries.map { (path, bytes) ->
                PortableFile(
                    relativePath = path,
                    size = bytes.size.toLong(),
                    sha256 = sha256(bytes),
                    kind = "metadata",
                )
            }
        val spanFiles = prepared.flatMap(PreparedItem::spanFiles)
        val artworkFiles =
            artworks.map { artwork ->
                PortableFile(
                    relativePath = artwork.relativePath,
                    size = artwork.size,
                    sha256 = artwork.sha256,
                    kind = "artwork",
                )
            }
        val resources = prepared.map(PreparedItem::resource)

        val manifest =
            PortableManifest(
                createdAt = createdAt.toString(),
                exportedSongCount = prepared.size,
                exportedDownloadCount = prepared.size,
                totalCacheBytes = prepared.sumOf { it.contentLength },
                totalArtworkBytes = artworks.sumOf(PreparedArtwork::size),
                songIds = prepared.map { it.item.song.id },
                requestIds = prepared.map { it.item.song.id },
                exportedCacheResources = resources,
                files = metadataFiles + spanFiles + artworkFiles,
            )
        val manifestBytes = json.encodeToString(manifest).encodeToByteArray()
        val requiredDataBytes =
            prepared.sumOf(PreparedItem::contentLength) +
                artworks.sumOf(PreparedArtwork::size) +
                metadataEntries.sumOf { it.second.size.toLong() } +
                manifestBytes.size.toLong()

        ensureDestinationCapacity(
            destination = destination,
            requiredDataBytes = requiredDataBytes,
        )

        try {
            var bytesWritten = 0L

            fun reportWritten(delta: Long) {
                bytesWritten += delta
                reportProgress(
                    onProgress,
                    PortableExportProgress(
                        phase = PortableExportPhase.WRITING,
                        completedItems = prepared.size,
                        totalItems = prepared.size,
                        bytesWritten = bytesWritten,
                        totalBytes = requiredDataBytes,
                    ),
                )
            }

            ZipOutputStream(BufferedOutputStream(Files.newOutputStream(temporary))).use { zip ->
                zip.setLevel(Deflater.NO_COMPRESSION)

                putBytes(zip, "manifest.json", manifestBytes)
                reportWritten(manifestBytes.size.toLong())

                metadataEntries.forEach { (path, bytes) ->
                    putBytes(zip, path, bytes)
                    reportWritten(bytes.size.toLong())
                }

                prepared.forEach { preparedItem ->
                    preparedItem.spans.forEach { span ->
                        putRange(
                            zip = zip,
                            entryName = span.relativePath,
                            source = preparedItem.item.mediaPath,
                            position = span.position,
                            length = span.length,
                        )
                        reportWritten(span.length)
                    }
                }
                artworks.forEach { artwork ->
                    putFile(
                        zip = zip,
                        entryName = artwork.relativePath,
                        source = artwork.source,
                    )
                    reportWritten(artwork.size)
                }
            }

            reportProgress(
                onProgress,
                PortableExportProgress(
                    phase = PortableExportPhase.VALIDATING,
                    completedItems = prepared.size,
                    totalItems = prepared.size,
                    bytesWritten = bytesWritten,
                    totalBytes = requiredDataBytes,
                ),
            )
            val validation = PortableValidator().validate(temporary)
            if (!validation.valid) {
                throw PortableValidationException(validation.errors)
            }

            reportProgress(
                onProgress,
                PortableExportProgress(
                    phase = PortableExportPhase.FINALIZING,
                    completedItems = prepared.size,
                    totalItems = prepared.size,
                    bytesWritten = bytesWritten,
                    totalBytes = requiredDataBytes,
                ),
            )
            try {
                Files.move(
                    temporary,
                    destination,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING)
            }

            reportProgress(
                onProgress,
                PortableExportProgress(
                    phase = PortableExportPhase.COMPLETE,
                    completedItems = prepared.size,
                    totalItems = prepared.size,
                    bytesWritten = bytesWritten,
                    totalBytes = requiredDataBytes,
                ),
            )
            return destination
        } catch (error: Exception) {
            Files.deleteIfExists(temporary)
            throw error
        }
    }

    private fun ensureDestinationCapacity(
        destination: Path,
        requiredDataBytes: Long,
    ) {
        val directory =
            destination.toAbsolutePath().parent
                ?: error("Backup destination has no parent directory")
        Files.createDirectories(directory)

        val safetyMargin =
            maxOf(
                MIN_EXPORT_SAFETY_MARGIN_BYTES,
                requiredDataBytes / 20L,
            )
        val requiredWithMargin = Math.addExact(requiredDataBytes, safetyMargin)
        val available = usableSpace(directory)

        if (available < requiredWithMargin) {
            throw InsufficientExportSpaceException(
                requiredBytes = requiredWithMargin,
                availableBytes = available,
            )
        }
    }

    private fun reportProgress(
        listener: (PortableExportProgress) -> Unit,
        progress: PortableExportProgress,
    ) {
        runCatching {
            listener(progress)
        }
    }

    private fun prepareItem(item: PortableItem): PreparedItem {
        val contentLength = Files.size(item.mediaPath)
        item.resolved.contentLength?.let { expected ->
            check(contentLength == expected) {
                "Offline media length mismatch for ${item.song.id}: expected=$expected actual=$contentLength"
            }
        }

        val spanPlans = SpanPlanner.plan(item.song.id, contentLength)
        val spans =
            spanPlans.map { plan ->
                PortableSpan(
                    relativePath = plan.relativePath,
                    cacheKey = item.song.id,
                    position = plan.position,
                    length = plan.length,
                    sha256 = sha256Range(item.mediaPath, plan.position, plan.length),
                )
            }

        val artworkPath = item.artworkPath
        val artwork =
            if (
                artworkPath != null &&
                item.song.thumbnail.isNotBlank() &&
                Files.isRegularFile(artworkPath) &&
                Files.size(artworkPath) > 0L
            ) {
                PreparedArtwork(
                    relativePath = "artwork/" + SpanPlanner.artworkFileName(item.song.thumbnail),
                    source = artworkPath,
                    size = Files.size(artworkPath),
                    sha256 = sha256(Files.readAllBytes(artworkPath)),
                )
            } else {
                null
            }

        return PreparedItem(
            item = item,
            contentLength = contentLength,
            spans = spans,
            resource =
                PortableResource(
                    songId = item.song.id,
                    cacheKey = item.song.id,
                    contentLength = contentLength,
                    spans = spans,
                ),
            spanFiles =
                spans.map { span ->
                    PortableFile(
                        relativePath = span.relativePath,
                        size = span.length,
                        sha256 = span.sha256,
                        kind = "media3_cache_span",
                    )
                },
            artwork = artwork,
        )
    }

    private fun buildMetadataEntries(items: List<PreparedItem>): List<Pair<String, ByteArray>> {
        val songs =
            items.map { prepared ->
                val song = prepared.item.song
                PortableSong(
                    id = song.id,
                    title = song.title,
                    duration = song.duration,
                    thumbnailUrl = song.thumbnail,
                    thumbnailArtworkPath = prepared.artwork?.relativePath,
                    albumId = song.album?.id,
                    albumName = song.album?.name,
                    explicit = song.explicit,
                    year = null,
                    date = null,
                    dateModified = null,
                    dateDownload = prepared.item.downloadedAt.toString(),
                    isVideo = song.isVideoSong,
                    isEpisode = song.isEpisode,
                )
            }

        val artistById =
            linkedMapOf<String, PortableArtist>().apply {
                items.forEach { prepared ->
                    prepared.item.song.artists.forEach artistLoop@{ artist ->
                        val id = artist.id ?: return@artistLoop
                        putIfAbsent(
                            id,
                            PortableArtist(
                                id = id,
                                name = artist.name,
                                thumbnailUrl = null,
                                channelId = id,
                                lastUpdateTime = prepared.item.downloadedAt.toString(),
                                isPodcastChannel = false,
                            ),
                        )
                    }
                }
            }

        val artistMaps =
            items.flatMap { prepared ->
                prepared.item.song.artists.mapIndexedNotNull { index, artist ->
                    val id = artist.id ?: return@mapIndexedNotNull null
                    PortableMap(
                        songId = prepared.item.song.id,
                        artistId = id,
                        position = index,
                    )
                }
            }

        val formats =
            items.map { prepared ->
                val resolved = prepared.item.resolved
                PortableFormat(
                    id = prepared.item.song.id,
                    itag = resolved.itag,
                    mimeType = resolved.mimeType,
                    codecs = resolved.codecs,
                    bitrate = resolved.bitrate,
                    sampleRate = resolved.sampleRate,
                    contentLength = prepared.contentLength,
                    loudnessDb = resolved.loudnessDb,
                    perceptualLoudnessDb = resolved.perceptualLoudnessDb,
                )
            }

        val transfers =
            items.map { prepared ->
                val item = prepared.item
                PortableTransfer(
                    requestId = item.song.id,
                    uri = item.song.id,
                    customCacheKey = item.song.id,
                    title = item.song.title,
                    contentLength = prepared.contentLength,
                    startTimeMs = item.startTimeMs,
                    updateTimeMs = item.updateTimeMs,
                    bytesDownloaded = prepared.contentLength,
                )
            }

        return listOf(
            "metadata/songs.json" to json.encodeToString(SongsEnvelope(songs)).encodeToByteArray(),
            "metadata/artists.json" to
                json.encodeToString(ArtistsEnvelope(artistById.values.toList())).encodeToByteArray(),
            "metadata/albums.json" to json.encodeToString(AlbumsEnvelope()).encodeToByteArray(),
            "metadata/song_artist_map.json" to
                json.encodeToString(MapsEnvelope(artistMaps)).encodeToByteArray(),
            "metadata/song_album_map.json" to json.encodeToString(MapsEnvelope()).encodeToByteArray(),
            "metadata/formats.json" to json.encodeToString(FormatsEnvelope(formats)).encodeToByteArray(),
            "metadata/downloads.json" to
                json.encodeToString(TransfersEnvelope(transfers)).encodeToByteArray(),
        )
    }

    private fun putBytes(zip: ZipOutputStream, entryName: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(entryName))
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun putFile(
        zip: ZipOutputStream,
        entryName: String,
        source: Path,
    ) {
        zip.putNextEntry(ZipEntry(entryName))
        Files.newInputStream(source).use { input ->
            input.copyTo(zip)
        }
        zip.closeEntry()
    }

    private fun putRange(
        zip: ZipOutputStream,
        entryName: String,
        source: Path,
        position: Long,
        length: Long,
    ) {
        zip.putNextEntry(ZipEntry(entryName))
        FileChannel.open(source, StandardOpenOption.READ).use { channel ->
            channel.position(position)
            var remaining = length
            val buffer = ByteBuffer.allocate(DEFAULT_BUFFER_SIZE)
            while (remaining > 0L) {
                buffer.clear()
                buffer.limit(minOf(buffer.capacity().toLong(), remaining).toInt())
                val count = channel.read(buffer)
                check(count > 0) { "Unexpected end of media file while writing $entryName" }
                zip.write(buffer.array(), 0, count)
                remaining -= count
            }
        }
        zip.closeEntry()
    }

    private fun sha256Range(source: Path, position: Long, length: Long): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileChannel.open(source, StandardOpenOption.READ).use { channel ->
            channel.position(position)
            var remaining = length
            val buffer = ByteBuffer.allocate(DEFAULT_BUFFER_SIZE)
            while (remaining > 0L) {
                buffer.clear()
                buffer.limit(minOf(buffer.capacity().toLong(), remaining).toInt())
                val count = channel.read(buffer)
                check(count > 0) { "Unexpected end of media file while hashing $source" }
                digest.update(buffer.array(), 0, count)
                remaining -= count
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val MIN_EXPORT_SAFETY_MARGIN_BYTES = 64L * 1024L * 1024L
    }

    private data class PreparedArtwork(
        val relativePath: String,
        val source: Path,
        val size: Long,
        val sha256: String,
    )

    private data class PreparedItem(
        val item: PortableItem,
        val contentLength: Long,
        val spans: List<PortableSpan>,
        val resource: PortableResource,
        val spanFiles: List<PortableFile>,
        val artwork: PreparedArtwork?,
    )
}
