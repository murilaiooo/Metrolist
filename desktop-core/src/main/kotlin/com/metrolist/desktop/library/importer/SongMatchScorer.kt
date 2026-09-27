package com.metrolist.desktop.library.importer

import com.metrolist.innertube.models.SongItem
import kotlin.math.max

object SongMatchScorer {
    const val READY_THRESHOLD = 0.90
    const val REVIEW_THRESHOLD = 0.70

    fun rank(
        input: ParsedImportLine,
        candidates: List<SongItem>,
    ): SongMatchDecision {
        val scored =
            candidates
                .mapIndexed { index, song ->
                    IndexedValue(index, score(input, song))
                }
                .sortedWith(
                    compareByDescending<IndexedValue<ScoredCandidate>> { it.value.score }
                        .thenByDescending { it.value.versionKind == SongVersionKind.ORIGINAL }
                        .thenByDescending { it.value.song.explicit }
                        .thenBy { it.index },
                ).map { it.value }

        val best = scored.firstOrNull()
            ?: return SongMatchDecision(null, emptyList(), MatchState.NOT_FOUND, 0.0)

        val state =
            when {
                best.score < REVIEW_THRESHOLD -> MatchState.NOT_FOUND
                best.versionKind != SongVersionKind.ORIGINAL -> MatchState.REVIEW
                best.score >= READY_THRESHOLD -> MatchState.READY
                else -> MatchState.REVIEW
            }

        return SongMatchDecision(
            selected = best.song.takeUnless { state == MatchState.NOT_FOUND },
            candidates = scored,
            state = state,
            score = best.score,
        )
    }

    private fun score(
        input: ParsedImportLine,
        song: SongItem,
    ): ScoredCandidate {
        val versionKind = SongVersionClassifier.classify(song.title, song.album?.name)
        val identity = identityScore(input, song)
        val adjusted = (identity - versionPenalty(versionKind)).coerceIn(0.0, 1.0)
        return ScoredCandidate(
            song = song,
            score = adjusted,
            versionKind = versionKind,
            identityScore = identity,
        )
    }

    private fun identityScore(
        input: ParsedImportLine,
        song: SongItem,
    ): Double {
        val right = input.right
        if (right == null) {
            return 0.88 * similarity(
                SongTextNormalizer.baseTitle(input.left, removeVersionMarkers = true),
                SongTextNormalizer.baseTitle(song.title, removeVersionMarkers = true),
            )
        }

        val leftAsArtist = orientationScore(artist = input.left, title = right, song = song)
        val rightAsArtist = orientationScore(artist = right, title = input.left, song = song)
        return max(leftAsArtist, rightAsArtist)
    }

    private fun orientationScore(
        artist: String,
        title: String,
        song: SongItem,
    ): Double {
        val titleScore =
            similarity(
                SongTextNormalizer.baseTitle(title, removeVersionMarkers = true),
                SongTextNormalizer.baseTitle(song.title, removeVersionMarkers = true),
            )
        val normalizedArtist = SongTextNormalizer.normalize(artist)
        val artistCandidates =
            buildList {
                song.artists.forEach { add(SongTextNormalizer.normalize(it.name)) }
                if (song.artists.size > 1) {
                    add(SongTextNormalizer.normalize(song.artists.joinToString(" ") { it.name }))
                }
            }
        val artistScore = artistCandidates.maxOfOrNull { similarity(normalizedArtist, it) } ?: 0.0
        return (titleScore * 0.72) + (artistScore * 0.28)
    }

    private fun versionPenalty(kind: SongVersionKind): Double =
        when (kind) {
            SongVersionKind.ORIGINAL -> 0.0
            SongVersionKind.REMASTER -> 0.05
            SongVersionKind.DELUXE -> 0.06
            SongVersionKind.ACOUSTIC -> 0.10
            SongVersionKind.UNPLUGGED -> 0.10
            SongVersionKind.LIVE -> 0.25
            SongVersionKind.REMIX -> 0.22
            SongVersionKind.RADIO_EDIT -> 0.18
            SongVersionKind.COVER -> 0.35
            SongVersionKind.KARAOKE -> 0.40
            SongVersionKind.SPED_UP -> 0.40
            SongVersionKind.SLOWED -> 0.40
            SongVersionKind.NIGHTCORE -> 0.40
            SongVersionKind.OTHER -> 0.12
        }

    internal fun similarity(
        left: String,
        right: String,
    ): Double {
        if (left.isBlank() || right.isBlank()) return 0.0
        if (left == right) return 1.0

        val leftTokens = left.split(' ').filter(String::isNotBlank).toSet()
        val rightTokens = right.split(' ').filter(String::isNotBlank).toSet()
        val intersection = leftTokens.intersect(rightTokens).size.toDouble()
        val dice =
            if (leftTokens.isEmpty() || rightTokens.isEmpty()) 0.0
            else (2.0 * intersection) / (leftTokens.size + rightTokens.size).toDouble()
        val edit = normalizedEditSimilarity(left, right)
        return (dice * 0.7 + edit * 0.3).coerceIn(0.0, 1.0)
    }

    private fun normalizedEditSimilarity(
        left: String,
        right: String,
    ): Double {
        val maxLength = max(left.length, right.length)
        if (maxLength == 0) return 1.0
        return 1.0 - (levenshtein(left, right).toDouble() / maxLength.toDouble())
    }

    private fun levenshtein(
        left: String,
        right: String,
    ): Int {
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length
        var previous = IntArray(right.length + 1) { it }
        var current = IntArray(right.length + 1)
        for (i in left.indices) {
            current[0] = i + 1
            for (j in right.indices) {
                val cost = if (left[i] == right[j]) 0 else 1
                current[j + 1] =
                    minOf(
                        current[j] + 1,
                        previous[j + 1] + 1,
                        previous[j] + cost,
                    )
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[right.length]
    }
}
