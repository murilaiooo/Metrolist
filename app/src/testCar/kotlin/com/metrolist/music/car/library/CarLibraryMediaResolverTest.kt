package com.metrolist.music.car.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@RunWith(RobolectricTestRunner::class)
class CarLibraryMediaResolverTest {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
    }

    @Test
    fun availableTrackBuildsDirectLocalMediaItemWithLocalArtwork() {
        val access =
            FakeAccess(
                missing = emptySet(),
            )
        val repository = repository(access)
        val resolver = CarLibraryMediaResolver(repository)

        val ready =
            assertType<CarLibraryMediaResolution.Ready>(
                resolver.resolve("song-1"),
            )

        assertEquals("song-1", ready.mediaItem.mediaId)
        assertEquals(
            "file:///library/media/song-1.media",
            ready.mediaItem.localConfiguration?.uri?.toString(),
        )
        assertEquals(
            "file:///library/artwork/song-1.img",
            ready.mediaItem.mediaMetadata.artworkUri?.toString(),
        )
        assertEquals("Song 1", ready.mediaItem.mediaMetadata.title)
    }

    @Test
    fun missingTrackReturnsTypedUnavailableWithoutRemoteFallback() {
        val access =
            FakeAccess(
                missing = setOf("media/song-1.media"),
            )
        val repository = repository(access)
        val resolver = CarLibraryMediaResolver(repository)

        val unavailable =
            assertType<CarLibraryMediaResolution.Unavailable>(
                resolver.resolve("song-1"),
            )

        assertEquals(CarLibraryUnavailableReason.MEDIA_MISSING, unavailable.reason)
    }

    private fun repository(access: FakeAccess): CarLibraryRepository {
        val repository =
            CarLibraryRepository(
                locationStore =
                    object : CarLibraryLocationStore {
                        override fun load(): CarLibraryLocation? = access.location
                        override fun save(location: CarLibraryLocation) = Unit
                    },
                accessFactory = CarLibraryTreeAccessFactory { access },
            )
        assertType<CarLibraryOpenResult.Opened>(repository.open(access))
        return repository
    }

    private inner class FakeAccess(
        private val missing: Set<String>,
    ) : CarLibraryTreeAccess {
        override val location = CarLibraryLocation.FilePath("/library")

        override fun readText(relativePath: String): String? =
            if (relativePath == "library.json") {
                json.encodeToString(
                    CarLibraryManifest(
                        libraryId = "library-1",
                        revision = "revision-1",
                        createdAt = "2026-09-27T12:00:00Z",
                        updatedAt = "2026-09-27T12:00:00Z",
                        producer = CarLibraryProducer("MetroList Windows", "0.2.3"),
                        songs =
                            listOf(
                                CarLibrarySong(
                                    id = "song-1",
                                    title = "Song 1",
                                    artists = listOf(CarLibraryArtist("artist-1", "Artist")),
                                    album = CarLibraryAlbum("album-1", "Album"),
                                    duration = 180,
                                    mediaPath = "media/song-1.media",
                                    artworkPath = "artwork/song-1.img",
                                    fileSize = 1024,
                                    sha256 = "a".repeat(64),
                                    artworkFileSize = 128,
                                    artworkSha256 = "b".repeat(64),
                                ),
                            ),
                    ),
                )
            } else {
                null
            }

        override fun exists(relativePath: String): Boolean =
            relativePath !in missing

        override fun locator(relativePath: String): String =
            "file:///library/" + relativePath
    }

    private inline fun <reified T> assertType(value: Any?): T {
        assertTrue(value is T)
        @Suppress("UNCHECKED_CAST")
        return value as T
    }
}