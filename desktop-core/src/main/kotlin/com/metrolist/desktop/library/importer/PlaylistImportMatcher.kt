package com.metrolist.desktop.library.importer

import com.metrolist.desktop.library.ImportCandidateSnapshot
import com.metrolist.desktop.library.ImportRow
import com.metrolist.desktop.library.ImportRowState
import com.metrolist.desktop.library.LibraryAlbumSnapshot
import com.metrolist.desktop.library.LibraryArtistSnapshot
import com.metrolist.desktop.library.LibrarySongSnapshot
import com.metrolist.desktop.library.LibraryStore
import com.metrolist.desktop.library.RememberedMatch
import com.metrolist.innertube.models.SongItem
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class ImportProgress(
    val total: Int = 0,
    val completed: Int = 0,
    val ready: Int = 0,
    val review: Int = 0,
    val notFound: Int = 0,
    val failed: Int = 0,
    val running: Int = 0,
)

class PlaylistImportMatcher(
    private val store: LibraryStore,
    private val searchGateway: SongSearchGateway,
) {
    private val semaphore = Semaphore(4)
    private val runningCount = AtomicInteger(0)
    private val _progress = MutableStateFlow(ImportProgress())
    val progress: StateFlow<ImportProgress> = _progress.asStateFlow()

    suspend fun match(sessionId: String) {
        refreshProgress(sessionId)
        val pending =
            store.importRows(sessionId).filter {
                it.state == ImportRowState.PENDING || it.state == ImportRowState.FAILED
            }
        coroutineScope {
            pending.map { row ->
                async {
                    semaphore.withPermit {
                        runningCount.incrementAndGet()
                        refreshProgress(sessionId)
                        try {
                            processRow(row)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Throwable) {
                            persistFailure(row, error)
                        } finally {
                            runningCount.decrementAndGet()
                            refreshProgress(sessionId)
                        }
                    }
                }
            }.awaitAll()
        }
        refreshProgress(sessionId)
    }

    fun selectCandidate(
        sessionId: String,
        position: Int,
        videoId: String,
    ): ImportRow {
        val row = requireNotNull(store.getImportRow(sessionId, position)) {
            "Import row not found: $sessionId/$position"
        }
        val candidate = row.candidates.firstOrNull { it.song.videoId == videoId }
            ?: error("Candidate $videoId is not available for $sessionId/$position")
        val selected =
            row.copy(
                state = ImportRowState.READY,
                selectedSong = candidate.song,
                score = candidate.score,
                error = null,
            )
        store.upsertImportRows(listOf(selected))

        val primaryArtist = candidate.song.artists.firstOrNull()?.name.orEmpty()
        if (primaryArtist.isNotBlank()) {
            store.upsertRememberedMatch(
                RememberedMatch(
                    identity = importIdentity(selected),
                    videoId = candidate.song.videoId,
                    title = candidate.song.title,
                    artist = primaryArtist,
                    duration = candidate.song.duration,
                    explicit = candidate.song.explicit,
                    updatedAtMs = System.currentTimeMillis(),
                ),
            )
        }
        refreshProgress(sessionId)
        return selected
    }

    suspend fun retryRow(
        sessionId: String,
        position: Int,
        query: String? = null,
    ) {
        val row = requireNotNull(store.getImportRow(sessionId, position)) {
            "Import row not found: $sessionId/$position"
        }
        semaphore.withPermit {
            runningCount.incrementAndGet()
            refreshProgress(sessionId)
            try {
                processRow(row, query?.trim()?.takeIf(String::isNotEmpty))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                persistFailure(row, error)
            } finally {
                runningCount.decrementAndGet()
                refreshProgress(sessionId)
            }
        }
    }

    private suspend fun processRow(
        row: ImportRow,
        queryOverride: String? = null,
    ) {
        if (queryOverride == null) {
            val remembered = store.getRememberedMatch(importIdentity(row))
            if (remembered != null) {
                val validated = validateRemembered(remembered)
                if (validated != null) {
                    store.upsertImportRows(
                        listOf(
                            row.copy(
                                state = ImportRowState.READY,
                                selectedSong = validated.toSnapshot(),
                                score = 1.0,
                                candidates =
                                    listOf(
                                        ImportCandidateSnapshot(
                                            song = validated.toSnapshot(),
                                            score = 1.0,
                                            versionKind = SongVersionClassifier
                                                .classify(validated.title, validated.album?.name)
                                                .name,
                                        ),
                                    ),
                                error = null,
                            ),
                        ),
                    )
                    return
                }
            }
        }

        val queries =
            queryOverride?.let(::listOf)
                ?: buildQueries(row)
        val byId = LinkedHashMap<String, SongItem>()
        for (query in queries) {
            searchGateway.search(query).forEach { song ->
                byId.putIfAbsent(song.id, song)
            }
        }

        val parsed = ParsedImportLine(row.position, row.original, row.left, row.right)
        val decision = SongMatchScorer.rank(parsed, byId.values.toList())
        val persistedCandidates =
            decision.candidates.take(8).map { candidate ->
                ImportCandidateSnapshot(
                    song = candidate.song.toSnapshot(),
                    score = candidate.score,
                    versionKind = candidate.versionKind.name,
                )
            }
        store.upsertImportRows(
            listOf(
                row.copy(
                    state = decision.state.toImportRowState(),
                    selectedSong = decision.selected?.toSnapshot(),
                    score = decision.score,
                    candidates = persistedCandidates,
                    error = null,
                ),
            ),
        )
    }

    private suspend fun validateRemembered(match: RememberedMatch): SongItem? {
        val candidates = searchGateway.search("${match.artist} ${match.title}")
        val candidate = candidates.firstOrNull { it.id == match.videoId } ?: return null
        val titleOk =
            SongMatchScorer.similarity(
                SongTextNormalizer.baseTitle(match.title, removeVersionMarkers = true),
                SongTextNormalizer.baseTitle(candidate.title, removeVersionMarkers = true),
            ) >= 0.90
        val rememberedArtist = SongTextNormalizer.normalize(match.artist)
        val artistOk =
            candidate.artists.any { artist ->
                SongMatchScorer.similarity(
                    rememberedArtist,
                    SongTextNormalizer.normalize(artist.name),
                ) >= 0.90
            }
        val durationOk =
            match.duration == null || candidate.duration == null || abs(match.duration - candidate.duration) <= 8
        val explicitOk = !match.explicit || candidate.explicit
        return candidate.takeIf { titleOk && artistOk && durationOk && explicitOk }
    }

    private fun buildQueries(row: ImportRow): List<String> =
        buildList {
            val right = row.right
            if (right == null) {
                add(row.left)
            } else {
                add("${row.left} $right")
                add("$right ${row.left}")
            }
        }.map(String::trim).filter(String::isNotEmpty).distinct()

    private fun persistFailure(
        row: ImportRow,
        error: Throwable,
    ) {
        store.upsertImportRows(
            listOf(
                row.copy(
                    state = ImportRowState.FAILED,
                    error = error.message ?: error::class.simpleName ?: "Search failed",
                ),
            ),
        )
    }

    private fun refreshProgress(sessionId: String) {
        val rows = store.importRows(sessionId)
        _progress.value =
            ImportProgress(
                total = rows.size,
                completed = rows.count { it.state != ImportRowState.PENDING },
                ready = rows.count { it.state == ImportRowState.READY },
                review = rows.count { it.state == ImportRowState.REVIEW },
                notFound = rows.count { it.state == ImportRowState.NOT_FOUND },
                failed = rows.count { it.state == ImportRowState.FAILED },
                running = runningCount.get(),
            )
    }
}

internal fun importIdentity(row: ImportRow): String =
    listOfNotNull(row.left, row.right)
        .map(SongTextNormalizer::normalize)
        .filter(String::isNotEmpty)
        .sorted()
        .joinToString("|")

private fun MatchState.toImportRowState(): ImportRowState =
    when (this) {
        MatchState.READY -> ImportRowState.READY
        MatchState.REVIEW -> ImportRowState.REVIEW
        MatchState.NOT_FOUND -> ImportRowState.NOT_FOUND
    }

internal fun SongItem.toSnapshot(): LibrarySongSnapshot =
    LibrarySongSnapshot(
        videoId = id,
        title = title,
        artists = artists.map { LibraryArtistSnapshot(it.name, it.id) },
        album = album?.let { LibraryAlbumSnapshot(it.name, it.id) },
        duration = duration,
        musicVideoType = musicVideoType,
        thumbnail = thumbnail,
        explicit = explicit,
        isEpisode = isEpisode,
    )
