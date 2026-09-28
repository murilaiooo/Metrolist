package com.metrolist.music.car.startup

import android.os.SystemClock
import android.util.Log
import java.util.EnumMap

enum class CarStartupMilestone {
    ACTIVITY_CREATED,
    CONTENT_SET,
    SHELL_COMPOSED,
    PLAYER_CONNECTED,
    LIBRARY_REOPEN_STARTED,
    LIBRARY_READY,
}

data class CarStartupSnapshot(
    private val marks: Map<CarStartupMilestone, Long>,
) {
    fun contains(milestone: CarStartupMilestone): Boolean = milestone in marks

    fun elapsedMs(milestone: CarStartupMilestone): Long? {
        val value = marks[milestone] ?: return null
        val base =
            marks[CarStartupMilestone.ACTIVITY_CREATED]
                ?: marks.values.minOrNull()
                ?: return null
        return (value - base).coerceAtLeast(0L)
    }
}

class CarStartupMilestoneTracker(
    private val nowMillis: () -> Long = SystemClock::elapsedRealtime,
) {
    private val marks = EnumMap<CarStartupMilestone, Long>(CarStartupMilestone::class.java)

    @Synchronized
    fun mark(milestone: CarStartupMilestone) {
        if (marks.containsKey(milestone)) return
        val now = nowMillis()
        marks[milestone] = now
        val snapshot = CarStartupSnapshot(marks.toMap())
        runCatching {
            Log.i(
                TAG,
                "milestone=${milestone.name} elapsedMs=${snapshot.elapsedMs(milestone) ?: 0L}",
            )
        }
    }

    @Synchronized
    fun snapshot(): CarStartupSnapshot = CarStartupSnapshot(marks.toMap())

    companion object {
        const val TAG = "MetroListCarStartup"
    }
}
