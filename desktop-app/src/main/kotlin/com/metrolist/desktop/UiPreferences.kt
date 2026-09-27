package com.metrolist.desktop

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Properties

enum class PresentationMode {
    LIST,
    GRID,
}

class UiPreferences(
    private val path: Path,
) : AutoCloseable {
    private val properties = Properties()

    init {
        if (Files.exists(path)) {
            Files.newInputStream(path).use(properties::load)
        }
    }

    @Synchronized
    fun libraryMode(tab: LibraryTab): PresentationMode {
        val value = properties.getProperty(key(tab)) ?: return PresentationMode.LIST
        return runCatching { PresentationMode.valueOf(value) }.getOrDefault(PresentationMode.LIST)
    }

    @Synchronized
    fun setLibraryMode(
        tab: LibraryTab,
        mode: PresentationMode,
    ) {
        properties.setProperty(key(tab), mode.name)
        persist()
    }

    private fun key(tab: LibraryTab): String =
        "library." + tab.name.lowercase()

    private fun persist() {
        path.parent?.let(Files::createDirectories)
        val temp = path.resolveSibling(path.fileName.toString() + ".tmp")
        Files.newOutputStream(temp).use { output ->
            properties.store(output, "MetroList Windows UI preferences")
        }
        try {
            Files.move(
                temp,
                path,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    override fun close() = Unit
}
