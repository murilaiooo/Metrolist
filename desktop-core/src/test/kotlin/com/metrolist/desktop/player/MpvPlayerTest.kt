package com.metrolist.desktop.player

import com.metrolist.desktop.offline.OfflineRecord
import com.metrolist.desktop.offline.OfflineState
import com.metrolist.desktop.offline.OfflineStore
import com.metrolist.desktop.stream.ResolvedAudio
import com.metrolist.innertube.models.SongItem
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MpvPlayerTest {
    @Test
    fun playsLocalFileAndControlsBackend() {
        runBlocking {
            val fixture = fixture("local")
            val media = fixture.dir.resolve("song.media")
            Files.write(media, ByteArray(8) { 1 })
            fixture.store.upsert(
                OfflineRecord.queued(fixture.song, 1L).copy(
                    state = OfflineState.COMPLETED,
                    localPath = media.toString(),
                    bytesDownloaded = 8L,
                    contentLength = 8L,
                    updateTimeMs = 2L,
                ),
            )

            val backend = FakeMpvBackend()
            val player = MpvPlayer(
                sourceSelector = PlayerSourceSelector(
                    fixture.store,
                    resolver = { error("network should not be used") },
                ),
                backend = backend,
            )

            player.play(fixture.song)
            player.pause()
            player.seek(12_500)
            player.setVolume(42)
            player.resume()

            assertEquals(media.toString(), backend.loadedSource)
            assertTrue(backend.loadedHeaders.isEmpty())
            assertFalse(backend.pausedState)
            assertEquals(12.5, backend.seekSeconds)
            assertEquals(42, backend.volumeLevel)
            assertEquals(fixture.song.id, player.state.value.currentSong?.id)

            player.close()
            fixture.store.close()
        }
    }

    @Test
    fun passesResolvedOnlineHeadersToBackend() {
        runBlocking {
            val fixture = fixture("remote")
            val backend = FakeMpvBackend()
            val resolved = resolved()

            val player = MpvPlayer(
                sourceSelector = PlayerSourceSelector(
                    fixture.store,
                    resolver = { resolved },
                ),
                backend = backend,
            )

            player.play(fixture.song)

            assertEquals(resolved.url, backend.loadedSource)
            assertEquals(resolved.headers, backend.loadedHeaders)

            player.close()
            fixture.store.close()
        }
    }

    private fun fixture(name: String): Fixture {
        val dir = Path.of(System.getProperty("java.io.tmpdir"), "mpv-player-$name")
        Files.createDirectories(dir)
        val db = dir.resolve("offline.db")
        Files.deleteIfExists(db)
        return Fixture(
            dir = dir,
            store = OfflineStore(db),
            song = SongItem(
                id = "song-1",
                title = "Song",
                artists = emptyList(),
                thumbnail = "",
            ),
        )
    }

    private fun resolved() =
        ResolvedAudio(
            videoId = "song-1",
            url = "https://redacted.invalid/media",
            headers = mapOf("User-Agent" to "MetroList"),
            clientName = "TEST",
            profileId = "TEST",
            expiresAtEpochMs = null,
            requireBoundedRange = false,
            rangeChunkSizeBytes = 1_048_576,
            useRangeChunks = false,
            itag = 251,
            mimeType = "audio/webm",
            codecs = "opus",
            bitrate = 144_000,
            sampleRate = 48_000,
            contentLength = 8L,
            loudnessDb = null,
            perceptualLoudnessDb = null,
        )

    private data class Fixture(
        val dir: Path,
        val store: OfflineStore,
        val song: SongItem,
    )

    private class FakeMpvBackend : MpvBackend {
        var loadedSource: String? = null
        var loadedHeaders: Map<String, String> = emptyMap()
        var pausedState = false
        var seekSeconds = 0.0
        var volumeLevel = 100

        override fun load(source: String, headers: Map<String, String>) {
            loadedSource = source
            loadedHeaders = headers
        }

        override fun setPaused(paused: Boolean) {
            this.pausedState = paused
        }

        override fun seek(seconds: Double) {
            seekSeconds = seconds
        }

        override fun setVolume(volume: Int) {
            this.volumeLevel = volume
        }

        override fun property(name: String): String? =
            when (name) {
                "pause" -> if (pausedState) "yes" else "no"
                "volume" -> volumeLevel.toString()
                else -> null
            }

        override fun stop() = Unit
        override fun close() = Unit
    }
}
