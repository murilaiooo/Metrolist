package com.metrolist.desktop.portable

import com.metrolist.desktop.auth.AuthSessionValidator
import com.metrolist.desktop.auth.DpapiSessionStore
import com.metrolist.desktop.media.ResolvedMediaFetcher
import com.metrolist.desktop.stream.DesktopInnerTubeXPlayer
import com.metrolist.innertube.models.SongItem
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime

suspend fun main(args: Array<String>) {
    val destination =
        Path.of(
            args.firstOrNull()
                ?: "I:\\codex\\metrolist-windows\\Metrolist_Offline_Downloads_TEST_1_SONG.metrolistbackup",
        )

    val videoId = "WcHpojJz8Bc"
    val localAppData = requireNotNull(System.getenv("LOCALAPPDATA"))
    val appData = Path.of(localAppData, "MetroListWindows")

    val session =
        requireNotNull(
            DpapiSessionStore(appData.resolve("auth").resolve("session.bin")).load(),
        )
    AuthSessionValidator().validate(session)

    val resolver =
        DesktopInnerTubeXPlayer(
            appData.resolve("innertubex").resolve("player-config.properties"),
        )
    val resolved = resolver.resolve(videoId)

    val media = appData.resolve("probe").resolve("$videoId.audio")
    if (!Files.exists(media) || Files.size(media) != resolved.contentLength) {
        ResolvedMediaFetcher().fetch(resolved, media)
    }

    val song =
        SongItem(
            id = videoId,
            title = "(Don't Fear) The Reaper (Single Version)",
            artists = emptyList(),
            duration = 230,
            thumbnail =
                "https://yt3.googleusercontent.com/BVF_CFWaAmlscVTmTpV6WS9-dQcXOtuUWnRKejTlyUEcTFuJs_debfuSiT7ESPzT4BYwd5ZuyDWz5ec=w544-h544-l90-rj",
            explicit = false,
        )

    val now = LocalDateTime.now()
    val nowMs = System.currentTimeMillis()

    PortableBackupWriter().write(
        items =
            listOf(
                PortableItem(
                    song = song,
                    resolved = resolved,
                    mediaPath = media,
                    downloadedAt = now,
                    startTimeMs = nowMs,
                    updateTimeMs = nowMs,
                ),
            ),
        destination = destination,
        createdAt = now,
    )

    val validation = PortableValidator().validate(destination)
    check(validation.valid) { validation.errors.joinToString("; ") }

    println(
        "PORTABLE_OK path=$destination songs=1 bytes=${Files.size(media)} " +
            "archiveBytes=${Files.size(destination)}",
    )
}
