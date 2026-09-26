package com.metrolist.desktop.offline

import com.metrolist.desktop.media.WrittenMedia
import com.metrolist.desktop.stream.ResolvedAudio
import com.metrolist.innertube.models.SongItem
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MetroListOfflineWorkerTest {
    @Test
    fun storesResolvedFormatAndValidatedMediaResult() {
        runBlocking {
            val mediaDir = Path.of(System.getProperty("java.io.tmpdir"), "offline-worker-media")
            Files.createDirectories(mediaDir)

            val resolved = resolvedAudio()
            val worker = MetroListOfflineWorker(
                mediaDir = mediaDir,
                resolver = { resolved },
                fetcher = { _, destination ->
                    Files.write(destination, ByteArray(12) { 1 })
                    WrittenMedia(destination, 12L, "a".repeat(64))
                },
            )

            val result = worker.transfer(
                OfflineRecord.queued(
                    SongItem(
                        id = "song-1",
                        title = "Song",
                        artists = emptyList(),
                        thumbnail = "",
                    ),
                    nowMs = 10L,
                ),
            )

            assertEquals(12L, result.bytesDownloaded)
            assertEquals(12L, result.contentLength)
            assertEquals(251, result.format?.itag)
            assertEquals("audio/webm", result.format?.mimeType)
            assertEquals("opus", result.format?.codecs)
            assertTrue(requireNotNull(result.localPath).endsWith("song-1.media"))
            assertNotNull(result.downloadedAt)
        }
    }

    @Test
    fun reResolvesOnceWhenMediaUrlExpires() {
        runBlocking {
            var resolves = 0
            var fetches = 0
            val mediaDir = Path.of(System.getProperty("java.io.tmpdir"), "offline-worker-retry")
            Files.createDirectories(mediaDir)

            val worker = MetroListOfflineWorker(
                mediaDir = mediaDir,
                resolver = {
                    resolves++
                    resolvedAudio()
                },
                fetcher = { _, destination ->
                    fetches++
                    if (fetches == 1) throw com.metrolist.desktop.media.ExpiredMediaUrlException(403)
                    Files.write(destination, ByteArray(12) { 1 })
                    WrittenMedia(destination, 12L, "b".repeat(64))
                },
            )

            val result = worker.transfer(
                OfflineRecord.queued(
                    SongItem(
                        id = "song-1",
                        title = "Song",
                        artists = emptyList(),
                        thumbnail = "",
                    ),
                    nowMs = 10L,
                ),
            )

            assertEquals(2, resolves)
            assertEquals(2, fetches)
            assertEquals(OfflineState.COMPLETED, result.state)
        }
    }

    private fun resolvedAudio() =
        ResolvedAudio(
            videoId = "song-1",
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
            contentLength = 12L,
            loudnessDb = -6.0,
            perceptualLoudnessDb = -13.0,
        )
}

