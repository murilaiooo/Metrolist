package com.metrolist.desktop

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.metrolist.desktop.offline.OfflineRecord
import com.metrolist.desktop.offline.OfflineState
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import kotlinx.coroutines.launch

@Composable
internal fun CatalogItemRow(
    runtime: DesktopRuntime,
    item: YTItem,
    downloadRecord: OfflineRecord?,
    onOpenPlaylist: (PlaylistItem) -> Unit,
    onOpenAlbum: (AlbumItem) -> Unit,
    onOpenArtist: (ArtistItem) -> Unit,
    onPlay: (SongItem) -> Unit,
    onDownload: (SongItem) -> Unit,
    onDownloadPlaylist: ((PlaylistItem) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val hoverInteraction = remember { MutableInteractionSource() }
    val hovered by hoverInteraction.collectIsHoveredAsState()
    val target = catalogActionTarget(item)
    val actionCapabilities =
        catalogActionCapabilities(item, downloadRecord).let { capabilities ->
            if (item is PlaylistItem && onDownloadPlaylist != null) {
                capabilities.copy(canDownload = true)
            } else {
                capabilities
            }
        }
    val actions =
        availableCatalogActions(
            target = target,
            capabilities = actionCapabilities,
        )
    val openPrimary: () -> Unit = {
        when (item) {
            is SongItem -> onPlay(item)
            is PlaylistItem -> onOpenPlaylist(item)
            is AlbumItem -> onOpenAlbum(item)
            is ArtistItem -> onOpenArtist(item)
            else -> Unit
        }
    }
    val handleAction: (CatalogAction) -> Unit = { action ->
        when (action) {
            CatalogAction.PlayNext -> if (item is SongItem) scope.launch { runtime.playNext(item) }
            CatalogAction.AddToQueue -> if (item is SongItem) scope.launch { runtime.addToQueue(item) }
            CatalogAction.Download ->
                when (item) {
                    is SongItem -> onDownload(item)
                    is PlaylistItem -> onDownloadPlaylist?.invoke(item)
                    else -> Unit
                }
            CatalogAction.RemoveDownload -> if (item is SongItem) runtime.remove(item.id)
            CatalogAction.OpenAlbum -> if (item is AlbumItem) onOpenAlbum(item)
            CatalogAction.OpenArtist -> if (item is ArtistItem) onOpenArtist(item)
            CatalogAction.CopyLink -> copyTextToClipboard(catalogItemLink(target, item.id))
            else -> Unit
        }
    }

    CatalogContextArea(
        actions = actions,
        onAction = handleAction,
    ) {
        Surface(
            onClick = openPrimary,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .hoverable(hoverInteraction),
            shape = MaterialTheme.shapes.medium,
            color =
                if (hovered) {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else {
                    Color.Transparent
                },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArtworkThumbnail(
                    runtime = runtime,
                    thumbnailUrl = item.thumbnail,
                    size = 48.dp,
                    contentDescription = item.title,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                    )
                    Text(
                        catalogSubtitle(item),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    if (item is SongItem && downloadRecord != null) {
                        DownloadInlineStatus(downloadRecord)
                    }
                }

                if (item is SongItem && downloadRecord?.state == OfflineState.COMPLETED) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Downloaded",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                } else if (item is SongItem && hovered && canStartDownload(downloadRecord)) {
                    IconButton(
                        onClick = { onDownload(item) },
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = "Download")
                    }
                }

                if (hovered) {
                    CatalogActionMenu(actions = actions, onAction = handleAction)
                }
            }
        }
    }
}

@Composable
internal fun SongRow(
    runtime: DesktopRuntime,
    song: SongItem,
    downloadRecord: OfflineRecord?,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val hoverInteraction = remember { MutableInteractionSource() }
    val hovered by hoverInteraction.collectIsHoveredAsState()
    val target =
        if (onRemoveFromPlaylist == null) {
            CatalogActionTarget.SONG
        } else {
            CatalogActionTarget.LOCAL_PLAYLIST_SONG
        }
    val actions =
        availableCatalogActions(
            target = target,
            capabilities =
                CatalogActionCapabilities(
                    canPlayNext = true,
                    canAddToQueue = true,
                    canDownload = true,
                    downloadCompleted = downloadRecord?.state == OfflineState.COMPLETED,
                    canCopyLink = true,
                    canRemoveFromLocalPlaylist = onRemoveFromPlaylist != null,
                ),
        )
    val handleAction: (CatalogAction) -> Unit = { action ->
        when (action) {
            CatalogAction.PlayNext -> scope.launch { runtime.playNext(song) }
            CatalogAction.AddToQueue -> scope.launch { runtime.addToQueue(song) }
            CatalogAction.Download -> onDownload()
            CatalogAction.RemoveDownload -> runtime.remove(song.id)
            CatalogAction.CopyLink -> copyTextToClipboard(catalogItemLink(target, song.id))
            CatalogAction.RemoveFromLocalPlaylist -> onRemoveFromPlaylist?.invoke()
            else -> Unit
        }
    }

    CatalogContextArea(
        actions = actions,
        onAction = handleAction,
    ) {
        Surface(
            onClick = onPlay,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .hoverable(hoverInteraction),
            shape = MaterialTheme.shapes.medium,
            color =
                if (hovered) {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else {
                    Color.Transparent
                },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArtworkThumbnail(
                    runtime = runtime,
                    thumbnailUrl = song.thumbnail,
                    size = 48.dp,
                    contentDescription = song.title,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        song.title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                    )
                    Text(
                        song.artists.joinToString(", ") { it.name }.ifBlank { "YouTube Music" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    downloadRecord?.let { DownloadInlineStatus(it) }
                }

                if (downloadRecord?.state == OfflineState.COMPLETED) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Downloaded",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                } else if (hovered && canStartDownload(downloadRecord)) {
                    IconButton(
                        onClick = onDownload,
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = "Download")
                    }
                }

                if (hovered) {
                    onMoveUp?.let { moveUp ->
                        IconButton(
                            onClick = moveUp,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move up")
                        }
                    }
                    onMoveDown?.let { moveDown ->
                        IconButton(
                            onClick = moveDown,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(Icons.Rounded.ArrowDownward, contentDescription = "Move down")
                        }
                    }
                    CatalogActionMenu(actions = actions, onAction = handleAction)
                }
            }
        }
    }
}

private fun catalogActionTarget(item: YTItem): CatalogActionTarget =
    when (item) {
        is SongItem -> CatalogActionTarget.SONG
        is AlbumItem -> CatalogActionTarget.ALBUM
        is ArtistItem -> CatalogActionTarget.ARTIST
        is PlaylistItem -> CatalogActionTarget.YOUTUBE_PLAYLIST
        else -> CatalogActionTarget.SONG
    }

private fun catalogActionCapabilities(
    item: YTItem,
    downloadRecord: OfflineRecord?,
): CatalogActionCapabilities =
    when (item) {
        is SongItem ->
            CatalogActionCapabilities(
                canPlayNext = true,
                canAddToQueue = true,
                canDownload = true,
                downloadCompleted = downloadRecord?.state == OfflineState.COMPLETED,
                canCopyLink = true,
            )

        is AlbumItem -> CatalogActionCapabilities(canOpenAlbum = true, canCopyLink = true)
        is ArtistItem -> CatalogActionCapabilities(canOpenArtist = true, canCopyLink = true)
        is PlaylistItem -> CatalogActionCapabilities(canCopyLink = true)
        else -> CatalogActionCapabilities()
    }
