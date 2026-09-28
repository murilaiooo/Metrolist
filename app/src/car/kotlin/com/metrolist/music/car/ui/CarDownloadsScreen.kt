package com.metrolist.music.car.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.R
import com.metrolist.music.constants.SongSortType
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.playback.queues.ListQueue

@Composable
fun CarDownloadsScreen(
    database: MusicDatabase,
    connection: PlayerConnection?,
    viewport: CarViewportMetrics,
    onOpenNowPlaying: () -> Unit,
) {
    val density = LocalDensity.current.density
    val songs by
        database
            .downloadedSongs(
                sortType = SongSortType.CREATE_DATE,
                descending = true,
            ).collectAsStateWithLifecycle(initialValue = emptyList())

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(viewport.pxToDp(32f, density)),
        verticalArrangement = Arrangement.spacedBy(viewport.pxToDp(14f, density)),
    ) {
        Text(
            text = stringResource(R.string.car_downloads),
            style = MaterialTheme.typography.headlineLarge,
        )
        Text(
            text =
                if (songs.isEmpty()) {
                    stringResource(R.string.car_downloads_empty)
                } else {
                    stringResource(R.string.car_downloads_count, songs.size)
                },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(viewport.pxToDp(8f, density)),
        ) {
            itemsIndexed(
                items = songs,
                key = { _, song -> song.id },
            ) { index, song ->
                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = connection != null) {
                                connection?.playQueue(
                                    ListQueue(
                                        title = "Downloads",
                                        items = songs.map { it.toMediaItem() },
                                        startIndex = index,
                                    ),
                                )
                                onOpenNowPlaying()
                            },
                    shape = MaterialTheme.shapes.large,
                    tonalElevation = viewport.pxToDp(1f, density),
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = viewport.pxToDp(16f, density),
                                    vertical = viewport.pxToDp(14f, density),
                                ),
                    ) {
                        Column {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                            )
                            val artist =
                                song.orderedArtists
                                    .joinToString(", ") { it.name }
                                    .ifBlank { song.album?.title.orEmpty() }
                            if (artist.isNotBlank()) {
                                Text(
                                    text = artist,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}