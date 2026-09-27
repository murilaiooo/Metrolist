package com.metrolist.music.car.importer

import com.metrolist.music.db.MusicDatabase

class CarRoomImportTarget(
    private val database: MusicDatabase,
) {
    suspend fun upsertSongBundle(
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
}
