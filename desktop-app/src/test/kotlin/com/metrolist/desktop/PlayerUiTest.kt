package com.metrolist.desktop

import com.metrolist.desktop.player.PlaybackAudioFormat
import com.metrolist.desktop.player.PlayerState
import com.metrolist.innertube.models.SongItem
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerUiTest {
    @Test
    fun nowPlayingStateExpandsCollapsesAndTogglesQueue() {
        val initial = NowPlayingUiState()

        val expanded = initial.toggleExpanded()
        assertEquals(true, expanded.expanded)
        assertEquals(true, expanded.queueVisible)

        val hiddenQueue = expanded.toggleQueue()
        assertEquals(false, hiddenQueue.queueVisible)

        val collapsed = hiddenQueue.toggleExpanded()
        assertEquals(false, collapsed.expanded)
        assertEquals(false, collapsed.queueVisible)
    }

    @Test
    fun miniPlayerIsHiddenWhileExpandedNowPlayingIsOpen() {
        assertEquals(true, shouldShowMiniPlayer(NowPlayingUiState(expanded = false)))
        assertEquals(false, shouldShowMiniPlayer(NowPlayingUiState(expanded = true)))
    }

    @Test
    fun playerSurfaceStatusDistinguishesEmptyReadyAndError() {
        assertEquals(PlayerSurfaceStatus.EMPTY, playerSurfaceStatus(PlayerState()))
        assertEquals(
            PlayerSurfaceStatus.READY,
            playerSurfaceStatus(PlayerState(currentSong = song("a"))),
        )
        assertEquals(
            PlayerSurfaceStatus.ERROR,
            playerSurfaceStatus(PlayerState(currentSong = song("a"), error = "boom")),
        )
    }

    @Test
    fun queuePresentationMarksHistoryCurrentAndUpcoming() {
        val state =
            PlayerState(
                currentSong = song("b"),
                queue = listOf(song("a"), song("b"), song("c"), song("d")),
                queueIndex = 1,
            )

        assertEquals("Played", queueItemMarker(state, 0))
        assertEquals("Playing", queueItemMarker(state, 1))
        assertEquals("Up next", queueItemMarker(state, 2))
        assertEquals("Upcoming", queueItemMarker(state, 3))
    }

    @Test
    fun queueActionEnablementProtectsCurrentAndHistory() {
        val state =
            PlayerState(
                currentSong = song("b"),
                queue = listOf(song("a"), song("b"), song("c"), song("d")),
                queueIndex = 1,
            )

        assertEquals(
            QueueItemActions(canJump = true, canMoveUp = false, canMoveDown = false, canRemove = false),
            queueItemActions(state, 0),
        )
        assertEquals(
            QueueItemActions(canJump = false, canMoveUp = false, canMoveDown = false, canRemove = false),
            queueItemActions(state, 1),
        )
        assertEquals(
            QueueItemActions(canJump = true, canMoveUp = false, canMoveDown = true, canRemove = true),
            queueItemActions(state, 2),
        )
        assertEquals(
            QueueItemActions(canJump = true, canMoveUp = true, canMoveDown = false, canRemove = true),
            queueItemActions(state, 3),
        )
        assertEquals(true, canClearUpcoming(state))
    }

    @Test
    fun clearUpcomingDisabledWhenNothingIsUpcoming() {
        val state =
            PlayerState(
                currentSong = song("b"),
                queue = listOf(song("a"), song("b")),
                queueIndex = 1,
            )

        assertEquals(false, canClearUpcoming(state))
    }

    @Test
    fun formatsPlaybackTimeForPlayerBar() {
        assertEquals("0:00", formatPlaybackTime(null))
        assertEquals("0:00", formatPlaybackTime(0L))
        assertEquals("0:42", formatPlaybackTime(42_000L))
        assertEquals("3:51", formatPlaybackTime(231_000L))
        assertEquals("1:02:03", formatPlaybackTime(3_723_000L))
    }

    @Test
    fun formatsRealAudioQualityWithoutInventingMissingValues() {
        assertEquals(
            "Opus · 256 kbps · 48 kHz",
            formatAudioQuality(
                PlaybackAudioFormat(
                    itag = 251,
                    mimeType = "audio/webm",
                    codecs = "opus",
                    bitrate = 256_000,
                    sampleRate = 48_000,
                ),
            ),
        )
        assertEquals(
            "AAC · 128 kbps · 44.1 kHz",
            formatAudioQuality(
                PlaybackAudioFormat(
                    itag = 140,
                    mimeType = "audio/mp4",
                    codecs = "mp4a.40.2",
                    bitrate = 128_000,
                    sampleRate = 44_100,
                ),
            ),
        )
        assertEquals(
            "144 kbps",
            formatAudioQuality(
                PlaybackAudioFormat(
                    itag = null,
                    mimeType = null,
                    codecs = null,
                    bitrate = 144_000,
                    sampleRate = null,
                ),
            ),
        )
        assertEquals(null, formatAudioQuality(null))
    }

    private fun song(id: String) =
        SongItem(
            id = id,
            title = id,
            artists = emptyList(),
            thumbnail = "",
        )

    @Test
    fun formatsEffectiveBitrateFromBytesAndDurationInsteadOfBogusReportedValue() {
        assertEquals(
            "AAC · ~129.3 kbps · 44.1 kHz",
            formatAudioQuality(
                format =
                    PlaybackAudioFormat(
                        itag = 140,
                        mimeType = "audio/mp4",
                        codecs = "mp4a.40.2",
                        bitrate = 640_000,
                        sampleRate = 44_100,
                        contentLength = 3_879_425L,
                    ),
                durationSeconds = 240,
            ),
        )
    }
}
