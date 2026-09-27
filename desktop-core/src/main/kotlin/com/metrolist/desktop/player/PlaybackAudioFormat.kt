package com.metrolist.desktop.player

import com.metrolist.desktop.offline.FormatSnapshot
import com.metrolist.desktop.stream.ResolvedAudio

data class PlaybackAudioFormat(
    val itag: Int?,
    val mimeType: String?,
    val codecs: String?,
    val bitrate: Int?,
    val sampleRate: Int?,
    val contentLength: Long? = null,
)

internal fun FormatSnapshot.toPlaybackAudioFormat() =
    PlaybackAudioFormat(
        itag = itag,
        mimeType = mimeType,
        codecs = codecs,
        bitrate = bitrate,
        sampleRate = sampleRate,
        contentLength = contentLength,
    )

internal fun ResolvedAudio.toPlaybackAudioFormat() =
    PlaybackAudioFormat(
        itag = itag,
        mimeType = mimeType,
        codecs = codecs,
        bitrate = bitrate,
        sampleRate = sampleRate,
        contentLength = contentLength,
    )
