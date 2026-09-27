package com.metrolist.desktop

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metrolist.desktop.library.LocalPlaylist
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.YTItem

internal fun supportsGrid(tab: LibraryTab): Boolean =
    when (tab) {
        LibraryTab.PLAYLISTS,
        LibraryTab.ALBUMS,
        LibraryTab.ARTISTS,
        -> true

        LibraryTab.SONGS -> false
    }

internal fun effectivePresentationMode(
    tab: LibraryTab,
    stored: PresentationMode,
): PresentationMode =
    if (supportsGrid(tab)) stored else PresentationMode.LIST

@Composable
internal fun CatalogCollectionGrid(
    runtime: DesktopRuntime,
    items: List<YTItem>,
    onOpenPlaylist: (PlaylistItem) -> Unit,
    onOpenAlbum: (AlbumItem) -> Unit,
    onOpenArtist: (ArtistItem) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 180.dp),
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { item -> item::class.simpleName + ":" + item.id }) { item ->
            CatalogGridCard(
                runtime = runtime,
                item = item,
                onOpen = {
                    when (item) {
                        is PlaylistItem -> onOpenPlaylist(item)
                        is AlbumItem -> onOpenAlbum(item)
                        is ArtistItem -> onOpenArtist(item)
                        else -> Unit
                    }
                },
            )
        }
    }
}

@Composable
internal fun PlaylistLibraryGrid(
    runtime: DesktopRuntime,
    localPlaylists: List<LocalPlaylist>,
    remotePlaylists: List<PlaylistItem>,
    onOpenLocalPlaylist: (LocalPlaylist) -> Unit,
    onOpenPlaylist: (PlaylistItem) -> Unit,
    onDownloadPlaylist: (PlaylistItem) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 190.dp),
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (localPlaylists.isNotEmpty()) {
            item(
                key = "local-playlists-heading",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                Text(
                    "Local playlists",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            items(localPlaylists, key = { "local:" + it.id }) { playlist ->
                LocalPlaylistGridCard(
                    playlist = playlist,
                    onOpen = { onOpenLocalPlaylist(playlist) },
                )
            }
        }

        if (remotePlaylists.isNotEmpty()) {
            item(
                key = "youtube-playlists-heading",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                Text(
                    "YouTube Music playlists",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(remotePlaylists, key = { "remote:" + it.id }) { playlist ->
                RemotePlaylistGridCard(
                    runtime = runtime,
                    playlist = playlist,
                    onOpen = { onOpenPlaylist(playlist) },
                    onDownload = { onDownloadPlaylist(playlist) },
                )
            }
        }
    }
}

@Composable
internal fun CatalogGridCard(
    runtime: DesktopRuntime,
    item: YTItem,
    onOpen: () -> Unit,
) {
    val hoverInteraction = remember { MutableInteractionSource() }
    val hovered by hoverInteraction.collectIsHoveredAsState()
    var actionMenuExpanded by remember { mutableStateOf(false) }
    val target =
        when (item) {
            is AlbumItem -> CatalogActionTarget.ALBUM
            is ArtistItem -> CatalogActionTarget.ARTIST
            is PlaylistItem -> CatalogActionTarget.YOUTUBE_PLAYLIST
            else -> CatalogActionTarget.SONG
        }
    val actions =
        availableCatalogActions(
            target = target,
            capabilities =
                CatalogActionCapabilities(
                    canOpenAlbum = item is AlbumItem,
                    canOpenArtist = item is ArtistItem,
                    canCopyLink = true,
                ),
        )
    val handleAction: (CatalogAction) -> Unit = { action ->
        when (action) {
            CatalogAction.OpenAlbum,
            CatalogAction.OpenArtist,
            -> onOpen()

            CatalogAction.CopyLink -> copyTextToClipboard(catalogItemLink(target, item.id))
            else -> Unit
        }
    }

    CatalogContextArea(
        actions = actions,
        onAction = handleAction,
    ) {
        Surface(
            onClick = onOpen,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .hoverable(hoverInteraction),
            shape = MaterialTheme.shapes.large,
            color =
                if (hovered) {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
            tonalElevation = if (hovered) 1.dp else 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    ArtworkThumbnail(
                        runtime = runtime,
                        thumbnailUrl = item.thumbnail,
                        size = 148.dp,
                        contentDescription = item.title,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.title,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            catalogSubtitle(item),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (shouldRenderCatalogActionMenu(hovered, actionMenuExpanded)) {
                        CatalogActionMenu(
                            actions = actions,
                            expanded = actionMenuExpanded,
                            onExpandedChange = { actionMenuExpanded = it },
                            onAction = handleAction,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalPlaylistGridCard(
    playlist: LocalPlaylist,
    onOpen: () -> Unit,
) {
    val hoverInteraction = remember { MutableInteractionSource() }
    val hovered by hoverInteraction.collectIsHoveredAsState()
    Surface(
        onClick = onOpen,
        modifier =
            Modifier
                .fillMaxWidth()
                .hoverable(hoverInteraction),
        shape = MaterialTheme.shapes.large,
        color =
            if (hovered) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        tonalElevation = if (hovered) 1.dp else 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Surface(
                modifier = Modifier.size(148.dp).align(Alignment.CenterHorizontally),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.LibraryMusic,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Text(
                playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (playlist.youtubePlaylistId == null) "Local" else "Local · YouTube Music",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun RemotePlaylistGridCard(
    runtime: DesktopRuntime,
    playlist: PlaylistItem,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
) {
    val hoverInteraction = remember { MutableInteractionSource() }
    val hovered by hoverInteraction.collectIsHoveredAsState()
    var actionMenuExpanded by remember { mutableStateOf(false) }
    val target = CatalogActionTarget.YOUTUBE_PLAYLIST
    val actions =
        availableCatalogActions(
            target = target,
            capabilities =
                CatalogActionCapabilities(
                    canDownload = true,
                    canCopyLink = true,
                ),
        )
    val handleAction: (CatalogAction) -> Unit = { action ->
        when (action) {
            CatalogAction.Download -> onDownload()
            CatalogAction.CopyLink -> copyTextToClipboard(catalogItemLink(target, playlist.id))
            else -> Unit
        }
    }

    CatalogContextArea(
        actions = actions,
        onAction = handleAction,
    ) {
        Surface(
            onClick = onOpen,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .hoverable(hoverInteraction),
            shape = MaterialTheme.shapes.large,
            color =
                if (hovered) {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
            tonalElevation = if (hovered) 1.dp else 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    ArtworkThumbnail(
                        runtime = runtime,
                        thumbnailUrl = playlist.thumbnail,
                        size = 148.dp,
                        contentDescription = playlist.title,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            playlist.title,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            catalogSubtitle(playlist),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (shouldRenderCatalogActionMenu(hovered, actionMenuExpanded)) {
                        CatalogActionMenu(
                            actions = actions,
                            expanded = actionMenuExpanded,
                            onExpandedChange = { actionMenuExpanded = it },
                            onAction = handleAction,
                        )
                    }
                }
            }
        }
    }
}
