package com.metrolist.music.car.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarNavigationTest {
    @Test
    fun coldStartOpensMusic() {
        assertEquals(CarDestination.MUSIC, carStartDestination(hasRestoredPlayback = false))
    }

    @Test
    fun destinationsAreExactlyTheFiveCarSurfaces() {
        assertEquals(
            listOf(
                CarDestination.NOW_PLAYING,
                CarDestination.MUSIC,
                CarDestination.LIBRARY,
                CarDestination.SEARCH,
                CarDestination.MORE,
            ),
            CarDestination.entries,
        )
    }

    @Test
    fun restoredPlaybackOpensNowPlaying() {
        assertEquals(CarDestination.NOW_PLAYING, carStartDestination(hasRestoredPlayback = true))
    }

    @Test
    fun backPopsOneCarRouteWithoutAnyPlaybackSideEffect() {
        val state = CarNavigationState(CarDestination.MUSIC)
        state.navigate(CarDestination.SEARCH)

        assertEquals(CarDestination.SEARCH, state.current)
        assertTrue(state.back())
        assertEquals(CarDestination.MUSIC, state.current)
        assertFalse(state.back())
    }
}
