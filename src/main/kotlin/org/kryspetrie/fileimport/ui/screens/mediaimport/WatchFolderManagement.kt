package org.kryspetrie.fileimport.ui.screens.mediaimport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.application.WatchFolderManager
import org.kryspetrie.fileimport.domain.model.WatchFolderConfig
import org.kryspetrie.fileimport.domain.model.WatchFolderStatus
import org.kryspetrie.fileimport.ui.components.SectionLabel

/**
 * Management panel for watch folder configurations.
 *
 * Lists all configured watch folders with their status, and provides add/edit/remove/start/stop
 * controls.
 */
@Composable
fun WatchFolderManagement(watchFolderManager: WatchFolderManager, modifier: Modifier = Modifier) {
    val statuses by watchFolderManager.statuses.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingConfig by remember { mutableStateOf<WatchFolderConfig?>(null) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Watch Folders")

        if (statuses.isEmpty()) {
            Text(
                "No watch folders configured. Click \"+ Add\" to start watching a folder.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        statuses.forEach { (configId, status) ->
            WatchFolderCard(status = status, onStop = { watchFolderManager.stopWatching(configId) })
        }

        OutlinedButton(onClick = { showAddDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Add Watch Folder")
        }
    }

    if (showAddDialog) {
        WatchFolderConfigDialog(
            existingConfig = null,
            onSave = { config ->
                watchFolderManager.addConfig(config)
                watchFolderManager.startWatching(config)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }

    editingConfig?.let { config ->
        WatchFolderConfigDialog(
            existingConfig = config,
            onSave = { updatedConfig ->
                watchFolderManager.updateConfig(updatedConfig)
                editingConfig = null
            },
            onDismiss = { editingConfig = null },
        )
    }
}

/** Card showing the status of a single watch folder. */
@Composable
private fun WatchFolderCard(status: WatchFolderStatus, onStop: () -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    when {
                        status.lastError != null && !status.isWatching -> Icons.Default.Error
                        status.isWatching -> Icons.Default.VisibilityOff
                        status.importCount > 0 -> Icons.Default.CheckCircle
                        else -> Icons.Default.Pause
                    },
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint =
                        when {
                            status.lastError != null && !status.isWatching ->
                                MaterialTheme.colorScheme.error
                            status.isWatching -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        status.watchPath,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        buildString {
                            if (status.isWatching) append("Watching") else append("Stopped")
                            append(" · ${status.filesDetected} detected")
                            if (status.importCount > 0) append(" · ${status.importCount} imported")
                            if (status.autoImportsPending > 0)
                                append(" · ${status.autoImportsPending} pending")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (status.lastImportTime > 0) {
                        Text(
                            "Last import: ${formatRelativeTime(status.lastImportTime)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (status.isWatching) {
                    OutlinedButton(onClick = onStop, modifier = Modifier.height(28.dp)) {
                        Text("Stop", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            status.lastError?.let { error ->
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
    val diffSec = (System.currentTimeMillis() - timestampMs) / 1000
    return when {
        diffSec < 5 -> "just now"
        diffSec < 60 -> "${diffSec}s ago"
        diffSec < 3600 -> "${diffSec / 60} min ago"
        diffSec < 86400 -> "${diffSec / 3600}h ago"
        else -> "${diffSec / 86400}d ago"
    }
}
