package org.kryspetrie.fileimport.ui.screens.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import org.kryspetrie.fileimport.application.WatchFolderService
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.model.ImportConfiguration
import org.kryspetrie.fileimport.domain.model.WatchFolderStatus

@Composable
fun SettingsSection(
    expanded: Boolean,
    onToggle: () -> Unit,
    configuration: ImportConfiguration,
    onConfigChange: (ImportConfiguration) -> Unit,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onClearCache: () -> Unit,
    sourcePath: String,
    destinationPath: String,
    watchFolderService: WatchFolderService,
    watchStatus: WatchFolderStatus,
    scope: CoroutineScope,
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier.fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.Tune,
                    null,
                    Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(Modifier.weight(1f)) {
                    Text("Custom Settings", style = MaterialTheme.typography.titleSmall)
                    if (!expanded) {
                        Text(
                            configSummary(configuration),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
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
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        OrganizationSettingsSection(configuration, onConfigChange)
                        DeduplicationSettingsSection(configuration, onConfigChange)
                        AdvancedSettingsSection(
                            configuration = configuration,
                            onConfigChange = onConfigChange,
                            onClearCache = onClearCache,
                            sourcePath = sourcePath,
                            destinationPath = destinationPath,
                            watchFolderService = watchFolderService,
                            watchStatus = watchStatus,
                            scope = scope,
                        )
                    }
                }
            }
        }
    }
}

private fun configSummary(c: ImportConfiguration): String = buildString {
    if (c.createSubfolders) append(c.folderPattern) else append("Flat")
    append(" · ")
    if (c.preserveOriginalName) append("original names") else append(c.fileNamePattern)
    if (c.verifyAfterCopy) append(" · verify")
    if (c.deleteAfterImport) append(" · delete source")
    if (c.detectVisualDuplicates) append(" · dedup")
}
