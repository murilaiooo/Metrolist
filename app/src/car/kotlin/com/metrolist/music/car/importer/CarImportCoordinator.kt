package com.metrolist.music.car.importer

import java.io.FilterInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

data class CarImportPreflight(
    val backup: ValidatedCarBackup,
    val archiveFingerprint: String,
    val requiredDestinationBytes: Long,
)

data class CarImportProgress(
    val phase: CarImportPhase,
    val completedSongs: Int,
    val totalSongs: Int,
    val bytesProcessed: Long,
    val totalBytes: Long,
)

sealed interface CarImportResult {
    data class Success(
        val importedSongs: Int,
        val totalSongs: Int,
    ) : CarImportResult

    data class Failure(
        val phase: CarImportPhase,
        val message: String,
    ) : CarImportResult
    data class Discarded(
        val preservedCompleted: Int,
        val cleanedIncomplete: Int,
    ) : CarImportResult
}

class CarImportCoordinator(
    private val validator: CarPortableArchiveValidator,
    private val journalStore: CarImportJournalStore,
    private val mediaTarget: CarMediaImportPort,
    private val roomTarget: CarRoomImportPort,
    private val artworkTarget: CarArtworkImportPort,
    private val inspector: CarImportInspectorPort,
    private val availableDestinationBytes: () -> Long,
) {
    fun validate(source: CarImportSource): CarImportPreflight =
        validateInternal(source, checkSpace = true)

    suspend fun runImport(
        source: CarImportSource,
        resume: Boolean,
        onProgress: (CarImportProgress) -> Unit = {},
    ): CarImportResult {
        val preflight =
            try {
                validateInternal(source, checkSpace = !resume)
            } catch (error: Exception) {
                return CarImportResult.Failure(
                    CarImportPhase.VALIDATING,
                    error.message ?: "Could not validate portable backup",
                )
            }
        var journal =
            try {
                prepareJournal(source, preflight, resume)
            } catch (error: Exception) {
                return CarImportResult.Failure(
                    CarImportPhase.VALIDATING,
                    error.message ?: "Could not resume portable import",
                )
            }

        if (resume) {
            val remaining = remainingRequiredBytes(preflight.backup)
            if (availableDestinationBytes() < remaining) {
                return CarImportResult.Failure(
                    CarImportPhase.VALIDATING,
                    "Insufficient destination space to resume import",
                )
            }
        }

        return try {
            journal = journal.copy(phase = CarImportPhase.IMPORTING)
            journalStore.save(journal)
            journal =
                streamPayload(
                    source = source,
                    backup = preflight.backup,
                    journal = journal,
                    totalBytes = preflight.requiredDestinationBytes,
                    onProgress = onProgress,
                )

            journal = journal.copy(phase = CarImportPhase.COMMITTING)
            journalStore.save(journal)
            journal =
                commitMetadata(
                    backup = preflight.backup,
                    journal = journal,
                    totalBytes = preflight.requiredDestinationBytes,
                    onProgress = onProgress,
                )
            journal = journal.copy(phase = CarImportPhase.COMPLETE)
            journalStore.save(journal)
            report(
                onProgress,
                CarImportProgress(
                    phase = CarImportPhase.COMPLETE,
                    completedSongs = journal.songs.values.count(CarSongImportRecord::completed),
                    totalSongs = preflight.backup.songs.size,
                    bytesProcessed = preflight.requiredDestinationBytes,
                    totalBytes = preflight.requiredDestinationBytes,
                ),
            )
            CarImportResult.Success(
                importedSongs = journal.songs.values.count(CarSongImportRecord::completed),
                totalSongs = preflight.backup.songs.size,
            )
        } catch (error: Exception) {
            CarImportResult.Failure(
                phase = journal.phase,
                message = error.message ?: "Portable import failed",
            )
        }
    }

    suspend fun discardIncomplete(): CarImportResult {
        val journal =
            try {
                journalStore.load()
            } catch (error: Exception) {
                return CarImportResult.Failure(
                    CarImportPhase.VALIDATING,
                    error.message ?: "Could not read import journal",
                )
            } ?: return CarImportResult.Discarded(0, 0)

        if (journal.phase == CarImportPhase.COMPLETE) {
            return CarImportResult.Discarded(
                preservedCompleted = journal.songs.size,
                cleanedIncomplete = 0,
            )
        }
        var preserved = 0
        var cleaned = 0
        return try {
            journal.songs.values.forEach { record ->
                val state =
                    inspector.inspect(
                        record.songId,
                        record.contentLength,
                        record.artworkExpected,
                    )
                if (state.fullyComplete) {
                    preserved++
                    return@forEach
                }

                if (!record.initialCachePresent && state.cachePresent) {
                    mediaTarget.removeResource(record.songId)
                }
                if (!record.initialDownloadPresent && state.downloadPresent) {
                    mediaTarget.removeDownload(record.songId)
                }
                if (
                    record.artworkExpected &&
                    !record.initialArtworkPresent &&
                    state.artworkPresent
                ) {
                    artworkTarget.delete(record.songId)
                }

                if (!record.initialRoomPresent && state.roomPresent) {
                    roomTarget.removeSongBundle(record.songId)
                } else if (
                    record.initialRoomPresent &&
                    state.roomPresent &&
                    (
                        state.roomDownloaded != record.initialRoomDownloaded ||
                            state.roomDateDownload != record.initialRoomDateDownload
                    )
                ) {
                    roomTarget.restoreDownloadState(
                        record.songId,
                        record.initialRoomDownloaded,
                        record.initialRoomDateDownload,
                    )
                }
                cleaned++
            }
            journalStore.clear()
            CarImportResult.Discarded(preserved, cleaned)
        } catch (error: Exception) {
            CarImportResult.Failure(
                journal.phase,
                error.message ?: "Could not discard incomplete import",
            )
        }
    }
    private fun validateInternal(
        source: CarImportSource,
        checkSpace: Boolean,
    ): CarImportPreflight {
        val backup =
            source.open().use { input ->
                validator.validate(
                    input = input,
                    availableDestinationBytes = Long.MAX_VALUE,
                )
            }
        val required = remainingRequiredBytes(backup)
        if (checkSpace && availableDestinationBytes() < required) {
            throw CarPortableValidationException(
                listOf("Insufficient destination space"),
            )
        }
        return CarImportPreflight(
            backup = backup,
            archiveFingerprint = fingerprint(backup.manifest),
            requiredDestinationBytes = required,
        )
    }

    private fun prepareJournal(
        source: CarImportSource,
        preflight: CarImportPreflight,
        resume: Boolean,
    ): CarImportJournal {
        val existing = journalStore.load()
        if (resume) {
            check(existing != null) { "No interrupted import is available to resume" }
            check(existing.phase != CarImportPhase.COMPLETE) {
                "The previous import is already complete"
            }
            check(existing.sourceIdentity == source.identity) {
                "Persisted import source does not match the interrupted import"
            }
            check(existing.archiveFingerprint == preflight.archiveFingerprint) {
                "Portable backup changed since the interrupted import"
            }
            return existing
        }
        if (existing != null && existing.phase != CarImportPhase.COMPLETE) {
            error("An interrupted import already exists; resume or discard it first")
        }
        return CarImportJournal(
            sourceIdentity = source.identity,
            archiveFingerprint = preflight.archiveFingerprint,
            phase = CarImportPhase.VALIDATING,
        )
    }

    private fun remainingRequiredBytes(backup: ValidatedCarBackup): Long {
        val resourcesById = backup.resources.associateBy(CarPortableResource::songId)
        val filesByPath = backup.manifest.files.associateBy(CarPortableFile::relativePath)
        var required = 0L
        backup.songs.forEach { song ->
            val resource = resourcesById.getValue(song.id)
            val artworkExpected = song.thumbnailArtworkPath != null
            val state = inspector.inspect(song.id, resource.contentLength, artworkExpected)
            if (!state.cacheComplete) {
                required = Math.addExact(required, resource.contentLength)
            }
            val artworkPath = song.thumbnailArtworkPath
            if (artworkPath != null && !state.artworkPresent) {
                required = Math.addExact(
                    required,
                    filesByPath.getValue(artworkPath).size,
                )
            }
        }
        return required
    }

    private fun streamPayload(
        source: CarImportSource,
        backup: ValidatedCarBackup,
        journal: CarImportJournal,
        totalBytes: Long,
        onProgress: (CarImportProgress) -> Unit,
    ): CarImportJournal {
        var current = initializeJournal(backup, journal)
        val spanOwners =
            backup.resources
                .flatMap { resource -> resource.spans.map { it.relativePath to (resource to it) } }
                .toMap()
        val remainingSpans =
            backup.resources.associate { it.songId to it.spans.size }.toMutableMap()
        val prepared = mutableSetOf<String>()
        val artworkOwners =
            backup.songs
                .mapNotNull { song -> song.thumbnailArtworkPath?.let { it to song.id } }
                .groupBy({ it.first }, { it.second })
        val filesByPath = backup.manifest.files.associateBy(CarPortableFile::relativePath)
        var processedBytes = 0L

        source.open().use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val spanOwner = spanOwners[entry.name]
                    val artworkSongIds = artworkOwners[entry.name]
                    if (spanOwner != null) {
                        val (resource, span) = spanOwner
                        val record = current.songs.getValue(resource.songId)
                        if (!record.cacheReady && resource.songId !in prepared) {
                            if (!mediaTarget.prepareResource(resource)) {
                                current = updateRecord(current, resource.songId) {
                                    it.copy(cacheReady = true)
                                }
                            }
                            prepared += resource.songId
                        }
                        if (!current.songs.getValue(resource.songId).cacheReady) {
                            mediaTarget.writeSpan(resource, span, nonClosing(zip))
                            processedBytes = Math.addExact(processedBytes, span.length)
                            val left = remainingSpans.getValue(resource.songId) - 1
                            remainingSpans[resource.songId] = left
                            if (left == 0) {
                                mediaTarget.finishResource(resource)
                                current = updateRecord(current, resource.songId) {
                                    it.copy(cacheReady = true)
                                }
                            }
                        }
                    } else if (artworkSongIds != null) {
                        val missingCopies =
                            artworkSongIds.count { !current.songs.getValue(it).artworkReady }
                        current = importArtworkEntry(
                            zip = zip,
                            songIds = artworkSongIds,
                            journal = current,
                        )
                        if (missingCopies > 0) {
                            val artworkBytes =
                                Math.multiplyExact(
                                    filesByPath.getValue(entry.name).size,
                                    missingCopies.toLong(),
                                )
                            processedBytes = Math.addExact(processedBytes, artworkBytes)
                        }
                    }
                    zip.closeEntry()
                    reportImportProgress(
                        onProgress,
                        current,
                        backup,
                        processedBytes,
                        totalBytes,
                    )
                }
            }
        }
        return current
    }
    private fun initializeJournal(
        backup: ValidatedCarBackup,
        journal: CarImportJournal,
    ): CarImportJournal {
        if (journal.songs.size == backup.songs.size) return journal
        val resources = backup.resources.associateBy(CarPortableResource::songId)
        var current = journal
        backup.songs.forEach { song ->
            if (song.id in current.songs) return@forEach
            val resource = resources.getValue(song.id)
            val artworkExpected = song.thumbnailArtworkPath != null
            val state = inspector.inspect(song.id, resource.contentLength, artworkExpected)
            val record =
                CarSongImportRecord(
                    songId = song.id,
                    contentLength = resource.contentLength,
                    artworkExpected = artworkExpected,
                    initialCachePresent = state.cachePresent,
                    initialDownloadPresent = state.downloadPresent,
                    initialRoomPresent = state.roomPresent,
                    initialRoomDownloaded = state.roomDownloaded,
                    initialRoomDateDownload = state.roomDateDownload,
                    initialArtworkPresent = state.artworkPresent,
                    cacheReady = state.cacheComplete,
                    downloadReady = state.downloadCompleted,
                    artworkReady = state.artworkPresent,
                    roomReady = state.roomComplete,
                    completed = state.fullyComplete,
                )
            current = current.copy(songs = current.songs + (song.id to record))
        }
        journalStore.save(current)
        return current
    }
    private fun importArtworkEntry(
        zip: ZipInputStream,
        songIds: List<String>,
        journal: CarImportJournal,
    ): CarImportJournal {
        var current = journal
        val missing = songIds.filter { !current.songs.getValue(it).artworkReady }
        if (missing.isEmpty()) return current

        val existing = songIds.firstOrNull { current.songs.getValue(it).artworkReady }
        val sourceSongId =
            if (existing != null) {
                existing
            } else {
                val first = missing.first()
                artworkTarget.write(first, nonClosing(zip))
                current = updateRecord(current, first) { it.copy(artworkReady = true) }
                first
            }

        missing.filter { it != sourceSongId }.forEach { songId ->
            artworkTarget.copy(sourceSongId, songId)
            current = updateRecord(current, songId) { it.copy(artworkReady = true) }
        }
        return current
    }
    private suspend fun commitMetadata(
        backup: ValidatedCarBackup,
        journal: CarImportJournal,
        totalBytes: Long,
        onProgress: (CarImportProgress) -> Unit,
    ): CarImportJournal {
        val transfers = backup.transfers.associateBy(CarPortableTransfer::requestId)
        val formats = backup.formats.associateBy(CarPortableFormat::id)
        val artists = backup.artists.associateBy(CarPortableArtist::id)
        val mapsBySong = backup.artistMaps.groupBy(CarPortableMap::songId)
        var current = journal

        backup.songs.forEach { song ->
            var record = current.songs.getValue(song.id)
            if (!record.downloadReady) {
                mediaTarget.putCompletedDownload(transfers.getValue(song.id))
                current = updateRecord(current, song.id) { it.copy(downloadReady = true) }
                record = current.songs.getValue(song.id)
            }
            if (!record.roomReady) {
                val maps = mapsBySong[song.id].orEmpty().sortedBy(CarPortableMap::position)
                val songArtists = maps.map { artists.getValue(it.artistId) }
                roomTarget.upsertSongBundle(
                    song = song,
                    artists = songArtists,
                    maps = maps,
                    format = formats.getValue(song.id),
                )
                current = updateRecord(current, song.id) { it.copy(roomReady = true) }
            }

            val state =
                inspector.inspect(
                    songId = song.id,
                    contentLength = current.songs.getValue(song.id).contentLength,
                    artworkExpected = current.songs.getValue(song.id).artworkExpected,
                )
            check(state.fullyComplete) { "Imported song layers are incomplete: ${song.id}" }
            current = updateRecord(current, song.id) {
                it.copy(
                    cacheReady = true,
                    downloadReady = true,
                    artworkReady = true,
                    roomReady = true,
                    completed = true,
                )
            }
            reportImportProgress(
                onProgress = onProgress,
                journal = current,
                backup = backup,
                bytesProcessed = totalBytes,
                totalBytes = totalBytes,
            )
        }
        return current
    }
    private fun updateRecord(
        journal: CarImportJournal,
        songId: String,
        transform: (CarSongImportRecord) -> CarSongImportRecord,
    ): CarImportJournal {
        val updated =
            journal.copy(
                songs = journal.songs + (songId to transform(journal.songs.getValue(songId))),
            )
        journalStore.save(updated)
        return updated
    }

    private fun reportImportProgress(
        onProgress: (CarImportProgress) -> Unit,
        journal: CarImportJournal,
        backup: ValidatedCarBackup,
        bytesProcessed: Long,
        totalBytes: Long,
    ) {
        report(
            onProgress,
            CarImportProgress(
                phase = journal.phase,
                completedSongs = journal.songs.values.count(CarSongImportRecord::completed),
                totalSongs = backup.songs.size,
                bytesProcessed = bytesProcessed.coerceAtMost(totalBytes),
                totalBytes = totalBytes,
            ),
        )
    }
    private fun fingerprint(manifest: CarPortableManifest): String {
        val canonical =
            buildString {
                append(manifest.backupFormatVersion).append('|')
                append(manifest.createdAt).append('|')
                append(manifest.sourceAppVersion).append('|')
                manifest.songIds.forEach { append(it).append('|') }
                manifest.files.forEach { file ->
                    append(file.relativePath).append(':')
                    append(file.size).append(':')
                    append(file.sha256).append('|')
                }
            }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.encodeToByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun nonClosing(input: InputStream): InputStream =
        object : FilterInputStream(input) {
            override fun close() = Unit
        }

    private fun report(
        onProgress: (CarImportProgress) -> Unit,
        progress: CarImportProgress,
    ) {
        onProgress(progress)
    }
}
