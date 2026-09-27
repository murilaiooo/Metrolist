package com.metrolist.desktop

import com.metrolist.desktop.offline.OfflineRecord
import com.metrolist.desktop.offline.OfflineState
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.SongItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DownloadStatusTest {
    @Test
    fun unresolvedDownloadShowsPreparingStatusInsteadOfZeroByteTransfer() {
        val record = OfflineRecord.queued(song(), 1L).copy(
            state = OfflineState.DOWNLOADING,
            bytesDownloaded = 0L,
            contentLength = null,
            bytesPerSecond = 0L,
        )

        assertEquals(
            "Preparing download · resolving stream...",
            downloadStatusText(record),
        )
    }

    @Test
    fun playlistSummaryCountsEveryVisibleDownloadState() {
        val songs = listOf(
            song("a"),
            song("b"),
            song("c"),
            song("d"),
            song("e"),
        )
        val records =
            listOf(
                record(songs[0], OfflineState.COMPLETED),
                record(songs[1], OfflineState.DOWNLOADING),
                record(songs[2], OfflineState.QUEUED),
                record(songs[3], OfflineState.FAILED),
            ).associateBy(OfflineRecord::videoId)

        val summary = summarizePlaylistDownloads(songs, records)

        assertEquals(5, summary.total)
        assertEquals(1, summary.completed)
        assertEquals(1, summary.downloading)
        assertEquals(1, summary.queued)
        assertEquals(1, summary.failed)
        assertEquals(1, summary.missing)
        assertEquals(2, summary.active)
        assertEquals(0.2f, summary.progressFraction)
    }

    @Test
    fun filtersDownloadsByStateAndSearchesTitleArtistOrAlbum() {
        val queen =
            record(
                song("queen-song", "Somebody To Love", "Queen"),
                OfflineState.COMPLETED,
            )
        val bowie =
            record(
                song("bowie-song", "Heroes", "David Bowie"),
                OfflineState.FAILED,
            )

        assertEquals(
            listOf("queen-song"),
            filterDownloads(
                listOf(queen, bowie),
                DownloadFilter.OFFLINE,
                "queen",
            ).map(OfflineRecord::videoId),
        )
        assertEquals(
            listOf("bowie-song"),
            filterDownloads(
                listOf(queen, bowie),
                DownloadFilter.FAILED,
                "heroes",
            ).map(OfflineRecord::videoId),
        )
    }

    @Test
    fun bulkActionsSelectOnlyEligibleRecordsWithinOptionalScope() {
        val records =
            listOf(
                record(song("queued"), OfflineState.QUEUED),
                record(song("active"), OfflineState.DOWNLOADING),
                record(song("failed"), OfflineState.FAILED),
                record(song("done"), OfflineState.COMPLETED),
            )

        assertEquals(
            setOf("queued", "active"),
            bulkActionIds(
                records,
                BulkDownloadAction.CANCEL_PENDING,
            ).toSet(),
        )
        assertEquals(
            listOf("failed"),
            bulkActionIds(
                records,
                BulkDownloadAction.RETRY_FAILED,
                allowedIds = setOf("failed", "done"),
            ),
        )
        assertEquals(
            listOf("done"),
            bulkActionIds(
                records,
                BulkDownloadAction.REMOVE_COMPLETED,
            ),
        )
        assertTrue(groupDownloads(records).isNotEmpty())
    }

    private fun record(
        song: SongItem,
        state: OfflineState,
    ): OfflineRecord =
        OfflineRecord.queued(song, 1L).copy(
            state = state,
            bytesDownloaded = if (state == OfflineState.COMPLETED) 10L else 0L,
            contentLength = if (state == OfflineState.COMPLETED) 10L else null,
        )

    private fun song(
        id: String = "status-song",
        title: String = "Status Song",
        artist: String? = null,
    ) = SongItem(
        id = id,
        title = title,
        artists = artist?.let { listOf(Artist(it, null)) }.orEmpty(),
        thumbnail = "",
    )
}
