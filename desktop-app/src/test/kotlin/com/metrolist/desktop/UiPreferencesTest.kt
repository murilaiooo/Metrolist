package com.metrolist.desktop

import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class UiPreferencesTest {
    @Test
    fun defaultsEveryLibraryTabToList() {
        val path = createTempDirectory("metrolist-ui-prefs").resolve("ui.properties")
        val prefs = UiPreferences(path)

        LibraryTab.entries.forEach { tab ->
            assertEquals(PresentationMode.LIST, prefs.libraryMode(tab))
        }
    }

    @Test
    fun persistsIndependentModesAcrossReopen() {
        val path = createTempDirectory("metrolist-ui-prefs").resolve("ui.properties")
        UiPreferences(path).use { prefs ->
            prefs.setLibraryMode(LibraryTab.PLAYLISTS, PresentationMode.GRID)
            prefs.setLibraryMode(LibraryTab.ALBUMS, PresentationMode.GRID)
            prefs.setLibraryMode(LibraryTab.ARTISTS, PresentationMode.LIST)
        }

        UiPreferences(path).use { reopened ->
            assertEquals(PresentationMode.GRID, reopened.libraryMode(LibraryTab.PLAYLISTS))
            assertEquals(PresentationMode.LIST, reopened.libraryMode(LibraryTab.SONGS))
            assertEquals(PresentationMode.GRID, reopened.libraryMode(LibraryTab.ALBUMS))
            assertEquals(PresentationMode.LIST, reopened.libraryMode(LibraryTab.ARTISTS))
        }
    }

    @Test
    fun invalidStoredValueFallsBackToList() {
        val dir = createTempDirectory("metrolist-ui-prefs")
        val path = dir.resolve("ui.properties")
        Files.writeString(path, "library.playlists=banana\n")

        UiPreferences(path).use { prefs ->
            assertEquals(PresentationMode.LIST, prefs.libraryMode(LibraryTab.PLAYLISTS))
        }
    }

    @Test
    fun settingOneTabDoesNotOverwriteAnother() {
        val path = createTempDirectory("metrolist-ui-prefs").resolve("ui.properties")
        UiPreferences(path).use { prefs ->
            prefs.setLibraryMode(LibraryTab.PLAYLISTS, PresentationMode.GRID)
            prefs.setLibraryMode(LibraryTab.ARTISTS, PresentationMode.GRID)
            prefs.setLibraryMode(LibraryTab.PLAYLISTS, PresentationMode.LIST)

            assertEquals(PresentationMode.LIST, prefs.libraryMode(LibraryTab.PLAYLISTS))
            assertEquals(PresentationMode.GRID, prefs.libraryMode(LibraryTab.ARTISTS))
        }
    }
}
