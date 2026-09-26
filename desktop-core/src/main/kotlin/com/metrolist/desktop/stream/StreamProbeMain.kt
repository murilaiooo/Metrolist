package com.metrolist.desktop.stream

import com.metrolist.desktop.auth.AuthSessionValidator
import com.metrolist.desktop.auth.DpapiSessionStore
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

    println(
        "STREAM_OK " +
            "videoId=${resolved.videoId} " +
            "itag=${resolved.itag} " +
            "mime=${resolved.mimeType} " +
            "codecs=${resolved.codecs} " +
            "bitrate=${resolved.bitrate} " +
            "sampleRate=${resolved.sampleRate} " +
            "contentLength=${resolved.contentLength} " +
            "client=${resolved.clientName} " +
            "profile=${resolved.profileId} " +
            "bounded=${resolved.requireBoundedRange} " +
            "chunked=${resolved.useRangeChunks}",
    )
}
