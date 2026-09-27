package com.metrolist.music.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRestorePolicyTest {
    @Test
    fun carDisablesPlatformSchedulerOnlyForCar() {
        assertTrue(shouldUsePlatformScheduler(isCar = false))
        assertFalse(shouldUsePlatformScheduler(isCar = true))
    }

    @Test
    fun carRestoresSavedPlayWhenReadyWhileStandardAndroidStaysPaused() {
        assertFalse(restoredPlayWhenReady(isCar = false, savedPlayWhenReady = true))
        assertFalse(restoredPlayWhenReady(isCar = false, savedPlayWhenReady = false))
        assertTrue(restoredPlayWhenReady(isCar = true, savedPlayWhenReady = true))
        assertFalse(restoredPlayWhenReady(isCar = true, savedPlayWhenReady = false))
    }
}
