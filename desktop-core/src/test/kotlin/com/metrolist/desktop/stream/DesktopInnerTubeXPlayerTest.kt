package com.metrolist.desktop.stream

import com.metrolist.innertubex.extraction.AudioQuality
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.ExtractedStream
import com.metrolist.innertubex.extraction.StreamExtractor
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopInnerTubeXPlayerTest {
    @Test
    fun alwaysRequestsHighQualityAndDisablesHlsAndSabr() = runBlocking {
        val fake = CapturingExtractor()
        val player = DesktopInnerTubeXPlayer(fake)

        val resolved = player.resolve("WcHpojJz8Bc")

        assertEquals(AudioQuality.HIGH, fake.quality)
        assertFalse(requireNotNull(fake.hints).allowHls)
        assertFalse(requireNotNull(fake.hints).allowSabr)
        assertTrue(requireNotNull(fake.hints).allowBoundedRange)
        assertEquals(251, resolved.itag)
        assertEquals(144_747, resolved.bitrate)
        assertTrue("signed.example" !in resolved.toString())
        assertTrue("Authorization" !in resolved.toString())
    }

    private class CapturingExtractor : StreamExtractor {
        var quality: AudioQuality? = null
        var hints: ContentHints? = null

        override suspend fun prewarm() = Unit

        override suspend fun extract(
            videoId: String,
            hints: ContentHints,
            excludedClients: Set<String>,
            audioQuality: AudioQuality,
            clientPlaybackNonce: String,
        ): ExtractedStream {
            this.quality = audioQuality
            this.hints = hints

            return ExtractedStream(
                videoId = videoId,
                audioUrl = "https://signed.example/videoplayback",
                headers = mapOf("Authorization" to "secret"),
                loudnessDb = -6.93,
                expiresAt = null,
                contentLengthBytes = 3_856_993,
                itag = 251,
                mimeType = "audio/webm",
                codecs = "opus",
                bitrate = 144_747,
                sampleRate = 48_000,
                clientName = "WEB_REMIX",
                profileId = "WEB_REMIX",
                requireBoundedRange = false,
                rangeChunkSizeBytes = 1_048_576,
                useRangeChunks = false,
            )
        }
    }
}
