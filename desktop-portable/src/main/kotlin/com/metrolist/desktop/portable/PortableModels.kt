package com.metrolist.desktop.portable

import com.metrolist.desktop.stream.ResolvedAudio
import com.metrolist.innertube.models.SongItem
import java.nio.file.Path
import java.time.LocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class PortableItem(
    val song: SongItem,
    val resolved: ResolvedAudio,
    val mediaPath: Path,
    val downloadedAt: LocalDateTime,
    val startTimeMs: Long,
    val updateTimeMs: Long,
)

@Serializable data class SongsEnvelope(val songs: List<PortableSong>)
@Serializable data class ArtistsEnvelope(val artists: List<PortableArtist>)
@Serializable data class AlbumsEnvelope(val albums: List<PortableAlbum> = emptyList())
@Serializable data class MapsEnvelope(val maps: List<PortableMap> = emptyList())
@Serializable data class FormatsEnvelope(val formats: List<PortableFormat>)
@Serializable data class TransfersEnvelope(val downloads: List<PortableTransfer>)

@Serializable
data class PortableSong(
    val id: String,
    val title: String,
    val duration: Int?,
    val thumbnailUrl: String?,
    val thumbnailArtworkPath: String?,
    val albumId: String?,
    val albumName: String?,
    val explicit: Boolean,
    val year: Int?,
    val date: String?,
    val dateModified: String?,
    val dateDownload: String?,
    val isVideo: Boolean,
    val isEpisode: Boolean,
)

@Serializable
data class PortableArtist(
    val id: String,
    val name: String,
    val thumbnailUrl: String?,
    val channelId: String?,
    val lastUpdateTime: String?,
    val isPodcastChannel: Boolean,
)

@Serializable
data class PortableAlbum(
    val id: String,
)

@Serializable
data class PortableMap(
    val songId: String,
    val artistId: String,
    val position: Int,
)

@Serializable
data class PortableFormat(
    val id: String,
    val itag: Int,
    val mimeType: String?,
    val codecs: String?,
    val bitrate: Int?,
    val sampleRate: Int?,
    val contentLength: Long,
    val loudnessDb: Double?,
    val perceptualLoudnessDb: Double?,
)

@Serializable
data class PortableTransfer(
    val requestId: String,
    val uri: String,
    val customCacheKey: String,
    val title: String,
    val contentLength: Long,
    val startTimeMs: Long,
    val updateTimeMs: Long,
    val bytesDownloaded: Long,
)

@Serializable
data class PortableSpan(
    val relativePath: String,
    val cacheKey: String,
    val position: Long,
    val length: Long,
    val sha256: String,
)

@Serializable
data class PortableResource(
    val songId: String,
    val cacheKey: String,
    val contentLength: Long,
    val spans: List<PortableSpan>,
)

@Serializable
data class PortableFile(
    val relativePath: String,
    val size: Long,
    val sha256: String,
    val kind: String,
)

@Serializable
data class PortableManifest(
    val appName: String = "Metrolist",
    val backupFormatVersion: Int = 1,
    val createdAt: String,
    val sourceAppVersion: String = "13.6.0",
    val sourceDatabaseVersion: Int = 38,
    val exportedSongCount: Int,
    val exportedDownloadCount: Int,
    val totalBackupBytes: Long? = null,
    val totalCacheBytes: Long,
    val totalArtworkBytes: Long = 0,
    val type: String = "offline_downloads",
    val cacheStrategy: String = "media3_simple_cache_spans",
    val songIds: List<String>,
    @SerialName("media3DownloadRequestIds")
    val requestIds: List<String>,
    val exportedCacheResources: List<PortableResource>,
    val files: List<PortableFile>,
    val skippedSongs: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
)
