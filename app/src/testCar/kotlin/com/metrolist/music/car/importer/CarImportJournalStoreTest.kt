package com.metrolist.music.car.importer

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarImportJournalStoreTest {
    @Test
    fun journalRoundTripsOverwritesAndClearsAtomically() {
        val root = Files.createTempDirectory("car-import-journal-test").toFile()
        try {
            val store = CarImportJournalStore(root)
            val record =
                CarSongImportRecord(
                    songId = "song1",
                    contentLength = 123,
                    artworkExpected = true,
                    initialCachePresent = false,
                    initialDownloadPresent = false,
                    initialRoomPresent = true,
                    initialRoomDownloaded = false,
                    initialRoomDateDownload = null,
                    initialArtworkPresent = false,
                )
            val journal =
                CarImportJournal(
                    sourceIdentity = "content://backup/one",
                    archiveFingerprint = "abc123",
                    phase = CarImportPhase.IMPORTING,
                    songs = mapOf("song1" to record),
                )

            store.save(journal)

            assertEquals(journal, store.load())
            assertFalse(store.temporaryFile.exists())

            val completed =
                journal.copy(
                    phase = CarImportPhase.COMPLETE,
                    songs = mapOf("song1" to record.copy(completed = true)),
                )
            store.save(completed)

            assertEquals(completed, store.load())
            assertFalse(store.temporaryFile.exists())

            store.clear()
            assertNull(store.load())
            assertFalse(store.temporaryFile.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun corruptJournalIsReportedInsteadOfSilentlyIgnored() {
        val root = Files.createTempDirectory("car-import-journal-corrupt").toFile()
        try {
            val store = CarImportJournalStore(root)
            store.journalFile.parentFile?.mkdirs()
            store.journalFile.writeText("{not-json")

            val error =
                runCatching { store.load() }
                    .exceptionOrNull()

            assertTrue(error is CarImportJournalException)
        } finally {
            root.deleteRecursively()
        }
    }
}
