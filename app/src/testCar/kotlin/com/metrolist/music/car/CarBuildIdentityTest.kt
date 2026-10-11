package com.metrolist.music.car

import com.metrolist.music.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarBuildIdentityTest {
    @Test
    fun carBuildHasIndependentIdentity() {
        assertTrue(BuildConfig.IS_CAR)
        assertEquals("0.4.2", BuildConfig.VERSION_NAME)
        assertEquals(4002, BuildConfig.VERSION_CODE)
        assertEquals("com.metrolist.music.car.debug", BuildConfig.APPLICATION_ID)
    }
}
