package com.metrolist.music.car.preferences

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CarPreferenceActionTest {
    @Test
    fun dislikePersistsBeforeQueueMutation() = runBlocking {
        val order = mutableListOf<String>()

        performCarDislike(
            persist = { order += "persist" },
            mutatePlayback = { order += "queue" },
        )

        assertEquals(listOf("persist", "queue"), order)
    }

    @Test
    fun failedPersistenceDoesNotMutatePlayback() = runBlocking {
        var mutated = false

        runCatching {
            performCarDislike(
                persist = { error("disk full") },
                mutatePlayback = { mutated = true },
            )
        }

        assertFalse(mutated)
    }
}
