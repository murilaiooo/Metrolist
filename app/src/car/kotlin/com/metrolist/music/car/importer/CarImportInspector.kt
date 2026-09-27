package com.metrolist.music.car.importer

import androidx.media3.database.DatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.offline.DefaultDownloadIndex
import androidx.media3.exoplayer.offline.Download
import com.metrolist.music.db.MusicDatabase

data class CarLocalImportState(
    val cacheComplete: Boolean,
    val downloadCompleted: Boolean,
    val roomComplete: Boolean,
    val artworkPresent: Boolean,
) {
    val downloadComplete: Boolean
        get() = downloadCompleted
    val artworkComplete: Boolean
        get() = artworkPresent
    val fullyComplete: Boolean
        get() = cacheComplete && downloadCompleted && roomComplete && artworkPresent
}

class CarImportInspector(
    private val cache: Cache,
    databaseProvider: DatabaseProvider,
    private val database: MusicDatabase,
    private val artworkStore: CarArtworkStore,
) {
    private val downloadIndex = DefaultDownloadIndex(databaseProvider)

    fun inspect(
        songId: String,
        contentLength: Long,
        artworkExpected: Boolean = false,
    ): CarLocalImportState {
        val download = downloadIndex.getDownload(songId)
        val song = database.getSongByIdBlocking(songId)
        return CarLocalImportState(
            cacheComplete =
                contentLength >= 0L &&
                    cache.isCached(songId, 0L, contentLength),
            downloadCompleted =
                download?.state == Download.STATE_COMPLETED &&
                    download.contentLength == contentLength,
            roomComplete =
                song?.song?.isDownloaded == true &&
                    song.format?.contentLength == contentLength,
            artworkPresent =
                !artworkExpected || artworkStore.fileFor(songId).isFile,
        )
    }
}
