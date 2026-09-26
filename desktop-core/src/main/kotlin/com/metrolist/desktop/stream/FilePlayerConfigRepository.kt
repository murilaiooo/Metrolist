package com.metrolist.desktop.stream

import com.metrolist.innertubex.cipher.PlayerConfigRepository
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Properties

class FilePlayerConfigRepository(
    private val path: Path,
) : PlayerConfigRepository {
    override val enabled: Boolean = true
    override val sourceUrl: String = PLAYER_CONFIG_URL
    override val defaultSourceUrl: String = PLAYER_CONFIG_URL

    override var cachedJson: String
        get() = read("json")
        set(value) = write("json", value)

    override var cachedAtMs: Long
        get() = read("cachedAtMs").toLongOrNull() ?: 0L
        set(value) = write("cachedAtMs", value.toString())

    override var cachedSourceUrl: String
        get() = read("sourceUrl")
        set(value) = write("sourceUrl", value)

    override var cachedEtag: String
        get() = read("etag")
        set(value) = write("etag", value)

    @Synchronized
    private fun read(key: String): String {
        if (!Files.exists(path)) return ""
        return Files.newInputStream(path).use { input ->
            Properties().apply { load(input) }.getProperty(key).orEmpty()
        }
    }

    @Synchronized
    private fun write(key: String, value: String) {
        path.parent?.let(Files::createDirectories)

        val properties =
            if (Files.exists(path)) {
                Files.newInputStream(path).use { input -> Properties().apply { load(input) } }
            } else {
                Properties()
            }

        properties.setProperty(key, value)

        val temporary = path.resolveSibling(path.fileName.toString() + ".tmp")
        Files.newOutputStream(temporary).use { output -> properties.store(output, null) }
        runCatching {
            Files.move(
                temporary,
                path,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.getOrElse {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private companion object {
        const val PLAYER_CONFIG_URL =
            "https://raw.githubusercontent.com/ZemerTeam/zemer-cipher/master/library/src/main/assets/player_configs.json"
    }
}
