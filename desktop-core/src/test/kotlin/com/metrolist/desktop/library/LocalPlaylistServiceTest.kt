package com.metrolist.desktop.library

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class LocalPlaylistServiceTest {
    @Test
    fun finalizesOnlyReadyRowsInOriginalOrderAndKeepsUnresolvedRows() {
        withStore { store ->
            val rows =
                listOf(
                    importRow(0, ImportRowState.READY, song("a", "First", "Shared Artist")),
                    importRow(1, ImportRowState.REVIEW, song("b", "Needs Review", "Other")),
                    importRow(2, ImportRowState.READY, song("c", "Third", "Shared Artist")),
                    importRow(3, ImportRowState.NOT_FOUND, null),
                )
            seedImport(store, rows)
            val service = LocalPlaylistService(store, idGenerator = { "playlist-1" }, nowMs = { 50L })

            val playlist = service.createFromImport("session", "Road Trip")

            assertEquals("playlist-1", playlist.id)
            assertEquals("Road Trip", playlist.name)
            assertEquals(listOf("a", "c"), service.songs(playlist.id).map { it.id })
            assertEquals(listOf(0, 1), store.playlistTracks(playlist.id).map { it.position })
            assertEquals(listOf("line-0", "line-2"), store.playlistTracks(playlist.id).map { it.sourceImportLine })
            assertEquals(4, store.importRows("session").size)
            assertEquals(ImportRowState.REVIEW, store.getImportRow("session", 1)?.state)
            assertEquals(ImportSessionStatus.FINALIZED, store.getImportSession("session")?.status)
        }
    }

    @Test
    fun renameSearchReorderRemoveAndDeleteOperateOnLocalPlaylist() {
        withStore { store ->
            seedImport(
                store,
                listOf(
                    importRow(0, ImportRowState.READY, song("a", "First", "Shared Artist")),
                    importRow(1, ImportRowState.READY, song("b", "Middle", "Other Artist")),
                    importRow(2, ImportRowState.READY, song("c", "Third", "Shared Artist")),
                ),
            )
            val service = LocalPlaylistService(store, idGenerator = { "playlist-1" }, nowMs = { 100L })
            val playlist = service.createFromImport("session", "Original")

            assertEquals("Renamed", service.rename(playlist.id, " Renamed ").name)
            assertEquals(listOf("a", "c"), service.searchSongs(playlist.id, "SHARED").map { it.id })

            service.moveTrack(playlist.id, fromPosition = 2, toPosition = 0)
            assertEquals(listOf("c", "a", "b"), service.songs(playlist.id).map { it.id })

            service.removeTrack(playlist.id, position = 1)
            assertEquals(listOf("c", "b"), service.songs(playlist.id).map { it.id })
            assertEquals(listOf(0, 1), store.playlistTracks(playlist.id).map { it.position })

            val beforeInvalidMove = service.songs(playlist.id).map { it.id }
            assertFailsWith<IllegalArgumentException> {
                service.moveTrack(playlist.id, fromPosition = 9, toPosition = 0)
            }
            assertEquals(beforeInvalidMove, service.songs(playlist.id).map { it.id })

            service.delete(playlist.id)
            assertNull(store.getPlaylist(playlist.id))
            assertEquals(emptyList(), store.playlistTracks(playlist.id))
        }
    }

    private fun withStore(block: (LibraryStore) -> Unit) {
        val db = createTempDirectory("metrolist-local-playlist").resolve("library.db")
        LibraryStore(db).use(block)
    }

    private fun seedImport(
        store: LibraryStore,
        rows: List<ImportRow>,
    ) {
        store.upsertImportSession(
            ImportSession(
                id = "session",
                name = "Import",
                sourceText = rows.joinToString("\n") { it.original },
                createdAtMs = 1L,
                updatedAtMs = 1L,
            ),
        )
        store.upsertImportRows(rows)
    }

    private fun importRow(
        position: Int,
        state: ImportRowState,
        selected: LibrarySongSnapshot?,
    ) =
        ImportRow(
            sessionId = "session",
            position = position,
            original = "line-$position",
            left = selected?.artists?.firstOrNull()?.name ?: "Unknown",
            right = selected?.title,
            state = state,
            selectedSong = selected,
            score = if (selected == null) 0.0 else 1.0,
        )

    private fun song(
        id: String,
        title: String,
        artist: String,
    ) =
        LibrarySongSnapshot(
            videoId = id,
            title = title,
            artists = listOf(LibraryArtistSnapshot(artist, "artist-$id")),
            album = LibraryAlbumSnapshot("Album", "album-$id"),
            duration = 200,
            musicVideoType = "MUSIC_VIDEO_TYPE_ATV",
            thumbnail = "https://example.invalid/$id.jpg",
            explicit = false,
            isEpisode = false,
        )
}
