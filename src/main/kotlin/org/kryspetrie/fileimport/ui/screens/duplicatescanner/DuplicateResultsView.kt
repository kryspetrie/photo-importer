package org.kryspetrie.fileimport.ui.screens.duplicatescanner

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.application.DuplicateAction
import org.kryspetrie.fileimport.domain.model.DuplicateInfo
import org.kryspetrie.fileimport.ui.components.formatFileSize

@Composable
fun DuplicateResultsView(
    duplicates: List<DuplicateInfo>,
    totalDupeFiles: Int,
    totalWastedBytes: Long,
    resolveAction: DuplicateAction,
    onResolveActionChange: (DuplicateAction) -> Unit,
    moveToTrash: Boolean,
    onMoveToTrashChange: (Boolean) -> Unit,
    onReset: () -> Unit,
) {
    if (duplicates.isEmpty()) {
        NoDuplicatesCard(onReset = onReset)
    } else {
        DuplicateSummaryCard(duplicates.size, totalDupeFiles, totalWastedBytes)
        ResolveStrategyCard(resolveAction, onResolveActionChange, moveToTrash, onMoveToTrashChange)
        Text("Duplicate Groups", style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun NoDuplicatesCard(onReset: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Default.CheckCircle,
                null,
                Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text("No Duplicates Found", style = MaterialTheme.typography.titleSmall)
            Text(
                "Your library is clean!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onReset) { Text("Done") }
        }
    }
}

@Composable
private fun DuplicateSummaryCard(groupCount: Int, totalDupeFiles: Int, totalWastedBytes: Long) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Found $groupCount duplicate groups", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$totalDupeFiles", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Duplicate files",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        formatFileSize(totalWastedBytes),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        "Reclaimable space",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ResolveStrategyCard(
    resolveAction: DuplicateAction,
    onResolveActionChange: (DuplicateAction) -> Unit,
    moveToTrash: Boolean,
    onMoveToTrashChange: (Boolean) -> Unit,
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Auto-Resolve Strategy", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DuplicateAction.entries.forEach { action ->
                    val label =
                        when (action) {
                            DuplicateAction.KEEP_HIGHEST_RES -> "Highest Res"
                            DuplicateAction.KEEP_RAW_OVER_JPEG -> "Prefer RAW"
                            DuplicateAction.KEEP_NEWEST -> "Newest"
                            DuplicateAction.KEEP_OLDEST -> "Oldest"
                            DuplicateAction.KEEP_LARGEST -> "Largest"
                        }
                    FilterChip(
                        selected = resolveAction == action,
                        onClick = { onResolveActionChange(action) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(28.dp),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(moveToTrash, onMoveToTrashChange, Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "Move to review folder instead of deleting",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
