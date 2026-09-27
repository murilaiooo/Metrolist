package com.metrolist.music.car.library

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.nio.file.Files
import java.nio.file.Path

sealed interface CarLibraryLocation {
    data class FilePath(
        val path: String,
    ) : CarLibraryLocation

    data class TreeUri(
        val uri: String,
    ) : CarLibraryLocation
}

interface CarLibraryTreeAccess {
    val location: CarLibraryLocation

    fun readText(relativePath: String): String?

    fun exists(relativePath: String): Boolean

    fun locator(relativePath: String): String
}

fun interface CarLibraryTreeAccessFactory {
    fun open(location: CarLibraryLocation): CarLibraryTreeAccess?
}

interface CarLibraryLocationStore {
    fun load(): CarLibraryLocation?

    fun save(location: CarLibraryLocation)
}

class SharedPreferencesCarLibraryLocationStore(
    context: Context,
) : CarLibraryLocationStore {
    private val preferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): CarLibraryLocation? {
        val value = preferences.getString(KEY_VALUE, null) ?: return null
        return when (preferences.getString(KEY_TYPE, null)) {
            TYPE_FILE -> CarLibraryLocation.FilePath(value)
            TYPE_TREE_URI -> CarLibraryLocation.TreeUri(value)
            else -> null
        }
    }

    override fun save(location: CarLibraryLocation) {
        val (type, value) =
            when (location) {
                is CarLibraryLocation.FilePath -> TYPE_FILE to location.path
                is CarLibraryLocation.TreeUri -> TYPE_TREE_URI to location.uri
            }

        preferences.edit()
            .putString(KEY_TYPE, type)
            .putString(KEY_VALUE, value)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "car-library"
        const val KEY_TYPE = "location-type"
        const val KEY_VALUE = "location-value"
        const val TYPE_FILE = "file"
        const val TYPE_TREE_URI = "tree-uri"
    }
}

class FileCarLibraryTreeAccess(
    root: Path,
) : CarLibraryTreeAccess {
    private val normalizedRoot = root.toAbsolutePath().normalize()

    override val location: CarLibraryLocation =
        CarLibraryLocation.FilePath(normalizedRoot.toString())

    override fun readText(relativePath: String): String? {
        val path = resolve(relativePath)
        if (!Files.isRegularFile(path)) return null
        return Files.readString(path)
    }

    override fun exists(relativePath: String): Boolean =
        Files.isRegularFile(resolve(relativePath))

    override fun locator(relativePath: String): String =
        resolve(relativePath).toUri().toString()

    private fun resolve(relativePath: String): Path {
        require(relativePath.isNotBlank()) { "Relative Car Library path cannot be blank" }

        val resolved =
            relativePath
                .split("/")
                .fold(normalizedRoot) { current, segment -> current.resolve(segment) }
                .normalize()

        require(resolved.startsWith(normalizedRoot)) {
            "Car Library path escapes its root"
        }
        return resolved
    }
}

class AndroidCarLibraryTreeAccessFactory(
    private val context: Context,
) : CarLibraryTreeAccessFactory {
    override fun open(location: CarLibraryLocation): CarLibraryTreeAccess? =
        runCatching {
            when (location) {
                is CarLibraryLocation.FilePath ->
                    FileCarLibraryTreeAccess(Path.of(location.path))
                is CarLibraryLocation.TreeUri ->
                    ContentTreeCarLibraryTreeAccess(
                        resolver = context.contentResolver,
                        treeUri = Uri.parse(location.uri),
                    )
            }
        }.getOrNull()
}

class ContentTreeCarLibraryTreeAccess(
    private val resolver: ContentResolver,
    private val treeUri: Uri,
) : CarLibraryTreeAccess {
    override val location: CarLibraryLocation =
        CarLibraryLocation.TreeUri(treeUri.toString())

    override fun readText(relativePath: String): String? {
        val uri = resolveDocument(relativePath) ?: return null
        return resolver.openInputStream(uri)
            ?.bufferedReader()
            ?.use { it.readText() }
    }

    override fun exists(relativePath: String): Boolean =
        resolveDocument(relativePath) != null

    override fun locator(relativePath: String): String =
        requireNotNull(resolveDocument(relativePath)) {
            "Car Library entry is unavailable: " + relativePath
        }.toString()

    private fun resolveDocument(relativePath: String): Uri? {
        val segments = relativePath.split("/").filter(String::isNotBlank)
        if (segments.isEmpty() || segments.any { it == "." || it == ".." }) return null

        var documentId =
            runCatching { DocumentsContract.getTreeDocumentId(treeUri) }
                .getOrNull()
                ?: return null

        var documentUri =
            DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)

        segments.forEach { segment ->
            val child =
                findChild(
                    parentDocumentId = documentId,
                    displayName = segment,
                )
                    ?: return null
            documentId = child.first
            documentUri = child.second
        }

        return documentUri
    }

    private fun findChild(
        parentDocumentId: String,
        displayName: String,
    ): Pair<String, Uri>? {
        val childrenUri =
            DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri,
                parentDocumentId,
            )
        val projection =
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            )

        resolver.query(
            childrenUri,
            projection,
            null,
            null,
            null,
        )?.use { cursor ->
            val idIndex =
                cursor.getColumnIndex(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                )
            val nameIndex =
                cursor.getColumnIndex(
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                )

            while (cursor.moveToNext()) {
                if (idIndex < 0 || nameIndex < 0) return null
                if (cursor.getString(nameIndex) != displayName) continue

                val childId = cursor.getString(idIndex)
                return childId to
                    DocumentsContract.buildDocumentUriUsingTree(
                        treeUri,
                        childId,
                    )
            }
        }
        return null
    }
}