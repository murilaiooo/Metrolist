package com.metrolist.desktop.artwork

import kotlin.test.Test
import kotlin.test.assertEquals

class ArtworkSourceSelectionTest {
    @Test
    fun selectsHighestResolutionUpstreamCandidate() {
        val selected =
            bestArtworkSource(
                upstream =
                    listOf(
                        ArtworkSource("120", 120, 120),
                        ArtworkSource("320", 320, 320),
                        ArtworkSource("544", 544, 544),
                        ArtworkSource("1200", 1200, 1200),
                    ),
            )

        assertEquals(ArtworkSource("1200", 1200, 1200), selected)
    }

    @Test
    fun keepsOnlyAvailableSourceWithoutUpscaling() {
        val source = ArtworkSource("544", 544, 544)

        assertEquals(source, bestArtworkSource(upstream = listOf(source)))
    }

    @Test
    fun lowResolutionCachedSourceCannotOverrideBetterUpstreamSource() {
        val selected =
            bestArtworkSource(
                upstream = listOf(ArtworkSource("1200", 1200, 1200)),
                cached = ArtworkSource("cached-320", 320, 320),
            )

        assertEquals(ArtworkSource("1200", 1200, 1200), selected)
    }
}
