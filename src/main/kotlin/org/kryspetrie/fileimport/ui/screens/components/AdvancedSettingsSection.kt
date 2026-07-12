package org.kryspetrie.fileimport.ui.screens.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import org.kryspetrie.fileimport.application.WatchFolderService
import org.kryspetrie.fileimport.domain.model.ImportConfiguration
import org.kryspetrie.fileimport.domain.model.RawJpegPairMode
import org.kryspetrie.fileimport.domain.model.WatchFolderConfig
import org.kryspetrie.fileimport.domain.model.WatchFolderStatus
import org.kryspetrie.fileimport.ui.components.SectionLabel
import org.kryspetrie.fileimport.ui.components.SettingsToggle

@Composable
fun AdvancedSettingsSection(
    configuration: ImportConfiguration,
    onConfigChange: (ImportConfiguration) -> Unit,
    onClearCache: () -> Unit,
    sourcePath: String,
    destinationPath: String,
    watchFolderService: WatchFolderService,
    watchStatus: WatchFolderStatus,
    scope: CoroutineScope,
) {
    var advancedExpanded by remember { mutableStateOf(false) }
    CollapsibleSubsection(
        title = "Advanced",
        icon = Icons.Default.SettingsApplications,
        expanded = advancedExpanded,
        onToggle = { advancedExpanded = !advancedExpanded },
    ) {
        RawJpegPairSection(configuration, onConfigChange)
        Spacer(Modifier.height(6.dp))
        SidecarSection(configuration, onConfigChange)
        Spacer(Modifier.height(6.dp))
        WatchFolderSection(
            configuration,
            sourcePath,
            destinationPath,
            watchFolderService,
            watchStatus,
            scope,
        )
        Spacer(Modifier.height(6.dp))
        CacheManagementSection(onClearCache)
    }
}

@Composable
private fun RawJpegPairSection(
    configuration: ImportConfiguration,
    onConfigChange: (ImportConfiguration) -> Unit,
) {
    SectionLabel("RAW+JPEG Pairs")
    Row(Modifier.fillMaxWidth()) {
        RawJpegPairMode.entries.forEach { mode ->
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    configuration.rawJpegPairMode == mode,
                    { onConfigChange(configuration.copy(rawJpegPairMode = mode)) },
                )
                Text(
                    when (mode) {
                        RawJpegPairMode.IMPORT_BOTH -> "Import both"
                        RawJpegPairMode.RAW_ONLY -> "RAW only"
                        RawJpegPairMode.JPEG_ONLY -> "JPEG only"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
        }
    }
    if (configuration.rawJpegPairMode == RawJpegPairMode.IMPORT_BOTH) {
        SettingsToggle(
            checked = configuration.keepPairsTogether,
            onCheckedChange = { onConfigChange(configuration.copy(keepPairsTogether = it)) },
            label = "Keep pairs in same folder",
        )
    }
}

@Composable
private fun SidecarSection(
    configuration: ImportConfiguration,
    onConfigChange: (ImportConfiguration) -> Unit,
) {
    SectionLabel("Sidecars & Post-Import")
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            SettingsToggle(
                checked = configuration.importSidecars,
                onCheckedChange = { onConfigChange(configuration.copy(importSidecars = it)) },
                label = "Import sidecars",
                description = ".xmp, .thm, .lrv, .aae",
            )
        }
        Column(Modifier.weight(1f)) {
            SettingsToggle(
                checked = configuration.verifyAfterCopy,
                onCheckedChange = { onConfigChange(configuration.copy(verifyAfterCopy = it)) },
                label = "Verify copies",
                description = "Hash check after import",
            )
        }
    }
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            SettingsToggle(
                checked = configuration.deleteAfterImport,
                onCheckedChange = { onConfigChange(configuration.copy(deleteAfterImport = it)) },
                label = "Delete source",
                description = "Remove after successful copy",
            )
        }
    }
    if (configuration.deleteAfterImport) {
        Text(
            "Warning: Source files will be deleted after successful copy and verification.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun WatchFolderSection(
    configuration: ImportConfiguration,
    sourcePath: String,
    destinationPath: String,
    watchFolderService: WatchFolderService,
    watchStatus: WatchFolderStatus,
    scope: CoroutineScope,
) {
    SectionLabel("Watch Folder")
    if (!watchStatus.isWatching) {
        val canWatch = sourcePath.isNotBlank() && destinationPath.isNotBlank()
        OutlinedButton(
            onClick = {
                watchFolderService.startWatching(
                    WatchFolderConfig(
                        watchPath = sourcePath,
                        destinationPath = destinationPath,
                        configuration = configuration,
                        profileName = "Import",
                    ),
                    scope,
                )
            },
            enabled = canWatch,
        ) {
            Icon(Icons.Default.Visibility, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Watch Folder")
        }
        Text(
            "Automatically import new files dropped into the source folder.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Text(
            "Currently watching: ${watchStatus.watchPath}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun CacheManagementSection(onClearCache: () -> Unit) {
    SectionLabel("Index Cache")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onClearCache) {
            Icon(Icons.Default.Delete, null, Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("Clear Cache")
        }
    }
}
