package com.metrolist.music.car.preferences

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CarDislikeUndoResult(
    val songId: String,
    val wasPhysicallyDeleted: Boolean,
)

class CarDislikeRepository(
    private val store: CarDislikeLedgerStore,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val _records =
        MutableStateFlow(
            store.load().records
                .sortedByDescending(CarDislikeRecord::dislikedAtEpochMillis),
        )
    val records: StateFlow<List<CarDislikeRecord>> = _records.asStateFlow()

    private val _activeSongIds =
        MutableStateFlow(_records.value.mapTo(linkedSetOf(), CarDislikeRecord::songId))
    val activeSongIds: StateFlow<Set<String>> = _activeSongIds.asStateFlow()

    @Synchronized
    fun dislike(snapshot: CarDislikeSnapshot): CarDislikeRecord {
        require(snapshot.songId.isNotBlank()) { "songId must not be blank" }
        require(snapshot.title.isNotBlank()) { "title must not be blank" }

        val existing = _records.value.firstOrNull { it.songId == snapshot.songId }
        val now = nowMillis()
        val updated =
            if (existing == null || existing.isDeleted) {
                CarDislikeRecord(
                    songId = snapshot.songId,
                    title = snapshot.title,
                    artists = snapshot.artists,
                    album = snapshot.album,
                    dislikedAtEpochMillis = now,
                    sourceKinds = snapshot.sourceKinds.sorted(),
                )
            } else {
                existing.copy(
                    title = snapshot.title,
                    artists = snapshot.artists,
                    album = snapshot.album,
                    sourceKinds = snapshot.sourceKinds.sorted(),
                    lastDeletionError = null,
                )
            }

        replace(updated)
        return updated
    }

    @Synchronized
    fun markDeleted(songId: String): CarDislikeRecord? {
        val existing = _records.value.firstOrNull { it.songId == songId } ?: return null
        if (existing.isDeleted) return existing
        val updated =
            existing.copy(
                deletedAtEpochMillis = nowMillis(),
                lastDeletionError = null,
            )
        replace(updated)
        return updated
    }

    @Synchronized
    fun markDeletionError(
        songId: String,
        message: String,
    ): CarDislikeRecord? {
        val existing = _records.value.firstOrNull { it.songId == songId } ?: return null
        val updated =
            existing.copy(
                deletedAtEpochMillis = null,
                lastDeletionError = message,
            )
        replace(updated)
        return updated
    }

    @Synchronized
    fun undo(songId: String): CarDislikeUndoResult {
        val existing = _records.value.firstOrNull { it.songId == songId }
        val remaining = _records.value.filterNot { it.songId == songId }
        persist(remaining)
        return CarDislikeUndoResult(
            songId = songId,
            wasPhysicallyDeleted = existing?.isDeleted == true,
        )
    }

    fun isDisliked(songId: String): Boolean = songId in _activeSongIds.value

    private fun replace(record: CarDislikeRecord) {
        val updated =
            (_records.value.filterNot { it.songId == record.songId } + record)
                .sortedByDescending(CarDislikeRecord::dislikedAtEpochMillis)
        persist(updated)
    }

    private fun persist(records: List<CarDislikeRecord>) {
        store.save(CarDislikeLedger(records = records))
        _records.value = records
        _activeSongIds.value = records.mapTo(linkedSetOf(), CarDislikeRecord::songId)
    }
}
