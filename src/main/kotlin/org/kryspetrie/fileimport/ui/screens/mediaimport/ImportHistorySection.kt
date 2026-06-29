package org.kryspetrie.fileimport.ui.screens.mediaimport

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.ImportHistoryEntry
import org.kryspetrie.fileimport.ui.components.formatFileSize

/**
 * Collapsible import history section for the media import screen.
 *
 * Lists recent import operations with source path, file count, timestamp, and total size. Extracted
 * from [MediaImportScreen] to reduce method length and complexity.
 */
@Composable
fun ImportHistorySection(
    historyEntries: List<ImportHistoryEntry>,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    if (historyEntries.isEmpty()) return

    OutlinedCard(Modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier.fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.History,
                    null,
                    Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(Modifier.weight(1f)) {
                    Text("Import History", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${historyEntries.size} imports • " +
                            "${historyEntries.take(10).sumOf { it.successCount }} files",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    "Toggle",
                    Modifier.size(18.dp),
                )
            }
            if (expanded) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        historyEntries.take(10).forEach { entry ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clip(MaterialTheme.shapes.small)
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    if (entry.errorCount == 0) Icons.Default.CheckCircle
                                    else Icons.Default.Warning,
                                    null,
                                    Modifier.size(16.dp),
                                    tint =
                                        if (entry.errorCount == 0) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.error,
                                )
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            entry.sourcePath,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            "— ${entry.successCount} files",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        java.text
                                            .SimpleDateFormat("yyyy-MM-dd HH:mm")
                                            .format(java.util.Date(entry.timestamp)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    formatFileSize(entry.totalBytes),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
