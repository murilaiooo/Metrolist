package com.metrolist.desktop

import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.BrowseEndpoint
import com.metrolist.innertube.pages.MoodAndGenres
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExploreUiTest {
    @Test
    fun primaryNavigationPlacesExploreBetweenHomeAndSearch() {
        assertEquals(
            listOf(
                AppDestination.HOME,
                AppDestination.EXPLORE,
                AppDestination.SEARCH,
                AppDestination.LIBRARY,
                AppDestination.DOWNLOADS,
            ),
            AppDestination.entries,
        )
    }

    @Test
    fun exploreSectionsOnlyAppearWhenTheyHaveContent() {
        val releasesOnly = ExploreContent(newReleaseAlbums = listOf(album()), moods = emptyList())
        assertEquals(setOf(ExploreSection.NEW_RELEASES), visibleExploreSections(releasesOnly))

        val moodsOnly = ExploreContent(newReleaseAlbums = emptyList(), moods = listOf(mood()))
        assertEquals(setOf(ExploreSection.MOODS), visibleExploreSections(moodsOnly))
    }

    @Test
    fun exploreEmptyStateWaitsForLoadingAndErrorToFinish() {
        val empty = ExploreContent()
        assertFalse(shouldShowExploreEmptyState(empty, loading = true, error = null))
        assertFalse(shouldShowExploreEmptyState(empty, loading = false, error = "offline"))
        assertTrue(shouldShowExploreEmptyState(empty, loading = false, error = null))
    }

    private fun album() =
        AlbumItem(
            browseId = "album-1",
            playlistId = "playlist-1",
            title = "Album One",
            artists = emptyList(),
            thumbnail = "",
        )

    private fun mood() =
        MoodAndGenres.Item(
            title = "Focus",
            stripeColor = 0,
            endpoint = BrowseEndpoint("focus"),
        )
}
