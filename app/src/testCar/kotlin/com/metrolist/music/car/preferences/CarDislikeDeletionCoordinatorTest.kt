package com.metrolist.music.car.preferences

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarDislikeDeletionCoordinatorTest {
    @Test
    fun doesNothingWithoutConfirmation() = runBlocking {
        val root = Files.createTempDirectory("car-dislike-delete-confirm").toFile()
        try {
            val repository = CarDislikeRepository(CarDislikeLedgerStore(root)) { 1_000L }
            repository.dislike(
                CarDislikeSnapshot("one", "One", listOf("Artist")),
            )
            var called = false
            val coordinator =
                CarDislikeDeletionCoordinator(repository) {
                    called = true
                    Result.success(Unit)
                }

            val result = coordinator.deletePending(confirmed = false)

            assertFalse(called)
            assertEquals(0, result.deletedCount)
            assertEquals(1, repository.records.value.size)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun partialFailureKeepsFailedItemPendingAndDeletesSuccessfulItem() = runBlocking {
        val root = Files.createTempDirectory("car-dislike-delete-partial").toFile()
        try {
            var now = 1_000L
            val repository = CarDislikeRepository(CarDislikeLedgerStore(root)) { now }
            repository.dislike(CarDislikeSnapshot("ok", "Okay", listOf("A")))
            repository.dislike(CarDislikeSnapshot("fail", "Fail", listOf("B")))
            now = 2_000L
            val coordinator =
                CarDislikeDeletionCoordinator(repository) { record ->
                    if (record.songId == "fail") {
                        Result.failure(IllegalStateException("read-only"))
                    } else {
                        Result.success(Unit)
                    }
                }

            val result = coordinator.deletePending(confirmed = true)
            val ok = repository.records.value.single { it.songId == "ok" }
            val fail = repository.records.value.single { it.songId == "fail" }
            assertEquals(1, result.deletedCount)
            assertEquals(1, result.failedCount)
            assertTrue(ok.isDeleted)
            assertNull(ok.lastDeletionError)
            assertFalse(fail.isDeleted)
            assertEquals("read-only", fail.lastDeletionError)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun alreadyDeletedEntriesAreNotDeletedTwice() = runBlocking {
        val root = Files.createTempDirectory("car-dislike-delete-idempotent").toFile()
        try {
            val repository = CarDislikeRepository(CarDislikeLedgerStore(root)) { 1_000L }
            repository.dislike(CarDislikeSnapshot("done", "Done", listOf("A")))
            repository.markDeleted("done")
            var calls = 0
            val coordinator =
                CarDislikeDeletionCoordinator(repository) {
                    calls += 1
                    Result.success(Unit)
                }

            val result = coordinator.deletePending(confirmed = true)

            assertEquals(0, calls)
            assertEquals(0, result.deletedCount)
            assertEquals(0, result.failedCount)
        } finally {
            root.deleteRecursively()
        }
    }
}
