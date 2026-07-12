package org.kryspetrie.fileimport.ui.screens.reorganize

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.ReorganizeMode
import org.kryspetrie.fileimport.domain.model.ReorganizePhase
import org.kryspetrie.fileimport.domain.model.ReorganizeProgress
import org.kryspetrie.fileimport.domain.model.ReorganizeResult
import org.kryspetrie.fileimport.ui.components.CircularSpinner
import org.kryspetrie.fileimport.ui.screens.ReorganizeViewModel

@Composable
fun ReorganizeProgressSection(
    step: ReorganizeViewModel.ReorgStep,
    progress: ReorganizeProgress,
    result: ReorganizeResult?,
    onReset: () -> Unit,
) {
    when (step) {
        ReorganizeViewModel.ReorgStep.SCANNING -> ScanningProgressCard(progress)
        ReorganizeViewModel.ReorgStep.EXECUTING -> ExecutingProgressCard(progress)
        ReorganizeViewModel.ReorgStep.COMPLETE -> result?.let { CompleteResultCard(it, onReset) }
        else -> {}
    }
}

@Composable
private fun ScanningProgressCard(progress: ReorganizeProgress) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularSpinner(size = 24.dp, strokeWidth = 2.5.dp)
                Text(
                    if (progress.phase == ReorganizePhase.SCANNING) "Scanning files..."
                    else "Reading metadata...",
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            if (progress.total > 0) {
                Text(
                    "${progress.current} of ${progress.total}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { progress.current.toFloat() / progress.total },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}

@Composable
private fun ExecutingProgressCard(progress: ReorganizeProgress) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularSpinner(size = 24.dp, strokeWidth = 2.5.dp)
                Text(
                    when (progress.phase) {
                        ReorganizePhase.ROLLING_BACK -> "Undoing changes..."
                        ReorganizePhase.UNDOING -> "Undoing changes..."
                        ReorganizePhase.EXECUTING -> "Reorganizing files..."
                        else -> "Processing..."
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            if (progress.total > 0) {
                Text(
                    "${progress.current} of ${progress.total}",
                    style = MaterialTheme.typography.bodySmall,
                )
                LinearProgressIndicator(
                    progress = { progress.current.toFloat() / progress.total },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                )
                Text(
                    progress.currentFile,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CompleteResultCard(r: ReorganizeResult, onReset: () -> Unit) {
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
                tint =
                    if (r.errorCount == 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
            )
            Text(
                if (r.errorCount == 0) "Reorganization Complete" else "Completed with Errors",
                style = MaterialTheme.typography.titleSmall,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                when (r.operationMode) {
                    ReorganizeMode.MOVE -> {
                        StatItem("${r.movedCount}", "Moved")
                        StatItem("${r.renamedCount}", "Renamed")
                    }
                    ReorganizeMode.COPY -> StatItem("${r.copiedCount}", "Copied")
                }
                StatItem("${r.skippedCount}", "Skipped")
                StatItem("${r.errorCount}", "Errors")
            }
            r.journalPath?.let {
                Text(
                    "Undo journal saved. You can reverse these changes at any time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (r.errors.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                r.errors.take(5).forEach { err ->
                    Text(
                        err,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (r.errors.size > 5) {
                    Text(
                        "...and ${r.errors.size - 5} more",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            OutlinedButton(onClick = onReset) { Text("Done") }
        }
    }
}

@Composable
private fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
