package com.metrolist.desktop.portable

import java.io.InputStream
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

data class ValidationResult(
    val valid: Boolean,
    val errors: List<String>,
)

class PortableValidator(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = true
    },
) {
    fun validate(path: Path): ValidationResult {
        val errors = mutableListOf<String>()

        runCatching {
            ZipFile(path.toFile()).use { zip ->
                val entries = archiveEntries(zip, errors)
                val manifestEntry = entries["manifest.json"] ?: error("manifest.json missing")
                requireDeflated(manifestEntry, errors)
                val manifest = zip.getInputStream(manifestEntry).bufferedReader().use {
                    json.decodeFromString<PortableManifest>(it.readText())
                }

                validateProfile(manifest, errors)
                val files = validateFiles(zip, entries, manifest, errors)
                validateResources(zip, entries, files, manifest, errors)
                validateMetadata(zip, manifest, errors)

                val declared = manifest.files.map(PortableFile::relativePath).toSet()
                entries.keys.filter { it != "manifest.json" && it !in declared }.forEach {
                    errors += "Undeclared archive entry: $it"
                }
            }
        }.onFailure { error ->
            errors += "Archive validation failed: ${error::class.simpleName}"
        }

        return ValidationResult(errors.isEmpty(), errors)
    }
    private fun validateProfile(
        manifest: PortableManifest,
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

        val resourceIds = manifest.exportedCacheResources.map(PortableResource::songId)
        if (resourceIds != manifest.songIds) {
            errors += "Cache resource count or song IDs mismatch"
        }
        val cacheBytes = manifest.exportedCacheResources.sumOf(PortableResource::contentLength)
        if (cacheBytes != manifest.totalCacheBytes) {
            errors += "Total cache byte count mismatch"
        }
        val artworkBytes = manifest.files.filter { it.kind == "artwork" }.sumOf(PortableFile::size)
        if (artworkBytes != manifest.totalArtworkBytes) {
            errors += "Total artwork byte count mismatch"
        }
    }

    private fun validateFiles(
        zip: ZipFile,
        entries: Map<String, ZipEntry>,
        manifest: PortableManifest,
        errors: MutableList<String>,
    ): Map<String, PortableFile> {
        val duplicates = manifest.files.groupingBy(PortableFile::relativePath)
            .eachCount().filterValues { it > 1 }.keys
        duplicates.forEach { errors += "Duplicate manifest file: $it" }

        val files = manifest.files.associateBy(PortableFile::relativePath)
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
            requireDeflated(entry, errors)
            if (entry.size != file.size) {
                errors += "File size mismatch: ${file.relativePath}"
            }
            val digest = zip.getInputStream(entry).use(::sha256)
            if (digest != file.sha256) {
                errors += "Hash mismatch: ${file.relativePath}"
            }
        }

        return files
    }

    private fun validateResources(
        zip: ZipFile,
        entries: Map<String, ZipEntry>,
        files: Map<String, PortableFile>,
        manifest: PortableManifest,
        errors: MutableList<String>,
    ) {
        manifest.exportedCacheResources.forEach { resource ->
            if (resource.cacheKey != resource.songId) {
                errors += "Cache key mismatch for ${resource.songId}"
            }
            if (resource.contentLength < 0L) {
                errors += "Invalid resource length for ${resource.songId}"
            }

            var position = 0L
            resource.spans.forEach { span ->
                if (span.cacheKey != resource.cacheKey) {
                    errors += "Span cache key mismatch for ${resource.songId}"
                }
                if (span.position != position || span.length <= 0L) {
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
                    val digest = zip.getInputStream(entry).use(::sha256)
                    if (digest != span.sha256) {
                        errors += "Span hash mismatch: ${span.relativePath}"
                    }
                }
                position += span.length
            }
            if (position != resource.contentLength) {
                errors += "Span length mismatch for ${resource.songId}"
            }
        }
    }
    private fun validateMetadata(
        zip: ZipFile,
        manifest: PortableManifest,
        errors: MutableList<String>,
    ) {
        val songs = decode<SongsEnvelope>(zip, "metadata/songs.json", errors)?.songs
        val artists = decode<ArtistsEnvelope>(zip, "metadata/artists.json", errors)?.artists
        val albums = decode<AlbumsEnvelope>(zip, "metadata/albums.json", errors)?.albums
        val artistMaps = decode<MapsEnvelope>(zip, "metadata/song_artist_map.json", errors)?.maps
        val albumMaps = decode<MapsEnvelope>(zip, "metadata/song_album_map.json", errors)?.maps
        val formats = decode<FormatsEnvelope>(zip, "metadata/formats.json", errors)?.formats
        val transfers = decode<TransfersEnvelope>(zip, "metadata/downloads.json", errors)?.downloads

        if (songs != null && songs.map(PortableSong::id) != manifest.songIds) {
            errors += "Song metadata count or IDs mismatch"
        }
        if (formats != null && formats.map(PortableFormat::id) != manifest.songIds) {
            errors += "Format metadata count or IDs mismatch"
        }
        if (transfers != null && transfers.map(PortableTransfer::requestId) != manifest.requestIds) {
            errors += "Download metadata count or IDs mismatch"
        }
        if (albums != null && albums.isNotEmpty()) {
            errors += "Compatibility profile requires empty albums metadata"
        }
        if (albumMaps != null && albumMaps.isNotEmpty()) {
            errors += "Compatibility profile requires empty song-album map"
        }

        val resources = manifest.exportedCacheResources.associateBy(PortableResource::songId)
        formats?.forEach { format ->
            val expected = resources[format.id]?.contentLength
            if (expected == null || format.contentLength != expected) {
                errors += "Format content length mismatch for ${format.id}"
            }
        }
        transfers?.forEach { transfer ->
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
        val artistIds = artists?.map(PortableArtist::id)?.toSet().orEmpty()
        artistMaps?.forEach { map ->
            if (map.songId !in songIds || map.artistId !in artistIds) {
                errors += "Invalid song-artist mapping"
            }
        }
        songs?.forEach { song ->
            song.thumbnailArtworkPath?.let { artwork ->
                val declared = manifest.files.any {
                    it.relativePath == artwork && it.kind == "artwork"
                }
                if (!declared || zip.getEntry(artwork) == null) {
                    errors += "Missing artwork entry for ${song.id}"
                }
            }
        }
    }
    private inline fun <reified T> decode(
        zip: ZipFile,
        path: String,
        errors: MutableList<String>,
    ): T? {
        val entry = zip.getEntry(path)
        if (entry == null) {
            errors += "Required metadata entry missing: $path"
            return null
        }
        return runCatching {
            zip.getInputStream(entry).bufferedReader().use {
                json.decodeFromString<T>(it.readText())
            }
        }.onFailure {
            errors += "Invalid metadata schema: $path"
        }.getOrNull()
    }

    private fun archiveEntries(
        zip: ZipFile,
        errors: MutableList<String>,
    ): Map<String, ZipEntry> {
        val result = linkedMapOf<String, ZipEntry>()
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (result.put(entry.name, entry) != null) {
                errors += "Duplicate archive entry: ${entry.name}"
            }
        }
        return result
    }

    private fun requireDeflated(
        entry: ZipEntry,
        errors: MutableList<String>,
    ) {
        if (!entry.isDirectory && entry.method != ZipEntry.DEFLATED) {
            errors += "Unexpected ZIP method: ${entry.name}"
        }
    }
    private fun isSafeRelativePath(path: String): Boolean =
        path.isNotBlank() &&
            !path.startsWith("/") &&
            !path.startsWith("\\") &&
            !path.contains("../") &&
            !path.contains("..\\")

    private fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count > 0) digest.update(buffer, 0, count)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        val REQUIRED_METADATA = setOf(
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
