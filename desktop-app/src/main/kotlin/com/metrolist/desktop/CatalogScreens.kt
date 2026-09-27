package com.metrolist.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.metrolist.desktop.library.LocalPlaylist
import com.metrolist.desktop.offline.OfflineRecord
import com.metrolist.desktop.offline.OfflineState
import com.metrolist.desktop.player.PlayerState
import com.metrolist.desktop.player.RepeatMode
import com.metrolist.desktop.portable.InsufficientExportSpaceException
import com.metrolist.desktop.portable.PortableExportEstimate
import com.metrolist.desktop.portable.PortableExportPhase
import com.metrolist.desktop.portable.PortableExportProgress
import com.metrolist.desktop.portable.PortableValidationException
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage

@Composable
internal fun ArtworkThumbnail(
    runtime: DesktopRuntime,
    thumbnailUrl: String?,
    size: Dp,
    contentDescription: String?,
) {
    val bitmap by
        produceState<ImageBitmap?>(
            initialValue = null,
            key1 = thumbnailUrl,
        ) {
            value =
                withContext(Dispatchers.IO) {
                    runtime.ensureArtwork(thumbnailUrl)?.let { path ->
                        runCatching {
                            SkiaImage
                                .makeFromEncoded(Files.readAllBytes(path))
                                .toComposeImageBitmap()
                        }.getOrNull()
                    }
                }
        }

    Surface(
        modifier = Modifier.size(size),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun HomeView(
    runtime: DesktopRuntime,
    ui: DesktopUiState,
    downloadsById: Map<String, OfflineRecord>,
    onOpenPlaylist: (PlaylistItem) -> Unit,
    onOpenAlbum: (AlbumItem) -> Unit,
    onOpenArtist: (ArtistItem) -> Unit,
    onPlay: (SongItem) -> Unit,
    onDownload: (SongItem) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item {
            Column {
                Text("Home", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Your music and recommendations",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ui.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text("Error: $it", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        ui.homeSections.forEach { section ->
            item(key = "home:" + section.title) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        section.title,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().height(228.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(end = 12.dp),
                    ) {
                        items(
                            items = section.items,
                            key = { item -> section.title + ":" + item::class.simpleName + ":" + item.id },
                        ) { item ->
                            HomeMediaCard(
                                runtime = runtime,
                                item = item,
                                onOpen = {
                                    when (item) {
                                        is SongItem -> onPlay(item)
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
            }
        }
    }
}

@Composable
private fun HomeMediaCard(
    runtime: DesktopRuntime,
    item: YTItem,
    onOpen: () -> Unit,
) {
    val hoverInteraction = remember { MutableInteractionSource() }
    val hovered by hoverInteraction.collectIsHoveredAsState()

    Surface(
        onClick = onOpen,
        modifier =
            Modifier
                .width(176.dp)
                .hoverable(hoverInteraction),
        shape = MaterialTheme.shapes.large,
        color =
            if (hovered) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                Color.Transparent
            },
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            ArtworkThumbnail(
                runtime = runtime,
                thumbnailUrl = item.thumbnail,
                size = 160.dp,
                contentDescription = item.title,
            )
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
    }
}

@Composable
internal fun SearchView(
    runtime: DesktopRuntime,
    ui: DesktopUiState,
    downloadsById: Map<String, OfflineRecord>,
    onSearch: (String) -> Unit,
    onOpenPlaylist: (PlaylistItem) -> Unit,
    onOpenAlbum: (AlbumItem) -> Unit,
    onOpenArtist: (ArtistItem) -> Unit,
    onPlay: (SongItem) -> Unit,
    onDownload: (SongItem) -> Unit,
    focusSearchRequestKey: Int = 0,
) {
    var query by remember(ui.searchQuery) { mutableStateOf(ui.searchQuery) }
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(focusSearchRequestKey) {
        if (focusSearchRequestKey > 0) searchFocusRequester.requestFocus()
    }

    Column(Modifier.fillMaxSize()) {
        Text("Search", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            "Find songs, artists, albums, and playlists",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("What do you want to listen to?") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                )
            },
            trailingIcon = {
                IconButton(
                    onClick = { onSearch(query) },
                    enabled = query.isNotBlank(),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search",
                    )
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(searchFocusRequester)
                    .trackTextInputFocus(),
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
        )

        ui.error?.let {
            Spacer(Modifier.height(8.dp))
            Text("Error: $it", color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ui.searchSections.forEach { section ->
                item(key = "search-section:" + section.title) {
                    Text(
                        section.title,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(
                    items = section.items,
                    key = { item -> "search:" + section.title + ":" + item::class.simpleName + ":" + item.id },
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
        }
    }
}

@Composable
internal fun LibraryView(
    runtime: DesktopRuntime,
    ui: DesktopUiState,
    downloadsById: Map<String, OfflineRecord>,
    localPlaylists: List<LocalPlaylist>,
    onCreateImport: () -> Unit,
    onOpenLocalPlaylist: (LocalPlaylist) -> Unit,
    onRefresh: () -> Unit,
    onSelectTab: (LibraryTab) -> Unit,
    onOpenPlaylist: (PlaylistItem) -> Unit,
    onOpenAlbum: (AlbumItem) -> Unit,
    onOpenArtist: (ArtistItem) -> Unit,
    onPlay: (SongItem) -> Unit,
    onDownload: (SongItem) -> Unit,
    onDownloadPlaylist: (PlaylistItem) -> Unit,
) {
    var presentationMode by
        remember(ui.libraryTab) {
            mutableStateOf(runtime.libraryPresentationMode(ui.libraryTab))
        }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Library",
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    "Your playlists, songs, albums, and artists",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onCreateImport) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                    contentDescription = null,
                )
                Spacer(Modifier.width(6.dp))
                Text("Create / Import")
            }
            Spacer(Modifier.width(6.dp))
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Refresh library",
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                LibraryTabButton(
                    label = "Playlists " + (ui.libraryPlaylists.size + localPlaylists.size),
                    selected = ui.libraryTab == LibraryTab.PLAYLISTS,
                    onClick = { onSelectTab(LibraryTab.PLAYLISTS) },
                )
                LibraryTabButton(
                    label = "Songs " + ui.librarySongs.size,
                    selected = ui.libraryTab == LibraryTab.SONGS,
                    onClick = { onSelectTab(LibraryTab.SONGS) },
                )
                LibraryTabButton(
                    label = "Albums " + ui.libraryAlbums.size,
                    selected = ui.libraryTab == LibraryTab.ALBUMS,
                    onClick = { onSelectTab(LibraryTab.ALBUMS) },
                )
                LibraryTabButton(
                    label = "Artists " + ui.libraryArtists.size,
                    selected = ui.libraryTab == LibraryTab.ARTISTS,
                    onClick = { onSelectTab(LibraryTab.ARTISTS) },
                )
            }

            Spacer(Modifier.weight(1f))

            if (supportsGrid(ui.libraryTab)) {
                IconButton(
                    onClick = {
                        presentationMode = PresentationMode.LIST
                        runtime.setLibraryPresentationMode(ui.libraryTab, PresentationMode.LIST)
                    },
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ViewList,
                        contentDescription = "List view",
                        tint =
                            if (presentationMode == PresentationMode.LIST) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
                IconButton(
                    onClick = {
                        presentationMode = PresentationMode.GRID
                        runtime.setLibraryPresentationMode(ui.libraryTab, PresentationMode.GRID)
                    },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GridView,
                        contentDescription = "Grid view",
                        tint =
                            if (presentationMode == PresentationMode.GRID) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        ui.error?.let {
            Spacer(Modifier.height(8.dp))
            Text("Error: $it", color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(12.dp))

        if (presentationMode == PresentationMode.GRID) {
            when (ui.libraryTab) {
                LibraryTab.PLAYLISTS ->
                    PlaylistLibraryGrid(
                        runtime = runtime,
                        localPlaylists = localPlaylists,
                        remotePlaylists = ui.libraryPlaylists,
                        onOpenLocalPlaylist = onOpenLocalPlaylist,
                        onOpenPlaylist = onOpenPlaylist,
                        onDownloadPlaylist = onDownloadPlaylist,
                    )

                LibraryTab.ALBUMS ->
                    CatalogCollectionGrid(
                        runtime = runtime,
                        items = ui.libraryAlbums,
                        onOpenPlaylist = onOpenPlaylist,
                        onOpenAlbum = onOpenAlbum,
                        onOpenArtist = onOpenArtist,
                    )

                LibraryTab.ARTISTS ->
                    CatalogCollectionGrid(
                        runtime = runtime,
                        items = ui.libraryArtists,
                        onOpenPlaylist = onOpenPlaylist,
                        onOpenAlbum = onOpenAlbum,
                        onOpenArtist = onOpenArtist,
                    )

                LibraryTab.SONGS -> Unit
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (ui.libraryTab) {
                LibraryTab.PLAYLISTS -> {
                    if (localPlaylists.isNotEmpty()) {
                        item(key = "local-playlists-heading") {
                            Text(
                                "Local playlists",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        items(localPlaylists, key = { "local:" + it.id }) { playlist ->
                            val hoverInteraction =
                                remember(playlist.id) { MutableInteractionSource() }
                            val hovered by hoverInteraction.collectIsHoveredAsState()
                            Surface(
                                onClick = { onOpenLocalPlaylist(playlist) },
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
                                    Surface(
                                        modifier = Modifier.size(48.dp),
                                        shape = MaterialTheme.shapes.medium,
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Rounded.LibraryMusic,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            playlist.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                        Text(
                                            if (playlist.youtubePlaylistId == null) {
                                                "Local playlist"
                                            } else {
                                                "Local · YouTube Music"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                        item(key = "youtube-playlists-heading") {
                            Text(
                                "YouTube Music playlists",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }

                    items(ui.libraryPlaylists, key = PlaylistItem::id) { playlist ->
                        CatalogItemRow(
                            runtime = runtime,
                            item = playlist,
                            downloadRecord = null,
                            onOpenPlaylist = onOpenPlaylist,
                            onOpenAlbum = onOpenAlbum,
                            onOpenArtist = onOpenArtist,
                            onPlay = onPlay,
                            onDownload = onDownload,
                            onDownloadPlaylist = onDownloadPlaylist,
                        )
                    }
                }

                LibraryTab.SONGS -> {
                    items(ui.librarySongs, key = SongItem::id) { song ->
                        SongRow(
                            runtime = runtime,
                            song = song,
                            downloadRecord = downloadsById[song.id],
                            onPlay = { onPlay(song) },
                            onDownload = { onDownload(song) },
                        )
                    }
                }

                LibraryTab.ALBUMS -> {
                    items(ui.libraryAlbums, key = AlbumItem::id) { album ->
                        CatalogItemRow(
                            runtime = runtime,
                            item = album,
                            downloadRecord = null,
                            onOpenPlaylist = onOpenPlaylist,
                            onOpenAlbum = onOpenAlbum,
                            onOpenArtist = onOpenArtist,
                            onPlay = onPlay,
                            onDownload = onDownload,
                        )
                    }
                }

                LibraryTab.ARTISTS -> {
                    items(ui.libraryArtists, key = ArtistItem::id) { artist ->
                        CatalogItemRow(
                            runtime = runtime,
                            item = artist,
                            downloadRecord = null,
                            onOpenPlaylist = onOpenPlaylist,
                            onOpenAlbum = onOpenAlbum,
                            onOpenArtist = onOpenArtist,
                            onPlay = onPlay,
                            onDownload = onDownload,
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
internal fun LibraryTabButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        colors =
            ButtonDefaults.textButtonColors(
                containerColor =
                    if (selected) {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    } else {
                        Color.Transparent
                    },
                contentColor =
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            ),
    ) {
        Text(label)
    }
}

@Composable
internal fun CatalogDetailView(
    runtime: DesktopRuntime,
    detail: CatalogDetail,
    downloadsById: Map<String, OfflineRecord>,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onOpenPlaylist: (PlaylistItem) -> Unit,
    onOpenAlbum: (AlbumItem) -> Unit,
    onOpenArtist: (ArtistItem) -> Unit,
    onPlay: (SongItem) -> Unit,
    onDownload: (SongItem) -> Unit,
) {
    val detailSongs =
        remember(detail) {
            detail.sections
                .flatMap(CatalogDetailSection::items)
                .filterIsInstance<SongItem>()
        }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                )
            }
            Spacer(Modifier.width(12.dp))
            ArtworkThumbnail(
                runtime = runtime,
                thumbnailUrl = detail.thumbnail,
                size = 140.dp,
                contentDescription = detail.title,
            )
            Spacer(Modifier.width(20.dp))
            Column(
                modifier = Modifier.weight(1f).padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    detail.title,
                    style = MaterialTheme.typography.headlineLarge,
                )
                detail.subtitle?.takeIf(String::isNotBlank)?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                detail.description?.takeIf(String::isNotBlank)?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (detailSongs.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Button(onClick = { onPlay(detailSongs.first()) }) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Play")
                    }
                }
            }
        }

        if (loading) {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                "Error: " + it,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            detail.sections.forEachIndexed { sectionIndex, section ->
                item(key = "detail-section:" + sectionIndex + ":" + section.title) {
                    Text(
                        section.title,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(
                    items = section.items,
                    key = { item ->
                        "detail:" + sectionIndex + ":" +
                            item::class.simpleName + ":" + item.id
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
        }
    }
}

@Composable
internal fun PlaylistView(
    runtime: DesktopRuntime,
    title: String,
    songs: List<SongItem>,
    downloadsById: Map<String, OfflineRecord>,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onPlay: (SongItem) -> Unit,
    onDownload: (SongItem) -> Unit,
    onDownloadAll: () -> Unit,
    onCancelBatch: () -> Unit,
    onRetryFailed: () -> Unit,
    onRemoveDownloaded: () -> Unit,
) {
    val summary = summarizePlaylistDownloads(songs, downloadsById)
    var confirmRemoveDownloaded by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineLarge)
                Text(
                    "${songs.size} tracks · ${summary.completed} downloaded" +
                        if (summary.active > 0) " · ${summary.active} active" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Button(
                onClick = { songs.firstOrNull()?.let(onPlay) },
                enabled = songs.isNotEmpty(),
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                )
                Spacer(Modifier.width(6.dp))
                Text("Play")
            }

            Spacer(Modifier.width(6.dp))

            TextButton(
                onClick = onDownloadAll,
                enabled =
                    !loading &&
                        songs.isNotEmpty() &&
                        !summary.allCompleted &&
                        summary.active == 0,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = null,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    when {
                        summary.allCompleted -> "Downloaded"
                        summary.completed > 0 || summary.failed > 0 -> "Download remaining"
                        else -> "Download all"
                    },
                )
            }
        }

        if (summary.total > 0 && summary.tracked > 0) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { summary.progressFraction },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (summary.failed > 0 || summary.active > 0 || summary.completed > 0) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (summary.failed > 0) {
                    TextButton(onClick = onRetryFailed) {
                        Text("Retry failed ${summary.failed}")
                    }
                }

                if (summary.active > 0) {
                    TextButton(onClick = onCancelBatch) {
                        Text("Cancel downloads ${summary.active}")
                    }
                }

                if (summary.completed > 0) {
                    TextButton(onClick = { confirmRemoveDownloaded = true }) {
                        Text("Remove offline ${summary.completed}")
                    }
                }
            }
        }

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                "Error: $it",
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(12.dp))

        when {
            loading && songs.isEmpty() ->
                Text("Loading playlist...")

            !loading && error == null && songs.isEmpty() ->
                Text("No songs found in this playlist.")

            else ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(songs, key = SongItem::id) { song ->
                        SongRow(
                            runtime = runtime,
                            song = song,
                            downloadRecord = downloadsById[song.id],
                            onPlay = { onPlay(song) },
                            onDownload = { onDownload(song) },
                        )
                    }
                }
        }
    }

    if (confirmRemoveDownloaded) {
        AlertDialog(
            onDismissRequest = { confirmRemoveDownloaded = false },
            title = { Text("Remove downloaded songs?") },
            text = {
                Text(
                    "This removes ${summary.completed} downloaded songs from offline storage. " +
                        "They stay in the playlist.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmRemoveDownloaded = false
                        onRemoveDownloaded()
                    },
                ) {
                    Text("Remove downloaded")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmRemoveDownloaded = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}
