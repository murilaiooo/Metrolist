package com.metrolist.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class Thumbnails(
    val thumbnails: List<Thumbnail>,
) {
    fun bestAvailableThumbnail(): Thumbnail? {
        val measurable =
            thumbnails.filter { thumbnail ->
                thumbnail.width != null && thumbnail.height != null
            }
        return measurable.maxWithOrNull(
            compareBy<Thumbnail> { thumbnail ->
                thumbnail.width!!.toLong() * thumbnail.height!!.toLong()
            }.thenBy { it.width }
                .thenBy { it.height },
        ) ?: thumbnails.lastOrNull()
    }
}

@Serializable
data class Thumbnail(
    val url: String,
    val width: Int?,
    val height: Int?,
)
