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
    fun auditAudioQualityParsesKnownValuesAndDefaultsToHigh() {
        assertEquals(AudioQuality.AUTO, auditAudioQuality("auto"))
        assertEquals(AudioQuality.MP4, auditAudioQuality("MP4"))
        assertEquals(AudioQuality.LOW, auditAudioQuality(" low "))
        assertEquals(AudioQuality.HIGH, auditAudioQuality(null))
        assertEquals(AudioQuality.HIGH, auditAudioQuality("unknown"))
    }

    @Test
    fun auditExcludedClientsParsesCommaSeparatedNames() {
        assertEquals(
            setOf("VISIONOS", "WEB_REMIX"),
            auditExcludedClients(" VISIONOS, WEB_REMIX, VISIONOS, "),
        )
        assertEquals(emptySet(), auditExcludedClients(null))
        assertEquals(emptySet(), auditExcludedClients("   "))
    }

    @Test
    fun auditResolutionCanOverrideAudioQualityWithoutChangingNormalResolve() = runBlocking {
        val fake = CapturingExtractor()
        val player = DesktopInnerTubeXPlayer(fake)

        player.resolveForAudit(
            "WcHpojJz8Bc",
            excludedClients = emptySet(),
            audioQuality = AudioQuality.AUTO,
        )

        assertEquals(AudioQuality.AUTO, fake.quality)
    }

    @Test
    fun auditResolutionCanExcludeAClientWithoutChangingNormalResolve() = runBlocking {
        val fake = CapturingExtractor()
        val player = DesktopInnerTubeXPlayer(fake)

        player.resolveForAudit("WcHpojJz8Bc", excludedClients = setOf("VISIONOS"))

        assertEquals(setOf("VISIONOS"), fake.excludedClients)
        assertEquals(AudioQuality.HIGH, fake.quality)
    }

    @Test
    fun alwaysRequestsHighQualityAndDisablesHlsAndSabr() = runBlocking {
        val fake = CapturingExtractor()
        val player = DesktopInnerTubeXPlayer(fake)

        val resolved = player.resolve("WcHpojJz8Bc")

        assertEquals(AudioQuality.HIGH, fake.quality)
        assertFalse(requireNotNull(fake.hints).allowHls)
        assertFalse(requireNotNull(fake.hints).allowSabr)
        assertFalse(requireNotNull(fake.hints).allowBoundedRange)
        assertEquals(251, resolved.itag)
        assertEquals("audio/webm", resolved.mimeType)
        assertEquals("opus", resolved.codecs)
        assertEquals(144_747, resolved.bitrate)
        assertEquals(48_000, resolved.sampleRate)
        assertTrue("signed.example" !in resolved.toString())
        assertTrue("Authorization" !in resolved.toString())
    }

    private class CapturingExtractor : StreamExtractor {
        var quality: AudioQuality? = null
        var hints: ContentHints? = null
        var excludedClients: Set<String> = emptySet()

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
            this.excludedClients = excludedClients

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
