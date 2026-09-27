package com.metrolist.music.car.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import coil3.compose.AsyncImage
import com.metrolist.music.R
import com.metrolist.music.car.ui.CarViewportMetrics
import com.metrolist.music.models.MediaMetadata

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
    val rowHeight = viewport.pxToDp(104f, density)
    val artworkSize = viewport.pxToDp(76f, density)
    val controlSize = viewport.pxToDp(68f, density)

    Surface(
        tonalElevation = viewport.pxToDp(2f, density),
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(viewport.pxToDp(10f, density)),
            modifier = Modifier.fillMaxWidth().height(rowHeight),
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
                Icon(
                    painter = painterResource(R.drawable.music_note),
                    contentDescription = null,
                    modifier = Modifier.size(artworkSize),
                )
            }

            androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
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

            IconButton(
                onClick = onPrevious,
                modifier = Modifier.size(controlSize),
            ) {
                Icon(
                    painter = painterResource(R.drawable.skip_previous),
                    contentDescription = stringResource(R.string.car_previous),
                )
            }
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(controlSize),
            ) {
                Icon(
                    painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                    contentDescription = stringResource(R.string.car_play_pause),
                )
            }
            IconButton(
                onClick = onNext,
                modifier = Modifier.size(controlSize),
            ) {
                Icon(
                    painter = painterResource(R.drawable.skip_next),
                    contentDescription = stringResource(R.string.car_next),
                )
            }
            Spacer(Modifier.width(viewport.pxToDp(8f, density)))
        }
    }
}
