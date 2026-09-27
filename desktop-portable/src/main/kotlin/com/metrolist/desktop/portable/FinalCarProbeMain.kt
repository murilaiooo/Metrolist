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
import com.metrolist.innertube.models.SongItem
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime

suspend fun main(args: Array<String>) {
    require(args.size == 1) { "Expected project root" }

    val projectRoot = Path.of(args[0])
    val localAppData = requireNotNull(System.getenv("LOCALAPPDATA"))
    val session =
        requireNotNull(
            DpapiSessionStore(
                Path.of(localAppData, "MetroListWindows", "auth", "session.bin"),
            ).load(),
        ) { "Saved YouTube Music session is missing" }

    AuthSessionValidator().validate(session)

    val stamp = LocalDateTime.now().toString().replace(":", "-")
    val runtime =
        projectRoot
            .resolve("runtime-dev")
            .resolve("final-car-probe-" + stamp)
    Files.createDirectories(runtime)

    val songs =
        YouTube.search(
            query = "Daft Punk",
            filter = YouTube.SearchFilter.FILTER_SONG,
        ).getOrThrow()
            .items
            .filterIsInstance<SongItem>()
            .distinctBy(SongItem::id)
            .take(3)

    require(songs.size == 3) { "Search did not return three distinct songs" }

    OfflineStore(runtime.resolve("offline.db")).use { store ->
        val resolver =
            DesktopInnerTubeXPlayer(
                runtime.resolve("player-config.properties"),
            )
        val fetcher = ResolvedMediaFetcher()
        val worker =
            MetroListOfflineWorker(
                mediaDir = runtime.resolve("media"),
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
        ).use { queue ->
            queue.enqueueAll(songs)
            check(queue.awaitIdle(180_000)) {
                "Three-song queue did not become idle"
            }
        }

        val completed = store.listCompleted()
        check(completed.size == 3) {
            "Expected three completed songs, got " + completed.size
        }
        check(completed.all { it.state == OfflineState.COMPLETED }) {
            "One or more songs were not completed"
        }

        val backup =
            OfflineBackupService(store).exportAll(
                runtime.resolve(
                    "Metrolist_Offline_Downloads_FINAL_3_SONGS.metrolistbackup",
                ),
            )

        val validation = PortableValidator().validate(backup)
        check(validation.valid) {
            "Three-song backup failed validation: " +
                validation.errors.joinToString("; ")
        }

        println(
            "FINAL_CAR_PROBE_OK songs=3 " +
                "mediaBytes=" + completed.sumOf { it.bytesDownloaded } +
                "backupBytes=" + Files.size(backup) +
                "backup=" + backup,
        )
    }
}
