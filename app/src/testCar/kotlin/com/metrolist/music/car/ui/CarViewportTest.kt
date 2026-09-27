package com.metrolist.music.car.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class CarViewportTest {
    @Test
    fun referenceViewportUsesUnitScale() {
        assertEquals(1f, CarViewportMetrics.from(1280, 720).scale, 0.0001f)
    }

    @Test
    fun smallerViewportUsesLimitingAxis() {
        assertEquals(0.8f, CarViewportMetrics.from(1024, 600).scale, 0.0001f)
    }

    @Test
    fun largeViewportClampsAtMaximumScale() {
        assertEquals(1.5f, CarViewportMetrics.from(1920, 1080).scale, 0.0001f)
    }

    @Test
    fun invalidViewportClampsAtSafeMinimum() {
        assertEquals(0.75f, CarViewportMetrics.from(0, 0).scale, 0.0001f)
        assertEquals(0.75f, CarViewportMetrics.from(-100, -50).scale, 0.0001f)
    }

    @Test
    fun hitTargetsPreserveReferencePixelsAcrossDensity() {
        val metrics = CarViewportMetrics.from(1280, 720)
        assertEquals(40f, metrics.pxToDp(80f, density = 2f).value, 0.0001f)
        assertEquals(32f, metrics.pxToDp(64f, density = 2f).value, 0.0001f)
        assertEquals(80f, metrics.pxToDp(80f, density = 0f).value, 0.0001f)
    }
}
