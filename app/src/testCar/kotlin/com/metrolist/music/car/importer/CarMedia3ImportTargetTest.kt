package com.metrolist.music.car.importer

import android.app.Application
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DefaultDownloadIndex
import androidx.media3.exoplayer.offline.Download
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
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
class CarMedia3ImportTargetTest {
    private lateinit var context: Application
    private lateinit var databaseProvider: StandaloneDatabaseProvider
    private lateinit var cache: SimpleCache

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(StandaloneDatabaseProvider.DATABASE_NAME)
        val cacheDir = context.cacheDir.resolve("car-media3-import").apply { deleteRecursively() }
        databaseProvider = StandaloneDatabaseProvider(context)
        cache = SimpleCache(cacheDir, NoOpCacheEvictor(), databaseProvider)
    }

    @After
    fun tearDown() {
        cache.release()
        databaseProvider.close()
        context.deleteDatabase(StandaloneDatabaseProvider.DATABASE_NAME)
        context.cacheDir.resolve("car-media3-import").deleteRecursively()
    }

    @Test
    fun importedSpansBecomeReadableCacheAndCompletedDownload() {
        val first = byteArrayOf(1, 2)
        val second = byteArrayOf(3, 4)
        val resource =
            CarPortableResource(
                songId = "song1",
                cacheKey = "song1",
                contentLength = 4,
                spans =
                    listOf(
                        CarPortableSpan("span-0", "song1", 0, 2, "unused"),
                        CarPortableSpan("span-1", "song1", 2, 2, "unused"),
                    ),
            )
        val transfer =
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
        val bytesByPath = mapOf("span-0" to first, "span-1" to second)
        val target = CarMedia3ImportTarget(cache, databaseProvider)

        target.writeResource(resource) { span ->
            ByteArrayInputStream(bytesByPath.getValue(span.relativePath))
        }
        target.putCompletedDownload(transfer)

        assertTrue(cache.isCached("song1", 0, 4))
        assertEquals(4L, ContentMetadata.getContentLength(cache.getContentMetadata("song1")))
        val stored = DefaultDownloadIndex(databaseProvider).getDownload("song1")!!
        assertEquals(Download.STATE_COMPLETED, stored.state)
        assertEquals(4L, stored.contentLength)
        assertEquals(4L, stored.bytesDownloaded)
        assertEquals("song1", stored.request.customCacheKey)
        assertEquals("Song 1", stored.request.data.decodeToString())
    }
}
