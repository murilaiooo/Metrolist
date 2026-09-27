package com.metrolist.music.car.importer

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarPortableMappersTest {
    @Test
    fun portableSongMapsToNativeDownloadedSong() {
        val entity =
            CarPortableMappers.toSongEntity(
                CarPortableSong(
                    id = "song1",
                    title = "Song 1",
                    duration = 245,
                    thumbnailUrl = "https://example/song.jpg",
                    thumbnailArtworkPath = "artwork/song1.img",
                    albumId = "album1",
                    albumName = "Album 1",
                    explicit = true,
                    year = 2026,
                    date = "2026-01-02T03:04:05",
                    dateModified = "2026-02-03T04:05:06",
                    dateDownload = "2026-09-27T07:00:00",
                    isVideo = false,
                    isEpisode = false,
                ),
            )

        assertEquals("song1", entity.id)
        assertEquals("Song 1", entity.title)
        assertEquals(245, entity.duration)
        assertEquals("album1", entity.albumId)
        assertEquals("Album 1", entity.albumName)
        assertTrue(entity.explicit)
        assertEquals(2026, entity.year)
        assertEquals(LocalDateTime.parse("2026-01-02T03:04:05"), entity.date)
        assertEquals(LocalDateTime.parse("2026-02-03T04:05:06"), entity.dateModified)
        assertEquals(LocalDateTime.parse("2026-09-27T07:00:00"), entity.dateDownload)
        assertTrue(entity.isDownloaded)
        assertFalse(entity.isVideo)
        assertFalse(entity.isEpisode)
    }

    @Test
    fun portableFormatPreservesRealQualityMetadata() {
        val entity =
            CarPortableMappers.toFormatEntity(
                CarPortableFormat(
                    id = "song1",
                    itag = 251,
                    mimeType = "audio/webm",
                    codecs = "opus",
                    bitrate = 137421,
                    sampleRate = 48000,
                    contentLength = 4_321_987L,
                    loudnessDb = -7.2,
                    perceptualLoudnessDb = -8.1,
                ),
            )

        assertEquals(251, entity.itag)
        assertEquals("audio/webm", entity.mimeType)
        assertEquals("opus", entity.codecs)
        assertEquals(137421, entity.bitrate)
        assertEquals(48000, entity.sampleRate)
        assertEquals(4_321_987L, entity.contentLength)
        assertEquals(-7.2, entity.loudnessDb!!, 0.0001)
        assertEquals(-8.1, entity.perceptualLoudnessDb!!, 0.0001)
    }

    @Test
    fun mergePreservesExistingPersonalStateWhileRefreshingPortableFields() {
        val existing =
            com.metrolist.music.db.entities.SongEntity(
                id = "song1",
                title = "Old title",
                liked = true,
                likedDate = LocalDateTime.parse("2025-01-01T00:00:00"),
                totalPlayTime = 9999,
                inLibrary = LocalDateTime.parse("2025-02-01T00:00:00"),
                playbackPosition = 12345,
            )
        val portable =
            CarPortableSong(
                id = "song1",
                title = "Fresh title",
                duration = 321,
                thumbnailUrl = "https://example/fresh.jpg",
                thumbnailArtworkPath = null,
                albumId = "album1",
                albumName = "Album 1",
                explicit = false,
                year = 2026,
                date = null,
                dateModified = null,
                dateDownload = "2026-09-27T08:00:00",
                isVideo = false,
                isEpisode = false,
            )

        val merged = CarPortableMappers.mergeSongEntity(existing, portable)

        assertEquals("Fresh title", merged.title)
        assertTrue(merged.isDownloaded)
        assertTrue(merged.liked)
        assertEquals(existing.likedDate, merged.likedDate)
        assertEquals(9999L, merged.totalPlayTime)
        assertEquals(existing.inLibrary, merged.inLibrary)
        assertEquals(12345L, merged.playbackPosition)
    }

    @Test(expected = IllegalArgumentException::class)
    fun missingPortableFormatQualityIsRejectedRatherThanInvented() {
        CarPortableMappers.toFormatEntity(
            CarPortableFormat(
                id = "song1",
                itag = 251,
                mimeType = null,
                codecs = null,
                bitrate = null,
                sampleRate = null,
                contentLength = 100,
                loudnessDb = null,
                perceptualLoudnessDb = null,
            ),
        )
    }

    @Test
    fun artistMappingsAreStableAndDeduplicated() {
        val maps =
            CarPortableMappers.toSongArtistMaps(
                songId = "song1",
                maps =
                    listOf(
                        CarPortableMap("song1", "artist2", 1),
                        CarPortableMap("song1", "artist1", 0),
                        CarPortableMap("song1", "artist1", 0),
                    ),
            )

        assertEquals(listOf("artist1", "artist2"), maps.map { it.artistId })
        assertEquals(listOf(0, 1), maps.map { it.position })
    }
}
