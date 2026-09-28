package com.metrolist.music.car.ui.player

import kotlin.math.abs

enum class CarArtworkSwipeAction {
    NONE,
    PREVIOUS,
    NEXT,
}

fun decideCarArtworkSwipe(
    offsetPx: Float,
    artworkWidthPx: Float,
    velocityPxPerMs: Float,
    canPrevious: Boolean,
    canNext: Boolean,
    isRtl: Boolean,
): CarArtworkSwipeAction {
    if (!offsetPx.isFinite() || !artworkWidthPx.isFinite() || artworkWidthPx <= 0f) {
        return CarArtworkSwipeAction.NONE
    }

    val distance = abs(offsetPx)
    val slowCommit = distance >= artworkWidthPx * 0.19f
    val fastCommit =
        distance >= artworkWidthPx * 0.10f &&
            abs(velocityPxPerMs) >= 1.2f
    if (!slowCommit && !fastCommit) return CarArtworkSwipeAction.NONE

    val physicalRight = offsetPx > 0f
    val action =
        when {
            isRtl && physicalRight -> CarArtworkSwipeAction.NEXT
            isRtl && !physicalRight -> CarArtworkSwipeAction.PREVIOUS
            !isRtl && physicalRight -> CarArtworkSwipeAction.PREVIOUS
            else -> CarArtworkSwipeAction.NEXT
        }

    return when (action) {
        CarArtworkSwipeAction.PREVIOUS ->
            if (canPrevious) action else CarArtworkSwipeAction.NONE
        CarArtworkSwipeAction.NEXT ->
            if (canNext) action else CarArtworkSwipeAction.NONE
        CarArtworkSwipeAction.NONE -> CarArtworkSwipeAction.NONE
    }
}
