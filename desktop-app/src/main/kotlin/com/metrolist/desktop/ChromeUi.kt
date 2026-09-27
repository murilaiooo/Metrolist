package com.metrolist.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
internal fun LoginPanel(
    accountError: String?,
    onLogin: () -> Unit,
    onAdvancedSession: (String) -> Unit,
    message: String?,
) {
    var showAdvanced by remember { mutableStateOf(false) }
    var advancedSessionText by remember { mutableStateOf("") }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 720.dp),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 2.dp,
            shadowElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier.padding(36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
        Text(
            "MetroList Windows",
            style = MaterialTheme.typography.headlineLarge,
        )
        Spacer(Modifier.height(12.dp))
        Text("Sign in to YouTube Music to browse, play, and download your library.")
        Spacer(Modifier.height(20.dp))
        Button(onClick = onLogin) {
            Icon(
                imageVector = Icons.Rounded.AccountCircle,
                contentDescription = null,
            )
            Spacer(Modifier.width(8.dp))
            Text("Sign in with YouTube Music")
        }

        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = { showAdvanced = !showAdvanced }) {
            Icon(
                imageVector = Icons.Rounded.Key,
                contentDescription = null,
            )
            Spacer(Modifier.width(8.dp))
            Text(if (showAdvanced) "Hide Advanced Session" else "Advanced Session")
        }

        if (showAdvanced) {
            Spacer(Modifier.height(14.dp))
            Column(
                modifier = Modifier.width(620.dp),
            ) {
                Text(
                    "Use this only if normal sign-in does not work.",
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Paste the complete Advanced Session block from MetroList. " +
                        "It contains private account credentials. MetroList Windows validates it first " +
                        "and only then stores the session encrypted with Windows DPAPI.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = advancedSessionText,
                    onValueChange = { advancedSessionText = it },
                    label = { Text("Advanced Session") },
                    placeholder = { Text("***INNERTUBE COOKIE*** = ...") },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .trackTextInputFocus(),
                    singleLine = false,
                    maxLines = 12,
                    visualTransformation = PasswordVisualTransformation(),
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            val submitted = advancedSessionText
                            advancedSessionText = ""
                            onAdvancedSession(submitted)
                        },
                        enabled = advancedSessionText.isNotBlank(),
                    ) {
                        Text("Validate and sign in")
                    }
                    OutlinedButton(
                        onClick = { advancedSessionText = "" },
                        enabled = advancedSessionText.isNotEmpty(),
                    ) {
                        Text("Clear")
                    }
                }
            }
        }

        accountError?.let {
            Spacer(Modifier.height(12.dp))
            Text(
                "Session error: $it",
                color = MaterialTheme.colorScheme.error,
            )
        }
        message?.takeIf(String::isNotBlank)?.let {
            Spacer(Modifier.height(8.dp))
            Text(it)
        }
            }
        }
    }
}

@Composable
internal fun NavigationPanel(
    accountLabel: String,
    destination: AppDestination,
    onNavigate: (AppDestination) -> Unit,
    onLogout: () -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .width(196.dp)
                .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 14.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(32.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.LibraryMusic,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "MetroList",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Windows",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            AppDestination.entries.forEach { item ->
                val label =
                    when (item) {
                        AppDestination.HOME -> "Home"
                        AppDestination.EXPLORE -> "Explore"
                        AppDestination.SEARCH -> "Search"
                        AppDestination.LIBRARY -> "Library"
                        AppDestination.DOWNLOADS -> "Downloads"
                    }
                val icon =
                    when (item) {
                        AppDestination.HOME -> Icons.Rounded.Home
                        AppDestination.EXPLORE -> Icons.Rounded.Explore
                        AppDestination.SEARCH -> Icons.Rounded.Search
                        AppDestination.LIBRARY -> Icons.Rounded.LibraryMusic
                        AppDestination.DOWNLOADS -> Icons.Rounded.Download
                    }
                val selected = destination == item

                Surface(
                    onClick = { onNavigate(item) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color =
                        if (selected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            modifier = Modifier.size(20.dp),
                            tint =
                                if (selected) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyMedium,
                            color =
                                if (selected) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            Spacer(Modifier.weight(1f))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.AccountCircle,
                    contentDescription = "Account",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        accountLabel,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "YouTube Music",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onLogout) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Logout,
                        contentDescription = "Sign out",
                    )
                }
            }
        }
    }
}
