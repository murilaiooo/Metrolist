package com.metrolist.music.car.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class CarPlaybackTimeTest {
    @Test
    fun formatsElapsedAndDurationForCarPlayer() {
        assertEquals("0:00", formatCarPlaybackTime(0L))
        assertEquals("1:05", formatCarPlaybackTime(65_000L))
        assertEquals("59:59", formatCarPlaybackTime(3_599_000L))
        assertEquals("1:02:03", formatCarPlaybackTime(3_723_000L))
    }

    @Test
    fun negativeTimeIsClampedToZero() {
        assertEquals("0:00", formatCarPlaybackTime(-5_000L))
    }
}
