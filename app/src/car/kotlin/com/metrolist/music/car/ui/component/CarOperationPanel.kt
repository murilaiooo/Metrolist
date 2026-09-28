package com.metrolist.music.car.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.metrolist.music.car.feedback.CarOperationProgress
import com.metrolist.music.car.feedback.CarOperationState
import java.util.Locale

@Composable
fun CarOperationPanel(
    state: CarOperationState,
    modifier: Modifier = Modifier,
) {
    if (state is CarOperationState.Idle) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (state) {
                CarOperationState.Idle -> Unit
                is CarOperationState.Running -> RunningOperation(state.progress)
                is CarOperationState.Success -> {
                    Text("Complete", style = MaterialTheme.typography.titleLarge)
                    Text(state.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is CarOperationState.Error -> {
                    Text("Could not complete operation", style = MaterialTheme.typography.titleLarge)
                    Text(state.message)
                    state.actionHint?.takeIf(String::isNotBlank)?.let {
                        Text(it, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun RunningOperation(progress: CarOperationProgress) {
    Text(
        progress.stage.name
            .lowercase()
            .replace('_', ' ')
            .replaceFirstChar(Char::uppercase),
        style = MaterialTheme.typography.titleLarge,
    )
    progress.detail?.takeIf(String::isNotBlank)?.let {
        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    val fraction = progress.fraction
    if (fraction == null) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    } else {
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    val details = formatCarOperationDetails(progress)
    if (details.isNotEmpty()) {
        Text(
            details,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun formatCarOperationDetails(progress: CarOperationProgress): String =
    buildList {
        if (progress.completedItems != null && progress.totalItems != null) {
            add("${progress.completedItems}/${progress.totalItems} items")
        }
        if (progress.bytesProcessed != null) {
            add(
                if (progress.totalBytes != null && progress.totalBytes > 0L) {
                    "${humanBytes(progress.bytesProcessed)} / ${humanBytes(progress.totalBytes)}"
                } else {
                    humanBytes(progress.bytesProcessed)
                },
            )
        }
        progress.fraction?.let { add("${(it * 100f).toInt()}%") }
        progress.bytesPerSecond?.takeIf { it > 0L }?.let {
            add("${humanBytes(it)}/s")
        }
    }.joinToString(" · ")

private fun humanBytes(bytes: Long): String {
    val value = bytes.coerceAtLeast(0L).toDouble()
    val kib = 1024.0
    val mib = kib * 1024.0
    val gib = mib * 1024.0
    return when {
        value >= gib -> String.format(Locale.US, "%.1f GB", value / gib)
        value >= mib -> String.format(Locale.US, "%.1f MB", value / mib)
        value >= kib -> String.format(Locale.US, "%.1f KB", value / kib)
        else -> "${value.toLong()} B"
    }
}
