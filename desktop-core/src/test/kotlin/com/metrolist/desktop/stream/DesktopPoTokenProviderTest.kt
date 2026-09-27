package com.metrolist.desktop.stream

import com.metrolist.innertubex.extraction.strategy.PoTokenProviderKind
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopPoTokenProviderTest {
    @Test
    fun mapsHelperResultToWebBotguardTokenProvider() = runBlocking {
        var requestedVideoId: String? = null
        var requestedVisitorData: String? = null
        val provider =
            DesktopPoTokenProvider { videoId, visitorData ->
                requestedVideoId = videoId
                requestedVisitorData = visitorData
                DesktopPoTokenResponse(
                    playerRequestToken = "player-token",
                    streamingDataToken = "stream-token",
                    visitorData = visitorData,
                )
            }

        val result =
            provider.getPoToken(
                videoId = "video-123",
                visitorData = "visitor-456",
                cookie = "ignored-cookie",
            )

        assertEquals("video-123", requestedVideoId)
        assertEquals("visitor-456", requestedVisitorData)
        assertEquals("player-token", result?.playerRequestToken)
        assertEquals("stream-token", result?.streamingDataToken)
        assertEquals("visitor-456", result?.visitorData)
        assertEquals(setOf(PoTokenProviderKind.WEB_BOTGUARD), provider.capabilities.providers)
        assertTrue(provider.capabilities.usesWebView)
    }
}
