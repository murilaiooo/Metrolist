package com.metrolist.music.car.preferences

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object CarDislikeExporter {
    private val json =
        Json {
            encodeDefaults = true
            prettyPrint = true
        }

    fun renderText(records: List<CarDislikeRecord>): String =
        records.joinToString(separator = "\n") { record ->
            val artists = record.artists.joinToString(", ").ifBlank { "Unknown artist" }
            "$artists — ${record.title}"
        } + if (records.isEmpty()) "" else "\n"

    fun renderJson(records: List<CarDislikeRecord>): String =
        json.encodeToString(records)

    fun createShareFiles(
        context: Context,
        records: List<CarDislikeRecord>,
    ): List<File> {
        val dir = context.cacheDir.resolve("disliked-export").apply { mkdirs() }
        val textFile = dir.resolve("Disliked.txt")
        val jsonFile = dir.resolve("Disliked.json")
        textFile.writeText(renderText(records), Charsets.UTF_8)
        jsonFile.writeText(renderJson(records), Charsets.UTF_8)
        return listOf(textFile, jsonFile)
    }

    fun createShareIntent(
        context: Context,
        records: List<CarDislikeRecord>,
    ): Intent {
        val uris =
            ArrayList<Uri>(
                createShareFiles(context, records).map { file ->
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.FileProvider",
                        file,
                    )
                },
            )
        return Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_SUBJECT, "MetroList Disliked")
        }
    }
}
