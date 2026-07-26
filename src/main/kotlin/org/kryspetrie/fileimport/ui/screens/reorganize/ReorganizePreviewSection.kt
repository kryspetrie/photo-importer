package org.kryspetrie.fileimport.ui.screens.reorganize

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.ReorganizePreview
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.i18n.strings

@Composable
fun ReorganizePreviewSection(
    preview: ReorganizePreview,
    folderPath: String,
    showFileChanges: Boolean,
) {
    val s = strings()
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                s.t(StringKey.REORG_DRY_RUN),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                s.t(StringKey.REORG_DRY_RUN_HINT),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatItem("${preview.totalFiles}", s.t(StringKey.REORG_STAT_TOTAL))
                StatItem("${preview.changedFiles}", s.t(StringKey.REORG_STAT_WILL_CHANGE))
                StatItem("${preview.conflictCount}", s.t(StringKey.REORG_STAT_CONFLICTS))
                StatItem("${preview.newFolderCount}", s.t(StringKey.REORG_STAT_NEW_FOLDERS))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Text(
                    s.t(StringKey.REORG_STAT_MODE, "mode" to preview.operationMode.name),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (preview.changedFiles == 0) {
                Text(
                    s.t(StringKey.REORG_ALREADY_ORGANIZED),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showFileChanges) {
        val changed = remember(preview) { preview.mappings.filter { it.isChanged } }
        val rootPath =
            remember(preview) {
                preview.mappings.firstOrNull()?.file?.file?.parent?.let { FilePath(it).parent }
                    ?: folderPath
            }

        Text(
            s.t(StringKey.REORG_FILE_CHANGES, "count" to "${changed.size}"),
            style = MaterialTheme.typography.titleSmall,
        )

        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                s.t(StringKey.REORG_CURRENT),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(24.dp))
            Text(
                s.t(StringKey.REORG_NEW),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
        }

        OutlinedCard(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
            LazyColumn {
                items(changed) { mapping ->
                    val currentRel = mapping.currentPath.removePrefix(rootPath).removePrefix("/")
                    val newRel = mapping.newPath.removePrefix(rootPath).removePrefix("/")
                    Row(
                        modifier =
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                mapping.file.fileName,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                currentRel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            null,
                            modifier = Modifier.padding(horizontal = 6.dp).size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                mapping.newFileName,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (mapping.wouldConflict) {
                                    Icon(
                                        Icons.Default.Warning,
                                        null,
                                        modifier = Modifier.size(12.dp).padding(end = 2.dp),
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                                Text(
                                    newRel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color =
                                        if (mapping.wouldConflict) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }
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
