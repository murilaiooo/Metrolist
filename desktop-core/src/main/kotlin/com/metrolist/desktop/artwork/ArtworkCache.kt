package com.metrolist.desktop.artwork

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration

object ArtworkKey {
    fun fileName(thumbnailUrl: String): String =
        sha256(thumbnailUrl.toByteArray(Charsets.UTF_8)) + ".img"

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}

class ArtworkCache(
    private val directory: Path,
    private val fetch: (String) -> ByteArray = ::fetchHttp,
) {
    fun cachedPath(thumbnailUrl: String?): Path? {
        val url = normalize(thumbnailUrl) ?: return null
        val target = directory.resolve(ArtworkKey.fileName(url))
        return target.takeIf {
            Files.isRegularFile(it) && runCatching { Files.size(it) > 0L }.getOrDefault(false)
        }
    }

    fun ensure(thumbnailUrl: String?): Path? {
        val url = normalize(thumbnailUrl) ?: return null
        cachedPath(url)?.let { return it }

        return runCatching {
            val bytes = fetch(url)
            check(bytes.isNotEmpty()) { "Artwork response was empty" }
            Files.createDirectories(directory)

            cachedPath(url)?.let { return@runCatching it }
            val target = directory.resolve(ArtworkKey.fileName(url))
            val temporary = Files.createTempFile(directory, target.fileName.toString(), ".part")
            try {
                Files.write(temporary, bytes)
                try {
                    Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(
                        temporary,
                        target,
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                }
            } finally {
                Files.deleteIfExists(temporary)
            }
            target
        }.getOrNull()
    }

    private fun normalize(thumbnailUrl: String?): String? =
        thumbnailUrl?.trim()?.takeIf { it.isNotEmpty() }

    companion object {
        private val httpClient: HttpClient =
            HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()

        private fun fetchHttp(url: String): ByteArray {
            val request =
                HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "MetroListWindows/0.1")
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())
            check(response.statusCode() in 200..299) {
                "Artwork request failed with HTTP ${response.statusCode()}"
            }
            return response.body()
        }
    }
}
