package com.metrolist.desktop.library.importer

import com.metrolist.innertube.models.Album
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.SongItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SongMatchScorerTest {
    @Test
    fun normalizesCasePunctuationAccentsAndFeaturingWithoutDroppingVersionWords() {
        assertEquals(
            "beyonce feat jay z live",
            SongTextNormalizer.normalize("Beyoncé (feat. JAY-Z) [LIVE]"),
        )
        assertEquals("song live", SongTextNormalizer.baseTitle("Song (Live)"))
        assertEquals("song", SongTextNormalizer.baseTitle("Song (Live)", removeVersionMarkers = true))
    }

    @Test
    fun classifiesCommonAlternateVersions() {
        assertEquals(SongVersionKind.LIVE, SongVersionClassifier.classify("Song (Live)", null))
        assertEquals(SongVersionKind.REMIX, SongVersionClassifier.classify("Song - Remix", null))
        assertEquals(SongVersionKind.RADIO_EDIT, SongVersionClassifier.classify("Song (Radio Edit)", null))
        assertEquals(SongVersionKind.ACOUSTIC, SongVersionClassifier.classify("Song Acoustic", null))
        assertEquals(SongVersionKind.UNPLUGGED, SongVersionClassifier.classify("Song", "MTV Unplugged"))
        assertEquals(SongVersionKind.REMASTER, SongVersionClassifier.classify("Song", "Album Remastered 2011"))
        assertEquals(SongVersionKind.DELUXE, SongVersionClassifier.classify("Song", "Album Deluxe Edition"))
        assertEquals(SongVersionKind.COVER, SongVersionClassifier.classify("Song Cover", null))
        assertEquals(SongVersionKind.KARAOKE, SongVersionClassifier.classify("Song Karaoke Version", null))
        assertEquals(SongVersionKind.SPED_UP, SongVersionClassifier.classify("Song Sped Up", null))
        assertEquals(SongVersionKind.SLOWED, SongVersionClassifier.classify("Song Slowed + Reverb", null))
        assertEquals(SongVersionKind.NIGHTCORE, SongVersionClassifier.classify("Nightcore Song", null))
        assertEquals(SongVersionKind.ORIGINAL, SongVersionClassifier.classify("Song", "Album"))
    }

    @Test
    fun explicitEditorialMarkerDoesNotLowerIdentityAndStillPrefersExplicitCandidate() {
        val input = ParsedImportLine(0, "Artist - Song [Explicit]", "Artist", "Song [Explicit]")
        val candidates =
            listOf(
                song("clean", "Song", explicit = false),
                song("explicit", "Song", explicit = true),
            )

        val decision = SongMatchScorer.rank(input, candidates)

        assertEquals("explicit", decision.selected?.id)
        assertEquals(MatchState.READY, decision.state)
        assertTrue(decision.score >= 0.90)
    }

    @Test
    fun originalExplicitBeatsCleanLiveRemixAndRadioEdit() {
        val input = ParsedImportLine(0, "Artist - Song", "Artist", "Song")
        val candidates =
            listOf(
                song("live", "Song (Live)", explicit = true),
                song("remix", "Song - Remix", explicit = true),
                song("radio", "Song (Radio Edit)", explicit = true),
                song("clean", "Song", explicit = false),
                song("explicit", "Song", explicit = true),
            )

        val decision = SongMatchScorer.rank(input, candidates)

        assertEquals("explicit", decision.selected?.id)
        assertEquals(MatchState.READY, decision.state)
        assertTrue(decision.score >= 0.90)
        assertEquals(SongVersionKind.ORIGINAL, decision.candidates.first().versionKind)
    }

    @Test
    fun explicitMarkerInImportedTextDoesNotReduceIdentityAndExplicitStillWins() {
        val input = ParsedImportLine(0, "Artist - Song [Explicit]", "Artist", "Song [Explicit]")
        val decision =
            SongMatchScorer.rank(
                input,
                listOf(
                    song("clean", "Song", explicit = false),
                    song("explicit", "Song", explicit = true),
                ),
            )

        assertEquals("explicit", decision.selected?.id)
        assertEquals(MatchState.READY, decision.state)
        assertTrue(decision.score >= 0.90)
    }

    @Test
    fun fallbackRemasterStaysReviewEvenWithExactIdentity() {
        val input = ParsedImportLine(0, "Artist - Song", "Artist", "Song")
        val decision = SongMatchScorer.rank(input, listOf(song("remaster", "Song (2011 Remaster)", album = "Album Remastered")))

        assertEquals("remaster", decision.selected?.id)
        assertEquals(MatchState.REVIEW, decision.state)
        assertTrue(decision.score >= 0.70)
    }

    @Test
    fun coverKaraokeAndAlteredVersionsCannotBecomeReadyFromTitleSimilarityAlone() {
        val input = ParsedImportLine(0, "Artist - Song", "Artist", "Song")
        val candidates =
            listOf(
                song("cover", "Song Cover"),
                song("karaoke", "Song Karaoke Version"),
                song("sped", "Song Sped Up"),
                song("slowed", "Song Slowed"),
            )

        val decision = SongMatchScorer.rank(input, candidates)

        assertTrue(decision.state != MatchState.READY)
    }

    @Test
    fun plainTitleExactMatchRequiresReviewBecauseArtistIsUnknown() {
        val input = ParsedImportLine(0, "Song", "Song", null)
        val decision = SongMatchScorer.rank(input, listOf(song("one", "Song")))

        assertEquals("one", decision.selected?.id)
        assertEquals(MatchState.REVIEW, decision.state)
        assertTrue(decision.score in 0.70..<0.90)
    }

    @Test
    fun poorIdentityBecomesNotFound() {
        val input = ParsedImportLine(0, "Artist - Song", "Artist", "Song")
        val decision = SongMatchScorer.rank(input, listOf(song("other", "Completely Different", artist = "Someone Else")))

        assertEquals(MatchState.NOT_FOUND, decision.state)
        assertEquals(null, decision.selected)
        assertTrue(decision.score < 0.70)
    }

    @Test
    fun evaluatesBothSidesSoSongArtistAndArtistSongBothResolve() {
        val candidate = song("match", "Song", artist = "Artist")
        val artistFirst = SongMatchScorer.rank(ParsedImportLine(0, "Artist - Song", "Artist", "Song"), listOf(candidate))
        val songFirst = SongMatchScorer.rank(ParsedImportLine(0, "Song - Artist", "Song", "Artist"), listOf(candidate))

        assertEquals("match", artistFirst.selected?.id)
        assertEquals("match", songFirst.selected?.id)
        assertEquals(MatchState.READY, artistFirst.state)
        assertEquals(MatchState.READY, songFirst.state)
    }

    @Test
    fun equalScoresKeepOriginalCandidateOrder() {
        val input = ParsedImportLine(0, "Artist - Song", "Artist", "Song")
        val first = song("first", "Song")
        val second = song("second", "Song")

        val decision = SongMatchScorer.rank(input, listOf(first, second))

        assertEquals(listOf("first", "second"), decision.candidates.map { it.song.id })
    }

    private fun song(
        id: String,
        title: String,
        artist: String = "Artist",
        album: String = "Album",
        explicit: Boolean = false,
    ) =
        SongItem(
            id = id,
            title = title,
            artists = listOf(Artist(artist, "artist-id")),
            album = Album(album, "album-id"),
            duration = 200,
            musicVideoType = "MUSIC_VIDEO_TYPE_ATV",
            thumbnail = "https://example.invalid/$id.jpg",
            explicit = explicit,
        )
}
