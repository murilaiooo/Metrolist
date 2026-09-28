package com.metrolist.music.car.library

import android.content.Context
import android.net.Uri
import com.metrolist.music.car.feedback.CarOperationProgress
import com.metrolist.music.car.feedback.CarOperationStage
import com.metrolist.music.car.feedback.CarOperationState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CarLibrarySession internal constructor(
    internal val repository: CarLibraryRepository,
) {
    constructor(context: Context) : this(
        CarLibraryRepository(
            locationStore = SharedPreferencesCarLibraryLocationStore(context),
            accessFactory = AndroidCarLibraryTreeAccessFactory(context),
        ),
    ) {
        this.context = context.applicationContext
    }

    private var context: Context? = null

    val mediaResolver = CarLibraryMediaResolver(repository)

    private val _index = MutableStateFlow<CarLibraryIndex?>(null)
    val index: StateFlow<CarLibraryIndex?> = _index.asStateFlow()

    private val _operation = MutableStateFlow<CarOperationState>(CarOperationState.Idle)
    val operation: StateFlow<CarOperationState> = _operation.asStateFlow()

    suspend fun reopen(): CarLibraryOpenResult {
        _operation.value =
            CarOperationState.Running(
                CarOperationProgress(
                    stage = CarOperationStage.RESTORING,
                    detail = "Restoring Car Library",
                ),
            )
        val result = repository.reopen()
        applyOpenResult(result)
        _operation.value = result.toOperationState("Car Library restored")
        return result
    }

    suspend fun open(uri: Uri): CarLibraryOpenResult {
        val appContext = requireNotNull(context) { "URI open requires an Android-backed session" }
        return open(
            ContentTreeCarLibraryTreeAccess(
                resolver = appContext.contentResolver,
                treeUri = uri,
            ),
        )
    }

    internal suspend fun open(access: CarLibraryTreeAccess): CarLibraryOpenResult {
        _operation.value =
            CarOperationState.Running(
                CarOperationProgress(
                    stage = CarOperationStage.OPENING_LIBRARY,
                    detail = "Opening Car Library",
                ),
            )
        val result = repository.open(access)
        applyOpenResult(result)
        _operation.value = result.toOperationState("Car Library opened")
        return result
    }

    suspend fun refresh(): CarLibraryRefreshResult {
        _operation.value =
            CarOperationState.Running(
                CarOperationProgress(
                    stage = CarOperationStage.REFRESHING_LIBRARY,
                    detail = "Refreshing Car Library",
                ),
            )
        val result = repository.refresh()
        applyRefreshResult(result)
        _operation.value = result.toOperationState()
        return result
    }

    private fun applyOpenResult(result: CarLibraryOpenResult) {
        _index.value =
            when (result) {
                is CarLibraryOpenResult.Opened -> result.index
                is CarLibraryOpenResult.Invalid,
                is CarLibraryOpenResult.Unavailable,
                -> null
            }
    }

    private fun applyRefreshResult(result: CarLibraryRefreshResult) {
        when (result) {
            is CarLibraryRefreshResult.Unchanged -> _index.value = result.index
            is CarLibraryRefreshResult.Updated -> _index.value = result.index
            is CarLibraryRefreshResult.Invalid,
            is CarLibraryRefreshResult.Unavailable,
            -> Unit
        }
    }
}

internal class CarLibraryStartupCoordinator(
    private val reopen: suspend () -> CarLibraryOpenResult,
) {
    private var hasReopened = false

    suspend fun reopenOnce(): CarLibraryOpenResult? {
        if (hasReopened) return null
        hasReopened = true
        return reopen()
    }
}

private fun CarLibraryOpenResult.toOperationState(successSummary: String): CarOperationState =
    when (this) {
        is CarLibraryOpenResult.Opened -> CarOperationState.Success(successSummary)
        is CarLibraryOpenResult.Unavailable ->
            CarOperationState.Error(
                message = reason,
                actionHint =
                    if (location == null) {
                        "Select a Car Library folder."
                    } else {
                        "Select the Car Library folder again."
                    },
            )
        is CarLibraryOpenResult.Invalid ->
            CarOperationState.Error(
                message = errors.joinToString(" · "),
                actionHint = "Select a valid MetroList Car Library folder.",
            )
    }

private fun CarLibraryRefreshResult.toOperationState(): CarOperationState =
    when (this) {
        is CarLibraryRefreshResult.Unchanged ->
            CarOperationState.Success("Car Library is already up to date")
        is CarLibraryRefreshResult.Updated ->
            CarOperationState.Success("Car Library refreshed")
        is CarLibraryRefreshResult.Unavailable ->
            CarOperationState.Error(
                message = reason,
                actionHint = "Check the selected folder or select it again.",
            )
        is CarLibraryRefreshResult.Invalid ->
            CarOperationState.Error(
                message = errors.joinToString(" · "),
                actionHint = "Select a valid MetroList Car Library folder.",
            )
    }
