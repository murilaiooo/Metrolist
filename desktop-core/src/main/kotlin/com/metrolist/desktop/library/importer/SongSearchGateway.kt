package com.metrolist.desktop.library.importer

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem

fun interface SongSearchGateway {
    suspend fun search(query: String): List<SongItem>
}

class YouTubeSongSearchGateway : SongSearchGateway {
    override suspend fun search(query: String): List<SongItem> =
        YouTube.search(
            query = query,
            filter = YouTube.SearchFilter.FILTER_SONG,
        ).getOrThrow()
            .items
            .filterIsInstance<SongItem>()
}
