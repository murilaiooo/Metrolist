package com.metrolist.music.car.library

import androidx.media3.common.MediaItem
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.playback.queues.ListQueue

sealed interface CarLibraryMediaResolution {
    data class Ready(
        val mediaItem: MediaItem,
    ) : CarLibraryMediaResolution

    data class Unavailable(
        val songId: String,
        val reason: CarLibraryUnavailableReason,
    ) : CarLibraryMediaResolution
}

sealed interface CarLibraryQueueResolution {
    data class Ready(
        val queue: ListQueue,
        val skippedSongIds: List<String>,
    ) : CarLibraryQueueResolution

    data class Unavailable(
        val songId: String,
        val reason: CarLibraryUnavailableReason,
    ) : CarLibraryQueueResolution
}

class CarLibraryMediaResolver(
    private val repository: CarLibraryRepository,
    private val artworkResolver: CarArtworkResolver = CarArtworkResolver(),
    private val legacyArtworkLocator: (String) -> String? = { null },
    private val remoteArtworkLocator: (CarLibrarySong) -> String? = { null },
) {
    fun resolve(songId: String): CarLibraryMediaResolution =
        when (val availability = repository.trackAvailability(songId)) {
            is CarLibraryTrackAvailability.Unavailable ->
                CarLibraryMediaResolution.Unavailable(
                    songId = songId,
                    reason = availability.reason,
                )

            is CarLibraryTrackAvailability.Available ->
                CarLibraryMediaResolution.Ready(
                    mediaItem =
                        availability.song
                            .toMediaMetadata(
                                artworkResolver.resolve(
                                    carLibraryArtwork = availability.artworkLocator,
                                    legacyArtwork = legacyArtworkLocator(availability.song.id),
                                    remoteArtwork = remoteArtworkLocator(availability.song),
                                ),
                            )
                            .toMediaItem()
                            .buildUpon()
                            .setUri(availability.locator)
                            .setCustomCacheKey(
                                "car-library:" +
                                    repository.index()?.libraryId.orEmpty() +
                                    ":" +
                                    availability.song.id,
                            )
                            .build(),
                )
        }

    fun resolveQueue(
        songIds: List<String>,
        startIndex: Int = 0,
        title: String? = null,
    ): CarLibraryQueueResolution {
        if (songIds.isEmpty()) {
            return CarLibraryQueueResolution.Unavailable(
                songId = "",
                reason = CarLibraryUnavailableReason.UNKNOWN_TRACK,
            )
        }
        val safeStartIndex = startIndex.coerceIn(songIds.indices)
        val startSongId = songIds[safeStartIndex]
        val resolved = mutableListOf<MediaItem>()
        val skipped = mutableListOf<String>()

        songIds.forEach { songId ->
            when (val result = resolve(songId)) {
                is CarLibraryMediaResolution.Ready ->
                    resolved += result.mediaItem
                is CarLibraryMediaResolution.Unavailable -> {
                    if (songId == startSongId) {
                        return CarLibraryQueueResolution.Unavailable(
                            songId = songId,
                            reason = result.reason,
                        )
                    }
                    skipped += songId
                }
            }
        }

        val resolvedStartIndex =
            resolved.indexOfFirst { it.mediaId == startSongId }
                .takeIf { it >= 0 }
                ?: return CarLibraryQueueResolution.Unavailable(
                    songId = startSongId,
                    reason = CarLibraryUnavailableReason.MEDIA_MISSING,
                )

        return CarLibraryQueueResolution.Ready(
            queue =
                ListQueue(
                    title = title,
                    items = resolved,
                    startIndex = resolvedStartIndex,
                ),
            skippedSongIds = skipped,
        )
    }

    private fun CarLibrarySong.toMediaMetadata(artworkLocator: String?): MediaMetadata =
        MediaMetadata(
            id = id,
            title = title,
            artists =
                artists.map { artist ->
                    MediaMetadata.Artist(
                        id = artist.id,
                        name = artist.name,
                    )
                },
            duration = duration ?: -1,
            thumbnailUrl = artworkLocator,
            album =
                album?.let { current ->
                    MediaMetadata.Album(
                        id = current.id ?: "car-library-album:" + id,
                        title = current.name,
                    )
                },
            musicVideoType = if (isVideo) "CAR_LIBRARY_VIDEO" else null,
            explicit = explicit,
            isEpisode = isEpisode,
        )
}