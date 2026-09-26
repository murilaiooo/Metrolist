package com.metrolist.desktop.media

import com.metrolist.desktop.stream.ResolvedAudio
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ResolvedMediaFetcherTest {
    @Test
    fun fetchesMediaInRangesAndPromotesOnlyAfterComplete() {
        val bytes = ByteArray(10) { it.toByte() }
        val starts = mutableListOf<Long>()
        val transport = MediaRangeTransport { _, start, end ->
            starts += start
            val slice = bytes.copyOfRange(start.toInt(), end.toInt() + 1)
            MediaRangeResponse(206, slice)
        }
        val destination = testPath("range.media")

        val written = ResolvedMediaFetcher(
            transport = transport,
            chunkSizeOverride = 4L,
        ).fetch(resolved(bytes.size.toLong()), destination)

        assertEquals(listOf(0L, 4L, 8L), starts)
        assertContentEquals(bytes, Files.readAllBytes(destination))
        assertEquals(10L, written.bytes)
    }

    @Test
    fun resumesFromExistingPartialFile() {
        val bytes = ByteArray(10) { (it + 10).toByte() }
        val starts = mutableListOf<Long>()
        val destination = testPath("resume.media")
        Files.write(
            destination.resolveSibling("resume.media.part"),
            bytes.copyOfRange(0, 4),
        )

        val transport = MediaRangeTransport { _, start, end ->
            starts += start
            MediaRangeResponse(
                206,
                bytes.copyOfRange(start.toInt(), end.toInt() + 1),
            )
        }

        ResolvedMediaFetcher(
            transport = transport,
            chunkSizeOverride = 4L,
        ).fetch(resolved(bytes.size.toLong()), destination)

        assertEquals(listOf(4L, 8L), starts)
        assertContentEquals(bytes, Files.readAllBytes(destination))
    }

    @Test
    fun expiredResponseIsReportedForFreshResolution() {
        val destination = testPath("expired.media")
        val fetcher = ResolvedMediaFetcher(
            transport = MediaRangeTransport { _, _, _ ->
                MediaRangeResponse(403, ByteArray(0))
            },
            chunkSizeOverride = 4L,
        )

        assertFailsWith<ExpiredMediaUrlException> {
            fetcher.fetch(resolved(10L), destination)
        }
    }

    private fun resolved(length: Long) =
        ResolvedAudio(
            videoId = "song-1",
            url = "https://redacted.invalid",
            headers = emptyMap(),
            clientName = "TEST",
            profileId = "TEST",
            expiresAtEpochMs = null,
            requireBoundedRange = false,
            rangeChunkSizeBytes = 4L,
            useRangeChunks = false,
            itag = 251,
            mimeType = "audio/webm",
            codecs = "opus",
            bitrate = 144_000,
            sampleRate = 48_000,
            contentLength = length,
            loudnessDb = null,
            perceptualLoudnessDb = null,
        )

    private fun testPath(name: String): Path {
        val dir = Path.of(System.getProperty("java.io.tmpdir"), "range-fetcher-test")
        Files.createDirectories(dir)
        val destination = dir.resolve(name)
        Files.deleteIfExists(destination)
        Files.deleteIfExists(destination.resolveSibling(name + ".part"))
        return destination
    }
}
