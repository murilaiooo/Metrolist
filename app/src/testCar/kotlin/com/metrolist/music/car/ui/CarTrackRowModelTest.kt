package com.metrolist.music.car.ui

import com.metrolist.music.car.library.CarSongSourceKind
import com.metrolist.music.car.library.CarUnifiedSong
import com.metrolist.music.car.ui.component.carTrackRowModel
import com.metrolist.music.car.ui.component.carTrackRowMinHeightDp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarTrackRowModelTest {
    private val song =
        CarUnifiedSong(
            id = "song-1",
            title = "Song One",
            artists = listOf("Artist A", "Artist B"),
            album = "Album One",
            explicit = true,
            primarySource = CarSongSourceKind.LEGACY_IMPORTED,
            sourceKinds = setOf(CarSongSourceKind.LEGACY_IMPORTED),
        )

    @Test
    fun artworkUsesCarLibraryThenLegacyThenRemotePrecedence() {
        assertEquals(
            "file:///car/high.jpg",
            carTrackRowModel(
                song = song,
                carLibraryArtwork = "file:///car/high.jpg",
                legacyArtwork = "file:///legacy/local.jpg",
                remoteArtwork = "https://example.com/remote.jpg",
            ).artworkUri,
        )
        assertEquals(
            "file:///legacy/local.jpg",
            carTrackRowModel(
                song = song,
                legacyArtwork = "file:///legacy/local.jpg",
                remoteArtwork = "https://example.com/remote.jpg",
            ).artworkUri,
        )
        assertEquals(
            "https://example.com/remote.jpg",
            carTrackRowModel(
                song = song,
                remoteArtwork = "https://example.com/remote.jpg",
            ).artworkUri,
        )
    }

    @Test
    fun modelCarriesCurrentBusyAndExplicitStateWithoutProvenanceLabel() {
        val model =
            carTrackRowModel(
                song = song,
                isCurrent = true,
                isBusy = true,
            )

        assertEquals("Song One", model.title)
        assertEquals("Artist A, Artist B", model.artist)
        assertTrue(model.explicit)
        assertTrue(model.isCurrent)
        assertTrue(model.isBusy)
        assertNull(model.provenanceLabel)
    }

    @Test
    fun emptyArtistFallsBackToAlbumThenUnknownArtist() {
        val albumOnly = song.copy(artists = emptyList(), album = "Album One")
        val unknown = song.copy(artists = emptyList(), album = null)

        assertEquals("Album One", carTrackRowModel(albumOnly).artist)
        assertEquals("Unknown artist", carTrackRowModel(unknown).artist)
    }

    @Test
    fun rowKeepsAtLeastEightyEightPhysicalPixelsWithSystemBarsAndSmallViewport() {
        assertEquals(
            88f,
            carTrackRowMinHeightDp(
                viewport = CarViewportMetrics.from(1024, 528),
                density = 1f,
            ),
            0.001f,
        )
        assertEquals(
            88f,
            carTrackRowMinHeightDp(
                viewport = CarViewportMetrics.from(800, 408),
                density = 1f,
            ),
            0.001f,
        )
    }

    @Test
    fun defaultStateIsNotCurrentOrBusy() {
        val model = carTrackRowModel(song)

        assertFalse(model.isCurrent)
        assertFalse(model.isBusy)
    }
}
