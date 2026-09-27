package com.metrolist.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopShortcutsTest {
    @Test
    fun resolvesUnmodifiedMediaShortcuts() {
        assertEquals(DesktopShortcut.PlayPause, resolveShortcut(event(DesktopKey.SPACE), false))
        assertEquals(DesktopShortcut.SeekBy(-5_000L), resolveShortcut(event(DesktopKey.LEFT), false))
        assertEquals(DesktopShortcut.SeekBy(5_000L), resolveShortcut(event(DesktopKey.RIGHT), false))
        assertEquals(DesktopShortcut.VolumeBy(5), resolveShortcut(event(DesktopKey.UP), false))
        assertEquals(DesktopShortcut.VolumeBy(-5), resolveShortcut(event(DesktopKey.DOWN), false))
    }

    @Test
    fun ctrlFResolvesFocusSearchButModifiedMediaKeysDoNot() {
        assertEquals(
            DesktopShortcut.FocusSearch,
            resolveShortcut(event(DesktopKey.F, ctrl = true), false),
        )
        assertNull(resolveShortcut(event(DesktopKey.SPACE, ctrl = true), false))
        assertNull(resolveShortcut(event(DesktopKey.LEFT, shift = true), false))
        assertNull(resolveShortcut(event(DesktopKey.F, ctrl = true, shift = true), false))
    }

    @Test
    fun typingSuppressesEverythingExceptEscape() {
        assertNull(resolveShortcut(event(DesktopKey.SPACE), true))
        assertNull(resolveShortcut(event(DesktopKey.RIGHT), true))
        assertNull(resolveShortcut(event(DesktopKey.F, ctrl = true), true))
        assertEquals(DesktopShortcut.Escape, resolveShortcut(event(DesktopKey.ESCAPE), true))
    }

    @Test
    fun escapeHasPrecedenceAndKeyUpIsIgnored() {
        assertEquals(
            DesktopShortcut.Escape,
            resolveShortcut(event(DesktopKey.ESCAPE, ctrl = true, shift = true), false),
        )
        assertNull(resolveShortcut(event(DesktopKey.SPACE, isKeyDown = false), false))
    }

    @Test
    fun seekAndVolumeHelpersClampAtBoundaries() {
        assertEquals(0L, applySeekShortcut(positionMs = 2_000L, durationMs = 20_000L, deltaMs = -5_000L))
        assertEquals(20_000L, applySeekShortcut(positionMs = 19_000L, durationMs = 20_000L, deltaMs = 5_000L))
        assertEquals(15_000L, applySeekShortcut(positionMs = 10_000L, durationMs = null, deltaMs = 5_000L))
        assertEquals(0, applyVolumeShortcut(2, -5))
        assertEquals(100, applyVolumeShortcut(98, 5))
        assertEquals(55, applyVolumeShortcut(50, 5))
    }

    private fun event(
        key: DesktopKey,
        ctrl: Boolean = false,
        alt: Boolean = false,
        shift: Boolean = false,
        meta: Boolean = false,
        isKeyDown: Boolean = true,
    ) =
        DesktopShortcutEvent(key, ctrl, alt, shift, meta, isKeyDown)
}
