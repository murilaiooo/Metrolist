package com.metrolist.desktop.portable

import java.io.BufferedOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun main(args: Array<String>) {
    require(args.size == 1) { "Expected output backup path" }

    val output = Path.of(args[0])
    output.parent?.let(Files::createDirectories)

    val logicalBytes = 4L * 1024L * 1024L * 1024L + 1024L * 1024L
    val cacheKey = "zip64-probe"
    val relativePath = "media3_cache/files/zip64probe000000/0_" + logicalBytes + "_0.span"
    val sha256 = sha256Zeros(logicalBytes)
    val json =
        Json {
            encodeDefaults = true
            explicitNulls = true
        }
    val metadataEntries = zip64MetadataEntries(json, cacheKey, logicalBytes)
    val metadataFiles =
        metadataEntries.map { (path, bytes) ->
            PortableFile(
                relativePath = path,
                size = bytes.size.toLong(),
                sha256 = sha256(bytes),
                kind = "metadata",
            )
        }

    val span =
        PortableSpan(
            relativePath = relativePath,
            cacheKey = cacheKey,
            position = 0L,
            length = logicalBytes,
            sha256 = sha256,
        )
    val resource =
        PortableResource(
            songId = cacheKey,
            cacheKey = cacheKey,
            contentLength = logicalBytes,
            spans = listOf(span),
        )
    val file =
        PortableFile(
            relativePath = relativePath,
            size = logicalBytes,
            sha256 = sha256,
            kind = "media3_cache_span",
        )
    val manifest =
        PortableManifest(
            createdAt = LocalDateTime.now().toString(),
            exportedSongCount = 1,
            exportedDownloadCount = 1,
            totalCacheBytes = logicalBytes,
            songIds = listOf(cacheKey),
            requestIds = listOf(cacheKey),
            exportedCacheResources = listOf(resource),
            files = metadataFiles + file,
        )

    val manifestBytes = json.encodeToString(manifest).encodeToByteArray()

    Files.deleteIfExists(output)
    ZipOutputStream(BufferedOutputStream(Files.newOutputStream(output))).use { zip ->
        zip.setLevel(Deflater.BEST_COMPRESSION)

        zip.putNextEntry(ZipEntry("manifest.json"))
        zip.write(manifestBytes)
        zip.closeEntry()

        metadataEntries.forEach { (path, bytes) ->
            zip.putNextEntry(ZipEntry(path))
            zip.write(bytes)
            zip.closeEntry()
        }

        zip.putNextEntry(ZipEntry(relativePath))
        writeZeros(zip, logicalBytes)
        zip.closeEntry()
    }

    val validation = PortableValidator().validate(output)
    check(validation.valid) {
        "Zip64 probe validation failed: " + validation.errors.joinToString("; ")
    }

    println(
        "ZIP64_PROBE_OK logicalBytes=" + logicalBytes +
            " archiveBytes=" + Files.size(output) +
            " sha256=" + sha256,
    )
}

private fun zip64MetadataEntries(
    json: Json,
    id: String,
    contentLength: Long,
): List<Pair<String, ByteArray>> {
    val song = PortableSong(
        id = id,
        title = "Zip64 Probe",
        duration = null,
        thumbnailUrl = null,
        thumbnailArtworkPath = null,
        albumId = null,
        albumName = null,
        explicit = false,
        year = null,
        date = null,
        dateModified = null,
        dateDownload = LocalDateTime.now().toString(),
        isVideo = false,
        isEpisode = false,
    )
    val format = PortableFormat(
        id = id,
        itag = 251,
        mimeType = "audio/webm",
        codecs = "opus",
        bitrate = 144_000,
        sampleRate = 48_000,
        contentLength = contentLength,
        loudnessDb = null,
        perceptualLoudnessDb = null,
    )
    val transfer = PortableTransfer(
        requestId = id,
        uri = id,
        customCacheKey = id,
        title = song.title,
        contentLength = contentLength,
        startTimeMs = 1L,
        updateTimeMs = 2L,
        bytesDownloaded = contentLength,
    )

    return listOf(
        "metadata/songs.json" to json.encodeToString(SongsEnvelope(listOf(song))).encodeToByteArray(),
        "metadata/artists.json" to json.encodeToString(ArtistsEnvelope(emptyList())).encodeToByteArray(),
        "metadata/albums.json" to json.encodeToString(AlbumsEnvelope()).encodeToByteArray(),
        "metadata/song_artist_map.json" to json.encodeToString(MapsEnvelope()).encodeToByteArray(),
        "metadata/song_album_map.json" to json.encodeToString(MapsEnvelope()).encodeToByteArray(),
        "metadata/formats.json" to json.encodeToString(FormatsEnvelope(listOf(format))).encodeToByteArray(),
        "metadata/downloads.json" to json.encodeToString(TransfersEnvelope(listOf(transfer))).encodeToByteArray(),
    )
}

private fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

private fun sha256Zeros(length: Long): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(1024 * 1024)
    var remaining = length

    while (remaining > 0L) {
        val count = minOf(buffer.size.toLong(), remaining).toInt()
        digest.update(buffer, 0, count)
        remaining -= count
    }

    return digest.digest().joinToString("") { "%02x".format(it) }
}

private fun writeZeros(
    output: java.io.OutputStream,
    length: Long,
) {
    val buffer = ByteArray(1024 * 1024)
    var remaining = length

    while (remaining > 0L) {
        val count = minOf(buffer.size.toLong(), remaining).toInt()
        output.write(buffer, 0, count)
        remaining -= count
    }
}
