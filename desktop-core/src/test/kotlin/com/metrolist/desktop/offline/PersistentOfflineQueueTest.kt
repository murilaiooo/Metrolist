package com.metrolist.desktop.offline

import com.metrolist.innertube.models.SongItem
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PersistentOfflineQueueTest {
    @Test
    fun limitsConcurrencyToThreeAndCompletesEveryQueuedSong() = runBlocking {
        val db = testPath("queue.db")
        val active = AtomicInteger()
        val maxActive = AtomicInteger()

        OfflineStore(db).use { store ->
            PersistentOfflineQueue(
                store = store,
                worker = { record ->
                    val now = active.incrementAndGet()
                    maxActive.updateAndGet { old -> maxOf(old, now) }
                    try {
                        delay(80)
                        record.copy(
                            state = OfflineState.COMPLETED,
                            localPath = "media/${record.videoId}.audio",
                            bytesDownloaded = 1L,
                            contentLength = 1L,
                        )
                    } finally {
                        active.decrementAndGet()
                    }
                },
                maxConcurrent = 3,
            ).use { queue ->
                queue.enqueueAll((1..7).map { song("song-$it") })
                assertTrue(queue.awaitIdle(5_000))
                assertEquals(7, store.listCompleted().size)
                assertTrue(maxActive.get() <= 3)
                assertEquals(3, maxActive.get())
            }
        }
    }

    @Test
    fun completedSongIsNotQueuedAgain() = runBlocking {
        val db = testPath("dedupe.db")
        val calls = AtomicInteger()

        OfflineStore(db).use { store ->
            store.upsert(
                OfflineRecord.queued(song("same"), 1L).copy(
                    state = OfflineState.COMPLETED,
                    localPath = "media/same.audio",
                    bytesDownloaded = 1L,
                    contentLength = 1L,
                ),
            )

            PersistentOfflineQueue(
                store = store,
                worker = { record ->
                    calls.incrementAndGet()
                    record.copy(state = OfflineState.COMPLETED)
                },
            ).use { queue ->
                queue.enqueue(song("same"))
                assertTrue(queue.awaitIdle(1_000))
                assertEquals(0, calls.get())
            }
        }
    }

    private fun song(id: String) = SongItem(
        id = id,
        title = id,
        artists = emptyList(),
        thumbnail = "",
    )

    private fun testPath(name: String): Path {
        val dir = Path.of(System.getProperty("java.io.tmpdir"), "offline-queue-test")
        Files.createDirectories(dir)
        Files.deleteIfExists(dir.resolve(name))
        return dir.resolve(name)
    }
}
