package com.metrolist.desktop.offline

import com.metrolist.innertube.models.Album
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.SongItem
import kotlinx.serialization.Serializable

@Serializable
enum class OfflineState {
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED,
}

@Serializable
data class ArtistSnapshot(
    val name: String,
    val id: String?,
)

@Serializable
data class AlbumSnapshot(
    val name: String,
    val id: String,
)

@Serializable
data class FormatSnapshot(
    val itag: Int,
    val mimeType: String?,
    val codecs: String?,
    val bitrate: Int?,
    val sampleRate: Int?,
    val contentLength: Long?,
    val loudnessDb: Double?,
    val perceptualLoudnessDb: Double?,
)

@Serializable
data class OfflineRecord(
    val videoId: String,
    val title: String,
    val artists: List<ArtistSnapshot>,
    val album: AlbumSnapshot?,
    val duration: Int?,
    val musicVideoType: String?,
    val thumbnail: String,
    val explicit: Boolean,
    val isEpisode: Boolean,
    val state: OfflineState,
    val localPath: String? = null,
    val bytesDownloaded: Long = 0L,
    val contentLength: Long? = null,
    val bytesPerSecond: Long = 0L,
    val format: FormatSnapshot? = null,
    val downloadedAt: String? = null,
    val startTimeMs: Long,
    val updateTimeMs: Long,
    val lastError: String? = null,
) {
    fun toSongItem(): SongItem =
        SongItem(
            id = videoId,
            title = title,
            artists = artists.map { Artist(it.name, it.id) },
            album = album?.let { Album(it.name, it.id) },
            duration = duration,
            musicVideoType = musicVideoType,
            thumbnail = thumbnail,
            explicit = explicit,
            isEpisode = isEpisode,
        )

    companion object {
        fun queued(
            song: SongItem,
            nowMs: Long = System.currentTimeMillis(),
        ): OfflineRecord =
            OfflineRecord(
                videoId = song.id,
                title = song.title,
                artists = song.artists.map { ArtistSnapshot(it.name, it.id) },
                album = song.album?.let { AlbumSnapshot(it.name, it.id) },
                duration = song.duration,
                musicVideoType = song.musicVideoType,
                thumbnail = song.thumbnail,
                explicit = song.explicit,
                isEpisode = song.isEpisode,
                state = OfflineState.QUEUED,
                startTimeMs = nowMs,
                updateTimeMs = nowMs,
            )
    }
}
