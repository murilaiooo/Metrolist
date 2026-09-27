package com.metrolist.desktop

import com.metrolist.desktop.library.ImportRow
import com.metrolist.desktop.library.ImportRowState
import com.metrolist.desktop.library.ImportSession
import com.metrolist.desktop.library.LocalPlaylist
import com.metrolist.desktop.library.SyncProgress
import com.metrolist.desktop.library.SyncResult
import com.metrolist.desktop.library.importer.ImportProgress
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PlaylistWorkspaceScreen {
    CLOSED,
    IMPORT_EDIT,
    IMPORT_REVIEW,
    LOCAL_PLAYLIST,
}

enum class ImportDestination {
    LOCAL,
    YOUTUBE_MUSIC,
}

enum class ImportRowFilter {
    ALL,
    READY,
    REVIEW,
    NOT_FOUND,
}

data class ImportSummary(
    val total: Int,
    val ready: Int,
    val review: Int,
    val notFound: Int,
    val failed: Int,
)

data class PlaylistWorkspaceState(
    val screen: PlaylistWorkspaceScreen = PlaylistWorkspaceScreen.CLOSED,
    val playlistName: String = "",
    val sourceText: String = "",
    val destination: ImportDestination = ImportDestination.LOCAL,
    val sessionId: String? = null,
    val rows: List<ImportRow> = emptyList(),
    val rowFilter: ImportRowFilter = ImportRowFilter.ALL,
    val importProgress: ImportProgress = ImportProgress(),
    val matching: Boolean = false,
    val busy: Boolean = false,
    val syncProgress: SyncProgress? = null,
    val error: String? = null,
    val localPlaylists: List<LocalPlaylist> = emptyList(),
    val selectedLocalPlaylist: LocalPlaylist? = null,
    val localPlaylistSongs: List<SongItem> = emptyList(),
    val localSearchQuery: String = "",
)

interface PlaylistWorkspaceGateway {
    val importProgress: StateFlow<ImportProgress>
    fun localPlaylists(): List<LocalPlaylist>
    fun localPlaylistSongs(playlistId: String): List<SongItem>
    fun createImportSession(name: String, sourceText: String): ImportSession
    fun importRows(sessionId: String): List<ImportRow>
    suspend fun matchImport(sessionId: String)
    suspend fun retryImportRow(sessionId: String, position: Int, query: String? = null)
    fun selectImportCandidate(sessionId: String, position: Int, videoId: String): ImportRow
    fun createLocalPlaylistFromImport(sessionId: String, name: String): LocalPlaylist
    suspend fun syncLocalPlaylist(
        playlistId: String,
        onProgress: (SyncProgress) -> Unit = {},
    ): SyncResult
    fun renameLocalPlaylist(playlistId: String, name: String): LocalPlaylist
    fun moveLocalPlaylistTrack(playlistId: String, fromPosition: Int, toPosition: Int)
    fun removeLocalPlaylistTrack(playlistId: String, position: Int)
    fun deleteLocalPlaylist(playlistId: String)
}

fun summarizeImportRows(rows: List<ImportRow>): ImportSummary =
    ImportSummary(
        total = rows.size,
        ready = rows.count { it.state == ImportRowState.READY },
        review = rows.count { it.state == ImportRowState.REVIEW },
        notFound = rows.count { it.state == ImportRowState.NOT_FOUND },
        failed = rows.count { it.state == ImportRowState.FAILED },
    )

fun filterImportRows(
    rows: List<ImportRow>,
    filter: ImportRowFilter,
): List<ImportRow> =
    when (filter) {
        ImportRowFilter.ALL -> rows
        ImportRowFilter.READY -> rows.filter { it.state == ImportRowState.READY }
        ImportRowFilter.REVIEW -> rows.filter { it.state == ImportRowState.REVIEW }
        ImportRowFilter.NOT_FOUND ->
            rows.filter { it.state == ImportRowState.NOT_FOUND || it.state == ImportRowState.FAILED }
    }

class PlaylistWorkspaceController(
    private val gateway: PlaylistWorkspaceGateway,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : AutoCloseable {
    private val mutableState = MutableStateFlow(PlaylistWorkspaceState())
    val state: StateFlow<PlaylistWorkspaceState> = mutableState.asStateFlow()

    init {
        scope.launch {
            gateway.importProgress.collect { progress ->
                mutableState.value = mutableState.value.copy(importProgress = progress)
            }
        }
    }

    fun refreshLocalPlaylists() {
        runCatching(gateway::localPlaylists)
            .onSuccess { playlists ->
                mutableState.value = mutableState.value.copy(localPlaylists = playlists, error = null)
            }.onFailure(::setError)
    }

    fun openImport() {
        mutableState.value =
            mutableState.value.copy(
                screen = PlaylistWorkspaceScreen.IMPORT_EDIT,
                playlistName = "",
                sourceText = "",
                destination = ImportDestination.LOCAL,
                sessionId = null,
                rows = emptyList(),
                rowFilter = ImportRowFilter.ALL,
                importProgress = ImportProgress(),
                matching = false,
                busy = false,
                syncProgress = null,
                error = null,
                selectedLocalPlaylist = null,
                localPlaylistSongs = emptyList(),
                localSearchQuery = "",
            )
    }

    fun closeWorkspace() {
        mutableState.value =
            mutableState.value.copy(
                screen = PlaylistWorkspaceScreen.CLOSED,
                selectedLocalPlaylist = null,
                localPlaylistSongs = emptyList(),
                localSearchQuery = "",
                error = null,
            )
    }

    fun setPlaylistName(value: String) {
        mutableState.value = mutableState.value.copy(playlistName = value)
    }

    fun setSourceText(value: String) {
        mutableState.value = mutableState.value.copy(sourceText = value)
    }

    fun setDestination(value: ImportDestination) {
        mutableState.value = mutableState.value.copy(destination = value)
    }

    fun setRowFilter(value: ImportRowFilter) {
        mutableState.value = mutableState.value.copy(rowFilter = value)
    }

    fun startImport() {
        val snapshot = mutableState.value
        val name = snapshot.playlistName.trim()
        val sourceText = snapshot.sourceText
        if (name.isEmpty()) {
            setError(IllegalArgumentException("Playlist name is required"))
            return
        }
        if (sourceText.isBlank()) {
            setError(IllegalArgumentException("Paste a song list or choose a TXT file first"))
            return
        }

        scope.launch {
            mutableState.value =
                mutableState.value.copy(
                    matching = true,
                    busy = true,
                    error = null,
                    screen = PlaylistWorkspaceScreen.IMPORT_REVIEW,
                )
            runCatching {
                val session = gateway.createImportSession(name, sourceText)
                mutableState.value = mutableState.value.copy(sessionId = session.id)
                gateway.matchImport(session.id)
                refreshRows(session.id)
            }.onFailure(::setError)
            mutableState.value =
                mutableState.value.copy(
                    matching = false,
                    busy = false,
                    importProgress = gateway.importProgress.value,
                )
        }
    }

    fun retryRow(
        position: Int,
        query: String? = null,
    ) {
        val sessionId = mutableState.value.sessionId ?: return
        scope.launch {
            mutableState.value = mutableState.value.copy(busy = true, error = null)
            runCatching {
                gateway.retryImportRow(sessionId, position, query)
                refreshRows(sessionId)
            }.onFailure(::setError)
            mutableState.value = mutableState.value.copy(busy = false)
        }
    }

    fun selectCandidate(
        position: Int,
        videoId: String,
    ) {
        val sessionId = mutableState.value.sessionId ?: return
        runCatching { gateway.selectImportCandidate(sessionId, position, videoId) }
            .onSuccess { refreshRows(sessionId) }
            .onFailure(::setError)
    }

    fun finalizeImport() {
        val snapshot = mutableState.value
        val sessionId = snapshot.sessionId ?: return
        val name = snapshot.playlistName.trim()
        scope.launch {
            mutableState.value = mutableState.value.copy(busy = true, syncProgress = null, error = null)
            runCatching {
                gateway.createLocalPlaylistFromImport(sessionId, name)
            }.onSuccess { local ->
                val playlists = gateway.localPlaylists()
                val selected = playlists.firstOrNull { it.id == local.id } ?: local
                mutableState.value =
                    mutableState.value.copy(
                        screen = PlaylistWorkspaceScreen.LOCAL_PLAYLIST,
                        localPlaylists = playlists,
                        selectedLocalPlaylist = selected,
                        localPlaylistSongs = gateway.localPlaylistSongs(local.id),
                        localSearchQuery = "",
                        error = null,
                    )

                if (snapshot.destination == ImportDestination.YOUTUBE_MUSIC) {
                    runCatching {
                        gateway.syncLocalPlaylist(local.id) { progress ->
                            mutableState.value = mutableState.value.copy(syncProgress = progress)
                        }
                    }.onSuccess {
                        reloadSelectedPlaylist(local.id)
                    }.onFailure { syncError ->
                        reloadSelectedPlaylist(local.id)
                        setError(syncError)
                    }
                }
            }.onFailure(::setError)
            mutableState.value = mutableState.value.copy(busy = false)
        }
    }

    fun openLocalPlaylist(playlistId: String) {
        runCatching {
            val playlist = gateway.localPlaylists().first { it.id == playlistId }
            val songs = gateway.localPlaylistSongs(playlistId)
            playlist to songs
        }.onSuccess { (playlist, songs) ->
            mutableState.value =
                mutableState.value.copy(
                    screen = PlaylistWorkspaceScreen.LOCAL_PLAYLIST,
                    localPlaylists = gateway.localPlaylists(),
                    selectedLocalPlaylist = playlist,
                    localPlaylistSongs = songs,
                    localSearchQuery = "",
                    error = null,
                )
        }.onFailure(::setError)
    }

    fun syncSelectedLocalPlaylist() {
        val playlistId = mutableState.value.selectedLocalPlaylist?.id ?: return
        scope.launch {
            mutableState.value = mutableState.value.copy(busy = true, syncProgress = null, error = null)
            runCatching {
                gateway.syncLocalPlaylist(playlistId) { progress ->
                    mutableState.value = mutableState.value.copy(syncProgress = progress)
                }
                reloadSelectedPlaylist(playlistId)
            }.onFailure(::setError)
            mutableState.value = mutableState.value.copy(busy = false)
        }
    }

    fun setLocalSearchQuery(value: String) {
        mutableState.value = mutableState.value.copy(localSearchQuery = value)
    }

    fun filteredLocalSongs(): List<SongItem> {
        val snapshot = mutableState.value
        val query = snapshot.localSearchQuery.trim()
        if (query.isEmpty()) return snapshot.localPlaylistSongs
        return snapshot.localPlaylistSongs.filter { song ->
            sequenceOf(
                song.title,
                song.artists.joinToString(" ") { it.name },
                song.album?.name.orEmpty(),
            ).any { it.contains(query, ignoreCase = true) }
        }
    }

    fun renameLocalPlaylist(name: String) {
        val playlistId = mutableState.value.selectedLocalPlaylist?.id ?: return
        runCatching { gateway.renameLocalPlaylist(playlistId, name) }
            .onSuccess { reloadSelectedPlaylist(playlistId) }
            .onFailure(::setError)
    }

    fun moveLocalPlaylistTrack(fromPosition: Int, toPosition: Int) {
        val playlistId = mutableState.value.selectedLocalPlaylist?.id ?: return
        runCatching { gateway.moveLocalPlaylistTrack(playlistId, fromPosition, toPosition) }
            .onSuccess { reloadSelectedPlaylist(playlistId) }
            .onFailure(::setError)
    }

    fun removeLocalPlaylistTrack(position: Int) {
        val playlistId = mutableState.value.selectedLocalPlaylist?.id ?: return
        runCatching { gateway.removeLocalPlaylistTrack(playlistId, position) }
            .onSuccess { reloadSelectedPlaylist(playlistId) }
            .onFailure(::setError)
    }

    fun deleteSelectedLocalPlaylist() {
        val playlistId = mutableState.value.selectedLocalPlaylist?.id ?: return
        runCatching { gateway.deleteLocalPlaylist(playlistId) }
            .onSuccess {
                mutableState.value =
                    mutableState.value.copy(
                        screen = PlaylistWorkspaceScreen.CLOSED,
                        localPlaylists = gateway.localPlaylists(),
                        selectedLocalPlaylist = null,
                        localPlaylistSongs = emptyList(),
                        localSearchQuery = "",
                        error = null,
                    )
            }.onFailure(::setError)
    }

    private fun refreshRows(sessionId: String) {
        mutableState.value =
            mutableState.value.copy(
                rows = gateway.importRows(sessionId),
                importProgress = gateway.importProgress.value,
                error = null,
            )
    }

    private fun reloadSelectedPlaylist(playlistId: String) {
        val playlists = gateway.localPlaylists()
        mutableState.value =
            mutableState.value.copy(
                localPlaylists = playlists,
                selectedLocalPlaylist = playlists.firstOrNull { it.id == playlistId },
                localPlaylistSongs = gateway.localPlaylistSongs(playlistId),
                error = null,
            )
    }

    private fun setError(error: Throwable) {
        mutableState.value =
            mutableState.value.copy(
                error = error.message ?: error::class.simpleName ?: "Playlist operation failed",
            )
    }

    override fun close() {
        scope.cancel()
    }
}
