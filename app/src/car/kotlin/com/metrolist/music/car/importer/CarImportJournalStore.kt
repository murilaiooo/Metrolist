package com.metrolist.music.car.importer

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class CarImportJournalStore(
    baseDir: File,
    private val json: Json =
        Json {
            encodeDefaults = true
            ignoreUnknownKeys = false
        },
) {
    val journalFile: File = baseDir.resolve("car-import-journal.json")
    val temporaryFile: File = baseDir.resolve("car-import-journal.json.part")

    fun load(): CarImportJournal? {
        if (!journalFile.isFile) return null
        return try {
            json.decodeFromString<CarImportJournal>(journalFile.readText())
        } catch (error: Exception) {
            throw CarImportJournalException("Car import journal is corrupt", error)
        }
    }
    fun save(journal: CarImportJournal) {
        journalFile.parentFile?.mkdirs()
        val bytes = json.encodeToString(journal).encodeToByteArray()
        try {
            FileOutputStream(temporaryFile).use { output ->
                output.write(bytes)
                output.flush()
                output.fd.sync()
            }
            Files.move(
                temporaryFile.toPath(),
                journalFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (error: Exception) {
            temporaryFile.delete()
            throw CarImportJournalException("Could not persist Car import journal", error)
        }
    }

    fun clear() {
        temporaryFile.delete()
        if (journalFile.exists() && !journalFile.delete()) {
            throw CarImportJournalException("Could not clear Car import journal")
        }
    }
}
