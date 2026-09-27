package com.metrolist.music.car.importer

import kotlinx.serialization.Serializable

@Serializable
enum class CarImportPhase {
    VALIDATING,
    IMPORTING,
    COMMITTING,
    COMPLETE,
}

@Serializable
data class CarSongImportRecord(
    val songId: String,
    val contentLength: Long,
    val artworkExpected: Boolean,
    val initialCachePresent: Boolean,
    val initialDownloadPresent: Boolean,
    val initialRoomPresent: Boolean,
    val initialRoomDownloaded: Boolean,
    val initialRoomDateDownload: String?,
    val initialArtworkPresent: Boolean,
    val cacheReady: Boolean = false,
    val downloadReady: Boolean = false,
    val artworkReady: Boolean = false,
    val roomReady: Boolean = false,
    val completed: Boolean = false,
)
@Serializable
data class CarImportJournal(
    val sourceIdentity: String,
    val archiveFingerprint: String,
    val phase: CarImportPhase,
    val songs: Map<String, CarSongImportRecord> = emptyMap(),
)

class CarImportJournalException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
