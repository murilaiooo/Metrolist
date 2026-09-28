package com.metrolist.music.car.ui.player

import com.metrolist.music.car.ui.CarViewportMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarMiniPlayerSizingTest {
    @Test
    fun controlsKeepAtLeastSeventySixPhysicalPixelsAcrossCarViewports() {
        listOf(
            CarViewportMetrics.from(800, 408),
            CarViewportMetrics.from(1024, 528),
            CarViewportMetrics.from(1280, 672),
        ).forEach { viewport ->
            val sizing = carMiniPlayerSizing(viewport, density = 1f)

            assertTrue(sizing.controlHitDp >= 76f)
            assertTrue(sizing.rowHeightDp >= sizing.controlHitDp)
            assertTrue(sizing.rowHeightDp <= viewport.heightPx.toFloat())
        }
    }

    @Test
    fun artworkAndRowRemainUsefulAtSmallViewport() {
        val sizing = carMiniPlayerSizing(CarViewportMetrics.from(800, 408), density = 1f)

        assertTrue(sizing.rowHeightDp >= 96f)
        assertTrue(sizing.artworkSizeDp >= 68f)
        assertTrue(sizing.artworkSizeDp < sizing.rowHeightDp)
    }

    @Test
    fun transportActionsExcludeVolume() {
        assertEquals(
            listOf(
                CarMiniPlayerAction.PREVIOUS,
                CarMiniPlayerAction.PLAY_PAUSE,
                CarMiniPlayerAction.NEXT,
            ),
            carMiniPlayerActions(),
        )
    }
}
