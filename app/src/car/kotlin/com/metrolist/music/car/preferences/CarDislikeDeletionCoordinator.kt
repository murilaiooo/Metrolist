package com.metrolist.music.car.preferences

data class CarDislikeDeletionResult(
    val deletedCount: Int,
    val failedCount: Int,
)

class CarDislikeDeletionCoordinator(
    private val repository: CarDislikeRepository,
    private val deleteOne: suspend (CarDislikeRecord) -> Result<Unit>,
) {
    suspend fun deletePending(confirmed: Boolean): CarDislikeDeletionResult {
        if (!confirmed) return CarDislikeDeletionResult(0, 0)

        var deleted = 0
        var failed = 0
        repository.records.value
            .filterNot(CarDislikeRecord::isDeleted)
            .forEach { record ->
                deleteOne(record)
                    .onSuccess {
                        repository.markDeleted(record.songId)
                        deleted += 1
                    }
                    .onFailure { error ->
                        repository.markDeletionError(
                            record.songId,
                            error.message ?: error::class.java.simpleName,
                        )
                        failed += 1
                    }
            }

        return CarDislikeDeletionResult(
            deletedCount = deleted,
            failedCount = failed,
        )
    }
}
