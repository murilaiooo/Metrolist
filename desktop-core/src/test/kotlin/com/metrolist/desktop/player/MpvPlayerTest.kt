package com.metrolist.desktop.player

import com.metrolist.desktop.offline.FormatSnapshot
import com.metrolist.desktop.offline.OfflineRecord
import com.metrolist.desktop.offline.OfflineState
import com.metrolist.desktop.offline.OfflineStore
import com.metrolist.desktop.stream.ResolvedAudio
import com.metrolist.innertube.models.SongItem
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
                    format = formatSnapshot(),
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
            assertEquals(251, player.state.value.audioFormat?.itag)
            assertEquals("audio/webm", player.state.value.audioFormat?.mimeType)
            assertEquals("opus", player.state.value.audioFormat?.codecs)
            assertEquals(144_000, player.state.value.audioFormat?.bitrate)
            assertEquals(48_000, player.state.value.audioFormat?.sampleRate)

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
            assertEquals(251, player.state.value.audioFormat?.itag)
            assertEquals("audio/webm", player.state.value.audioFormat?.mimeType)
            assertEquals("opus", player.state.value.audioFormat?.codecs)
            assertEquals(144_000, player.state.value.audioFormat?.bitrate)
            assertEquals(48_000, player.state.value.audioFormat?.sampleRate)

            player.close()
            fixture.store.close()
        }
    }

    @Test
    fun queueSupportsNextPreviousAndMetroListRestartBehavior() {
        runBlocking {
            val fixture = fixture("queue")
            val songs = listOf(
                fixture.song,
                fixture.song.copy(id = "song-2", title = "Song Two"),
                fixture.song.copy(id = "song-3", title = "Song Three"),
            )
            val backend = FakeMpvBackend()
            val player = MpvPlayer(
                sourceSelector = PlayerSourceSelector(
                    fixture.store,
                    resolver = { videoId -> resolved(videoId) },
                ),
                backend = backend,
            )

            player.playQueue(songs, startIndex = 1)
            assertEquals("song-2", player.state.value.currentSong?.id)
            assertEquals(1, player.state.value.queueIndex)
            assertEquals(3, player.state.value.queue.size)

            player.next()
            assertEquals("song-3", player.state.value.currentSong?.id)
            assertEquals(2, player.state.value.queueIndex)

            player.seek(4_000)
            val loadCount = backend.loadedSources.size
            player.previous()
            assertEquals("song-3", player.state.value.currentSong?.id)
            assertEquals(loadCount, backend.loadedSources.size)
            assertEquals(0.0, backend.seekSeconds)

            player.seek(1_000)
            player.previous()
            assertEquals("song-2", player.state.value.currentSong?.id)
            assertEquals(1, player.state.value.queueIndex)

            player.close()
            fixture.store.close()
        }
    }

    @Test
    fun playNextAndAddToQueueDoNotReloadCurrentTrack() {
        runBlocking {
            val fixture = fixture("queue-insert")
            val songs = listOf(
                fixture.song,
                fixture.song.copy(id = "song-2", title = "Song Two"),
            )
            val backend = FakeMpvBackend()
            val player = MpvPlayer(
                sourceSelector = PlayerSourceSelector(
                    fixture.store,
                    resolver = { videoId -> resolved(videoId) },
                ),
                backend = backend,
            )

            player.playQueue(songs)
            val loadCount = backend.loadedSources.size

            player.playNext(fixture.song.copy(id = "next", title = "Play Next"))
            assertEquals(listOf("song-1", "next", "song-2"), player.state.value.queue.map { it.id })
            assertEquals("song-1", player.state.value.currentSong?.id)
            assertEquals(0, player.state.value.queueIndex)
            assertEquals(loadCount, backend.loadedSources.size)

            player.addToQueue(fixture.song.copy(id = "last", title = "Last"))
            assertEquals(listOf("song-1", "next", "song-2", "last"), player.state.value.queue.map { it.id })
            assertEquals(loadCount, backend.loadedSources.size)

            player.close()
            fixture.store.close()
        }
    }

    @Test
    fun moveAndRemoveAroundCurrentAdjustIndexWithoutReloadingCurrent() {
        runBlocking {
            val fixture = fixture("queue-mutate")
            val songs = listOf(
                fixture.song,
                fixture.song.copy(id = "song-2", title = "Song Two"),
                fixture.song.copy(id = "song-3", title = "Song Three"),
                fixture.song.copy(id = "song-4", title = "Song Four"),
            )
            val backend = FakeMpvBackend()
            val player = MpvPlayer(
                sourceSelector = PlayerSourceSelector(
                    fixture.store,
                    resolver = { videoId -> resolved(videoId) },
                ),
                backend = backend,
            )

            player.playQueue(songs, startIndex = 1)
            val loadCount = backend.loadedSources.size

            player.moveQueueItem(fromIndex = 3, toIndex = 0)
            assertEquals(listOf("song-4", "song-1", "song-2", "song-3"), player.state.value.queue.map { it.id })
            assertEquals(2, player.state.value.queueIndex)
            assertEquals("song-2", player.state.value.currentSong?.id)
            assertEquals(loadCount, backend.loadedSources.size)

            player.removeQueueItem(0)
            assertEquals(listOf("song-1", "song-2", "song-3"), player.state.value.queue.map { it.id })
            assertEquals(1, player.state.value.queueIndex)
            assertEquals("song-2", player.state.value.currentSong?.id)
            assertEquals(loadCount, backend.loadedSources.size)

            assertFailsWith<IllegalArgumentException> {
                runBlocking { player.removeQueueItem(player.state.value.queueIndex) }
            }

            player.close()
            fixture.store.close()
        }
    }

    @Test
    fun clearUpcomingKeepsCurrentAndHistoryWithoutReloading() {
        runBlocking {
            val fixture = fixture("queue-clear")
            val songs = listOf(
                fixture.song,
                fixture.song.copy(id = "song-2", title = "Song Two"),
                fixture.song.copy(id = "song-3", title = "Song Three"),
            )
            val backend = FakeMpvBackend()
            val player = MpvPlayer(
                sourceSelector = PlayerSourceSelector(
                    fixture.store,
                    resolver = { videoId -> resolved(videoId) },
                ),
                backend = backend,
            )

            player.playQueue(songs, startIndex = 1)
            val loadCount = backend.loadedSources.size

            player.clearUpcoming()

            assertEquals(listOf("song-1", "song-2"), player.state.value.queue.map { it.id })
            assertEquals(1, player.state.value.queueIndex)
            assertEquals("song-2", player.state.value.currentSong?.id)
            assertFalse(player.state.value.canSkipNext)
            assertEquals(loadCount, backend.loadedSources.size)

            player.close()
            fixture.store.close()
        }
    }

    @Test
    fun playNextRemainsNextWhenShuffleIsEnabled() {
        runBlocking {
            val fixture = fixture("queue-shuffle-next")
            val songs = listOf(
                fixture.song,
                fixture.song.copy(id = "song-2", title = "Song Two"),
                fixture.song.copy(id = "song-3", title = "Song Three"),
            )
            val player = MpvPlayer(
                sourceSelector = PlayerSourceSelector(
                    fixture.store,
                    resolver = { videoId -> resolved(videoId) },
                ),
                backend = FakeMpvBackend(),
                shuffleIndices = { indices -> indices.reversed() },
            )

            player.playQueue(songs)
            player.toggleShuffle()
            player.playNext(fixture.song.copy(id = "priority", title = "Priority"))
            player.next()

            assertEquals("priority", player.state.value.currentSong?.id)
            assertTrue(player.state.value.shuffleEnabled)

            player.close()
            fixture.store.close()
        }
    }

    @Test
    fun advancesAutomaticallyWhenCurrentTrackEnds() {
        runBlocking {
            val fixture = fixture("auto-next")
            val songs = listOf(
                fixture.song,
                fixture.song.copy(id = "song-2", title = "Song Two"),
            )
            val backend = FakeMpvBackend()
            val player = MpvPlayer(
                sourceSelector = PlayerSourceSelector(
                    fixture.store,
                    resolver = { videoId -> resolved(videoId) },
                ),
                backend = backend,
            )

            player.playQueue(songs)
            backend.eofReached = true
            delay(600)

            assertEquals("song-2", player.state.value.currentSong?.id)
            assertEquals(1, player.state.value.queueIndex)
            assertEquals(2, backend.loadedSources.size)

            player.close()
            fixture.store.close()
        }
    }

    @Test
    fun shuffleKeepsCurrentSongAndChangesTraversalOrder() {
        runBlocking {
            val fixture = fixture("shuffle")
            val songs = listOf(
                fixture.song,
                fixture.song.copy(id = "song-2", title = "Song Two"),
                fixture.song.copy(id = "song-3", title = "Song Three"),
                fixture.song.copy(id = "song-4", title = "Song Four"),
            )
            val player = MpvPlayer(
                sourceSelector = PlayerSourceSelector(
                    fixture.store,
                    resolver = { videoId -> resolved(videoId) },
                ),
                backend = FakeMpvBackend(),
                shuffleIndices = { indices -> indices.reversed() },
            )

            player.playQueue(songs, startIndex = 1)
            player.toggleShuffle()

            assertTrue(player.state.value.shuffleEnabled)
            assertEquals("song-2", player.state.value.currentSong?.id)

            player.next()
            assertEquals("song-4", player.state.value.currentSong?.id)

            player.next()
            assertEquals("song-3", player.state.value.currentSong?.id)

            player.toggleShuffle()
            assertFalse(player.state.value.shuffleEnabled)

            player.next()
            assertEquals("song-4", player.state.value.currentSong?.id)

            player.close()
            fixture.store.close()
        }
    }

    @Test
    fun repeatModeCyclesAndRepeatOneReplaysCurrentTrack() {
        runBlocking {
            val fixture = fixture("repeat-one")
            val backend = FakeMpvBackend()
            val player = MpvPlayer(
                sourceSelector = PlayerSourceSelector(
                    fixture.store,
                    resolver = { videoId -> resolved(videoId) },
                ),
                backend = backend,
            )

            player.play(fixture.song)
            assertEquals(RepeatMode.OFF, player.state.value.repeatMode)

            player.cycleRepeatMode()
            assertEquals(RepeatMode.ALL, player.state.value.repeatMode)

            player.cycleRepeatMode()
            assertEquals(RepeatMode.ONE, player.state.value.repeatMode)

            backend.eofReached = true
            delay(600)

            assertEquals("song-1", player.state.value.currentSong?.id)
            assertEquals(2, backend.loadedSources.size)
            assertTrue(player.state.value.isPlaying)
            assertFalse(player.state.value.ended)

            player.cycleRepeatMode()
            assertEquals(RepeatMode.OFF, player.state.value.repeatMode)

            player.close()
            fixture.store.close()
        }
    }

    @Test
    fun repeatAllWrapsFromLastTrackToFirst() {
        runBlocking {
            val fixture = fixture("repeat-all")
            val songs = listOf(
                fixture.song,
                fixture.song.copy(id = "song-2", title = "Song Two"),
            )
            val backend = FakeMpvBackend()
            val player = MpvPlayer(
                sourceSelector = PlayerSourceSelector(
                    fixture.store,
                    resolver = { videoId -> resolved(videoId) },
                ),
                backend = backend,
            )

            player.playQueue(songs, startIndex = 1)
            player.cycleRepeatMode()
            backend.eofReached = true
            delay(600)

            assertEquals("song-1", player.state.value.currentSong?.id)
            assertEquals(0, player.state.value.queueIndex)
            assertTrue(player.state.value.isPlaying)

            player.close()
            fixture.store.close()
        }
    }

    @Test
    fun finalTrackShowsEndedAndPlayRestartsIt() {
        runBlocking {
            val fixture = fixture("ended")
            val backend = FakeMpvBackend()
            val player = MpvPlayer(
                sourceSelector = PlayerSourceSelector(
                    fixture.store,
                    resolver = { videoId -> resolved(videoId) },
                ),
                backend = backend,
            )

            player.play(fixture.song)
            backend.eofReached = true
            delay(600)

            assertTrue(player.state.value.ended)
            assertFalse(player.state.value.isPlaying)

            player.resume()

            assertFalse(player.state.value.ended)
            assertTrue(player.state.value.isPlaying)
            assertEquals(0.0, backend.seekSeconds)

            player.close()
            fixture.store.close()
        }
    }

    @Test
    fun failedTrackChangeClearsPreviousAudioFormat() {
        runBlocking {
            val fixture = fixture("format-cleared-on-error")
            val second = fixture.song.copy(id = "song-2", title = "Song Two")
            val player = MpvPlayer(
                sourceSelector = PlayerSourceSelector(
                    fixture.store,
                    resolver = { videoId ->
                        if (videoId == fixture.song.id) resolved(videoId)
                        else throw IllegalStateException("stream unavailable")
                    },
                ),
                backend = FakeMpvBackend(),
            )

            player.play(fixture.song)
            assertEquals(144_000, player.state.value.audioFormat?.bitrate)

            runCatching { player.play(second) }

            assertEquals(null, player.state.value.audioFormat)
            assertEquals("stream unavailable", player.state.value.error)

            player.close()
            fixture.store.close()
        }
    }

    @Test
    fun playbackFailureKeepsUsefulErrorMessage() {
        runBlocking {
            val fixture = fixture("error-message")
            val player = MpvPlayer(
                sourceSelector = PlayerSourceSelector(
                    fixture.store,
                    resolver = { throw IllegalStateException("stream unavailable") },
                ),
                backend = FakeMpvBackend(),
            )

            runCatching { player.play(fixture.song) }

            assertEquals("stream unavailable", player.state.value.error)
            assertFalse(player.state.value.isPlaying)

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

    private fun formatSnapshot() =
        FormatSnapshot(
            itag = 251,
            mimeType = "audio/webm",
            codecs = "opus",
            bitrate = 144_000,
            sampleRate = 48_000,
            contentLength = 8L,
            loudnessDb = null,
            perceptualLoudnessDb = null,
        )

    private fun resolved(videoId: String = "song-1") =
        ResolvedAudio(
            videoId = videoId,
            url = "https://redacted.invalid/media/" + videoId,
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
        val loadedSources = mutableListOf<String>()
        var loadedHeaders: Map<String, String> = emptyMap()
        var pausedState = false
        var seekSeconds = 0.0
        var volumeLevel = 100
        var eofReached = false

        override fun load(source: String, headers: Map<String, String>) {
            loadedSource = source
            loadedSources += source
            loadedHeaders = headers
            eofReached = false
        }

        override fun setPaused(paused: Boolean) {
            this.pausedState = paused
        }

        override fun seek(seconds: Double) {
            seekSeconds = seconds
            eofReached = false
        }

        override fun setVolume(volume: Int) {
            this.volumeLevel = volume
        }

        override fun property(name: String): String? =
            when (name) {
                "pause" -> if (pausedState) "yes" else "no"
                "volume" -> volumeLevel.toString()
                "eof-reached" -> if (eofReached) "yes" else "no"
                else -> null
            }

        override fun stop() = Unit
        override fun close() = Unit
    }
}
