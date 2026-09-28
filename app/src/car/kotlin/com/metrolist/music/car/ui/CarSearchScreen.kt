package com.metrolist.music.car.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.R
import com.metrolist.music.car.importer.CarArtworkStore
import com.metrolist.music.car.library.CarLibrarySession
import com.metrolist.music.car.library.CarUnifiedCatalog
import com.metrolist.music.car.library.CarUnifiedPlaybackResolver
import com.metrolist.music.car.library.CarUnifiedQueueResolution
import com.metrolist.music.car.library.CarUnifiedSong
import com.metrolist.music.playback.PlayerConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CarSearchScreen(
    catalog: List<CarUnifiedSong>,
    carLibrarySession: CarLibrarySession,
    connection: PlayerConnection?,
    viewport: CarViewportMetrics,
    onOpenNowPlaying: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val currentMetadata by
        connection?.mediaMetadata?.collectAsStateWithLifecycle()
            ?: remember { mutableStateOf(null) }
    val resolver =
        remember(context, carLibrarySession) {
            CarUnifiedPlaybackResolver(
                carLibraryResolver = carLibrarySession.mediaResolver,
                legacyArtworkStore = CarArtworkStore(context.filesDir),
            )
        }

    var query by remember { mutableStateOf("") }
    var resolvingSongId by remember { mutableStateOf<String?>(null) }

    val results =
        remember(catalog, query) {
            CarUnifiedCatalog.search(catalog, query)
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(viewport.pxToDp(32f, density)),
        verticalArrangement = Arrangement.spacedBy(viewport.pxToDp(14f, density)),
    ) {
        Text(
            text = stringResource(R.string.car_search),
            style = MaterialTheme.typography.headlineLarge,
        )
        Text(
            text = stringResource(R.string.car_search_unified_desc),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.car_search_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (query.isNotBlank() && results.isEmpty()) {
            Text(
                text = stringResource(R.string.car_search_no_results),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(viewport.pxToDp(8f, density)),
        ) {
            itemsIndexed(
                items = results,
                key = { _, song -> song.id },
            ) { resultIndex, song ->
                val isCurrent = currentMetadata?.id == song.id
                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(
                                enabled = connection != null && resolvingSongId == null,
                            ) {
                                val playerConnection = connection ?: return@clickable
                                resolvingSongId = song.id
                                scope.launch {
                                    val resolution =
                                        withContext(Dispatchers.IO) {
                                            resolver.resolveQueue(
                                                songs = results,
                                                startIndex = resultIndex,
                                                title = "Search",
                                            )
                                        }
                                    resolvingSongId = null
                                    if (resolution is CarUnifiedQueueResolution.Ready) {
                                        playerConnection.playQueue(resolution.queue)
                                        onOpenNowPlaying()
                                    }
                                }
                            },
                    shape = MaterialTheme.shapes.large,
                    tonalElevation = viewport.pxToDp(if (isCurrent) 5f else 1f, density),
                ) {
                    Column(
                        modifier = Modifier.padding(viewport.pxToDp(18f, density)),
                    ) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.titleMedium,
                            color =
                                if (isCurrent) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            maxLines = 1,
                        )
                        val artist = song.artists.joinToString(", ").ifBlank { song.album.orEmpty() }
                        if (artist.isNotBlank()) {
                            Text(
                                text = artist,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}