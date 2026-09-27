package com.metrolist.music.car.ui.player

enum class CarPlayerControl {
    PREVIOUS,
    PLAY_PAUSE,
    NEXT,
    SEEK,
    QUEUE,
    SHUFFLE,
    REPEAT,
    FAVORITE,
}

fun defaultCarNowPlayingControls(): List<CarPlayerControl> =
    listOf(
        CarPlayerControl.PREVIOUS,
        CarPlayerControl.PLAY_PAUSE,
        CarPlayerControl.NEXT,
        CarPlayerControl.SEEK,
        CarPlayerControl.QUEUE,
        CarPlayerControl.SHUFFLE,
        CarPlayerControl.REPEAT,
        CarPlayerControl.FAVORITE,
    )
