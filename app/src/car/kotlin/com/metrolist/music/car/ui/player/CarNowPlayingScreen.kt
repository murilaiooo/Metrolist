package com.metrolist.music.car.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import com.metrolist.music.R
import com.metrolist.music.car.ui.CarViewportMetrics
import com.metrolist.music.playback.PlayerConnection
import kotlinx.coroutines.delay

@Composable
fun CarNowPlayingScreen(
    connection: PlayerConnection?,
    viewport: CarViewportMetrics,
) {
    val density = LocalDensity.current.density
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

    Row(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(viewport.pxToDp(28f, density)),
        horizontalArrangement = Arrangement.spacedBy(viewport.pxToDp(36f, density)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val artworkSize = viewport.pxToDp(430f, density)
        Box(
            modifier =
                Modifier
                    .size(artworkSize)
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
                    modifier = Modifier.size(viewport.pxToDp(150f, density)),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
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

            Spacer(Modifier.height(viewport.pxToDp(24f, density)))

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
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val secondarySize = viewport.pxToDp(72f, density)
                val primarySize = viewport.pxToDp(96f, density)

                IconButton(
                    onClick = { connection?.seekToPrevious() },
                    enabled = metadata != null,
                    modifier = Modifier.size(secondarySize),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.skip_previous),
                        contentDescription = stringResource(R.string.car_previous),
                        modifier = Modifier.size(viewport.pxToDp(40f, density)),
                    )
                }

                Spacer(Modifier.width(viewport.pxToDp(24f, density)))

                FilledIconButton(
                    onClick = { connection?.togglePlayPause() },
                    enabled = metadata != null,
                    modifier = Modifier.size(primarySize),
                ) {
                    Icon(
                        painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                        contentDescription = stringResource(R.string.car_play_pause),
                        modifier = Modifier.size(viewport.pxToDp(48f, density)),
                    )
                }

                Spacer(Modifier.width(viewport.pxToDp(24f, density)))

                IconButton(
                    onClick = { connection?.seekToNext() },
                    enabled = metadata != null,
                    modifier = Modifier.size(secondarySize),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.skip_next),
                        contentDescription = stringResource(R.string.car_next),
                        modifier = Modifier.size(viewport.pxToDp(40f, density)),
                    )
                }
            }

            Spacer(Modifier.height(viewport.pxToDp(18f, density)))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                IconButton(
                    onClick = {
                        runCatching {
                            connection?.player?.shuffleModeEnabled = !shuffleEnabled
                        }
                    },
                    enabled = metadata != null,
                    modifier = Modifier.size(viewport.pxToDp(68f, density)),
                ) {
                    Icon(
                        painter = painterResource(if (shuffleEnabled) R.drawable.shuffle_on else R.drawable.shuffle),
                        contentDescription = stringResource(R.string.car_shuffle),
                    )
                }

                IconButton(
                    onClick = { showQueue = true },
                    enabled = queue.isNotEmpty(),
                    modifier = Modifier.size(viewport.pxToDp(68f, density)),
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
                    modifier = Modifier.size(viewport.pxToDp(68f, density)),
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
                Column(verticalArrangement = Arrangement.spacedBy(viewport.pxToDp(8f, density))) {
                    queue.take(10).forEach { window ->
                        Text(
                            text = window.mediaItem.mediaMetadata.title?.toString().orEmpty(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
        )
    }
}
