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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import com.metrolist.music.R
import com.metrolist.music.car.library.AndroidCarLibraryTreeAccessFactory
import com.metrolist.music.car.library.CarLibraryIndex
import com.metrolist.music.car.library.CarLibraryMediaResolver
import com.metrolist.music.car.library.CarLibraryOpenResult
import com.metrolist.music.car.library.CarLibraryQueueResolution
import com.metrolist.music.car.library.CarLibraryRepository
import com.metrolist.music.car.library.SharedPreferencesCarLibraryLocationStore
import com.metrolist.music.playback.PlayerConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CarSearchScreen(
    connection: PlayerConnection?,
    viewport: CarViewportMetrics,
    onOpenNowPlaying: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val density = LocalDensity.current.density
    val repository =
        remember(context) {
            CarLibraryRepository(
                locationStore = SharedPreferencesCarLibraryLocationStore(context),
                accessFactory = AndroidCarLibraryTreeAccessFactory(context),
            )
        }
    val resolver = remember(repository) { CarLibraryMediaResolver(repository) }

    var index by remember { mutableStateOf<CarLibraryIndex?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(repository) {
        when (val result = withContext(Dispatchers.IO) { repository.reopen() }) {
            is CarLibraryOpenResult.Opened -> {
                index = result.index
                status = null
            }
            is CarLibraryOpenResult.Unavailable -> {
                index = null
                status = result.reason
            }
            is CarLibraryOpenResult.Invalid -> {
                index = null
                status = result.errors.joinToString(" · ")
            }
        }
    }

    val results =
        remember(index, query) {
            val normalized = query.trim()
            if (normalized.isBlank()) {
                emptyList()
            } else {
                index
                    ?.songs
                    .orEmpty()
                    .filter { song ->
                        song.title.contains(normalized, ignoreCase = true) ||
                            song.artists.any { it.name.contains(normalized, ignoreCase = true) } ||
                            song.album?.name?.contains(normalized, ignoreCase = true) == true
                    }
            }
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
            text = stringResource(R.string.car_search_local_desc),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.car_search_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        status?.let {
            Text(
                text = stringResource(R.string.car_search_no_library),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (query.isNotBlank() && results.isEmpty() && index != null) {
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
                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = connection != null) {
                                val resolution =
                                    resolver.resolveQueue(
                                        songIds = results.map { it.id },
                                        startIndex = resultIndex,
                                        title = "Search",
                                    )
                                if (resolution is CarLibraryQueueResolution.Ready) {
                                    connection?.playQueue(resolution.queue)
                                    onOpenNowPlaying()
                                }
                            },
                    shape = MaterialTheme.shapes.large,
                    tonalElevation = viewport.pxToDp(1f, density),
                ) {
                    Column(
                        modifier = Modifier.padding(viewport.pxToDp(16f, density)),
                    ) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                        )
                        val artist =
                            song.artists
                                .joinToString(", ") { it.name }
                                .ifBlank { song.album?.name.orEmpty() }
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