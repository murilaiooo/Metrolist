package com.metrolist.desktop

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.metrolist.desktop.player.PlaybackAudioFormat
import com.metrolist.desktop.player.PlayerState

@Composable
internal fun PlayerBar(
    runtime: DesktopRuntime,
    onExpand: (queueVisible: Boolean) -> Unit = {},
) {
    val playerFlow = runtime.playerState
    if (playerFlow == null) {
        Surface {
            Text(
                "Player runtime unavailable",
                modifier = Modifier.padding(12.dp),
            )
        }
        return
    }

    val state by playerFlow.collectAsState()

    Surface(
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        ProfessionalMiniPlayer(
            runtime = runtime,
            state = state,
            onExpand = onExpand,
        )
    }
}

internal fun formatAudioQuality(
    format: PlaybackAudioFormat?,
    durationSeconds: Int? = null,
): String? {
    format ?: return null

    val codec =
        format.codecs
            ?.lowercase()
            ?.let { normalized ->
                when {
                    "opus" in normalized -> "Opus"
                    "mp4a" in normalized || "aac" in normalized -> "AAC"
                    else -> format.codecs
                }
            }

    val contentLength = format.contentLength
    val effectiveBitrate =
        if (contentLength != null && contentLength > 0L && durationSeconds != null && durationSeconds > 0) {
            contentLength.toDouble() * 8.0 / durationSeconds.toDouble() / 1_000.0
        } else {
            null
        }
    val bitrate =
        effectiveBitrate
            ?.let { String.format(java.util.Locale.ROOT, "~%.1f kbps", it) }
            ?: format.bitrate?.takeIf { it > 0 }?.let { formatKiloRate(it, "kbps") }
    val sampleRate = format.sampleRate?.takeIf { it > 0 }?.let { formatKiloRate(it, "kHz") }

    return listOfNotNull(codec, bitrate, sampleRate)
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" · ")
}

private fun formatKiloRate(value: Int, unit: String): String =
    if (value % 1_000 == 0) {
        "${value / 1_000} $unit"
    } else {
        String.format(java.util.Locale.ROOT, "%.1f %s", value / 1_000.0, unit)
    }

data class NowPlayingUiState(
    val expanded: Boolean = false,
    val queueVisible: Boolean = true,
) {
    fun toggleExpanded(): NowPlayingUiState = copy(expanded = !expanded)
    fun toggleQueue(): NowPlayingUiState = copy(queueVisible = !queueVisible)
}

enum class PlayerSurfaceStatus {
    EMPTY,
    READY,
    ERROR,
}

data class QueueItemActions(
    val canJump: Boolean,
    val canMoveUp: Boolean,
    val canMoveDown: Boolean,
    val canRemove: Boolean,
)

internal fun playerSurfaceStatus(state: PlayerState): PlayerSurfaceStatus =
    when {
        state.currentSong == null -> PlayerSurfaceStatus.EMPTY
        state.error != null -> PlayerSurfaceStatus.ERROR
        else -> PlayerSurfaceStatus.READY
    }

internal fun queueItemMarker(
    state: PlayerState,
    index: Int,
): String =
    when {
        index == state.queueIndex -> "Playing"
        state.queueIndex in state.queue.indices && index < state.queueIndex -> "Played"
        state.queueIndex in state.queue.indices && index == state.queueIndex + 1 -> "Up next"
        else -> "Upcoming"
    }

internal fun queueItemActions(
    state: PlayerState,
    index: Int,
): QueueItemActions {
    val current = state.queueIndex
    if (index !in state.queue.indices || current !in state.queue.indices) {
        return QueueItemActions(false, false, false, false)
    }
    val isCurrent = index == current
    val isUpcoming = index > current
    return QueueItemActions(
        canJump = !isCurrent,
        canMoveUp = isUpcoming && index > current + 1,
        canMoveDown = isUpcoming && index < state.queue.lastIndex,
        canRemove = isUpcoming,
    )
}

internal fun canClearUpcoming(state: PlayerState): Boolean =
    state.queueIndex in state.queue.indices && state.queueIndex < state.queue.lastIndex

internal fun shouldShowMiniPlayer(uiState: NowPlayingUiState): Boolean = !uiState.expanded
