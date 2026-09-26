package com.metrolist.desktop.stream

data class ResolvedAudio(
    val videoId: String,
    val url: String,
    val headers: Map<String, String>,
    val clientName: String,
    val profileId: String,
    val expiresAtEpochMs: Long?,
    val requireBoundedRange: Boolean,
    val rangeChunkSizeBytes: Long,
    val useRangeChunks: Boolean,
    val itag: Int,
    val mimeType: String?,
    val codecs: String?,
    val bitrate: Int?,
    val sampleRate: Int?,
    val contentLength: Long?,
    val loudnessDb: Double?,
    val perceptualLoudnessDb: Double?,
) {
    override fun toString(): String =
        "ResolvedAudio(" +
            "videoId=$videoId, " +
            "url=present, " +
            "headerCount=${headers.size}, " +
            "clientName=$clientName, " +
            "profileId=$profileId, " +
            "itag=$itag, " +
            "mimeType=$mimeType, " +
            "bitrate=$bitrate, " +
            "sampleRate=$sampleRate, " +
            "contentLength=$contentLength)"
}
