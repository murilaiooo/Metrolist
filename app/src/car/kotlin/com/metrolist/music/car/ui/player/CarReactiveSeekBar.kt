package com.metrolist.music.car.ui.player

import android.graphics.Color as AndroidColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.metrolist.music.ui.theme.rankedColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class CarSeekPalette(
    val start: Color,
    val middle: Color,
    val end: Color,
)

private val DefaultCarSeekPalette =
    CarSeekPalette(
        start = Color(0xFF4B8DFF),
        middle = Color(0xFF77E4FF),
        end = Color(0xFFC88CFF),
    )

@Composable
internal fun rememberCarSeekPalette(
    mediaId: String?,
    thumbnailUrl: String?,
): CarSeekPalette {
    val context = LocalContext.current
    var palette by remember(mediaId) { mutableStateOf(DefaultCarSeekPalette) }

    LaunchedEffect(mediaId, thumbnailUrl) {
        palette = DefaultCarSeekPalette
        val url = thumbnailUrl?.takeIf(String::isNotBlank) ?: return@LaunchedEffect
        val dominant =
            withContext(Dispatchers.IO) {
                val request =
                    ImageRequest.Builder(context)
                        .data(url)
                        .size(96, 96)
                        .allowHardware(false)
                        .build()
                val result = runCatching { context.imageLoader.execute(request) }.getOrNull()
                val bitmap = result?.image?.toBitmap() ?: return@withContext null
                val generated =
                    Palette.from(bitmap)
                        .maximumColorCount(8)
                        .resizeBitmapArea(96 * 96)
                        .generate()
                Color(generated.rankedColors(1, DefaultCarSeekPalette.start.toArgb()).first())
            }
        if (dominant != null) palette = carSeekPaletteFrom(dominant)
    }
    return palette
}

private fun carSeekPaletteFrom(dominant: Color): CarSeekPalette {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(dominant.toArgb(), hsv)
    if (hsv[1] < 0.12f || hsv[2] < 0.18f) return DefaultCarSeekPalette

    val hue = hsv[0]
    val saturation = hsv[1].coerceIn(0.48f, 0.72f)

    fun tone(
        hueOffset: Float,
        saturationScale: Float,
        value: Float,
    ): Color {
        val adjustedHue = (hue + hueOffset + 360f) % 360f
        return Color(
            AndroidColor.HSVToColor(
                floatArrayOf(
                    adjustedHue,
                    (saturation * saturationScale).coerceIn(0.38f, 0.78f),
                    value,
                ),
            ),
        )
    }

    return CarSeekPalette(
        start = tone(-18f, 1.05f, 0.94f),
        middle = tone(5f, 0.78f, 1f),
        end = tone(34f, 0.92f, 0.96f),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CarReactiveSeekBar(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    palette: CarSeekPalette,
    audioLevel: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val safeValue = value.coerceIn(valueRange.start, valueRange.endInclusive)
    val sliderState =
        remember(valueRange.start, valueRange.endInclusive) {
            SliderState(value = safeValue, trackRange = valueRange)
        }
    sliderState.value = safeValue

    val energy by
        animateFloatAsState(
            targetValue = if (enabled) audioLevel.coerceIn(0f, 1f) else 0f,
            animationSpec = tween(durationMillis = 90),
            label = "carSeekEnergy",
        )
    val start by animateColorAsState(palette.start, tween(650), label = "carSeekStart")
    val middle by animateColorAsState(palette.middle, tween(650), label = "carSeekMiddle")
    val end by animateColorAsState(palette.end, tween(650), label = "carSeekEnd")
    val animatedPalette = CarSeekPalette(start, middle, end)

    Slider(
        state = sliderState,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        enabled = enabled,
        thumb = {
            CarSeekThumb(
                palette = animatedPalette,
                energy = energy,
                enabled = enabled,
            )
        },
        track = { state ->
            CarSeekTrack(
                sliderState = state,
                palette = animatedPalette,
                energy = energy,
                enabled = enabled,
            )
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CarSeekTrack(
    sliderState: SliderState,
    palette: CarSeekPalette,
    energy: Float,
    enabled: Boolean,
) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(30.dp),
    ) {
        val range = sliderState.trackRange
        val fraction =
            if (range.endInclusive == range.start) {
                0f
            } else {
                ((sliderState.value - range.start) / (range.endInclusive - range.start))
                    .coerceIn(0f, 1f)
            }
        val y = center.y
        val start = Offset(0f, y)
        val end = Offset(size.width, y)
        val activeEnd = Offset(size.width * fraction, y)
        val activeBrush =
            Brush.linearGradient(
                colors = listOf(palette.start, palette.middle, palette.end),
                start = start,
                end = Offset(activeEnd.x.coerceAtLeast(1f), y),
            )
        val alpha = if (enabled) 1f else 0.38f
        drawLine(
            color = Color(0xFF232A35).copy(alpha = alpha),
            start = start,
            end = end,
            strokeWidth = 7.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color(0xFF9BA8BA).copy(alpha = 0.22f * alpha),
            start = start,
            end = end,
            strokeWidth = 1.dp.toPx(),
            cap = StrokeCap.Round,
        )

        if (fraction > 0f) {
            drawLine(
                brush =
                    Brush.linearGradient(
                        colors =
                            listOf(
                                palette.start.copy(alpha = 0.08f + (energy * 0.08f)),
                                palette.middle.copy(alpha = 0.10f + (energy * 0.10f)),
                                palette.end.copy(alpha = 0.08f + (energy * 0.08f)),
                            ),
                        start = start,
                        end = Offset(activeEnd.x.coerceAtLeast(1f), y),
                    ),
                start = start,
                end = activeEnd,
                strokeWidth = (13f + (energy * 5f)).dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                brush = activeBrush,
                start = start,
                end = activeEnd,
                strokeWidth = 6.dp.toPx(),
                cap = StrokeCap.Round,
                alpha = alpha,
            )
            drawLine(
                color = Color.White.copy(alpha = 0.30f * alpha),
                start = Offset(start.x, y - 1.dp.toPx()),
                end = Offset(activeEnd.x, y - 1.dp.toPx()),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun CarSeekThumb(
    palette: CarSeekPalette,
    energy: Float,
    enabled: Boolean,
) {
    val alpha = if (enabled) 1f else 0.42f
    Canvas(Modifier.size(34.dp)) {
        val radius = size.minDimension / 2f
        drawCircle(
            color = palette.end.copy(alpha = (0.08f + energy * 0.12f) * alpha),
            radius = radius,
        )
        drawCircle(
            brush =
                Brush.radialGradient(
                    colors =
                        listOf(
                            Color(0xFFF8FBFF),
                            Color(0xFFC7D0DC),
                            Color(0xFF626D7B),
                        ),
                ),
            radius = radius * 0.70f,
            alpha = alpha,
        )
        drawCircle(
            brush =
                Brush.sweepGradient(
                    listOf(
                        palette.start,
                        palette.middle,
                        palette.end,
                        palette.start,
                    ),
                ),
            radius = radius * 0.76f,
            style = Stroke(width = 2.dp.toPx()),
            alpha = alpha,
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.50f * alpha),
            radius = radius * 0.62f,
            style = Stroke(width = 0.7.dp.toPx()),
        )
    }
}
