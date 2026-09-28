package com.metrolist.music.car.startup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarStartupMilestoneTrackerTest {
    @Test
    fun shellCanCompleteBeforePlayerAndLibrary() {
        var now = 100L
        val tracker = CarStartupMilestoneTracker { now }

        tracker.mark(CarStartupMilestone.ACTIVITY_CREATED)
        now = 140L
        tracker.mark(CarStartupMilestone.CONTENT_SET)
        now = 180L
        tracker.mark(CarStartupMilestone.SHELL_COMPOSED)

        val snapshot = tracker.snapshot()

        assertEquals(80L, snapshot.elapsedMs(CarStartupMilestone.SHELL_COMPOSED))
        assertFalse(snapshot.contains(CarStartupMilestone.PLAYER_CONNECTED))
        assertFalse(snapshot.contains(CarStartupMilestone.LIBRARY_READY))
    }

    @Test
    fun duplicateMarksKeepFirstTimestamp() {
        var now = 1_000L
        val tracker = CarStartupMilestoneTracker { now }

        tracker.mark(CarStartupMilestone.ACTIVITY_CREATED)
        now = 1_100L
        tracker.mark(CarStartupMilestone.SHELL_COMPOSED)
        now = 9_000L
        tracker.mark(CarStartupMilestone.SHELL_COMPOSED)

        assertEquals(100L, tracker.snapshot().elapsedMs(CarStartupMilestone.SHELL_COMPOSED))
    }

    @Test
    fun elapsedDurationsAreMonotonicAndNonNegative() {
        var now = 50L
        val tracker = CarStartupMilestoneTracker { now }

        tracker.mark(CarStartupMilestone.ACTIVITY_CREATED)
        now = 60L
        tracker.mark(CarStartupMilestone.CONTENT_SET)
        now = 80L
        tracker.mark(CarStartupMilestone.SHELL_COMPOSED)
        now = 120L
        tracker.mark(CarStartupMilestone.PLAYER_CONNECTED)

        val snapshot = tracker.snapshot()
        val values =
            listOf(
                CarStartupMilestone.ACTIVITY_CREATED,
                CarStartupMilestone.CONTENT_SET,
                CarStartupMilestone.SHELL_COMPOSED,
                CarStartupMilestone.PLAYER_CONNECTED,
            ).mapNotNull(snapshot::elapsedMs)

        assertTrue(values.all { it >= 0L })
        assertEquals(values.sorted(), values)
    }

    @Test
    fun lateActivityMarkStillNeverProducesNegativeElapsedTime() {
        var now = 500L
        val tracker = CarStartupMilestoneTracker { now }

        tracker.mark(CarStartupMilestone.SHELL_COMPOSED)
        now = 550L
        tracker.mark(CarStartupMilestone.ACTIVITY_CREATED)

        assertEquals(0L, tracker.snapshot().elapsedMs(CarStartupMilestone.SHELL_COMPOSED))
    }
}
