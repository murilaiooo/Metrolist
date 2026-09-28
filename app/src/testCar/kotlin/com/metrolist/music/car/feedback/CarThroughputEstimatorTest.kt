package com.metrolist.music.car.feedback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CarThroughputEstimatorTest {
    @Test
    fun firstSampleHasNoRateAndSecondSampleProducesRate() {
        val estimator = CarThroughputEstimator(windowMillis = 3_000)

        assertNull(estimator.sample(bytesProcessed = 1_000, timestampMillis = 1_000))
        assertEquals(1_000L, estimator.sample(bytesProcessed = 2_000, timestampMillis = 2_000))
    }

    @Test
    fun rollingWindowSmoothsShortSpike() {
        val estimator = CarThroughputEstimator(windowMillis = 3_000)

        estimator.sample(1_000, 1_000)
        estimator.sample(2_000, 2_000)
        val rate = estimator.sample(8_000, 3_000)

        assertEquals(3_500L, rate)
    }

    @Test
    fun zeroOrNegativeElapsedTimeIsIgnored() {
        val estimator = CarThroughputEstimator(windowMillis = 3_000)

        estimator.sample(1_000, 1_000)
        assertNull(estimator.sample(2_000, 1_000))
        assertEquals(2_000L, estimator.sample(3_000, 2_000))
        assertEquals(2_000L, estimator.sample(9_000, 1_500))
    }

    @Test
    fun resetStartsANewWindow() {
        val estimator = CarThroughputEstimator(windowMillis = 3_000)

        estimator.sample(0, 0)
        assertEquals(1_000L, estimator.sample(1_000, 1_000))

        estimator.reset()

        assertNull(estimator.sample(20_000, 10_000))
    }
}
