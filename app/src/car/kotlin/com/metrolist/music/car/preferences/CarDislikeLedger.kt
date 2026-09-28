package com.metrolist.music.car.preferences

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class CarDislikeRecord(
    val songId: String,
    val title: String,
    val artists: List<String>,
    val album: String? = null,
    val dislikedAtEpochMillis: Long,
    val sourceKinds: List<String> = emptyList(),
    val deletedAtEpochMillis: Long? = null,
    val lastDeletionError: String? = null,
) {
    val isDeleted: Boolean
        get() = deletedAtEpochMillis != null
}

@Serializable
data class CarDislikeLedger(
    val version: Int = CURRENT_VERSION,
    val records: List<CarDislikeRecord> = emptyList(),
) {
    init {
        require(version == CURRENT_VERSION) {
            "Unsupported Car dislike ledger version: $version"
        }
    }

    companion object {
        const val CURRENT_VERSION = 1
    }
}

data class CarDislikeSnapshot(
    val songId: String,
    val title: String,
    val artists: List<String>,
    val album: String? = null,
    val sourceKinds: Set<String> = emptySet(),
)

class CarDislikeLedgerException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

class CarDislikeLedgerStore(
    baseDir: File,
    private val json: Json =
        Json {
            encodeDefaults = true
            ignoreUnknownKeys = false
            prettyPrint = true
        },
) {
    val ledgerFile: File = baseDir.resolve("car-dislikes.json")
    val temporaryFile: File = baseDir.resolve("car-dislikes.json.part")

    fun load(): CarDislikeLedger {
        if (!ledgerFile.isFile) return CarDislikeLedger()
        return try {
            json.decodeFromString<CarDislikeLedger>(ledgerFile.readText())
        } catch (error: Exception) {
            throw CarDislikeLedgerException("Car dislike ledger is corrupt", error)
        }
    }

    fun save(ledger: CarDislikeLedger) {
        ledgerFile.parentFile?.mkdirs()
        val bytes = json.encodeToString(ledger).encodeToByteArray()
        try {
            FileOutputStream(temporaryFile).use { output ->
                output.write(bytes)
                output.flush()
                output.fd.sync()
            }
            Files.move(
                temporaryFile.toPath(),
                ledgerFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (error: Exception) {
            temporaryFile.delete()
            throw CarDislikeLedgerException("Could not persist Car dislike ledger", error)
        }
    }
}
