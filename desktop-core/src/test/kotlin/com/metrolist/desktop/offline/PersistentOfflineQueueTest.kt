package com.metrolist.desktop.offline

import com.metrolist.desktop.media.MediaTransferProgress
import com.metrolist.innertube.models.SongItem
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
    fun exposesLiveProgressWhileDownloadIsStillRunning() = runBlocking {
        val db = testPath("progress.db")
        val release = CompletableDeferred<Unit>()

        OfflineStore(db).use { store ->
            PersistentOfflineQueue(
                store = store,
                worker = { record, onProgress ->
                    onProgress(
                        MediaTransferProgress(
                            bytesDownloaded = 5L,
                            totalBytes = 10L,
                            bytesPerSecond = 2_048L,
                        ),
                    )
                    release.await()
                    record.copy(
                        state = OfflineState.COMPLETED,
                        localPath = "media/" + record.videoId + ".audio",
                        bytesDownloaded = 10L,
                        contentLength = 10L,
                    )
                },
            ).use { queue ->
                queue.enqueue(song("progress-song"))

                val live =
                    withTimeout(2_000) {
                        queue.downloads.first { records ->
                            records.any {
                                it.videoId == "progress-song" &&
                                    it.state == OfflineState.DOWNLOADING &&
                                    it.bytesDownloaded == 5L
                            }
                        }.first { it.videoId == "progress-song" }
                    }

                assertEquals(10L, live.contentLength)
                assertEquals(2_048L, live.bytesPerSecond)

                release.complete(Unit)
                assertTrue(queue.awaitIdle(2_000))
            }
        }
    }

    @Test
    fun recoveredDownloadWaitsForExplicitResumeWhenAutoResumeIsDisabled() = runBlocking {
        val db = testPath("resume-gate.db")
        val calls = AtomicInteger()

        OfflineStore(db).use { store ->
            store.upsert(
                OfflineRecord.queued(song("resume-song"), 1L).copy(
                    state = OfflineState.DOWNLOADING,
                ),
            )

            PersistentOfflineQueue(
                store = store,
                worker = { record ->
                    calls.incrementAndGet()
                    record.copy(
                        state = OfflineState.COMPLETED,
                        localPath = "media/" + record.videoId + ".audio",
                        bytesDownloaded = 1L,
                        contentLength = 1L,
                    )
                },
                autoResumePending = false,
            ).use { queue ->
                delay(100)
                assertEquals(0, calls.get())
                assertEquals(OfflineState.QUEUED, store.get("resume-song")?.state)

                queue.resumePending()
                assertTrue(queue.awaitIdle(2_000))
                assertEquals(1, calls.get())
            }
        }
    }

    @Test
    fun removingActiveBlockingTransferDoesNotRecreateRecord() = runBlocking {
        val db = testPath("remove-active.db")
        val started = CompletableDeferred<Unit>()

        OfflineStore(db).use { store ->
            PersistentOfflineQueue(
                store = store,
                worker = { record ->
                    started.complete(Unit)
                    Thread.sleep(150)
                    record.copy(
                        state = OfflineState.COMPLETED,
                        localPath = "media/" + record.videoId + ".audio",
                        bytesDownloaded = 1L,
                        contentLength = 1L,
                    )
                },
            ).use { queue ->
                queue.enqueue(song("remove-me"))
                started.await()
                queue.remove("remove-me")
                delay(300)

                assertNull(store.get("remove-me"))
            }
        }
    }

    @Test
    fun removingActiveBlockingFailureDoesNotRecreateFailedRecord() = runBlocking {
        val db = testPath("remove-active-failure.db")
        val started = CompletableDeferred<Unit>()

        OfflineStore(db).use { store ->
            PersistentOfflineQueue(
                store = store,
                worker = {
                    started.complete(Unit)
                    Thread.sleep(150)
                    error("late worker failure")
                },
            ).use { queue ->
                queue.enqueue(song("remove-failure"))
                started.await()
                queue.remove("remove-failure")
                delay(300)

                assertNull(store.get("remove-failure"))
            }
        }
    }

    @Test
    fun closingQueueBeforeStoreDoesNotTouchClosedStoreFromLateJobCompletion() = runBlocking {
        val db = testPath("close-active.db")
        val started = CompletableDeferred<Unit>()
        val uncaught = Collections.synchronizedList(mutableListOf<Throwable>())
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { _, error ->
            uncaught += error
        }

        try {
            val store = OfflineStore(db)
            val queue =
                PersistentOfflineQueue(
                    store = store,
                    worker = { record ->
                        started.complete(Unit)
                        Thread.sleep(150)
                        record.copy(
                            state = OfflineState.COMPLETED,
                            localPath = "media/" + record.videoId + ".audio",
                            bytesDownloaded = 1L,
                            contentLength = 1L,
                        )
                    },
                )

            queue.enqueue(song("close-active"))
            started.await()
            queue.close()
            store.close()
            delay(350)

            assertTrue(
                uncaught.isEmpty(),
                "Late queue completion touched the closed store: " +
                    uncaught.joinToString { it::class.simpleName.orEmpty() },
            )
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        }
    }

    @Test
    fun retryTriggeredWhileFailedJobIsStillCompletingIsNotLeftQueued() = runBlocking {
        val db = testPath("retry-completion-race.db")
        val calls = AtomicInteger()

        OfflineStore(db).use { store ->
            PersistentOfflineQueue(
                store = store,
                worker = { record ->
                    if (calls.incrementAndGet() == 1) {
                        error("first attempt fails")
                    }
                    record.copy(
                        state = OfflineState.COMPLETED,
                        localPath = "media/" + record.videoId + ".audio",
                        bytesDownloaded = 1L,
                        contentLength = 1L,
                    )
                },
            ).use { queue ->
                val retryObserved = CompletableDeferred<Unit>()
                val collector =
                    launch(Dispatchers.Unconfined) {
                        queue.downloads.collect { records ->
                            if (
                                !retryObserved.isCompleted &&
                                records.any {
                                    it.videoId == "retry-race" &&
                                        it.state == OfflineState.FAILED
                                }
                            ) {
                                queue.retry("retry-race")
                                retryObserved.complete(Unit)
                                cancel()
                            }
                        }
                    }

                queue.enqueue(song("retry-race"))
                withTimeout(2_000) { retryObserved.await() }

                assertTrue(queue.awaitIdle(2_000))
                assertEquals(2, calls.get())
                assertEquals(OfflineState.COMPLETED, store.get("retry-race")?.state)
                collector.cancel()
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
