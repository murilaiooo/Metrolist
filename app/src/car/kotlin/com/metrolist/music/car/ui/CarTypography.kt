package com.metrolist.music.car.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

internal fun carTypography(
    viewport: CarViewportMetrics,
    density: Density,
): Typography {
    val base = Typography()
    val scaledDensity =
        (density.density * density.fontScale)
            .takeIf { it.isFinite() && it > 0f }
            ?: 1f

    fun pxToSp(referencePx: Float): TextUnit =
        ((referencePx * viewport.scale) / scaledDensity).sp

    return base.copy(
        headlineLarge =
            base.headlineLarge.copy(
                fontSize = pxToSp(40f),
                lineHeight = pxToSp(48f),
            ),
        titleLarge =
            base.titleLarge.copy(
                fontSize = pxToSp(28f),
                lineHeight = pxToSp(34f),
            ),
        titleMedium =
            base.titleMedium.copy(
                fontSize = pxToSp(23f),
                lineHeight = pxToSp(29f),
            ),
        bodyLarge =
            base.bodyLarge.copy(
                fontSize = pxToSp(21f),
                lineHeight = pxToSp(28f),
            ),
        bodyMedium =
            base.bodyMedium.copy(
                fontSize = pxToSp(18f),
                lineHeight = pxToSp(24f),
            ),
        labelLarge =
            base.labelLarge.copy(
                fontSize = pxToSp(18f),
                lineHeight = pxToSp(22f),
            ),
        labelMedium =
            base.labelMedium.copy(
                fontSize = pxToSp(16f),
                lineHeight = pxToSp(20f),
            ),
    )
}