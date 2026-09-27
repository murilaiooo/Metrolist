package com.metrolist.desktop.library

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class PlaylistSyncServiceTest {
    @Test
    fun createsRemoteOncePersistsLinkImmediatelyAndSyncsTracksInOrder() = runBlocking {
        withPlaylist { store, playlist ->
            val creates = AtomicInteger(0)
            val adds = CopyOnWriteArrayList<String>()
            val gateway =
                object : YouTubePlaylistGateway {
                    override suspend fun create(name: String): String {
                        creates.incrementAndGet()
                        assertEquals("Road Trip", name)
                        return "remote-1"
                    }

                    override suspend fun add(playlistId: String, videoId: String): Result<Unit> {
                        assertEquals("remote-1", playlistId)
                        adds += videoId
                        return Result.success(Unit)
                    }
                }
            val service = PlaylistSyncService(store, gateway, nowMs = { 100L })

            val result = service.sync(playlist.id)

            assertEquals(1, creates.get())
            assertEquals(listOf("a", "b", "c"), adds.toList())
            assertEquals("remote-1", result.remotePlaylistId)
            assertEquals(3, result.succeeded)
            assertEquals(0, result.failed)
            assertEquals("remote-1", store.getPlaylist(playlist.id)?.youtubePlaylistId)
            assertEquals(PlaylistSyncState.SYNCED, store.getPlaylist(playlist.id)?.syncState)
            assertTrue(store.playlistTracks(playlist.id).all { it.syncState == TrackSyncState.SYNCED })
        }
    }

    @Test
    fun partialFailureKeepsRemoteLinkAndRetryOnlyAddsFailedTracks() = runBlocking {
        withPlaylist { store, playlist ->
            val creates = AtomicInteger(0)
            val failB = AtomicBoolean(true)
            val adds = CopyOnWriteArrayList<String>()
            val gateway =
                object : YouTubePlaylistGateway {
                    override suspend fun create(name: String): String {
                        creates.incrementAndGet()
                        return "remote-1"
                    }

                    override suspend fun add(playlistId: String, videoId: String): Result<Unit> {
                        adds += videoId
                        return if (videoId == "b" && failB.compareAndSet(true, false)) {
                            Result.failure(IllegalStateException("temporary failure"))
                        } else {
                            Result.success(Unit)
                        }
                    }
                }
            val service = PlaylistSyncService(store, gateway, nowMs = { 200L })

            val first = service.sync(playlist.id)

            assertEquals(1, creates.get())
            assertEquals(2, first.succeeded)
            assertEquals(1, first.failed)
            assertEquals(PlaylistSyncState.PARTIAL, store.getPlaylist(playlist.id)?.syncState)
            val failed = store.playlistTracks(playlist.id).single { it.song.videoId == "b" }
            assertEquals(TrackSyncState.FAILED, failed.syncState)
            assertEquals("temporary failure", failed.lastSyncError)

            adds.clear()
            val second = service.sync(playlist.id)

            assertEquals(1, creates.get())
            assertEquals(listOf("b"), adds.toList())
            assertEquals(1, second.succeeded)
            assertEquals(0, second.failed)
            assertEquals(PlaylistSyncState.SYNCED, store.getPlaylist(playlist.id)?.syncState)
            assertTrue(store.playlistTracks(playlist.id).all { it.syncState == TrackSyncState.SYNCED })
        }
    }

    @Test
    fun reportsProgressAfterEachAttempt() = runBlocking {
        withPlaylist { store, playlist ->
            val progress = mutableListOf<SyncProgress>()
            val gateway =
                object : YouTubePlaylistGateway {
                    override suspend fun create(name: String): String = "remote-1"

                    override suspend fun add(playlistId: String, videoId: String): Result<Unit> =
                        if (videoId == "b") Result.failure(IllegalStateException("failed"))
                        else Result.success(Unit)
                }

            PlaylistSyncService(store, gateway).sync(playlist.id) { progress += it }

            assertEquals(listOf(0, 1, 2, 3), progress.map { it.completed })
            assertEquals(3, progress.last().total)
            assertEquals(2, progress.last().succeeded)
            assertEquals(1, progress.last().failed)
        }
    }

    @Test
    fun alreadyLinkedPlaylistNeverCreatesAnotherRemotePlaylist() = runBlocking {
        val db = createTempDirectory("metrolist-sync-linked").resolve("library.db")
        LibraryStore(db).use { store ->
            val playlist = seedPlaylist(store, youtubePlaylistId = "existing")
            val creates = AtomicInteger(0)
            val gateway =
                object : YouTubePlaylistGateway {
                    override suspend fun create(name: String): String {
                        creates.incrementAndGet()
                        return "wrong"
                    }

                    override suspend fun add(playlistId: String, videoId: String): Result<Unit> {
                        assertEquals("existing", playlistId)
                        return Result.success(Unit)
                    }
                }

            PlaylistSyncService(store, gateway).sync(playlist.id)

            assertEquals(0, creates.get())
            assertEquals("existing", store.getPlaylist(playlist.id)?.youtubePlaylistId)
        }
    }

    private suspend fun withPlaylist(block: suspend (LibraryStore, LocalPlaylist) -> Unit) {
        val db = createTempDirectory("metrolist-sync").resolve("library.db")
        LibraryStore(db).use { store ->
            val playlist = seedPlaylist(store)
            block(store, playlist)
        }
    }

    private fun seedPlaylist(
        store: LibraryStore,
        youtubePlaylistId: String? = null,
    ): LocalPlaylist {
        val playlist =
            LocalPlaylist(
                id = "local",
                name = "Road Trip",
                createdAtMs = 1L,
                updatedAtMs = 1L,
                youtubePlaylistId = youtubePlaylistId,
                syncState = if (youtubePlaylistId == null) PlaylistSyncState.LOCAL_ONLY else PlaylistSyncState.LINKED,
            )
        store.upsertPlaylist(playlist)
        store.replacePlaylistTracks(
            playlist.id,
            listOf("a", "b", "c").mapIndexed { index, id ->
                LocalPlaylistTrack(
                    playlistId = playlist.id,
                    position = index,
                    song =
                        LibrarySongSnapshot(
                            videoId = id,
                            title = "Song $id",
                            artists = listOf(LibraryArtistSnapshot("Artist", null)),
                            thumbnail = "https://example.invalid/$id.jpg",
                        ),
                )
            },
        )
        return playlist
    }
}
