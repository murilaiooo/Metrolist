package com.metrolist.desktop.artwork

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArtworkCacheTest {
    @Test
    fun cachesArtworkByThumbnailUrlAndReusesExistingFile() {
        val dir = Files.createTempDirectory("metrolist-artwork-cache")
        var fetchCount = 0
        val expected = byteArrayOf(1, 2, 3, 4)
        val cache = ArtworkCache(dir) {
            fetchCount++
            expected
        }

        val url = "https://example.invalid/thumb?id=123"
        val first = assertNotNull(cache.ensure(url))
        val second = assertNotNull(cache.ensure(url))

        assertEquals(first, second)
        assertEquals(1, fetchCount)
        assertTrue(first.fileName.toString().endsWith(".img"))
        assertContentEquals(expected, Files.readAllBytes(first))
    }

    @Test
    fun failedOrMissingArtworkDoesNotCreateCacheEntry() {
        val dir = Files.createTempDirectory("metrolist-artwork-failure")
        val cache = ArtworkCache(dir) {
            error("network unavailable")
        }

        assertNull(cache.ensure(""))
        assertNull(cache.ensure(null))
        assertNull(cache.ensure("https://example.invalid/fails"))
        assertEquals(0L, Files.list(dir).use { it.count() })
    }
}
