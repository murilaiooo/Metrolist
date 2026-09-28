package com.metrolist.music.car.diagnostics

import com.metrolist.music.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CarDiagnosticsSnapshotTest {
    @Test
    fun snapshotUsesEmbeddedCarVersionAndContainsNoAuthSecrets() {
        val snapshot =
            CarDiagnosticsSnapshot(
                versionName = BuildConfig.VERSION_NAME,
                deviceModel = "test-device",
                apiLevel = 35,
                viewportWidthPx = 1280,
                viewportHeightPx = 720,
                density = 1f,
                freeStorageBytes = 8L * 1024 * 1024 * 1024,
                totalStorageBytes = 16L * 1024 * 1024 * 1024,
                roomDownloadedCount = 12,
                media3CachedKeyCount = 12,
                                importState = "Idle",
                carLibraryState = "MetroList Windows 0.3.0 · 10 tracks · 2 playlists · rev abc",
                logicalCatalogCount = 12,
                carLibraryPrimaryCount = 10,
                carLocalPrimaryCount = 1,
                legacyPrimaryCount = 1,
                lastInputObservation = "keyCode=85, scanCode=0, source=257",
            )

        assertEquals(BuildConfig.VERSION_NAME, snapshot.versionName)
        val rendered = snapshot.lines().joinToString("\n").lowercase()
        assertFalse(rendered.contains("cookie"))
        assertFalse(rendered.contains("token"))
        assertFalse(rendered.contains("authorization"))
    }
}