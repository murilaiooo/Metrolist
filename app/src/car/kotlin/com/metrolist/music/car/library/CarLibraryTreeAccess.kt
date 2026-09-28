package com.metrolist.music.car.library

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

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

    fun size(relativePath: String): Long? = null

    fun invalidateCaches() = Unit
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

    override fun size(relativePath: String): Long? {
        val path = resolve(relativePath)
        if (!Files.isRegularFile(path)) return null
        return runCatching { Files.size(path) }.getOrNull()
    }

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
    private data class DocumentEntry(
        val documentId: String,
        val uri: Uri,
        val size: Long?,
    )

    private val childrenCache =
        ConcurrentHashMap<String, Map<String, DocumentEntry>>()

    override val location: CarLibraryLocation =
        CarLibraryLocation.TreeUri(treeUri.toString())

    override fun readText(relativePath: String): String? {
        val entry = resolveDocument(relativePath) ?: return null
        return resolver.openInputStream(entry.uri)
            ?.bufferedReader()
            ?.use { it.readText() }
    }

    override fun exists(relativePath: String): Boolean =
        resolveDocument(relativePath) != null

    override fun locator(relativePath: String): String =
        requireNotNull(resolveDocument(relativePath)) {
            "Car Library entry is unavailable: " + relativePath
        }.uri.toString()

    override fun size(relativePath: String): Long? =
        resolveDocument(relativePath)?.size

    override fun invalidateCaches() {
        childrenCache.clear()
    }

    private fun resolveDocument(relativePath: String): DocumentEntry? {
        val segments = relativePath.split("/").filter(String::isNotBlank)
        if (segments.isEmpty() || segments.any { it == "." || it == ".." }) return null

        var documentId =
            runCatching { DocumentsContract.getTreeDocumentId(treeUri) }
                .getOrNull()
                ?: return null
        var current =
            DocumentEntry(
                documentId = documentId,
                uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId),
                size = null,
            )

        segments.forEach { segment ->
            current = findChild(documentId, segment) ?: return null
            documentId = current.documentId
        }

        return current
    }

    private fun findChild(
        parentDocumentId: String,
        displayName: String,
    ): DocumentEntry? =
        childrenFor(parentDocumentId)[displayName]

    private fun childrenFor(parentDocumentId: String): Map<String, DocumentEntry> {
        childrenCache[parentDocumentId]?.let { return it }

        val childrenUri =
            DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri,
                parentDocumentId,
            )
        val projection =
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_SIZE,
            )
        val loaded = linkedMapOf<String, DocumentEntry>()

        resolver.query(
            childrenUri,
            projection,
            null,
            null,
            null,
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            if (idIndex < 0 || nameIndex < 0) {
                return@use
            }

            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIndex) ?: continue
                val childId = cursor.getString(idIndex) ?: continue
                val size =
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        cursor.getLong(sizeIndex)
                    } else {
                        null
                    }
                loaded[name] =
                    DocumentEntry(
                        documentId = childId,
                        uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId),
                        size = size,
                    )
            }
        }

        val immutable = loaded.toMap()
        childrenCache[parentDocumentId] = immutable
        return immutable
    }
}