package com.metrolist.desktop.portable

import java.security.MessageDigest

data class SpanPlan(
    val relativePath: String,
    val position: Long,
    val length: Long,
    val index: Int,
)

object SpanPlanner {
    const val CHUNK_SIZE_BYTES: Long = 5_242_880L

    fun plan(
        cacheKey: String,
        contentLength: Long,
        chunkSize: Long = CHUNK_SIZE_BYTES,
    ): List<SpanPlan> {
        require(contentLength >= 0L)
        require(chunkSize > 0L)

        val directory = cacheDirectory(cacheKey)
        val spans = mutableListOf<SpanPlan>()
        var position = 0L
        var index = 0

        while (position < contentLength) {
            val length = minOf(chunkSize, contentLength - position)
            spans += SpanPlan(
                relativePath =
                    "media3_cache/files/$directory/" +
                        "${position}_${length}_$index.span",
                position = position,
                length = length,
                index = index,
            )
            position += length
            index++
        }

        return spans
    }

    fun cacheDirectory(cacheKey: String): String =
        sha256(cacheKey.toByteArray(Charsets.UTF_8)).take(16)

    fun artworkFileName(thumbnailUrl: String): String =
        sha256(thumbnailUrl.toByteArray(Charsets.UTF_8)) + ".img"

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
