package com.metrolist.desktop.library

data class SyncFailure(
    val position: Int,
    val videoId: String,
    val message: String,
)

data class SyncProgress(
    val total: Int,
    val completed: Int,
    val succeeded: Int,
    val failed: Int,
    val currentVideoId: String? = null,
)

data class SyncResult(
    val remotePlaylistId: String,
    val attempted: Int,
    val succeeded: Int,
    val failed: Int,
    val failures: List<SyncFailure>,
)

class PlaylistSyncService(
    private val store: LibraryStore,
    private val gateway: YouTubePlaylistGateway,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    suspend fun sync(
        localPlaylistId: String,
        onProgress: (SyncProgress) -> Unit = {},
    ): SyncResult {
        var playlist =
            requireNotNull(store.getPlaylist(localPlaylistId)) {
                "Local playlist not found: $localPlaylistId"
            }

        val remoteId =
            playlist.youtubePlaylistId ?: gateway.create(playlist.name).also { createdId ->
                require(createdId.isNotBlank()) { "YouTube Music returned a blank playlist ID" }
                playlist =
                    playlist.copy(
                        youtubePlaylistId = createdId,
                        syncState = PlaylistSyncState.LINKED,
                        updatedAtMs = nowMs(),
                    )
                store.upsertPlaylist(playlist)
            }

        val pending =
            store.playlistTracks(localPlaylistId).filter { it.syncState != TrackSyncState.SYNCED }
        var succeeded = 0
        val failures = mutableListOf<SyncFailure>()
        onProgress(
            SyncProgress(
                total = pending.size,
                completed = 0,
                succeeded = 0,
                failed = 0,
            ),
        )

        pending.forEachIndexed { index, track ->
            gateway.add(remoteId, track.song.videoId)
                .onSuccess {
                    succeeded += 1
                    store.upsertPlaylistTrack(
                        track.copy(
                            syncState = TrackSyncState.SYNCED,
                            lastSyncError = null,
                        ),
                    )
                }.onFailure { error ->
                    val message = error.message ?: error::class.simpleName ?: "Playlist sync failed"
                    failures += SyncFailure(track.position, track.song.videoId, message)
                    store.upsertPlaylistTrack(
                        track.copy(
                            syncState = TrackSyncState.FAILED,
                            lastSyncError = message,
                        ),
                    )
                }
            onProgress(
                SyncProgress(
                    total = pending.size,
                    completed = index + 1,
                    succeeded = succeeded,
                    failed = failures.size,
                    currentVideoId = track.song.videoId,
                ),
            )
        }

        val allSynced = store.playlistTracks(localPlaylistId).all { it.syncState == TrackSyncState.SYNCED }
        playlist =
            requireNotNull(store.getPlaylist(localPlaylistId)).copy(
                syncState = if (allSynced) PlaylistSyncState.SYNCED else PlaylistSyncState.PARTIAL,
                updatedAtMs = nowMs(),
            )
        store.upsertPlaylist(playlist)

        return SyncResult(
            remotePlaylistId = remoteId,
            attempted = pending.size,
            succeeded = succeeded,
            failed = failures.size,
            failures = failures,
        )
    }
}
