package com.metrolist.music.car.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.metrolist.music.R
import com.metrolist.music.car.ui.CarViewportMetrics
import com.metrolist.music.car.ui.theme.CarDesignTokens
import com.metrolist.music.models.MediaMetadata

enum class CarMiniPlayerAction {
    PREVIOUS,
    PLAY_PAUSE,
    NEXT,
}

data class CarMiniPlayerSizing(
    val rowHeightDp: Float,
    val artworkSizeDp: Float,
    val controlHitDp: Float,
)

fun carMiniPlayerActions(): List<CarMiniPlayerAction> =
    listOf(
        CarMiniPlayerAction.PREVIOUS,
        CarMiniPlayerAction.PLAY_PAUSE,
        CarMiniPlayerAction.NEXT,
    )

fun carMiniPlayerSizing(
    viewport: CarViewportMetrics,
    density: Float,
): CarMiniPlayerSizing {
    val safeDensity = density.takeIf { it.isFinite() && it > 0f } ?: 1f
    val minimumRow = 96f / safeDensity
    val minimumControl = 76f / safeDensity
    val minimumArtwork = 68f / safeDensity

    val rowHeight =
        maxOf(
            viewport.pxToDp(CarDesignTokens.MiniPlayerHeightPx, safeDensity).value,
            minimumRow,
        )
    val controlHit =
        maxOf(
            viewport.pxToDp(CarDesignTokens.MiniPlayerControlTargetPx, safeDensity).value,
            minimumControl,
        )
    val artwork =
        maxOf(
            viewport.pxToDp(82f, safeDensity).value,
            minimumArtwork,
        ).coerceAtMost(rowHeight - (16f / safeDensity))

    return CarMiniPlayerSizing(
        rowHeightDp = rowHeight,
        artworkSizeDp = artwork,
        controlHitDp = controlHit,
    )
}

@Composable
fun CarMiniPlayer(
    metadata: MediaMetadata?,
    isPlaying: Boolean,
    viewport: CarViewportMetrics,
    onOpen: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    val density = LocalDensity.current.density
    val sizing = carMiniPlayerSizing(viewport, density)
    val rowHeight = sizing.rowHeightDp.dp
    val artworkSize = sizing.artworkSizeDp.dp
    val controlSize = sizing.controlHitDp.dp
    val visualIconSize =
        maxOf(
            viewport.pxToDp(34f, density).value,
            28f / density.coerceAtLeast(1f),
        ).dp
    val horizontalGap = viewport.pxToDp(10f, density)
    val edgePadding = viewport.pxToDp(10f, density)

    Surface(
        tonalElevation = viewport.pxToDp(3f, density),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .padding(horizontal = edgePadding),
        ) {
            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(onClick = onOpen)
                        .padding(end = horizontalGap),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(horizontalGap),
            ) {
                if (metadata?.thumbnailUrl != null) {
                    AsyncImage(
                        model = metadata.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .size(artworkSize)
                                .clip(MaterialTheme.shapes.medium),
                    )
                } else {
                    Surface(
                        modifier =
                            Modifier
                                .size(artworkSize)
                                .clip(MaterialTheme.shapes.medium),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        androidx.compose.foundation.layout.Box(
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.music_note),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(visualIconSize),
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = metadata?.title ?: stringResource(R.string.car_no_music),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val artist = metadata?.artists?.joinToString(", ") { it.name }.orEmpty()
                    if (artist.isNotBlank()) {
                        Text(
                            text = artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            IconButton(
                onClick = onPrevious,
                modifier = Modifier.size(controlSize),
            ) {
                Icon(
                    painter = painterResource(R.drawable.skip_previous),
                    contentDescription = stringResource(R.string.car_previous),
                    modifier = Modifier.size(visualIconSize),
                )
            }
            FilledTonalIconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(controlSize),
            ) {
                Icon(
                    painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                    contentDescription = stringResource(R.string.car_play_pause),
                    modifier = Modifier.size(visualIconSize),
                )
            }
            IconButton(
                onClick = onNext,
                modifier = Modifier.size(controlSize),
            ) {
                Icon(
                    painter = painterResource(R.drawable.skip_next),
                    contentDescription = stringResource(R.string.car_next),
                    modifier = Modifier.size(visualIconSize),
                )
            }
        }
    }
}
