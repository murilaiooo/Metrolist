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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.core.content.ContextCompat
import com.metrolist.music.R
import com.metrolist.music.car.library.AndroidCarLibraryTreeAccessFactory
import com.metrolist.music.car.library.CarLibraryIndex
import com.metrolist.music.car.library.CarLibraryMediaResolver
import com.metrolist.music.car.library.CarLibraryOpenResult
import com.metrolist.music.car.library.CarLibraryQueueResolution
import com.metrolist.music.car.library.CarLibraryRefreshResult
import com.metrolist.music.car.library.CarLibraryRepository
import com.metrolist.music.car.library.ContentTreeCarLibraryTreeAccess
import com.metrolist.music.car.library.SharedPreferencesCarLibraryLocationStore
import com.metrolist.music.car.service.CarImportService
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
    viewport: CarViewportMetrics,
    connection: PlayerConnection?,
    onOpenNowPlaying: () -> Unit,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val repository =
        remember(appContext) {
            CarLibraryRepository(
                locationStore = SharedPreferencesCarLibraryLocationStore(appContext),
                accessFactory = AndroidCarLibraryTreeAccessFactory(appContext),
            )
        }
    val mediaResolver = remember(repository) { CarLibraryMediaResolver(repository) }

    var state by remember {
        mutableStateOf<CarLibraryManagementState>(CarLibraryManagementState.Loading)
    }
    var legacyImportMessage by remember { mutableStateOf<String?>(null) }
    var playbackMessage by remember { mutableStateOf<String?>(null) }

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
        state = CarLibraryManagementState.Loading
        scope.launch {
            state =
                withContext(Dispatchers.IO) {
                    repository.open(
                        ContentTreeCarLibraryTreeAccess(
                            resolver = appContext.contentResolver,
                            treeUri = uri,
                        ),
                    ).toManagementState()
                }
        }
    }

    fun refreshLibrary() {
        state = CarLibraryManagementState.Loading
        scope.launch {
            state =
                withContext(Dispatchers.IO) {
                    repository.refresh().toManagementState()
                }
        }
    }

    fun playLocalQueue(
        songIds: List<String>,
        startIndex: Int,
        title: String?,
    ) {
        val playerConnection = connection
        if (playerConnection == null) {
            playbackMessage = context.getString(R.string.car_player_starting)
            return
        }

        when (
            val resolution =
                mediaResolver.resolveQueue(
                    songIds = songIds,
                    startIndex = startIndex,
                    title = title,
                )
        ) {
            is CarLibraryQueueResolution.Ready -> {
                playerConnection.playQueue(resolution.queue)
                playbackMessage =
                    if (resolution.skippedSongIds.isEmpty()) {
                        null
                    } else {
                        context.getString(
                            R.string.car_library_skipped_tracks,
                            resolution.skippedSongIds.size,
                        )
                    }
                onOpenNowPlaying()
            }

            is CarLibraryQueueResolution.Unavailable -> {
                playbackMessage =
                    context.getString(R.string.car_library_track_unavailable)
            }
        }
    }

    val openFolder =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let(::openTree)
        }

    val importBackup =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                takeReadPermission(uri)
                val intent =
                    Intent(appContext, CarImportService::class.java).apply {
                        action = CarImportService.ACTION_START
                        data = uri
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                ContextCompat.startForegroundService(appContext, intent)
                legacyImportMessage =
                    context.getString(R.string.car_legacy_import_started)
            }
        }

    LaunchedEffect(repository) {
        state =
            withContext(Dispatchers.IO) {
                repository.reopen().toManagementState()
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
                        Text(
                            text = stringResource(R.string.car_library_loading),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
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

        (state as? CarLibraryManagementState.Active)?.let { active ->
            playbackMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                    Surface(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = connection != null) {
                                    playLocalQueue(
                                        songIds = allSongIds,
                                        startIndex = index,
                                        title =
                                            context.getString(
                                                R.string.car_library,
                                            ),
                                    )
                                },
                        shape = MaterialTheme.shapes.large,
                        tonalElevation = viewport.pxToDp(1f, density),
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal =
                                            viewport.pxToDp(16f, density),
                                        vertical =
                                            viewport.pxToDp(14f, density),
                                    ),
                            horizontalArrangement =
                                Arrangement.spacedBy(
                                    viewport.pxToDp(12f, density),
                                ),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                )
                                val artist =
                                    song.artists
                                        .joinToString(", ") { artist ->
                                            artist.name
                                        }
                                        .ifBlank {
                                            song.album?.name.orEmpty()
                                        }
                                if (artist.isNotBlank()) {
                                    Text(
                                        text = artist,
                                        color =
                                            MaterialTheme.colorScheme
                                                .onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                            }
                            if (song.explicit) {
                                Text(
                                    text = "E",
                                    style = MaterialTheme.typography.labelLarge,
                                    color =
                                        MaterialTheme.colorScheme
                                            .onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider()

        Text(
            text = stringResource(R.string.car_legacy_backup),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.car_legacy_backup_desc),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = { importBackup.launch(arrayOf("*/*")) },
            modifier =
                Modifier.sizeIn(
                    minHeight = viewport.pxToDp(60f, density),
                ),
        ) {
            Text(stringResource(R.string.car_import_legacy_backup))
        }
        legacyImportMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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