package com.metrolist.music.car.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.R
import com.metrolist.music.car.importer.CarImportRepository
import com.metrolist.music.car.importer.CarLegacyImportProvenanceStore
import com.metrolist.music.car.input.CarInputDiagnostics
import com.metrolist.music.car.library.CarLibrarySession
import com.metrolist.music.car.library.CarUnifiedCatalog
import com.metrolist.music.car.library.CarUnifiedSong
import com.metrolist.music.car.ui.player.CarMiniPlayer
import com.metrolist.music.car.ui.player.CarNowPlayingScreen
import com.metrolist.music.constants.SongSortType
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.playback.DownloadUtil
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.ui.theme.MetrolistTheme
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

@Composable
fun CarApp(
    connectionFlow: StateFlow<PlayerConnection?>,
    database: MusicDatabase,
    downloadUtil: DownloadUtil,
    importRepository: CarImportRepository,
    inputDiagnostics: CarInputDiagnostics,
) {
    val context = LocalContext.current.applicationContext
    val connection by connectionFlow.collectAsStateWithLifecycle()
    val localDensity = LocalDensity.current
    var viewport by remember { mutableStateOf(CarViewportMetrics.from(1280, 720)) }

    val carLibrarySession =
        remember(context) {
            CarLibrarySession(context)
        }
    val provenanceStore =
        remember(context) {
            CarLegacyImportProvenanceStore(context)
        }
    val downloadedSongs by
        database
            .downloadedSongs(
                sortType = SongSortType.CREATE_DATE,
                descending = true,
            ).collectAsStateWithLifecycle(initialValue = emptyList())
    val carLibraryIndex by carLibrarySession.index.collectAsStateWithLifecycle()
    val importStatus by importRepository.status.collectAsStateWithLifecycle()
    var legacySongIds by remember { mutableStateOf(provenanceStore.importedSongIds()) }

    LaunchedEffect(carLibrarySession) {
        carLibrarySession.reopen()
    }
    LaunchedEffect(importStatus) {
        provenanceStore.markImported(importRepository.completedSongIds())
        legacySongIds = provenanceStore.importedSongIds()
    }

    val catalog =
        remember(carLibraryIndex, downloadedSongs, legacySongIds) {
            CarUnifiedCatalog.merge(
                index = carLibraryIndex,
                downloadedSongs = downloadedSongs,
                legacySongIds = legacySongIds,
            )
        }

    MetrolistTheme {
        Surface(
            modifier =
                Modifier
                    .fillMaxSize()
                    .onSizeChanged {
                        viewport = CarViewportMetrics.from(it.width, it.height)
                    },
            color = MaterialTheme.colorScheme.surface,
        ) {
            CarShell(
                connection = connection,
                viewport = viewport,
                database = database,
                downloadUtil = downloadUtil,
                importRepository = importRepository,
                inputDiagnostics = inputDiagnostics,
                carLibrarySession = carLibrarySession,
                catalog = catalog,
            )
        }
    }
}

@Composable
private fun CarShell(
    connection: PlayerConnection?,
    viewport: CarViewportMetrics,
    database: MusicDatabase,
    downloadUtil: DownloadUtil,
    importRepository: CarImportRepository,
    inputDiagnostics: CarInputDiagnostics,
    carLibrarySession: CarLibrarySession,
    catalog: List<CarUnifiedSong>,
) {
    val navigation = remember { CarNavigationState(CarDestination.DOWNLOADS) }
    val stack by navigation.stack.collectAsStateWithLifecycle()
    val destination = stack.last()

    val metadata by
        connection?.mediaMetadata?.collectAsStateWithLifecycle()
            ?: remember { mutableStateOf(null) }
    val isPlaying by
        connection?.isEffectivelyPlaying?.collectAsStateWithLifecycle()
            ?: remember { mutableStateOf(false) }

    LaunchedEffect(connection) {
        val restored = connection?.mediaMetadata?.filterNotNull()?.first()
        if (restored != null && navigation.current == CarDestination.DOWNLOADS) {
            navigation.navigate(CarDestination.NOW_PLAYING)
        }
    }

    androidx.activity.compose.BackHandler(enabled = stack.size > 1) {
        navigation.back()
    }

    Row(Modifier.fillMaxSize()) {
        CarNavigationRail(
            current = destination,
            viewport = viewport,
            onNavigate = navigation::navigate,
        )

        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                when (destination) {
                    CarDestination.NOW_PLAYING ->
                        CarNowPlayingScreen(
                            connection = connection,
                            viewport = viewport,
                        )

                    CarDestination.DOWNLOADS ->
                        CarDownloadsScreen(
                            catalog = catalog,
                            carLibrarySession = carLibrarySession,
                            connection = connection,
                            viewport = viewport,
                            onOpenNowPlaying = {
                                navigation.navigate(CarDestination.NOW_PLAYING)
                            },
                        )

                    CarDestination.LIBRARY ->
                        CarLibraryScreen(
                            carLibrarySession = carLibrarySession,
                            viewport = viewport,
                            connection = connection,
                            onOpenNowPlaying = {
                                navigation.navigate(CarDestination.NOW_PLAYING)
                            },
                        )

                    CarDestination.SEARCH ->
                        CarSearchScreen(
                            catalog = catalog,
                            carLibrarySession = carLibrarySession,
                            connection = connection,
                            viewport = viewport,
                            onOpenNowPlaying = {
                                navigation.navigate(CarDestination.NOW_PLAYING)
                            },
                        )

                    CarDestination.MORE ->
                        CarMoreScreen(
                            database = database,
                            downloadUtil = downloadUtil,
                            importRepository = importRepository,
                            inputDiagnostics = inputDiagnostics,
                            carLibrarySession = carLibrarySession,
                            catalog = catalog,
                            viewport = viewport,
                        )
                }
            }

            if (destination != CarDestination.NOW_PLAYING && metadata != null) {
                CarMiniPlayer(
                    metadata = metadata,
                    isPlaying = isPlaying,
                    viewport = viewport,
                    onOpen = { navigation.navigate(CarDestination.NOW_PLAYING) },
                    onPrevious = { connection?.seekToPrevious() },
                    onPlayPause = { connection?.togglePlayPause() },
                    onNext = { connection?.seekToNext() },
                )
            }
        }
    }
}

@Composable
private fun CarNavigationRail(
    current: CarDestination,
    viewport: CarViewportMetrics,
    onNavigate: (CarDestination) -> Unit,
) {
    val density = LocalDensity.current.density
    NavigationRail(
        modifier = Modifier.width(viewport.pxToDp(154f, density)),
    ) {
        CAR_NAV_ITEMS.forEach { item ->
            NavigationRailItem(
                selected = current == item.destination,
                onClick = { onNavigate(item.destination) },
                icon = {
                    Icon(
                        painter = painterResource(item.iconRes),
                        contentDescription = stringResource(item.labelRes),
                    )
                },
                label = { Text(stringResource(item.labelRes)) },
                modifier = Modifier.height(viewport.pxToDp(112f, density)),
            )
        }
    }
}

private data class CarNavItem(
    val destination: CarDestination,
    val iconRes: Int,
    val labelRes: Int,
)

private val CAR_NAV_ITEMS =
    listOf(
        CarNavItem(CarDestination.NOW_PLAYING, R.drawable.music_note, R.string.now_playing),
        CarNavItem(CarDestination.DOWNLOADS, R.drawable.download, R.string.car_music),
        CarNavItem(CarDestination.LIBRARY, R.drawable.library_music, R.string.car_library),
        CarNavItem(CarDestination.SEARCH, R.drawable.search, R.string.car_search),
        CarNavItem(CarDestination.MORE, R.drawable.more_horiz, R.string.car_more),
    )