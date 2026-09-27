package com.metrolist.desktop

import com.metrolist.desktop.offline.OfflineRecord
import com.metrolist.desktop.offline.OfflineState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DownloadActivityPresentationTest {
    @Test
    fun hiddenWhenNothingNeedsAttention() {
        assertFalse(shouldShowDownloadActivity(emptyList()))
        assertFalse(
            shouldShowDownloadActivity(
                listOf(record("done", OfflineState.COMPLETED)),
            ),
        )
    }

    @Test
    fun visibleForActiveQueuedOrFailedWork() {
        assertTrue(shouldShowDownloadActivity(listOf(record("a", OfflineState.DOWNLOADING))))
        assertTrue(shouldShowDownloadActivity(listOf(record("a", OfflineState.QUEUED))))
        assertTrue(shouldShowDownloadActivity(listOf(record("a", OfflineState.FAILED))))
    }

    @Test
    fun compactSummaryPrioritizesActiveState() {
        val summary =
            compactDownloadActivitySummary(
                listOf(
                    record("a", OfflineState.DOWNLOADING, 2_000_000L),
                    record("b", OfflineState.QUEUED),
                    record("c", OfflineState.COMPLETED),
                ),
            )

        assertEquals("1 downloading · 1 queued · 1.9 MB/s", summary)
    }

    private fun record(
        id: String,
        state: OfflineState,
        speed: Long = 0L,
    ) =
        OfflineRecord(
            videoId = id,
            title = id,
            artists = emptyList(),
            album = null,
            duration = null,
            musicVideoType = null,
            thumbnail = "",
            explicit = false,
            isEpisode = false,
            state = state,
            bytesPerSecond = speed,
            startTimeMs = 0L,
            updateTimeMs = 0L,
        )
}
