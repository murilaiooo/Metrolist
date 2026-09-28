package com.metrolist.music.car.ui

import android.content.Intent
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.R
import com.metrolist.music.car.importer.CarArtworkStore
import com.metrolist.music.car.library.CarLibrarySession
import com.metrolist.music.car.library.CarUnifiedPlaybackResolver
import com.metrolist.music.car.library.CarUnifiedQueueResolution
import com.metrolist.music.car.library.CarUnifiedSong
import com.metrolist.music.car.preferences.CarDislikeDeletionCoordinator
import com.metrolist.music.car.preferences.CarDislikeExporter
import com.metrolist.music.car.preferences.CarDislikeMediaDeletion
import com.metrolist.music.car.preferences.CarDislikeRepository
import com.metrolist.music.playback.DownloadUtil
import com.metrolist.music.playback.PlayerConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CarDislikedScreen(
    repository: CarDislikeRepository,
    rawCatalog: List<CarUnifiedSong>,
    carLibrarySession: CarLibrarySession,
    downloadUtil: DownloadUtil,
    connection: PlayerConnection?,
    viewport: CarViewportMetrics,
    onOpenNowPlaying: () -> Unit,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val records = repository.records.collectAsStateWithLifecycle().value
    val artworkStore = remember(appContext) { CarArtworkStore(appContext.filesDir) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var deleteSummary by remember { mutableStateOf<String?>(null) }
    var isDeleting by remember { mutableStateOf(false) }
    val resolver =
        remember(carLibrarySession, artworkStore, repository) {
            CarUnifiedPlaybackResolver(
                carLibraryResolver = carLibrarySession.mediaResolver,
                legacyArtworkStore = artworkStore,
                activeDislikedSongIds = { repository.activeSongIds.value },
            )
        }
    val mediaDeletion =
        remember(appContext, downloadUtil, carLibrarySession, rawCatalog) {
            CarDislikeMediaDeletion(
                context = appContext,
                downloadUtil = downloadUtil,
                carLibrarySession = carLibrarySession,
                catalogProvider = { rawCatalog },
            )
        }
    val deletionCoordinator =
        remember(repository, mediaDeletion) {
            CarDislikeDeletionCoordinator(repository, mediaDeletion::delete)
        }
    val pendingCount = records.count { !it.isDeleted }

    fun playReviewSong(songId: String) {
        val playerConnection = connection ?: return
        val reviewSongs =
            records
                .filterNot { it.isDeleted }
                .mapNotNull { record -> rawCatalog.firstOrNull { it.id == record.songId } }
        val startIndex = reviewSongs.indexOfFirst { it.id == songId }
        if (startIndex < 0) return
        val resolution =
            resolver.resolveQueue(
                songs = reviewSongs,
                startIndex = startIndex,
                title = context.getString(R.string.car_disliked),
                allowDisliked = true,
            )
        if (resolution is CarUnifiedQueueResolution.Ready) {
            playerConnection.playQueue(resolution.queue)
            onOpenNowPlaying()
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(viewport.pxToDp(28f, density)),
        verticalArrangement = Arrangement.spacedBy(viewport.pxToDp(12f, density)),
    ) {
        Text(
            text = stringResource(R.string.car_disliked),
            style = MaterialTheme.typography.headlineLarge,
        )
        Text(
            text = stringResource(R.string.car_disliked_desc),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (records.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(viewport.pxToDp(12f, density)),
            ) {
                Button(
                    onClick = {
                        runCatching {
                            val shareIntent =
                                CarDislikeExporter.createShareIntent(appContext, records)
                            context.startActivity(
                                Intent.createChooser(
                                    shareIntent,
                                    context.getString(R.string.car_disliked_export),
                                ),
                            )
                        }.onFailure { error ->
                            deleteSummary =
                                "Export failed: " +
                                    (error.message ?: error::class.java.simpleName)
                        }
                    },
                    modifier =
                        Modifier.sizeIn(
                            minHeight = viewport.pxToDp(58f, density),
                        ),
                ) {
                    Text(stringResource(R.string.car_disliked_export))
                }

                OutlinedButton(
                    onClick = { showDeleteConfirmation = true },
                    enabled = pendingCount > 0 && !isDeleting,
                    modifier =
                        Modifier.sizeIn(
                            minHeight = viewport.pxToDp(58f, density),
                        ),
                ) {
                    Text(
                        if (isDeleting) {
                            "Deleting…"
                        } else {
                            stringResource(R.string.car_disliked_delete_all)
                        },
                    )
                }
            }
        }

        deleteSummary?.let { summary ->
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (records.isEmpty()) {
            Text(
                text = stringResource(R.string.car_disliked_empty),
                style = MaterialTheme.typography.titleMedium,
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(viewport.pxToDp(8f, density)),
            ) {
                items(
                    items = records,
                    key = { it.songId },
                ) { record ->
                    val localSong =
                        if (record.isDeleted) null else rawCatalog.firstOrNull { it.id == record.songId }
                    Surface(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = localSong != null) {
                                    playReviewSong(record.songId)
                                },
                        shape = MaterialTheme.shapes.large,
                        tonalElevation = viewport.pxToDp(1f, density),
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(viewport.pxToDp(14f, density)),
                            horizontalArrangement =
                                Arrangement.spacedBy(viewport.pxToDp(12f, density)),
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement =
                                    Arrangement.spacedBy(viewport.pxToDp(4f, density)),
                            ) {
                                Text(
                                    text = record.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                )
                                Text(
                                    text = record.artists.joinToString(", ").ifBlank { "Unknown artist" },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                                Text(
                                    text =
                                        if (record.isDeleted) {
                                            stringResource(R.string.car_disliked_deleted)
                                        } else {
                                            stringResource(R.string.car_disliked_pending)
                                        },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                record.lastDeletionError?.let { error ->
                                    Text(
                                        text = error,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                            if (!record.isDeleted) {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                repository.undo(record.songId)
                                            }
                                        }
                                    },
                                    modifier =
                                        Modifier.sizeIn(
                                            minHeight = viewport.pxToDp(54f, density),
                                        ),
                                ) {
                                    Text(stringResource(R.string.car_disliked_undo))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = {
                Text(stringResource(R.string.car_disliked_delete_title))
            },
            text = {
                Text(
                    text =
                        context.getString(
                            R.string.car_disliked_delete_message,
                            pendingCount,
                        ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        isDeleting = true
                        deleteSummary = null
                        scope.launch {
                            val result =
                                withContext(Dispatchers.IO) {
                                    deletionCoordinator.deletePending(confirmed = true)
                                }
                            isDeleting = false
                            deleteSummary =
                                if (result.failedCount == 0) {
                                    "Deleted ${result.deletedCount} file(s). Dislike history was kept."
                                } else {
                                    "Deleted ${result.deletedCount}; ${result.failedCount} failed. Failed items remain pending."
                                }
                        }
                    },
                ) {
                    Text(stringResource(R.string.car_disliked_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(stringResource(R.string.car_disliked_delete_cancel))
                }
            },
        )
    }
}
