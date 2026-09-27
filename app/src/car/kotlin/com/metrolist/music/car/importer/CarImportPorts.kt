package com.metrolist.music.car.importer

import java.io.File
import java.io.InputStream

interface CarMediaImportPort {
    fun prepareResource(resource: CarPortableResource): Boolean

    fun writeSpan(
        resource: CarPortableResource,
        span: CarPortableSpan,
        input: InputStream,
    )

    fun finishResource(resource: CarPortableResource)

    fun putCompletedDownload(transfer: CarPortableTransfer)

    fun removeResource(songId: String)

    fun removeDownload(songId: String)
}
interface CarRoomImportPort {
    suspend fun upsertSongBundle(
        song: CarPortableSong,
        artists: List<CarPortableArtist>,
        maps: List<CarPortableMap>,
        format: CarPortableFormat,
    )

    suspend fun removeSongBundle(songId: String)

    suspend fun restoreDownloadState(
        songId: String,
        isDownloaded: Boolean,
        dateDownload: String?,
    )
}

interface CarArtworkImportPort {
    fun write(songId: String, input: InputStream): File

    fun copy(sourceSongId: String, targetSongId: String): File

    fun delete(songId: String): Boolean
}
interface CarImportInspectorPort {
    fun inspect(
        songId: String,
        contentLength: Long,
        artworkExpected: Boolean = false,
    ): CarLocalImportState
}

interface CarImportSource {
    val identity: String

    fun open(): InputStream
}
