package com.metrolist.music.car.importer

import androidx.media3.database.DatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.offline.DefaultDownloadIndex
import androidx.media3.exoplayer.offline.Download
import com.metrolist.music.db.MusicDatabase

data class CarLocalImportState(
    val cachePresent: Boolean = false,
    val cacheComplete: Boolean = false,
    val downloadPresent: Boolean = false,
    val downloadCompleted: Boolean = false,
    val roomPresent: Boolean = false,
    val roomComplete: Boolean = false,
    val roomDownloaded: Boolean = false,
    val roomDateDownload: String? = null,
    val artworkPresent: Boolean = false,
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
) : CarImportInspectorPort {
    private val downloadIndex = DefaultDownloadIndex(databaseProvider)

    override fun inspect(
        songId: String,
        contentLength: Long,
        artworkExpected: Boolean,
    ): CarLocalImportState {
        val download = downloadIndex.getDownload(songId)
        val song = database.getSongByIdBlocking(songId)
        val cachePresent =
            contentLength > 0L &&
                cache.getCachedBytes(songId, 0L, contentLength) > 0L
        return CarLocalImportState(
            cachePresent = cachePresent,
            cacheComplete =
                contentLength >= 0L &&
                    cache.isCached(songId, 0L, contentLength),
            downloadPresent = download != null,
            downloadCompleted =
                download?.state == Download.STATE_COMPLETED &&
                    download.contentLength == contentLength,
            roomPresent = song != null,
            roomComplete =
                song?.song?.isDownloaded == true &&
                    song.format?.contentLength == contentLength,
            roomDownloaded = song?.song?.isDownloaded == true,
            roomDateDownload = song?.song?.dateDownload?.toString(),
            artworkPresent =
                !artworkExpected || artworkStore.fileFor(songId).isFile,
        )
    }
}
