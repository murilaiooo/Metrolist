package com.metrolist.desktop.library.importer

import com.metrolist.desktop.library.ImportRow
import com.metrolist.desktop.library.ImportRowState
import com.metrolist.desktop.library.ImportSession
import com.metrolist.desktop.library.LibraryStore
import com.metrolist.desktop.library.RememberedMatch
import com.metrolist.innertube.models.Album
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.SongItem
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class PlaylistImportMatcherTest {
    @Test
    fun boundsConcurrentSearchesAtFourAndPersistsProgress() = runBlocking {
        withStore(8) { store, sessionId ->
            val active = AtomicInteger(0)
            val maxActive = AtomicInteger(0)
            val gateway =
                SongSearchGateway { query ->
                    val current = active.incrementAndGet()
                    maxActive.updateAndGet { maxOf(it, current) }
                    try {
                        delay(30)
                        val n = Regex("""\d+""").find(query)?.value ?: "0"
                        listOf(song("id-$n", "Song$n", "Artist$n"))
                    } finally {
                        active.decrementAndGet()
                    }
                }
            val matcher = PlaylistImportMatcher(store, gateway)

            matcher.match(sessionId)

            assertTrue(maxActive.get() in 2..4)
            assertEquals(8, store.importRows(sessionId).count { it.state == ImportRowState.READY })
            assertEquals(8, matcher.progress.value.completed)
            assertEquals(8, matcher.progress.value.ready)
            assertEquals(0, matcher.progress.value.running)
        }
    }

    @Test
    fun persistsAtMostEightDeduplicatedCandidatesPerRow() = runBlocking {
        withRows(listOf(ImportRow("session", 0, "Song", "Song"))) { store ->
            val gateway =
                SongSearchGateway {
                    (0 until 12).map { index -> song("id-$index", "Song", "Artist$index") } +
                        song("id-0", "Song", "Duplicate")
                }
            val matcher = PlaylistImportMatcher(store, gateway)

            matcher.match("session")

            val row = requireNotNull(store.getImportRow("session", 0))
            assertEquals(8, row.candidates.size)
            assertEquals(8, row.candidates.map { it.song.videoId }.distinct().size)
        }
    }

    @Test
    fun networkFailureIsPersistedAndResumeSkipsCompletedRows() = runBlocking {
        val rows =
            listOf(
                ImportRow("session", 0, "GoodArtist - GoodSong", "GoodArtist", "GoodSong"),
                ImportRow("session", 1, "FailArtist - FailSong", "FailArtist", "FailSong"),
            )
        withRows(rows) { store ->
            val failOnce = AtomicBoolean(true)
            val calls = ConcurrentLinkedQueue<String>()
            val gateway =
                SongSearchGateway { query ->
                    calls += query
                    if ("Fail" in query && failOnce.compareAndSet(true, false)) error("network down")
                    when {
                        "Good" in query -> listOf(song("good", "GoodSong", "GoodArtist"))
                        else -> listOf(song("fixed", "FailSong", "FailArtist"))
                    }
                }
            val matcher = PlaylistImportMatcher(store, gateway)

            matcher.match("session")
            assertEquals(ImportRowState.READY, store.getImportRow("session", 0)?.state)
            assertEquals(ImportRowState.FAILED, store.getImportRow("session", 1)?.state)

            calls.clear()
            matcher.match("session")

            assertEquals(ImportRowState.READY, store.getImportRow("session", 1)?.state)
            assertTrue(calls.none { "Good" in it })
        }
    }

    @Test
    fun cancellationKeepsAlreadyCompletedRowsAndLeavesOthersResumable() = runBlocking {
        val rows =
            (0 until 6).map { index ->
                ImportRow("session", index, "Artist$index - Song$index", "Artist$index", "Song$index")
            }
        withRows(rows) { store ->
            val gateway =
                SongSearchGateway { query ->
                    if ("Song0" in query) {
                        listOf(song("id-0", "Song0", "Artist0"))
                    } else {
                        delay(5_000)
                        emptyList()
                    }
                }
            val matcher = PlaylistImportMatcher(store, gateway)
            val job = launch { matcher.match("session") }

            withTimeout(2_000) {
                while (store.getImportRow("session", 0)?.state != ImportRowState.READY) delay(10)
            }
            job.cancelAndJoin()

            assertEquals(ImportRowState.READY, store.getImportRow("session", 0)?.state)
            assertTrue(store.importRows("session").drop(1).any { it.state == ImportRowState.PENDING })
        }
    }

    @Test
    fun duplicateInputRowsRemainIndependentAndMayResolveToSameVideo() = runBlocking {
        val rows =
            listOf(
                ImportRow("session", 0, "Artist - Song", "Artist", "Song"),
                ImportRow("session", 1, "Artist - Song", "Artist", "Song"),
            )
        withRows(rows) { store ->
            val matcher = PlaylistImportMatcher(store, SongSearchGateway { listOf(song("same", "Song", "Artist")) })

            matcher.match("session")

            assertEquals(listOf(0, 1), store.importRows("session").map { it.position })
            assertEquals(listOf("same", "same"), store.importRows("session").map { it.selectedSong?.videoId })
        }
    }

    @Test
    fun manualCandidateSelectionMarksReadyAndRemembersChoice() = runBlocking {
        val row = ImportRow("session", 0, "Artist - Song", "Artist", "Song")
        withRows(listOf(row)) { store ->
            val matcher =
                PlaylistImportMatcher(
                    store,
                    SongSearchGateway {
                        listOf(
                            song("wrong", "Song (Live)", "Artist"),
                            song("chosen", "Song", "Artist", explicit = true),
                        )
                    },
                )

            matcher.match("session")
            matcher.selectCandidate("session", 0, "chosen")

            val updated = requireNotNull(store.getImportRow("session", 0))
            assertEquals(ImportRowState.READY, updated.state)
            assertEquals("chosen", updated.selectedSong?.videoId)
            val remembered = store.getRememberedMatch(importIdentity(updated))
            assertEquals("chosen", remembered?.videoId)
            assertEquals(true, remembered?.explicit)
        }
    }

    @Test
    fun validRememberedMatchUsesValidatedTargetWithoutBroadRematch() = runBlocking {
        val row = ImportRow("session", 0, "Artist - Song", "Artist", "Song")
        withRows(listOf(row)) { store ->
            store.upsertRememberedMatch(
                RememberedMatch(
                    identity = importIdentity(row),
                    videoId = "remembered",
                    title = "Song",
                    artist = "Artist",
                    duration = 200,
                    explicit = true,
                    updatedAtMs = 1L,
                ),
            )
            val calls = AtomicInteger(0)
            val matcher =
                PlaylistImportMatcher(
                    store,
                    SongSearchGateway {
                        calls.incrementAndGet()
                        listOf(song("remembered", "Song", "Artist", explicit = true))
                    },
                )

            matcher.match("session")

            assertEquals(1, calls.get())
            assertEquals("remembered", store.getImportRow("session", 0)?.selectedSong?.videoId)
            assertEquals(ImportRowState.READY, store.getImportRow("session", 0)?.state)
        }
    }

    @Test
    fun unavailableRememberedMatchFallsBackToNormalSearch() = runBlocking {
        val row = ImportRow("session", 0, "Artist - Song", "Artist", "Song")
        withRows(listOf(row)) { store ->
            store.upsertRememberedMatch(
                RememberedMatch(importIdentity(row), "missing", "Song", "Artist", 200, false, 1L),
            )
            val calls = AtomicInteger(0)
            val matcher =
                PlaylistImportMatcher(
                    store,
                    SongSearchGateway { query ->
                        calls.incrementAndGet()
                        if (calls.get() == 1) emptyList() else listOf(song("new", "Song", "Artist"))
                    },
                )

            matcher.match("session")

            assertTrue(calls.get() >= 2)
            assertEquals("new", store.getImportRow("session", 0)?.selectedSong?.videoId)
        }
    }

    private suspend fun withStore(
        count: Int,
        block: suspend (LibraryStore, String) -> Unit,
    ) {
        val rows =
            (0 until count).map { index ->
                ImportRow("session", index, "Artist$index - Song$index", "Artist$index", "Song$index")
            }
        withRows(rows) { store -> block(store, "session") }
    }

    private suspend fun withRows(
        rows: List<ImportRow>,
        block: suspend (LibraryStore) -> Unit,
    ) {
        val db = createTempDirectory("metrolist-import-matcher").resolve("library.db")
        LibraryStore(db).use { store ->
            store.upsertImportSession(
                ImportSession(
                    id = "session",
                    name = "Test",
                    sourceText = rows.joinToString("\n") { it.original },
                    createdAtMs = 1L,
                    updatedAtMs = 1L,
                ),
            )
            store.upsertImportRows(rows)
            block(store)
        }
    }

    private fun song(
        id: String,
        title: String,
        artist: String,
        explicit: Boolean = false,
    ) =
        SongItem(
            id = id,
            title = title,
            artists = listOf(Artist(artist, "artist-id")),
            album = Album("Album", "album-id"),
            duration = 200,
            musicVideoType = "MUSIC_VIDEO_TYPE_ATV",
            thumbnail = "https://example.invalid/$id.jpg",
            explicit = explicit,
        )
}
