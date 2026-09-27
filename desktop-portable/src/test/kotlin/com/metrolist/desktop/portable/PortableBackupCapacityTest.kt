package com.metrolist.desktop.portable

import com.metrolist.desktop.stream.ResolvedAudio
import com.metrolist.innertube.models.SongItem
import java.nio.file.Files
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class PortableBackupCapacityTest {
    @Test
    fun rejectsDestinationWithoutEnoughSpaceBeforeCreatingPartialArchive() {
        val dir = Files.createTempDirectory("portable-capacity")
        val media = dir.resolve("song.media")
        Files.write(media, ByteArray(1024) { 7 })
        val output = dir.resolve("no-space.metrolistbackup")

        val writer = PortableBackupWriter(
            usableSpace = { 0L },
        )

        assertFailsWith<IllegalStateException> {
            writer.write(
                items = listOf(
                    PortableItem(
                        song = SongItem(
                            id = "capacity-song",
                            title = "Capacity Song",
                            artists = emptyList(),
                            thumbnail = "",
                        ),
                        resolved = ResolvedAudio(
                            videoId = "capacity-song",
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
                        downloadedAt = LocalDateTime.parse("2026-09-26T15:00:00"),
                        startTimeMs = 1L,
                        updateTimeMs = 2L,
                    ),
                ),
                destination = output,
            )
        }

        assertFalse(Files.exists(output))
        assertFalse(Files.exists(output.resolveSibling(output.fileName.toString() + ".part")))
    }
}
