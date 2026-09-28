package com.metrolist.music.car.library

import com.metrolist.music.car.feedback.CarOperationProgress
import com.metrolist.music.car.feedback.CarOperationStage
import com.metrolist.music.car.feedback.CarOperationState

data class CarQueueOperation(
    val songId: String?,
    val operation: CarOperationState,
)

fun carQueuePreparingOperation(
    songId: String,
    title: String,
): CarQueueOperation =
    CarQueueOperation(
        songId = songId,
        operation =
            CarOperationState.Running(
                CarOperationProgress(
                    stage = CarOperationStage.PREPARING_QUEUE,
                    detail = "Preparing $title",
                ),
            ),
    )

fun carQueueUnavailableOperation(
    songId: String,
    message: String,
): CarQueueOperation =
    CarQueueOperation(
        songId = songId,
        operation =
            CarOperationState.Error(
                message = message,
                actionHint = "Choose another track or refresh the library.",
            ),
    )
