package com.metrolist.desktop

import com.metrolist.desktop.offline.OfflineRecord
import com.metrolist.desktop.offline.OfflineState
import com.metrolist.innertube.models.SongItem

enum class DownloadFilter {
    ALL,
    DOWNLOADING,
    QUEUED,
    OFFLINE,
    FAILED,
}

enum class DownloadGroup(
    val label: String,
) {
    DOWNLOADING("Downloading"),
    QUEUED("Queued"),
    FAILED("Failed"),
    OFFLINE("Offline"),
}

enum class BulkDownloadAction {
    CANCEL_PENDING,
    RETRY_FAILED,
    REMOVE_COMPLETED,
    CLEAR_FAILED,
}

data class PlaylistDownloadSummary(
    val total: Int,
    val completed: Int,
    val downloading: Int,
    val queued: Int,
    val failed: Int,
) {
    val tracked: Int
        get() = completed + downloading + queued + failed

    val missing: Int
        get() = (total - tracked).coerceAtLeast(0)

    val active: Int
        get() = downloading + queued

    val progressFraction: Float
        get() =
            if (total <= 0) 0f
            else completed.toFloat() / total.toFloat()

    val allCompleted: Boolean
        get() = total > 0 && completed == total
}

internal fun summarizePlaylistDownloads(
    songs: List<SongItem>,
    downloadsById: Map<String, OfflineRecord>,
): PlaylistDownloadSummary {
    val ids = songs.map(SongItem::id).toSet()
    val records = downloadsById.values.filter { it.videoId in ids }

    return PlaylistDownloadSummary(
        total = ids.size,
        completed = records.count { it.state == OfflineState.COMPLETED },
        downloading = records.count { it.state == OfflineState.DOWNLOADING },
        queued = records.count { it.state == OfflineState.QUEUED },
        failed = records.count { it.state == OfflineState.FAILED },
    )
}

internal fun filterDownloads(
    downloads: List<OfflineRecord>,
    filter: DownloadFilter,
    query: String,
): List<OfflineRecord> {
    val normalized = query.trim().lowercase()
    return downloads.filter { record ->
        matchesDownloadFilter(record, filter) &&
            (
                normalized.isEmpty() ||
                    record.title.lowercase().contains(normalized) ||
                    record.artists.any { it.name.lowercase().contains(normalized) } ||
                    record.album?.name?.lowercase()?.contains(normalized) == true
            )
    }
}

internal fun groupDownloads(
    downloads: List<OfflineRecord>,
): List<Pair<DownloadGroup, List<OfflineRecord>>> {
    val order =
        listOf(
            DownloadGroup.DOWNLOADING,
            DownloadGroup.QUEUED,
            DownloadGroup.FAILED,
            DownloadGroup.OFFLINE,
        )
    return order.mapNotNull { group ->
        val records =
            downloads
                .filter { record -> groupFor(record.state) == group }
                .sortedByDescending(OfflineRecord::updateTimeMs)
        records.takeIf(List<OfflineRecord>::isNotEmpty)?.let { group to it }
    }
}

internal fun bulkActionIds(
    downloads: List<OfflineRecord>,
    action: BulkDownloadAction,
    allowedIds: Set<String>? = null,
): List<String> =
    downloads
        .asSequence()
        .filter { allowedIds == null || it.videoId in allowedIds }
        .filter { record ->
            when (action) {
                BulkDownloadAction.CANCEL_PENDING ->
                    record.state == OfflineState.QUEUED ||
                        record.state == OfflineState.DOWNLOADING
                BulkDownloadAction.RETRY_FAILED ->
                    record.state == OfflineState.FAILED
                BulkDownloadAction.REMOVE_COMPLETED ->
                    record.state == OfflineState.COMPLETED
                BulkDownloadAction.CLEAR_FAILED ->
                    record.state == OfflineState.FAILED
            }
        }
        .map(OfflineRecord::videoId)
        .distinct()
        .toList()

private fun matchesDownloadFilter(
    record: OfflineRecord,
    filter: DownloadFilter,
): Boolean =
    when (filter) {
        DownloadFilter.ALL -> true
        DownloadFilter.DOWNLOADING -> record.state == OfflineState.DOWNLOADING
        DownloadFilter.QUEUED -> record.state == OfflineState.QUEUED
        DownloadFilter.OFFLINE -> record.state == OfflineState.COMPLETED
        DownloadFilter.FAILED -> record.state == OfflineState.FAILED
    }

private fun groupFor(state: OfflineState): DownloadGroup =
    when (state) {
        OfflineState.DOWNLOADING -> DownloadGroup.DOWNLOADING
        OfflineState.QUEUED -> DownloadGroup.QUEUED
        OfflineState.FAILED -> DownloadGroup.FAILED
        OfflineState.COMPLETED -> DownloadGroup.OFFLINE
    }
