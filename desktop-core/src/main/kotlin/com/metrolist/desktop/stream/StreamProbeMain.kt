package com.metrolist.desktop.stream

import com.metrolist.desktop.auth.AuthSessionValidator
import com.metrolist.desktop.auth.DpapiSessionStore
import com.metrolist.innertubex.extraction.AudioQuality
import java.nio.file.Path
import java.util.Locale

internal fun auditAudioQuality(value: String?): AudioQuality =
    value
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.uppercase(Locale.ROOT)
        ?.let { normalized -> runCatching { AudioQuality.valueOf(normalized) }.getOrNull() }
        ?: AudioQuality.HIGH

internal fun auditExcludedClients(value: String?): Set<String> =
    value
        ?.split(',')
        ?.map(String::trim)
        ?.filter(String::isNotEmpty)
        ?.toSet()
        .orEmpty()

suspend fun main(args: Array<String>) {
    val videoId = args.firstOrNull()?.takeIf(String::isNotBlank) ?: "WcHpojJz8Bc"
    val localAppData = requireNotNull(System.getenv("LOCALAPPDATA"))
    val appData = Path.of(localAppData, "MetroListWindows")
    val session = requireNotNull(
        DpapiSessionStore(appData.resolve("auth").resolve("session.bin")).load(),
    )

    AuthSessionValidator().validate(session)

    val player =
        DesktopInnerTubeXPlayer(
            appData.resolve("innertubex").resolve("player-config.properties"),
        )
    val excludedClients = auditExcludedClients(System.getenv("METROLIST_AUDIT_EXCLUDE_CLIENTS"))
    val audioQuality = auditAudioQuality(System.getenv("METROLIST_AUDIT_AUDIO_QUALITY"))
    val resolved =
        if (excludedClients.isEmpty() && audioQuality == AudioQuality.HIGH) {
            player.resolve(videoId)
        } else {
            player.resolveForAudit(
                videoId = videoId,
                excludedClients = excludedClients,
                audioQuality = audioQuality,
            )
        }

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
