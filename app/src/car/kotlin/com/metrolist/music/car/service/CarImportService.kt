package com.metrolist.music.car.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media3.database.DatabaseProvider
import com.metrolist.music.R
import com.metrolist.music.car.importer.CarArtworkStore
import com.metrolist.music.car.importer.CarContentUriImportSource
import com.metrolist.music.car.importer.CarImportCoordinator
import com.metrolist.music.car.importer.CarImportInspector
import com.metrolist.music.car.feedback.CarOperationProgress
import com.metrolist.music.car.importer.CarImportPhase
import com.metrolist.music.car.importer.CarImportRepository
import com.metrolist.music.car.importer.CarImportResult
import com.metrolist.music.car.importer.CarLegacyImportProvenanceStore
import com.metrolist.music.car.importer.CarMedia3ImportTarget
import com.metrolist.music.car.importer.CarPortableArchiveValidator
import com.metrolist.music.car.importer.CarRoomImportTarget
import com.metrolist.music.car.preferences.CarDislikeLedgerStore
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.playback.DownloadUtil
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import timber.log.Timber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

sealed interface CarImportServiceCommand {
    data class Start(val uri: Uri) : CarImportServiceCommand
    data object Resume : CarImportServiceCommand
    data object Discard : CarImportServiceCommand

    companion object {
        fun from(intent: Intent): CarImportServiceCommand =
            when (intent.action) {
                CarImportService.ACTION_START ->
                    Start(requireNotNull(intent.data) { "Import source URI is required" })
                CarImportService.ACTION_RESUME -> Resume
                CarImportService.ACTION_DISCARD -> Discard
                else -> throw IllegalArgumentException(
                    "Unknown Car import action: ${intent.action}",
                )
            }
    }
}


internal fun carImportNotificationPercent(progress: CarOperationProgress): Int? =
    progress.fraction
        ?.let { fraction -> (fraction * 100f).toInt().coerceIn(0, 100) }

internal fun carImportProgressText(progress: CarOperationProgress): String =
    buildList {
        add(
            progress.stage.name
                .lowercase()
                .replace('_', ' ')
                .replaceFirstChar(Char::uppercase),
        )
        progress.detail?.takeIf(String::isNotBlank)?.let(::add)
        if (progress.completedItems != null && progress.totalItems != null) {
            add("${progress.completedItems}/${progress.totalItems} items")
        }
        carImportNotificationPercent(progress)?.let { add("$it%") }
    }.joinToString(" · ")

@AndroidEntryPoint
class CarImportService : Service() {
    @Inject lateinit var repository: CarImportRepository
    @Inject lateinit var downloadUtil: DownloadUtil
    @Inject lateinit var database: MusicDatabase
    @Inject lateinit var databaseProvider: DatabaseProvider

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val command =
            runCatching { CarImportServiceCommand.from(requireNotNull(intent)) }
                .getOrElse { error ->
                    repository.applyResult(
                        repository.sourceUri()?.toString(),
                        CarImportResult.Failure(
                            CarImportPhase.VALIDATING,
                            error.message ?: "Invalid import command",
                        ),
                    )
                    stopSelf(startId)
                    return START_NOT_STICKY
                }

        if (activeJob?.isActive == true) return START_NOT_STICKY
        startInForeground()
        activeJob =
            serviceScope.launch {
                try {
                    handle(command)
                } finally {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(startId)
                }
            }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        activeJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun handle(command: CarImportServiceCommand) {
        val coordinator = buildCoordinator()
        when (command) {
            is CarImportServiceCommand.Start -> {
                repository.rememberSource(command.uri)
                runImport(coordinator, command.uri, resume = false)
            }

            CarImportServiceCommand.Resume -> {
                val uri = repository.sourceUri()
                if (uri == null) {
                    repository.applyResult(
                        null,
                        CarImportResult.Failure(
                            CarImportPhase.VALIDATING,
                            "No persisted import source is available",
                        ),
                    )
                } else {
                    runImport(coordinator, uri, resume = true)
                }
            }

            CarImportServiceCommand.Discard -> {
                val sourceIdentity = repository.sourceUri()?.toString()
                repository.applyResult(
                    sourceIdentity,
                    coordinator.discardIncomplete(),
                )
            }
        }
    }

    private suspend fun runImport(
        coordinator: CarImportCoordinator,
        uri: Uri,
        resume: Boolean,
    ) {
        val source = CarContentUriImportSource(this, uri)
        val result =
            coordinator.runImport(
                source = source,
                resume = resume,
            ) { progress ->
                repository.updateProgress(source.identity, progress)
                updateNotification(progress)
            }
        if (result is CarImportResult.Success) {
            CarLegacyImportProvenanceStore(this)
                .markImported(repository.completedSongIds())
        }
        repository.applyResult(source.identity, result)
    }

    private fun buildCoordinator(): CarImportCoordinator {
        val artworkStore = CarArtworkStore(filesDir)
        return CarImportCoordinator(
            validator = CarPortableArchiveValidator(),
            journalStore = repository.journalStore,
            mediaTarget = CarMedia3ImportTarget(downloadUtil.downloadCache, databaseProvider),
            roomTarget = CarRoomImportTarget(database),
            artworkTarget = artworkStore,
            inspector =
                CarImportInspector(
                    cache = downloadUtil.downloadCache,
                    databaseProvider = databaseProvider,
                    database = database,
                    artworkStore = artworkStore,
                ),
            availableDestinationBytes = { filesDir.usableSpace },
            excludedSongIds = {
                CarDislikeLedgerStore(filesDir)
                    .load()
                    .records
                    .mapTo(linkedSetOf()) { it.songId }
            },
        )
    }

    private fun startInForeground() {
        val notification = buildNotification(null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(progress: CarOperationProgress) {
        try {
            NotificationManagerCompat.from(this).notify(
                NOTIFICATION_ID,
                buildNotification(progress),
            )
        } catch (error: SecurityException) {
            Timber.w(error, "Car import notification permission unavailable")
        }
    }

    private fun buildNotification(progress: CarOperationProgress?) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.download)
            .setContentTitle("Importing MetroList backup")
            .setContentText(progress?.let(::carImportProgressText) ?: "Preparing import")
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .apply {
                val percent = progress?.let(::carImportNotificationPercent)
                if (percent != null) {
                    setProgress(100, percent, false)
                } else {
                    setProgress(0, 0, true)
                }
            }
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Car library import",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "MetroList Car backup import progress"
                    setShowBadge(false)
                },
            )
    }

    companion object {
        const val ACTION_START = "com.metrolist.music.car.import.START"
        const val ACTION_RESUME = "com.metrolist.music.car.import.RESUME"
        const val ACTION_DISCARD = "com.metrolist.music.car.import.DISCARD"

        private const val CHANNEL_ID = "car_import"
        private const val NOTIFICATION_ID = 9201
    }
}
