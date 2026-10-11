package com.metrolist.music.car.library

import kotlinx.serialization.Serializable

@Serializable
data class CarLibraryManifest(
    val formatVersion: Int = 1,
    val libraryId: String,
    val revision: String,
    val createdAt: String,
    val updatedAt: String,
    val producer: CarLibraryProducer,
    val songs: List<CarLibrarySong>,
    val playlists: List<CarLibraryPlaylist> = emptyList(),
)

@Serializable
data class CarLibraryProducer(
    val product: String,
    val version: String,
)

@Serializable
data class CarLibraryArtist(
    val id: String? = null,
    val name: String,
)

@Serializable
data class CarLibraryAlbum(
    val id: String? = null,
    val name: String,
)

@Serializable
data class CarLibrarySong(
    val id: String,
    val title: String,
    val artists: List<CarLibraryArtist> = emptyList(),
    val album: CarLibraryAlbum? = null,
    val duration: Int? = null,
    val explicit: Boolean = false,
    val isVideo: Boolean = false,
    val isEpisode: Boolean = false,
    val mediaPath: String,
    val artworkPath: String? = null,
    val mimeType: String? = null,
    val codecs: String? = null,
    val bitrate: Int? = null,
    val sampleRate: Int? = null,
    val fileSize: Long,
    val sha256: String,
    val sourceModifiedAtMs: Long? = null,
    val artworkFileSize: Long? = null,
    val artworkSha256: String? = null,
    val artworkSourceModifiedAtMs: Long? = null,
    val artworkWidth: Int? = null,
    val artworkHeight: Int? = null,
)

@Serializable
data class CarLibraryPlaylist(
    val id: String,
    val name: String,
    val songIds: List<String>,
    val youtubePlaylistId: String? = null,
    val sourceRevision: String? = null,
)
