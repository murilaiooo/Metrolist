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
