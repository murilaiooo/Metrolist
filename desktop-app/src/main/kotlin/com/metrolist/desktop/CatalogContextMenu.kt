package com.metrolist.desktop

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

sealed interface CatalogAction {
    data object PlayNext : CatalogAction
    data object AddToQueue : CatalogAction
    data object Download : CatalogAction
    data object RemoveDownload : CatalogAction
    data object AddToLocalPlaylist : CatalogAction
    data object OpenAlbum : CatalogAction
    data object OpenArtist : CatalogAction
    data object CopyLink : CatalogAction
    data object RemoveFromLocalPlaylist : CatalogAction
}

enum class CatalogActionTarget {
    SONG,
    ALBUM,
    ARTIST,
    YOUTUBE_PLAYLIST,
    LOCAL_PLAYLIST_SONG,
}

data class CatalogActionCapabilities(
    val canPlayNext: Boolean = false,
    val canAddToQueue: Boolean = false,
    val canDownload: Boolean = false,
    val downloadCompleted: Boolean = false,
    val canAddToLocalPlaylist: Boolean = false,
    val canOpenAlbum: Boolean = false,
    val canOpenArtist: Boolean = false,
    val canCopyLink: Boolean = false,
    val canRemoveFromLocalPlaylist: Boolean = false,
)

internal fun availableCatalogActions(
    target: CatalogActionTarget,
    capabilities: CatalogActionCapabilities,
): List<CatalogAction> =
    buildList {
        if (target == CatalogActionTarget.SONG || target == CatalogActionTarget.LOCAL_PLAYLIST_SONG) {
            if (capabilities.canPlayNext) add(CatalogAction.PlayNext)
            if (capabilities.canAddToQueue) add(CatalogAction.AddToQueue)
            if (capabilities.canDownload) {
                add(if (capabilities.downloadCompleted) CatalogAction.RemoveDownload else CatalogAction.Download)
            }
            if (capabilities.canAddToLocalPlaylist) add(CatalogAction.AddToLocalPlaylist)
            if (capabilities.canOpenAlbum) add(CatalogAction.OpenAlbum)
            if (capabilities.canOpenArtist) add(CatalogAction.OpenArtist)
        }

        if (target == CatalogActionTarget.ALBUM && capabilities.canOpenAlbum) {
            add(CatalogAction.OpenAlbum)
        }
        if (target == CatalogActionTarget.ARTIST && capabilities.canOpenArtist) {
            add(CatalogAction.OpenArtist)
        }
        if (target == CatalogActionTarget.YOUTUBE_PLAYLIST && capabilities.canDownload) {
            add(CatalogAction.Download)
        }
        if (capabilities.canCopyLink) add(CatalogAction.CopyLink)
        if (target == CatalogActionTarget.LOCAL_PLAYLIST_SONG && capabilities.canRemoveFromLocalPlaylist) {
            add(CatalogAction.RemoveFromLocalPlaylist)
        }
    }

@Composable
internal fun CatalogContextArea(
    actions: List<CatalogAction>,
    onAction: (CatalogAction) -> Unit,
    content: @Composable () -> Unit,
) {
    if (actions.isEmpty()) {
        content()
        return
    }

    ContextMenuArea(
        items = {
            actions.map { action ->
                ContextMenuItem(
                    label = catalogActionLabel(action),
                    onClick = { onAction(action) },
                )
            }
        },
        content = content,
    )
}

internal fun shouldRenderCatalogActionMenu(
    hovered: Boolean,
    expanded: Boolean,
): Boolean = hovered || expanded

@Composable
internal fun CatalogActionMenu(
    actions: List<CatalogAction>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onAction: (CatalogAction) -> Unit,
) {
    if (actions.isEmpty()) return

    Box {
        IconButton(onClick = { onExpandedChange(true) }) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "More actions",
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(catalogActionLabel(action)) },
                    leadingIcon = {
                        Icon(
                            imageVector = catalogActionIcon(action),
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        onExpandedChange(false)
                        onAction(action)
                    },
                )
            }
        }
    }
}

internal fun catalogItemLink(
    target: CatalogActionTarget,
    id: String,
): String =
    when (target) {
        CatalogActionTarget.SONG,
        CatalogActionTarget.LOCAL_PLAYLIST_SONG,
        -> "https://music.youtube.com/watch?v=$id"

        CatalogActionTarget.YOUTUBE_PLAYLIST -> "https://music.youtube.com/playlist?list=$id"
        CatalogActionTarget.ALBUM,
        CatalogActionTarget.ARTIST,
        -> "https://music.youtube.com/browse/$id"
    }

internal fun copyTextToClipboard(text: String) {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
}

internal fun catalogActionLabel(action: CatalogAction): String =
    when (action) {
        CatalogAction.PlayNext -> "Play next"
        CatalogAction.AddToQueue -> "Add to queue"
        CatalogAction.Download -> "Download"
        CatalogAction.RemoveDownload -> "Remove download"
        CatalogAction.AddToLocalPlaylist -> "Add to local playlist"
        CatalogAction.OpenAlbum -> "Open album"
        CatalogAction.OpenArtist -> "Open artist"
        CatalogAction.CopyLink -> "Copy link"
        CatalogAction.RemoveFromLocalPlaylist -> "Remove from playlist"
    }

private fun catalogActionIcon(action: CatalogAction): ImageVector =
    when (action) {
        CatalogAction.PlayNext -> Icons.AutoMirrored.Rounded.QueueMusic
        CatalogAction.AddToQueue -> Icons.AutoMirrored.Rounded.PlaylistAdd
        CatalogAction.Download -> Icons.Rounded.Download
        CatalogAction.RemoveDownload -> Icons.Rounded.DeleteOutline
        CatalogAction.AddToLocalPlaylist -> Icons.AutoMirrored.Rounded.PlaylistAdd
        CatalogAction.OpenAlbum -> Icons.Rounded.Album
        CatalogAction.OpenArtist -> Icons.Rounded.Person
        CatalogAction.CopyLink -> Icons.Rounded.Link
        CatalogAction.RemoveFromLocalPlaylist -> Icons.Rounded.DeleteOutline
    }
