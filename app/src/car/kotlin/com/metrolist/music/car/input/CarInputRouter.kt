package com.metrolist.music.car.input

import android.view.KeyEvent

enum class CarMediaAction {
    PLAY,
    PAUSE,
    PLAY_PAUSE,
    NEXT,
    PREVIOUS,
    STOP,
}

data class CarKeySignature(
    val keyCode: Int,
    val scanCode: Int,
    val source: Int,
)

data class CarInputDecision(
    val action: CarMediaAction?,
    val consume: Boolean,
)

class CarInputRouter(
    private val confirmedVendorMappings: Map<CarKeySignature, CarMediaAction> = emptyMap(),
) {
    fun decide(
        signature: CarKeySignature,
        keyAction: Int,
    ): CarInputDecision {
        if (keyAction != KeyEvent.ACTION_DOWN) {
            return CarInputDecision(action = null, consume = false)
        }

        if (signature.keyCode in VOLUME_KEYS) {
            return CarInputDecision(action = null, consume = false)
        }

        STANDARD_MEDIA_KEYS[signature.keyCode]?.let { action ->
            return CarInputDecision(action = action, consume = false)
        }

        return confirmedVendorMappings[signature]
            ?.let { CarInputDecision(action = it, consume = true) }
            ?: CarInputDecision(action = null, consume = false)
    }

    private companion object {
        val VOLUME_KEYS =
            setOf(
                KeyEvent.KEYCODE_VOLUME_UP,
                KeyEvent.KEYCODE_VOLUME_DOWN,
                KeyEvent.KEYCODE_VOLUME_MUTE,
            )

        val STANDARD_MEDIA_KEYS =
            mapOf(
                KeyEvent.KEYCODE_MEDIA_PLAY to CarMediaAction.PLAY,
                KeyEvent.KEYCODE_MEDIA_PAUSE to CarMediaAction.PAUSE,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE to CarMediaAction.PLAY_PAUSE,
                KeyEvent.KEYCODE_MEDIA_NEXT to CarMediaAction.NEXT,
                KeyEvent.KEYCODE_MEDIA_PREVIOUS to CarMediaAction.PREVIOUS,
                KeyEvent.KEYCODE_MEDIA_STOP to CarMediaAction.STOP,
            )
    }
}
