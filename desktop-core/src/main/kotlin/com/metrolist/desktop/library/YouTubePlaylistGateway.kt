package com.metrolist.desktop.library

import com.metrolist.innertube.YouTube

interface YouTubePlaylistGateway {
    suspend fun create(name: String): String
    suspend fun add(playlistId: String, videoId: String): Result<Unit>
}

class MetroListYouTubePlaylistGateway : YouTubePlaylistGateway {
    override suspend fun create(name: String): String = YouTube.createPlaylist(name)

    override suspend fun add(
        playlistId: String,
        videoId: String,
    ): Result<Unit> =
        YouTube.addToPlaylist(playlistId, videoId).fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { Result.failure(it) },
        )
}
