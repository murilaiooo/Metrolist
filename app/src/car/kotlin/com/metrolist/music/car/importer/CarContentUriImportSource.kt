package com.metrolist.music.car.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.FileNotFoundException
import java.io.InputStream

class CarContentUriImportSource(
    private val context: Context,
    private val uri: Uri,
) : CarImportSource {
    override val identity: String = uri.toString()

    override val sizeBytes: Long?
        get() =
            runCatching {
                context.contentResolver
                    .query(
                        uri,
                        arrayOf(OpenableColumns.SIZE),
                        null,
                        null,
                        null,
                    )?.use { cursor ->
                        val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (index < 0 || !cursor.moveToFirst() || cursor.isNull(index)) {
                            null
                        } else {
                            cursor.getLong(index).takeIf { it >= 0L }
                        }
                    }
            }.getOrNull()

    override fun open(): InputStream =
        context.contentResolver.openInputStream(uri)
            ?: throw FileNotFoundException("Persisted import source is unavailable: $uri")
}
