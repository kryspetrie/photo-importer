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
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.components.CircularSpinner
import org.kryspetrie.fileimport.ui.i18n.strings
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
    val s = strings()
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
                    if (progress.phase == ReorganizePhase.SCANNING) s.t(StringKey.REORG_SCANNING)
                    else s.t(StringKey.REORG_READING_METADATA),
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
    val s = strings()
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
                        ReorganizePhase.ROLLING_BACK -> s.t(StringKey.REORG_UNDOING)
                        ReorganizePhase.UNDOING -> s.t(StringKey.REORG_UNDOING)
                        ReorganizePhase.EXECUTING -> s.t(StringKey.REORG_REORGANIZING)
                        else -> s.t(StringKey.REORG_PROCESSING)
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
    val s = strings()
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
                if (r.errorCount == 0) s.t(StringKey.REORG_COMPLETE)
                else s.t(StringKey.REORG_COMPLETE_ERRORS),
                style = MaterialTheme.typography.titleSmall,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                when (r.operationMode) {
                    ReorganizeMode.MOVE -> {
                        StatItem("${r.movedCount}", s.t(StringKey.REORG_STAT_MOVED))
                        StatItem("${r.renamedCount}", s.t(StringKey.REORG_STAT_RENAMED))
                    }
                    ReorganizeMode.COPY ->
                        StatItem("${r.copiedCount}", s.t(StringKey.REORG_STAT_COPIED))
                }
                StatItem("${r.skippedCount}", s.t(StringKey.REORG_STAT_SKIPPED))
                StatItem("${r.errorCount}", s.t(StringKey.IMPORT_STAT_ERRORS))
            }
            r.journalPath?.let {
                Text(
                    s.t(StringKey.REORG_UNDO_SAVED),
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
                        s.t(StringKey.REORG_AND_MORE, "count" to "${r.errors.size - 5}"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            OutlinedButton(onClick = onReset) { Text(s.t(StringKey.ACTION_OK)) }
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
