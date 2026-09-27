package com.metrolist.music.playback

import android.net.Uri
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaybackDataSourceRouterTest {
    @Test
    fun localSchemesBypassCachedFactoryWhileHttpsUsesIt() {
        var cachedCreates = 0
        var directCreates = 0
        val cached =
            DataSource.Factory {
                cachedCreates++
                ByteArrayDataSource(byteArrayOf(1, 2, 3))
            }
        val direct =
            DataSource.Factory {
                directCreates++
                ByteArrayDataSource(byteArrayOf(4, 5, 6))
            }
        val router =
            PlaybackDataSourceRouterFactory(
                cachedFactory = cached,
                directFactory = direct,
            )

        router.createDataSource().useOpen("file:///music/song.media")
        router.createDataSource().useOpen("content://provider/tree/song")
        router.createDataSource().useOpen("https://example.com/audio")

        assertEquals(2, directCreates)
        assertEquals(1, cachedCreates)
        assertTrue(isDirectLocalMediaUri(Uri.parse("file:///music/song.media")))
        assertTrue(isDirectLocalMediaUri(Uri.parse("content://provider/song")))
        assertFalse(isDirectLocalMediaUri(Uri.parse("https://example.com/audio")))
    }

    private fun DataSource.useOpen(uri: String) {
        try {
            open(DataSpec(Uri.parse(uri)))
        } finally {
            close()
        }
    }
}