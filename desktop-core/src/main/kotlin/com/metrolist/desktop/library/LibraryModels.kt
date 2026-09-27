package com.metrolist.desktop.library

import kotlinx.serialization.Serializable

@Serializable
data class LibraryArtistSnapshot(
    val name: String,
    val id: String?,
)

@Serializable
data class LibraryAlbumSnapshot(
    val name: String,
    val id: String,
)

@Serializable
data class LibrarySongSnapshot(
    val videoId: String,
    val title: String,
    val artists: List<LibraryArtistSnapshot>,
    val album: LibraryAlbumSnapshot? = null,
    val duration: Int? = null,
    val musicVideoType: String? = null,
    val thumbnail: String,
    val explicit: Boolean = false,
    val isEpisode: Boolean = false,
)

@Serializable
enum class PlaylistSyncState {
    LOCAL_ONLY,
    LINKED,
    PARTIAL,
    SYNCED,
}

@Serializable
enum class TrackSyncState {
    NOT_SYNCED,
    SYNCED,
    FAILED,
}

@Serializable
data class LocalPlaylist(
    val id: String,
    val name: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val youtubePlaylistId: String? = null,
    val syncState: PlaylistSyncState = PlaylistSyncState.LOCAL_ONLY,
)

@Serializable
data class LocalPlaylistTrack(
    val playlistId: String,
    val position: Int,
    val song: LibrarySongSnapshot,
    val sourceImportLine: String? = null,
    val syncState: TrackSyncState = TrackSyncState.NOT_SYNCED,
    val lastSyncError: String? = null,
)

@Serializable
enum class ImportSessionStatus {
    MATCHING,
    REVIEW,
    FINALIZED,
    CANCELLED,
}

@Serializable
data class ImportSession(
    val id: String,
    val name: String? = null,
    val sourceText: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val status: ImportSessionStatus = ImportSessionStatus.MATCHING,
)

@Serializable
enum class ImportRowState {
    PENDING,
    READY,
    REVIEW,
    NOT_FOUND,
    FAILED,
}

@Serializable
data class ImportCandidateSnapshot(
    val song: LibrarySongSnapshot,
    val score: Double,
    val versionKind: String? = null,
)

@Serializable
data class ImportRow(
    val sessionId: String,
    val position: Int,
    val original: String,
    val left: String,
    val right: String? = null,
    val state: ImportRowState = ImportRowState.PENDING,
    val selectedSong: LibrarySongSnapshot? = null,
    val score: Double? = null,
    val candidates: List<ImportCandidateSnapshot> = emptyList(),
    val error: String? = null,
)

@Serializable
data class RememberedMatch(
    val identity: String,
    val videoId: String,
    val title: String,
    val artist: String,
    val duration: Int? = null,
    val explicit: Boolean = false,
    val updatedAtMs: Long,
)
