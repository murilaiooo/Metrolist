package com.metrolist.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metrolist.desktop.offline.OfflineRecord
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.pages.MoodAndGenres

internal enum class ExploreSection {
    NEW_RELEASES,
    MOODS,
}

internal fun visibleExploreSections(content: ExploreContent): Set<ExploreSection> =
    buildSet {
        if (content.newReleaseAlbums.isNotEmpty()) add(ExploreSection.NEW_RELEASES)
        if (content.moods.isNotEmpty()) add(ExploreSection.MOODS)
    }

internal fun shouldShowExploreEmptyState(
    content: ExploreContent,
    loading: Boolean,
    error: String?,
): Boolean =
    !loading &&
        error == null &&
        visibleExploreSections(content).isEmpty()

@Composable
internal fun ExploreView(
    runtime: DesktopRuntime,
    ui: DesktopUiState,
    downloadsById: Map<String, OfflineRecord>,
    onRefresh: () -> Unit,
    onSelectMood: (MoodAndGenres.Item) -> Unit,
    onCloseMood: () -> Unit,
    onOpenPlaylist: (PlaylistItem) -> Unit,
    onOpenAlbum: (AlbumItem) -> Unit,
    onOpenArtist: (ArtistItem) -> Unit,
    onPlay: (SongItem) -> Unit,
    onDownload: (SongItem) -> Unit,
) {
    val selectedMood = ui.exploreSelectedMood
    if (selectedMood != null) {
        ExploreMoodDetail(
            runtime = runtime,
            mood = selectedMood,
            sections = ui.exploreDetailSections,
            loading = ui.exploreDetailLoading,
            error = ui.exploreDetailError,
            downloadsById = downloadsById,
            onBack = onCloseMood,
            onRetry = { onSelectMood(selectedMood) },
            onOpenPlaylist = onOpenPlaylist,
            onOpenAlbum = onOpenAlbum,
            onOpenArtist = onOpenArtist,
            onPlay = onPlay,
            onDownload = onDownload,
        )
        return
    }

    val sections = visibleExploreSections(ui.exploreContent)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(bottom = 20.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Explore", style = MaterialTheme.typography.headlineLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "New releases and ways to discover something different",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = onRefresh,
                    enabled = !ui.exploreLoading,
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh Explore")
                }
            }

            if (ui.exploreLoading) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            ui.exploreError?.let { error ->
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Could not refresh Explore: " + error,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = onRefresh) {
                        Text("Try again")
                    }
                }
            }
        }

        if (ExploreSection.NEW_RELEASES in sections) {
            item(key = "explore:new-releases") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("New releases", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Fresh albums available on YouTube Music",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(end = 12.dp),
                    ) {
                        items(
                            items = ui.exploreContent.newReleaseAlbums,
                            key = { album -> album.id },
                        ) { album ->
                            Box(Modifier.width(184.dp)) {
                                CatalogGridCard(
                                    runtime = runtime,
                                    item = album,
                                    onOpen = { onOpenAlbum(album) },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (ExploreSection.MOODS in sections) {
            item(key = "explore:moods") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Moods & genres", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Pick a direction and browse the collection behind it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(end = 12.dp),
                    ) {
                        items(
                            items = ui.exploreContent.moods.chunked(3),
                            key = { column ->
                                column.joinToString("|") { mood ->
                                    mood.title + ":" + mood.endpoint.browseId + ":" +
                                        mood.endpoint.params.orEmpty()
                                }
                            },
                        ) { column ->
                            Column(
                                modifier = Modifier.width(230.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                column.forEach { mood ->
                                    MoodButton(
                                        mood = mood,
                                        onClick = { onSelectMood(mood) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (
            shouldShowExploreEmptyState(
                ui.exploreContent,
                ui.exploreLoading,
                ui.exploreError,
            )
        ) {
            item(key = "explore:empty") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            "Nothing to explore right now",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "Refresh to ask YouTube Music for the latest discovery collections.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MoodButton(
    mood: MoodAndGenres.Item,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Explore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = mood.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ExploreMoodDetail(
    runtime: DesktopRuntime,
    mood: MoodAndGenres.Item,
    sections: List<CatalogDetailSection>,
    loading: Boolean,
    error: String?,
    downloadsById: Map<String, OfflineRecord>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenPlaylist: (PlaylistItem) -> Unit,
    onOpenAlbum: (AlbumItem) -> Unit,
    onOpenArtist: (ArtistItem) -> Unit,
    onPlay: (SongItem) -> Unit,
    onDownload: (SongItem) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 20.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back to Explore",
                    )
                }
                Spacer(Modifier.width(4.dp))
                Column(Modifier.weight(1f)) {
                    Text(mood.title, style = MaterialTheme.typography.headlineLarge)
                    Text(
                        "Explore collection",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (loading) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            error?.let {
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Could not load this collection: " + it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = onRetry) {
                        Text("Try again")
                    }
                }
            }
        }

        sections.forEachIndexed { sectionIndex, section ->
            item(key = "mood-heading:" + sectionIndex + ":" + section.title) {
                Text(
                    section.title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(
                items = section.items,
                key = { item ->
                    "mood:" + sectionIndex + ":" + item::class.simpleName + ":" + item.id
                },
            ) { item ->
                CatalogItemRow(
                    runtime = runtime,
                    item = item,
                    downloadRecord = downloadsById[item.id],
                    onOpenPlaylist = onOpenPlaylist,
                    onOpenAlbum = onOpenAlbum,
                    onOpenArtist = onOpenArtist,
                    onPlay = onPlay,
                    onDownload = onDownload,
                )
            }
        }

        if (!loading && error == null && sections.isEmpty()) {
            item {
                Text(
                    "No playable items were returned for this collection.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
