package com.metrolist.music.car.importer

import android.content.Context
import android.net.Uri
import java.io.FileNotFoundException
import java.io.InputStream

class CarContentUriImportSource(
    private val context: Context,
    private val uri: Uri,
) : CarImportSource {
    override val identity: String = uri.toString()

    override fun open(): InputStream =
        context.contentResolver.openInputStream(uri)
            ?: throw FileNotFoundException("Persisted import source is unavailable: $uri")
}
