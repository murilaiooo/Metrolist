package com.metrolist.music.car.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.metrolist.music.car.library.CarLibraryIndex
import com.metrolist.music.car.library.CarLibraryMediaResolver
import com.metrolist.music.car.library.CarLibraryMediaResolution
import com.metrolist.music.car.library.CarLibraryOpenResult
import com.metrolist.music.car.library.CarLibraryQueueResolution
import com.metrolist.music.car.library.CarLibraryRefreshResult
import com.metrolist.music.car.library.CarLibrarySession
import com.metrolist.music.car.library.CarQueueOperation
import com.metrolist.music.car.library.carQueuePreparingOperation
import com.metrolist.music.car.library.carQueueUnavailableOperation
import com.metrolist.music.car.ui.component.CarOperationPanel
import com.metrolist.music.car.ui.component.CarTrackRow
import com.metrolist.music.car.ui.component.carTrackRowModel
import com.metrolist.music.playback.PlayerConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface CarLibraryManagementState {
    data object Loading : CarLibraryManagementState
    data object Empty : CarLibraryManagementState

    data class Active(
        val index: CarLibraryIndex,
        val message: String? = null,
    ) : CarLibraryManagementState

    data class Unavailable(
        val message: String,
    ) : CarLibraryManagementState

    data class Invalid(
        val errors: List<String>,
    ) : CarLibraryManagementState
}

@Composable
fun CarLibraryScreen(
    carLibrarySession: CarLibrarySession,
    viewport: CarViewportMetrics,
    connection: PlayerConnection?,
    onOpenNowPlaying: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val mediaResolver = carLibrarySession.mediaResolver
    val currentMetadata by
        connection?.mediaMetadata?.collectAsStateWithLifecycle()
            ?: remember { mutableStateOf(null) }

    val libraryIndex by carLibrarySession.index.collectAsStateWithLifecycle()
    val libraryOperation by carLibrarySession.operation.collectAsStateWithLifecycle()
    var state by remember {
        mutableStateOf<CarLibraryManagementState>(
            carLibrarySession.index.value
                ?.let(CarLibraryManagementState::Active)
                ?: CarLibraryManagementState.Empty,
        )
    }
    var queueOperation by remember { mutableStateOf<CarQueueOperation?>(null) }

    fun takeReadPermission(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    fun openTree(uri: Uri) {
        takeReadPermission(uri)
        scope.launch {
            state =
                withContext(Dispatchers.IO) {
                    carLibrarySession.open(uri).toManagementState()
                }
        }
    }

    fun refreshLibrary() {
        scope.launch {
            val result =
                withContext(Dispatchers.IO) {
                    carLibrarySession.refresh()
                }
            val preservedIndex = carLibrarySession.index.value
            state =
                when {
                    result is CarLibraryRefreshResult.Unavailable && preservedIndex != null ->
                        CarLibraryManagementState.Active(preservedIndex)
                    result is CarLibraryRefreshResult.Invalid && preservedIndex != null ->
                        CarLibraryManagementState.Active(preservedIndex)
                    else -> result.toManagementState()
                }
        }
    }

    fun playLocalQueue(
        songIds: List<String>,
        startIndex: Int,
        title: String?,
    ) {
        val selectedSongId = songIds.getOrNull(startIndex) ?: return
        val selectedTitle =
            carLibrarySession.index.value
                ?.song(selectedSongId)
                ?.title
                ?: title
                ?: "track"
        val playerConnection = connection
        if (playerConnection == null) {
            queueOperation =
                carQueueUnavailableOperation(
                    songId = selectedSongId,
                    message = context.getString(R.string.car_player_starting),
                )
            return
        }

        queueOperation = carQueuePreparingOperation(selectedSongId, selectedTitle)
        scope.launch {
            val resolution =
                withContext(Dispatchers.IO) {
                    mediaResolver.resolveQueue(
                        songIds = songIds,
                        startIndex = startIndex,
                        title = title,
                    )
                }

            when (resolution) {
                is CarLibraryQueueResolution.Ready -> {
                    playerConnection.playQueue(resolution.queue)
                    queueOperation =
                        if (resolution.skippedSongIds.isEmpty()) {
                            null
                        } else {
                            CarQueueOperation(
                                songId = null,
                                operation =
                                    CarOperationState.Success(
                                        context.getString(
                                            R.string.car_library_skipped_tracks,
                                            resolution.skippedSongIds.size,
                                        ),
                                    ),
                            )
                        }
                    onOpenNowPlaying()
                }

                is CarLibraryQueueResolution.Unavailable -> {
                    queueOperation =
                        carQueueUnavailableOperation(
                            songId = resolution.songId,
                            message = context.getString(R.string.car_library_track_unavailable),
                        )
                }
            }
        }
    }

    val openFolder =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let(::openTree)
        }

    LaunchedEffect(libraryIndex) {
        libraryIndex?.let { index ->
            state = CarLibraryManagementState.Active(index)
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
            text = stringResource(R.string.car_library),
            style = MaterialTheme.typography.headlineLarge,
        )
        Text(
            text = stringResource(R.string.car_library_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = viewport.pxToDp(2f, density),
        ) {
            Column(
                modifier = Modifier.padding(viewport.pxToDp(20f, density)),
                verticalArrangement =
                    Arrangement.spacedBy(viewport.pxToDp(12f, density)),
            ) {
                when (val current = state) {
                    CarLibraryManagementState.Loading -> {
                        CarOperationPanel(libraryOperation)
                    }

                    CarLibraryManagementState.Empty -> {
                        Text(
                            text = stringResource(R.string.car_library_no_folder),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = stringResource(R.string.car_library_no_folder_desc),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = { openFolder.launch(null) },
                            modifier =
                                Modifier.sizeIn(
                                    minHeight = viewport.pxToDp(64f, density),
                                ),
                        ) {
                            Text(stringResource(R.string.car_library_open_folder))
                        }
                    }

                    is CarLibraryManagementState.Active -> {
                        Text(
                            text = stringResource(R.string.car_library_active),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text =
                                stringResource(
                                    R.string.car_library_summary,
                                    current.index.songs.size,
                                    current.index.playlists.size,
                                ),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text =
                                stringResource(
                                    R.string.car_library_revision,
                                    current.index.revision.take(16),
                                ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        current.message?.let {
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(
                            horizontalArrangement =
                                Arrangement.spacedBy(viewport.pxToDp(12f, density)),
                        ) {
                            Button(
                                onClick = ::refreshLibrary,
                                modifier =
                                    Modifier.sizeIn(
                                        minHeight = viewport.pxToDp(60f, density),
                                    ),
                            ) {
                                Text(stringResource(R.string.car_library_refresh))
                            }
                            OutlinedButton(
                                onClick = { openFolder.launch(null) },
                                modifier =
                                    Modifier.sizeIn(
                                        minHeight = viewport.pxToDp(60f, density),
                                    ),
                            ) {
                                Text(
                                    stringResource(
                                        R.string.car_library_choose_another,
                                    ),
                                )
                            }
                        }
                    }

                    is CarLibraryManagementState.Unavailable -> {
                        Text(
                            text = stringResource(R.string.car_library_unavailable),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = current.message,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = { openFolder.launch(null) },
                            modifier =
                                Modifier.sizeIn(
                                    minHeight = viewport.pxToDp(64f, density),
                                ),
                        ) {
                            Text(stringResource(R.string.car_library_reselect))
                        }
                    }

                    is CarLibraryManagementState.Invalid -> {
                        Text(
                            text = stringResource(R.string.car_library_invalid),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = current.errors.take(3).joinToString(" · "),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = { openFolder.launch(null) },
                            modifier =
                                Modifier.sizeIn(
                                    minHeight = viewport.pxToDp(64f, density),
                                ),
                        ) {
                            Text(stringResource(R.string.car_library_reselect))
                        }
                    }
                }
            }
        }

        val showLibraryOperation =
            when (libraryOperation) {
                is CarOperationState.Running -> true
                is CarOperationState.Success,
                is CarOperationState.Error,
                -> state is CarLibraryManagementState.Active
                CarOperationState.Idle -> false
            }
        if (showLibraryOperation) {
            CarOperationPanel(libraryOperation)
        }

        (state as? CarLibraryManagementState.Active)?.let { active ->
            queueOperation?.let { current ->
                CarOperationPanel(current.operation)
            }

            val allSongIds = active.index.songs.map { it.id }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement =
                    Arrangement.spacedBy(viewport.pxToDp(8f, density)),
            ) {
                if (active.index.playlists.isNotEmpty()) {
                    item(key = "playlist-heading") {
                        Text(
                            text = stringResource(R.string.car_library_playlists),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                    items(
                        items = active.index.playlists,
                        key = { playlist -> "playlist:" + playlist.id },
                    ) { playlist ->
                        Surface(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = connection != null) {
                                        playLocalQueue(
                                            songIds = playlist.songIds,
                                            startIndex = 0,
                                            title = playlist.name,
                                        )
                                    },
                            shape = MaterialTheme.shapes.large,
                            tonalElevation = viewport.pxToDp(1f, density),
                        ) {
                            Column(
                                modifier =
                                    Modifier.padding(
                                        viewport.pxToDp(16f, density),
                                    ),
                            ) {
                                Text(
                                    text = playlist.name,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text =
                                        stringResource(
                                            R.string.car_library_track_count,
                                            playlist.songIds.size,
                                        ),
                                    color =
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                item(key = "track-heading") {
                    Text(
                        text = stringResource(R.string.car_library_tracks),
                        style = MaterialTheme.typography.titleLarge,
                        modifier =
                            Modifier.padding(
                                top = viewport.pxToDp(6f, density),
                            ),
                    )
                }

                itemsIndexed(
                    items = active.index.songs,
                    key = { _, song -> "song:" + song.id },
                ) { index, song ->
                    val isCurrent = currentMetadata?.id == song.id
                    val isBusy =
                        queueOperation?.songId == song.id &&
                            queueOperation?.operation is CarOperationState.Running
                    val artworkUri =
                        when (val resolution = mediaResolver.resolve(song.id)) {
                            is CarLibraryMediaResolution.Ready ->
                                resolution.mediaItem.mediaMetadata.artworkUri?.toString()
                            is CarLibraryMediaResolution.Unavailable -> null
                        }

                    CarTrackRow(
                        model =
                            carTrackRowModel(
                                song = song,
                                artworkUri = artworkUri,
                                isCurrent = isCurrent,
                                isBusy = isBusy,
                            ),
                        viewport = viewport,
                        enabled = connection != null,
                        onClick = {
                            playLocalQueue(
                                songIds = allSongIds,
                                startIndex = index,
                                title = context.getString(R.string.car_library),
                            )
                        },
                    )
                }
            }
        }
    }
}

private fun CarLibraryOpenResult.toManagementState(): CarLibraryManagementState =
    when (this) {
        is CarLibraryOpenResult.Opened ->
            CarLibraryManagementState.Active(index)
        is CarLibraryOpenResult.Unavailable ->
            if (location == null) {
                CarLibraryManagementState.Empty
            } else {
                CarLibraryManagementState.Unavailable(reason)
            }
        is CarLibraryOpenResult.Invalid ->
            CarLibraryManagementState.Invalid(errors)
    }

private fun CarLibraryRefreshResult.toManagementState(): CarLibraryManagementState =
    when (this) {
        is CarLibraryRefreshResult.Unchanged ->
            CarLibraryManagementState.Active(
                index = index,
                message = "Library is already up to date.",
            )
        is CarLibraryRefreshResult.Updated ->
            CarLibraryManagementState.Active(
                index = index,
                message = "Library refreshed.",
            )
        is CarLibraryRefreshResult.Unavailable ->
            CarLibraryManagementState.Unavailable(reason)
        is CarLibraryRefreshResult.Invalid ->
            CarLibraryManagementState.Invalid(errors)
    }