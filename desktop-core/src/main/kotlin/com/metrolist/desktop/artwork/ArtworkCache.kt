package com.metrolist.desktop.artwork

import java.io.ByteArrayInputStream
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
import javax.imageio.ImageIO

private const val MIN_HQ_EDGE = 1000

private val GOOGLE_USER_CONTENT =
    Regex("^(https://(?:lh3|yt3)\\.googleusercontent\\.com/[^?]*?)=w\\d+-h\\d+[^?]*(\\?.*)?$")
private val GGPHT =
    Regex("^(https://yt3\\.ggpht\\.com/[^?=]+)=(?:s\\d+|w\\d+-h\\d+)[^?]*(\\?.*)?$")
private val YTIMG =
    Regex("^(https://i\\.ytimg\\.com/vi/[^/]+/)([^?]+)(\\?.*)?$")

object ArtworkKey {
    fun fileName(thumbnailUrl: String): String =
        sha256(thumbnailUrl.toByteArray(Charsets.UTF_8)) + ".img"

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}

internal fun highQualityArtworkCandidates(thumbnailUrl: String): List<String> {
    val url = thumbnailUrl.trim()
    if (url.isEmpty()) return emptyList()

    GOOGLE_USER_CONTENT
        .matchEntire(url)
        ?.groupValues
        ?.let { group ->
            val query = group[2]
            return listOf(
                group[1] + "=w1200-h1200-p-l90-rj" + query,
                url,
            ).distinct()
        }

    GGPHT
        .matchEntire(url)
        ?.groupValues
        ?.let { group ->
            val query = group[2]
            return listOf(
                group[1] + "=w1200-h1200-p-l90-rj" + query,
                url,
            ).distinct()
        }

    YTIMG
        .matchEntire(url)
        ?.groupValues
        ?.let { group ->
            val prefix = group[1]
            val query = group[3]
            return listOf(
                prefix + "maxresdefault.jpg" + query,
                prefix + "sddefault.jpg" + query,
                url,
            ).distinct()
        }

    return listOf(url)
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
        val originalUrl = normalize(thumbnailUrl) ?: return null
        val candidates = highQualityArtworkCandidates(originalUrl)
        val existing = cachedPath(originalUrl)
        val existingDimensions = existing?.let(::imageDimensions)
        val existingArea =
            existingDimensions?.let { (width, height) -> width.toLong() * height.toLong() }
                ?: -1L

        if (
            existing != null &&
            (
                candidates.size == 1 ||
                    existingDimensions?.let { maxOf(it.first, it.second) >= MIN_HQ_EDGE } == true
            )
        ) {
            return existing
        }

        var bestBytes: ByteArray? = null
        var bestArea = -1L
        var foundHighQuality = false

        for (candidate in candidates) {
            if (existing != null && candidate == originalUrl) continue

            val bytes =
                runCatching { fetch(candidate) }
                    .getOrNull()
                    ?.takeIf { it.isNotEmpty() }
                    ?: continue

            val dimensions = imageDimensions(bytes)
            val area =
                dimensions?.let { (width, height) -> width.toLong() * height.toLong() }
                    ?: 0L
            if (bestBytes == null || area > bestArea) {
                bestBytes = bytes
                bestArea = area
            }
            if (dimensions != null && maxOf(dimensions.first, dimensions.second) >= MIN_HQ_EDGE) {
                foundHighQuality = true
                break
            }
        }

        if (!foundHighQuality && existing != null) {
            return existing
        }

        val selected = bestBytes ?: return existing

        return runCatching {
            Files.createDirectories(directory)
            val target = directory.resolve(ArtworkKey.fileName(originalUrl))
            val temporary = Files.createTempFile(directory, target.fileName.toString(), ".part")
            try {
                Files.write(temporary, selected)
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
        }.getOrElse { existing }
    }

    private fun imageDimensions(path: Path): Pair<Int, Int>? =
        runCatching {
            Files.newInputStream(path).use { input ->
                ImageIO.read(input)?.let { image -> image.width to image.height }
            }
        }.getOrNull()

    private fun imageDimensions(bytes: ByteArray): Pair<Int, Int>? =
        runCatching {
            ByteArrayInputStream(bytes).use { input ->
                ImageIO.read(input)?.let { image -> image.width to image.height }
            }
        }.getOrNull()

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
                    .header("User-Agent", "MetroListWindows/0.3")
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())
            check(response.statusCode() in 200..299) {
                "Artwork request failed with HTTP " + response.statusCode()
            }
            return response.body()
        }
    }
}