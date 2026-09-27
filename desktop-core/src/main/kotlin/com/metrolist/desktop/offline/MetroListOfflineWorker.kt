package com.metrolist.desktop.offline

import com.metrolist.desktop.media.ExpiredMediaUrlException
import com.metrolist.desktop.media.MediaTransferException
import com.metrolist.desktop.media.MediaTransferProgress
import com.metrolist.desktop.media.WrittenMedia
import com.metrolist.desktop.stream.ResolvedAudio
import com.metrolist.innertubex.extraction.StreamResolveException
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

class MetroListOfflineWorker(
    private val mediaDir: Path,
    private val resolver: suspend (String) -> ResolvedAudio,
    private val fetcher: (ResolvedAudio, Path, (MediaTransferProgress) -> Unit) -> WrittenMedia,
    private val resolveTimeoutMs: Long = DEFAULT_RESOLVE_TIMEOUT_MS,
    private val resolveRetryDelaysMs: List<Long> = DEFAULT_RESOLVE_RETRY_DELAYS_MS,
) {
    constructor(
        mediaDir: Path,
        resolver: suspend (String) -> ResolvedAudio,
        fetcher: (ResolvedAudio, Path) -> WrittenMedia,
        resolveTimeoutMs: Long = DEFAULT_RESOLVE_TIMEOUT_MS,
        resolveRetryDelaysMs: List<Long> = DEFAULT_RESOLVE_RETRY_DELAYS_MS,
    ) : this(
        mediaDir = mediaDir,
        resolver = resolver,
        fetcher = { resolved, destination, _ ->
            fetcher(resolved, destination)
        },
        resolveTimeoutMs = resolveTimeoutMs,
        resolveRetryDelaysMs = resolveRetryDelaysMs,
    )

    suspend fun transfer(record: OfflineRecord): OfflineRecord =
        transfer(record, onProgress = {})

    suspend fun transfer(
        record: OfflineRecord,
        onProgress: (MediaTransferProgress) -> Unit,
    ): OfflineRecord {
        Files.createDirectories(mediaDir)
        val destination = mediaDir.resolve(record.videoId + ".media")
        val temporary = destination.resolveSibling(destination.fileName.toString() + ".part")

        var lastExpired: ExpiredMediaUrlException? = null

        try {
            repeat(2) { attempt ->
                currentCoroutineContext().ensureActive()
                val resolved = resolveWithRetry(record.videoId)
                currentCoroutineContext().ensureActive()

                try {
                    val written =
                        fetcher(
                            resolved,
                            destination,
                            onProgress,
                        )
                    currentCoroutineContext().ensureActive()

                    val contentLength = resolved.contentLength ?: written.bytes
                    check(written.bytes == contentLength) {
                        "Offline media length mismatch"
                    }

                    return record.copy(
                        state = OfflineState.COMPLETED,
                        localPath = written.path.toString(),
                        bytesDownloaded = written.bytes,
                        contentLength = contentLength,
                        bytesPerSecond = 0L,
                        format =
                            FormatSnapshot(
                                itag = resolved.itag,
                                mimeType = resolved.mimeType,
                                codecs = resolved.codecs,
                                bitrate = resolved.bitrate,
                                sampleRate = resolved.sampleRate,
                                contentLength = contentLength,
                                loudnessDb = resolved.loudnessDb,
                                perceptualLoudnessDb = resolved.perceptualLoudnessDb,
                            ),
                        downloadedAt = LocalDateTime.now().toString(),
                        updateTimeMs = System.currentTimeMillis(),
                        lastError = null,
                    )
                } catch (expired: ExpiredMediaUrlException) {
                    lastExpired = expired
                    if (attempt == 1) throw expired
                }
            }

            throw requireNotNull(lastExpired)
        } catch (error: Throwable) {
            val context = currentCoroutineContext()
            if (error is CancellationException || !context.isActive) {
                Files.deleteIfExists(destination)
                Files.deleteIfExists(temporary)
                if (error is CancellationException) throw error

                throw CancellationException("Offline transfer cancelled").also {
                    it.initCause(error)
                }
            }
            throw error
        }
    }

    private suspend fun resolveWithRetry(videoId: String): ResolvedAudio {
        var retryIndex = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            try {
                return withTimeout(resolveTimeoutMs) {
                    resolver(videoId)
                }
            } catch (_: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive()
                throw MediaTransferException(
                    "Stream resolution timed out after ${resolveTimeoutMs}ms",
                )
            } catch (error: StreamResolveException) {
                currentCoroutineContext().ensureActive()
                val retryable =
                    error.reason == StreamResolveException.Reason.NO_PLAYABLE_STREAM ||
                        error.reason == StreamResolveException.Reason.NETWORK
                if (!retryable || retryIndex >= resolveRetryDelaysMs.size) throw error

                val delayMs = resolveRetryDelaysMs[retryIndex++].coerceAtLeast(0L)
                if (delayMs > 0L) delay(delayMs)
            }
        }
    }

    private companion object {
        const val DEFAULT_RESOLVE_TIMEOUT_MS = 45_000L
        val DEFAULT_RESOLVE_RETRY_DELAYS_MS = listOf(1_500L, 4_000L)
    }
}
