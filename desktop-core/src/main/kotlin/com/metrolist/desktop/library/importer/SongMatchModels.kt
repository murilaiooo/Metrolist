package com.metrolist.desktop.library.importer

import com.metrolist.innertube.models.SongItem

enum class MatchState {
    READY,
    REVIEW,
    NOT_FOUND,
}

enum class SongVersionKind {
    ORIGINAL,
    REMASTER,
    DELUXE,
    ACOUSTIC,
    UNPLUGGED,
    LIVE,
    REMIX,
    RADIO_EDIT,
    COVER,
    KARAOKE,
    SPED_UP,
    SLOWED,
    NIGHTCORE,
    OTHER,
}

data class ScoredCandidate(
    val song: SongItem,
    val score: Double,
    val versionKind: SongVersionKind,
    val identityScore: Double,
)

data class SongMatchDecision(
    val selected: SongItem?,
    val candidates: List<ScoredCandidate>,
    val state: MatchState,
    val score: Double,
)
