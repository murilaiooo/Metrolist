package com.metrolist.music.car.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import com.metrolist.music.R
import com.metrolist.music.car.library.CarUnifiedSong
import com.metrolist.music.car.preferences.CarDislikeRepository
import com.metrolist.music.car.preferences.CarDislikeSnapshot
import com.metrolist.music.car.preferences.performCarDislike
import com.metrolist.music.car.ui.CarViewportMetrics
import com.metrolist.music.playback.PlayerConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun CarNowPlayingScreen(
    connection: PlayerConnection?,
    viewport: CarViewportMetrics,
    dislikeRepository: CarDislikeRepository,
    rawCatalog: List<CarUnifiedSong>,
) {
    val density = LocalDensity.current.density
    val layoutDirection = LocalLayoutDirection.current
    val gestureScope = rememberCoroutineScope()
    val artworkOffset = remember { Animatable(0f) }
    var artworkDragStartMs by remember { mutableLongStateOf(0L) }
    var artworkDragDistance by remember { mutableFloatStateOf(0f) }
    val metadata by
        connection?.mediaMetadata?.collectAsStateWithLifecycle()
            ?: remember { mutableStateOf(null) }
    val isPlaying by
        connection?.isEffectivelyPlaying?.collectAsStateWithLifecycle()
            ?: remember { mutableStateOf(false) }
    val shuffleEnabled by
        connection?.shuffleModeEnabled?.collectAsStateWithLifecycle()
            ?: remember { mutableStateOf(false) }
    val repeatMode by
        connection?.repeatMode?.collectAsStateWithLifecycle()
            ?: remember { mutableStateOf(Player.REPEAT_MODE_OFF) }
    val queue by
        connection?.queueWindows?.collectAsStateWithLifecycle()
            ?: remember { mutableStateOf(emptyList()) }
    val dislikedSongIds by dislikeRepository.activeSongIds.collectAsStateWithLifecycle()

    var positionMs by remember(metadata?.id) { mutableLongStateOf(0L) }
    var seeking by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }

    LaunchedEffect(connection, metadata?.id) {
        while (true) {
            if (!seeking) {
                positionMs = runCatching { connection?.player?.currentPosition ?: 0L }.getOrDefault(0L)
            }
            delay(500)
        }
    }

    val durationMs =
        runCatching {
            connection?.player?.duration
                ?.takeIf { it != C.TIME_UNSET && it > 0L }
        }.getOrNull() ?: ((metadata?.duration ?: 0).coerceAtLeast(0) * 1000L)

    val layout = CarNowPlayingLayoutMetrics.forViewport(viewport)
    val horizontalPadding = (layout.horizontalPaddingPx / density).dp
    val verticalPadding = (layout.verticalPaddingPx / density).dp
    val gap = (layout.gapPx / density).dp
    val controlsWidth = (layout.controlsWidthPx / density).dp
    val artworkSize = (layout.artworkSizePx / density).dp
    val previousNextSize = (layout.previousNextHitPx / density).dp
    val playPauseSize = (layout.playPauseHitPx / density).dp
    val secondaryActionSize = (layout.secondaryHitPx / density).dp

    Row(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(
                    horizontal = horizontalPadding,
                    vertical = verticalPadding,
                ),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier =
                Modifier
                    .width(controlsWidth)
                    .fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = metadata?.title ?: stringResource(R.string.car_no_music),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            val artist = metadata?.artists?.joinToString(", ") { it.name }.orEmpty()
            if (artist.isNotBlank()) {
                Text(
                    text = artist,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(viewport.pxToDp(18f, density)))

            Slider(
                value = positionMs.coerceIn(0L, durationMs.coerceAtLeast(1L)).toFloat(),
                onValueChange = {
                    seeking = true
                    positionMs = it.toLong()
                },
                onValueChangeFinished = {
                    connection?.seekTo(positionMs)
                    seeking = false
                },
                valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
                enabled = metadata != null && durationMs > 0L,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatCarPlaybackTime(positionMs),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatCarPlaybackTime(durationMs),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(viewport.pxToDp(8f, density)))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        viewport.pxToDp(16f, density),
                        Alignment.CenterHorizontally,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { connection?.seekToPrevious() },
                    enabled = metadata != null,
                    modifier = Modifier.size(previousNextSize),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.skip_previous),
                        contentDescription = stringResource(R.string.car_previous),
                        modifier = Modifier.size((previousNextSize.value * 0.42f).dp),
                    )
                }

                FilledIconButton(
                    onClick = { connection?.togglePlayPause() },
                    enabled = metadata != null,
                    modifier = Modifier.size(playPauseSize),
                ) {
                    Icon(
                        painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                        contentDescription = stringResource(R.string.car_play_pause),
                        modifier = Modifier.size((playPauseSize.value * 0.42f).dp),
                    )
                }

                IconButton(
                    onClick = { connection?.seekToNext() },
                    enabled = metadata != null,
                    modifier = Modifier.size(previousNextSize),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.skip_next),
                        contentDescription = stringResource(R.string.car_next),
                        modifier = Modifier.size((previousNextSize.value * 0.42f).dp),
                    )
                }
            }

            Spacer(Modifier.height(viewport.pxToDp(8f, density)))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val currentMetadata = metadata
                val currentDisliked = currentMetadata?.id in dislikedSongIds
                val likeGreen = Color(0xFF45B96D)
                val dislikeRed = Color(0xFFE05252)

                FilledIconButton(
                    onClick = {
                        val song = currentMetadata ?: return@FilledIconButton
                        if (song.id in dislikedSongIds) return@FilledIconButton
                        val source = rawCatalog.firstOrNull { it.id == song.id }
                        val snapshot =
                            CarDislikeSnapshot(
                                songId = song.id,
                                title = song.title,
                                artists = song.artists.map { it.name },
                                album = source?.album ?: song.album?.title,
                                sourceKinds = source?.sourceKinds?.map { it.name }?.toSet().orEmpty(),
                            )
                        gestureScope.launch {
                            performCarDislike(
                                persist = {
                                    withContext(Dispatchers.IO) {
                                        dislikeRepository.dislike(snapshot)
                                    }
                                },
                                mutatePlayback = {
                                    connection?.player?.let { player ->
                                        val currentIndex = player.currentMediaItemIndex
                                        if (currentIndex !in 0 until player.mediaItemCount) return@let
                                        if (player.mediaItemCount <= 1) {
                                            player.clearMediaItems()
                                        } else {
                                            player.removeMediaItem(currentIndex)
                                            if (
                                                player.playbackState == Player.STATE_IDLE ||
                                                    player.playbackState == Player.STATE_ENDED
                                            ) {
                                                player.prepare()
                                            }
                                            player.playWhenReady = true
                                        }
                                    }
                                },
                            )
                        }
                    },
                    enabled = currentMetadata != null,
                    colors =
                        IconButtonDefaults.filledIconButtonColors(
                            containerColor =
                                if (currentDisliked) {
                                    dislikeRed
                                } else {
                                    dislikeRed.copy(alpha = 0.16f)
                                },
                            contentColor = if (currentDisliked) Color.White else dislikeRed,
                        ),
                    modifier = Modifier.size(secondaryActionSize),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.thumb_down),
                        contentDescription = stringResource(R.string.car_dislike),
                    )
                }

                Spacer(Modifier.weight(1f))

                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(viewport.pxToDp(6f, density)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            runCatching {
                                connection?.player?.shuffleModeEnabled = !shuffleEnabled
                            }
                        },
                        enabled = metadata != null,
                        modifier = Modifier.size(secondaryActionSize),
                    ) {
                        Icon(
                            painter = painterResource(if (shuffleEnabled) R.drawable.shuffle_on else R.drawable.shuffle),
                            contentDescription = stringResource(R.string.car_shuffle),
                        )
                    }

                    IconButton(
                        onClick = { showQueue = true },
                        enabled = queue.isNotEmpty(),
                        modifier = Modifier.size(secondaryActionSize),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.queue_music),
                            contentDescription = stringResource(R.string.car_open_queue),
                        )
                    }

                    IconButton(
                        onClick = {
                            runCatching {
                                val player = connection?.player ?: return@runCatching
                                player.repeatMode =
                                    when (player.repeatMode) {
                                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                        else -> Player.REPEAT_MODE_OFF
                                    }
                            }
                        },
                        enabled = metadata != null,
                        modifier = Modifier.size(secondaryActionSize),
                    ) {
                        Icon(
                            painter =
                                painterResource(
                                    when (repeatMode) {
                                        Player.REPEAT_MODE_ONE -> R.drawable.repeat_one_on
                                        Player.REPEAT_MODE_ALL -> R.drawable.repeat_on
                                        else -> R.drawable.repeat
                                    },
                                ),
                            contentDescription = stringResource(R.string.car_repeat),
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                FilledIconButton(
                    onClick = {
                        val song = currentMetadata ?: return@FilledIconButton
                        if (song.id in dislikedSongIds) {
                            gestureScope.launch {
                                withContext(Dispatchers.IO) {
                                    dislikeRepository.undo(song.id)
                                }
                                if (!song.liked) {
                                    connection?.toggleLike()
                                }
                            }
                        } else {
                            connection?.toggleLike()
                        }
                    },
                    enabled = currentMetadata != null,
                    colors =
                        IconButtonDefaults.filledIconButtonColors(
                            containerColor =
                                if (currentMetadata?.liked == true) {
                                    likeGreen
                                } else {
                                    likeGreen.copy(alpha = 0.16f)
                                },
                            contentColor =
                                if (currentMetadata?.liked == true) Color.White else likeGreen,
                        ),
                    modifier = Modifier.size(secondaryActionSize),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.thumb_up),
                        contentDescription = stringResource(R.string.car_like),
                    )
                }
            }
        }

        Box(
            modifier =
                Modifier
                    .size(artworkSize)
                    .offset { IntOffset(artworkOffset.value.roundToInt(), 0) }
                    .pointerInput(connection, layout.artworkSizePx, layoutDirection) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                artworkDragStartMs = System.currentTimeMillis()
                                artworkDragDistance = 0f
                                gestureScope.launch { artworkOffset.stop() }
                            },
                            onDragCancel = {
                                gestureScope.launch {
                                    artworkOffset.animateTo(
                                        targetValue = 0f,
                                        animationSpec = tween(durationMillis = 170),
                                    )
                                }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                artworkDragDistance += abs(dragAmount)

                                val currentOffset = artworkOffset.value
                                val proposedOffset = currentOffset + dragAmount
                                val isRtl = layoutDirection == LayoutDirection.Rtl
                                val canPrevious =
                                    connection?.player?.previousMediaItemIndex
                                        ?.let { it != -1 } == true
                                val canNext =
                                    connection?.player?.nextMediaItemIndex
                                        ?.let { it != -1 } == true
                                val wantsPrevious =
                                    if (isRtl) proposedOffset < 0f else proposedOffset > 0f
                                val canMoveInDirection =
                                    if (wantsPrevious) canPrevious else canNext
                                val returningToCenter =
                                    currentOffset != 0f &&
                                        dragAmount != 0f &&
                                        (currentOffset > 0f) != (dragAmount > 0f)
                                val resistance =
                                    if (canMoveInDirection || returningToCenter) 1f else 0.18f
                                val maxOffset = layout.artworkSizePx * 0.48f
                                val target =
                                    (currentOffset + (dragAmount * resistance))
                                        .coerceIn(-maxOffset, maxOffset)

                                gestureScope.launch {
                                    artworkOffset.snapTo(target)
                                }
                            },
                            onDragEnd = {
                                val elapsedMs =
                                    (System.currentTimeMillis() - artworkDragStartMs)
                                        .coerceAtLeast(1L)
                                val velocityPxPerMs = artworkDragDistance / elapsedMs.toFloat()
                                val canPrevious =
                                    connection?.player?.previousMediaItemIndex
                                        ?.let { it != -1 } == true
                                val canNext =
                                    connection?.player?.nextMediaItemIndex
                                        ?.let { it != -1 } == true
                                when (
                                    decideCarArtworkSwipe(
                                        offsetPx = artworkOffset.value,
                                        artworkWidthPx = layout.artworkSizePx,
                                        velocityPxPerMs = velocityPxPerMs,
                                        canPrevious = canPrevious,
                                        canNext = canNext,
                                        isRtl = layoutDirection == LayoutDirection.Rtl,
                                    )
                                ) {
                                    CarArtworkSwipeAction.PREVIOUS -> {
                                        connection?.player?.let { player ->
                                            player.seekToPreviousMediaItem()
                                            if (
                                                player.playbackState == Player.STATE_IDLE ||
                                                    player.playbackState == Player.STATE_ENDED
                                            ) {
                                                player.prepare()
                                            }
                                            player.playWhenReady = true
                                        }
                                    }
                                    CarArtworkSwipeAction.NEXT -> connection?.seekToNext()
                                    CarArtworkSwipeAction.NONE -> Unit
                                }
                                gestureScope.launch {
                                    artworkOffset.animateTo(
                                        targetValue = 0f,
                                        animationSpec = tween(durationMillis = 170),
                                    )
                                }
                            },
                        )
                    }
                    .clip(MaterialTheme.shapes.extraLarge),
            contentAlignment = Alignment.Center,
        ) {
            if (metadata?.thumbnailUrl != null) {
                AsyncImage(
                    model = metadata?.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.music_note),
                    contentDescription = null,
                    modifier = Modifier.size((artworkSize.value * 0.34f).dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showQueue) {
        AlertDialog(
            onDismissRequest = { showQueue = false },
            confirmButton = {
                TextButton(onClick = { showQueue = false }) {
                    Text(stringResource(R.string.close))
                }
            },
            title = { Text(stringResource(R.string.car_queue)) },
            text = {
                val currentIndex =
                    runCatching { connection?.player?.currentMediaItemIndex ?: -1 }
                        .getOrDefault(-1)
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = viewport.pxToDp(430f, density)),
                    verticalArrangement =
                        Arrangement.spacedBy(viewport.pxToDp(8f, density)),
                ) {
                    itemsIndexed(
                        items = queue,
                        key = { index, window ->
                            window.mediaItem.mediaId + ":" + index
                        },
                    ) { index, window ->
                        val isCurrent = index == currentIndex
                        Surface(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .sizeIn(minHeight = viewport.pxToDp(64f, density))
                                    .clickable {
                                        connection?.player?.seekToDefaultPosition(index)
                                        showQueue = false
                                    },
                            shape = MaterialTheme.shapes.medium,
                            tonalElevation = viewport.pxToDp(if (isCurrent) 5f else 1f, density),
                        ) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            horizontal = viewport.pxToDp(14f, density),
                                            vertical = viewport.pxToDp(12f, density),
                                        ),
                                horizontalArrangement =
                                    Arrangement.spacedBy(viewport.pxToDp(12f, density)),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = (index + 1).toString(),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = window.mediaItem.mediaMetadata.title?.toString().orEmpty(),
                                        style = MaterialTheme.typography.titleMedium,
                                        color =
                                            if (isCurrent) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    window.mediaItem.mediaMetadata.artist
                                        ?.toString()
                                        ?.takeIf(String::isNotBlank)
                                        ?.let { artist ->
                                            Text(
                                                text = artist,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                }
                            }
                        }
                    }
                }
            },
        )
    }
}


internal fun formatCarPlaybackTime(positionMs: Long): String {
    val totalSeconds = (positionMs.coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
