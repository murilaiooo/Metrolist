package com.metrolist.desktop.player

import com.metrolist.innertube.models.SongItem

data class QueueMutationResult(
    val queue: List<SongItem>,
    val currentIndex: Int,
)

internal fun insertNext(
    queue: List<SongItem>,
    currentIndex: Int,
    song: SongItem,
): QueueMutationResult {
    requireCurrentIndex(queue, currentIndex)
    val insertAt = currentIndex + 1
    val updated = queue.toMutableList().apply { add(insertAt, song) }
    return QueueMutationResult(updated, currentIndex)
}

internal fun appendToQueue(
    queue: List<SongItem>,
    currentIndex: Int,
    song: SongItem,
): QueueMutationResult {
    requireCurrentIndex(queue, currentIndex)
    return QueueMutationResult(queue + song, currentIndex)
}

internal fun moveQueueEntry(
    queue: List<SongItem>,
    currentIndex: Int,
    fromIndex: Int,
    toIndex: Int,
): QueueMutationResult {
    requireCurrentIndex(queue, currentIndex)
    require(fromIndex in queue.indices) { "Queue source index is invalid" }
    require(toIndex in queue.indices) { "Queue target index is invalid" }
    if (fromIndex == toIndex) return QueueMutationResult(queue, currentIndex)

    val updated = queue.toMutableList()
    val moved = updated.removeAt(fromIndex)
    updated.add(toIndex, moved)

    val newCurrentIndex =
        when {
            fromIndex == currentIndex -> toIndex
            fromIndex < currentIndex && toIndex >= currentIndex -> currentIndex - 1
            fromIndex > currentIndex && toIndex <= currentIndex -> currentIndex + 1
            else -> currentIndex
        }

    return QueueMutationResult(updated, newCurrentIndex)
}

internal fun removeQueueEntry(
    queue: List<SongItem>,
    currentIndex: Int,
    removeIndex: Int,
): QueueMutationResult {
    requireCurrentIndex(queue, currentIndex)
    require(removeIndex in queue.indices) { "Queue remove index is invalid" }
    require(removeIndex != currentIndex) { "Cannot remove the currently playing queue item" }

    val updated = queue.toMutableList().apply { removeAt(removeIndex) }
    val newCurrentIndex = if (removeIndex < currentIndex) currentIndex - 1 else currentIndex
    return QueueMutationResult(updated, newCurrentIndex)
}

internal fun clearUpcomingEntries(
    queue: List<SongItem>,
    currentIndex: Int,
): QueueMutationResult {
    requireCurrentIndex(queue, currentIndex)
    return QueueMutationResult(queue.take(currentIndex + 1), currentIndex)
}

private fun requireCurrentIndex(
    queue: List<SongItem>,
    currentIndex: Int,
) {
    require(queue.isNotEmpty()) { "Queue cannot be empty" }
    require(currentIndex in queue.indices) { "Current queue index is invalid" }
}
