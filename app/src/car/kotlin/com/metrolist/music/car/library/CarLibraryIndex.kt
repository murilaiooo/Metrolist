package com.metrolist.music.car.library

data class CarLibraryIndex(
    val libraryId: String,
    val revision: String,
    val createdAt: String,
    val updatedAt: String,
    val producer: CarLibraryProducer,
    val songs: List<CarLibrarySong>,
    val playlists: List<CarLibraryPlaylist>,
) {
    private val songsById: Map<String, CarLibrarySong> =
        songs.associateBy(CarLibrarySong::id)

    fun song(songId: String): CarLibrarySong? = songsById[songId]

    companion object {
        fun from(manifest: CarLibraryManifest): CarLibraryIndex =
            CarLibraryIndex(
                libraryId = manifest.libraryId,
                revision = manifest.revision,
                createdAt = manifest.createdAt,
                updatedAt = manifest.updatedAt,
                producer = manifest.producer,
                songs = manifest.songs,
                playlists = manifest.playlists,
            )
    }
}