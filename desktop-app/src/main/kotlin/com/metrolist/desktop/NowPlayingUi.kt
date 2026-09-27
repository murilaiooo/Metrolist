package com.metrolist.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.metrolist.desktop.offline.OfflineState
import com.metrolist.desktop.player.PlayerState
import com.metrolist.desktop.player.RepeatMode
import kotlinx.coroutines.launch

@Composable
internal fun ProfessionalMiniPlayer(
    runtime: DesktopRuntime,
    state: PlayerState,
    onExpand: (queueVisible: Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val duration = state.durationMs?.coerceAtLeast(1L) ?: 1L

    if (state.currentSong == null) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = "Nothing playing",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "Choose something to play",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val song = requireNotNull(state.currentSong)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(84.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier =
                Modifier
                    .widthIn(min = 236.dp, max = 320.dp)
                    .fillMaxHeight()
                    .clickable { onExpand(false) }
                    .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtworkThumbnail(
                runtime = runtime,
                thumbnailUrl = song.thumbnail,
                size = 52.dp,
                contentDescription = song.title,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    song.artists.joinToString(", ") { it.name }.ifBlank { "YouTube Music" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                IconButton(
                    onClick = { scope.launch { runCatching { runtime.previous() } } },
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous")
                }
                FilledIconButton(
                    onClick = {
                        when {
                            state.isPlaying -> runtime.pause()
                            state.ended -> {
                                runtime.seek(0L)
                                runtime.resume()
                            }
                            else -> runtime.resume()
                        }
                    },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector =
                            when {
                                state.isPlaying -> Icons.Rounded.Pause
                                state.ended -> Icons.Rounded.Replay
                                else -> Icons.Rounded.PlayArrow
                            },
                        contentDescription =
                            when {
                                state.isPlaying -> "Pause"
                                state.ended -> "Replay"
                                else -> "Play"
                            },
                    )
                }
                IconButton(
                    onClick = { scope.launch { runCatching { runtime.next() } } },
                    enabled = state.canSkipNext,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Next")
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(formatPlaybackTime(state.positionMs), style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = state.positionMs.coerceIn(0L, duration).toFloat(),
                    onValueChange = { runtime.seek(it.toLong()) },
                    valueRange = 0f..duration.toFloat(),
                    modifier = Modifier.weight(1f).height(36.dp).padding(horizontal = 10.dp),
                )
                Text(formatPlaybackTime(state.durationMs), style = MaterialTheme.typography.labelSmall)
            }
        }

        Row(
            modifier = Modifier.widthIn(min = 236.dp, max = 330.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            formatAudioQuality(state.audioFormat, song.duration)?.let { quality ->
                Text(
                    quality,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
            }
            Icon(
                Icons.AutoMirrored.Rounded.VolumeUp,
                contentDescription = "Volume",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = state.volume.toFloat(),
                onValueChange = { runtime.setVolume(it.toInt()) },
                valueRange = 0f..100f,
                modifier = Modifier.width(104.dp).height(36.dp),
            )
            IconButton(
                onClick = { onExpand(true) },
                enabled = state.queue.isNotEmpty(),
                modifier = Modifier.size(44.dp),
            ) {
                Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = "Open queue")
            }
        }
    }
}

@Composable
internal fun ExpandedNowPlaying(
    runtime: DesktopRuntime,
    uiState: NowPlayingUiState,
    onUiStateChange: (NowPlayingUiState) -> Unit,
) {
    val playerFlow = runtime.playerState
    if (playerFlow == null) {
        EmptyNowPlaying("Player runtime unavailable")
        return
    }
    val state by playerFlow.collectAsState()
    val downloads by runtime.downloads.collectAsState()
    val currentDownload = state.currentSong?.id?.let { id -> downloads.firstOrNull { it.videoId == id } }
    val scope = rememberCoroutineScope()

    when (playerSurfaceStatus(state)) {
            PlayerSurfaceStatus.EMPTY -> EmptyNowPlaying("Choose something to play")
            PlayerSurfaceStatus.READY,
            PlayerSurfaceStatus.ERROR,
            -> {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                ) {
                    val availableWidth = maxWidth
                    val wide = availableWidth >= 920.dp
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = { onUiStateChange(uiState.copy(expanded = false)) },
                            ) {
                                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Close Now Playing")
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Now Playing", style = MaterialTheme.typography.titleLarge)
                                Text(
                                    state.currentSong?.album?.name ?: "MetroList",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(
                                onClick = { onUiStateChange(uiState.toggleQueue()) },
                                enabled = state.queue.isNotEmpty(),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.QueueMusic,
                                    contentDescription = if (uiState.queueVisible) "Hide queue" else "Show queue",
                                    tint =
                                        if (uiState.queueVisible) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                )
                            }
                        }

                        if (wide) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(28.dp),
                            ) {
                                NowPlayingHero(
                                    runtime = runtime,
                                    state = state,
                                    downloadState = currentDownload?.state,
                                    artworkSize = 360.dp,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                )
                                if (uiState.queueVisible) {
                                    NowPlayingQueue(
                                        state = state,
                                        modifier = Modifier.widthIn(min = 340.dp, max = 440.dp).fillMaxHeight(),
                                        onJump = { index ->
                                            scope.launch { runCatching { runtime.playQueue(state.queue, index) } }
                                        },
                                        onMove = { from, to ->
                                            scope.launch { runCatching { runtime.moveQueueItem(from, to) } }
                                        },
                                        onRemove = { index ->
                                            scope.launch { runCatching { runtime.removeQueueItem(index) } }
                                        },
                                        onClearUpcoming = {
                                            scope.launch { runCatching { runtime.clearUpcoming() } }
                                        },
                                    )
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(18.dp),
                            ) {
                                NowPlayingHero(
                                    runtime = runtime,
                                    state = state,
                                    downloadState = currentDownload?.state,
                                    artworkSize = if (availableWidth < 600.dp) 200.dp else 240.dp,
                                    modifier = Modifier.fillMaxWidth().weight(if (uiState.queueVisible) 1f else 2f),
                                )
                                if (uiState.queueVisible) {
                                    NowPlayingQueue(
                                        state = state,
                                        modifier = Modifier.fillMaxWidth().weight(1f),
                                        onJump = { index ->
                                            scope.launch { runCatching { runtime.playQueue(state.queue, index) } }
                                        },
                                        onMove = { from, to ->
                                            scope.launch { runCatching { runtime.moveQueueItem(from, to) } }
                                        },
                                        onRemove = { index ->
                                            scope.launch { runCatching { runtime.removeQueueItem(index) } }
                                        },
                                        onClearUpcoming = {
                                            scope.launch { runCatching { runtime.clearUpcoming() } }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
}

@Composable
private fun EmptyNowPlaying(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            Text(message, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun NowPlayingHero(
    runtime: DesktopRuntime,
    state: PlayerState,
    downloadState: OfflineState?,
    artworkSize: Dp,
    modifier: Modifier,
) {
    val song = state.currentSong ?: return
    val scope = rememberCoroutineScope()
    val duration = state.durationMs?.coerceAtLeast(1L) ?: 1L

    Column(
        modifier = modifier.padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ArtworkThumbnail(
            runtime = runtime,
            thumbnailUrl = song.thumbnail,
            size = artworkSize,
            contentDescription = song.title,
        )
        Spacer(Modifier.height(22.dp))
        Text(
            song.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            song.artists.joinToString(", ") { it.name }.ifBlank { "YouTube Music" },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        song.album?.name?.let { album ->
            Text(
                album,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(14.dp))
        formatAudioQuality(state.audioFormat, song.duration)?.let { quality ->
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    quality,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        state.error?.let { error ->
            Spacer(Modifier.height(8.dp))
            Text(
                "Playback error · $error",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(14.dp))
        Slider(
            value = state.positionMs.coerceIn(0L, duration).toFloat(),
            onValueChange = { runtime.seek(it.toLong()) },
            valueRange = 0f..duration.toFloat(),
            modifier = Modifier.fillMaxWidth().widthIn(max = 620.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().widthIn(max = 620.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(formatPlaybackTime(state.positionMs), style = MaterialTheme.typography.labelSmall)
            Text(formatPlaybackTime(state.durationMs), style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = { scope.launch { runCatching { runtime.toggleShuffle() } } }) {
                Icon(
                    Icons.Rounded.Shuffle,
                    contentDescription = if (state.shuffleEnabled) "Disable shuffle" else "Enable shuffle",
                    tint = if (state.shuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { scope.launch { runCatching { runtime.previous() } } }) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous")
            }
            FilledIconButton(
                onClick = {
                    when {
                        state.isPlaying -> runtime.pause()
                        state.ended -> {
                            runtime.seek(0L)
                            runtime.resume()
                        }
                        else -> runtime.resume()
                    }
                },
                modifier = Modifier.size(58.dp),
            ) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Rounded.Pause else if (state.ended) Icons.Rounded.Replay else Icons.Rounded.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else if (state.ended) "Replay" else "Play",
                    modifier = Modifier.size(30.dp),
                )
            }
            IconButton(
                onClick = { scope.launch { runCatching { runtime.next() } } },
                enabled = state.canSkipNext,
            ) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "Next")
            }
            IconButton(onClick = { scope.launch { runCatching { runtime.cycleRepeatMode() } } }) {
                Icon(
                    imageVector = if (state.repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    contentDescription = repeatDescription(state.repeatMode),
                    tint = if (state.repeatMode == RepeatMode.OFF) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = "Volume")
            Slider(
                value = state.volume.toFloat(),
                onValueChange = { runtime.setVolume(it.toInt()) },
                valueRange = 0f..100f,
                modifier = Modifier.width(180.dp),
            )
            Text("${state.volume}%", style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(10.dp))
        when (downloadState) {
            OfflineState.COMPLETED ->
                TextButton(onClick = { runtime.remove(song.id) }) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Downloaded")
                }

            OfflineState.DOWNLOADING,
            OfflineState.QUEUED,
            ->
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (downloadState == OfflineState.DOWNLOADING) "Downloading" else "Queued",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

            else ->
                TextButton(onClick = { runtime.enqueue(song) }) {
                    Icon(Icons.Rounded.Download, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Download")
                }
        }
    }
}

@Composable
private fun NowPlayingQueue(
    state: PlayerState,
    modifier: Modifier,
    onJump: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    onClearUpcoming: () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Queue", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${state.queue.size} tracks · ${state.queueIndex + 1} playing",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = onClearUpcoming,
                    enabled = canClearUpcoming(state),
                ) {
                    Icon(Icons.Rounded.ClearAll, contentDescription = "Clear upcoming")
                }
            }
            HorizontalDivider()
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(
                    items = state.queue,
                    key = { index, song -> "$index:${song.id}" },
                ) { index, song ->
                    val actions = queueItemActions(state, index)
                    val isCurrent = index == state.queueIndex
                    val isNext = index == state.queueIndex + 1
                    val hoverInteraction =
                        remember(index, song.id) { MutableInteractionSource() }
                    val hovered by hoverInteraction.collectIsHoveredAsState()

                    Surface(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .hoverable(hoverInteraction),
                        shape = MaterialTheme.shapes.medium,
                        color =
                            when {
                                isCurrent -> MaterialTheme.colorScheme.secondaryContainer
                                hovered -> MaterialTheme.colorScheme.surfaceContainerHigh
                                else -> Color.Transparent
                            },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    song.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    song.artists.joinToString(", ") { it.name }.ifBlank { "YouTube Music" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (isCurrent || isNext) {
                                    Text(
                                        if (isCurrent) "Playing" else "Up next",
                                        style = MaterialTheme.typography.labelSmall,
                                        color =
                                            if (isCurrent) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                    )
                                }
                            }

                            if (hovered && actions.canJump) {
                                IconButton(
                                    onClick = { onJump(index) },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Play this track")
                                }
                            }
                            if (hovered && actions.canMoveUp) {
                                IconButton(
                                    onClick = { onMove(index, index - 1) },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move up")
                                }
                            }
                            if (hovered && actions.canMoveDown) {
                                IconButton(
                                    onClick = { onMove(index, index + 1) },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(Icons.Rounded.ArrowDownward, contentDescription = "Move down")
                                }
                            }
                            if (hovered && actions.canRemove) {
                                IconButton(
                                    onClick = { onRemove(index) },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove from queue")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun repeatDescription(mode: RepeatMode): String =
    when (mode) {
        RepeatMode.OFF -> "Enable repeat"
        RepeatMode.ALL -> "Repeat all"
        RepeatMode.ONE -> "Repeat one"
    }
