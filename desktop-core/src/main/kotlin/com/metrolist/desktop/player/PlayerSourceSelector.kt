package com.metrolist.desktop.player

import com.metrolist.desktop.offline.FormatSnapshot
import com.metrolist.desktop.offline.OfflineState
import com.metrolist.desktop.offline.OfflineStore
import com.metrolist.desktop.stream.ResolvedAudio
import com.metrolist.innertube.models.SongItem
import java.nio.file.Files
import java.nio.file.Path

sealed interface PlayerSource {
    data class Local(
        val path: Path,
        val format: FormatSnapshot?,
    ) : PlayerSource

    data class Remote(
        val resolved: ResolvedAudio,
    ) : PlayerSource
}

class OfflineMediaUnavailableException :
    IllegalStateException("Song is not available offline")

class PlayerSourceSelector(
    private val store: OfflineStore,
    private val resolver: suspend (String) -> ResolvedAudio,
) {
    suspend fun select(
        song: SongItem,
        offlineOnly: Boolean = false,
    ): PlayerSource {
        val record = store.get(song.id)

        if (record?.state == OfflineState.COMPLETED) {
            val localPath = record.localPath?.let(Path::of)
            if (localPath != null && Files.exists(localPath)) {
                val expectedLength = record.contentLength
                if (expectedLength == null || Files.size(localPath) == expectedLength) {
                    return PlayerSource.Local(localPath, record.format)
                }
            }
        }

        if (offlineOnly) {
            throw OfflineMediaUnavailableException()
        }

        return PlayerSource.Remote(resolver(song.id))
    }
}
