package com.metrolist.music.car.importer

import android.app.Application
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.metrolist.music.db.InternalDatabase
import com.metrolist.music.db.MusicDatabase
import java.io.ByteArrayInputStream
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class CarImportTargetsTest {
    private lateinit var context: Application
    private lateinit var databaseProvider: StandaloneDatabaseProvider
    private lateinit var cache: SimpleCache
    private lateinit var internalDatabase: InternalDatabase
    private lateinit var database: MusicDatabase
    private lateinit var artworkRoot: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(StandaloneDatabaseProvider.DATABASE_NAME)
        val cacheDir = context.cacheDir.resolve("car-import-targets").apply { deleteRecursively() }
        artworkRoot = context.cacheDir.resolve("car-artwork-root").apply { deleteRecursively() }

        databaseProvider = StandaloneDatabaseProvider(context)
        cache = SimpleCache(cacheDir, NoOpCacheEvictor(), databaseProvider)
        internalDatabase =
            Room.inMemoryDatabaseBuilder(context, InternalDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        database = MusicDatabase(internalDatabase)
    }

    @After
    fun tearDown() {
        database.close()
        cache.release()
        databaseProvider.close()
        context.deleteDatabase(StandaloneDatabaseProvider.DATABASE_NAME)
        context.cacheDir.resolve("car-import-targets").deleteRecursively()
        artworkRoot.deleteRecursively()
    }

    @Test
    fun repeatedRoomImportDoesNotDuplicateArtistRelations() = runBlocking {
        val target = CarRoomImportTarget(database)
        val song = portableSong()
        val artist = portableArtist()
        val map = CarPortableMap("song1", "artist1", 0)
        val format = portableFormat()

        target.upsertSongBundle(song, listOf(artist), listOf(map), format)
        target.upsertSongBundle(song, listOf(artist), listOf(map), format)

        assertEquals(1, database.songArtistMap("song1").size)
        assertTrue(database.songEntity("song1")!!.isDownloaded)
    }

    @Test
    fun artworkAndInspectorReportCompleteLocalState() = runBlocking {
        val resource =
            CarPortableResource(
                songId = "song1",
                cacheKey = "song1",
                contentLength = 4,
                spans = listOf(CarPortableSpan("span", "song1", 0, 4, "unused")),
            )
        val mediaTarget = CarMedia3ImportTarget(cache, databaseProvider)
        val roomTarget = CarRoomImportTarget(database)
        val artworkStore = CarArtworkStore(artworkRoot)

        mediaTarget.writeResource(resource) { ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)) }
        mediaTarget.putCompletedDownload(portableTransfer())
        roomTarget.upsertSongBundle(
            portableSong(),
            listOf(portableArtist()),
            listOf(CarPortableMap("song1", "artist1", 0)),
            portableFormat(),
        )
        val artwork = artworkStore.write("song1", ByteArrayInputStream(byteArrayOf(9, 8, 7)))

        assertEquals(artworkRoot.resolve("car-artwork/song1.img"), artwork)
        assertTrue(artwork.readBytes().contentEquals(byteArrayOf(9, 8, 7)))

        val state =
            CarImportInspector(cache, databaseProvider, database, artworkStore)
                .inspect("song1", 4, artworkExpected = true)

        assertTrue(state.cacheComplete)
        assertTrue(state.downloadCompleted)
        assertTrue(state.roomComplete)
        assertTrue(state.artworkPresent)
    }

    private fun portableSong() =
        CarPortableSong(
            id = "song1",
            title = "Song 1",
            duration = 123,
            thumbnailUrl = "https://example/song.jpg",
            thumbnailArtworkPath = "artwork/song1.img",
            albumId = "album1",
            albumName = "Album 1",
            explicit = false,
            year = 2026,
            date = null,
            dateModified = null,
            dateDownload = "2026-09-27T07:00:00",
            isVideo = false,
            isEpisode = false,
        )

    private fun portableArtist() =
        CarPortableArtist(
            id = "artist1",
            name = "Artist 1",
            thumbnailUrl = null,
            channelId = null,
            lastUpdateTime = "2026-09-27T06:00:00",
            isPodcastChannel = false,
        )

    private fun portableFormat() =
        CarPortableFormat(
            id = "song1",
            itag = 251,
            mimeType = "audio/webm",
            codecs = "opus",
            bitrate = 128000,
            sampleRate = 48000,
            contentLength = 4,
            loudnessDb = -5.0,
            perceptualLoudnessDb = -6.0,
        )

    private fun portableTransfer() =
        CarPortableTransfer(
            requestId = "song1",
            uri = "song1",
            customCacheKey = "song1",
            title = "Song 1",
            contentLength = 4,
            startTimeMs = 100,
            updateTimeMs = 200,
            bytesDownloaded = 4,
        )
}
