package org.kryspetrie.fileimport.ui.screens.mediaimport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.WatchFolderStatus

/**
 * Status card shown when a folder is being watched for automatic imports.
 *
 * Displays the watched path, number of detected files, auto-import count, last import time, and any
 * errors. Provides a "Stop" button to cancel the watch.
 */
@Composable
fun WatchFolderStatusCard(
    watchStatus: WatchFolderStatus,
    onStopWatching: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    if (watchStatus.lastError != null && !watchStatus.isWatching) {
                        Icons.Default.Error
                    } else {
                        Icons.Default.Visibility
                    },
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint =
                        if (watchStatus.lastError != null && !watchStatus.isWatching) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        "Watching: ${watchStatus.watchPath}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        buildString {
                            append("${watchStatus.filesDetected} file(s) detected")
                            if (watchStatus.autoImportsPending > 0) {
                                append(" · ${watchStatus.autoImportsPending} pending")
                            }
                            if (watchStatus.importCount > 0) {
                                append(" · ${watchStatus.importCount} imported")
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (watchStatus.lastImportTime > 0) {
                        Text(
                            "Last import: ${formatRelativeTime(watchStatus.lastImportTime)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                OutlinedButton(
                    onClick = onStopWatching,
                    modifier = Modifier.height(28.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text("Stop", style = MaterialTheme.typography.labelSmall)
                }
            }

            // Show error if present
            watchStatus.lastError?.let { error ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        error,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Formats a timestamp as a relative time string (e.g., "2 min ago", "just now"). */
private fun formatRelativeTime(timestampMs: Long): String {
    val now = System.currentTimeMillis()
    val diffMs = now - timestampMs
    val diffSec = diffMs / 1000
    return when {
        diffSec < 5 -> "just now"
        diffSec < 60 -> "${diffSec}s ago"
        diffSec < 3600 -> "${diffSec / 60} min ago"
        diffSec < 86400 -> "${diffSec / 3600}h ago"
        else -> "${diffSec / 86400}d ago"
    }
}
