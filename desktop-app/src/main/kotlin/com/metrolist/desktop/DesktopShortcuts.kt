package com.metrolist.desktop

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

enum class DesktopKey {
    SPACE,
    LEFT,
    RIGHT,
    UP,
    DOWN,
    F,
    ESCAPE,
    OTHER,
}

data class DesktopShortcutEvent(
    val key: DesktopKey,
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false,
    val meta: Boolean = false,
    val isKeyDown: Boolean = true,
)

sealed interface DesktopShortcut {
    data object PlayPause : DesktopShortcut
    data class SeekBy(val deltaMs: Long) : DesktopShortcut
    data class VolumeBy(val delta: Int) : DesktopShortcut
    data object FocusSearch : DesktopShortcut
    data object Escape : DesktopShortcut
}

internal fun resolveShortcut(
    event: DesktopShortcutEvent,
    textInputFocused: Boolean,
): DesktopShortcut? {
    if (!event.isKeyDown) return null
    if (event.key == DesktopKey.ESCAPE) return DesktopShortcut.Escape
    if (textInputFocused) return null

    if (
        event.key == DesktopKey.F &&
        event.ctrl &&
        !event.alt &&
        !event.shift &&
        !event.meta
    ) {
        return DesktopShortcut.FocusSearch
    }

    if (event.ctrl || event.alt || event.shift || event.meta) return null

    return when (event.key) {
        DesktopKey.SPACE -> DesktopShortcut.PlayPause
        DesktopKey.LEFT -> DesktopShortcut.SeekBy(-5_000L)
        DesktopKey.RIGHT -> DesktopShortcut.SeekBy(5_000L)
        DesktopKey.UP -> DesktopShortcut.VolumeBy(5)
        DesktopKey.DOWN -> DesktopShortcut.VolumeBy(-5)
        else -> null
    }
}

internal fun applySeekShortcut(
    positionMs: Long,
    durationMs: Long?,
    deltaMs: Long,
): Long {
    val upper = durationMs?.coerceAtLeast(0L) ?: Long.MAX_VALUE
    return (positionMs + deltaMs)
        .coerceAtLeast(0L)
        .coerceAtMost(upper)
}

internal fun applyVolumeShortcut(
    volume: Int,
    delta: Int,
): Int =
    (volume + delta).coerceIn(0, 100)

internal val LocalTextInputFocusReporter =
    staticCompositionLocalOf<(Boolean) -> Unit> { {} }

internal fun Modifier.trackTextInputFocus(): Modifier =
    composed {
        val reporter = LocalTextInputFocusReporter.current
        onFocusChanged { focusState -> reporter(focusState.isFocused) }
    }

internal fun KeyEvent.toDesktopShortcutEvent(): DesktopShortcutEvent =
    DesktopShortcutEvent(
        key =
            when (key) {
                Key.Spacebar -> DesktopKey.SPACE
                Key.DirectionLeft -> DesktopKey.LEFT
                Key.DirectionRight -> DesktopKey.RIGHT
                Key.DirectionUp -> DesktopKey.UP
                Key.DirectionDown -> DesktopKey.DOWN
                Key.F -> DesktopKey.F
                Key.Escape -> DesktopKey.ESCAPE
                else -> DesktopKey.OTHER
            },
        ctrl = isCtrlPressed,
        alt = isAltPressed,
        shift = isShiftPressed,
        meta = isMetaPressed,
        isKeyDown = type == KeyEventType.KeyDown,
    )
