package com.metrolist.desktop.offline

import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.SongItem
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OfflineStoreTest {
    @Test
    fun persistsRecordsAndRecoversInterruptedState() {
        val db = testPath("offline-store.db")
        val song = SongItem(
            id = "song-1",
            title = "Song One",
            artists = listOf(Artist("Artist", "artist-1")),
            duration = 123,
            thumbnail = "https://example.invalid/thumb",
        )

        OfflineStore(db).use { store ->
            store.upsert(OfflineRecord.queued(song, nowMs = 100L))
            store.upsert(requireNotNull(store.get("song-1")).copy(state = OfflineState.DOWNLOADING))
        }

        OfflineStore(db).use { store ->
            store.recoverInterrupted()
            assertEquals(OfflineState.QUEUED, requireNotNull(store.get("song-1")).state)

            store.upsert(
                requireNotNull(store.get("song-1")).copy(
                    state = OfflineState.COMPLETED,
                    localPath = "media/song-1.audio",
                    bytesDownloaded = 456L,
                    contentLength = 456L,
                ),
            )
            assertEquals(listOf("song-1"), store.listCompleted().map { it.videoId })

            store.remove("song-1")
            assertNull(store.get("song-1"))
        }
    }

    private fun testPath(name: String): Path {
        val dir = Path.of(System.getProperty("java.io.tmpdir"), "offline-store-test")
        Files.createDirectories(dir)
        Files.deleteIfExists(dir.resolve(name))
        return dir.resolve(name)
    }
}
