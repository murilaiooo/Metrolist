package com.metrolist.music.car.importer

import com.metrolist.music.car.feedback.CarOperationProgress
import com.metrolist.music.car.feedback.CarOperationStage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarValidationProgressTest {
    @Test
    fun knownSizeReportsValidationBytesBeforeImportAndFinishesAtSourceSize() = runBlocking {
        val harness = Harness(knownSize = true)
        try {
            val events = mutableListOf<CarOperationProgress>()

            val result = harness.coordinator.runImport(harness.source, resume = false, onProgress = events::add)

            assertTrue(result is CarImportResult.Success)
            assertEquals(CarOperationStage.PREPARING, events.first().stage)
            val validating = events.filter { it.stage == CarOperationStage.VALIDATING }
            assertTrue(validating.isNotEmpty())
            assertTrue(validating.any { (it.bytesProcessed ?: 0L) > 0L })
            assertTrue(
                validating.zipWithNext().all { (left, right) ->
                    (left.bytesProcessed ?: 0L) <= (right.bytesProcessed ?: 0L)
                },
            )
            assertEquals(harness.source.sizeBytes, validating.last().bytesProcessed)
            assertEquals(harness.source.sizeBytes, validating.last().totalBytes)
            assertTrue(events.indexOfFirst { it.stage == CarOperationStage.VALIDATING } <
                events.indexOfFirst { it.stage == CarOperationStage.IMPORTING })
        } finally {
            harness.close()
        }
    }

    @Test
    fun unknownSizeStaysIndeterminateWhileValidationStillShowsActivity() = runBlocking {
        val harness = Harness(knownSize = false)
        try {
            val events = mutableListOf<CarOperationProgress>()

            val result = harness.coordinator.runImport(harness.source, resume = false, onProgress = events::add)

            assertTrue(result is CarImportResult.Success)
            val validating = events.filter { it.stage == CarOperationStage.VALIDATING }
            assertTrue(validating.size >= 2)
            assertTrue(validating.all { it.totalBytes == null })
            assertTrue(validating.any { (it.bytesProcessed ?: 0L) > 0L })
            assertNull(validating.first().fraction)
            assertTrue(validating.mapNotNull { it.detail }.distinct().size >= 2)
        } finally {
            harness.close()
        }
    }

    @Test
    fun successfulImportMovesThroughAllUserVisibleStagesInOrder() = runBlocking {
        val harness = Harness(knownSize = true)
        try {
            val events = mutableListOf<CarOperationProgress>()

            val result = harness.coordinator.runImport(harness.source, resume = false, onProgress = events::add)

            assertTrue(result is CarImportResult.Success)
            assertEquals(
                listOf(
                    CarOperationStage.PREPARING,
                    CarOperationStage.VALIDATING,
                    CarOperationStage.IMPORTING,
                    CarOperationStage.FINALIZING,
                ),
                events.map { it.stage }.distinct(),
            )
            assertEquals(CarImportPhase.COMPLETE, harness.journalStore.load()!!.phase)
        } finally {
            harness.close()
        }
    }

    private class Harness(knownSize: Boolean) {
        val root: File = Files.createTempDirectory("car-validation-progress").toFile()
        private val bytes = tinyBackup()
        val source = TestSource(bytes, if (knownSize) bytes.size.toLong() else null)
        private val state = FakeState()
        val journalStore = CarImportJournalStore(root)
        private var now = 0L
        val coordinator =
            CarImportCoordinator(
                validator = CarPortableArchiveValidator(),
                journalStore = journalStore,
                mediaTarget = FakeMedia(state),
                roomTarget = FakeRoom(state),
                artworkTarget = FakeArtwork(root),
                inspector = FakeInspector(state),
                availableDestinationBytes = { Long.MAX_VALUE },
                nowMillis = {
                    now += 300L
                    now
                },
            )

        fun close() {
            root.deleteRecursively()
        }
    }

    private class TestSource(
        private val bytes: ByteArray,
        override val sizeBytes: Long?,
    ) : CarImportSource {
        override val identity: String = "content://progress/test"
        override fun open(): InputStream = ByteArrayInputStream(bytes)
    }

    private data class FakeState(
        var cachePresent: Boolean = false,
        var cacheComplete: Boolean = false,
        var downloadPresent: Boolean = false,
        var downloadCompleted: Boolean = false,
        var roomPresent: Boolean = false,
        var roomComplete: Boolean = false,
        var roomDownloaded: Boolean = false,
    )

    private class FakeInspector(private val state: FakeState) : CarImportInspectorPort {
        override fun inspect(songId: String, contentLength: Long, artworkExpected: Boolean): CarLocalImportState =
            CarLocalImportState(
                cachePresent = state.cachePresent,
                cacheComplete = state.cacheComplete,
                downloadPresent = state.downloadPresent,
                downloadCompleted = state.downloadCompleted,
                roomPresent = state.roomPresent,
                roomComplete = state.roomComplete,
                roomDownloaded = state.roomDownloaded,
                roomDateDownload = null,
                artworkPresent = true,
            )
    }

    private class FakeMedia(private val state: FakeState) : CarMediaImportPort {
        override fun prepareResource(resource: CarPortableResource): Boolean = true
        override fun writeSpan(resource: CarPortableResource, span: CarPortableSpan, input: InputStream) {
            check(input.readBytes().size.toLong() == span.length)
            state.cachePresent = true
        }
        override fun finishResource(resource: CarPortableResource) {
            state.cachePresent = true
            state.cacheComplete = true
        }
        override fun putCompletedDownload(transfer: CarPortableTransfer) {
            state.downloadPresent = true
            state.downloadCompleted = true
        }
        override fun removeResource(songId: String) = Unit
        override fun removeDownload(songId: String) = Unit
    }

    private class FakeRoom(private val state: FakeState) : CarRoomImportPort {
        override suspend fun upsertSongBundle(
            song: CarPortableSong,
            artists: List<CarPortableArtist>,
            maps: List<CarPortableMap>,
            format: CarPortableFormat,
        ) {
            state.roomPresent = true
            state.roomComplete = true
            state.roomDownloaded = true
        }
        override suspend fun removeSongBundle(songId: String) = Unit
        override suspend fun restoreDownloadState(songId: String, isDownloaded: Boolean, dateDownload: String?) = Unit
    }

    private class FakeArtwork(private val root: File) : CarArtworkImportPort {
        override fun write(songId: String, input: InputStream): File =
            root.resolve("$songId.img").apply { writeBytes(input.readBytes()) }
        override fun copy(sourceSongId: String, targetSongId: String): File =
            root.resolve(sourceSongId + ".img").copyTo(root.resolve(targetSongId + ".img"), overwrite = true)
        override fun delete(songId: String): Boolean = root.resolve("$songId.img").delete()
    }

    companion object {
        private fun tinyBackup(): ByteArray {
            val spanPath = "media3_cache/files/abc/0_4_0.span"
            val span = byteArrayOf(1, 2, 3, 4)
            val spanSha = sha256(span)
            val metadata =
                linkedMapOf(
                    "metadata/songs.json" to
                        """{"songs":[{"id":"song1","title":"Song 1","duration":123,"thumbnailUrl":null,"thumbnailArtworkPath":null,"albumId":null,"albumName":null,"explicit":false,"year":null,"date":null,"dateModified":null,"dateDownload":"2026-09-27T00:00:00","isVideo":false,"isEpisode":false}]}""".toByteArray(),
                    "metadata/artists.json" to
                        """{"artists":[{"id":"artist1","name":"Artist 1","thumbnailUrl":null,"channelId":null,"lastUpdateTime":"2026-09-27T00:00:00","isPodcastChannel":false}]}""".toByteArray(),
                    "metadata/albums.json" to """{"albums":[]}""".toByteArray(),
                    "metadata/song_artist_map.json" to
                        """{"maps":[{"songId":"song1","artistId":"artist1","position":0}]}""".toByteArray(),
                    "metadata/song_album_map.json" to """{"maps":[]}""".toByteArray(),
                    "metadata/formats.json" to
                        """{"formats":[{"id":"song1","itag":251,"mimeType":"audio/webm","codecs":"opus","bitrate":128000,"sampleRate":48000,"contentLength":4,"loudnessDb":null,"perceptualLoudnessDb":null}]}""".toByteArray(),
                    "metadata/downloads.json" to
                        """{"downloads":[{"requestId":"song1","uri":"song1","customCacheKey":"song1","title":"Song 1","contentLength":4,"startTimeMs":1,"updateTimeMs":2,"bytesDownloaded":4}]}""".toByteArray(),
                )

            val fileJson =
                (
                    metadata.map { (path, bytes) ->
                        fileJson(path, bytes.size.toLong(), sha256(bytes), "metadata")
                    } +
                        fileJson(spanPath, span.size.toLong(), spanSha, "media3_cache_span")
                ).joinToString(",")

            val manifest =
                """
                {
                  "appName":"Metrolist",
                  "backupFormatVersion":1,
                  "createdAt":"2026-09-27T00:00:00",
                  "sourceAppVersion":"13.6.0",
                  "sourceDatabaseVersion":38,
                  "exportedSongCount":1,
                  "exportedDownloadCount":1,
                  "totalBackupBytes":null,
                  "totalCacheBytes":4,
                  "totalArtworkBytes":0,
                  "type":"offline_downloads",
                  "cacheStrategy":"media3_simple_cache_spans",
                  "songIds":["song1"],
                  "media3DownloadRequestIds":["song1"],
                  "exportedCacheResources":[{"songId":"song1","cacheKey":"song1","contentLength":4,"spans":[{"relativePath":"$spanPath","cacheKey":"song1","position":0,"length":4,"sha256":"$spanSha"}]}],
                  "files":[$fileJson],
                  "skippedSongs":[],
                  "warnings":[],
                  "errors":[]
                }
                """.trimIndent().toByteArray()

            return ByteArrayOutputStream().use { output ->
                ZipOutputStream(output).use { zip ->
                    zip.setLevel(Deflater.NO_COMPRESSION)
                    put(zip, "manifest.json", manifest)
                    metadata.forEach { (path, bytes) -> put(zip, path, bytes) }
                    put(zip, spanPath, span)
                }
                output.toByteArray()
            }
        }

        private fun fileJson(path: String, size: Long, sha: String, kind: String) =
            """{"relativePath":"$path","size":$size,"sha256":"$sha","kind":"$kind"}"""

        private fun put(zip: ZipOutputStream, name: String, bytes: ByteArray) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(bytes)
            zip.closeEntry()
        }

        private fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256")
                .digest(bytes)
                .joinToString("") { "%02x".format(it) }
    }
}
