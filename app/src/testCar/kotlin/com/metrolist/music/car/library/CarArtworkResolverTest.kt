package com.metrolist.music.car.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CarArtworkResolverTest {
    private val resolver = CarArtworkResolver()

    @Test
    fun localCarLibraryArtworkWinsOverLegacyAndRemote() {
        assertEquals(
            "file:///library/artwork/high-quality.img",
            resolver.resolve(
                carLibraryArtwork = "file:///library/artwork/high-quality.img",
                legacyArtwork = "file:///legacy/song.img",
                remoteArtwork = "https://example.com/thumb.jpg",
            ),
        )
    }

    @Test
    fun fallsBackToLegacyThenRemoteThenPlaceholder() {
        assertEquals(
            "file:///legacy/song.img",
            resolver.resolve(
                carLibraryArtwork = null,
                legacyArtwork = "file:///legacy/song.img",
                remoteArtwork = "https://example.com/thumb.jpg",
            ),
        )
        assertEquals(
            "https://example.com/thumb.jpg",
            resolver.resolve(
                carLibraryArtwork = null,
                legacyArtwork = null,
                remoteArtwork = "https://example.com/thumb.jpg",
            ),
        )
        assertNull(
            resolver.resolve(
                carLibraryArtwork = null,
                legacyArtwork = null,
                remoteArtwork = null,
            ),
        )
    }
}