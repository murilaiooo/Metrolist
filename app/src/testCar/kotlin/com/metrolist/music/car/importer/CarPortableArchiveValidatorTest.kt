package com.metrolist.music.car.importer

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarPortableArchiveValidatorTest {
    private val validator = CarPortableArchiveValidator()

    @Test
    fun validArchiveParsesPortableProfile() {
        val validated = validator.validate(ByteArrayInputStream(fixture()), 1_000_000L)

        assertEquals(listOf("song1"), validated.manifest.songIds)
        assertEquals("Song 1", validated.songs.single().title)
        assertEquals(4L, validated.resources.single().contentLength)
    }

    @Test fun wrongCompatibilityProfileIsRejected() =
        assertInvalid(fixture(appName = "Other"), "Compatibility profile mismatch")

    @Test fun duplicateSongIdsAreRejected() =
        assertInvalid(fixture(duplicateSongIds = true), "Duplicate song IDs")

    @Test fun missingDeclaredEntryIsRejected() =
        assertInvalid(fixture(omitFormatsEntry = true), "Missing entry: metadata/formats.json")

    @Test fun undeclaredEntryIsRejected() =
        assertInvalid(fixture(extraEntry = true), "Undeclared archive entry")

    @Test fun storedEntryIsRejected() =
        assertInvalid(fixture(storedSpan = true), "Unexpected ZIP method")

    @Test fun unsafeRelativePathIsRejected() =
        assertInvalid(fixture(unsafeSpanPath = true), "Unsafe manifest path")

    @Test fun hashMismatchIsRejected() =
        assertInvalid(fixture(corruptDeclaredSpanHash = true), "Hash mismatch")

    @Test fun spanGapIsRejected() =
        assertInvalid(fixture(spanPosition = 1L), "Span coverage mismatch")

    @Test fun malformedMetadataIsRejected() =
        assertInvalid(fixture(malformedSongs = true), "Invalid metadata schema")

    @Test fun insufficientSpaceIsRejectedBeforeImport() =
        assertInvalid(fixture(), "Insufficient destination space", availableBytes = 3L)

    private fun assertInvalid(
        bytes: ByteArray,
        expectedMessage: String,
        availableBytes: Long = 1_000_000L,
    ) {
        try {
            validator.validate(ByteArrayInputStream(bytes), availableBytes)
            throw AssertionError("Expected validation failure containing: $expectedMessage")
        } catch (error: CarPortableValidationException) {
            assertTrue(
                "Expected '$expectedMessage' in ${error.errors}",
                error.errors.any { it.contains(expectedMessage) },
            )
        }
    }

    private fun fixture(
        appName: String = "Metrolist",
        duplicateSongIds: Boolean = false,
        omitFormatsEntry: Boolean = false,
        extraEntry: Boolean = false,
        storedSpan: Boolean = false,
        unsafeSpanPath: Boolean = false,
        corruptDeclaredSpanHash: Boolean = false,
        spanPosition: Long = 0L,
        malformedSongs: Boolean = false,
    ): ByteArray {
        val spanPath = if (unsafeSpanPath) "../evil.span" else "media3_cache/files/abc/0_4_0.span"
        val spanBytes = "data".toByteArray(StandardCharsets.UTF_8)
        val metadata =
            linkedMapOf(
                "metadata/songs.json" to
                    if (malformedSongs) "{not-json".toByteArray() else
                        """{"songs":[{"id":"song1","title":"Song 1","duration":123,"thumbnailUrl":null,"thumbnailArtworkPath":null,"albumId":null,"albumName":null,"explicit":false,"year":null,"date":null,"dateModified":null,"dateDownload":"2026-09-27T00:00:00","isVideo":false,"isEpisode":false}]}""".toByteArray(),
                "metadata/artists.json" to
                    """{"artists":[{"id":"artist1","name":"Artist 1","thumbnailUrl":null,"channelId":null,"lastUpdateTime":null,"isPodcastChannel":false}]}""".toByteArray(),
                "metadata/albums.json" to """{"albums":[]}""".toByteArray(),
                "metadata/song_artist_map.json" to
                    """{"maps":[{"songId":"song1","artistId":"artist1","position":0}]}""".toByteArray(),
                "metadata/song_album_map.json" to """{"maps":[]}""".toByteArray(),
                "metadata/formats.json" to
                    """{"formats":[{"id":"song1","itag":251,"mimeType":"audio/webm","codecs":"opus","bitrate":128000,"sampleRate":48000,"contentLength":4,"loudnessDb":null,"perceptualLoudnessDb":null}]}""".toByteArray(),
                "metadata/downloads.json" to
                    """{"downloads":[{"requestId":"song1","uri":"song1","customCacheKey":"song1","title":"Song 1","contentLength":4,"startTimeMs":1,"updateTimeMs":2,"bytesDownloaded":4}]}""".toByteArray(),
            )

        val declaredMetadata = metadata.entries.map { (path, bytes) ->
            fileJson(path, bytes.size.toLong(), sha256(bytes), "metadata")
        }
        val declaredSpanHash = if (corruptDeclaredSpanHash) "0".repeat(64) else sha256(spanBytes)
        val filesJson =
            (declaredMetadata + fileJson(spanPath, spanBytes.size.toLong(), declaredSpanHash, "media3_cache_span"))
                .joinToString(",")
        val ids = if (duplicateSongIds) """["song1","song1"]""" else """["song1"]"""
        val exportedCount = if (duplicateSongIds) 2 else 1

        val manifest =
            """
            {
              "appName":"$appName",
              "backupFormatVersion":1,
              "createdAt":"2026-09-27T00:00:00",
              "sourceAppVersion":"13.6.0",
              "sourceDatabaseVersion":38,
              "exportedSongCount":$exportedCount,
              "exportedDownloadCount":1,
              "totalBackupBytes":null,
              "totalCacheBytes":4,
              "totalArtworkBytes":0,
              "type":"offline_downloads",
              "cacheStrategy":"media3_simple_cache_spans",
              "songIds":$ids,
              "media3DownloadRequestIds":["song1"],
              "exportedCacheResources":[{"songId":"song1","cacheKey":"song1","contentLength":4,"spans":[{"relativePath":"$spanPath","cacheKey":"song1","position":$spanPosition,"length":4,"sha256":"${sha256(spanBytes)}"}]}],
              "files":[$filesJson],
              "skippedSongs":[],
              "warnings":[],
              "errors":[]
            }
            """.trimIndent().toByteArray()

        return ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                zip.setLevel(Deflater.NO_COMPRESSION)
                put(zip, "manifest.json", manifest)
                metadata.forEach { (path, bytes) ->
                    if (!(omitFormatsEntry && path == "metadata/formats.json")) put(zip, path, bytes)
                }
                put(zip, spanPath, spanBytes, storedSpan)
                if (extraEntry) put(zip, "extra.bin", byteArrayOf(1, 2, 3))
            }
            output.toByteArray()
        }
    }

    private fun fileJson(path: String, size: Long, sha: String, kind: String): String =
        """{"relativePath":"$path","size":$size,"sha256":"$sha","kind":"$kind"}"""

    private fun put(zip: ZipOutputStream, name: String, bytes: ByteArray, stored: Boolean = false) {
        val entry = ZipEntry(name)
        if (stored) {
            val crc = CRC32().apply { update(bytes) }
            entry.method = ZipEntry.STORED
            entry.size = bytes.size.toLong()
            entry.compressedSize = bytes.size.toLong()
            entry.crc = crc.value
        }
        zip.putNextEntry(entry)
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
