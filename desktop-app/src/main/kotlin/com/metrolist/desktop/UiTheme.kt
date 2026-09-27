package com.metrolist.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val MetroPurple = Color(0xFF6F4BB8)
private val MetroPurpleDark = Color(0xFF5B3B9F)
private val MetroLavender = Color(0xFFF5F1FA)
private val MetroSurface = Color(0xFFFFFBFF)
private val MetroOutline = Color(0xFFD0C7D8)

private val LightColors =
    lightColorScheme(
        primary = MetroPurple,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE9DDFF),
        onPrimaryContainer = Color(0xFF24123F),
        secondary = Color(0xFF655A70),
        background = MetroLavender,
        onBackground = Color(0xFF1D1A20),
        surface = MetroSurface,
        onSurface = Color(0xFF1D1A20),
        surfaceVariant = Color(0xFFEAE3EF),
        onSurfaceVariant = Color(0xFF4C4651),
        outline = MetroOutline,
        error = Color(0xFFB3261E),
    )

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFFD3B8FF),
        onPrimary = Color(0xFF3A1F67),
        primaryContainer = MetroPurpleDark,
        onPrimaryContainer = Color(0xFFF0E4FF),
        secondary = Color(0xFFCEC2D9),
        background = Color(0xFF151218),
        onBackground = Color(0xFFEAE1EC),
        surface = Color(0xFF1C191F),
        onSurface = Color(0xFFEAE1EC),
        surfaceVariant = Color(0xFF332E36),
        onSurfaceVariant = Color(0xFFCFC6D1),
        outline = Color(0xFF958E99),
        error = Color(0xFFFFB4AB),
    )

private val MetroShapes =
    Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(10.dp),
        large = RoundedCornerShape(14.dp),
        extraLarge = RoundedCornerShape(18.dp),
    )

@Composable
internal fun MetroListTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme =
            if (isSystemInDarkTheme()) {
                DarkColors
            } else {
                LightColors
            },
        shapes = MetroShapes,
        content = content,
    )
}
