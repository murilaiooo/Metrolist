package com.metrolist.desktop.offline

import com.metrolist.desktop.media.WrittenMedia
import com.metrolist.desktop.stream.ResolvedAudio
import com.metrolist.innertube.models.SongItem
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun cancellationCleansFinalAndPartialMedia() = runBlocking {
        val mediaDir = Path.of(System.getProperty("java.io.tmpdir"), "offline-worker-cancel")
        Files.createDirectories(mediaDir)
        val started = CompletableDeferred<Unit>()

        val worker = MetroListOfflineWorker(
            mediaDir = mediaDir,
            resolver = { resolvedAudio() },
            fetcher = { _, destination, _ ->
                started.complete(Unit)
                Thread.sleep(150)
                Files.write(destination, ByteArray(12) { 1 })
                Files.write(
                    destination.resolveSibling(destination.fileName.toString() + ".part"),
                    ByteArray(4) { 2 },
                )
                WrittenMedia(destination, 12L, "c".repeat(64))
            },
        )

        val job = launch(Dispatchers.IO) {
            worker.transfer(
                OfflineRecord.queued(
                    SongItem(
                        id = "cancel-song",
                        title = "Song",
                        artists = emptyList(),
                        thumbnail = "",
                    ),
                    nowMs = 10L,
                ),
            )
        }

        started.await()
        job.cancelAndJoin()

        assertFalse(Files.exists(mediaDir.resolve("cancel-song.media")))
        assertFalse(Files.exists(mediaDir.resolve("cancel-song.media.part")))
    }

    @Test
    fun cancellationCleansPartialEvenWhenBlockingFetcherReturnsOrdinaryError() = runBlocking {
        val mediaDir = Path.of(System.getProperty("java.io.tmpdir"), "offline-worker-cancel-error")
        Files.createDirectories(mediaDir)
        Files.deleteIfExists(mediaDir.resolve("cancel-error.media"))
        Files.deleteIfExists(mediaDir.resolve("cancel-error.media.part"))
        val started = CompletableDeferred<Unit>()

        val worker = MetroListOfflineWorker(
            mediaDir = mediaDir,
            resolver = { resolvedAudio().copy(videoId = "cancel-error") },
            fetcher = { _, destination, _ ->
                started.complete(Unit)
                Thread.sleep(150)
                Files.write(
                    destination.resolveSibling(destination.fileName.toString() + ".part"),
                    ByteArray(4) { 2 },
                )
                error("late fetch failure")
            },
        )

        val job = launch(Dispatchers.IO) {
            runCatching {
                worker.transfer(
                    OfflineRecord.queued(
                        SongItem(
                            id = "cancel-error",
                            title = "Song",
                            artists = emptyList(),
                            thumbnail = "",
                        ),
                        nowMs = 10L,
                    ),
                )
            }
        }

        started.await()
        job.cancelAndJoin()

        assertFalse(Files.exists(mediaDir.resolve("cancel-error.media")))
        assertFalse(Files.exists(mediaDir.resolve("cancel-error.media.part")))
    }

    @Test
    fun streamResolutionTimeoutBecomesRetryableTransferFailure() = runBlocking {
        val mediaDir = Path.of(System.getProperty("java.io.tmpdir"), "offline-worker-resolve-timeout")
        Files.createDirectories(mediaDir)

        val worker = MetroListOfflineWorker(
            mediaDir = mediaDir,
            resolver = {
                kotlinx.coroutines.delay(5_000)
                resolvedAudio()
            },
            fetcher = { _, destination ->
                Files.write(destination, ByteArray(12) { 1 })
                WrittenMedia(destination, 12L, "d".repeat(64))
            },
            resolveTimeoutMs = 30L,
        )

        val error = kotlin.test.assertFailsWith<com.metrolist.desktop.media.MediaTransferException> {
            worker.transfer(
                OfflineRecord.queued(
                    SongItem(
                        id = "timeout-song",
                        title = "Song",
                        artists = emptyList(),
                        thumbnail = "",
                    ),
                    nowMs = 10L,
                ),
            )
        }

        assertTrue(error.message.orEmpty().contains("timed out", ignoreCase = true))
    }

    @Test
    fun transientNoPlayableStreamIsRetriedBeforeFailing() = runBlocking {
        var resolves = 0
        val mediaDir = Path.of(System.getProperty("java.io.tmpdir"), "offline-worker-resolve-retry")
        Files.createDirectories(mediaDir)

        val worker =
            MetroListOfflineWorker(
                mediaDir = mediaDir,
                resolver = {
                    resolves++
                    if (resolves == 1) {
                        throw com.metrolist.innertubex.extraction.StreamResolveException(
                            com.metrolist.innertubex.extraction.StreamResolveException.Reason.NO_PLAYABLE_STREAM,
                            "temporary resolver miss",
                            null,
                            null,
                        )
                    }
                    resolvedAudio()
                },
                fetcher = { _, destination, _ ->
                    Files.write(destination, ByteArray(12) { 1 })
                    WrittenMedia(destination, 12L, "r".repeat(64))
                },
                resolveRetryDelaysMs = listOf(0L, 0L),
            )

        val result =
            worker.transfer(
                OfflineRecord.queued(
                    SongItem(
                        id = "retry-resolve-song",
                        title = "Song",
                        artists = emptyList(),
                        thumbnail = "",
                    ),
                    nowMs = 10L,
                ),
            )

        assertEquals(2, resolves)
        assertEquals(OfflineState.COMPLETED, result.state)
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

