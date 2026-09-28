package com.metrolist.music.car.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

data class CarViewportMetrics(
    val widthPx: Int,
    val heightPx: Int,
    val scale: Float,
) {
    fun pxToDp(
        referencePx: Float,
        density: Float,
    ): Dp {
        val safeDensity = density.takeIf { it.isFinite() && it > 0f } ?: 1f
        return ((referencePx * scale) / safeDensity).dp
    }

    companion object {
        private const val REFERENCE_WIDTH_PX = 1280f
        private const val REFERENCE_HEIGHT_PX = 720f
        private const val MIN_SCALE = 0.55f
        private const val MAX_SCALE = 1.5f

        fun from(
            widthPx: Int,
            heightPx: Int,
        ): CarViewportMetrics {
            val rawScale =
                min(
                    widthPx.toFloat() / REFERENCE_WIDTH_PX,
                    heightPx.toFloat() / REFERENCE_HEIGHT_PX,
                )
            val scale = rawScale.coerceIn(MIN_SCALE, MAX_SCALE)
            return CarViewportMetrics(widthPx, heightPx, scale)
        }
    }
}
