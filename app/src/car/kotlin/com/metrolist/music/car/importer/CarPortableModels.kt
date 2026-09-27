package com.metrolist.music.car.importer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable data class CarSongsEnvelope(val songs: List<CarPortableSong>)
@Serializable data class CarArtistsEnvelope(val artists: List<CarPortableArtist>)
@Serializable data class CarAlbumsEnvelope(val albums: List<CarPortableAlbum> = emptyList())
@Serializable data class CarMapsEnvelope(val maps: List<CarPortableMap> = emptyList())
@Serializable data class CarFormatsEnvelope(val formats: List<CarPortableFormat>)
@Serializable data class CarTransfersEnvelope(val downloads: List<CarPortableTransfer>)

@Serializable
data class CarPortableSong(
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
data class CarPortableArtist(
    val id: String,
    val name: String,
    val thumbnailUrl: String?,
    val channelId: String?,
    val lastUpdateTime: String?,
    val isPodcastChannel: Boolean,
)

@Serializable data class CarPortableAlbum(val id: String)

@Serializable
data class CarPortableMap(
    val songId: String,
    val artistId: String,
    val position: Int,
)

@Serializable
data class CarPortableFormat(
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
data class CarPortableTransfer(
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
data class CarPortableSpan(
    val relativePath: String,
    val cacheKey: String,
    val position: Long,
    val length: Long,
    val sha256: String,
)

@Serializable
data class CarPortableResource(
    val songId: String,
    val cacheKey: String,
    val contentLength: Long,
    val spans: List<CarPortableSpan>,
)

@Serializable
data class CarPortableFile(
    val relativePath: String,
    val size: Long,
    val sha256: String,
    val kind: String,
)

@Serializable
data class CarPortableManifest(
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
    val exportedCacheResources: List<CarPortableResource>,
    val files: List<CarPortableFile>,
    val skippedSongs: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
)
