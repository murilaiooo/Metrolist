package com.metrolist.music.car.importer

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CarImportRepositoryTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("car-import", Context.MODE_PRIVATE).edit().clear().commit()
        context.filesDir.resolve("car-import").deleteRecursively()
    }

    @Test
    fun rememberedSourceAndInterruptedJournalSurviveRepositoryRecreation() {
        val source = Uri.parse("content://backup/library")
        val first = CarImportRepository(context)
        first.rememberSource(source)
        first.journalStore.save(
            CarImportJournal(
                sourceIdentity = source.toString(),
                archiveFingerprint = "abc",
                phase = CarImportPhase.IMPORTING,
            ),
        )

        val restored = CarImportRepository(context)

        assertEquals(source, restored.sourceUri())
        val state = restored.status.value
        assertTrue(state is CarImportStatus.Interrupted)
        assertEquals(CarImportPhase.IMPORTING, (state as CarImportStatus.Interrupted).phase)
        assertEquals(source.toString(), state.sourceIdentity)
    }

    @Test
    fun progressAndCompletionArePublished() {
        val repository = CarImportRepository(context)
        val source = Uri.parse("content://backup/library")
        repository.rememberSource(source)
        val progress =
            CarImportProgress(
                phase = CarImportPhase.IMPORTING,
                completedSongs = 2,
                totalSongs = 10,
                bytesProcessed = 40,
                totalBytes = 100,
            )

        repository.updateProgress(source.toString(), progress)

        assertEquals(
            CarImportStatus.Running(source.toString(), progress),
            repository.status.value,
        )

        repository.applyResult(
            source.toString(),
            CarImportResult.Success(importedSongs = 10, totalSongs = 10),
        )

        assertEquals(
            CarImportStatus.Complete(source.toString(), 10, 10),
            repository.status.value,
        )
    }
}
