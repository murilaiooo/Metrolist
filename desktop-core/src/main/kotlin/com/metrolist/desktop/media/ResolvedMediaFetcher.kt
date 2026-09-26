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

fun interface MediaRangeTransport {
    fun fetch(
        resolved: ResolvedAudio,
        start: Long,
        endInclusive: Long,
    ): MediaRangeResponse
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
            HttpRequest.newBuilder(URI.create(resolved.url))
                .timeout(Duration.ofSeconds(45))
                .header("Range", "bytes=$start-$endInclusive")
                .GET()
                .apply {
                    resolved.headers.forEach { (name, value) ->
                        header(name, value)
                    }
                }
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
            val cause = error.cause ?: error
            throw MediaTransferException(
                "Media range request failed: ${cause::class.simpleName ?: "NetworkError"}",
            )
        }
    }
}

class ResolvedMediaFetcher(
    private val transport: MediaRangeTransport = JdkMediaRangeTransport(),
    private val writer: ValidatedMediaWriter = ValidatedMediaWriter(),
    private val chunkSizeOverride: Long? = null,
) {
    fun fetch(
        resolved: ResolvedAudio,
        destination: Path,
    ): WrittenMedia {
        val expectedLength =
            requireNotNull(resolved.contentLength) {
                "Offline media requires a known content length"
            }
        require(expectedLength >= 0L)

        destination.parent?.let(Files::createDirectories)
        val temporary = destination.resolveSibling(destination.fileName.toString() + ".part")

        if (Files.exists(temporary) && Files.size(temporary) > expectedLength) {
            Files.newOutputStream(
                temporary,
                StandardOpenOption.TRUNCATE_EXISTING,
            ).close()
        }

        if (Files.exists(temporary) && Files.size(temporary) == expectedLength) {
            return writer.promoteExisting(temporary, destination, expectedLength)
        }

        val requestedChunkSize =
            chunkSizeOverride
                ?: resolved.rangeChunkSizeBytes.takeIf { it > 0L }
                ?: DEFAULT_CHUNK_SIZE
        val chunkSize = requestedChunkSize.coerceIn(MIN_CHUNK_SIZE, MAX_CHUNK_SIZE)

        var position = if (Files.exists(temporary)) Files.size(temporary) else 0L

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
                }

                200 -> {
                    if (position == 0L && response.bytes.size.toLong() == expectedLength) {
                        Files.write(
                            temporary,
                            response.bytes,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING,
                        )
                        position = expectedLength
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

    private companion object {
        const val DEFAULT_CHUNK_SIZE = 1_048_576L
        const val MIN_CHUNK_SIZE = 4L
        const val MAX_CHUNK_SIZE = 5_242_880L
    }
}
