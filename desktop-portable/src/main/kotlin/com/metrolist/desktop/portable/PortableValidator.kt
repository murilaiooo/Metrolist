package com.metrolist.desktop.portable

import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

data class ValidationResult(
    val valid: Boolean,
    val errors: List<String>,
)

class PortableValidator(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = true
    },
) {
    fun validate(path: Path): ValidationResult {
        val errors = mutableListOf<String>()

        runCatching {
            ZipFile(path.toFile()).use { zip ->
                val manifestEntry =
                    zip.getEntry("manifest.json")
                        ?: error("manifest.json missing")
                val manifest =
                    zip.getInputStream(manifestEntry).bufferedReader().use {
                        json.decodeFromString<PortableManifest>(it.readText())
                    }

                if (manifest.backupFormatVersion != 1) {
                    errors += "Unsupported format version"
                }

                manifest.files.forEach { file ->
                    val entry = zip.getEntry(file.relativePath)
                    if (entry == null) {
                        errors += "Missing entry: ${file.relativePath}"
                    } else {
                        val digest = zip.getInputStream(entry).use(::sha256)
                        if (digest != file.sha256) {
                            errors += "Hash mismatch: ${file.relativePath}"
                        }
                    }
                }
                manifest.exportedCacheResources.forEach { resource ->
                    var position = 0L
                    resource.spans.sortedBy(PortableSpan::position).forEach { span ->
                        if (span.position != position) {
                            errors += "Span gap for ${resource.songId}"
                        }
                        position += span.length
                    }
                    if (position != resource.contentLength) {
                        errors += "Span length mismatch for ${resource.songId}"
                    }
                }
            }
        }.onFailure { error ->
            errors += "Archive validation failed: ${error::class.simpleName}"
        }

        return ValidationResult(errors.isEmpty(), errors)
    }
    private fun sha256(input: java.io.InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count > 0) digest.update(buffer, 0, count)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
