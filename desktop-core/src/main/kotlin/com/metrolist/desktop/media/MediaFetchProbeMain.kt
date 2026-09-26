package com.metrolist.desktop.media

import com.metrolist.desktop.auth.AuthSessionValidator
import com.metrolist.desktop.auth.DpapiSessionStore
import com.metrolist.desktop.stream.DesktopInnerTubeXPlayer
import java.nio.file.Path

suspend fun main(args: Array<String>) {
    val videoId = args.firstOrNull()?.takeIf(String::isNotBlank) ?: "WcHpojJz8Bc"
    val localAppData = requireNotNull(System.getenv("LOCALAPPDATA"))
    val appData = Path.of(localAppData, "MetroListWindows")

    val session = requireNotNull(
        DpapiSessionStore(appData.resolve("auth").resolve("session.bin")).load(),
    )
    AuthSessionValidator().validate(session)

    val resolved = DesktopInnerTubeXPlayer(
        appData.resolve("innertubex").resolve("player-config.properties"),
    ).resolve(videoId)

    val destination = appData.resolve("probe").resolve("$videoId.audio")
    val written = ResolvedMediaFetcher().fetch(resolved, destination)

    println(
        "MEDIA_OK " +
            "videoId=$videoId " +
            "bytes=${written.bytes} " +
            "sha256=${written.sha256} " +
            "itag=${resolved.itag} " +
            "mime=${resolved.mimeType} " +
            "codecs=${resolved.codecs}",
    )
}
