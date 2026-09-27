package com.metrolist.desktop.library.importer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PlaylistTextParserTest {
    @Test
    fun parsesBomCrLfNumberingAndBlankLines() {
        val text = "\uFEFF1. Artist A - Song One\r\n\r\n2) Artist B\tSong Two\r\n  3. Plain Song  \r\n"

        val rows = PlaylistTextParser.parse(text)

        assertEquals(3, rows.size)
        assertEquals(0, rows[0].position)
        assertEquals("Artist A - Song One", rows[0].original)
        assertEquals("Artist A", rows[0].left)
        assertEquals("Song One", rows[0].right)
        assertEquals("Artist B", rows[1].left)
        assertEquals("Song Two", rows[1].right)
        assertEquals("Plain Song", rows[2].left)
        assertNull(rows[2].right)
    }

    @Test
    fun splitsOnlyTheFirstSpacedSeparatorAndPreservesHyphensInsideText() {
        val rows =
            PlaylistTextParser.parse(
                "Artist - Song - Part 2\nAC-DC - T.N.T.\nSong-Without-Spaced-Separator",
            )

        assertEquals("Artist", rows[0].left)
        assertEquals("Song - Part 2", rows[0].right)
        assertEquals("AC-DC", rows[1].left)
        assertEquals("T.N.T.", rows[1].right)
        assertEquals("Song-Without-Spaced-Separator", rows[2].left)
        assertNull(rows[2].right)
    }

    @Test
    fun preservesBothOrientationsWithoutGuessingWhichSideIsArtist() {
        val rows = PlaylistTextParser.parse("Artist - Song\nSong - Artist")

        assertEquals("Artist", rows[0].left)
        assertEquals("Song", rows[0].right)
        assertEquals("Song", rows[1].left)
        assertEquals("Artist", rows[1].right)
    }

    @Test
    fun acceptsExactlyOneThousandMeaningfulLines() {
        val text = (1..1000).joinToString("\n") { "Artist $it - Song $it" }

        val rows = PlaylistTextParser.parse(text)

        assertEquals(1000, rows.size)
        assertEquals(999, rows.last().position)
    }

    @Test
    fun rejectsMoreThanOneThousandMeaningfulLinesInsteadOfSilentlyTruncating() {
        val text = (1..1001).joinToString("\n") { "Song $it" }

        val error = assertFailsWith<IllegalArgumentException> { PlaylistTextParser.parse(text) }

        assertEquals("Playlist import supports at most 1000 non-blank lines", error.message)
    }
}
