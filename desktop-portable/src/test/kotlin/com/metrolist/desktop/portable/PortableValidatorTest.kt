package com.metrolist.desktop.portable

import com.metrolist.desktop.stream.ResolvedAudio
import com.metrolist.innertube.models.SongItem
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PortableValidatorTest {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
    }

    @Test
    fun rejectsDeclaredFileSizeMismatch() {
        val valid = validBackup("size")
        val tampered = rewriteManifest(valid, "size-tampered") { manifest ->
            val first = manifest.files.first()
            manifest.copy(files = listOf(first.copy(size = first.size + 1)) + manifest.files.drop(1))
        }

        val result = PortableValidator().validate(tampered)

        assertFalse(result.valid)
        assertTrue(result.errors.any { "size" in it.lowercase() })
    }

    @Test
    fun rejectsWrongCompatibilityProfile() {
        val valid = validBackup("profile")
        val tampered = rewriteManifest(valid, "profile-tampered") { manifest ->
            manifest.copy(
                appName = "WrongApp",
                sourceDatabaseVersion = 999,
                cacheStrategy = "wrong-cache",
            )
        }

        val result = PortableValidator().validate(tampered)

        assertFalse(result.valid)
        assertTrue(result.errors.any { "profile" in it.lowercase() })
    }

    @Test
    fun rejectsManifestCountMismatch() {
        val valid = validBackup("count")
        val tampered = rewriteManifest(valid, "count-tampered") { manifest ->
            manifest.copy(exportedSongCount = manifest.exportedSongCount + 1)
        }

        val result = PortableValidator().validate(tampered)

        assertFalse(result.valid)
        assertTrue(result.errors.any { "count" in it.lowercase() })
    }

    @Test
    fun rejectsMissingRequiredMetadataDeclaration() {
        val valid = validBackup("metadata")
        val tampered = rewriteManifest(valid, "metadata-tampered") { manifest ->
            manifest.copy(
                files = manifest.files.filterNot {
                    it.relativePath == "metadata/downloads.json"
                },
            )
        }

        val result = PortableValidator().validate(tampered)

        assertFalse(result.valid)
        assertTrue(result.errors.any { "required" in it.lowercase() })
    }

    @Test
    fun rejectsSpanHashThatDisagreesWithArchive() {
        val valid = validBackup("span")
        val tampered = rewriteManifest(valid, "span-tampered") { manifest ->
            val resource = manifest.exportedCacheResources.single()
            val span = resource.spans.single()
            manifest.copy(
                exportedCacheResources =
                    listOf(resource.copy(spans = listOf(span.copy(sha256 = "0".repeat(64))))),
            )
        }

        val result = PortableValidator().validate(tampered)

        assertFalse(result.valid)
        assertTrue(result.errors.any { "span hash" in it.lowercase() })
    }

    private fun validBackup(name: String): Path {
        val dir = Files.createTempDirectory("portable-validator-$name")
        val media = dir.resolve("song.media")
        Files.write(media, ByteArray(64) { (it + 1).toByte() })
        val output = dir.resolve("$name.metrolistbackup")

        PortableBackupWriter().write(
            items = listOf(portableItem(media)),
            destination = output,
            createdAt = LocalDateTime.parse("2026-09-26T15:00:00"),
        )
        return output
    }

    private fun portableItem(media: Path): PortableItem =
        PortableItem(
            song = SongItem(
                id = "validator-song",
                title = "Validator Song",
                artists = emptyList(),
                thumbnail = "",
            ),
            resolved = ResolvedAudio(
                videoId = "validator-song",
                url = "https://redacted.invalid",
                headers = emptyMap(),
                clientName = "TEST",
                profileId = "TEST",
                expiresAtEpochMs = null,
                requireBoundedRange = false,
                rangeChunkSizeBytes = 1_048_576,
                useRangeChunks = false,
                itag = 251,
                mimeType = "audio/webm",
                codecs = "opus",
                bitrate = 144_000,
                sampleRate = 48_000,
                contentLength = Files.size(media),
                loudnessDb = null,
                perceptualLoudnessDb = null,
            ),
            mediaPath = media,
            downloadedAt = LocalDateTime.parse("2026-09-26T14:59:00"),
            startTimeMs = 1L,
            updateTimeMs = 2L,
        )

    private fun rewriteManifest(
        source: Path,
        suffix: String,
        transform: (PortableManifest) -> PortableManifest,
    ): Path {
        val destination = source.resolveSibling("$suffix.metrolistbackup")
        ZipFile(source.toFile()).use { zip ->
            val manifestEntry = requireNotNull(zip.getEntry("manifest.json"))
            val manifest = zip.getInputStream(manifestEntry).bufferedReader().use {
                json.decodeFromString<PortableManifest>(it.readText())
            }
            val replacement = json.encodeToString(transform(manifest)).encodeToByteArray()

            ZipOutputStream(Files.newOutputStream(destination)).use { output ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    output.putNextEntry(ZipEntry(entry.name))
                    if (entry.name == "manifest.json") {
                        output.write(replacement)
                    } else {
                        zip.getInputStream(entry).use { input -> input.copyTo(output) }
                    }
                    output.closeEntry()
                }
            }
        }
        return destination
    }
}
