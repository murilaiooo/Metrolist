package com.metrolist.music.car.library

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

data class CarLibraryValidationResult(
    val manifest: CarLibraryManifest?,
    val errors: List<String>,
) {
    val valid: Boolean
        get() = manifest != null && errors.isEmpty()
}

class CarLibraryValidator(
    private val json: Json =
        Json {
            ignoreUnknownKeys = false
            explicitNulls = true
        },
) {
    fun parseAndValidate(raw: String): CarLibraryValidationResult {
        val manifest =
            runCatching {
                json.decodeFromString<CarLibraryManifest>(raw)
            }.getOrElse { error ->
                return CarLibraryValidationResult(
                    manifest = null,
                    errors = listOf("Invalid library.json schema: ${error::class.simpleName}"),
                )
            }
        return validate(manifest)
    }

    fun validate(manifest: CarLibraryManifest): CarLibraryValidationResult {
        val errors = mutableListOf<String>()

        if (manifest.formatVersion != FORMAT_VERSION) {
            errors += "Unsupported format version: ${manifest.formatVersion}"
        }
        if (manifest.libraryId.isBlank()) errors += "Missing library ID"
        if (manifest.revision.isBlank()) errors += "Missing library revision"
        if (manifest.createdAt.isBlank()) errors += "Missing createdAt"
        if (manifest.updatedAt.isBlank()) errors += "Missing updatedAt"
        if (manifest.producer.product.isBlank() || manifest.producer.version.isBlank()) {
            errors += "Invalid producer identity"
        }

        val songIds = manifest.songs.map(CarLibrarySong::id)
        if (songIds.size != songIds.toSet().size) {
            errors += "Duplicate song IDs"
        }

        val mediaPaths = manifest.songs.map(CarLibrarySong::mediaPath)
        if (mediaPaths.size != mediaPaths.toSet().size) {
            errors += "Duplicate media paths"
        }

        manifest.songs.forEach { song ->
            if (song.id.isBlank()) errors += "Blank song ID"
            if (song.title.isBlank()) errors += "Blank song title: ${song.id}"
            if (!isSafeRelativePath(song.mediaPath, MEDIA_ROOT)) {
                errors += "Unsafe media path: ${song.mediaPath}"
            }
            song.artworkPath?.let { artwork ->
                if (!isSafeRelativePath(artwork, ARTWORK_ROOT)) {
                    errors += "Unsafe artwork path: $artwork"
                }
            }
            if (song.fileSize < 0L) errors += "Invalid media file size: ${song.id}"
            if (!SHA256.matches(song.sha256)) errors += "Invalid media hash: ${song.id}"
            if (song.duration != null && song.duration < 0) errors += "Invalid duration: ${song.id}"
            if (song.bitrate != null && song.bitrate <= 0) errors += "Invalid bitrate: ${song.id}"
            if (song.sampleRate != null && song.sampleRate <= 0) errors += "Invalid sample rate: ${song.id}"
            if (song.artworkFileSize != null && song.artworkFileSize < 0L) {
                errors += "Invalid artwork file size: ${song.id}"
            }
            if (song.artworkSha256 != null && !SHA256.matches(song.artworkSha256)) {
                errors += "Invalid artwork hash: ${song.id}"
            }
            if (song.artworkWidth != null && song.artworkWidth <= 0) {
                errors += "Invalid artwork width: ${song.id}"
            }
            if (song.artworkHeight != null && song.artworkHeight <= 0) {
                errors += "Invalid artwork height: ${song.id}"
            }
            song.artists.forEach { artist ->
                if (artist.name.isBlank()) errors += "Blank artist name: ${song.id}"
            }
        }

        val playlistIds = manifest.playlists.map(CarLibraryPlaylist::id)
        if (playlistIds.size != playlistIds.toSet().size) {
            errors += "Duplicate playlist IDs"
        }

        val knownSongs = songIds.toSet()
        manifest.playlists.forEach { playlist ->
            if (playlist.id.isBlank()) errors += "Blank playlist ID"
            if (playlist.name.isBlank()) errors += "Blank playlist name: ${playlist.id}"
            playlist.songIds.forEach { songId ->
                if (songId !in knownSongs) errors += "Unknown playlist song ID: $songId"
            }
        }

        return CarLibraryValidationResult(
            manifest = manifest,
            errors = errors.distinct(),
        )
    }

    private fun isSafeRelativePath(
        path: String,
        requiredRoot: String,
    ): Boolean {
        if (
            path.isBlank() ||
            path.startsWith("/") ||
            path.startsWith("\\") ||
            path.contains("\\")
        ) {
            return false
        }
        if (WINDOWS_DRIVE.containsMatchIn(path)) return false

        val segments = path.split("/")
        if (segments.size < 2 || segments.first() != requiredRoot) return false

        return segments.all { segment ->
            segment.isNotBlank() &&
                segment != "." &&
                segment != ".." &&
                !segment.endsWith(" ") &&
                !segment.endsWith(".") &&
                segment.none { it.isISOControl() || it in INVALID_FILENAME_CHARS } &&
                !WINDOWS_RESERVED.matches(segment)
        }
    }

    private companion object {
        const val FORMAT_VERSION = 1
        const val MEDIA_ROOT = "media"
        const val ARTWORK_ROOT = "artwork"

        val SHA256 = Regex("^[0-9a-fA-F]{64}$")
        val WINDOWS_DRIVE = Regex("^[A-Za-z]:")
        val WINDOWS_RESERVED =
            Regex("^(?i:CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?$")
        val INVALID_FILENAME_CHARS = setOf('<', '>', ':', '"', '|', '?', '*')
    }
}
