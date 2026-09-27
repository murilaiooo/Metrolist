package com.metrolist.desktop.portable.carlibrary

import com.metrolist.desktop.portable.PortableItem
import java.awt.image.BufferedImage
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import javax.imageio.ImageIO
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class CarLibraryExportSelection(
    val items: List<PortableItem>,
    val playlists: List<CarLibraryPlaylist> = emptyList(),
)

enum class CarLibraryExportPhase {
    PREPARING,
    COPYING,
    FINALIZING,
    COMPLETE,
}

data class CarLibraryExportProgress(
    val phase: CarLibraryExportPhase,
    val completedTracks: Int,
    val totalTracks: Int,
    val copiedFiles: Int = 0,
    val reusedFiles: Int = 0,
    val updatedFiles: Int = 0,
    val failedFiles: Int = 0,
    val copiedBytes: Long = 0L,
    val totalBytes: Long = 0L,
)

data class CarLibraryExportResult(
    val manifest: CarLibraryManifest,
    val copiedFiles: Int,
    val reusedFiles: Int,
    val updatedFiles: Int,
    val copiedBytes: Long,
)

class CarLibraryExporter(
    private val producerVersion: String,
    private val newIdentity: () -> String = { UUID.randomUUID().toString() },
    private val now: () -> String = { Instant.now().toString() },
    private val beforeManifestPromotion: () -> Unit = {},
    private val json: Json =
        Json {
            encodeDefaults = true
            explicitNulls = true
            prettyPrint = true
            ignoreUnknownKeys = false
        },
) {
    fun exportCarLibrary(
        destination: Path,
        selection: CarLibraryExportSelection,
        onProgress: (CarLibraryExportProgress) -> Unit = {},
    ): CarLibraryExportResult {
        require(selection.items.isNotEmpty()) { "At least one offline item is required" }

        val root = destination.toAbsolutePath().normalize()
        if (Files.exists(root)) {
            require(Files.isDirectory(root)) { "Car Library destination must be a directory" }
        }
        Files.createDirectories(root)
        Files.createDirectories(root.resolve(MEDIA_ROOT))
        Files.createDirectories(root.resolve(ARTWORK_ROOT))

        val previous = readExistingManifest(root)
        val libraryId = previous?.libraryId ?: nextIdentity("library ID")
        val revision = nextIdentity("library revision")
        require(revision != previous?.revision) { "New library revision must differ from the current revision" }

        val timestamp = now()
        val totalBytes =
            selection.items.sumOf { item ->
                Files.size(item.mediaPath) +
                    (item.artworkPath?.takeIf(Files::isRegularFile)?.let(Files::size) ?: 0L)
            }

        report(
            onProgress,
            CarLibraryExportProgress(
                phase = CarLibraryExportPhase.PREPARING,
                completedTracks = 0,
                totalTracks = selection.items.size,
                totalBytes = totalBytes,
            ),
        )

        var copiedFiles = 0
        var reusedFiles = 0
        var updatedFiles = 0
        var copiedBytes = 0L
        var completedTracks = 0

        val preparedSongs = mutableListOf<CarLibrarySong>()
        selection.items.forEach { item ->
            val mediaSource = item.mediaPath.toAbsolutePath().normalize()
            require(Files.isRegularFile(mediaSource)) {
                "Offline media file is missing for ${item.song.id}"
            }
            val mediaSize = Files.size(mediaSource)
            item.resolved.contentLength?.let { expected ->
                require(mediaSize == expected) {
                    "Offline media length mismatch for ${item.song.id}: expected=$expected actual=$mediaSize"
                }
            }

            val mediaSha = sha256(mediaSource)
            val mediaRelative = mediaRelativePath(item, mediaSource)
            val mediaCopy =
                copyOrReuse(
                    source = mediaSource,
                    target = root.resolve(mediaRelative),
                    expectedSize = mediaSize,
                    expectedSha256 = mediaSha,
                    revision = revision,
                )
            copiedFiles += if (mediaCopy.copied) 1 else 0
            reusedFiles += if (mediaCopy.reused) 1 else 0
            updatedFiles += if (mediaCopy.updated) 1 else 0
            copiedBytes += mediaCopy.bytesCopied

            val artwork = prepareArtwork(root, item, revision)
            if (artwork != null) {
                copiedFiles += if (artwork.copy.copied) 1 else 0
                reusedFiles += if (artwork.copy.reused) 1 else 0
                updatedFiles += if (artwork.copy.updated) 1 else 0
                copiedBytes += artwork.copy.bytesCopied
            }

            preparedSongs +=
                CarLibrarySong(
                    id = item.song.id,
                    title = item.song.title,
                    artists =
                        item.song.artists.map { artist ->
                            CarLibraryArtist(
                                id = artist.id,
                                name = artist.name,
                            )
                        },
                    album =
                        item.song.album?.let { album ->
                            CarLibraryAlbum(
                                id = album.id,
                                name = album.name,
                            )
                        },
                    duration = item.song.duration,
                    explicit = item.song.explicit,
                    isVideo = item.song.isVideoSong,
                    isEpisode = item.song.isEpisode,
                    mediaPath = mediaRelative,
                    artworkPath = artwork?.relativePath,
                    mimeType = item.resolved.mimeType,
                    codecs = item.resolved.codecs,
                    bitrate = item.resolved.bitrate,
                    sampleRate = item.resolved.sampleRate,
                    fileSize = mediaSize,
                    sha256 = mediaSha,
                    artworkFileSize = artwork?.size,
                    artworkSha256 = artwork?.sha256,
                    artworkWidth = artwork?.width,
                    artworkHeight = artwork?.height,
                )

            completedTracks++
            report(
                onProgress,
                CarLibraryExportProgress(
                    phase = CarLibraryExportPhase.COPYING,
                    completedTracks = completedTracks,
                    totalTracks = selection.items.size,
                    copiedFiles = copiedFiles,
                    reusedFiles = reusedFiles,
                    updatedFiles = updatedFiles,
                    copiedBytes = copiedBytes,
                    totalBytes = totalBytes,
                ),
            )
        }

        val manifest =
            CarLibraryManifest(
                libraryId = libraryId,
                revision = revision,
                createdAt = previous?.createdAt ?: timestamp,
                updatedAt = timestamp,
                producer =
                    CarLibraryProducer(
                        product = PRODUCER_PRODUCT,
                        version = producerVersion,
                    ),
                songs = preparedSongs,
                playlists = selection.playlists,
            )

        val validation = CarLibraryValidator(json).validate(manifest)
        require(validation.valid) {
            "Generated Car Library manifest is invalid: ${validation.errors.joinToString("; ")}"
        }

        report(
            onProgress,
            CarLibraryExportProgress(
                phase = CarLibraryExportPhase.FINALIZING,
                completedTracks = completedTracks,
                totalTracks = selection.items.size,
                copiedFiles = copiedFiles,
                reusedFiles = reusedFiles,
                updatedFiles = updatedFiles,
                copiedBytes = copiedBytes,
                totalBytes = totalBytes,
            ),
        )

        publishManifest(root, manifest, revision)

        report(
            onProgress,
            CarLibraryExportProgress(
                phase = CarLibraryExportPhase.COMPLETE,
                completedTracks = completedTracks,
                totalTracks = selection.items.size,
                copiedFiles = copiedFiles,
                reusedFiles = reusedFiles,
                updatedFiles = updatedFiles,
                copiedBytes = copiedBytes,
                totalBytes = totalBytes,
            ),
        )

        return CarLibraryExportResult(
            manifest = manifest,
            copiedFiles = copiedFiles,
            reusedFiles = reusedFiles,
            updatedFiles = updatedFiles,
            copiedBytes = copiedBytes,
        )
    }

    private fun readExistingManifest(root: Path): CarLibraryManifest? {
        val manifestPath = root.resolve(MANIFEST_NAME)
        if (!Files.isRegularFile(manifestPath)) return null

        val parsed =
            runCatching {
                json.decodeFromString<CarLibraryManifest>(Files.readString(manifestPath))
            }.getOrElse { error ->
                throw IllegalStateException("Existing Car Library manifest cannot be read", error)
            }
        val validation = CarLibraryValidator(json).validate(parsed)
        require(validation.valid) {
            "Existing Car Library manifest is invalid: ${validation.errors.joinToString("; ")}"
        }
        return parsed
    }

    private fun prepareArtwork(
        root: Path,
        item: PortableItem,
        revision: String,
    ): PreparedArtwork? {
        val source =
            item.artworkPath
                ?.takeIf(Files::isRegularFile)
                ?.takeIf { runCatching { Files.size(it) > 0L }.getOrDefault(false) }
                ?: return null

        val size = Files.size(source)
        val digest = sha256(source)
        val stableKey = item.song.thumbnail.takeIf(String::isNotBlank) ?: item.song.id
        val relativePath = "$ARTWORK_ROOT/${sha256(stableKey.encodeToByteArray()).take(32)}.img"
        val copy =
            copyOrReuse(
                source = source,
                target = root.resolve(relativePath),
                expectedSize = size,
                expectedSha256 = digest,
                revision = revision,
            )
        val dimensions = readImageDimensions(source)
        return PreparedArtwork(
            relativePath = relativePath,
            size = size,
            sha256 = digest,
            width = dimensions?.first,
            height = dimensions?.second,
            copy = copy,
        )
    }

    private fun mediaRelativePath(
        item: PortableItem,
        source: Path,
    ): String {
        val idHash = sha256(item.song.id.encodeToByteArray()).take(32)
        val extension =
            source.fileName
                ?.toString()
                ?.substringAfterLast('.', missingDelimiterValue = "")
                ?.lowercase()
                ?.takeIf { it.matches(SAFE_EXTENSION) }
                ?: "bin"
        return "$MEDIA_ROOT/$idHash.$extension"
    }

    private fun copyOrReuse(
        source: Path,
        target: Path,
        expectedSize: Long,
        expectedSha256: String,
        revision: String,
    ): CopyOutcome {
        Files.createDirectories(requireNotNull(target.parent))
        val existed = Files.isRegularFile(target)
        if (
            existed &&
            Files.size(target) == expectedSize &&
            sha256(target) == expectedSha256
        ) {
            return CopyOutcome(reused = true)
        }

        val temporary =
            target.resolveSibling(
                target.fileName.toString() + ".part-" + safeToken(revision),
            )
        Files.deleteIfExists(temporary)
        try {
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING)
            FileChannel.open(temporary, StandardOpenOption.WRITE).use { channel ->
                channel.force(true)
            }
            require(Files.size(temporary) == expectedSize) {
                "Copied file size mismatch: ${target.fileName}"
            }
            require(sha256(temporary) == expectedSha256) {
                "Copied file hash mismatch: ${target.fileName}"
            }
            atomicReplace(temporary, target)
        } finally {
            Files.deleteIfExists(temporary)
        }

        return CopyOutcome(
            copied = true,
            updated = existed,
            bytesCopied = expectedSize,
        )
    }

    private fun publishManifest(
        root: Path,
        manifest: CarLibraryManifest,
        revision: String,
    ) {
        val target = root.resolve(MANIFEST_NAME)
        val temporary =
            root.resolve("$MANIFEST_NAME.part-${safeToken(revision)}")
        Files.deleteIfExists(temporary)
        try {
            val bytes = json.encodeToString(manifest).encodeToByteArray()
            Files.write(
                temporary,
                bytes,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE,
            )
            FileChannel.open(temporary, StandardOpenOption.WRITE).use { channel ->
                channel.force(true)
            }
            beforeManifestPromotion()
            atomicReplace(temporary, target)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun atomicReplace(
        source: Path,
        target: Path,
    ) {
        try {
            Files.move(
                source,
                target,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun nextIdentity(label: String): String =
        newIdentity().trim().also {
            require(it.isNotEmpty()) { "$label cannot be blank" }
        }

    private fun safeToken(value: String): String =
        sha256(value.encodeToByteArray()).take(16)

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHex()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }

    private fun readImageDimensions(path: Path): Pair<Int, Int>? =
        runCatching {
            val image: BufferedImage = ImageIO.read(path.toFile()) ?: return@runCatching null
            image.width to image.height
        }.getOrNull()

    private fun report(
        listener: (CarLibraryExportProgress) -> Unit,
        progress: CarLibraryExportProgress,
    ) {
        runCatching { listener(progress) }
    }

    private data class PreparedArtwork(
        val relativePath: String,
        val size: Long,
        val sha256: String,
        val width: Int?,
        val height: Int?,
        val copy: CopyOutcome,
    )

    private data class CopyOutcome(
        val copied: Boolean = false,
        val reused: Boolean = false,
        val updated: Boolean = false,
        val bytesCopied: Long = 0L,
    )

    private companion object {
        const val MANIFEST_NAME = "library.json"
        const val MEDIA_ROOT = "media"
        const val ARTWORK_ROOT = "artwork"
        const val PRODUCER_PRODUCT = "MetroList Windows"
        val SAFE_EXTENSION = Regex("^[a-z0-9]{1,10}$")
    }
}
