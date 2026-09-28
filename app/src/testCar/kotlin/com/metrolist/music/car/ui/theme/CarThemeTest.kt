package com.metrolist.music.car.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarThemeTest {
    @Test
    fun carPaletteIsFixedDarkAndIndependentOfSystemTheme() {
        val scheme = carColorScheme()

        assertEquals(Color(0xFF0B0D10), scheme.background)
        assertEquals(Color(0xFF12161B), scheme.surface)
        assertEquals(Color(0xFF171C22), scheme.surfaceContainer)
        assertEquals(Color(0xFFED5564), scheme.primary)
        assertEquals(Color(0xFFF4F5F7), scheme.onBackground)
        assertEquals(Color(0xFFF4F5F7), scheme.onSurface)
        assertEquals(Color(0xFFB8C0CA), scheme.onSurfaceVariant)
        assertEquals(Color(0xFF333A44), scheme.outline)

        assertTrue(scheme.background.luminance() < 0.05f)
        assertTrue(scheme.surface.luminance() < 0.05f)
        assertTrue(scheme.onBackground.luminance() > 0.8f)
    }
}
