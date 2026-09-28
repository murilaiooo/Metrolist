package com.metrolist.music.car.library

import com.metrolist.music.car.importer.CarArtworkStore
import com.metrolist.music.db.entities.Song
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.queues.ListQueue

enum class CarSongSourceKind {
    CAR_LIBRARY_LOCAL,
    CAR_LOCAL_DOWNLOAD,
    LEGACY_IMPORTED,
}

data class CarUnifiedSong(
    val id: String,
    val title: String,
    val artists: List<String>,
    val album: String?,
    val explicit: Boolean,
    val primarySource: CarSongSourceKind,
    val sourceKinds: Set<CarSongSourceKind>,
    val carLibrarySong: CarLibrarySong? = null,
    val downloadedSong: Song? = null,
)

object CarUnifiedCatalog {
    fun merge(
        index: CarLibraryIndex?,
        downloadedSongs: List<Song>,
        legacySongIds: Set<String>,
        dislikedSongIds: Set<String> = emptySet(),
    ): List<CarUnifiedSong> {
        val downloadsById = downloadedSongs.associateBy(Song::id).toMutableMap()
        val result = mutableListOf<CarUnifiedSong>()

        index?.songs?.forEach { carSong ->
            val downloaded = downloadsById.remove(carSong.id)
            val sources = linkedSetOf(CarSongSourceKind.CAR_LIBRARY_LOCAL)
            downloaded?.let {
                sources +=
                    if (carSong.id in legacySongIds) {
                        CarSongSourceKind.LEGACY_IMPORTED
                    } else {
                        CarSongSourceKind.CAR_LOCAL_DOWNLOAD
                    }
            }
            result +=
                CarUnifiedSong(
                    id = carSong.id,
                    title = carSong.title,
                    artists = carSong.artists.map(CarLibraryArtist::name),
                    album = carSong.album?.name,
                    explicit = carSong.explicit,
                    primarySource = CarSongSourceKind.CAR_LIBRARY_LOCAL,
                    sourceKinds = sources,
                    carLibrarySong = carSong,
                    downloadedSong = downloaded,
                )
        }

        downloadedSongs.forEach { downloaded ->
            if (downloaded.id !in downloadsById) return@forEach
            val source =
                if (downloaded.id in legacySongIds) {
                    CarSongSourceKind.LEGACY_IMPORTED
                } else {
                    CarSongSourceKind.CAR_LOCAL_DOWNLOAD
                }
            result +=
                CarUnifiedSong(
                    id = downloaded.id,
                    title = downloaded.title,
                    artists = downloaded.orderedArtists.map { it.name },
                    album = downloaded.album?.title,
                    explicit = downloaded.song.explicit,
                    primarySource = source,
                    sourceKinds = setOf(source),
                    downloadedSong = downloaded,
                )
        }

        return filterActiveDislikes(
            songs = result,
            dislikedSongIds = dislikedSongIds,
            allowDisliked = false,
        )
    }

    fun search(
        songs: List<CarUnifiedSong>,
        query: String,
    ): List<CarUnifiedSong> {
        val normalized = query.trim()
        if (normalized.isBlank()) return emptyList()
        return songs.filter { song ->
            song.title.contains(normalized, ignoreCase = true) ||
                song.artists.any { it.contains(normalized, ignoreCase = true) } ||
                song.album?.contains(normalized, ignoreCase = true) == true
        }
    }
}

fun filterActiveDislikes(
    songs: List<CarUnifiedSong>,
    dislikedSongIds: Set<String>,
    allowDisliked: Boolean,
): List<CarUnifiedSong> =
    if (allowDisliked || dislikedSongIds.isEmpty()) {
        songs
    } else {
        songs.filterNot { it.id in dislikedSongIds }
    }

sealed interface CarUnifiedQueueResolution {
    data class Ready(
        val queue: ListQueue,
        val skippedSongIds: List<String>,
    ) : CarUnifiedQueueResolution

    data class Unavailable(
        val songId: String,
    ) : CarUnifiedQueueResolution
}

class CarUnifiedPlaybackResolver(
    private val carLibraryResolver: CarLibraryMediaResolver,
    private val legacyArtworkStore: CarArtworkStore,
    private val activeDislikedSongIds: () -> Set<String> = { emptySet() },
) {
    fun resolveQueue(
        songs: List<CarUnifiedSong>,
        startIndex: Int = 0,
        title: String? = null,
        allowDisliked: Boolean = false,
    ): CarUnifiedQueueResolution {
        if (songs.isEmpty()) return CarUnifiedQueueResolution.Unavailable("")
        val safeStartIndex = startIndex.coerceIn(songs.indices)
        val startId = songs[safeStartIndex].id
        val dislikedIds = activeDislikedSongIds()
        if (!allowDisliked && startId in dislikedIds) {
            return CarUnifiedQueueResolution.Unavailable(startId)
        }
        val visibleSongs =
            filterActiveDislikes(
                songs = songs,
                dislikedSongIds = dislikedIds,
                allowDisliked = allowDisliked,
            )
        val resolved = mutableListOf<androidx.media3.common.MediaItem>()
        val skipped =
            if (allowDisliked) {
                mutableListOf()
            } else {
                songs.filter { it.id in dislikedIds }.mapTo(mutableListOf(), CarUnifiedSong::id)
            }

        visibleSongs.forEach { song ->
            val mediaItem = resolve(song)
            if (mediaItem == null) {
                if (song.id == startId) return CarUnifiedQueueResolution.Unavailable(song.id)
                skipped += song.id
            } else {
                resolved += mediaItem
            }
        }

        val resolvedStartIndex =
            resolved.indexOfFirst { it.mediaId == startId }
                .takeIf { it >= 0 }
                ?: return CarUnifiedQueueResolution.Unavailable(startId)

        return CarUnifiedQueueResolution.Ready(
            queue =
                ListQueue(
                    title = title,
                    items = resolved,
                    startIndex = resolvedStartIndex,
                ),
            skippedSongIds = skipped,
        )
    }

    private fun resolve(song: CarUnifiedSong): androidx.media3.common.MediaItem? {
        if (song.carLibrarySong != null) {
            when (val direct = carLibraryResolver.resolve(song.id)) {
                is CarLibraryMediaResolution.Ready -> return direct.mediaItem
                is CarLibraryMediaResolution.Unavailable -> Unit
            }
        }

        val downloaded = song.downloadedSong ?: return null
        val localArtwork =
            if (CarSongSourceKind.LEGACY_IMPORTED in song.sourceKinds) {
                runCatching { legacyArtworkStore.fileFor(song.id) }
                    .getOrNull()
                    ?.takeIf { it.isFile }
                    ?.toURI()
                    ?.toString()
            } else {
                null
            }
        val metadata =
            downloaded.toMediaMetadata().let { current ->
                if (localArtwork != null) current.copy(thumbnailUrl = localArtwork) else current
            }
        return metadata.toMediaItem()
    }
}