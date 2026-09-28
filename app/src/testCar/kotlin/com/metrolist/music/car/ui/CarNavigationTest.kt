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
    fun primaryRailDestinationsRemainExactlyFive() {
        assertEquals(
            listOf(
                CarDestination.NOW_PLAYING,
                CarDestination.MUSIC,
                CarDestination.LIBRARY,
                CarDestination.SEARCH,
                CarDestination.MORE,
            ),
            CAR_PRIMARY_DESTINATIONS,
        )
    }

    @Test
    fun dislikedIsSecondaryAndBackReturnsToMore() {
        val state = CarNavigationState(CarDestination.MORE)
        state.navigate(CarDestination.DISLIKED)

        assertEquals(CarDestination.DISLIKED, state.current)
        assertTrue(state.back())
        assertEquals(CarDestination.MORE, state.current)
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
