package com.metrolist.innertube.models

import org.junit.Assert.assertEquals
import org.junit.Test

class ThumbnailQualityTest {
    @Test
    fun selectsLargestRealThumbnailRegardlessOfSourceOrder() {
        val thumbnails =
            Thumbnails(
                listOf(
                    Thumbnail("https://example/544", 544, 544),
                    Thumbnail("https://example/120", 120, 120),
                    Thumbnail("https://example/1200", 1200, 1200),
                    Thumbnail("https://example/320", 320, 320),
                ),
            )

        assertEquals("https://example/1200", thumbnails.bestAvailableThumbnail()?.url)
    }

    @Test
    fun keepsOnlyAvailableResolutionWithoutInventingUpscale() {
        val thumbnails = Thumbnails(listOf(Thumbnail("https://example/544", 544, 544)))
        assertEquals("https://example/544", thumbnails.bestAvailableThumbnail()?.url)
    }

    @Test
    fun preservesExistingFallbackWhenDimensionsAreUnknown() {
        val thumbnails =
            Thumbnails(
                listOf(
                    Thumbnail("https://example/first", null, null),
                    Thumbnail("https://example/last", null, null),
                ),
            )

        assertEquals("https://example/last", thumbnails.bestAvailableThumbnail()?.url)
    }
}

