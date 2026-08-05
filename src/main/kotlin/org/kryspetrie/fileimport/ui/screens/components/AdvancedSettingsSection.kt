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
import org.kryspetrie.fileimport.domain.model.ImportConfiguration
import org.kryspetrie.fileimport.domain.model.RawJpegPairMode
import org.kryspetrie.fileimport.domain.model.WatchFolderConfig
import org.kryspetrie.fileimport.domain.model.WatchFolderStatus
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.components.SectionLabel
import org.kryspetrie.fileimport.ui.components.SettingsToggle
import org.kryspetrie.fileimport.ui.i18n.strings

@Composable
fun AdvancedSettingsSection(
    configuration: ImportConfiguration,
    onConfigChange: (ImportConfiguration) -> Unit,
    onClearCache: () -> Unit,
    sourcePath: String,
    destinationPath: String,
    onStartWatchFolder: (WatchFolderConfig) -> Unit,
    watchStatus: WatchFolderStatus,
    scope: CoroutineScope,
) {
    val s = strings()
    var advancedExpanded by remember { mutableStateOf(false) }
    CollapsibleSubsection(
        title = s.t(StringKey.SETTINGS_ADVANCED),
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
            onStartWatchFolder,
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
    val s = strings()
    SectionLabel(s.t(StringKey.SETTINGS_RAW_JPEG_PAIRS))
    Row(Modifier.fillMaxWidth()) {
        RawJpegPairMode.entries.forEach { mode ->
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    configuration.rawJpegPairMode == mode,
                    { onConfigChange(configuration.copy(rawJpegPairMode = mode)) },
                )
                Text(
                    when (mode) {
                        RawJpegPairMode.IMPORT_BOTH -> s.t(StringKey.SETTINGS_RAW_JPEG_IMPORT_BOTH)
                        RawJpegPairMode.RAW_ONLY -> s.t(StringKey.SETTINGS_RAW_JPEG_RAW_ONLY)
                        RawJpegPairMode.JPEG_ONLY -> s.t(StringKey.SETTINGS_RAW_JPEG_JPEG_ONLY)
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
            label = s.t(StringKey.SETTINGS_RAW_JPEG_KEEP_TOGETHER),
        )
    }
}

@Composable
private fun SidecarSection(
    configuration: ImportConfiguration,
    onConfigChange: (ImportConfiguration) -> Unit,
) {
    val s = strings()
    SectionLabel(s.t(StringKey.SETTINGS_SIDECARS))
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            SettingsToggle(
                checked = configuration.importSidecars,
                onCheckedChange = { onConfigChange(configuration.copy(importSidecars = it)) },
                label = s.t(StringKey.SETTINGS_IMPORT_SIDECARS),
                description = s.t(StringKey.SETTINGS_SIDECAR_TYPES_DESC),
            )
        }
        Column(Modifier.weight(1f)) {
            SettingsToggle(
                checked = configuration.verifyAfterCopy,
                onCheckedChange = { onConfigChange(configuration.copy(verifyAfterCopy = it)) },
                label = s.t(StringKey.SETTINGS_VERIFY_COPIES),
                description = s.t(StringKey.SETTINGS_VERIFY_COPIES_DESC),
            )
        }
    }
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            SettingsToggle(
                checked = configuration.deleteAfterImport,
                onCheckedChange = { onConfigChange(configuration.copy(deleteAfterImport = it)) },
                label = s.t(StringKey.SETTINGS_DELETE_SOURCE),
                description = s.t(StringKey.SETTINGS_DELETE_SOURCE_DESC),
            )
        }
    }
    if (configuration.deleteAfterImport) {
        Text(
            s.t(StringKey.SETTINGS_DELETE_SOURCE_WARNING),
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
    onStartWatchFolder: (WatchFolderConfig) -> Unit,
    watchStatus: WatchFolderStatus,
    scope: CoroutineScope,
) {
    val s = strings()
    SectionLabel(s.t(StringKey.SETTINGS_WATCH_FOLDER))
    if (!watchStatus.isWatching) {
        val canWatch = sourcePath.isNotBlank() && destinationPath.isNotBlank()
        OutlinedButton(
            onClick = {
                onStartWatchFolder(
                    WatchFolderConfig(
                        watchPath = sourcePath,
                        destinationPath = destinationPath,
                        configuration = configuration,
                        profileName = "Import",
                    )
                )
            },
            enabled = canWatch,
        ) {
            Icon(Icons.Default.Visibility, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(s.t(StringKey.SETTINGS_WATCH_FOLDER_START))
        }
        Text(
            s.t(StringKey.SETTINGS_WATCH_FOLDER_DESC),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Text(
            s.t(StringKey.SETTINGS_WATCHING, "path" to watchStatus.watchPath),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun CacheManagementSection(onClearCache: () -> Unit) {
    val s = strings()
    SectionLabel(s.t(StringKey.SETTINGS_INDEX_CACHE))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onClearCache) {
            Icon(Icons.Default.Delete, null, Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(s.t(StringKey.SETTINGS_CLEAR_CACHE))
        }
    }
}
