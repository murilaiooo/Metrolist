package com.metrolist.desktop.player

import java.nio.file.Files
import java.nio.file.Path

fun main(args: Array<String>) {
    require(args.size == 2) { "Expected libmpv DLL path and media path" }

    val dll = Path.of(args[0])
    val media = Path.of(args[1])

    require(Files.exists(dll)) { "libmpv DLL is missing" }
    require(Files.exists(media)) { "Smoke media is missing" }

    LibMpvBackend(dll).use { backend ->
        backend.setVolume(0)
        backend.load(media.toString())
        Thread.sleep(1_500)

        val duration = backend.property("duration")?.toDoubleOrNull()
        val position = backend.property("time-pos")?.toDoubleOrNull()

        require(duration != null && duration > 1.0) { "libmpv did not report a valid duration" }
        require(position != null && position >= 0.0) { "libmpv did not report playback position" }

        backend.setPaused(true)
        backend.seek(10.0)
        Thread.sleep(250)

        val seeked = backend.property("time-pos")?.toDoubleOrNull()
        require(seeked != null && seeked >= 8.0) { "libmpv seek smoke failed" }

        backend.stop()

        println(
            "MPV_SMOKE_OK durationSeconds=" +
                String.format(java.util.Locale.ROOT, "%.2f", duration) +
                " seekSeconds=" +
                String.format(java.util.Locale.ROOT, "%.2f", seeked),
        )
    }
}
