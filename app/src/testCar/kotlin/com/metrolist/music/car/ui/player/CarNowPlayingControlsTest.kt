package com.metrolist.music.car.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarNowPlayingControlsTest {
    @Test
    fun defaultControlsContainCarEssentialsWithoutVolume() {
        val controls = defaultCarNowPlayingControls().toSet()

        assertTrue(CarPlayerControl.PREVIOUS in controls)
        assertTrue(CarPlayerControl.PLAY_PAUSE in controls)
        assertTrue(CarPlayerControl.NEXT in controls)
        assertTrue(CarPlayerControl.SEEK in controls)
        assertTrue(CarPlayerControl.QUEUE in controls)
        assertTrue(CarPlayerControl.SHUFFLE in controls)
        assertTrue(CarPlayerControl.REPEAT in controls)
        assertFalse(controls.any { it.name.contains("VOLUME") })
    }
}
