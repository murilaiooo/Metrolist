package com.metrolist.music.car.preferences

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarDislikeRepositoryTest {
    @Test
    fun dislikePersistsAndReloadsExactMetadata() {
        val root = Files.createTempDirectory("car-dislike-ledger").toFile()
        try {
            var now = 1_000L
            val store = CarDislikeLedgerStore(root)
            val repository = CarDislikeRepository(store) { now }

            repository.dislike(
                CarDislikeSnapshot(
                    songId = "song-1",
                    title = "Song One",
                    artists = listOf("Artist A", "Artist B"),
                    album = "Album One",
                    sourceKinds = setOf("LEGACY_IMPORTED"),
                ),
            )

            val reloaded = CarDislikeRepository(CarDislikeLedgerStore(root)) { 9_999L }
            val record = reloaded.records.value.single()

            assertEquals("song-1", record.songId)
            assertEquals("Song One", record.title)
            assertEquals(listOf("Artist A", "Artist B"), record.artists)
            assertEquals("Album One", record.album)
            assertEquals(1_000L, record.dislikedAtEpochMillis)
            assertEquals(listOf("LEGACY_IMPORTED"), record.sourceKinds)
            assertNull(record.deletedAtEpochMillis)
            assertEquals(setOf("song-1"), reloaded.activeSongIds.value)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun duplicateDislikeIsIdempotentAndRefreshesSnapshotWithoutDuplicating() {
        val root = Files.createTempDirectory("car-dislike-idempotent").toFile()
        try {
            var now = 1_000L
            val repository = CarDislikeRepository(CarDislikeLedgerStore(root)) { now }

            repository.dislike(
                CarDislikeSnapshot(
                    songId = "song-1",
                    title = "Old title",
                    artists = listOf("Artist"),
                ),
            )
            now = 2_000L
            repository.dislike(
                CarDislikeSnapshot(
                    songId = "song-1",
                    title = "Correct title",
                    artists = listOf("Artist"),
                ),
            )

            val record = repository.records.value.single()
            assertEquals("Correct title", record.title)
            assertEquals(1_000L, record.dislikedAtEpochMillis)
            assertEquals(1, repository.records.value.size)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun deletedRecordRetainsPreferenceHistoryAndMetadata() {
        val root = Files.createTempDirectory("car-dislike-delete").toFile()
        try {
            var now = 1_000L
            val repository = CarDislikeRepository(CarDislikeLedgerStore(root)) { now }
            repository.dislike(
                CarDislikeSnapshot(
                    songId = "song-1",
                    title = "Song One",
                    artists = listOf("Artist A"),
                ),
            )

            now = 5_000L
            repository.markDeleted("song-1")

            val record = repository.records.value.single()
            assertEquals("Song One", record.title)
            assertEquals(listOf("Artist A"), record.artists)
            assertEquals(5_000L, record.deletedAtEpochMillis)
            assertEquals(setOf("song-1"), repository.activeSongIds.value)

            val reloaded = CarDislikeRepository(CarDislikeLedgerStore(root)) { 8_000L }
            assertEquals(5_000L, reloaded.records.value.single().deletedAtEpochMillis)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun undoRemovesActiveDislikeButCannotRestoreDeletedMedia() {
        val root = Files.createTempDirectory("car-dislike-undo").toFile()
        try {
            var now = 1_000L
            val repository = CarDislikeRepository(CarDislikeLedgerStore(root)) { now }
            repository.dislike(
                CarDislikeSnapshot(
                    songId = "song-1",
                    title = "Song One",
                    artists = listOf("Artist A"),
                ),
            )
            now = 2_000L
            repository.markDeleted("song-1")

            val result = repository.undo("song-1")

            assertTrue(result.wasPhysicallyDeleted)
            assertTrue(repository.records.value.isEmpty())
            assertTrue(repository.activeSongIds.value.isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun deletionErrorKeepsItemPendingAndSurvivesReload() {
        val root = Files.createTempDirectory("car-dislike-error").toFile()
        try {
            val repository = CarDislikeRepository(CarDislikeLedgerStore(root)) { 1_000L }
            repository.dislike(
                CarDislikeSnapshot(
                    songId = "song-1",
                    title = "Song One",
                    artists = listOf("Artist A"),
                ),
            )

            repository.markDeletionError("song-1", "File is read-only")

            val record = repository.records.value.single()
            assertNull(record.deletedAtEpochMillis)
            assertEquals("File is read-only", record.lastDeletionError)

            val reloaded = CarDislikeRepository(CarDislikeLedgerStore(root)) { 2_000L }
            assertEquals("File is read-only", reloaded.records.value.single().lastDeletionError)
        } finally {
            root.deleteRecursively()
        }
    }
}
