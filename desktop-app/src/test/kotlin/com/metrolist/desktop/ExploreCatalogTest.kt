package com.metrolist.desktop

import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.BrowseEndpoint
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.pages.BrowseResult
import com.metrolist.innertube.pages.ExplorePage
import com.metrolist.innertube.pages.MoodAndGenres
import kotlin.test.Test
import kotlin.test.assertEquals

class ExploreCatalogTest {
    @Test
    fun exploreMappingKeepsOnlyUsefulDistinctSourceContent() {
        val album = album("album-1", "Album One")
        val mood = mood("Focus", "FEmusic_moods_and_genres_category")
        val content = mapExploreContent(
            ExplorePage(
                newReleaseAlbums = listOf(album, album, album("", "")),
                moodAndGenres = listOf(mood, mood, mood("", "")),
            ),
        )

        assertEquals(listOf("album-1"), content.newReleaseAlbums.map(AlbumItem::id))
        assertEquals(listOf("Focus"), content.moods.map(MoodAndGenres.Item::title))
    }

    @Test
    fun browseMappingDropsEmptySectionsAndUsesUsefulFallbackTitle() {
        val song = SongItem(id = "song-1", title = "Song One", artists = emptyList(), thumbnail = "")
        val sections = mapBrowseSections(
            BrowseResult(
                title = "Focus",
                items = listOf(
                    BrowseResult.Item(title = "", items = listOf(song)),
                    BrowseResult.Item(title = "Empty", items = emptyList()),
                ),
            ),
        )

        assertEquals(1, sections.size)
        assertEquals("Focus", sections.single().title)
        assertEquals(listOf("song-1"), sections.single().items.map { it.id })
    }

    private fun album(id: String, title: String) =
        AlbumItem(
            browseId = id,
            playlistId = "playlist-$id",
            title = title,
            artists = emptyList(),
            thumbnail = "",
        )

    private fun mood(title: String, browseId: String) =
        MoodAndGenres.Item(
            title = title,
            stripeColor = 0,
            endpoint = BrowseEndpoint(browseId = browseId),
        )
}
