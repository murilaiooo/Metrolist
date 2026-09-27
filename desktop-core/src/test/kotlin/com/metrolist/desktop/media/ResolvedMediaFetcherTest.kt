package com.metrolist.desktop.media

import com.metrolist.desktop.stream.ResolvedAudio
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

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
    fun reportsLiveByteProgressUntilComplete() {
        val bytes = ByteArray(10) { it.toByte() }
        val progress = mutableListOf<MediaTransferProgress>()
        val destination = testPath("progress.media")

        val transport = MediaRangeTransport { _, start, end ->
            MediaRangeResponse(
                206,
                bytes.copyOfRange(start.toInt(), end.toInt() + 1),
            )
        }

        ResolvedMediaFetcher(
            transport = transport,
            chunkSizeOverride = 4L,
        ).fetch(
            resolved = resolved(bytes.size.toLong()),
            destination = destination,
            onProgress = progress::add,
        )

        assertEquals(listOf(0L, 4L, 8L, 10L), progress.map { it.bytesDownloaded })
        assertTrue(progress.all { it.totalBytes == 10L })
        assertEquals(10L, progress.last().bytesDownloaded)
        assertTrue(progress.drop(1).all { it.bytesPerSecond >= 0L })
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
    fun replacesPartialWhenServerIgnoresResumeRangeWithCompleteBody() {
        val bytes = ByteArray(10) { (it + 20).toByte() }
        val destination = testPath("ignored-range.media")
        Files.write(
            destination.resolveSibling("ignored-range.media.part"),
            bytes.copyOfRange(0, 4),
        )

        val starts = mutableListOf<Long>()
        val transport = MediaRangeTransport { _, start, _ ->
            starts += start
            MediaRangeResponse(200, bytes)
        }

        ResolvedMediaFetcher(
            transport = transport,
            chunkSizeOverride = 4L,
        ).fetch(resolved(bytes.size.toLong()), destination)

        assertEquals(listOf(4L), starts)
        assertContentEquals(bytes, Files.readAllBytes(destination))
    }

    @Test
    fun probesContentLengthWhenResolverDoesNotProvideIt() {
        val bytes = ByteArray(10) { (it + 30).toByte() }
        val destination = testPath("probe-length.media")
        var probes = 0

        val transport = object : MediaRangeTransport {
            override fun probeLength(resolved: ResolvedAudio): Long? {
                probes++
                return bytes.size.toLong()
            }

            override fun fetch(
                resolved: ResolvedAudio,
                start: Long,
                endInclusive: Long,
            ): MediaRangeResponse =
                MediaRangeResponse(
                    206,
                    bytes.copyOfRange(start.toInt(), endInclusive.toInt() + 1),
                )
        }

        val written = ResolvedMediaFetcher(
            transport = transport,
            chunkSizeOverride = 4L,
        ).fetch(resolved(null), destination)

        assertEquals(1, probes)
        assertEquals(10L, written.bytes)
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

    private fun resolved(length: Long?) =
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
