package com.metrolist.music.car.importer

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class CarPortableArchiveValidator(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = true
    },
) {
    fun validate(
        input: InputStream,
        availableDestinationBytes: Long,
    ): ValidatedCarBackup {
        val errors = mutableListOf<String>()
        val entries = linkedMapOf<String, EntryObservation>()

        try {
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    if (entries.containsKey(name)) {
                        errors += "Duplicate archive entry: $name"
                    }
                    if (!isSafeRelativePath(name)) {
                        errors += "Unsafe archive path: $name"
                    }
                    if (!entry.isDirectory && entry.method != ZipEntry.DEFLATED) {
                        errors += "Unexpected ZIP method: $name"
                    }

                    val observation =
                        readEntry(
                            zip = zip,
                            capture = name == MANIFEST || name.startsWith("metadata/"),
                        )
                    if (observation.captureOverflow) {
                        errors += "Metadata entry too large: $name"
                    }
                    if (!entries.containsKey(name)) {
                        entries[name] =
                            observation.copy(
                                name = name,
                                method = entry.method,
                                isDirectory = entry.isDirectory,
                            )
                    }
                    zip.closeEntry()
                }
            }
        } catch (error: Exception) {
            errors += "Archive validation failed: ${error::class.simpleName}"
        }

        val manifest =
            entries[MANIFEST]
                ?.captured
                ?.let { bytes ->
                    runCatching {
                        json.decodeFromString<CarPortableManifest>(bytes.decodeToString())
                    }.onFailure {
                        errors += "Invalid manifest schema"
                    }.getOrNull()
                }

        if (manifest == null) {
            if (MANIFEST !in entries) errors += "$MANIFEST missing"
            throw CarPortableValidationException(errors.distinct())
        }

        validateProfile(manifest, errors)
        validateSpace(manifest, availableDestinationBytes, errors)
        val declaredFiles = validateFiles(entries, manifest, errors)
        validateResources(entries, declaredFiles, manifest, errors)

        val songs = decode<CarSongsEnvelope>(entries, "metadata/songs.json", errors)?.songs.orEmpty()
        val artists = decode<CarArtistsEnvelope>(entries, "metadata/artists.json", errors)?.artists.orEmpty()
        val albums = decode<CarAlbumsEnvelope>(entries, "metadata/albums.json", errors)?.albums.orEmpty()
        val artistMaps = decode<CarMapsEnvelope>(entries, "metadata/song_artist_map.json", errors)?.maps.orEmpty()
        val albumMaps = decode<CarMapsEnvelope>(entries, "metadata/song_album_map.json", errors)?.maps.orEmpty()
        val formats = decode<CarFormatsEnvelope>(entries, "metadata/formats.json", errors)?.formats.orEmpty()
        val transfers = decode<CarTransfersEnvelope>(entries, "metadata/downloads.json", errors)?.downloads.orEmpty()

        validateMetadata(
            entries = entries,
            manifest = manifest,
            songs = songs,
            artists = artists,
            albums = albums,
            artistMaps = artistMaps,
            albumMaps = albumMaps,
            formats = formats,
            transfers = transfers,
            errors = errors,
        )

        if (errors.isNotEmpty()) {
            throw CarPortableValidationException(errors.distinct())
        }

        return ValidatedCarBackup(
            manifest = manifest,
            songs = songs,
            artists = artists,
            albums = albums,
            artistMaps = artistMaps,
            albumMaps = albumMaps,
            formats = formats,
            transfers = transfers,
            resources = manifest.exportedCacheResources,
        )
    }

    private fun validateProfile(
        manifest: CarPortableManifest,
        errors: MutableList<String>,
    ) {
        if (
            manifest.appName != "Metrolist" ||
            manifest.backupFormatVersion != 1 ||
            manifest.sourceAppVersion != "13.6.0" ||
            manifest.sourceDatabaseVersion != 38 ||
            manifest.type != "offline_downloads" ||
            manifest.cacheStrategy != "media3_simple_cache_spans" ||
            manifest.totalBackupBytes != null
        ) {
            errors += "Compatibility profile mismatch"
        }

        if (manifest.songIds.size != manifest.songIds.toSet().size) {
            errors += "Duplicate song IDs"
        }
        if (manifest.requestIds.size != manifest.requestIds.toSet().size) {
            errors += "Duplicate request IDs"
        }
        if (manifest.exportedSongCount != manifest.songIds.size) {
            errors += "Exported song count mismatch"
        }
        if (manifest.exportedDownloadCount != manifest.requestIds.size) {
            errors += "Exported download count mismatch"
        }
        if (manifest.requestIds != manifest.songIds) {
            errors += "Request IDs do not match song IDs"
        }

        val resourceIds = manifest.exportedCacheResources.map(CarPortableResource::songId)
        if (resourceIds != manifest.songIds) {
            errors += "Cache resource count or song IDs mismatch"
        }
        val cacheBytes = manifest.exportedCacheResources.sumOf(CarPortableResource::contentLength)
        if (cacheBytes != manifest.totalCacheBytes) {
            errors += "Total cache byte count mismatch"
        }
        val artworkBytes = manifest.files.filter { it.kind == "artwork" }.sumOf(CarPortableFile::size)
        if (artworkBytes != manifest.totalArtworkBytes) {
            errors += "Total artwork byte count mismatch"
        }
    }

    private fun validateSpace(
        manifest: CarPortableManifest,
        availableDestinationBytes: Long,
        errors: MutableList<String>,
    ) {
        val required =
            runCatching {
                Math.addExact(manifest.totalCacheBytes, manifest.totalArtworkBytes)
            }.getOrElse {
                errors += "Invalid required destination size"
                Long.MAX_VALUE
            }
        if (required < 0L || availableDestinationBytes < required) {
            errors += "Insufficient destination space"
        }
    }

    private fun validateFiles(
        entries: Map<String, EntryObservation>,
        manifest: CarPortableManifest,
        errors: MutableList<String>,
    ): Map<String, CarPortableFile> {
        val duplicates =
            manifest.files
                .groupingBy(CarPortableFile::relativePath)
                .eachCount()
                .filterValues { it > 1 }
                .keys
        duplicates.forEach { errors += "Duplicate manifest file: $it" }

        val files = manifest.files.associateBy(CarPortableFile::relativePath)
        REQUIRED_METADATA.forEach { required ->
            if (required !in files) errors += "Required metadata declaration missing: $required"
        }

        manifest.files.forEach { file ->
            if (!isSafeRelativePath(file.relativePath)) {
                errors += "Unsafe manifest path: ${file.relativePath}"
                return@forEach
            }
            if (file.size < 0L) {
                errors += "Invalid file size: ${file.relativePath}"
                return@forEach
            }

            val entry = entries[file.relativePath]
            if (entry == null) {
                errors += "Missing entry: ${file.relativePath}"
                return@forEach
            }
            if (!entry.isDirectory && entry.method != ZipEntry.DEFLATED) {
                errors += "Unexpected ZIP method: ${file.relativePath}"
            }
            if (entry.size != file.size) {
                errors += "File size mismatch: ${file.relativePath}"
            }
            if (entry.sha256 != file.sha256) {
                errors += "Hash mismatch: ${file.relativePath}"
            }
        }

        val declared = manifest.files.map(CarPortableFile::relativePath).toSet()
        entries.keys
            .filter { it != MANIFEST && it !in declared }
            .forEach { errors += "Undeclared archive entry: $it" }

        return files
    }

    private fun validateResources(
        entries: Map<String, EntryObservation>,
        files: Map<String, CarPortableFile>,
        manifest: CarPortableManifest,
        errors: MutableList<String>,
    ) {
        manifest.exportedCacheResources.forEach { resource ->
            if (resource.cacheKey != resource.songId) {
                errors += "Cache key mismatch for ${resource.songId}"
            }
            if (resource.contentLength < 0L) {
                errors += "Invalid resource length for ${resource.songId}"
            }

            var expectedPosition = 0L
            resource.spans.forEach { span ->
                if (span.cacheKey != resource.cacheKey) {
                    errors += "Span cache key mismatch for ${resource.songId}"
                }
                if (span.position != expectedPosition || span.length <= 0L) {
                    errors += "Span coverage mismatch for ${resource.songId}"
                }
                if (!span.relativePath.startsWith("media3_cache/files/")) {
                    errors += "Invalid span path for ${resource.songId}"
                }

                val entry = entries[span.relativePath]
                val file = files[span.relativePath]
                if (entry == null || file == null) {
                    errors += "Missing span entry: ${span.relativePath}"
                } else {
                    if (
                        entry.size != span.length ||
                        file.size != span.length ||
                        file.kind != "media3_cache_span"
                    ) {
                        errors += "Span size mismatch: ${span.relativePath}"
                    }
                    if (file.sha256 != span.sha256) {
                        errors += "Span hash declaration mismatch: ${span.relativePath}"
                    }
                    if (entry.sha256 != span.sha256) {
                        errors += "Span hash mismatch: ${span.relativePath}"
                    }
                }
                expectedPosition =
                    runCatching { Math.addExact(expectedPosition, span.length) }
                        .getOrElse {
                            errors += "Span length overflow for ${resource.songId}"
                            Long.MAX_VALUE
                        }
            }
            if (expectedPosition != resource.contentLength) {
                errors += "Span length mismatch for ${resource.songId}"
            }
        }
    }

    private fun validateMetadata(
        entries: Map<String, EntryObservation>,
        manifest: CarPortableManifest,
        songs: List<CarPortableSong>,
        artists: List<CarPortableArtist>,
        albums: List<CarPortableAlbum>,
        artistMaps: List<CarPortableMap>,
        albumMaps: List<CarPortableMap>,
        formats: List<CarPortableFormat>,
        transfers: List<CarPortableTransfer>,
        errors: MutableList<String>,
    ) {
        if (songs.map(CarPortableSong::id) != manifest.songIds) {
            errors += "Song metadata count or IDs mismatch"
        }
        if (formats.map(CarPortableFormat::id) != manifest.songIds) {
            errors += "Format metadata count or IDs mismatch"
        }
        if (transfers.map(CarPortableTransfer::requestId) != manifest.requestIds) {
            errors += "Download metadata count or IDs mismatch"
        }
        if (albums.isNotEmpty()) {
            errors += "Compatibility profile requires empty albums metadata"
        }
        if (albumMaps.isNotEmpty()) {
            errors += "Compatibility profile requires empty song-album map"
        }

        val resources = manifest.exportedCacheResources.associateBy(CarPortableResource::songId)
        formats.forEach { format ->
            val expected = resources[format.id]?.contentLength
            if (expected == null || format.contentLength != expected) {
                errors += "Format content length mismatch for ${format.id}"
            }
        }
        transfers.forEach { transfer ->
            val expected = resources[transfer.requestId]?.contentLength
            if (
                transfer.uri != transfer.requestId ||
                transfer.customCacheKey != transfer.requestId ||
                expected == null ||
                transfer.contentLength != expected ||
                transfer.bytesDownloaded != expected
            ) {
                errors += "Download metadata mismatch for ${transfer.requestId}"
            }
        }

        val songIds = manifest.songIds.toSet()
        val artistIds = artists.map(CarPortableArtist::id).toSet()
        artistMaps.forEach { map ->
            if (map.songId !in songIds || map.artistId !in artistIds) {
                errors += "Invalid song-artist mapping"
            }
        }
        songs.forEach { song ->
            song.thumbnailArtworkPath?.let { artwork ->
                val declared =
                    manifest.files.any {
                        it.relativePath == artwork && it.kind == "artwork"
                    }
                if (!declared || entries[artwork] == null) {
                    errors += "Missing artwork entry for ${song.id}"
                }
            }
        }
    }

    private inline fun <reified T> decode(
        entries: Map<String, EntryObservation>,
        path: String,
        errors: MutableList<String>,
    ): T? {
        val bytes = entries[path]?.captured
        if (bytes == null) {
            errors += "Required metadata entry missing: $path"
            return null
        }
        return runCatching {
            json.decodeFromString<T>(bytes.decodeToString())
        }.onFailure {
            errors += "Invalid metadata schema: $path"
        }.getOrNull()
    }

    private fun readEntry(
        zip: ZipInputStream,
        capture: Boolean,
    ): EntryObservation {
        val digest = MessageDigest.getInstance("SHA-256")
        val output = if (capture) ByteArrayOutputStream() else null
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var size = 0L
        var captureOverflow = false

        while (true) {
            val count = zip.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            size += count
            digest.update(buffer, 0, count)
            if (output != null && !captureOverflow) {
                if (size <= MAX_CAPTURE_BYTES) {
                    output.write(buffer, 0, count)
                } else {
                    captureOverflow = true
                    output.reset()
                }
            }
        }

        return EntryObservation(
            name = "",
            method = -1,
            isDirectory = false,
            size = size,
            sha256 = digest.digest().joinToString("") { "%02x".format(it) },
            captured = if (captureOverflow) null else output?.toByteArray(),
            captureOverflow = captureOverflow,
        )
    }

    private fun isSafeRelativePath(path: String): Boolean =
        path.isNotBlank() &&
            !path.startsWith("/") &&
            !path.startsWith("\\") &&
            !path.contains("../") &&
            !path.contains("..\\")

    private data class EntryObservation(
        val name: String,
        val method: Int,
        val isDirectory: Boolean,
        val size: Long,
        val sha256: String,
        val captured: ByteArray?,
        val captureOverflow: Boolean,
    )

    private companion object {
        const val MANIFEST = "manifest.json"
        const val MAX_CAPTURE_BYTES = 32L * 1024L * 1024L
        val REQUIRED_METADATA =
            setOf(
                "metadata/songs.json",
                "metadata/artists.json",
                "metadata/albums.json",
                "metadata/song_artist_map.json",
                "metadata/song_album_map.json",
                "metadata/formats.json",
                "metadata/downloads.json",
            )
    }
}
