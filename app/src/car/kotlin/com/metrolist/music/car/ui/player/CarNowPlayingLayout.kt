package com.metrolist.music.car.ui.player

import com.metrolist.music.car.ui.CarViewportMetrics
import com.metrolist.music.car.ui.theme.CarDesignTokens
import kotlin.math.min

data class CarNowPlayingLayoutMetrics(
    val contentWidthPx: Float,
    val contentHeightPx: Float,
    val horizontalPaddingPx: Float,
    val verticalPaddingPx: Float,
    val gapPx: Float,
    val controlsLeftPx: Float,
    val controlsWidthPx: Float,
    val artworkLeftPx: Float,
    val artworkSizePx: Float,
    val playPauseHitPx: Float,
    val previousNextHitPx: Float,
    val secondaryHitPx: Float,
) {
    val controlsRightPx: Float
        get() = controlsLeftPx + controlsWidthPx

    val artworkRightPx: Float
        get() = artworkLeftPx + artworkSizePx

    val controlsCenterPx: Float
        get() = controlsLeftPx + (controlsWidthPx / 2f)

    val artworkCenterPx: Float
        get() = artworkLeftPx + (artworkSizePx / 2f)

    companion object {
        fun forViewport(viewport: CarViewportMetrics): CarNowPlayingLayoutMetrics {
            val scale = viewport.scale
            val navigationRailPx = CarDesignTokens.NavigationRailWidthPx * scale
            val contentWidth = (viewport.widthPx - navigationRailPx).coerceAtLeast(1f)
            val contentHeight = viewport.heightPx.toFloat().coerceAtLeast(1f)

            val horizontalPadding =
                (CarDesignTokens.ContentPaddingPx * scale).coerceAtLeast(14f)
            val verticalPadding =
                (CarDesignTokens.ContentPaddingPx * scale).coerceAtLeast(12f)
            val gap =
                (CarDesignTokens.MajorHorizontalGapPx * scale).coerceAtLeast(18f)

            val innerWidth =
                (contentWidth - (2f * horizontalPadding)).coerceAtLeast(1f)
            val innerHeight =
                (contentHeight - (2f * verticalPadding)).coerceAtLeast(1f)

            val desiredArtwork = innerWidth * 0.43f
            val artworkSize =
                min(innerHeight, desiredArtwork)
                    .coerceAtLeast(160f.coerceAtMost(innerHeight))
            val controlsWidth =
                (innerWidth - gap - artworkSize).coerceAtLeast(1f)

            val controlsLeft = horizontalPadding
            val artworkLeft = controlsLeft + controlsWidth + gap

            val wideReference = viewport.widthPx >= 1000
            val playPauseMin = if (wideReference) 108f else 96f
            val previousNextMin = if (wideReference) 90f else 80f

            return CarNowPlayingLayoutMetrics(
                contentWidthPx = contentWidth,
                contentHeightPx = contentHeight,
                horizontalPaddingPx = horizontalPadding,
                verticalPaddingPx = verticalPadding,
                gapPx = gap,
                controlsLeftPx = controlsLeft,
                controlsWidthPx = controlsWidth,
                artworkLeftPx = artworkLeft,
                artworkSizePx = artworkSize,
                playPauseHitPx =
                    (CarDesignTokens.PrimaryPlayPauseHitTargetPx * scale)
                        .coerceIn(playPauseMin, 124f),
                previousNextHitPx =
                    (CarDesignTokens.PreviousNextHitTargetPx * scale)
                        .coerceIn(previousNextMin, 104f),
                secondaryHitPx =
                    (CarDesignTokens.SecondaryActionHitTargetPx * scale)
                        .coerceIn(60f, 84f),
            )
        }
    }
}
