package com.metrolist.music.car.feedback

class CarThroughputEstimator(
    private val windowMillis: Long = 3_000L,
) {
    private data class Sample(
        val bytes: Long,
        val timestampMillis: Long,
    )

    private val samples = ArrayDeque<Sample>()
    private var lastRate: Long? = null

    init {
        require(windowMillis > 0L) { "windowMillis must be positive" }
    }

    fun sample(
        bytesProcessed: Long,
        timestampMillis: Long,
    ): Long? {
        val last = samples.lastOrNull()
        if (last != null && timestampMillis <= last.timestampMillis) {
            return lastRate
        }

        val current = Sample(bytesProcessed.coerceAtLeast(0L), timestampMillis)
        if (last != null && current.bytes < last.bytes) {
            samples.clear()
            lastRate = null
        }
        samples.addLast(current)
        trim(timestampMillis)

        if (samples.size < 2) return null
        val first = samples.first()
        val elapsedMillis = current.timestampMillis - first.timestampMillis
        val deltaBytes = current.bytes - first.bytes
        if (elapsedMillis <= 0L || deltaBytes < 0L) return lastRate

        lastRate = ((deltaBytes.toDouble() * 1_000.0) / elapsedMillis.toDouble()).toLong()
        return lastRate
    }

    fun reset() {
        samples.clear()
        lastRate = null
    }

    private fun trim(nowMillis: Long) {
        val cutoff = nowMillis - windowMillis
        while (
            samples.size > 2 &&
            samples.first().timestampMillis < cutoff &&
            samples[1].timestampMillis <= cutoff
        ) {
            samples.removeFirst()
        }
    }
}
