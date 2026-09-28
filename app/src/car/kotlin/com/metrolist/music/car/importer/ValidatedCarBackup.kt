package com.metrolist.music.car.importer

data class ValidatedCarBackup(
    val manifest: CarPortableManifest,
    val songs: List<CarPortableSong>,
    val artists: List<CarPortableArtist>,
    val albums: List<CarPortableAlbum>,
    val artistMaps: List<CarPortableMap>,
    val albumMaps: List<CarPortableMap>,
    val formats: List<CarPortableFormat>,
    val transfers: List<CarPortableTransfer>,
    val resources: List<CarPortableResource>,
)

class CarPortableValidationException(
    val errors: List<String>,
) : IllegalArgumentException(errors.joinToString("; "))


internal fun ValidatedCarBackup.withoutSongs(excludedSongIds: Set<String>): ValidatedCarBackup {
    if (excludedSongIds.isEmpty()) return this
    val includedIds = songs.map(CarPortableSong::id).filterNot(excludedSongIds::contains).toSet()
    return copy(
        songs = songs.filter { it.id in includedIds },
        artistMaps = artistMaps.filter { it.songId in includedIds },
        albumMaps = albumMaps.filter { it.songId in includedIds },
        formats = formats.filter { it.id in includedIds },
        transfers = transfers.filter { it.requestId in includedIds },
        resources = resources.filter { it.songId in includedIds },
    )
}
