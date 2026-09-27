package com.metrolist.music.car.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private inline fun <reified T> assertIs(value: Any?): T {
    assertTrue(
        "Expected " + T::class.simpleName + " but was " + value?.let { it::class.simpleName },
        value is T,
    )
    @Suppress("UNCHECKED_CAST")
    return value as T
}

class CarLibraryRepositoryTest {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
    }

    @Test
    fun opensLargeManifestWithoutTouchingMediaPayloads() {
        val location = CarLibraryLocation.FilePath("/car/library-a")
        val access = FakeTreeAccess(location, manifest(songCount = 5_000))
        val store = MemoryLocationStore()
        val repository =
            CarLibraryRepository(
                locationStore = store,
                accessFactory = CarLibraryTreeAccessFactory { null },
            )

        val opened = assertIs<CarLibraryOpenResult.Opened>(repository.open(access))

        assertEquals(5_000, opened.index.songs.size)
        assertEquals(0, access.mediaChecks)
        assertEquals(1, access.textReads)
        assertEquals(location, store.load())
    }

    @Test
    fun movedLibraryResolvesRelativeMediaFromNewRootAndMissingTrackIsIsolated() {
        val oldLocation = CarLibraryLocation.FilePath("/old/library")
        val newLocation = CarLibraryLocation.FilePath("/new/library")
        val raw = manifest(songCount = 3)
        val store = MemoryLocationStore(oldLocation)
        val newAccess =
            FakeTreeAccess(
                location = newLocation,
                manifest = raw,
                missing = setOf("media/song-2.media"),
            )
        val repository =
            CarLibraryRepository(
                locationStore = store,
                accessFactory = CarLibraryTreeAccessFactory { newAccess },
            )

        val opened = assertIs<CarLibraryOpenResult.Opened>(repository.open(newAccess))
        assertEquals("library-test", opened.index.libraryId)

        val first = assertIs<CarLibraryTrackAvailability.Available>(
            repository.trackAvailability("song-1"),
        )
        assertTrue(first.locator.contains("/new/library/media/song-1.media"))

        assertIs<CarLibraryTrackAvailability.Unavailable>(
            repository.trackAvailability("song-2"),
        )
        assertIs<CarLibraryTrackAvailability.Available>(
            repository.trackAvailability("song-3"),
        )
        assertEquals(3, repository.index()?.songs?.size)
        assertEquals(newLocation, store.load())
    }

    @Test
    fun restartReopensPersistedLibraryAndLostAccessKeepsRecoveryLocation() {
        val location = CarLibraryLocation.FilePath("/car/library")
        val access = FakeTreeAccess(location, manifest(songCount = 2))
        val store = MemoryLocationStore()
        val first =
            CarLibraryRepository(
                locationStore = store,
                accessFactory = CarLibraryTreeAccessFactory { access },
            )
        assertIs<CarLibraryOpenResult.Opened>(first.open(access))

        val restarted =
            CarLibraryRepository(
                locationStore = store,
                accessFactory = CarLibraryTreeAccessFactory { saved ->
                    if (saved == location) access else null
                },
            )
        assertIs<CarLibraryOpenResult.Opened>(restarted.reopen())

        val unavailable =
            CarLibraryRepository(
                locationStore = store,
                accessFactory = CarLibraryTreeAccessFactory { null },
            )
        assertIs<CarLibraryOpenResult.Unavailable>(unavailable.reopen())
        assertEquals(location, store.load())
    }

    private fun manifest(songCount: Int): String =
        json.encodeToString(
            CarLibraryManifest(
                libraryId = "library-test",
                revision = "revision-1",
                createdAt = "2026-09-27T12:00:00Z",
                updatedAt = "2026-09-27T12:00:00Z",
                producer = CarLibraryProducer("MetroList Windows", "0.2.3"),
                songs =
                    (1..songCount).map { index ->
                        CarLibrarySong(
                            id = "song-$index",
                            title = "Song $index",
                            artists = listOf(CarLibraryArtist(name = "Artist")),
                            mediaPath = "media/song-$index.media",
                            artworkPath = "artwork/song-$index.img",
                            fileSize = 100L + index,
                            sha256 = "a".repeat(64),
                        )
                    },
            ),
        )

    private class MemoryLocationStore(
        initial: CarLibraryLocation? = null,
    ) : CarLibraryLocationStore {
        private var value = initial

        override fun load(): CarLibraryLocation? = value

        override fun save(location: CarLibraryLocation) {
            value = location
        }
    }

    private class FakeTreeAccess(
        override val location: CarLibraryLocation,
        private val manifest: String,
        private val missing: Set<String> = emptySet(),
    ) : CarLibraryTreeAccess {
        var textReads = 0
        var mediaChecks = 0

        override fun readText(relativePath: String): String? {
            textReads++
            return if (relativePath == "library.json") manifest else null
        }

        override fun exists(relativePath: String): Boolean {
            mediaChecks++
            return relativePath !in missing
        }

        override fun locator(relativePath: String): String =
            when (val current = location) {
                is CarLibraryLocation.FilePath ->
                    "file://" + current.path.trimEnd('/') + "/" + relativePath
                is CarLibraryLocation.TreeUri ->
                    current.uri.trimEnd('/') + "/" + relativePath
            }
    }
}