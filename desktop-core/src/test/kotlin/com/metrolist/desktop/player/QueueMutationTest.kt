package com.metrolist.desktop.player

import com.metrolist.innertube.models.SongItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class QueueMutationTest {
    @Test
    fun insertNextPlacesSongImmediatelyAfterCurrent() {
        val queue = songs("a", "b", "c")

        val result = insertNext(queue, currentIndex = 1, song = song("x"))

        assertEquals(listOf("a", "b", "x", "c"), result.queue.map { it.id })
        assertEquals(1, result.currentIndex)
    }

    @Test
    fun appendKeepsCurrentIndex() {
        val result = appendToQueue(songs("a", "b"), currentIndex = 0, song = song("c"))

        assertEquals(listOf("a", "b", "c"), result.queue.map { it.id })
        assertEquals(0, result.currentIndex)
    }

    @Test
    fun movingItemFromBeforeCurrentToAfterCurrentAdjustsCurrentIndex() {
        val result = moveQueueEntry(songs("a", "b", "c", "d"), currentIndex = 1, fromIndex = 0, toIndex = 3)

        assertEquals(listOf("b", "c", "d", "a"), result.queue.map { it.id })
        assertEquals(0, result.currentIndex)
        assertEquals("b", result.queue[result.currentIndex].id)
    }

    @Test
    fun movingItemFromAfterCurrentToBeforeCurrentAdjustsCurrentIndex() {
        val result = moveQueueEntry(songs("a", "b", "c", "d"), currentIndex = 1, fromIndex = 3, toIndex = 0)

        assertEquals(listOf("d", "a", "b", "c"), result.queue.map { it.id })
        assertEquals(2, result.currentIndex)
        assertEquals("b", result.queue[result.currentIndex].id)
    }

    @Test
    fun movingCurrentEntryMovesItsIndexButKeepsSameEntryCurrent() {
        val result = moveQueueEntry(songs("a", "b", "c"), currentIndex = 1, fromIndex = 1, toIndex = 2)

        assertEquals(listOf("a", "c", "b"), result.queue.map { it.id })
        assertEquals(2, result.currentIndex)
        assertEquals("b", result.queue[result.currentIndex].id)
    }

    @Test
    fun removingBeforeCurrentShiftsCurrentIndex() {
        val result = removeQueueEntry(songs("a", "b", "c"), currentIndex = 1, removeIndex = 0)

        assertEquals(listOf("b", "c"), result.queue.map { it.id })
        assertEquals(0, result.currentIndex)
        assertEquals("b", result.queue[result.currentIndex].id)
    }

    @Test
    fun removingCurrentEntryIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            removeQueueEntry(songs("a", "b", "c"), currentIndex = 1, removeIndex = 1)
        }
    }

    @Test
    fun clearUpcomingKeepsHistoryAndCurrentOnly() {
        val result = clearUpcomingEntries(songs("a", "b", "c", "d"), currentIndex = 1)

        assertEquals(listOf("a", "b"), result.queue.map { it.id })
        assertEquals(1, result.currentIndex)
    }

    @Test
    fun duplicateSongIdsRemainDistinctQueueEntries() {
        val first = song("dup").copy(title = "First occurrence")
        val second = song("dup").copy(title = "Second occurrence")
        val queue = listOf(first, song("middle"), second)

        val result = moveQueueEntry(queue, currentIndex = 0, fromIndex = 2, toIndex = 1)

        assertEquals(listOf("First occurrence", "Second occurrence", "middle"), result.queue.map { it.title })
        assertEquals(0, result.currentIndex)
        assertEquals("First occurrence", result.queue[result.currentIndex].title)
    }

    @Test
    fun invalidIndicesAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            moveQueueEntry(songs("a"), currentIndex = 0, fromIndex = 1, toIndex = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            removeQueueEntry(songs("a"), currentIndex = 2, removeIndex = 0)
        }
    }

    private fun songs(vararg ids: String) = ids.map(::song)

    private fun song(id: String) =
        SongItem(
            id = id,
            title = id,
            artists = emptyList(),
            thumbnail = "",
        )
}
