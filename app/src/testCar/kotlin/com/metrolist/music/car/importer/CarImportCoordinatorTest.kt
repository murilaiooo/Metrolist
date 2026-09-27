package com.metrolist.music.car.importer

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarImportCoordinatorTest {
    @Test
    fun resumeRepairsDownloadAfterFailureWithoutRewritingCache() = runBlocking {
        val harness = Harness()
        try {
            harness.media.failOnPutDownload = true
            val first = harness.coordinator.runImport(harness.source, resume = false)

            assertTrue(first is CarImportResult.Failure)
            assertTrue(harness.layers.cacheComplete)
            assertFalse(harness.layers.downloadCompleted)
            assertFalse(harness.layers.roomComplete)
            assertEquals(1, harness.media.writeSpanCalls)
            assertTrue(harness.journalStore.load() != null)

            harness.media.failOnPutDownload = false
            val resumed = harness.coordinator.runImport(harness.source, resume = true)

            assertTrue(resumed is CarImportResult.Success)
            assertTrue(harness.layers.cacheComplete)
            assertTrue(harness.layers.downloadCompleted)
            assertTrue(harness.layers.roomComplete)
            assertEquals(1, harness.media.writeSpanCalls)
            assertEquals(2, harness.media.putDownloadCalls)
            assertEquals(CarImportPhase.COMPLETE, harness.journalStore.load()!!.phase)
        } finally {
            harness.close()
        }
    }

    @Test
    fun resumeRepairsRoomAfterFailureWithoutRewritingMediaLayers() = runBlocking {
        val harness = Harness()
        try {
            harness.room.failNext = true
            val first = harness.coordinator.runImport(harness.source, resume = false)

            assertTrue(first is CarImportResult.Failure)
            assertTrue(harness.layers.cacheComplete)
            assertTrue(harness.layers.downloadCompleted)
            assertFalse(harness.layers.roomComplete)
            assertEquals(1, harness.media.writeSpanCalls)
            assertEquals(1, harness.media.putDownloadCalls)
            harness.room.failNext = false
            val resumed = harness.coordinator.runImport(harness.source, resume = true)

            assertTrue(resumed is CarImportResult.Success)
            assertTrue(harness.layers.roomComplete)
            assertEquals(1, harness.media.writeSpanCalls)
            assertEquals(1, harness.media.putDownloadCalls)
            assertEquals(2, harness.room.upsertCalls)
        } finally {
            harness.close()
        }
    }

    @Test
    fun fullyCompleteSongIsNotRewrittenOnFreshImport() = runBlocking {
        val harness =
            Harness(
                initial =
                    FakeLayers(
                        cachePresent = true,
                        cacheComplete = true,
                        downloadPresent = true,
                        downloadCompleted = true,
                        roomPresent = true,
                        roomComplete = true,
                        roomDownloaded = true,
                    ),
                availableDestinationBytes = 0L,
            )
        try {
            val progress = mutableListOf<CarImportProgress>()
            val result =
                harness.coordinator.runImport(
                    harness.source,
                    resume = false,
                    onProgress = progress::add,
                )

            assertTrue("Unexpected result: $result", result is CarImportResult.Success)
            assertEquals(0, harness.media.writeSpanCalls)
            assertEquals(0, harness.media.putDownloadCalls)
            assertEquals(0, harness.room.upsertCalls)
            assertTrue(progress.isNotEmpty())
            assertTrue("Unexpected progress: $progress", progress.all { it.totalBytes == 0L })
            assertEquals(CarImportPhase.COMPLETE, harness.journalStore.load()!!.phase)
        } finally {
            harness.close()
        }
    }

    @Test
    fun unavailablePersistedSourceFailsBeforeAnyResumeMutation() = runBlocking {
        val harness = Harness()
        try {
            harness.media.failOnPutDownload = true
            harness.coordinator.runImport(harness.source, resume = false)

            val before = harness.layers.copy()
            val mediaWritesBefore = harness.media.writeSpanCalls
            val roomWritesBefore = harness.room.upsertCalls
            harness.source.available = false

            val resumed = harness.coordinator.runImport(harness.source, resume = true)

            assertTrue(resumed is CarImportResult.Failure)
            assertEquals(CarImportPhase.VALIDATING, (resumed as CarImportResult.Failure).phase)
            assertEquals(before, harness.layers)
            assertEquals(mediaWritesBefore, harness.media.writeSpanCalls)
            assertEquals(roomWritesBefore, harness.room.upsertCalls)
        } finally {
            harness.close()
        }
    }
    @Test
    fun discardIncompleteRemovesOnlyNewArtifactsAndPreservesExistingRoomSong() = runBlocking {
        val harness =
            Harness(
                initial =
                    FakeLayers(
                        roomPresent = true,
                        roomDownloaded = false,
                    ),
            )
        try {
            harness.media.failOnPutDownload = true
            harness.coordinator.runImport(harness.source, resume = false)

            assertTrue(harness.layers.cachePresent)
            assertTrue(harness.layers.roomPresent)

            val result = harness.coordinator.discardIncomplete()

            assertTrue(result is CarImportResult.Discarded)
            assertFalse(harness.layers.cachePresent)
            assertFalse(harness.layers.downloadPresent)
            assertTrue(harness.layers.roomPresent)
            assertFalse(harness.layers.roomDownloaded)
            assertEquals(1, harness.media.removeResourceCalls)
            assertEquals(0, harness.room.removeCalls)
            assertEquals(null, harness.journalStore.load())
        } finally {
            harness.close()
        }
    }

    private class Harness(
        initial: FakeLayers = FakeLayers(),
        availableDestinationBytes: Long = Long.MAX_VALUE,
    ) {
        val root: File = Files.createTempDirectory("car-import-coordinator").toFile()
        val source = ByteArrayCarImportSource("content://backup/one", tinyBackup())
        val layers = initial.copy()
        val media = FakeMediaPort(layers)
        val room = FakeRoomPort(layers)
        val artwork = FakeArtworkPort(root, layers)
        val inspector = FakeInspector(layers)
        val journalStore = CarImportJournalStore(root)
        val coordinator =
            CarImportCoordinator(
                validator = CarPortableArchiveValidator(),
                journalStore = journalStore,
                mediaTarget = media,
                roomTarget = room,
                artworkTarget = artwork,
                inspector = inspector,
                availableDestinationBytes = { availableDestinationBytes },
            )

        fun close() {
            root.deleteRecursively()
        }
    }

    private data class FakeLayers(
        var cachePresent: Boolean = false,
        var cacheComplete: Boolean = false,
        var downloadPresent: Boolean = false,
        var downloadCompleted: Boolean = false,
        var roomPresent: Boolean = false,
        var roomComplete: Boolean = false,
        var roomDownloaded: Boolean = false,
        var roomDateDownload: String? = null,
        var artworkPresent: Boolean = false,
    )
    private class ByteArrayCarImportSource(
        override val identity: String,
        private val bytes: ByteArray,
    ) : CarImportSource {
        var available = true

        override fun open(): InputStream {
            if (!available) throw FileNotFoundException("Persisted URI is unavailable")
            return ByteArrayInputStream(bytes)
        }
    }

    private class FakeInspector(
        private val layers: FakeLayers,
    ) : CarImportInspectorPort {
        override fun inspect(
            songId: String,
            contentLength: Long,
            artworkExpected: Boolean,
        ): CarLocalImportState =
            CarLocalImportState(
                cachePresent = layers.cachePresent,
                cacheComplete = layers.cacheComplete,
                downloadPresent = layers.downloadPresent,
                downloadCompleted = layers.downloadCompleted,
                roomPresent = layers.roomPresent,
                roomComplete = layers.roomComplete,
                roomDownloaded = layers.roomDownloaded,
                roomDateDownload = layers.roomDateDownload,
                artworkPresent = !artworkExpected || layers.artworkPresent,
            )
    }
    private class FakeMediaPort(
        private val layers: FakeLayers,
    ) : CarMediaImportPort {
        var failOnPutDownload = false
        var writeSpanCalls = 0
        var putDownloadCalls = 0
        var removeResourceCalls = 0

        override fun prepareResource(resource: CarPortableResource): Boolean {
            if (layers.cacheComplete) return false
            layers.cachePresent = false
            layers.cacheComplete = false
            return true
        }

        override fun writeSpan(
            resource: CarPortableResource,
            span: CarPortableSpan,
            input: InputStream,
        ) {
            writeSpanCalls++
            val bytes = input.readBytes()
            check(bytes.size.toLong() == span.length)
            layers.cachePresent = true
        }

        override fun finishResource(resource: CarPortableResource) {
            layers.cachePresent = true
            layers.cacheComplete = true
        }
        override fun putCompletedDownload(transfer: CarPortableTransfer) {
            putDownloadCalls++
            if (failOnPutDownload) error("Injected failure after cache")
            layers.downloadPresent = true
            layers.downloadCompleted = true
        }

        override fun removeResource(songId: String) {
            removeResourceCalls++
            layers.cachePresent = false
            layers.cacheComplete = false
        }

        override fun removeDownload(songId: String) {
            layers.downloadPresent = false
            layers.downloadCompleted = false
        }
    }

    private class FakeRoomPort(
        private val layers: FakeLayers,
    ) : CarRoomImportPort {
        var failNext = false
        var upsertCalls = 0
        var removeCalls = 0

        override suspend fun upsertSongBundle(
            song: CarPortableSong,
            artists: List<CarPortableArtist>,
            maps: List<CarPortableMap>,
            format: CarPortableFormat,
        ) {
            upsertCalls++
            if (failNext) error("Injected failure before Room commit")
            layers.roomPresent = true
            layers.roomComplete = true
            layers.roomDownloaded = true
            layers.roomDateDownload = song.dateDownload
        }
        override suspend fun removeSongBundle(songId: String) {
            removeCalls++
            layers.roomPresent = false
            layers.roomComplete = false
            layers.roomDownloaded = false
            layers.roomDateDownload = null
        }

        override suspend fun restoreDownloadState(
            songId: String,
            isDownloaded: Boolean,
            dateDownload: String?,
        ) {
            layers.roomDownloaded = isDownloaded
            layers.roomDateDownload = dateDownload
            if (!isDownloaded) layers.roomComplete = false
        }
    }

    private class FakeArtworkPort(
        private val root: File,
        private val layers: FakeLayers,
    ) : CarArtworkImportPort {
        override fun write(songId: String, input: InputStream): File {
            val file = root.resolve("$songId.img")
            file.writeBytes(input.readBytes())
            layers.artworkPresent = true
            return file
        }

        override fun copy(
            sourceSongId: String,
            targetSongId: String,
        ): File {
            val source = root.resolve("$sourceSongId.img")
            val target = root.resolve("$targetSongId.img")
            source.copyTo(target, overwrite = true)
            layers.artworkPresent = true
            return target
        }
        override fun delete(songId: String): Boolean {
            layers.artworkPresent = false
            return root.resolve("$songId.img").let { !it.exists() || it.delete() }
        }
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
