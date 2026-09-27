package com.metrolist.music.car.input

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CarInputObservation(
    val signature: CarKeySignature,
    val keyAction: Int,
    val decision: CarInputDecision,
)

class CarInputDiagnostics {
    private val _lastObservation = MutableStateFlow<CarInputObservation?>(null)
    val lastObservation: StateFlow<CarInputObservation?> = _lastObservation.asStateFlow()

    fun record(
        signature: CarKeySignature,
        keyAction: Int,
        decision: CarInputDecision,
    ) {
        _lastObservation.value =
            CarInputObservation(
                signature = signature,
                keyAction = keyAction,
                decision = decision,
            )
    }
}
