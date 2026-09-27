package com.metrolist.desktop.portable.carlibrary

import com.metrolist.desktop.portable.PortableItem
import com.metrolist.desktop.stream.ResolvedAudio
import com.metrolist.innertube.models.Album
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.SongItem
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class CarLibraryExporterTest {
    private val json = Json {
        ignoreUnknownKeys = false
        explicitNulls = true
    }

    @Test
    fun firstExportWritesFolderTreeArtworkPlaylistAndValidManifest() {
        val root = Files.createTempDirectory("car-library-first")
        val source = Files.createDirectories(root.resolve("source"))
        val destination = root.resolve("MetroList Car Library")
        val media = source.resolve("song-one.webm")
        val mediaBytes = ByteArray(4096) { (it % 251).toByte() }
        Files.write(media, mediaBytes)
        val artwork = source.resolve("cover.img")
        val artworkBytes = ByteArray(2048) { ((it * 7) % 251).toByte() }
        Files.write(artwork, artworkBytes)

        val result =
            exporter(ids = ArrayDeque(listOf("library-test", "rev-1")))
                .exportCarLibrary(
                    destination = destination,
                    selection =
                        CarLibraryExportSelection(
                            items = listOf(item("song-1", media, artwork)),
                            playlists =
                                listOf(
                                    CarLibraryPlaylist(
                                        id = "road",
                                        name = "Road",
                                        songIds = listOf("song-1"),
                                    ),
                                ),
                        ),
                )

        val manifest = result.manifest
        assertEquals("library-test", manifest.libraryId)
        assertEquals("rev-1", manifest.revision)
        assertEquals(listOf("song-1"), manifest.playlists.single().songIds)
        assertTrue(CarLibraryValidator().validate(manifest).valid)
        assertContentEquals(mediaBytes, Files.readAllBytes(destination.resolve(manifest.songs.single().mediaPath)))
        assertContentEquals(
            artworkBytes,
            Files.readAllBytes(destination.resolve(requireNotNull(manifest.songs.single().artworkPath))),
        )
        assertTrue(Files.isRegularFile(destination.resolve("library.json")))
        assertTrue(result.copiedFiles >= 2)
        assertEquals(0, result.reusedFiles)
    }

    @Test
    fun incrementalExportReusesUnchangedMediaAndPublishesNewRevisionLast() {
        val root = Files.createTempDirectory("car-library-incremental")
        val source = Files.createDirectories(root.resolve("source"))
        val destination = root.resolve("library")
        val mediaOne = source.resolve("one.webm")
        val mediaTwo = source.resolve("two.webm")
        Files.write(mediaOne, ByteArray(1024) { 1 })
        Files.write(mediaTwo, ByteArray(2048) { 2 })
        val artwork = source.resolve("cover.img")
        Files.write(artwork, byteArrayOf(1, 2, 3, 4))

        val ids = ArrayDeque(listOf("library-test", "rev-1", "rev-2"))
        val exporter = exporter(ids)
        val first =
            exporter.exportCarLibrary(
                destination,
                CarLibraryExportSelection(items = listOf(item("song-1", mediaOne, artwork))),
            )
        val firstSong = first.manifest.songs.single()
        val originalMediaPath = firstSong.mediaPath

        Files.write(artwork, byteArrayOf(9, 8, 7, 6, 5))
        val second =
            exporter.exportCarLibrary(
                destination,
                CarLibraryExportSelection(
                    items =
                        listOf(
                            item("song-1", mediaOne, artwork),
                            item("song-2", mediaTwo, null),
                        ),
                ),
            )

        assertEquals(first.manifest.libraryId, second.manifest.libraryId)
        assertNotEquals(first.manifest.revision, second.manifest.revision)
        assertEquals(originalMediaPath, second.manifest.songs.first { it.id == "song-1" }.mediaPath)
        assertTrue(second.reusedFiles >= 1)
        assertTrue(second.copiedFiles >= 2)
        val persisted = json.decodeFromString<CarLibraryManifest>(Files.readString(destination.resolve("library.json")))
        assertEquals(second.manifest.revision, persisted.revision)
    }

    @Test
    fun failureBeforeManifestPromotionLeavesPreviousRevisionAuthoritative() {
        val root = Files.createTempDirectory("car-library-interrupted")
        val source = Files.createDirectories(root.resolve("source"))
        val destination = root.resolve("library")
        val one = source.resolve("one.webm")
        val two = source.resolve("two.webm")
        Files.write(one, ByteArray(512) { 1 })
        Files.write(two, ByteArray(768) { 2 })

        val initial =
            exporter(ArrayDeque(listOf("library-test", "rev-1")))
                .exportCarLibrary(
                    destination,
                    CarLibraryExportSelection(items = listOf(item("song-1", one, null))),
                )
        val oldManifestText = Files.readString(destination.resolve("library.json"))

        val failing =
            CarLibraryExporter(
                producerVersion = "0.2.3",
                newIdentity = ArrayDeque(listOf("rev-2"))::removeFirst,
                beforeManifestPromotion = { throw IOException("injected failure") },
            )

        assertFailsWith<IOException> {
            failing.exportCarLibrary(
                destination,
                CarLibraryExportSelection(
                    items = listOf(item("song-1", one, null), item("song-2", two, null)),
                ),
            )
        }

        assertEquals(oldManifestText, Files.readString(destination.resolve("library.json")))
        initial.manifest.songs.forEach { song ->
            assertTrue(Files.isRegularFile(destination.resolve(song.mediaPath)))
        }
    }

    private fun exporter(ids: ArrayDeque<String>) =
        CarLibraryExporter(
            producerVersion = "0.2.3",
            newIdentity = ids::removeFirst,
        )

    private fun item(
        id: String,
        media: Path,
        artwork: Path?,
    ): PortableItem {
        val length = Files.size(media)
        return PortableItem(
            song =
                SongItem(
                    id = id,
                    title = "Title $id",
                    artists = listOf(Artist("Artist", "artist-1")),
                    album = Album("Album", "album-1"),
                    duration = 180,
                    thumbnail = "https://example.invalid/$id/1200",
                    explicit = id == "song-2",
                ),
            resolved =
                ResolvedAudio(
                    videoId = id,
                    url = "",
                    headers = emptyMap(),
                    clientName = "OFFLINE_EXPORT",
                    profileId = "OFFLINE_EXPORT",
                    expiresAtEpochMs = null,
                    requireBoundedRange = false,
                    rangeChunkSizeBytes = 0L,
                    useRangeChunks = false,
                    itag = 251,
                    mimeType = "audio/webm",
                    codecs = "opus",
                    bitrate = 160_000,
                    sampleRate = 48_000,
                    contentLength = length,
                    loudnessDb = null,
                    perceptualLoudnessDb = null,
                ),
            mediaPath = media,
            artworkPath = artwork,
            downloadedAt = LocalDateTime.parse("2026-09-27T12:00:00"),
            startTimeMs = 1L,
            updateTimeMs = 2L,
        )
    }
}
