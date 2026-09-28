package com.metrolist.music.car.ui.player

import com.metrolist.music.car.ui.CarViewportMetrics
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarNowPlayingLayoutTest {
    @Test
    fun controlsStayLeftOfArtworkAndEverythingFitsAcrossCarViewports() {
        listOf(
            CarViewportMetrics.from(800, 408),
            CarViewportMetrics.from(1024, 528),
            CarViewportMetrics.from(1280, 672),
        ).forEach { viewport ->
            val metrics = CarNowPlayingLayoutMetrics.forViewport(viewport)

            assertTrue(metrics.controlsLeftPx >= 0f)
            assertTrue(metrics.controlsRightPx <= metrics.artworkLeftPx)
            assertTrue(metrics.artworkRightPx <= metrics.contentWidthPx)
            assertTrue(metrics.artworkSizePx <= metrics.contentHeightPx - (2f * metrics.verticalPaddingPx))
            assertTrue(metrics.controlsWidthPx > 0f)
        }
    }

    @Test
    fun primaryTouchTargetsStayLargeOnSmallHeadUnit() {
        val small = CarNowPlayingLayoutMetrics.forViewport(CarViewportMetrics.from(800, 408))

        assertTrue(small.playPauseHitPx >= 96f)
        assertTrue(small.previousNextHitPx >= 80f)
        assertTrue(small.secondaryHitPx >= 60f)
    }

    @Test
    fun referenceHeadUnitHitsApprovedPhysicalBandsWithSystemBarsVisible() {
        val metrics = CarNowPlayingLayoutMetrics.forViewport(CarViewportMetrics.from(1024, 528))

        assertTrue(metrics.playPauseHitPx in 104f..112f)
        assertTrue(metrics.previousNextHitPx in 88f..96f)
    }

    @Test
    fun artworkRemainsRightHandRegion() {
        val metrics = CarNowPlayingLayoutMetrics.forViewport(CarViewportMetrics.from(1024, 528))

        assertTrue(metrics.artworkCenterPx > metrics.controlsCenterPx)
        assertFalse(metrics.artworkLeftPx < metrics.controlsLeftPx)
    }
}
