package com.metrolist.desktop.artwork

data class ArtworkSource(
    val url: String,
    val width: Int?,
    val height: Int?,
)

fun bestArtworkSource(
    upstream: List<ArtworkSource>,
    cached: ArtworkSource? = null,
): ArtworkSource? {
    val upstreamBest =
        upstream
            .filter(::isUsableArtworkSource)
            .maxWithOrNull(artworkSourceComparator)

    val cachedSource = cached?.takeIf(::isUsableArtworkSource)
    if (upstreamBest == null) return cachedSource
    if (cachedSource == null) return upstreamBest

    return if (artworkSourceComparator.compare(cachedSource, upstreamBest) > 0) {
        cachedSource
    } else {
        upstreamBest
    }
}

private fun isUsableArtworkSource(source: ArtworkSource): Boolean =
    source.url.isNotBlank()

private val artworkSourceComparator =
    compareBy<ArtworkSource>(
        { pixelArea(it) },
        { minDimension(it) },
        { maxDimension(it) },
        { it.url },
    )

private fun pixelArea(source: ArtworkSource): Long {
    val width = source.width?.takeIf { it > 0 } ?: return -1L
    val height = source.height?.takeIf { it > 0 } ?: return -1L
    return width.toLong() * height.toLong()
}

private fun minDimension(source: ArtworkSource): Int =
    minOf(source.width ?: -1, source.height ?: -1)

private fun maxDimension(source: ArtworkSource): Int =
    maxOf(source.width ?: -1, source.height ?: -1)
