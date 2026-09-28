package com.metrolist.music.car.library

import com.metrolist.music.db.entities.Song
import com.metrolist.music.db.entities.SongEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarUnifiedCatalogTest {
    @Test
    fun deduplicatesCanonicalIdAndPrefersCarLibraryWithLocalFallback() {
        val car =
            CarLibraryIndex.from(
                CarLibraryManifest(
                    libraryId = "library-1",
                    revision = "rev-1",
                    createdAt = "2026-09-27T00:00:00Z",
                    updatedAt = "2026-09-27T00:00:00Z",
                    producer = CarLibraryProducer("MetroList Windows", "0.3.0"),
                    songs =
                        listOf(
                            CarLibrarySong(
                                id = "same-id",
                                title = "Car Title",
                                mediaPath = "media/same-id.media",
                                fileSize = 100,
                                sha256 = "a".repeat(64),
                            ),
                        ),
                ),
            )
        val downloaded =
            Song(
                song =
                    SongEntity(
                        id = "same-id",
                        title = "Downloaded Title",
                        isDownloaded = true,
                    ),
                artists = emptyList(),
            )

        val catalog =
            CarUnifiedCatalog.merge(
                index = car,
                downloadedSongs = listOf(downloaded),
                legacySongIds = setOf("same-id"),
            )

        assertEquals(1, catalog.size)
        assertEquals(CarSongSourceKind.CAR_LIBRARY_LOCAL, catalog.single().primarySource)
        assertTrue(CarSongSourceKind.LEGACY_IMPORTED in catalog.single().sourceKinds)
        assertEquals("Car Title", catalog.single().title)
    }

    @Test
    fun keepsDeviceOnlyDownloadWhenCarLibraryDoesNotContainIt() {
        val downloaded =
            Song(
                song =
                    SongEntity(
                        id = "device-only",
                        title = "Device only",
                        isDownloaded = true,
                    ),
                artists = emptyList(),
            )

        val catalog =
            CarUnifiedCatalog.merge(
                index = null,
                downloadedSongs = listOf(downloaded),
                legacySongIds = emptySet(),
            )

        assertEquals(1, catalog.size)
        assertEquals(CarSongSourceKind.CAR_LOCAL_DOWNLOAD, catalog.single().primarySource)
    }
}