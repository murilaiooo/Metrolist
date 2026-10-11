package com.metrolist.music.car.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.metrolist.music.car.library.CarLibraryStartupCoordinator
import com.metrolist.music.car.library.CarUnifiedCatalog
import com.metrolist.music.car.library.CarUnifiedSong
import com.metrolist.music.car.loudness.CarLoudnessCoordinator
import com.metrolist.music.car.loudness.CarLoudnessProgress
import com.metrolist.music.car.preferences.CarDislikeLedgerStore
import com.metrolist.music.car.preferences.CarDislikeRepository
import com.metrolist.music.car.startup.CarStartupMilestone
import com.metrolist.music.car.startup.CarStartupMilestoneTracker
import com.metrolist.music.car.ui.player.CarMiniPlayer
import com.metrolist.music.car.ui.player.CarNowPlayingScreen
import com.metrolist.music.constants.SongSortType
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.playback.DownloadUtil
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.car.ui.theme.CarDesignTokens
import com.metrolist.music.car.ui.theme.CarTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@Composable
fun CarApp(
    connectionFlow: StateFlow<PlayerConnection?>,
    database: MusicDatabase,
    downloadUtil: DownloadUtil,
    importRepository: CarImportRepository,
    inputDiagnostics: CarInputDiagnostics,
    startupTracker: CarStartupMilestoneTracker,
) {
    val context = LocalContext.current.applicationContext
    val connection by connectionFlow.collectAsStateWithLifecycle()
    val localDensity = LocalDensity.current
    var viewport by remember { mutableStateOf(CarViewportMetrics.from(1280, 720)) }

    val carLibrarySession =
        remember(context) {
            CarLibrarySession(context)
        }
    val carLibraryStartup =
        remember(carLibrarySession) {
            CarLibraryStartupCoordinator(carLibrarySession::reopen)
        }
    val loudnessCoordinator =
        remember(context, carLibrarySession) {
            CarLoudnessCoordinator(context, carLibrarySession)
        }
    DisposableEffect(loudnessCoordinator) {
        onDispose {
            loudnessCoordinator.close()
        }
    }
    val provenanceStore =
        remember(context) {
            CarLegacyImportProvenanceStore(context)
        }
    val dislikeRepository =
        remember(context) {
            CarDislikeRepository(CarDislikeLedgerStore(context.filesDir))
        }
    val downloadedSongs by
        database
            .downloadedSongs(
                sortType = SongSortType.CREATE_DATE,
                descending = true,
            ).collectAsStateWithLifecycle(initialValue = emptyList())
    val carLibraryIndex by carLibrarySession.index.collectAsStateWithLifecycle()
    val loudnessProgress by loudnessCoordinator.progress.collectAsStateWithLifecycle()
    val importStatus by importRepository.status.collectAsStateWithLifecycle()
    val dislikedSongIds by dislikeRepository.activeSongIds.collectAsStateWithLifecycle()
    var legacySongIds by remember { mutableStateOf(provenanceStore.importedSongIds()) }

    SideEffect {
        startupTracker.mark(CarStartupMilestone.SHELL_COMPOSED)
    }
    LaunchedEffect(connection) {
        if (connection != null) {
            startupTracker.mark(CarStartupMilestone.PLAYER_CONNECTED)
        }
    }
    LaunchedEffect(carLibraryStartup) {
        startupTracker.mark(CarStartupMilestone.LIBRARY_REOPEN_STARTED)
        withContext(Dispatchers.IO) {
            carLibraryStartup.reopenOnce()
        }
        startupTracker.mark(CarStartupMilestone.LIBRARY_READY)
    }
    LaunchedEffect(importStatus) {
        provenanceStore.markImported(importRepository.completedSongIds())
        legacySongIds = provenanceStore.importedSongIds()
    }
    LaunchedEffect(carLibraryIndex) {
        withContext(Dispatchers.IO) {
            loudnessCoordinator.updateLibrary(carLibraryIndex)
        }
    }

    val rawCatalog =
        remember(carLibraryIndex, downloadedSongs, legacySongIds) {
            CarUnifiedCatalog.merge(
                index = carLibraryIndex,
                downloadedSongs = downloadedSongs,
                legacySongIds = legacySongIds,
            )
        }
    val catalog =
        remember(rawCatalog, dislikedSongIds) {
            com.metrolist.music.car.library.filterActiveDislikes(
                songs = rawCatalog,
                dislikedSongIds = dislikedSongIds,
                allowDisliked = false,
            )
        }

    CarTheme {
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
                rawCatalog = rawCatalog,
                dislikedSongIds = dislikedSongIds,
                dislikeRepository = dislikeRepository,
                loudnessCoordinator = loudnessCoordinator,
                loudnessProgress = loudnessProgress,
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
    rawCatalog: List<CarUnifiedSong>,
    dislikedSongIds: Set<String>,
    dislikeRepository: CarDislikeRepository,
    loudnessCoordinator: CarLoudnessCoordinator,
    loudnessProgress: CarLoudnessProgress,
) {
    val navigation = remember { CarNavigationState(CarDestination.MUSIC) }
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
        if (restored != null && navigation.current == CarDestination.MUSIC) {
            navigation.navigate(CarDestination.NOW_PLAYING)
        }
    }
    LaunchedEffect(connection, metadata) {
        val controller = connection?.player
        if (controller == null || controller.mediaItemCount <= 0) {
            loudnessCoordinator.updatePriority(emptyList())
        } else {
            val start = controller.currentMediaItemIndex.coerceAtLeast(0)
            val end = minOf(controller.mediaItemCount, start + 12)
            loudnessCoordinator.updatePriority(
                (start until end).map { index ->
                    controller.getMediaItemAt(index).mediaId
                },
            )
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
                            dislikeRepository = dislikeRepository,
                            rawCatalog = rawCatalog,
                        )

                    CarDestination.MUSIC ->
                        CarDownloadsScreen(
                            catalog = catalog,
                            carLibrarySession = carLibrarySession,
                            connection = connection,
                            viewport = viewport,
                            dislikedSongIds = dislikedSongIds,
                            onOpenNowPlaying = {
                                navigation.navigate(CarDestination.NOW_PLAYING)
                            },
                        )

                    CarDestination.LIBRARY ->
                        CarLibraryScreen(
                            carLibrarySession = carLibrarySession,
                            viewport = viewport,
                            connection = connection,
                            dislikedSongIds = dislikedSongIds,
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
                            dislikedSongIds = dislikedSongIds,
                            onOpenNowPlaying = {
                                navigation.navigate(CarDestination.NOW_PLAYING)
                            },
                        )

                    CarDestination.MORE ->
                        CarMoreScreen(
                            connection = connection,
                            database = database,
                            downloadUtil = downloadUtil,
                            importRepository = importRepository,
                            inputDiagnostics = inputDiagnostics,
                            carLibrarySession = carLibrarySession,
                            catalog = catalog,
                            viewport = viewport,
                            dislikedCount = dislikeRepository.records.collectAsStateWithLifecycle().value.size,
                            loudnessProgress = loudnessProgress,
                            onOpenDisliked = {
                                navigation.navigate(CarDestination.DISLIKED)
                            },
                        )

                    CarDestination.DISLIKED ->
                        CarDislikedScreen(
                            repository = dislikeRepository,
                            rawCatalog = rawCatalog,
                            carLibrarySession = carLibrarySession,
                            downloadUtil = downloadUtil,
                            connection = connection,
                            viewport = viewport,
                            onOpenNowPlaying = {
                                navigation.navigate(CarDestination.NOW_PLAYING)
                            },
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
    val railWidth = viewport.pxToDp(CarDesignTokens.NavigationRailWidthPx, density)
    val itemSize = viewport.pxToDp(CarDesignTokens.NavigationItemSizePx, density)
    val itemGap = viewport.pxToDp(CarDesignTokens.NavigationItemGapPx, density)
    val cornerRadius = viewport.pxToDp(CarDesignTokens.NavigationItemCornerRadiusPx, density)
    val iconSize = viewport.pxToDp(CarDesignTokens.NavigationIconSizePx, density)
    val verticalPadding = viewport.pxToDp(CarDesignTokens.NavigationVerticalPaddingPx, density)
    val borderWidth = viewport.pxToDp(1f, density)
    val activeShadow = viewport.pxToDp(10f, density)
    val inactiveShadow = viewport.pxToDp(4f, density)

    Column(
        modifier =
            Modifier
                .width(railWidth)
                .fillMaxHeight()
                .background(Color(0xFF0B0F13))
                .padding(vertical = verticalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(itemGap),
    ) {
        CAR_NAV_ITEMS.forEach { item ->
            val selected =
                current == item.destination ||
                    (current == CarDestination.DISLIKED && item.destination == CarDestination.MORE)
            val shape = RoundedCornerShape(cornerRadius)
            val backgroundBrush =
                if (selected) {
                    Brush.linearGradient(
                        colors =
                            listOf(
                                Color(0xFFD9B9FF),
                                Color(0xFFA66AF0),
                                Color(0xFF5F1A9B),
                            ),
                    )
                } else {
                    Brush.linearGradient(
                        colors =
                            listOf(
                                Color(0xFF353B43),
                                Color(0xFF20252B),
                                Color(0xFF161A1F),
                            ),
                    )
                }
            val borderColor =
                if (selected) {
                    Color(0xFFE8D4FF).copy(alpha = 0.72f)
                } else {
                    Color(0xFF59616C).copy(alpha = 0.48f)
                }

            Box(
                modifier =
                    Modifier
                        .size(itemSize)
                        .shadow(
                            elevation = if (selected) activeShadow else inactiveShadow,
                            shape = shape,
                            clip = false,
                        )
                        .clip(shape)
                        .background(backgroundBrush)
                        .border(
                            width = borderWidth,
                            color = borderColor,
                            shape = shape,
                        )
                        .clickable { onNavigate(item.destination) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(item.iconRes),
                    contentDescription = stringResource(item.labelRes),
                    modifier = Modifier.size(iconSize),
                    tint = if (selected) Color(0xFFFAF4FF) else Color(0xFFF1F3F6),
                )
            }
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
        CarNavItem(CarDestination.MUSIC, R.drawable.download, R.string.car_music),
        CarNavItem(CarDestination.LIBRARY, R.drawable.library_music, R.string.car_library),
        CarNavItem(CarDestination.SEARCH, R.drawable.search, R.string.car_search),
        CarNavItem(CarDestination.MORE, R.drawable.more_horiz, R.string.car_more),
    )