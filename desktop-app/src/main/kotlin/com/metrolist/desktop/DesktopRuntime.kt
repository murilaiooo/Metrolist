package com.metrolist.desktop

import com.metrolist.desktop.artwork.ArtworkCache
import com.metrolist.desktop.auth.AdvancedSessionParser
import com.metrolist.desktop.auth.AuthHelperLauncher
import com.metrolist.desktop.auth.AuthSessionValidator
import com.metrolist.desktop.auth.DpapiSessionStore
import com.metrolist.desktop.library.ImportRow
import com.metrolist.desktop.library.ImportSession
import com.metrolist.desktop.library.LibraryStore
import com.metrolist.desktop.library.LocalPlaylist
import com.metrolist.desktop.library.LocalPlaylistService
import com.metrolist.desktop.library.MetroListYouTubePlaylistGateway
import com.metrolist.desktop.library.PlaylistSyncService
import com.metrolist.desktop.library.SyncProgress
import com.metrolist.desktop.library.SyncResult
import com.metrolist.desktop.library.importer.ImportProgress
import com.metrolist.desktop.library.importer.PlaylistImportMatcher
import com.metrolist.desktop.library.importer.PlaylistTextParser
import com.metrolist.desktop.library.importer.YouTubeSongSearchGateway
import com.metrolist.desktop.media.ResolvedMediaFetcher
import com.metrolist.desktop.offline.MetroListOfflineWorker
import com.metrolist.desktop.offline.OfflineRecord
import com.metrolist.desktop.offline.OfflineStore
import com.metrolist.desktop.offline.PersistentOfflineQueue
import com.metrolist.desktop.player.LibMpvBackend
import com.metrolist.desktop.player.MpvPlayer
import com.metrolist.desktop.player.PlayerSourceSelector
import com.metrolist.desktop.player.PlayerState
import com.metrolist.desktop.portable.OfflineBackupService
import com.metrolist.desktop.portable.PortableExportEstimate
import com.metrolist.desktop.portable.PortableExportProgress
import com.metrolist.desktop.stream.DesktopInnerTubeXPlayer
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AccountState(
    val loading: Boolean = true,
    val signedIn: Boolean = false,
    val name: String? = null,
    val email: String? = null,
    val error: String? = null,
)

internal fun advancedSessionErrorMessage(error: Throwable): String {
    val message = error.message.orEmpty()

    return when {
        message.contains("missing SAPISID", ignoreCase = true) ->
            "Advanced Session is missing a valid SAPISID cookie. Paste the complete session block."

        message.contains("missing visitor data", ignoreCase = true) ->
            "Advanced Session is missing visitor data. Paste the complete session block."

        else ->
            "Advanced Session could not be validated with YouTube Music. Generate a fresh session and try again."
    }
}

internal fun deleteOfflineMediaFiles(
    mediaDir: Path,
    videoId: String,
) {
    val destination = mediaDir.resolve(videoId + ".media")
    Files.deleteIfExists(destination)
    Files.deleteIfExists(
        destination.resolveSibling(destination.fileName.toString() + ".part"),
    )
}

class DesktopRuntime private constructor(
    private val projectRoot: Path,
    private val appData: Path,
    private val sessionStore: DpapiSessionStore,
    private val offlineStore: OfflineStore,
    private val artworkCache: ArtworkCache,
    private val resolver: DesktopInnerTubeXPlayer,
    private val queue: PersistentOfflineQueue,
    private val player: MpvPlayer?,
    private val libraryStore: LibraryStore,
    private val importMatcher: PlaylistImportMatcher,
    private val localPlaylistService: LocalPlaylistService,
    private val playlistSyncService: PlaylistSyncService,
    private val uiPreferences: UiPreferences,
) : AutoCloseable, PlaylistWorkspaceGateway {
    private val mutableAccount = MutableStateFlow(AccountState())
    val account: StateFlow<AccountState> = mutableAccount.asStateFlow()

    val downloads: StateFlow<List<OfflineRecord>> = queue.downloads
    val playerState: StateFlow<PlayerState>? = player?.state
    override val importProgress: StateFlow<ImportProgress> = importMatcher.progress

    fun libraryPresentationMode(tab: LibraryTab): PresentationMode =
        effectivePresentationMode(tab, uiPreferences.libraryMode(tab))

    fun setLibraryPresentationMode(
        tab: LibraryTab,
        mode: PresentationMode,
    ) {
        uiPreferences.setLibraryMode(tab, effectivePresentationMode(tab, mode))
    }

    suspend fun initializeSession() {
        mutableAccount.value =
            mutableAccount.value.copy(
                loading = true,
                error = null,
            )

        runCatching {
            sessionStore.load()?.let { saved ->
                AuthSessionValidator().validate(saved)
            }
        }.onSuccess { validated ->
            if (validated == null) {
                mutableAccount.value = AccountState(loading = false)
            } else {
                queue.resumePending()
                mutableAccount.value =
                    AccountState(
                        loading = false,
                        signedIn = true,
                        name = validated.session.accountName,
                        email = validated.session.accountEmail,
                    )
            }
        }.onFailure { error ->
            mutableAccount.value =
                AccountState(
                    loading = false,
                    signedIn = false,
                    error = error.message ?: error::class.simpleName,
                )
        }
    }

    suspend fun loginInteractive() {
        mutableAccount.value =
            mutableAccount.value.copy(
                loading = true,
                error = null,
            )

        try {
            val helperCommand = authHelperCommand(projectRoot)
            val session =
                AuthHelperLauncher(
                    command = helperCommand,
                    workingDirectory = projectRoot,
                ).login()

            acceptValidatedSession(
                AuthSessionValidator().validate(session),
            )
        } catch (error: Throwable) {
            mutableAccount.value =
                AccountState(
                    loading = false,
                    signedIn = false,
                    error = error.message ?: error::class.simpleName,
                )
            throw error
        }
    }

    suspend fun loginAdvanced(sessionText: String) {
        mutableAccount.value =
            mutableAccount.value.copy(
                loading = true,
                error = null,
            )

        try {
            val candidate = AdvancedSessionParser.parse(sessionText)
            acceptValidatedSession(
                AuthSessionValidator().validate(candidate),
            )
        } catch (error: Throwable) {
            val safeMessage = advancedSessionErrorMessage(error)
            mutableAccount.value =
                AccountState(
                    loading = false,
                    signedIn = false,
                    error = safeMessage,
                )
            throw IllegalStateException(safeMessage)
        }
    }

    private fun acceptValidatedSession(
        validated: com.metrolist.desktop.auth.ValidatedAuthSession,
    ) {
        sessionStore.save(validated.session)
        queue.resumePending()
        mutableAccount.value =
            AccountState(
                loading = false,
                signedIn = true,
                name = validated.session.accountName,
                email = validated.session.accountEmail,
            )
    }

    fun logout() {
        sessionStore.clear()
        YouTube.cookie = ""
        YouTube.visitorData = ""
        YouTube.dataSyncId = ""
        YouTube.authUser = "0"
        mutableAccount.value = AccountState(loading = false)
    }

    override fun localPlaylists(): List<LocalPlaylist> =
        localPlaylistService.playlists()

    override fun localPlaylistSongs(playlistId: String): List<SongItem> =
        localPlaylistService.songs(playlistId)

    override fun createImportSession(
        name: String,
        sourceText: String,
    ): ImportSession {
        val parsed = PlaylistTextParser.parse(sourceText)
        val timestamp = System.currentTimeMillis()
        val session =
            ImportSession(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                sourceText = sourceText,
                createdAtMs = timestamp,
                updatedAtMs = timestamp,
            )
        val rows =
            parsed.map { line ->
                ImportRow(
                    sessionId = session.id,
                    position = line.position,
                    original = line.original,
                    left = line.left,
                    right = line.right,
                )
            }
        libraryStore.upsertImportSession(session)
        libraryStore.upsertImportRows(rows)
        return session
    }

    override fun importRows(sessionId: String): List<ImportRow> =
        libraryStore.importRows(sessionId)

    override suspend fun matchImport(sessionId: String) {
        importMatcher.match(sessionId)
    }

    override suspend fun retryImportRow(
        sessionId: String,
        position: Int,
        query: String?,
    ) {
        importMatcher.retryRow(sessionId, position, query)
    }

    override fun selectImportCandidate(
        sessionId: String,
        position: Int,
        videoId: String,
    ): ImportRow =
        importMatcher.selectCandidate(sessionId, position, videoId)

    override fun createLocalPlaylistFromImport(
        sessionId: String,
        name: String,
    ): LocalPlaylist =
        localPlaylistService.createFromImport(sessionId, name)

    override suspend fun syncLocalPlaylist(
        playlistId: String,
        onProgress: (SyncProgress) -> Unit,
    ): SyncResult =
        playlistSyncService.sync(playlistId, onProgress)

    override fun renameLocalPlaylist(
        playlistId: String,
        name: String,
    ): LocalPlaylist =
        localPlaylistService.rename(playlistId, name)

    override fun moveLocalPlaylistTrack(
        playlistId: String,
        fromPosition: Int,
        toPosition: Int,
    ) {
        localPlaylistService.moveTrack(playlistId, fromPosition, toPosition)
    }

    override fun removeLocalPlaylistTrack(
        playlistId: String,
        position: Int,
    ) {
        localPlaylistService.removeTrack(playlistId, position)
    }

    override fun deleteLocalPlaylist(playlistId: String) {
        localPlaylistService.delete(playlistId)
    }

    fun enqueue(song: SongItem) {
        queue.enqueue(song)
    }

    suspend fun enqueuePlaylist(playlistId: String): Int =
        queue.enqueuePlaylist(playlistId)

    fun retry(videoId: String) {
        queue.retry(videoId)
    }

    fun cancelPending(videoIds: Set<String>): Int {
        val ids =
            bulkActionIds(
                downloads = downloads.value,
                action = BulkDownloadAction.CANCEL_PENDING,
                allowedIds = videoIds,
            )
        ids.forEach(queue::remove)
        return ids.size
    }

    fun retryFailed(videoIds: Set<String>? = null): Int {
        val ids =
            bulkActionIds(
                downloads = downloads.value,
                action = BulkDownloadAction.RETRY_FAILED,
                allowedIds = videoIds,
            )
        ids.forEach(queue::retry)
        return ids.size
    }

    fun clearFailed(videoIds: Set<String>? = null): Int {
        val ids =
            bulkActionIds(
                downloads = downloads.value,
                action = BulkDownloadAction.CLEAR_FAILED,
                allowedIds = videoIds,
            )
        ids.forEach(::remove)
        return ids.size
    }

    fun removeCompleted(videoIds: Set<String>? = null): Int {
        val ids =
            bulkActionIds(
                downloads = downloads.value,
                action = BulkDownloadAction.REMOVE_COMPLETED,
                allowedIds = videoIds,
            )
        ids.forEach(::remove)
        return ids.size
    }

    fun remove(videoId: String) {
        val recordedPath = offlineStore.get(videoId)?.localPath?.let(Path::of)

        queue.remove(videoId)

        recordedPath?.let(Files::deleteIfExists)
        deleteOfflineMediaFiles(
            mediaDir = appData.resolve("desktop").resolve("media"),
            videoId = videoId,
        )
    }

    suspend fun play(song: SongItem) {
        val backend = requireNotNull(player) {
            "libmpv runtime is unavailable"
        }
        backend.play(song)
    }

    suspend fun playQueue(
        songs: List<SongItem>,
        startIndex: Int,
    ) {
        val backend = requireNotNull(player) {
            "libmpv runtime is unavailable"
        }
        backend.playQueue(songs, startIndex)
    }

    suspend fun next() {
        player?.next()
    }

    suspend fun previous() {
        player?.previous()
    }

    suspend fun playNext(song: SongItem) {
        val backend = requireNotNull(player) { "libmpv runtime is unavailable" }
        val snapshot = backend.state.value
        if (snapshot.queueIndex in snapshot.queue.indices) {
            backend.playNext(song)
        } else {
            backend.play(song)
        }
    }

    suspend fun addToQueue(song: SongItem) {
        val backend = requireNotNull(player) { "libmpv runtime is unavailable" }
        val snapshot = backend.state.value
        if (snapshot.queueIndex in snapshot.queue.indices) {
            backend.addToQueue(song)
        } else {
            backend.play(song)
        }
    }

    suspend fun moveQueueItem(
        fromIndex: Int,
        toIndex: Int,
    ) {
        player?.moveQueueItem(fromIndex, toIndex)
    }

    suspend fun removeQueueItem(index: Int) {
        player?.removeQueueItem(index)
    }

    suspend fun clearUpcoming() {
        player?.clearUpcoming()
    }

    suspend fun toggleShuffle() {
        player?.toggleShuffle()
    }

    suspend fun cycleRepeatMode() {
        player?.cycleRepeatMode()
    }

    fun pause() {
        player?.pause()
    }

    fun resume() {
        player?.resume()
    }

    fun seek(positionMs: Long) {
        player?.seek(positionMs)
    }

    fun setVolume(volume: Int) {
        player?.setVolume(volume)
    }

    fun ensureArtwork(thumbnailUrl: String?): Path? =
        artworkCache.ensure(thumbnailUrl)

    fun estimateExport(): PortableExportEstimate =
        OfflineBackupService(
            store = offlineStore,
            artworkProvider = artworkCache::ensure,
        ).estimateAll()

    fun exportAll(
        destination: Path,
        onProgress: (PortableExportProgress) -> Unit = {},
    ): Path =
        OfflineBackupService(
            store = offlineStore,
            artworkProvider = artworkCache::ensure,
        ).exportAll(
            destination = destination,
            onProgress = onProgress,
        )

    override fun close() {
        player?.close()
        queue.close()
        uiPreferences.close()
        libraryStore.close()
        offlineStore.close()
    }

    companion object {
        fun open(projectRoot: Path): DesktopRuntime {
            val localAppData =
                requireNotNull(System.getenv("LOCALAPPDATA")) {
                    "LOCALAPPDATA is not available"
                }
            val appData = Path.of(localAppData, "MetroListWindows")
            Files.createDirectories(appData)

            val sessionStore =
                DpapiSessionStore(
                    appData.resolve("auth").resolve("session.bin"),
                )
            val offlineStore =
                OfflineStore(
                    appData.resolve("desktop").resolve("offline.db"),
                )
            val artworkCache =
                ArtworkCache(
                    appData.resolve("desktop").resolve("artwork"),
                )
            val libraryStore =
                LibraryStore(
                    appData.resolve("desktop").resolve("library.db"),
                )
            val uiPreferences =
                UiPreferences(
                    appData.resolve("desktop").resolve("ui.properties"),
                )
            val importMatcher =
                PlaylistImportMatcher(
                    store = libraryStore,
                    searchGateway = YouTubeSongSearchGateway(),
                )
            val localPlaylistService = LocalPlaylistService(libraryStore)
            val playlistSyncService =
                PlaylistSyncService(
                    store = libraryStore,
                    gateway = MetroListYouTubePlaylistGateway(),
                )
            val resolver =
                DesktopInnerTubeXPlayer(
                    appData.resolve("innertubex").resolve("player-config.properties"),
                )
            val fetcher = ResolvedMediaFetcher()
            val worker =
                MetroListOfflineWorker(
                    mediaDir = appData.resolve("desktop").resolve("media"),
                    resolver = resolver::resolve,
                    fetcher = { resolved, destination, onProgress ->
                        fetcher.fetch(resolved, destination, onProgress)
                    },
                )
            val queue =
                PersistentOfflineQueue(
                    store = offlineStore,
                    worker = { record, onProgress ->
                        worker.transfer(record, onProgress)
                    },
                    maxConcurrent = 3,
                    autoResumePending = false,
                )

            val mpvDll =
                locateMpvDll(projectRoot)
            val player =
                mpvDll?.let {
                    runCatching {
                        MpvPlayer(
                            sourceSelector =
                                PlayerSourceSelector(
                                    store = offlineStore,
                                    resolver = resolver::resolve,
                                ),
                            backend = LibMpvBackend(it),
                        )
                    }.getOrNull()
                }

            return DesktopRuntime(
                projectRoot = projectRoot,
                appData = appData,
                sessionStore = sessionStore,
                offlineStore = offlineStore,
                artworkCache = artworkCache,
                resolver = resolver,
                queue = queue,
                player = player,
                libraryStore = libraryStore,
                importMatcher = importMatcher,
                localPlaylistService = localPlaylistService,
                playlistSyncService = playlistSyncService,
                uiPreferences = uiPreferences,
            )
        }

        private fun locateMpvDll(projectRoot: Path): Path? {
            val explicit =
                System.getProperty("metrolist.mpv.dll")
                    ?.takeIf(String::isNotBlank)
                    ?.let(Path::of)
                    ?.takeIf(Files::exists)

            if (explicit != null) return explicit

            val packaged =
                applicationResourcesDir()
                    ?.resolve("mpv")
                    ?.resolve("libmpv-2.dll")
                    ?.takeIf(Files::exists)

            if (packaged != null) return packaged

            val development =
                projectRoot
                    .resolve("runtime-dev")
                    .resolve("mpv")
                    .resolve("20260924")
                    .resolve("files")
                    .resolve("libmpv-2.dll")

            return development.takeIf(Files::exists)
        }

        private fun authHelperCommand(projectRoot: Path): List<String> {
            val packaged =
                applicationResourcesDir()
                    ?.resolve("auth-helper")
                    ?.resolve("Metrolist.AuthHelper.exe")
                    ?.takeIf(Files::exists)

            if (packaged != null) {
                return listOf(packaged.toString())
            }

            val helperProject =
                projectRoot
                    .resolve("desktop-auth-helper")
                    .resolve("Metrolist.AuthHelper.csproj")

            return listOf(
                "dotnet",
                "run",
                "--project",
                helperProject.toString(),
                "--no-build",
                "--",
            )
        }

        private fun applicationResourcesDir(): Path? =
            System.getProperty("compose.application.resources.dir")
                ?.takeIf(String::isNotBlank)
                ?.let(Path::of)
                ?.takeIf(Files::exists)
    }
}
