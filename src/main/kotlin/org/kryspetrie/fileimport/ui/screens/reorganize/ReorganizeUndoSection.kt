package org.kryspetrie.fileimport.ui.screens.reorganize

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
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Badge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.ReorganizeJournalSummary

/**
 * Collapsible undo journal history section for the reorganize screen.
 *
 * Lists past reorganization operations with the ability to undo any non-undone operation. Extracted
 * from [ReorganizeScreen] to reduce method length and complexity.
 */
@Composable
fun ReorganizeUndoSection(
    journals: List<ReorganizeJournalSummary>,
    onUndoRequest: (ReorganizeJournalSummary) -> Unit,
    onViewJournal: (ReorganizeJournalSummary) -> Unit,
) {
    if (journals.isEmpty()) return

    var undoExpanded by remember { mutableStateOf(false) }

    OutlinedCard(Modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier.fillMaxWidth()
                    .clickable { undoExpanded = !undoExpanded }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Undo,
                    null,
                    Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(Modifier.weight(1f)) {
                    Text("Undo History", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${journals.size} operations • " +
                            "${journals.sumOf { it.changedFiles }} files changed",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    if (undoExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    "Toggle",
                    Modifier.size(18.dp),
                )
            }
            if (undoExpanded) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        journals.forEach { journal ->
                            JournalRow(
                                journal = journal,
                                onUndoRequest = onUndoRequest,
                                onViewJournal = onViewJournal,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JournalRow(
    journal: ReorganizeJournalSummary,
    onUndoRequest: (ReorganizeJournalSummary) -> Unit,
    onViewJournal: (ReorganizeJournalSummary) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable { if (!journal.undone) onUndoRequest(journal) else onViewJournal(journal) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.AutoMirrored.Filled.Undo,
            null,
            Modifier.size(16.dp),
            tint =
                if (journal.undone) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.primary,
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    journal.rootFolder.substringAfterLast("/"),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "— ${journal.operationMode}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (journal.undone) {
                    Spacer(Modifier.width(4.dp))
                    Badge { Text("Undone", Modifier.padding(horizontal = 4.dp)) }
                }
            }
            Text(
                "${journal.changedFiles} files • ${journal.timestampString}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            if (!journal.undone) "Undo" else "View",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
