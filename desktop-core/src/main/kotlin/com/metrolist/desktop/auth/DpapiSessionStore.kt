package com.metrolist.desktop.auth

import com.sun.jna.platform.win32.Crypt32Util
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class DpapiSessionStore(
    private val path: Path,
    private val json: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    },
) {
    fun save(session: AuthSession) {
        path.parent?.let(Files::createDirectories)

        val plainBytes = json.encodeToString(session).encodeToByteArray()
        val encrypted = try {
            Crypt32Util.cryptProtectData(plainBytes)
        } finally {
            plainBytes.fill(0)
        }

        val temporary = path.resolveSibling(path.fileName.toString() + ".tmp")
        Files.write(temporary, encrypted)

        try {
            Files.move(
                temporary,
                path,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    fun load(): AuthSession? {
        if (!Files.exists(path)) return null

        val encrypted = Files.readAllBytes(path)
        val plainBytes = Crypt32Util.cryptUnprotectData(encrypted)
        return try {
            json.decodeFromString<AuthSession>(plainBytes.decodeToString())
        } finally {
            plainBytes.fill(0)
        }
    }

    fun clear() {
        Files.deleteIfExists(path)
    }
}
