package com.metrolist.music.car.importer

import android.content.Context
import android.net.Uri
import com.metrolist.music.car.feedback.CarOperationProgress
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface CarImportStatus {
    data object Idle : CarImportStatus

    data class Interrupted(
        val sourceIdentity: String,
        val phase: CarImportPhase,
    ) : CarImportStatus

    data class Running(
        val sourceIdentity: String,
        val progress: CarOperationProgress,
    ) : CarImportStatus
    data class Complete(
        val sourceIdentity: String,
        val importedSongs: Int,
        val totalSongs: Int,
    ) : CarImportStatus

    data class Failed(
        val sourceIdentity: String?,
        val phase: CarImportPhase,
        val message: String,
    ) : CarImportStatus

    data class Discarded(
        val preservedCompleted: Int,
        val cleanedIncomplete: Int,
    ) : CarImportStatus
}

@Singleton
class CarImportRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val preferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val journalStore = CarImportJournalStore(context.filesDir.resolve("car-import"))
    private val _status = MutableStateFlow(initialStatus())
    val status: StateFlow<CarImportStatus> = _status.asStateFlow()

    fun rememberSource(uri: Uri) {
        preferences.edit().putString(KEY_SOURCE_URI, uri.toString()).apply()
    }

    fun sourceUri(): Uri? =
        preferences.getString(KEY_SOURCE_URI, null)?.let(Uri::parse)

    fun updateProgress(
        sourceIdentity: String,
        progress: CarOperationProgress,
    ) {
        _status.value = CarImportStatus.Running(sourceIdentity, progress)
    }

    fun applyResult(
        sourceIdentity: String?,
        result: CarImportResult,
    ) {
        _status.value =
            when (result) {
                is CarImportResult.Success ->
                    CarImportStatus.Complete(
                        sourceIdentity = sourceIdentity.orEmpty(),
                        importedSongs = result.importedSongs,
                        totalSongs = result.totalSongs,
                    )

                is CarImportResult.Failure ->
                    CarImportStatus.Failed(
                        sourceIdentity = sourceIdentity,
                        phase = result.phase,
                        message = result.message,
                    )

                is CarImportResult.Discarded ->
                    CarImportStatus.Discarded(
                        preservedCompleted = result.preservedCompleted,
                        cleanedIncomplete = result.cleanedIncomplete,
                    )
            }
    }

    fun completedSongIds(): Set<String> =
        runCatching {
            journalStore.load()
                ?.takeIf { it.phase == CarImportPhase.COMPLETE }
                ?.songs
                ?.values
                ?.filter(CarSongImportRecord::completed)
                ?.mapTo(linkedSetOf(), CarSongImportRecord::songId)
                .orEmpty()
        }.getOrDefault(emptySet())
    fun refreshFromDisk() {
        _status.value = initialStatus()
    }

    fun reset() {
        journalStore.clear()
        preferences.edit().remove(KEY_SOURCE_URI).apply()
        _status.value = CarImportStatus.Idle
    }

    private fun initialStatus(): CarImportStatus {
        val journal =
            runCatching { journalStore.load() }
                .getOrElse { error ->
                    return CarImportStatus.Failed(
                        sourceIdentity = sourceUri()?.toString(),
                        phase = CarImportPhase.VALIDATING,
                        message = error.message ?: "Could not read import journal",
                    )
                }
                ?: return CarImportStatus.Idle

        return if (journal.phase == CarImportPhase.COMPLETE) {
            CarImportStatus.Complete(
                sourceIdentity = journal.sourceIdentity,
                importedSongs = journal.songs.values.count(CarSongImportRecord::completed),
                totalSongs = journal.songs.size,
            )
        } else {
            CarImportStatus.Interrupted(
                sourceIdentity = journal.sourceIdentity,
                phase = journal.phase,
            )
        }
    }

    private companion object {
        const val PREFS_NAME = "car-import"
        const val KEY_SOURCE_URI = "source-uri"
    }
}
