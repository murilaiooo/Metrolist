package com.metrolist.music.car.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class CarArtworkSwipePolicyTest {
    @Test
    fun ltrSlowSwipeUsesNineteenPercentThreshold() {
        assertEquals(
            CarArtworkSwipeAction.NONE,
            decideCarArtworkSwipe(
                offsetPx = -180f,
                artworkWidthPx = 1000f,
                velocityPxPerMs = 0.5f,
                canPrevious = true,
                canNext = true,
                isRtl = false,
            ),
        )
        assertEquals(
            CarArtworkSwipeAction.NEXT,
            decideCarArtworkSwipe(
                offsetPx = -200f,
                artworkWidthPx = 1000f,
                velocityPxPerMs = 0.5f,
                canPrevious = true,
                canNext = true,
                isRtl = false,
            ),
        )
        assertEquals(
            CarArtworkSwipeAction.PREVIOUS,
            decideCarArtworkSwipe(
                offsetPx = 200f,
                artworkWidthPx = 1000f,
                velocityPxPerMs = 0.5f,
                canPrevious = true,
                canNext = true,
                isRtl = false,
            ),
        )
    }

    @Test
    fun fastFlingCanCommitAtTenPercentButNotBelow() {
        assertEquals(
            CarArtworkSwipeAction.NEXT,
            decideCarArtworkSwipe(
                offsetPx = -100f,
                artworkWidthPx = 1000f,
                velocityPxPerMs = 1.2f,
                canPrevious = true,
                canNext = true,
                isRtl = false,
            ),
        )
        assertEquals(
            CarArtworkSwipeAction.NONE,
            decideCarArtworkSwipe(
                offsetPx = -99f,
                artworkWidthPx = 1000f,
                velocityPxPerMs = 9f,
                canPrevious = true,
                canNext = true,
                isRtl = false,
            ),
        )
    }

    @Test
    fun smallNoiseAndUnavailableDirectionsNeverSkip() {
        assertEquals(
            CarArtworkSwipeAction.NONE,
            decideCarArtworkSwipe(
                offsetPx = 20f,
                artworkWidthPx = 1000f,
                velocityPxPerMs = 0.1f,
                canPrevious = true,
                canNext = true,
                isRtl = false,
            ),
        )
        assertEquals(
            CarArtworkSwipeAction.NONE,
            decideCarArtworkSwipe(
                offsetPx = -400f,
                artworkWidthPx = 1000f,
                velocityPxPerMs = 3f,
                canPrevious = true,
                canNext = false,
                isRtl = false,
            ),
        )
        assertEquals(
            CarArtworkSwipeAction.NONE,
            decideCarArtworkSwipe(
                offsetPx = 400f,
                artworkWidthPx = 1000f,
                velocityPxPerMs = 3f,
                canPrevious = false,
                canNext = true,
                isRtl = false,
            ),
        )
    }

    @Test
    fun rtlReversesPhysicalDirections() {
        assertEquals(
            CarArtworkSwipeAction.PREVIOUS,
            decideCarArtworkSwipe(
                offsetPx = -200f,
                artworkWidthPx = 1000f,
                velocityPxPerMs = 0.5f,
                canPrevious = true,
                canNext = true,
                isRtl = true,
            ),
        )
        assertEquals(
            CarArtworkSwipeAction.NEXT,
            decideCarArtworkSwipe(
                offsetPx = 200f,
                artworkWidthPx = 1000f,
                velocityPxPerMs = 0.5f,
                canPrevious = true,
                canNext = true,
                isRtl = true,
            ),
        )
    }

    @Test
    fun zeroOrInvalidWidthIsSafe() {
        assertEquals(
            CarArtworkSwipeAction.NONE,
            decideCarArtworkSwipe(
                offsetPx = -500f,
                artworkWidthPx = 0f,
                velocityPxPerMs = 5f,
                canPrevious = true,
                canNext = true,
                isRtl = false,
            ),
        )
        assertEquals(
            CarArtworkSwipeAction.NONE,
            decideCarArtworkSwipe(
                offsetPx = Float.NaN,
                artworkWidthPx = 1000f,
                velocityPxPerMs = 5f,
                canPrevious = true,
                canNext = true,
                isRtl = false,
            ),
        )
    }
}
