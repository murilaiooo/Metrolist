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
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.metrolist.music.car.importer.CarImportRepository
import com.metrolist.music.car.importer.CarImportStatus
import com.metrolist.music.car.input.CarInputDiagnostics
import com.metrolist.music.car.library.CarLibrarySession
import com.metrolist.music.car.library.CarSongSourceKind
import com.metrolist.music.car.library.CarUnifiedSong
import com.metrolist.music.car.service.CarImportService
import com.metrolist.music.car.ui.component.CarOperationPanel
import com.metrolist.music.constants.SongSortType
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.playback.DownloadUtil

@Composable
fun CarMoreScreen(
    database: MusicDatabase,
    downloadUtil: DownloadUtil,
    importRepository: CarImportRepository,
    inputDiagnostics: CarInputDiagnostics,
    carLibrarySession: CarLibrarySession,
    catalog: List<CarUnifiedSong>,
    viewport: CarViewportMetrics,
    dislikedCount: Int,
    onOpenDisliked: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
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