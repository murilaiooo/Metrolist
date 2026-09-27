package com.metrolist.music.car.importer

import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class CarArtworkStore(
    baseDir: File,
) {
    private val rootDir = baseDir.resolve("car-artwork")
    fun fileFor(songId: String): File {
        require(SAFE_ID.matches(songId)) { "Unsafe song ID for artwork path" }
        return rootDir.resolve("$songId.img")
    }

    fun write(
        songId: String,
        input: InputStream,
    ): File {
        rootDir.mkdirs()
        val target = fileFor(songId)
        val temporary = rootDir.resolve("$songId.img.part")
        try {
            temporary.outputStream().buffered().use { output ->
                input.copyTo(output)
            }
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
            return target
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    fun delete(songId: String): Boolean =
        !fileFor(songId).exists() || fileFor(songId).delete()

    private companion object {
        val SAFE_ID = Regex("[A-Za-z0-9_-]+")
    }
}
