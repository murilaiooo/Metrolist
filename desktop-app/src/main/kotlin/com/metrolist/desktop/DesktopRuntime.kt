package com.metrolist.desktop

import com.metrolist.desktop.auth.AuthHelperLauncher
import com.metrolist.desktop.auth.AuthSessionValidator
import com.metrolist.desktop.auth.DpapiSessionStore
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
import com.metrolist.desktop.stream.DesktopInnerTubeXPlayer
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AccountState(
    val signedIn: Boolean = false,
    val name: String? = null,
    val email: String? = null,
    val error: String? = null,
)

class DesktopRuntime private constructor(
    private val projectRoot: Path,
    private val appData: Path,
    private val sessionStore: DpapiSessionStore,
    private val offlineStore: OfflineStore,
    private val resolver: DesktopInnerTubeXPlayer,
    private val queue: PersistentOfflineQueue,
    private val player: MpvPlayer?,
) : AutoCloseable {
    private val mutableAccount = MutableStateFlow(AccountState())
    val account: StateFlow<AccountState> = mutableAccount.asStateFlow()

    val downloads: StateFlow<List<OfflineRecord>> = queue.downloads
    val playerState: StateFlow<PlayerState>? = player?.state

    suspend fun initializeSession() {
        val saved = sessionStore.load()
        if (saved == null) {
            mutableAccount.value = AccountState()
            return
        }

        runCatching {
            AuthSessionValidator().validate(saved)
        }.onSuccess { validated ->
            mutableAccount.value =
                AccountState(
                    signedIn = true,
                    name = validated.session.accountName,
                    email = validated.session.accountEmail,
                )
        }.onFailure { error ->
            mutableAccount.value =
                AccountState(
                    signedIn = false,
                    error = error.message ?: error::class.simpleName,
                )
        }
    }

    suspend fun loginInteractive() {
        val helperProject =
            projectRoot
                .resolve("desktop-auth-helper")
                .resolve("Metrolist.AuthHelper.csproj")

        val session =
            AuthHelperLauncher(
                command =
                    listOf(
                        "dotnet",
                        "run",
                        "--project",
                        helperProject.toString(),
                        "--no-build",
                        "--",
                    ),
                workingDirectory = projectRoot,
            ).login()

        val validated = AuthSessionValidator().validate(session)
        sessionStore.save(validated.session)

        mutableAccount.value =
            AccountState(
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
        mutableAccount.value = AccountState()
    }

    fun enqueue(song: SongItem) {
        queue.enqueue(song)
    }

    suspend fun enqueuePlaylist(playlistId: String): Int =
        queue.enqueuePlaylist(playlistId)

    fun retry(videoId: String) {
        queue.retry(videoId)
    }

    fun remove(videoId: String) {
        offlineStore.get(videoId)
            ?.localPath
            ?.let(Path::of)
            ?.takeIf(Files::exists)
            ?.let(Files::deleteIfExists)

        queue.remove(videoId)
    }

    suspend fun play(song: SongItem) {
        val backend = requireNotNull(player) {
            "libmpv runtime is unavailable"
        }
        backend.play(song)
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

    fun exportAll(destination: Path): Path =
        OfflineBackupService(offlineStore).exportAll(destination)

    override fun close() {
        player?.close()
        queue.close()
        offlineStore.close()
    }

    companion object {
        suspend fun open(projectRoot: Path): DesktopRuntime {
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
            val resolver =
                DesktopInnerTubeXPlayer(
                    appData.resolve("innertubex").resolve("player-config.properties"),
                )
            val fetcher = ResolvedMediaFetcher()
            val worker =
                MetroListOfflineWorker(
                    mediaDir = appData.resolve("desktop").resolve("media"),
                    resolver = resolver::resolve,
                    fetcher = fetcher::fetch,
                )
            val queue =
                PersistentOfflineQueue(
                    store = offlineStore,
                    worker = worker::transfer,
                    maxConcurrent = 3,
                )

            val mpvDll =
                locateMpvDll(projectRoot)
            val player =
                mpvDll?.let {
                    MpvPlayer(
                        sourceSelector =
                            PlayerSourceSelector(
                                store = offlineStore,
                                resolver = resolver::resolve,
                            ),
                        backend = LibMpvBackend(it),
                    )
                }

            return DesktopRuntime(
                projectRoot = projectRoot,
                appData = appData,
                sessionStore = sessionStore,
                offlineStore = offlineStore,
                resolver = resolver,
                queue = queue,
                player = player,
            ).also {
                it.initializeSession()
            }
        }

        private fun locateMpvDll(projectRoot: Path): Path? {
            val explicit =
                System.getProperty("metrolist.mpv.dll")
                    ?.takeIf(String::isNotBlank)
                    ?.let(Path::of)
                    ?.takeIf(Files::exists)

            if (explicit != null) return explicit

            val development =
                projectRoot
                    .resolve("runtime-dev")
                    .resolve("mpv")
                    .resolve("20260924")
                    .resolve("files")
                    .resolve("libmpv-2.dll")

            return development.takeIf(Files::exists)
        }
    }
}
