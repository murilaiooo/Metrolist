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

    @Test
    fun exportUsesArtworkProviderForCompletedRecord() {
        val temp = Files.createTempDirectory("offline-export-artwork")
        val db = temp.resolve("offline.db")
        val media = temp.resolve("complete.media")
        Files.write(media, ByteArray(256) { 4 })
        val artwork = temp.resolve("artwork.img")
        Files.write(artwork, byteArrayOf(1, 3, 5, 7))
        val thumbnailUrl = "https://example.invalid/export-cover"

        OfflineStore(db).use { store ->
            store.upsert(
                OfflineRecord.queued(song("complete", thumbnailUrl), 1L).copy(
                    state = OfflineState.COMPLETED,
                    localPath = media.toString(),
                    bytesDownloaded = 256L,
                    contentLength = 256L,
                    format = FormatSnapshot(
                        itag = 251,
                        mimeType = "audio/webm",
                        codecs = "opus",
                        bitrate = 144_000,
                        sampleRate = 48_000,
                        contentLength = 256L,
                        loudnessDb = null,
                        perceptualLoudnessDb = null,
                    ),
                    downloadedAt = "2026-09-26T15:00:00",
                    updateTimeMs = 2L,
                ),
            )

            var requestedUrl: String? = null
            val output = temp.resolve("with-artwork.metrolistbackup")
            OfflineBackupService(
                store = store,
                artworkProvider = { url ->
                    requestedUrl = url
                    artwork
                },
            ).exportAll(output)

            assertEquals(thumbnailUrl, requestedUrl)
            ZipFile(output.toFile()).use { zip ->
                val expected = "artwork/" + SpanPlanner.artworkFileName(thumbnailUrl)
                assertTrue(zip.getEntry(expected) != null)
            }
        }
    }

    @Test
    fun estimatesCompletedAudioAndUniqueArtworkBeforeExport() {
        val temp = Files.createTempDirectory("offline-export-estimate")
        val db = temp.resolve("offline.db")
        val artwork = temp.resolve("shared-artwork.img")
        Files.write(artwork, ByteArray(33) { 9 })
        val thumbnailUrl = "https://example.invalid/shared-cover"

        OfflineStore(db).use { store ->
            listOf(
                "one" to 256,
                "two" to 512,
            ).forEachIndexed { index, (id, size) ->
                val media = temp.resolve("$id.media")
                Files.write(media, ByteArray(size) { index.toByte() })
                store.upsert(
                    OfflineRecord.queued(song(id, thumbnailUrl), index.toLong()).copy(
                        state = OfflineState.COMPLETED,
                        localPath = media.toString(),
                        bytesDownloaded = size.toLong(),
                        contentLength = size.toLong(),
                        format = FormatSnapshot(
                            itag = 251,
                            mimeType = "audio/webm",
                            codecs = "opus",
                            bitrate = 144_000,
                            sampleRate = 48_000,
                            contentLength = size.toLong(),
                            loudnessDb = null,
                            perceptualLoudnessDb = null,
                        ),
                        downloadedAt = "2026-09-26T15:00:00",
                        updateTimeMs = 2L,
                    ),
                )
            }

            val estimate =
                OfflineBackupService(
                    store = store,
                    artworkProvider = { artwork },
                ).estimateAll()

            assertEquals(2, estimate.songCount)
            assertEquals(768L, estimate.audioBytes)
            assertEquals(33L, estimate.artworkBytes)
            assertEquals(801L, estimate.estimatedContentBytes)
        }
    }

    private fun song(
        id: String,
        thumbnail: String = "",
    ) = SongItem(
        id = id,
        title = id,
        artists = emptyList(),
        thumbnail = thumbnail,
    )
}
