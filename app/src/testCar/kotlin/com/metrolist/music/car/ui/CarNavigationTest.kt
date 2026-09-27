package com.metrolist.music.car.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarNavigationTest {
    @Test
    fun coldStartOpensDownloads() {
        assertEquals(CarDestination.DOWNLOADS, carStartDestination(hasRestoredPlayback = false))
    }

    @Test
    fun restoredPlaybackOpensNowPlaying() {
        assertEquals(CarDestination.NOW_PLAYING, carStartDestination(hasRestoredPlayback = true))
    }

    @Test
    fun backPopsOneCarRouteWithoutAnyPlaybackSideEffect() {
        val state = CarNavigationState(CarDestination.DOWNLOADS)
        state.navigate(CarDestination.SEARCH)

        assertEquals(CarDestination.SEARCH, state.current)
        assertTrue(state.back())
        assertEquals(CarDestination.DOWNLOADS, state.current)
        assertFalse(state.back())
    }
}
