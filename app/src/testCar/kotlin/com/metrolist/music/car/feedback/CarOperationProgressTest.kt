package com.metrolist.music.car.feedback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CarOperationProgressTest {
    @Test
    fun knownByteTotalsProduceClampedFraction() {
        val progress =
            CarOperationProgress(
                stage = CarOperationStage.IMPORTING,
                bytesProcessed = 50,
                totalBytes = 100,
            )

        assertEquals(0.5f, progress.fraction!!, 0.0001f)
        assertEquals(1f, progress.copy(bytesProcessed = 150).fraction!!, 0.0001f)
        assertEquals(0f, progress.copy(bytesProcessed = -10).fraction!!, 0.0001f)
    }

    @Test
    fun unknownOrZeroByteTotalIsIndeterminate() {
        assertNull(
            CarOperationProgress(
                stage = CarOperationStage.VALIDATING,
                bytesProcessed = 5,
                totalBytes = null,
            ).fraction,
        )
        assertNull(
            CarOperationProgress(
                stage = CarOperationStage.VALIDATING,
                bytesProcessed = 0,
                totalBytes = 0,
            ).fraction,
        )
    }

    @Test
    fun itemProgressDoesNotRequireByteProgress() {
        val progress =
            CarOperationProgress(
                stage = CarOperationStage.OPENING_LIBRARY,
                completedItems = 3,
                totalItems = 10,
            )

        assertEquals(3, progress.completedItems)
        assertEquals(10, progress.totalItems)
        assertNull(progress.fraction)
    }

    @Test
    fun errorRetainsActionableText() {
        val state =
            CarOperationState.Error(
                message = "Library permission lost",
                actionHint = "Select folder again",
            )

        assertEquals("Library permission lost", state.message)
        assertEquals("Select folder again", state.actionHint)
    }
}
