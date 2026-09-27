package com.metrolist.music.car.library

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarLibraryContractTest {
    private val json =
        Json {
            ignoreUnknownKeys = false
            explicitNulls = true
        }
    private val validator = CarLibraryValidator(json)

    @Test
    fun sharedOneSongFixtureIsValidAndAllowsMissingArtwork() {
        val result = validator.parseAndValidate(fixture("minimal-one-song.json"))

        assertTrue(result.errors.toString(), result.valid)
        val manifest = requireNotNull(result.manifest)
        assertEquals(1, manifest.formatVersion)
        assertEquals("library-1", manifest.libraryId)
        assertNull(manifest.songs.single().artworkPath)
    }

    @Test
    fun sharedPlaylistFixturePreservesSongOrdering() {
        val result = validator.parseAndValidate(fixture("playlist-two-song.json"))

        assertTrue(result.errors.toString(), result.valid)
        assertEquals(
            listOf("song-2", "song-1"),
            requireNotNull(result.manifest).playlists.single().songIds,
        )
    }

    @Test
    fun rejectsTraversalPath() {
        val source = manifest()
        val invalid =
            source.copy(
                songs = source.songs.map { it.copy(mediaPath = "media/../escape.m4a") },
            )

        assertInvalid(invalid, "Unsafe media path")
    }

    @Test
    fun rejectsDuplicateSongIds() {
        val source = manifest()
        assertInvalid(source.copy(songs = source.songs + source.songs.single()), "Duplicate song IDs")
    }

    @Test
    fun rejectsDuplicateRelativeMediaPaths() {
        val source = manifest("playlist-two-song.json")
        val firstPath = source.songs.first().mediaPath
        val invalid =
            source.copy(
                songs =
                    source.songs.mapIndexed { index, song ->
                        if (index == 1) song.copy(mediaPath = firstPath) else song
                    },
            )

        assertInvalid(invalid, "Duplicate media paths")
    }

    @Test
    fun rejectsUnsupportedFormatVersion() {
        assertInvalid(manifest().copy(formatVersion = 2), "Unsupported format version")
    }

    @Test
    fun rejectsUnsafeFilename() {
        val source = manifest()
        val invalid =
            source.copy(
                songs = source.songs.map { it.copy(mediaPath = "media/song?.m4a") },
            )

        assertInvalid(invalid, "Unsafe media path")
    }

    private fun manifest(name: String = "minimal-one-song.json"): CarLibraryManifest =
        json.decodeFromString(fixture(name))

    private fun assertInvalid(
        manifest: CarLibraryManifest,
        expected: String,
    ) {
        val result = validator.validate(manifest)
        assertTrue(!result.valid)
        assertTrue(result.errors.toString(), result.errors.any { it.contains(expected) })
    }

    private fun fixture(name: String): String {
        val relative = Path.of("test-fixtures", "car-library", "v1", name)
        val candidates = listOf(relative, Path.of("..").resolve(relative))
        val path = candidates.firstOrNull(Files::isRegularFile)
            ?: error("Fixture not found: $name")
        return Files.readString(path)
    }
}
