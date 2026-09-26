package com.metrolist.desktop.portable

import com.metrolist.desktop.stream.ResolvedAudio
import com.metrolist.innertube.models.SongItem
import java.nio.file.Files
import java.time.LocalDateTime
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PortableBackupWriterTest {
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
}
