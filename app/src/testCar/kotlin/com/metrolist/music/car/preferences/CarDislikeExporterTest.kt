package com.metrolist.music.car.preferences

import org.junit.Assert.assertTrue
import org.junit.Test

class CarDislikeExporterTest {
    @Test
    fun textExportUsesHumanReadableArtistAndTitleLines() {
        val records =
            listOf(
                CarDislikeRecord(
                    songId = "one",
                    title = "Song One",
                    artists = listOf("Artist A", "Artist B"),
                    dislikedAtEpochMillis = 1_000L,
                ),
                CarDislikeRecord(
                    songId = "two",
                    title = "Song Two",
                    artists = emptyList(),
                    dislikedAtEpochMillis = 2_000L,
                    deletedAtEpochMillis = 3_000L,
                ),
            )

        val text = CarDislikeExporter.renderText(records)

        assertTrue(text.contains("Artist A, Artist B — Song One"))
        assertTrue(text.contains("Unknown artist — Song Two"))
    }

    @Test
    fun jsonExportKeepsIdMetadataAndDeletionState() {
        val records =
            listOf(
                CarDislikeRecord(
                    songId = "song-1",
                    title = "Song One",
                    artists = listOf("Artist A"),
                    album = "Album One",
                    dislikedAtEpochMillis = 1_000L,
                    sourceKinds = listOf("LEGACY_IMPORTED"),
                    deletedAtEpochMillis = 5_000L,
                ),
            )

        val json = CarDislikeExporter.renderJson(records)

        assertTrue(json.contains("\"songId\": \"song-1\""))
        assertTrue(json.contains("\"title\": \"Song One\""))
        assertTrue(json.contains("\"deletedAtEpochMillis\": 5000"))
        assertTrue(json.contains("\"LEGACY_IMPORTED\""))
    }
}
