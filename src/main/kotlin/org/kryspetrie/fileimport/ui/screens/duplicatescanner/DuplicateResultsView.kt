package org.kryspetrie.fileimport.ui.screens.duplicatescanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
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
import org.kryspetrie.fileimport.domain.model.DuplicateAction
import org.kryspetrie.fileimport.domain.model.DuplicateInfo
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.components.SettingsToggle
import org.kryspetrie.fileimport.ui.components.formatFileSize
import org.kryspetrie.fileimport.ui.i18n.strings

internal fun DuplicateAction.labelKey(): StringKey =
    when (this) {
        DuplicateAction.KEEP_HIGHEST_RES -> StringKey.DUP_KEEP_HIGHEST_RES
        DuplicateAction.KEEP_RAW_OVER_JPEG -> StringKey.DUP_PREFER_RAW
        DuplicateAction.KEEP_NEWEST -> StringKey.DUP_KEEP_NEWEST
        DuplicateAction.KEEP_OLDEST -> StringKey.DUP_KEEP_OLDEST
        DuplicateAction.KEEP_LARGEST -> StringKey.DUP_KEEP_LARGEST
    }

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
    val s = strings()

    if (duplicates.isEmpty()) {
        NoDuplicatesCard(onReset = onReset)
    } else {
        DuplicateSummaryCard(duplicates.size, totalDupeFiles, totalWastedBytes)
        ResolveStrategyCard(resolveAction, onResolveActionChange, moveToTrash, onMoveToTrashChange)
        Text(s.t(StringKey.DUP_GROUPS), style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun NoDuplicatesCard(onReset: () -> Unit) {
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
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(s.t(StringKey.DUP_NONE_FOUND), style = MaterialTheme.typography.titleSmall)
            Text(
                s.t(StringKey.DUP_LIBRARY_CLEAN),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onReset) { Text(s.t(StringKey.META_DONE)) }
        }
    }
}

@Composable
private fun DuplicateSummaryCard(groupCount: Int, totalDupeFiles: Int, totalWastedBytes: Long) {
    val s = strings()

    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                s.t(StringKey.DUP_FOUND_GROUPS, "count" to groupCount.toString()),
                style = MaterialTheme.typography.titleSmall,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$totalDupeFiles", style = MaterialTheme.typography.titleSmall)
                    Text(
                        s.t(StringKey.DUP_DUPLICATE_FILES),
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
                        s.t(StringKey.DUP_RECLAIMABLE),
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
    val s = strings()

    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(s.t(StringKey.DUP_AUTO_RESOLVE), style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DuplicateAction.entries.forEach { action ->
                    FilterChip(
                        selected = resolveAction == action,
                        onClick = { onResolveActionChange(action) },
                        label = {
                            Text(
                                s.t(action.labelKey()),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        modifier = Modifier.height(28.dp),
                    )
                }
            }
            SettingsToggle(
                checked = moveToTrash,
                onCheckedChange = onMoveToTrashChange,
                label = s.t(StringKey.DUP_MOVE_TO_REVIEW),
                description = s.t(StringKey.DUP_MOVE_TO_REVIEW_DESC),
            )
        }
    }
}
