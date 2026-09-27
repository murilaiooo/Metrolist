package com.metrolist.music.playback

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

internal fun isDirectLocalMediaUri(uri: Uri): Boolean =
    when (uri.scheme?.lowercase()) {
        "file",
        "content",
        "android.resource",
        -> true
        else -> false
    }

internal class PlaybackDataSourceRouterFactory(
    private val cachedFactory: DataSource.Factory,
    private val directFactory: DataSource.Factory,
) : DataSource.Factory {
    override fun createDataSource(): DataSource =
        PlaybackDataSourceRouter(
            cachedFactory = cachedFactory,
            directFactory = directFactory,
        )
}

private class PlaybackDataSourceRouter(
    private val cachedFactory: DataSource.Factory,
    private val directFactory: DataSource.Factory,
) : DataSource {
    private val transferListeners = mutableListOf<TransferListener>()
    private var delegate: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        transferListeners += transferListener
        delegate?.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        check(delegate == null) { "DataSource is already open" }

        val factory =
            if (isDirectLocalMediaUri(dataSpec.uri)) {
                directFactory
            } else {
                cachedFactory
            }

        val opened =
            factory.createDataSource().also { source ->
                transferListeners.forEach(source::addTransferListener)
            }
        delegate = opened
        return opened.open(dataSpec)
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int =
        requireNotNull(delegate) { "DataSource is not open" }
            .read(buffer, offset, length)

    override fun getUri(): Uri? = delegate?.uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        delegate?.responseHeaders.orEmpty()

    override fun close() {
        val current = delegate
        delegate = null
        current?.close()
    }
}