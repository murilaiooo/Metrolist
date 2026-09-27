package com.metrolist.desktop.library

import java.nio.file.Files
import java.sql.DriverManager
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LibraryStoreTest {
    @Test
    fun createsVersionOneSchemaAndPersistsPlaylistOrderAcrossReopen() {
        val dir = createTempDirectory("metrolist-library-store")
        val db = dir.resolve("library.db")
        val playlist =
            LocalPlaylist(
                id = "local-1",
                name = "Road trip",
                createdAtMs = 10L,
                updatedAtMs = 10L,
            )
        val first = track("local-1", 0, "same-video", "First occurrence")
        val second = track("local-1", 1, "other-video", "Second")
        val duplicate = track("local-1", 2, "same-video", "Duplicate occurrence")

        LibraryStore(db).use { store ->
            assertEquals(1, store.schemaVersion())
            store.upsertPlaylist(playlist)
            store.replacePlaylistTracks(playlist.id, listOf(first, second, duplicate))
        }

        DriverManager.getConnection("jdbc:sqlite:${db.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA user_version").use { result ->
                    result.next()
                    assertEquals(1, result.getInt(1))
                }
            }
        }

        LibraryStore(db).use { reopened ->
            assertEquals(playlist, reopened.getPlaylist("local-1"))
            assertEquals(
                listOf("same-video", "other-video", "same-video"),
                reopened.playlistTracks("local-1").map { it.song.videoId },
            )
            assertEquals(listOf(0, 1, 2), reopened.playlistTracks("local-1").map { it.position })
        }

        Files.deleteIfExists(db)
    }

    @Test
    fun importRowsRoundTripInPositionOrder() {
        val dir = createTempDirectory("metrolist-import-store")
        val db = dir.resolve("library.db")
        val session =
            ImportSession(
                id = "session-1",
                name = "Imported",
                sourceText = "A - One\nB - Two",
                createdAtMs = 100L,
                updatedAtMs = 100L,
            )
        val rows =
            listOf(
                ImportRow("session-1", 1, "B - Two", "B", "Two"),
                ImportRow("session-1", 0, "A - One", "A", "One"),
            )

        LibraryStore(db).use { store ->
            store.upsertImportSession(session)
            store.upsertImportRows(rows)

            assertEquals(session, store.getImportSession("session-1"))
            assertEquals(listOf(0, 1), store.importRows("session-1").map { it.position })
            assertEquals("A - One", store.getImportRow("session-1", 0)?.original)
        }
    }

    @Test
    fun rememberedMatchUpsertReplacesExistingIdentity() {
        val dir = createTempDirectory("metrolist-remembered-store")
        val db = dir.resolve("library.db")

        LibraryStore(db).use { store ->
            store.upsertRememberedMatch(
                RememberedMatch(
                    identity = "artist|song",
                    videoId = "old",
                    title = "Song",
                    artist = "Artist",
                    duration = 200,
                    explicit = false,
                    updatedAtMs = 1L,
                ),
            )
            store.upsertRememberedMatch(
                RememberedMatch(
                    identity = "artist|song",
                    videoId = "new",
                    title = "Song",
                    artist = "Artist",
                    duration = 201,
                    explicit = true,
                    updatedAtMs = 2L,
                ),
            )

            assertEquals("new", store.getRememberedMatch("artist|song")?.videoId)
            assertEquals(true, store.getRememberedMatch("artist|song")?.explicit)
            assertNull(store.getRememberedMatch("missing"))
        }
    }

    private fun track(
        playlistId: String,
        position: Int,
        videoId: String,
        title: String,
    ) =
        LocalPlaylistTrack(
            playlistId = playlistId,
            position = position,
            song =
                LibrarySongSnapshot(
                    videoId = videoId,
                    title = title,
                    artists = listOf(LibraryArtistSnapshot("Artist", "artist-id")),
                    album = LibraryAlbumSnapshot("Album", "album-id"),
                    duration = 180,
                    musicVideoType = null,
                    thumbnail = "https://example.invalid/$videoId.jpg",
                    explicit = position == 0,
                    isEpisode = false,
                ),
            sourceImportLine = "$title - Artist",
        )
}
