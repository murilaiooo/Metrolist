package com.metrolist.desktop

import com.metrolist.desktop.auth.AuthSessionValidator
import com.metrolist.desktop.auth.DpapiSessionStore
import com.metrolist.desktop.media.ResolvedMediaFetcher
import com.metrolist.desktop.offline.MetroListOfflineWorker
import com.metrolist.desktop.offline.OfflineState
import com.metrolist.desktop.offline.OfflineStore
import com.metrolist.desktop.offline.PersistentOfflineQueue
import com.metrolist.desktop.stream.DesktopInnerTubeXPlayer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

private const val TARGET_VIDEO_ID = "IurTYZu_Pqg"

fun main() = runBlocking {
    val localAppData = requireNotNull(System.getenv("LOCALAPPDATA"))
    val appData = Path.of(localAppData, "MetroListWindows")
    val session = requireNotNull(
        DpapiSessionStore(appData.resolve("auth").resolve("session.bin")).load(),
    ) { "Saved session missing" }

    val source = OfflineStore(appData.resolve("desktop").resolve("offline.db")).use {
        requireNotNull(it.get(TARGET_VIDEO_ID)) { "Target record missing" }
    }

    val runDir =
        Path.of(System.getProperty("user.dir"))
            .resolve("runtime-dev")
            .resolve("download-recovery-probe")
            .resolve(System.currentTimeMillis().toString())
    val mediaDir = runDir.resolve("media")
    Files.createDirectories(mediaDir)

    OfflineStore(runDir.resolve("offline.db")).use { store ->
        store.upsert(
            source.copy(
                state = OfflineState.DOWNLOADING,
                localPath = null,
                bytesDownloaded = 0L,
                contentLength = null,
                bytesPerSecond = 0L,
                format = null,
                downloadedAt = null,
                updateTimeMs = System.currentTimeMillis(),
                lastError = null,
            ),
        )

        val resolver = DesktopInnerTubeXPlayer(
            appData.resolve("innertubex").resolve("player-config.properties"),
        )
        val fetcher = ResolvedMediaFetcher()
        val worker = MetroListOfflineWorker(
            mediaDir = mediaDir,
            resolver = resolver::resolve,
            fetcher = { resolved, destination, onProgress ->
                fetcher.fetch(resolved, destination, onProgress)
            },
        )

        PersistentOfflineQueue(
            store = store,
            worker = { record, onProgress ->
                worker.transfer(record, onProgress)
            },
            maxConcurrent = 3,
            autoResumePending = false,
        ).use { queue ->
            delay(250)
            val gated = requireNotNull(store.get(TARGET_VIDEO_ID))
            check(gated.state == OfflineState.QUEUED)
            check(gated.bytesDownloaded == 0L)
            println("GATE_OK state=${gated.state} bytes=${gated.bytesDownloaded}")

            AuthSessionValidator().validate(session)

            var previous = ""
            val elapsed = measureTimeMillis {
                queue.resumePending()
                while (true) {
                    val current = requireNotNull(store.get(TARGET_VIDEO_ID))
                    val snapshot =
                        "${current.state}|${current.bytesDownloaded}|" +
                            "${current.contentLength}|${current.bytesPerSecond}"
                    if (snapshot != previous) {
                        println(
                            "STATE state=${current.state} " +
                                "bytes=${current.bytesDownloaded} " +
                                "total=${current.contentLength} " +
                                "speed=${current.bytesPerSecond}",
                        )
                        previous = snapshot
                    }
                    if (current.state == OfflineState.COMPLETED ||
                        current.state == OfflineState.FAILED
                    ) break
                    delay(100)
                }
            }

            val completed = requireNotNull(store.get(TARGET_VIDEO_ID))
            check(completed.state == OfflineState.COMPLETED) {
                "Recovery failed: ${completed.lastError}"
            }
            val output = requireNotNull(completed.localPath).let(Path::of)
            check(Files.exists(output))
            check(Files.size(output) == completed.contentLength)
            println(
                "RECOVERY_OK elapsedMs=$elapsed " +
                    "bytes=${completed.bytesDownloaded} path=$output",
            )
        }
    }
}
