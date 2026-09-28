package com.metrolist.music.car.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CarBackground = Color(0xFF0B0D10)
private val CarSurface = Color(0xFF12161B)
private val CarSurfaceContainer = Color(0xFF171C22)
private val CarPrimary = Color(0xFFED5564)
private val CarOnSurface = Color(0xFFF4F5F7)
private val CarOnSurfaceVariant = Color(0xFFB8C0CA)
private val CarOutline = Color(0xFF333A44)

internal fun carColorScheme(): ColorScheme =
    darkColorScheme(
        primary = CarPrimary,
        onPrimary = Color.White,
        background = CarBackground,
        onBackground = CarOnSurface,
        surface = CarSurface,
        onSurface = CarOnSurface,
        onSurfaceVariant = CarOnSurfaceVariant,
        outline = CarOutline,
    ).copy(
        surfaceContainer = CarSurfaceContainer,
    )

@Composable
fun CarTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = carColorScheme(),
        content = content,
    )
}
