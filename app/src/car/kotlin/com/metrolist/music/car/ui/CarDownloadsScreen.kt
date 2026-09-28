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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.R
import com.metrolist.music.car.feedback.CarOperationState
import com.metrolist.music.car.importer.CarArtworkStore
import com.metrolist.music.car.library.CarLibraryMediaResolution
import com.metrolist.music.car.library.CarLibrarySession
import com.metrolist.music.car.library.CarQueueOperation
import com.metrolist.music.car.library.CarSongSourceKind
import com.metrolist.music.car.library.CarUnifiedPlaybackResolver
import com.metrolist.music.car.library.CarUnifiedQueueResolution
import com.metrolist.music.car.library.CarUnifiedSong
import com.metrolist.music.car.library.carQueuePreparingOperation
import com.metrolist.music.car.library.carQueueUnavailableOperation
import com.metrolist.music.car.ui.component.CarOperationPanel
import com.metrolist.music.car.ui.component.CarTrackRow
import com.metrolist.music.car.ui.component.carTrackRowModel
import com.metrolist.music.playback.PlayerConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CarDownloadsScreen(
    catalog: List<CarUnifiedSong>,
    carLibrarySession: CarLibrarySession,
    connection: PlayerConnection?,
    viewport: CarViewportMetrics,
    onOpenNowPlaying: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val currentMetadata by
        connection?.mediaMetadata?.collectAsStateWithLifecycle()
            ?: remember { mutableStateOf(null) }
    val artworkStore = remember(context) { CarArtworkStore(context.filesDir) }
    val resolver =
        remember(carLibrarySession, artworkStore) {
            CarUnifiedPlaybackResolver(
                carLibraryResolver = carLibrarySession.mediaResolver,
                legacyArtworkStore = artworkStore,
            )
        }
    var queueOperation by remember { mutableStateOf<CarQueueOperation?>(null) }
    var resolutionJob by remember { mutableStateOf<Job?>(null) }

    fun play(index: Int) {
        val song = catalog.getOrNull(index) ?: return
        val playerConnection = connection
        if (playerConnection == null) {
            queueOperation =
                carQueueUnavailableOperation(
                    songId = song.id,
                    message = context.getString(R.string.car_player_starting),
                )
            return
        }

        resolutionJob?.cancel()
        queueOperation = carQueuePreparingOperation(song.id, song.title)
        resolutionJob =
            scope.launch {
                val resolution =
                    withContext(Dispatchers.IO) {
                        resolver.resolveQueue(
                            songs = catalog,
                            startIndex = index,
                            title = "Music",
                        )
                    }
                when (resolution) {
                    is CarUnifiedQueueResolution.Ready -> {
                        queueOperation = null
                        playerConnection.playQueue(resolution.queue)
                        onOpenNowPlaying()
                    }
                    is CarUnifiedQueueResolution.Unavailable -> {
                        queueOperation =
                            carQueueUnavailableOperation(
                                songId = resolution.songId,
                                message = "Track file is unavailable",
                            )
                    }
                }
            }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(viewport.pxToDp(32f, density)),
        verticalArrangement = Arrangement.spacedBy(viewport.pxToDp(14f, density)),
    ) {
        Text(
            text = stringResource(R.string.car_music),
            style = MaterialTheme.typography.headlineLarge,
        )
        Text(
            text =
                if (catalog.isEmpty()) {
                    stringResource(R.string.car_music_empty)
                } else {
                    stringResource(R.string.car_music_count, catalog.size)
                },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        queueOperation?.let { current ->
            CarOperationPanel(current.operation)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(viewport.pxToDp(8f, density)),
        ) {
            itemsIndexed(
                items = catalog,
                key = { _, song -> song.id },
            ) { index, song ->
                val isCurrent = currentMetadata?.id == song.id
                val isBusy =
                    queueOperation?.songId == song.id &&
                        queueOperation?.operation is CarOperationState.Running
                val carLibraryArtwork =
                    if (song.carLibrarySong != null) {
                        when (val resolution = carLibrarySession.mediaResolver.resolve(song.id)) {
                            is CarLibraryMediaResolution.Ready ->
                                resolution.mediaItem.mediaMetadata.artworkUri?.toString()
                            is CarLibraryMediaResolution.Unavailable -> null
                        }
                    } else {
                        null
                    }
                val legacyArtwork =
                    if (CarSongSourceKind.LEGACY_IMPORTED in song.sourceKinds) {
                        runCatching { artworkStore.fileFor(song.id) }
                            .getOrNull()
                            ?.takeIf { it.isFile }
                            ?.toURI()
                            ?.toString()
                    } else {
                        null
                    }

                CarTrackRow(
                    model =
                        carTrackRowModel(
                            song = song,
                            carLibraryArtwork = carLibraryArtwork,
                            legacyArtwork = legacyArtwork,
                            isCurrent = isCurrent,
                            isBusy = isBusy,
                        ),
                    viewport = viewport,
                    enabled = connection != null,
                    onClick = { play(index) },
                )
            }
        }
    }
}