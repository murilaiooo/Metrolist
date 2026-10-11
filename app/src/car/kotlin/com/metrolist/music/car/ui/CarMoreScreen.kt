package com.metrolist.music.car.ui

import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.BuildConfig
import com.metrolist.music.R
import com.metrolist.music.car.diagnostics.CarDiagnosticsSnapshot
import com.metrolist.music.car.feedback.CarOperationState
import com.metrolist.music.car.importer.CarArtworkStore
import com.metrolist.music.car.importer.CarImportRepository
import com.metrolist.music.car.importer.CarImportStatus
import com.metrolist.music.car.importer.CarLegacyImportProvenanceStore
import com.metrolist.music.car.input.CarInputDiagnostics
import com.metrolist.music.car.library.CarLibrarySession
import com.metrolist.music.car.library.CarSongSourceKind
import com.metrolist.music.car.library.CarUnifiedSong
import com.metrolist.music.car.loudness.CarLoudnessProgress
import com.metrolist.music.car.service.CarImportService
import com.metrolist.music.car.ui.component.CarOperationPanel
import com.metrolist.music.constants.CarLoudnessModeKey
import com.metrolist.music.constants.SongSortType
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.playback.loudness.CarLoudnessMode
import com.metrolist.music.playback.DownloadUtil
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.utils.rememberEnumPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CarMoreScreen(
    connection: PlayerConnection?,
    database: MusicDatabase,
    downloadUtil: DownloadUtil,
    importRepository: CarImportRepository,
    inputDiagnostics: CarInputDiagnostics,
    carLibrarySession: CarLibrarySession,
    catalog: List<CarUnifiedSong>,
    viewport: CarViewportMetrics,
    dislikedCount: Int,
    loudnessProgress: CarLoudnessProgress,
    onOpenDisliked: () -> Unit,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val artworkStore = remember(appContext) { CarArtworkStore(appContext.filesDir) }
    val provenanceStore = remember(appContext) { CarLegacyImportProvenanceStore(appContext) }
    var showDeleteAllConfirmation by remember { mutableStateOf(false) }
    val (loudnessMode, onLoudnessModeChange) =
        rememberEnumPreference(
            key = CarLoudnessModeKey,
            defaultValue = CarLoudnessMode.HIGH,
        )
    var isDeletingAll by remember { mutableStateOf(false) }
    var deleteAllSummary by remember { mutableStateOf<String?>(null) }
    val downloaded by
        database
            .downloadedSongs(
                sortType = SongSortType.CREATE_DATE,
                descending = true,
            ).collectAsStateWithLifecycle(initialValue = emptyList())
    val importStatus by importRepository.status.collectAsStateWithLifecycle()
    val carLibraryIndex by carLibrarySession.index.collectAsStateWithLifecycle()
    val lastInput by inputDiagnostics.lastObservation.collectAsStateWithLifecycle()

    fun startImportAction(action: String) {
        ContextCompat.startForegroundService(
            context,
            Intent(context, CarImportService::class.java).apply {
                this.action = action
            },
        )
    }

    val importBackup =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, CarImportService::class.java).apply {
                        action = CarImportService.ACTION_START
                        data = uri
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                )
            }
        }

    val importOperationState =
        when (val state = importStatus) {
            CarImportStatus.Idle -> CarOperationState.Idle
            is CarImportStatus.Interrupted ->
                CarOperationState.Error(
                    message = "Import interrupted during ${state.phase.name.lowercase()}",
                    actionHint = "Resume or discard the interrupted import.",
                )
            is CarImportStatus.Running -> CarOperationState.Running(state.progress)
            is CarImportStatus.Complete ->
                CarOperationState.Success(
                    "Imported ${state.importedSongs}/${state.totalSongs} tracks",
                )
            is CarImportStatus.Failed ->
                CarOperationState.Error(
                    message = "Failed during ${state.phase.name.lowercase()}: ${state.message}",
                    actionHint = "Try again or select a different backup.",
                )
            is CarImportStatus.Discarded ->
                CarOperationState.Success(
                    "Discarded incomplete import · ${state.preservedCompleted} preserved, " +
                        "${state.cleanedIncomplete} cleaned",
                )
        }

    val storage = context.filesDir
    val observationText =
        lastInput?.let { observation ->
            "keyCode=${observation.signature.keyCode}, scanCode=${observation.signature.scanCode}, " +
                "source=${observation.signature.source}, action=${observation.keyAction}, " +
                "mapped=${observation.decision.action ?: "none"}, consume=${observation.decision.consume}"
        }
    val snapshot =
        CarDiagnosticsSnapshot(
            versionName = BuildConfig.VERSION_NAME,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            apiLevel = Build.VERSION.SDK_INT,
            viewportWidthPx = viewport.widthPx,
            viewportHeightPx = viewport.heightPx,
            density = density,
            freeStorageBytes = storage.usableSpace,
            totalStorageBytes = storage.totalSpace,
            roomDownloadedCount = downloaded.size,
            media3CachedKeyCount = downloadUtil.downloadCache.keys.size,
            importState = importStatus.toDisplayText(),
            carLibraryState =
                carLibraryIndex?.let { index ->
                    index.producer.product + " " + index.producer.version +
                        " · " + index.songs.size + " tracks · " +
                        index.playlists.size + " playlists · rev " + index.revision.take(16)
                } ?: "not selected / unavailable",
            logicalCatalogCount = catalog.size,
            carLibraryPrimaryCount =
                catalog.count { it.primarySource == CarSongSourceKind.CAR_LIBRARY_LOCAL },
            carLocalPrimaryCount =
                catalog.count { it.primarySource == CarSongSourceKind.CAR_LOCAL_DOWNLOAD },
            legacyPrimaryCount =
                catalog.count { it.primarySource == CarSongSourceKind.LEGACY_IMPORTED },
            lastInputObservation = observationText,
        )

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(viewport.pxToDp(32f, density)),
        verticalArrangement = Arrangement.spacedBy(viewport.pxToDp(14f, density)),
    ) {
        Text(
            text = stringResource(R.string.car_more),
            style = MaterialTheme.typography.headlineLarge,
        )

        Button(
            onClick = onOpenDisliked,
            modifier =
                Modifier.sizeIn(
                    minHeight = viewport.pxToDp(60f, density),
                ),
        ) {
            Text(
                text =
                    stringResource(R.string.car_disliked_review) +
                        if (dislikedCount > 0) " ($dislikedCount)" else "",
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = viewport.pxToDp(2f, density),
        ) {
            Column(
                modifier = Modifier.padding(viewport.pxToDp(20f, density)),
                verticalArrangement = Arrangement.spacedBy(viewport.pxToDp(10f, density)),
            ) {
                Text(
                    text = stringResource(R.string.car_loudness_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = stringResource(R.string.car_loudness_desc),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(viewport.pxToDp(8f, density)),
                ) {
                    CarLoudnessMode.entries.forEach { mode ->
                        val label =
                            when (mode) {
                                CarLoudnessMode.OFF -> R.string.car_loudness_off
                                CarLoudnessMode.LOW -> R.string.car_loudness_low
                                CarLoudnessMode.MEDIUM -> R.string.car_loudness_medium
                                CarLoudnessMode.HIGH -> R.string.car_loudness_high
                            }
                        if (mode == loudnessMode) {
                            Button(
                                onClick = { onLoudnessModeChange(mode) },
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .sizeIn(minHeight = viewport.pxToDp(52f, density)),
                            ) {
                                Text(stringResource(label))
                            }
                        } else {
                            OutlinedButton(
                                onClick = { onLoudnessModeChange(mode) },
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .sizeIn(minHeight = viewport.pxToDp(52f, density)),
                            ) {
                                Text(stringResource(label))
                            }
                        }
                    }
                }

                when {
                    loudnessProgress.total <= 0 ->
                        Text(
                            text = stringResource(R.string.car_loudness_no_library),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    else -> {
                        Text(
                            text =
                                stringResource(
                                    R.string.car_loudness_progress,
                                    loudnessProgress.analyzed,
                                    loudnessProgress.total,
                                ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text =
                                when {
                                    loudnessProgress.running ->
                                        stringResource(
                                            R.string.car_loudness_running,
                                            loudnessProgress.pending,
                                        )
                                    loudnessProgress.pending == 0 ->
                                        stringResource(R.string.car_loudness_complete)
                                    loudnessProgress.pausedReason != null ->
                                        loudnessProgress.pausedReason
                                    else ->
                                        stringResource(
                                            R.string.car_loudness_pending,
                                            loudnessProgress.pending,
                                        )
                                },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (loudnessProgress.failed > 0) {
                            Text(
                                text =
                                    stringResource(
                                        R.string.car_loudness_failed,
                                        loudnessProgress.failed,
                                    ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = viewport.pxToDp(2f, density),
        ) {
            Column(
                modifier = Modifier.padding(viewport.pxToDp(20f, density)),
                verticalArrangement = Arrangement.spacedBy(viewport.pxToDp(10f, density)),
            ) {
                Text(
                    text = stringResource(R.string.car_import_status),
                    style = MaterialTheme.typography.titleLarge,
                )
                CarOperationPanel(importOperationState)
                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(viewport.pxToDp(12f, density)),
                ) {
                    Button(
                        onClick = { importBackup.launch(arrayOf("*/*")) },
                        enabled = importStatus !is CarImportStatus.Running,
                        modifier =
                            Modifier.sizeIn(
                                minHeight = viewport.pxToDp(60f, density),
                            ),
                    ) {
                        Text(stringResource(R.string.car_import_legacy_backup))
                    }
                    if (importStatus is CarImportStatus.Running) {
                        OutlinedButton(
                            onClick = {
                                startImportAction(CarImportService.ACTION_CANCEL)
                            },
                            modifier =
                                Modifier.sizeIn(
                                    minHeight = viewport.pxToDp(60f, density),
                                ),
                        ) {
                            Text(stringResource(R.string.car_import_cancel))
                        }
                    }
                    if (importStatus is CarImportStatus.Interrupted) {
                        OutlinedButton(
                            onClick = {
                                startImportAction(CarImportService.ACTION_RESUME)
                            },
                            modifier =
                                Modifier.sizeIn(
                                    minHeight = viewport.pxToDp(60f, density),
                                ),
                        ) {
                            Text(stringResource(R.string.car_import_resume))
                        }
                        OutlinedButton(
                            onClick = {
                                startImportAction(CarImportService.ACTION_DISCARD)
                            },
                            modifier =
                                Modifier.sizeIn(
                                    minHeight = viewport.pxToDp(60f, density),
                                ),
                        ) {
                            Text(stringResource(R.string.car_import_discard))
                        }
                    }
                }
            }
        }

        HorizontalDivider()

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = viewport.pxToDp(2f, density),
        ) {
            Column(
                modifier = Modifier.padding(viewport.pxToDp(20f, density)),
                verticalArrangement = Arrangement.spacedBy(viewport.pxToDp(10f, density)),
            ) {
                Text(
                    text = stringResource(R.string.car_storage_reset_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = stringResource(R.string.car_storage_reset_desc),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { showDeleteAllConfirmation = true },
                    enabled = !isDeletingAll && importStatus !is CarImportStatus.Running,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    modifier =
                        Modifier.sizeIn(
                            minHeight = viewport.pxToDp(60f, density),
                        ),
                ) {
                    Text(
                        if (isDeletingAll) {
                            stringResource(R.string.car_delete_all_music_progress)
                        } else {
                            stringResource(R.string.car_delete_all_music)
                        },
                    )
                }
                deleteAllSummary?.let { summary ->
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        HorizontalDivider()

        Text(
            text = stringResource(R.string.car_diagnostics),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(R.string.car_diagnostics_desc),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        snapshot.lines().forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }

    if (showDeleteAllConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirmation = false },
            title = {
                Text(stringResource(R.string.car_delete_all_music_title))
            },
            text = {
                Text(
                    context.getString(
                        R.string.car_delete_all_music_message,
                        downloaded.size,
                        carLibraryIndex?.songs?.size ?: 0,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteAllConfirmation = false
                        isDeletingAll = true
                        deleteAllSummary = null

                        runCatching {
                            connection?.player?.stop()
                            connection?.player?.clearMediaItems()
                        }

                        val managedSongIds = downloaded.map { it.id }.distinct()
                        scope.launch {
                            val result =
                                withContext(Dispatchers.IO) {
                                    runCatching {
                                        managedSongIds.forEach { songId ->
                                            downloadUtil.downloadManager.removeDownload(songId)
                                            downloadUtil.downloadCache.removeResource(songId)
                                            downloadUtil.playerCache.removeResource(songId)
                                            database.updateDownloadedInfo(songId, false, null)
                                            artworkStore.delete(songId)
                                        }
                                        downloadUtil.downloadCache.keys
                                            .toList()
                                            .forEach(downloadUtil.downloadCache::removeResource)
                                        downloadUtil.playerCache.keys
                                            .toList()
                                            .forEach(downloadUtil.playerCache::removeResource)
                                        provenanceStore.clear()
                                        importRepository.reset()
                                        carLibrarySession.disconnect()
                                        managedSongIds.size
                                    }
                                }

                            isDeletingAll = false
                            deleteAllSummary =
                                result.fold(
                                    onSuccess = { removed ->
                                        context.getString(
                                            R.string.car_delete_all_music_done,
                                            removed,
                                        )
                                    },
                                    onFailure = { error ->
                                        context.getString(
                                            R.string.car_delete_all_music_failed,
                                            error.message ?: error::class.java.simpleName,
                                        )
                                    },
                                )
                        }
                    },
                ) {
                    Text(stringResource(R.string.car_delete_all_music_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllConfirmation = false }) {
                    Text(stringResource(R.string.car_delete_all_music_cancel))
                }
            },
        )
    }
}

private fun CarImportStatus.toDisplayText(): String =
    when (this) {
        CarImportStatus.Idle -> "Idle"
        is CarImportStatus.Interrupted -> "Interrupted during ${phase.name.lowercase()}"
        is CarImportStatus.Running -> {
            val items =
                if (progress.completedItems != null && progress.totalItems != null) {
                    " · ${progress.completedItems}/${progress.totalItems} items"
                } else {
                    ""
                }
            progress.stage.name.lowercase().replace('_', ' ') + items
        }
        is CarImportStatus.Complete -> "Complete · $importedSongs/$totalSongs tracks"
        is CarImportStatus.Failed -> "Failed during ${phase.name.lowercase()}: $message"
        is CarImportStatus.Discarded ->
            "Discarded incomplete import · $preservedCompleted preserved, $cleanedIncomplete cleaned"
    }