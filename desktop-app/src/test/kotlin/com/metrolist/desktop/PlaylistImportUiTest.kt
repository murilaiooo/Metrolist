package com.metrolist.desktop

import com.metrolist.desktop.library.ImportCandidateSnapshot
import com.metrolist.desktop.library.ImportRow
import com.metrolist.desktop.library.ImportRowState
import com.metrolist.desktop.library.ImportSession
import com.metrolist.desktop.library.LibraryArtistSnapshot
import com.metrolist.desktop.library.LibrarySongSnapshot
import com.metrolist.desktop.library.LocalPlaylist
import com.metrolist.desktop.library.PlaylistSyncState
import com.metrolist.desktop.library.SyncProgress
import com.metrolist.desktop.library.SyncResult
import com.metrolist.desktop.library.importer.ImportProgress
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaylistImportUiTest {
    @Test
    fun summaryAndFiltersExposeReadyReviewAndNotFoundWithoutHidingFailures() {
        val rows =
            listOf(
                row(0, ImportRowState.READY),
                row(1, ImportRowState.REVIEW),
                row(2, ImportRowState.NOT_FOUND),
                row(3, ImportRowState.FAILED),
            )

        assertEquals(ImportSummary(4, 1, 1, 1, 1), summarizeImportRows(rows))
        assertEquals(listOf(0, 1, 2, 3), filterImportRows(rows, ImportRowFilter.ALL).map { it.position })
        assertEquals(listOf(0), filterImportRows(rows, ImportRowFilter.READY).map { it.position })
        assertEquals(listOf(1), filterImportRows(rows, ImportRowFilter.REVIEW).map { it.position })
        assertEquals(listOf(2, 3), filterImportRows(rows, ImportRowFilter.NOT_FOUND).map { it.position })
    }

    @Test
    fun startImportShowsReviewAndTracksMatchingProgress() = runBlocking {
        val gateway = FakePlaylistWorkspaceGateway()
        gateway.rows = listOf(row(0, ImportRowState.PENDING), row(1, ImportRowState.PENDING))
        gateway.onMatch = {
            gateway.mutableProgress.value = ImportProgress(total = 2, completed = 1, ready = 1, running = 1)
            gateway.rows = listOf(row(0, ImportRowState.READY), row(1, ImportRowState.REVIEW))
            gateway.mutableProgress.value = ImportProgress(total = 2, completed = 2, ready = 1, review = 1)
        }
        val controller = PlaylistWorkspaceController(gateway)

        controller.openImport()
        controller.setPlaylistName("Road Trip")
        controller.setSourceText("Artist - Song\nArtist 2 - Song 2")
        controller.startImport()
        waitUntil { !controller.state.value.matching && controller.state.value.sessionId != null }

        assertEquals(PlaylistWorkspaceScreen.IMPORT_REVIEW, controller.state.value.screen)
        assertEquals(2, controller.state.value.importProgress.total)
        assertEquals(1, controller.state.value.importProgress.ready)
        assertEquals(1, controller.state.value.importProgress.review)
        assertEquals(listOf(ImportRowState.READY, ImportRowState.REVIEW), controller.state.value.rows.map { it.state })
        controller.close()
    }

    @Test
    fun localOnlyDestinationNeverCallsYouTubeSync() = runBlocking {
        val gateway = FakePlaylistWorkspaceGateway()
        gateway.rows = listOf(row(0, ImportRowState.READY))
        val controller = PlaylistWorkspaceController(gateway)
        controller.openImport()
        controller.setPlaylistName("One-off")
        controller.setSourceText("Artist - Song")
        controller.startImport()
        waitUntil { !controller.state.value.matching }

        controller.setDestination(ImportDestination.LOCAL)
        controller.finalizeImport()
        waitUntil { !controller.state.value.busy && controller.state.value.screen == PlaylistWorkspaceScreen.LOCAL_PLAYLIST }

        assertTrue(gateway.calls.contains("create-local:session-1:One-off"))
        assertFalse(gateway.calls.any { it.startsWith("sync:") })
        assertEquals(null, controller.state.value.selectedLocalPlaylist?.youtubePlaylistId)
        controller.close()
    }

    @Test
    fun youtubeDestinationCreatesLocalMirrorBeforeExplicitSync() = runBlocking {
        val gateway = FakePlaylistWorkspaceGateway()
        gateway.rows = listOf(row(0, ImportRowState.READY))
        val controller = PlaylistWorkspaceController(gateway)
        controller.openImport()
        controller.setPlaylistName("Road Trip")
        controller.setSourceText("Artist - Song")
        controller.startImport()
        waitUntil { !controller.state.value.matching }
        controller.setDestination(ImportDestination.YOUTUBE_MUSIC)

        controller.finalizeImport()
        waitUntil { !controller.state.value.busy && gateway.calls.contains("sync:local-1") }

        val createIndex = gateway.calls.indexOf("create-local:session-1:Road Trip")
        val syncIndex = gateway.calls.indexOf("sync:local-1")
        assertTrue(createIndex >= 0)
        assertTrue(syncIndex > createIndex)
        assertEquals("remote-1", controller.state.value.selectedLocalPlaylist?.youtubePlaylistId)
        assertEquals(PlaylistWorkspaceScreen.LOCAL_PLAYLIST, controller.state.value.screen)
        controller.close()
    }

    @Test
    fun youtubeSyncFailureStillOpensTheCreatedLocalPlaylist() = runBlocking {
        val gateway = FakePlaylistWorkspaceGateway()
        gateway.rows = listOf(row(0, ImportRowState.READY))
        gateway.syncFailure = IllegalStateException("YouTube unavailable")
        val controller = PlaylistWorkspaceController(gateway)
        controller.openImport()
        controller.setPlaylistName("Road Trip")
        controller.setSourceText("Artist - Song")
        controller.startImport()
        waitUntil { !controller.state.value.matching }
        controller.setDestination(ImportDestination.YOUTUBE_MUSIC)

        controller.finalizeImport()
        waitUntil { !controller.state.value.busy }

        assertEquals(1, gateway.calls.count { it.startsWith("create-local:") })
        assertEquals(PlaylistWorkspaceScreen.LOCAL_PLAYLIST, controller.state.value.screen)
        assertEquals("local-1", controller.state.value.selectedLocalPlaylist?.id)
        assertEquals("YouTube unavailable", controller.state.value.error)
        controller.close()
    }

    @Test
    fun manualCandidateSelectionRefreshesRows() = runBlocking {
        val candidate = snapshot("chosen", "Song")
        val gateway = FakePlaylistWorkspaceGateway()
        gateway.rows =
            listOf(
                row(0, ImportRowState.REVIEW).copy(
                    candidates = listOf(ImportCandidateSnapshot(candidate, 0.84, "ORIGINAL")),
                ),
            )
        val controller = PlaylistWorkspaceController(gateway)
        controller.openImport()
        controller.setPlaylistName("Road Trip")
        controller.setSourceText("Artist - Song")
        controller.startImport()
        waitUntil { !controller.state.value.matching }

        controller.selectCandidate(0, "chosen")

        assertEquals(ImportRowState.READY, controller.state.value.rows.single().state)
        assertEquals("chosen", controller.state.value.rows.single().selectedSong?.videoId)
        controller.close()
    }

    private fun row(position: Int, state: ImportRowState) =
        ImportRow(
            sessionId = "session-1",
            position = position,
            original = "Artist - Song $position",
            left = "Artist",
            right = "Song $position",
            state = state,
            selectedSong = if (state == ImportRowState.READY) snapshot("id-$position", "Song $position") else null,
        )

    private fun snapshot(id: String, title: String) =
        LibrarySongSnapshot(
            videoId = id,
            title = title,
            artists = listOf(LibraryArtistSnapshot("Artist", "artist-id")),
            thumbnail = "",
        )

    private suspend fun waitUntil(
        timeoutMs: Long = 2_000,
        condition: () -> Boolean,
    ) {
        val started = System.currentTimeMillis()
        while (!condition()) {
            check(System.currentTimeMillis() - started < timeoutMs) { "Timed out waiting for controller state" }
            delay(10)
        }
    }
}

internal class FakePlaylistWorkspaceGateway : PlaylistWorkspaceGateway {
    val mutableProgress = MutableStateFlow(ImportProgress())
    override val importProgress: StateFlow<ImportProgress> = mutableProgress
    val calls = mutableListOf<String>()
    var rows: List<ImportRow> = emptyList()
    var playlists = mutableListOf<LocalPlaylist>()
    var songs = mutableMapOf<String, List<SongItem>>()
    var onMatch: suspend () -> Unit = {}
    var syncFailure: Throwable? = null

    override fun localPlaylists(): List<LocalPlaylist> = playlists.toList()

    override fun localPlaylistSongs(playlistId: String): List<SongItem> = songs[playlistId].orEmpty()

    override fun createImportSession(name: String, sourceText: String): ImportSession {
        calls += "create-session:$name"
        return ImportSession("session-1", name, sourceText, 1L, 1L)
    }

    override fun importRows(sessionId: String): List<ImportRow> = rows

    override suspend fun matchImport(sessionId: String) {
        calls += "match:$sessionId"
        onMatch()
    }

    override suspend fun retryImportRow(sessionId: String, position: Int, query: String?) = Unit

    override fun selectImportCandidate(sessionId: String, position: Int, videoId: String): ImportRow {
        val old = rows.single { it.position == position }
        val candidate = old.candidates.single { it.song.videoId == videoId }
        val updated = old.copy(state = ImportRowState.READY, selectedSong = candidate.song, score = candidate.score)
        rows = rows.map { if (it.position == position) updated else it }
        return updated
    }

    override fun createLocalPlaylistFromImport(sessionId: String, name: String): LocalPlaylist {
        calls += "create-local:$sessionId:$name"
        val playlist = LocalPlaylist("local-1", name, 1L, 1L)
        playlists += playlist
        songs[playlist.id] = rows.filter { it.state == ImportRowState.READY }.mapNotNull { it.selectedSong?.toSongItemForTest() }
        return playlist
    }

    override suspend fun syncLocalPlaylist(
        playlistId: String,
        onProgress: (SyncProgress) -> Unit,
    ): SyncResult {
        calls += "sync:$playlistId"
        syncFailure?.let { throw it }
        onProgress(SyncProgress(1, 0, 0, 0))
        onProgress(SyncProgress(1, 1, 1, 0, "id-0"))
        val existing = playlists.single { it.id == playlistId }
        val linked = existing.copy(youtubePlaylistId = "remote-1", syncState = PlaylistSyncState.SYNCED)
        playlists = playlists.mapTo(mutableListOf()) { if (it.id == playlistId) linked else it }
        return SyncResult("remote-1", 1, 1, 0, emptyList())
    }

    override fun renameLocalPlaylist(playlistId: String, name: String): LocalPlaylist {
        val existing = playlists.single { it.id == playlistId }
        val updated = existing.copy(name = name)
        playlists = playlists.mapTo(mutableListOf()) { if (it.id == playlistId) updated else it }
        return updated
    }

    override fun moveLocalPlaylistTrack(playlistId: String, fromPosition: Int, toPosition: Int) = Unit
    override fun removeLocalPlaylistTrack(playlistId: String, position: Int) = Unit
    override fun deleteLocalPlaylist(playlistId: String) { playlists.removeAll { it.id == playlistId } }
}

private fun LibrarySongSnapshot.toSongItemForTest() =
    SongItem(
        id = videoId,
        title = title,
        artists = emptyList(),
        thumbnail = thumbnail,
    )
