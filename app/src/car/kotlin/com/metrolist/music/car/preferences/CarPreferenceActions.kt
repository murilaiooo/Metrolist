package com.metrolist.music.car.preferences

suspend fun performCarDislike(
    persist: suspend () -> Unit,
    mutatePlayback: () -> Unit,
) {
    persist()
    mutatePlayback()
}
