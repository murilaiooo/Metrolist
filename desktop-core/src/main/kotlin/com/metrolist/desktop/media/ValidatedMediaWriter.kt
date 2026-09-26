package com.metrolist.desktop.media

import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

data class WrittenMedia(
    val path: Path,
    val bytes: Long,
    val sha256: String,
)

class ValidatedMediaWriter {
    fun write(
        input: InputStream,
        expectedLength: Long?,
        destination: Path,
    ): WrittenMedia {
        destination.parent?.let(Files::createDirectories)
        val temporary = destination.resolveSibling(destination.fileName.toString() + ".part")
        Files.deleteIfExists(temporary)

        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L

        try {
            Files.newOutputStream(temporary).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue

                    output.write(buffer, 0, count)
                    digest.update(buffer, 0, count)
                    total += count
                }
            }

            if (expectedLength != null) {
                check(total == expectedLength) {
                    "Media length mismatch: expected=$expectedLength actual=$total"
                }
            }

            try {
                Files.move(
                    temporary,
                    destination,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING)
            }

            return WrittenMedia(
                path = destination,
                bytes = total,
                sha256 = digest.digest().joinToString("") { "%02x".format(it) },
            )
        } catch (error: Exception) {
            Files.deleteIfExists(temporary)
            throw error
        }
    }
}
