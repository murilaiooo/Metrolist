package com.metrolist.desktop.portable

import com.metrolist.desktop.stream.ResolvedAudio
import com.metrolist.innertube.models.SongItem
import java.nio.file.Files
import java.time.LocalDateTime
import java.util.zip.ZipFile
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PortableBackupWriterTest {
    @Test
    fun refusesExportWhenDestinationLacksSafetyMargin() {
        val dir = Files.createTempDirectory("metrolist-portable-space")
        val media = dir.resolve("song.audio")
        Files.write(media, ByteArray(1024) { 1 })
        val item = portableItem(media, 1024L)

        val error = assertFailsWith<IllegalStateException> {
            PortableBackupWriter(
                usableSpace = { 512L },
            ).write(
                items = listOf(item),
                destination = dir.resolve("too-small.metrolistbackup"),
            )
        }

        assertTrue(error.message.orEmpty().contains("space", ignoreCase = true))
    }

    @Test
    fun writesValidatedSingleSongBackup() {
        val dir = Files.createTempDirectory("metrolist-portable")
        val media = dir.resolve("song.audio")
        val bytes = ByteArray(5_847_658) { (it % 251).toByte() }
        Files.write(media, bytes)

        val item =
            PortableItem(
                song =
                    SongItem(
                        id = "WcHpojJz8Bc",
                        title = "(Don't Fear) The Reaper (Single Version)",
                        artists = emptyList(),
                        duration = 230,
                        thumbnail = "https://example.invalid/thumb",
                        explicit = false,
                    ),
                resolved =
                    ResolvedAudio(
                        videoId = "WcHpojJz8Bc",
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
                        bitrate = 144_748,
                        sampleRate = 48_000,
                        contentLength = bytes.size.toLong(),
                        loudnessDb = -6.93,
                        perceptualLoudnessDb = -13.93,
                    ),
                mediaPath = media,
                downloadedAt = LocalDateTime.parse("2026-09-26T15:00:00"),
                startTimeMs = 1_000L,
                updateTimeMs = 2_000L,
            )

        val output = dir.resolve("test.metrolistbackup")
        val written =
            PortableBackupWriter().write(
                items = listOf(item),
                destination = output,
                createdAt = LocalDateTime.parse("2026-09-26T15:01:00"),
            )

        assertEquals(output, written)
        assertTrue(PortableValidator().validate(output).valid)

        ZipFile(output.toFile()).use { zip ->
            val first = zip.getInputStream(
                zip.getEntry("media3_cache/files/f9688284ac58d7e4/0_5242880_0.span"),
            ).readBytes()
            val second = zip.getInputStream(
                zip.getEntry("media3_cache/files/f9688284ac58d7e4/5242880_604778_1.span"),
            ).readBytes()
            assertContentEquals(bytes, first + second)
        }
    }

    @Test
    fun reportsExportProgressThroughEverySafetyPhase() {
        val dir = Files.createTempDirectory("metrolist-portable-progress")
        val media = dir.resolve("song.audio")
        Files.write(media, ByteArray(2048) { 3 })
        val phases = mutableListOf<PortableExportPhase>()

        val output = dir.resolve("progress.metrolistbackup")
        PortableBackupWriter().write(
            items = listOf(portableItem(media, 2048L)),
            destination = output,
            createdAt = LocalDateTime.parse("2026-09-26T15:01:00"),
            onProgress = { progress ->
                phases += progress.phase
            },
        )

        assertTrue(PortableExportPhase.PREPARING in phases)
        assertTrue(PortableExportPhase.WRITING in phases)
        assertTrue(PortableExportPhase.VALIDATING in phases)
        assertTrue(PortableExportPhase.FINALIZING in phases)
        assertEquals(PortableExportPhase.COMPLETE, phases.last())
    }

    @Test
    fun includesCachedArtworkInBackupAndMetadata() {
        val dir = Files.createTempDirectory("metrolist-portable-artwork")
        val media = dir.resolve("song.audio")
        Files.write(media, ByteArray(1024) { 7 })
        val artwork = dir.resolve("cover.img")
        val artworkBytes = byteArrayOf(9, 8, 7, 6, 5)
        Files.write(artwork, artworkBytes)
        val thumbnailUrl = "https://example.invalid/cover?id=1"

        val item =
            portableItem(media, 1024L).copy(
                song = portableItem(media, 1024L).song.copy(thumbnail = thumbnailUrl),
                artworkPath = artwork,
            )

        val output = dir.resolve("with-artwork.metrolistbackup")
        PortableBackupWriter().write(
            items = listOf(item),
            destination = output,
            createdAt = LocalDateTime.parse("2026-09-26T15:01:00"),
        )

        val artworkEntry = "artwork/" + SpanPlanner.artworkFileName(thumbnailUrl)
        ZipFile(output.toFile()).use { zip ->
            assertContentEquals(
                artworkBytes,
                zip.getInputStream(zip.getEntry(artworkEntry)).readBytes(),
            )
            val manifest =
                zip.getInputStream(zip.getEntry("manifest.json"))
                    .bufferedReader()
                    .use { Json.decodeFromString<PortableManifest>(it.readText()) }
            assertEquals(artworkBytes.size.toLong(), manifest.totalArtworkBytes)
            assertTrue(manifest.files.any { it.relativePath == artworkEntry && it.kind == "artwork" })

            val songs =
                zip.getInputStream(zip.getEntry("metadata/songs.json"))
                    .bufferedReader()
                    .use { Json.decodeFromString<SongsEnvelope>(it.readText()) }
            assertEquals(artworkEntry, songs.songs.single().thumbnailArtworkPath)
        }
    }

    private fun portableItem(
        media: java.nio.file.Path,
        length: Long,
    ) = PortableItem(
        song = SongItem(
            id = "space-test",
            title = "Space Test",
            artists = emptyList(),
            thumbnail = "",
        ),
        resolved = ResolvedAudio(
            videoId = "space-test",
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
            contentLength = length,
            loudnessDb = null,
            perceptualLoudnessDb = null,
        ),
        mediaPath = media,
        downloadedAt = LocalDateTime.parse("2026-09-26T15:00:00"),
        startTimeMs = 1L,
        updateTimeMs = 2L,
    )
}
