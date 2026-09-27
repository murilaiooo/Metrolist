package com.metrolist.desktop.media

import com.metrolist.desktop.stream.ResolvedAudio
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.util.concurrent.CompletionException
import java.util.concurrent.TimeUnit

data class MediaRangeResponse(
    val statusCode: Int,
    val bytes: ByteArray,
)

data class MediaTransferProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val bytesPerSecond: Long,
)

fun interface MediaRangeTransport {
    fun fetch(
        resolved: ResolvedAudio,
        start: Long,
        endInclusive: Long,
    ): MediaRangeResponse

    fun probeLength(resolved: ResolvedAudio): Long? = null
}

class ExpiredMediaUrlException(
    val statusCode: Int,
) : IllegalStateException("Resolved media URL expired with HTTP $statusCode")

class MediaTransferException(
    message: String,
) : IllegalStateException(message)

class JdkMediaRangeTransport(
    private val client: HttpClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build(),
) : MediaRangeTransport {
    override fun fetch(
        resolved: ResolvedAudio,
        start: Long,
        endInclusive: Long,
    ): MediaRangeResponse {
        val request =
            requestBuilder(resolved)
                .header("Range", "bytes=$start-$endInclusive")
                .GET()
                .build()

        return try {
            val response =
                client.sendAsync(
                    request,
                    HttpResponse.BodyHandlers.ofByteArray(),
                ).orTimeout(45, TimeUnit.SECONDS)
                    .join()

            MediaRangeResponse(
                statusCode = response.statusCode(),
                bytes = response.body(),
            )
        } catch (error: CompletionException) {
            throw networkFailure("Media range request failed", error)
        }
    }

    override fun probeLength(resolved: ResolvedAudio): Long? {
        val request =
            requestBuilder(resolved)
                .header("Range", "bytes=0-0")
                .GET()
                .build()

        return try {
            val response =
                client.sendAsync(
                    request,
                    HttpResponse.BodyHandlers.ofInputStream(),
                ).orTimeout(45, TimeUnit.SECONDS)
                    .join()

            response.body().use {
                val length =
                    mediaContentLength(
                        statusCode = response.statusCode(),
                        contentRange = response.headers().firstValue("Content-Range").orElse(null),
                        contentLength = response.headers().firstValue("Content-Length").orElse(null),
                    )
                when (response.statusCode()) {
                    403, 410 -> throw ExpiredMediaUrlException(response.statusCode())
                    416 -> length ?: throw ExpiredMediaUrlException(response.statusCode())
                    else -> length
                }
            }
        } catch (error: CompletionException) {
            throw networkFailure("Media length probe failed", error)
        }
    }

    private fun requestBuilder(resolved: ResolvedAudio): HttpRequest.Builder =
        HttpRequest.newBuilder(URI.create(resolved.url))
            .timeout(Duration.ofSeconds(45))
            .apply {
                resolved.headers.forEach { (name, value) ->
                    header(name, value)
                }
            }

    private fun networkFailure(
        message: String,
        error: CompletionException,
    ): MediaTransferException {
        val cause = error.cause ?: error
        return MediaTransferException(
            "$message: ${cause::class.simpleName ?: "NetworkError"}",
        )
    }
}

internal fun mediaContentLength(
    statusCode: Int,
    contentRange: String?,
    contentLength: String?,
): Long? {
    val rangePattern =
        when (statusCode) {
            206 -> PARTIAL_CONTENT_RANGE
            416 -> UNSATISFIED_CONTENT_RANGE
            else -> null
        }
    if (rangePattern != null) {
        return contentRange
            ?.trim()
            ?.let(rangePattern::matchEntire)
            ?.groupValues
            ?.get(1)
            ?.toLongOrNull()
            ?.takeIf { it > 0L }
    }
    return if (statusCode == 200) {
        contentLength?.toLongOrNull()?.takeIf { it > 0L }
    } else {
        null
    }
}

private val PARTIAL_CONTENT_RANGE =
    Regex("""bytes\s+0-0/(\d+)""", RegexOption.IGNORE_CASE)
private val UNSATISFIED_CONTENT_RANGE =
    Regex("""bytes\s+\*/(\d+)""", RegexOption.IGNORE_CASE)

class ResolvedMediaFetcher(
    private val transport: MediaRangeTransport = JdkMediaRangeTransport(),
    private val writer: ValidatedMediaWriter = ValidatedMediaWriter(),
    private val chunkSizeOverride: Long? = null,
) {
    fun fetch(
        resolved: ResolvedAudio,
        destination: Path,
    ): WrittenMedia =
        fetch(
            resolved = resolved,
            destination = destination,
            onProgress = {},
        )

    fun fetch(
        resolved: ResolvedAudio,
        destination: Path,
        onProgress: (MediaTransferProgress) -> Unit,
    ): WrittenMedia {
        val expectedLength =
            resolved.contentLength
                ?.takeIf { it > 0L }
                ?: transport.probeLength(resolved)
                ?: throw MediaTransferException(
                    "Offline media requires a known content length",
                )
        require(expectedLength > 0L)

        destination.parent?.let(Files::createDirectories)
        val temporary = destination.resolveSibling(destination.fileName.toString() + ".part")

        if (Files.exists(temporary) && Files.size(temporary) > expectedLength) {
            Files.newOutputStream(
                temporary,
                StandardOpenOption.TRUNCATE_EXISTING,
            ).close()
        }

        if (Files.exists(temporary) && Files.size(temporary) == expectedLength) {
            onProgress(
                MediaTransferProgress(
                    bytesDownloaded = expectedLength,
                    totalBytes = expectedLength,
                    bytesPerSecond = 0L,
                ),
            )
            return writer.promoteExisting(temporary, destination, expectedLength)
        }

        val requestedChunkSize =
            chunkSizeOverride
                ?: resolved.rangeChunkSizeBytes.takeIf { it > 0L }
                ?: DEFAULT_CHUNK_SIZE
        val chunkSize = requestedChunkSize.coerceIn(MIN_CHUNK_SIZE, MAX_CHUNK_SIZE)

        var position = if (Files.exists(temporary)) Files.size(temporary) else 0L
        val initialPosition = position
        val startedAtNanos = System.nanoTime()

        onProgress(
            MediaTransferProgress(
                bytesDownloaded = position,
                totalBytes = expectedLength,
                bytesPerSecond = 0L,
            ),
        )

        while (position < expectedLength) {
            val endInclusive = minOf(position + chunkSize - 1L, expectedLength - 1L)
            val response = transport.fetch(resolved, position, endInclusive)

            when (response.statusCode) {
                206 -> {
                    if (response.bytes.isEmpty()) {
                        throw MediaTransferException("Media range response was empty")
                    }

                    val requestedLength = endInclusive - position + 1L
                    if (response.bytes.size.toLong() > requestedLength) {
                        throw MediaTransferException("Media range response exceeded requested size")
                    }

                    Files.write(
                        temporary,
                        response.bytes,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND,
                    )
                    position += response.bytes.size
                    onProgress(
                        MediaTransferProgress(
                            bytesDownloaded = position,
                            totalBytes = expectedLength,
                            bytesPerSecond =
                                calculateSpeed(
                                    bytesTransferred = position - initialPosition,
                                    startedAtNanos = startedAtNanos,
                                ),
                        ),
                    )
                }

                200 -> {
                    if (response.bytes.size.toLong() == expectedLength) {
                        Files.write(
                            temporary,
                            response.bytes,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING,
                        )
                        position = expectedLength
                        onProgress(
                            MediaTransferProgress(
                                bytesDownloaded = position,
                                totalBytes = expectedLength,
                                bytesPerSecond =
                                    calculateSpeed(
                                        bytesTransferred = response.bytes.size.toLong(),
                                        startedAtNanos = startedAtNanos,
                                    ),
                            ),
                        )
                    } else {
                        throw MediaTransferException("Server ignored byte-range request")
                    }
                }

                403, 410, 416 -> throw ExpiredMediaUrlException(response.statusCode)
                else -> throw MediaTransferException(
                    "Media range request failed with HTTP ${response.statusCode}",
                )
            }
        }

        return writer.promoteExisting(
            temporary = temporary,
            destination = destination,
            expectedLength = expectedLength,
        )
    }

    private fun calculateSpeed(
        bytesTransferred: Long,
        startedAtNanos: Long,
    ): Long {
        if (bytesTransferred <= 0L) return 0L
        val elapsedNanos = (System.nanoTime() - startedAtNanos).coerceAtLeast(1L)
        return ((bytesTransferred.toDouble() * 1_000_000_000.0) / elapsedNanos)
            .toLong()
            .coerceAtLeast(0L)
    }

    private companion object {
        const val DEFAULT_CHUNK_SIZE = 1_048_576L
        const val MIN_CHUNK_SIZE = 4L
        const val MAX_CHUNK_SIZE = 5_242_880L
    }
}
