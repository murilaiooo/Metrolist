package com.metrolist.desktop.player

import com.metrolist.desktop.offline.FormatSnapshot
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
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class PlayerSourceSelectorTest {
    @Test
    fun validCompletedMediaWinsOverNetwork() {
        runBlocking {
            val fixture = fixture("local-wins")
            val media = fixture.dir.resolve("song.media")
            Files.write(media, ByteArray(8) { 1 })
            fixture.store.upsert(completed(fixture.song, media, 8L))

            var onlineCalls = 0
            val selector = PlayerSourceSelector(
                store = fixture.store,
                resolver = {
                    onlineCalls++
                    resolved()
                },
            )

            val source = selector.select(fixture.song)

            assertIs<PlayerSource.Local>(source)
            assertEquals(media, source.path)
            assertEquals(251, source.format?.itag)
            assertEquals("audio/webm", source.format?.mimeType)
            assertEquals("opus", source.format?.codecs)
            assertEquals(144_000, source.format?.bitrate)
            assertEquals(48_000, source.format?.sampleRate)
            assertEquals(0, onlineCalls)
            fixture.store.close()
        }
    }

    @Test
    fun missingLocalFileFallsBackToFreshOnlineResolution() {
        runBlocking {
            val fixture = fixture("online-fallback")
            fixture.store.upsert(
                completed(
                    fixture.song,
                    fixture.dir.resolve("missing.media"),
                    8L,
                ),
            )

            var onlineCalls = 0
            val selector = PlayerSourceSelector(
                store = fixture.store,
                resolver = {
                    onlineCalls++
                    resolved()
                },
            )

            val source = selector.select(fixture.song)

            assertIs<PlayerSource.Remote>(source)
            assertEquals(251, source.resolved.itag)
            assertEquals("audio/webm", source.resolved.mimeType)
            assertEquals("opus", source.resolved.codecs)
            assertEquals(144_000, source.resolved.bitrate)
            assertEquals(48_000, source.resolved.sampleRate)
            assertEquals(1, onlineCalls)
            fixture.store.close()
        }
    }

    @Test
    fun offlineOnlyRejectsSongWithoutValidLocalMedia() {
        runBlocking {
            val fixture = fixture("offline-only")
            val selector = PlayerSourceSelector(
                store = fixture.store,
                resolver = { resolved() },
            )

            assertFailsWith<OfflineMediaUnavailableException> {
                selector.select(fixture.song, offlineOnly = true)
            }
            fixture.store.close()
        }
    }

    private fun completed(
        song: SongItem,
        path: Path,
        bytes: Long,
    ) = OfflineRecord.queued(song, 1L).copy(
        state = OfflineState.COMPLETED,
        localPath = path.toString(),
        bytesDownloaded = bytes,
        contentLength = bytes,
        updateTimeMs = 2L,
        format =
            FormatSnapshot(
                itag = 251,
                mimeType = "audio/webm",
                codecs = "opus",
                bitrate = 144_000,
                sampleRate = 48_000,
                contentLength = bytes,
                loudnessDb = null,
                perceptualLoudnessDb = null,
            ),
    )

    private fun resolved() =
        ResolvedAudio(
            videoId = "song-1",
            url = "https://redacted.invalid",
            headers = emptyMap(),
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

    private fun fixture(name: String): Fixture {
        val dir = Path.of(System.getProperty("java.io.tmpdir"), "player-source-$name")
        Files.createDirectories(dir)
        val db = dir.resolve("offline.db")
        Files.deleteIfExists(db)
        val store = OfflineStore(db)
        return Fixture(
            dir = dir,
            store = store,
            song = SongItem(
                id = "song-1",
                title = "Song",
                artists = emptyList(),
                thumbnail = "",
            ),
        )
    }

    private data class Fixture(
        val dir: Path,
        val store: OfflineStore,
        val song: SongItem,
    )
}
