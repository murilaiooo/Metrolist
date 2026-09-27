package com.metrolist.desktop.portable

import com.metrolist.desktop.auth.AuthSessionValidator
import com.metrolist.desktop.auth.DpapiSessionStore
import com.metrolist.desktop.media.ResolvedMediaFetcher
import com.metrolist.desktop.offline.MetroListOfflineWorker
import com.metrolist.desktop.offline.OfflineState
import com.metrolist.desktop.offline.OfflineStore
import com.metrolist.desktop.offline.PersistentOfflineQueue
import com.metrolist.desktop.stream.DesktopInnerTubeXPlayer
import com.metrolist.innertube.YouTube
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime

suspend fun main(args: Array<String>) {
    val projectRoot = Path.of(args.first())
    val localAppData = requireNotNull(System.getenv("LOCALAPPDATA"))
    val session = requireNotNull(
        DpapiSessionStore(
            Path.of(localAppData, "MetroListWindows", "auth", "session.bin"),
        ).load(),
    )
    AuthSessionValidator().validate(session)

    val stamp = LocalDateTime.now().toString().replace(":", "-")
    val runtime = projectRoot.resolve("runtime-dev").resolve("queue-export-$stamp")
    Files.createDirectories(runtime)

    val store = OfflineStore(runtime.resolve("offline.db"))
    try {
        val resolver = DesktopInnerTubeXPlayer(runtime.resolve("player-config.properties"))
        val fetcher = ResolvedMediaFetcher()
        val worker = MetroListOfflineWorker(
            mediaDir = runtime.resolve("media"),
            resolver = resolver::resolve,
            fetcher = { resolved, destination, onProgress ->
                fetcher.fetch(resolved, destination, onProgress)
            },
        )

        val song = YouTube.queue(videoIds = listOf("WcHpojJz8Bc"))
            .getOrThrow()
            .first { it.id == "WcHpojJz8Bc" }

        PersistentOfflineQueue(
            store = store,
            worker = { record, onProgress ->
                worker.transfer(record, onProgress)
            },
            maxConcurrent = 3,
        ).use { queue ->
            queue.enqueue(song)
            check(queue.awaitIdle(90_000)) { "Queue did not become idle" }
        }

        val completed = requireNotNull(store.get("WcHpojJz8Bc"))
        check(completed.state == OfflineState.COMPLETED) {
            "Probe item did not complete"
        }

        val backup = OfflineBackupService(store).exportAll(
            runtime.resolve("queue-export.metrolistbackup"),
        )

        println(
            "QUEUE_EXPORT_OK " +
                "state=${completed.state} " +
                "bytes=${completed.bytesDownloaded} " +
                "backupBytes=${Files.size(backup)} " +
                "runtime=$runtime",
        )
    } finally {
        store.close()
    }
}
