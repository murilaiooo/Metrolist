package com.metrolist.music.car.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CarImportExclusionTest {
    @Test
    fun dislikedSongIdsAreRemovedBeforeImportPlanning() {
        val keep = portableSong("keep")
        val block = portableSong("block")
        val backup =
            ValidatedCarBackup(
                manifest =
                    CarPortableManifest(
                        createdAt = "2026-09-28T00:00:00Z",
                        exportedSongCount = 2,
                        exportedDownloadCount = 2,
                        totalCacheBytes = 20,
                        songIds = listOf("keep", "block"),
                        requestIds = listOf("keep", "block"),
                        exportedCacheResources = emptyList(),
                        files = emptyList(),
                    ),
                songs = listOf(keep, block),
                artists = emptyList(),
                albums = emptyList(),
                artistMaps =
                    listOf(
                        CarPortableMap("keep", "artist", 0),
                        CarPortableMap("block", "artist", 0),
                    ),
                albumMaps = emptyList(),
                formats = emptyList(),
                transfers = emptyList(),
                resources =
                    listOf(
                        CarPortableResource("keep", "keep", 10, emptyList()),
                        CarPortableResource("block", "block", 10, emptyList()),
                    ),
            )

        val filtered = backup.withoutSongs(setOf("block"))

        assertEquals(listOf("keep"), filtered.songs.map { it.id })
        assertEquals(listOf("keep"), filtered.resources.map { it.songId })
        assertEquals(listOf("keep"), filtered.artistMaps.map { it.songId })
        assertFalse(filtered.songs.any { it.id == "block" })
    }

    private fun portableSong(id: String) =
        CarPortableSong(
            id = id,
            title = id,
            duration = 120,
            thumbnailUrl = null,
            thumbnailArtworkPath = null,
            albumId = null,
            albumName = null,
            explicit = false,
            year = null,
            date = null,
            dateModified = null,
            dateDownload = null,
            isVideo = false,
            isEpisode = false,
        )
}
