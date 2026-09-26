package com.metrolist.desktop.offline

import com.metrolist.desktop.media.ExpiredMediaUrlException
import com.metrolist.desktop.media.WrittenMedia
import com.metrolist.desktop.stream.ResolvedAudio
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime

class MetroListOfflineWorker(
    private val mediaDir: Path,
    private val resolver: suspend (String) -> ResolvedAudio,
    private val fetcher: (ResolvedAudio, Path) -> WrittenMedia,
) {
    suspend fun transfer(record: OfflineRecord): OfflineRecord {
        Files.createDirectories(mediaDir)
        val destination = mediaDir.resolve("${record.videoId}.media")

        var lastExpired: ExpiredMediaUrlException? = null

        repeat(2) { attempt ->
            val resolved = resolver(record.videoId)

            try {
                val written = fetcher(resolved, destination)
                val contentLength = resolved.contentLength ?: written.bytes

                check(written.bytes == contentLength) {
                    "Offline media length mismatch"
                }

                return record.copy(
                    state = OfflineState.COMPLETED,
                    localPath = written.path.toString(),
                    bytesDownloaded = written.bytes,
                    contentLength = contentLength,
                    format =
                        FormatSnapshot(
                            itag = resolved.itag,
                            mimeType = resolved.mimeType,
                            codecs = resolved.codecs,
                            bitrate = resolved.bitrate,
                            sampleRate = resolved.sampleRate,
                            contentLength = contentLength,
                            loudnessDb = resolved.loudnessDb,
                            perceptualLoudnessDb = resolved.perceptualLoudnessDb,
                        ),
                    downloadedAt = LocalDateTime.now().toString(),
                    updateTimeMs = System.currentTimeMillis(),
                    lastError = null,
                )
            } catch (expired: ExpiredMediaUrlException) {
                lastExpired = expired
                if (attempt == 1) throw expired
            }
        }

        throw requireNotNull(lastExpired)
    }
}
