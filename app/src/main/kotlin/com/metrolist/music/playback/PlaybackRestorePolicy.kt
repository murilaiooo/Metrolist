package com.metrolist.music.playback

fun shouldUsePlatformScheduler(isCar: Boolean): Boolean = !isCar

fun restoredPlayWhenReady(
    isCar: Boolean,
    savedPlayWhenReady: Boolean,
): Boolean = isCar && savedPlayWhenReady
