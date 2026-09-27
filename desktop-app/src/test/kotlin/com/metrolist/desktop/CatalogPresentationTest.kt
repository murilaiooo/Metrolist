package com.metrolist.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CatalogPresentationTest {
    @Test
    fun gridIsAvailableOnlyForCollections() {
        assertTrue(supportsGrid(LibraryTab.PLAYLISTS))
        assertFalse(supportsGrid(LibraryTab.SONGS))
        assertTrue(supportsGrid(LibraryTab.ALBUMS))
        assertTrue(supportsGrid(LibraryTab.ARTISTS))
    }

    @Test
    fun songsRemainListEvenIfStoredPreferenceSaysGrid() {
        assertEquals(
            PresentationMode.LIST,
            effectivePresentationMode(LibraryTab.SONGS, PresentationMode.GRID),
        )
    }

    @Test
    fun collectionTabsRespectStoredMode() {
        assertEquals(
            PresentationMode.GRID,
            effectivePresentationMode(LibraryTab.PLAYLISTS, PresentationMode.GRID),
        )
        assertEquals(
            PresentationMode.LIST,
            effectivePresentationMode(LibraryTab.ALBUMS, PresentationMode.LIST),
        )
    }
}
