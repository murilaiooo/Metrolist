package com.metrolist.music.car.catalog

import android.net.Uri
import androidx.media3.common.MediaItem
import com.metrolist.music.car.importer.CarArtworkStore
import com.metrolist.music.car.library.CarLibraryIndex
import com.metrolist.music.car.library.CarLibraryMediaResolution
import com.metrolist.music.car.library.CarLibraryMediaResolver
import com.metrolist.music.car.library.CarLibrarySong
import com.metrolist.music.db.entities.Song
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.playback.queues.ListQueue

enum class CarOfflineSourceKind {
    CAR_LIBRARY_LOCAL,
    CAR_LOCAL_DOWNLOAD,
    LEGACY_IMPORTED,
}

data class CarOfflineCatalogItem(
    val id: String,
    val title: String,
    val artists: List<String>,
    val album: String?,
    val explicit: Boolean,
    val sources: Set<CarOfflineSourceKind>,
    val carLibrarySong: CarLibrarySong?,
    val roomSong: Song?,
)

object CarOfflineCatalog {
    fun build(
        carLibrary: CarLibraryIndex?,
        downloadedSongs: List<Song>,
        isLegacyImported: (String) -> Boolean,
    ): List<CarOfflineCatalogItem> {
        val roomById = downloadedSongs.associateBy(Song::id)
        val seen = linkedSetOf<String>()
        val result = mutableListOf<CarOfflineCatalogItem>()

        carLibrary?.songs.orEmpty().forEach { carSong ->
            val roomSong = roomById[carSong.id]
            val sources =
                buildSet {
                    add(CarOfflineSourceKind.CAR_LIBRARY_LOCAL)
                    if (roomSong != null) {
                        add(
                            if (isLegacyImported(carSong.id)) {
                                CarOfflineSourceKind.LEGACY_IMPORTED
                            } else {
                                CarOfflineSourceKind.CAR_LOCAL_DOWNLOAD
                            },
                        )
                    }
                }
            result +=
                CarOfflineCatalogItem(
                    id = carSong.id,
                    title = carSong.title,
                    artists = carSong.artists.map { it.name },
                    album = carSong.album?.name,
                    explicit = carSong.explicit,
                    sources = sources,
                    carLibrarySong = carSong,
                    roomSong = roomSong,
                )
            seen += carSong.id
        }

        downloadedSongs.forEach { roomSong ->
            if (!seen.add(roomSong.id)) return@forEach
            val legacy = isLegacyImported(roomSong.id)
            result +=
                CarOfflineCatalogItem(
                    id = roomSong.id,
                    title = roomSong.title,
                    artists = roomSong.orderedArtists.map { it.name },
                    album = roomSong.album?.title,
                    explicit = roomSong.song.explicit,
                    sources =
                        setOf(
                            if (legacy) {
                                CarOfflineSourceKind.LEGACY_IMPORTED
                            } else {
                                CarOfflineSourceKind.CAR_LOCAL_DOWNLOAD
                            },
                        ),
                    carLibrarySong = null,
                    roomSong = roomSong,
                )
        }

        return result
    }
}

sealed interface CarOfflineQueueResolution {
    data class Ready(
        val queue: ListQueue,
        val skippedSongIds: List<String>,
    ) : CarOfflineQueueResolution

    data class Unavailable(
        val songId: String,
    ) : CarOfflineQueueResolution
}

class CarOfflineCatalogMediaResolver(
    private val carLibraryResolver: CarLibraryMediaResolver,
    private val legacyArtworkStore: CarArtworkStore,
) {
    fun resolveQueue(
        items: List<CarOfflineCatalogItem>,
        startIndex: Int,
        title: String?,
    ): CarOfflineQueueResolution {
        if (items.isEmpty()) return CarOfflineQueueResolution.Unavailable("")
        val safeStart = startIndex.coerceIn(items.indices)
        val startId = items[safeStart].id
        val mediaItems = mutableListOf<MediaItem>()
        val skipped = mutableListOf<String>()

        items.forEach { item ->
            val mediaItem = resolve(item)
            if (mediaItem == null) {
                if (item.id == startId) {
                    return CarOfflineQueueResolution.Unavailable(item.id)
                }
                skipped += item.id
            } else {
                mediaItems += mediaItem
            }
        }

        val resolvedStart =
            mediaItems.indexOfFirst { it.mediaId == startId }
                .takeIf { it >= 0 }
                ?: return CarOfflineQueueResolution.Unavailable(startId)

        return CarOfflineQueueResolution.Ready(
            queue =
                ListQueue(
                    title = title,
                    items = mediaItems,
                    startIndex = resolvedStart,
                ),
            skippedSongIds = skipped,
        )
    }

    private fun resolve(item: CarOfflineCatalogItem): MediaItem? {
        if (CarOfflineSourceKind.CAR_LIBRARY_LOCAL in item.sources) {
            when (val direct = carLibraryResolver.resolve(item.id)) {
                is CarLibraryMediaResolution.Ready -> return direct.mediaItem
                is CarLibraryMediaResolution.Unavailable -> Unit
            }
        }

        val roomSong = item.roomSong ?: return null
        val base = roomSong.toMediaItem()

        if (CarOfflineSourceKind.LEGACY_IMPORTED !in item.sources) {
            return base
        }

        val artworkFile =
            runCatching { legacyArtworkStore.fileFor(item.id) }
                .getOrNull()
                ?.takeIf { it.isFile }
                ?: return base
        return base.buildUpon()
            .setMediaMetadata(
                base.mediaMetadata.buildUpon()
                    .setArtworkUri(Uri.fromFile(artworkFile))
                    .build(),
            )
            .build()
    }
}