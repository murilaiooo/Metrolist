package com.metrolist.music.car.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.metrolist.music.R
import com.metrolist.music.car.library.CarArtworkResolver
import com.metrolist.music.car.library.CarLibrarySong
import com.metrolist.music.car.library.CarUnifiedSong
import com.metrolist.music.car.ui.CarViewportMetrics
import com.metrolist.music.car.ui.theme.CarDesignTokens

data class CarTrackRowModel(
    val title: String,
    val artist: String,
    val artworkUri: String?,
    val explicit: Boolean,
    val isCurrent: Boolean,
    val isBusy: Boolean,
    val provenanceLabel: String? = null,
)

fun carTrackRowModel(
    song: CarUnifiedSong,
    carLibraryArtwork: String? = null,
    legacyArtwork: String? = null,
    remoteArtwork: String? = song.downloadedSong?.thumbnailUrl,
    isCurrent: Boolean = false,
    isBusy: Boolean = false,
    artworkResolver: CarArtworkResolver = CarArtworkResolver(),
): CarTrackRowModel =
    CarTrackRowModel(
        title = song.title,
        artist = artistLabel(song.artists, song.album),
        artworkUri =
            artworkResolver.resolve(
                carLibraryArtwork = carLibraryArtwork,
                legacyArtwork = legacyArtwork,
                remoteArtwork = remoteArtwork,
            ),
        explicit = song.explicit,
        isCurrent = isCurrent,
        isBusy = isBusy,
    )

fun carTrackRowModel(
    song: CarLibrarySong,
    artworkUri: String? = null,
    isCurrent: Boolean = false,
    isBusy: Boolean = false,
): CarTrackRowModel =
    CarTrackRowModel(
        title = song.title,
        artist = artistLabel(song.artists.map { it.name }, song.album?.name),
        artworkUri = artworkUri,
        explicit = song.explicit,
        isCurrent = isCurrent,
        isBusy = isBusy,
    )

private fun artistLabel(
    artists: List<String>,
    album: String?,
): String =
    artists.joinToString(", ")
        .ifBlank { album.orEmpty() }
        .ifBlank { "Unknown artist" }

internal fun carTrackRowMinHeightDp(
    viewport: CarViewportMetrics,
    density: Float,
): Float {
    val safeDensity = density.takeIf { it.isFinite() && it > 0f } ?: 1f
    val scaled = viewport.pxToDp(CarDesignTokens.StandardTrackRowHeightPx, safeDensity).value
    val physicalMinimum = 88f / safeDensity
    return maxOf(scaled, physicalMinimum)
}

@Composable
fun CarTrackRow(
    model: CarTrackRowModel,
    viewport: CarViewportMetrics,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current.density
    val minHeight = carTrackRowMinHeightDp(viewport, density).dp
    val artworkSize = viewport.pxToDp(82f, density)
    val horizontalPadding = viewport.pxToDp(14f, density)
    val gap = viewport.pxToDp(14f, density)
    val busySize = viewport.pxToDp(28f, density)

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .clickable(enabled = enabled, onClick = onClick),
        shape = MaterialTheme.shapes.large,
        tonalElevation = viewport.pxToDp(if (model.isCurrent) 5f else 1f, density),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding, vertical = viewport.pxToDp(10f, density)),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(artworkSize)
                        .clip(MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center,
            ) {
                if (!model.artworkUri.isNullOrBlank()) {
                    AsyncImage(
                        model = model.artworkUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                } else {
                    Surface(
                        modifier = Modifier.matchParentSize(),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.music_note),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(viewport.pxToDp(34f, density)),
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(viewport.pxToDp(4f, density)),
            ) {
                Text(
                    text = model.title,
                    style = MaterialTheme.typography.titleMedium,
                    color =
                        if (model.isCurrent) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = model.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (model.explicit) {
                Text(
                    text = "E",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (model.isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(busySize),
                    strokeWidth = viewport.pxToDp(3f, density),
                )
            }
        }
    }
}
