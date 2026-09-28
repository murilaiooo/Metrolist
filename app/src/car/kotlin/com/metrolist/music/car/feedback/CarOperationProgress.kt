package com.metrolist.music.car.feedback

enum class CarOperationStage {
    PREPARING,
    VALIDATING,
    IMPORTING,
    FINALIZING,
    OPENING_LIBRARY,
    REFRESHING_LIBRARY,
    PREPARING_QUEUE,
    RESTORING,
}

data class CarOperationProgress(
    val stage: CarOperationStage,
    val completedItems: Int? = null,
    val totalItems: Int? = null,
    val bytesProcessed: Long? = null,
    val totalBytes: Long? = null,
    val bytesPerSecond: Long? = null,
    val detail: String? = null,
) {
    val fraction: Float?
        get() {
            val total = totalBytes ?: return null
            if (total <= 0L) return null
            val processed = bytesProcessed ?: return null
            return (processed.toDouble() / total.toDouble())
                .toFloat()
                .coerceIn(0f, 1f)
        }
}

sealed interface CarOperationState {
    data object Idle : CarOperationState

    data class Running(
        val progress: CarOperationProgress,
    ) : CarOperationState

    data class Success(
        val summary: String,
    ) : CarOperationState

    data class Error(
        val message: String,
        val actionHint: String? = null,
    ) : CarOperationState
}
