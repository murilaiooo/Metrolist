package com.metrolist.desktop.player

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

internal fun quoteMpvArgument(value: String): String {
    val escaped =
        value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
    return "\"" + escaped + "\""
}

internal interface MpvLibrary : Library {
    fun mpv_create(): Pointer?
    fun mpv_initialize(context: Pointer): Int
    fun mpv_set_option_string(
        context: Pointer,
        name: String,
        data: String,
    ): Int
    fun mpv_command_string(
        context: Pointer,
        command: String,
    ): Int
    fun mpv_set_property_string(
        context: Pointer,
        name: String,
        data: String,
    ): Int
    fun mpv_get_property_string(
        context: Pointer,
        name: String,
    ): Pointer?
    fun mpv_error_string(error: Int): Pointer?
    fun mpv_free(data: Pointer?)
    fun mpv_terminate_destroy(context: Pointer)
}

class LibMpvBackend(
    dllPath: Path,
) : MpvBackend {
    private val library: MpvLibrary =
        Native.load(
            dllPath.toAbsolutePath().toString(),
            MpvLibrary::class.java,
        )
    private val context: Pointer =
        requireNotNull(library.mpv_create()) {
            "libmpv could not create a playback context"
        }
    private val closed = AtomicBoolean(false)

    init {
        checkCode(library.mpv_set_option_string(context, "config", "no"), "set config")
        checkCode(library.mpv_set_option_string(context, "terminal", "no"), "set terminal")
        checkCode(library.mpv_set_option_string(context, "vid", "no"), "disable video")
        checkCode(
            library.mpv_set_option_string(context, "audio-display", "no"),
            "disable audio display",
        )
        checkCode(library.mpv_initialize(context), "initialize")
    }

    @Synchronized
    override fun load(
        source: String,
        headers: Map<String, String>,
    ) {
        ensureOpen()

        val userAgent =
            headers.entries
                .firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }
                ?.value
        if (userAgent != null) {
            checkCode(
                library.mpv_set_property_string(context, "user-agent", userAgent),
                "set user-agent",
            )
        }

        val extraHeaders =
            headers.entries
                .filterNot { it.key.equals("User-Agent", ignoreCase = true) }
                .joinToString(",") { (name, value) -> "$name: $value" }

        checkCode(
            library.mpv_set_property_string(context, "http-header-fields", extraHeaders),
            "set headers",
        )
        checkCode(
            library.mpv_command_string(
                context,
                "loadfile " + quoteMpvArgument(source) + " replace",
            ),
            "load media",
        )
    }

    @Synchronized
    override fun setPaused(paused: Boolean) {
        ensureOpen()
        checkCode(
            library.mpv_set_property_string(
                context,
                "pause",
                if (paused) "yes" else "no",
            ),
            "set pause",
        )
    }

    @Synchronized
    override fun seek(seconds: Double) {
        ensureOpen()
        checkCode(
            library.mpv_command_string(
                context,
                "seek $seconds absolute+exact",
            ),
            "seek",
        )
    }

    @Synchronized
    override fun setVolume(volume: Int) {
        ensureOpen()
        checkCode(
            library.mpv_set_property_string(
                context,
                "volume",
                volume.coerceIn(0, 100).toString(),
            ),
            "set volume",
        )
    }

    @Synchronized
    override fun property(name: String): String? {
        ensureOpen()
        val pointer = library.mpv_get_property_string(context, name) ?: return null
        return try {
            pointer.getString(0, "UTF-8")
        } finally {
            library.mpv_free(pointer)
        }
    }

    @Synchronized
    override fun stop() {
        ensureOpen()
        checkCode(
            library.mpv_command_string(context, "stop"),
            "stop",
        )
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            library.mpv_terminate_destroy(context)
        }
    }

    private fun ensureOpen() {
        check(!closed.get()) { "libmpv backend is closed" }
    }

    private fun checkCode(
        code: Int,
        operation: String,
    ) {
        if (code >= 0) return

        val message =
            library.mpv_error_string(code)
                ?.getString(0, "UTF-8")
                ?.takeIf(String::isNotBlank)
                ?: "error $code"
        throw IllegalStateException("libmpv $operation failed: $message")
    }
}
