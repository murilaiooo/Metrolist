package com.metrolist.music.car.importer

import com.metrolist.music.db.MusicDatabase
import java.time.LocalDateTime

class CarRoomImportTarget(
    private val database: MusicDatabase,
) : CarRoomImportPort {
    override suspend fun upsertSongBundle(
        song: CarPortableSong,
        artists: List<CarPortableArtist>,
        maps: List<CarPortableMap>,
        format: CarPortableFormat,
    ) {
        database.withTransaction {
            val existing = getSongByIdBlocking(song.id)?.song
            val songEntity =
                if (existing == null) {
                    CarPortableMappers.toSongEntity(song)
                } else {
                    CarPortableMappers.mergeSongEntity(existing, song)
                }
            upsert(songEntity)

            val artistEntities = artists.map(CarPortableMappers::toArtistEntity)
            artistEntities.forEach(::insert)

            songArtistMap(song.id).forEach(::delete)
            CarPortableMappers.toSongArtistMaps(song.id, maps).forEach(::insert)

            upsert(CarPortableMappers.toFormatEntity(format))
        }
    }

    override suspend fun removeSongBundle(songId: String) {
        database.withTransaction {
            songEntity(songId)?.let(::delete)
            deleteFormat(songId)
        }
    }

    override suspend fun restoreDownloadState(
        songId: String,
        isDownloaded: Boolean,
        dateDownload: String?,
    ) {        database.withTransaction {
            val existing = songEntity(songId) ?: return@withTransaction
            upsert(
                existing.copy(
                    isDownloaded = isDownloaded,
                    dateDownload = dateDownload?.let(LocalDateTime::parse),
                ),
            )
        }
    }
}
