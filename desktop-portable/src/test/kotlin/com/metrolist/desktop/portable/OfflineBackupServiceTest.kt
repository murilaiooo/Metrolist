package com.metrolist.desktop.portable

import com.metrolist.desktop.offline.FormatSnapshot
import com.metrolist.desktop.offline.OfflineRecord
import com.metrolist.desktop.offline.OfflineState
import com.metrolist.desktop.offline.OfflineStore
import com.metrolist.innertube.models.SongItem
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OfflineBackupServiceTest {
    @Test
    fun exportsEveryAndOnlyCompletedOfflineRecord() {
        val temp = Path.of(System.getProperty("java.io.tmpdir"), "offline-export-service")
        Files.createDirectories(temp)
        val db = temp.resolve("offline.db")
        Files.deleteIfExists(db)

        val media = temp.resolve("complete.media")
        Files.write(media, ByteArray(1024) { (it % 251).toByte() })

        OfflineStore(db).use { store ->
            store.upsert(
                OfflineRecord.queued(song("complete"), 1L).copy(
                    state = OfflineState.COMPLETED,
                    localPath = media.toString(),
                    bytesDownloaded = 1024L,
                    contentLength = 1024L,
                    format = FormatSnapshot(
                        itag = 251,
                        mimeType = "audio/webm",
                        codecs = "opus",
                        bitrate = 144_000,
                        sampleRate = 48_000,
                        contentLength = 1024L,
                        loudnessDb = -6.0,
                        perceptualLoudnessDb = -13.0,
                    ),
                    downloadedAt = "2026-09-26T15:00:00",
                    updateTimeMs = 2L,
                ),
            )
            store.upsert(OfflineRecord.queued(song("queued"), 3L))

            val output = temp.resolve("all-offline.metrolistbackup")
            Files.deleteIfExists(output)

            val written = OfflineBackupService(store).exportAll(output)

            assertEquals(output, written)
            assertTrue(PortableValidator().validate(output).valid)

            ZipFile(output.toFile()).use { zip ->
                val manifest = zip.getInputStream(zip.getEntry("manifest.json"))
                    .bufferedReader()
                    .use { Json.decodeFromString<PortableManifest>(it.readText()) }

                assertEquals(1, manifest.exportedSongCount)
                assertEquals(listOf("complete"), manifest.songIds)
            }
        }
    }

    private fun song(id: String) = SongItem(
        id = id,
        title = id,
        artists = emptyList(),
        thumbnail = "",
    )
}
