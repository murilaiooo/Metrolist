package com.metrolist.music.car.library

import com.metrolist.music.car.feedback.CarOperationStage
import com.metrolist.music.car.feedback.CarOperationState
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CarLibrarySessionTest {
    private val json = Json { encodeDefaults = true; explicitNulls = true }

    @Test
    fun reopenPublishesRestoringBeforeReadAndSuccessAfterOpen() = runBlocking {
        val location = CarLibraryLocation.FilePath("/car/library")
        val store = MemoryStore(location)
        lateinit var session: CarLibrarySession
        val access =
            FakeAccess(location, manifest("revision-1")) {
                val running = session.operation.value as CarOperationState.Running
                assertEquals(CarOperationStage.RESTORING, running.progress.stage)
            }
        val repository = CarLibraryRepository(store, CarLibraryTreeAccessFactory { access })
        session = CarLibrarySession(repository)

        val result = session.reopen()

        assertTrue(result is CarLibraryOpenResult.Opened)
        assertTrue(session.operation.value is CarOperationState.Success)
        assertEquals("revision-1", session.index.value?.revision)
    }

    @Test
    fun explicitOpenPublishesOpeningLibraryBeforeRead() = runBlocking {
        val location = CarLibraryLocation.FilePath("/car/library")
        val store = MemoryStore()
        lateinit var session: CarLibrarySession
        val access =
            FakeAccess(location, manifest("revision-1")) {
                val running = session.operation.value as CarOperationState.Running
                assertEquals(CarOperationStage.OPENING_LIBRARY, running.progress.stage)
            }
        val repository = CarLibraryRepository(store, CarLibraryTreeAccessFactory { access })
        session = CarLibrarySession(repository)

        val result = session.open(access)

        assertTrue(result is CarLibraryOpenResult.Opened)
        assertTrue(session.operation.value is CarOperationState.Success)
    }

    @Test
    fun refreshFailurePublishesErrorWithoutDroppingLastGoodIndex() = runBlocking {
        val location = CarLibraryLocation.FilePath("/car/library")
        val store = MemoryStore()
        lateinit var session: CarLibrarySession
        val access = FakeAccess(location, manifest("revision-1"))
        val repository = CarLibraryRepository(store, CarLibraryTreeAccessFactory { access })
        session = CarLibrarySession(repository)
        session.open(access)
        val previous = session.index.value
        access.failReads = true
        access.onRead = {
            val running = session.operation.value as CarOperationState.Running
            assertEquals(CarOperationStage.REFRESHING_LIBRARY, running.progress.stage)
        }

        val result = session.refresh()

        assertTrue(result is CarLibraryRefreshResult.Unavailable)
        assertSame(previous, session.index.value)
        val error = session.operation.value as CarOperationState.Error
        assertTrue(error.message.contains("library", ignoreCase = true))
    }

    @Test
    fun startupCoordinatorReopensOnlyOnce() = runBlocking {
        var calls = 0
        val coordinator =
            CarLibraryStartupCoordinator {
                calls++
                CarLibraryOpenResult.Unavailable(null, "No library")
            }

        coordinator.reopenOnce()
        coordinator.reopenOnce()

        assertEquals(1, calls)
    }

    @Test
    fun queueFeedbackExposesSelectedTrackAndActionableFailure() {
        val preparing = carQueuePreparingOperation("song-1", "My Song")
        assertEquals("song-1", preparing.songId)
        val running = preparing.operation as CarOperationState.Running
        assertEquals(CarOperationStage.PREPARING_QUEUE, running.progress.stage)
        assertTrue(running.progress.detail!!.contains("My Song"))

        val failed = carQueueUnavailableOperation("song-1", "Track file is unavailable")
        val error = failed.operation as CarOperationState.Error
        assertEquals("song-1", failed.songId)
        assertTrue(error.message.contains("unavailable"))
        assertTrue(error.actionHint!!.isNotBlank())
    }

    private fun manifest(revision: String): String =
        json.encodeToString(
            CarLibraryManifest(
                libraryId = "library-test",
                revision = revision,
                createdAt = "2026-09-28T00:00:00Z",
                updatedAt = "2026-09-28T00:00:00Z",
                producer = CarLibraryProducer("MetroList Windows", "0.3.1"),
                songs =
                    listOf(
                        CarLibrarySong(
                            id = "song-1",
                            title = "Song 1",
                            artists = listOf(CarLibraryArtist(name = "Artist")),
                            mediaPath = "media/song-1.media",
                            fileSize = 4L,
                            sha256 = "a".repeat(64),
                        ),
                    ),
            ),
        )

    private class MemoryStore(initial: CarLibraryLocation? = null) : CarLibraryLocationStore {
        private var value = initial
        override fun load(): CarLibraryLocation? = value
        override fun save(location: CarLibraryLocation) {
            value = location
        }
    }

    private class FakeAccess(
        override val location: CarLibraryLocation,
        private val manifest: String,
        var onRead: () -> Unit = {},
    ) : CarLibraryTreeAccess {
        var failReads = false

        override fun readText(relativePath: String): String? {
            onRead()
            if (failReads) error("Library access lost")
            return if (relativePath == "library.json") manifest else null
        }

        override fun exists(relativePath: String): Boolean = true
        override fun locator(relativePath: String): String = "file:///car/library/$relativePath"
    }
}
