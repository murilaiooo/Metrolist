package com.metrolist.music.car.importer

import android.net.Uri
import androidx.media3.database.DatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.ContentMetadataMutations
import androidx.media3.exoplayer.offline.DefaultDownloadIndex
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadProgress
import androidx.media3.exoplayer.offline.DownloadRequest
import java.io.InputStream

class CarMedia3ImportTarget(
    private val cache: Cache,
    databaseProvider: DatabaseProvider,
) {
    private val downloadIndex = DefaultDownloadIndex(databaseProvider)

    fun writeResource(
        resource: CarPortableResource,
        openSpan: (CarPortableSpan) -> InputStream,
    ) {
        if (cache.isCached(resource.cacheKey, 0L, resource.contentLength)) return

        if (cache.getCachedBytes(resource.cacheKey, 0L, resource.contentLength) > 0L) {
            cache.removeResource(resource.cacheKey)
        }

        resource.spans.sortedBy(CarPortableSpan::position).forEach { span ->
            require(span.cacheKey == resource.cacheKey) { "Span cache key mismatch" }
            val hole = cache.startReadWrite(resource.cacheKey, span.position, span.length)
            check(hole.isHoleSpan) { "Expected writable cache hole for " + span.relativePath }
            try {
                val sink = CacheDataSink(cache, CacheDataSink.DEFAULT_FRAGMENT_SIZE)
                val dataSpec =
                    DataSpec.Builder()
                        .setUri(resource.songId)
                        .setPosition(span.position)
                        .setLength(span.length)
                        .setKey(resource.cacheKey)
                        .build()
                sink.open(dataSpec)
                try {
                    openSpan(span).use { input ->
                        copyExactly(input, sink, span.length)
                    }
                } finally {
                    sink.close()
                }
            } finally {
                cache.releaseHoleSpan(hole)
            }
        }

        val metadata = ContentMetadataMutations()
        ContentMetadataMutations.setContentLength(metadata, resource.contentLength)
        cache.applyContentMetadataMutations(resource.cacheKey, metadata)
    }

    fun putCompletedDownload(transfer: CarPortableTransfer) {
        val request =
            DownloadRequest.Builder(
                transfer.requestId,
                Uri.parse(transfer.uri),
            )
                .setCustomCacheKey(transfer.customCacheKey)
                .setData(transfer.title.encodeToByteArray())
                .build()

        val progress =
            DownloadProgress().apply {
                bytesDownloaded = transfer.bytesDownloaded
                percentDownloaded =
                    if (transfer.contentLength > 0L) {
                        (transfer.bytesDownloaded.toDouble() / transfer.contentLength * 100.0)
                            .toFloat()
                            .coerceIn(0f, 100f)
                    } else {
                        100f
                    }
            }

        downloadIndex.putDownload(
            Download(
                request,
                Download.STATE_COMPLETED,
                transfer.startTimeMs,
                transfer.updateTimeMs,
                transfer.contentLength,
                Download.STOP_REASON_NONE,
                Download.FAILURE_REASON_NONE,
                progress,
            ),
        )
    }

    private fun copyExactly(
        input: InputStream,
        sink: CacheDataSink,
        expectedLength: Long,
    ) {
        require(expectedLength >= 0L) { "Negative span length" }
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var remaining = expectedLength

        while (remaining > 0L) {
            val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            check(count >= 0) { "Span ended before declared length" }
            if (count == 0) continue
            sink.write(buffer, 0, count)
            remaining -= count
        }
    }
}
