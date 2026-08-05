package org.kryspetrie.fileimport.ui.screens.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.model.ImportConfiguration
import org.kryspetrie.fileimport.domain.model.WatchFolderConfig
import org.kryspetrie.fileimport.domain.model.WatchFolderStatus
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.i18n.configSummary
import org.kryspetrie.fileimport.ui.i18n.strings

/**
 * Media Import settings: Orientation and Organization are top-level panels; remaining options
 * (dedupe, advanced) stay under Custom Settings.
 */
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
    onStartWatchFolder: (WatchFolderConfig) -> Unit,
    watchStatus: WatchFolderStatus,
    scope: CoroutineScope,
) {
    val s = strings()
    var orientationExpanded by remember { mutableStateOf(false) }
    var organizationExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CollapsibleSettingsCard(
            title = s.t(StringKey.IMPORT_ORIENTATION),
            icon = Icons.Default.AutoFixHigh,
            expanded = orientationExpanded,
            onToggle = { orientationExpanded = !orientationExpanded },
            summary =
                if (configuration.autoOrientEnabled) {
                    s.t(StringKey.IMPORT_SUMMARY_AUTO_ORIENT)
                } else {
                    null
                },
        ) {
            OrientationSettingsSection(
                configuration = configuration,
                onConfigChange = onConfigChange,
                collapsible = false,
            )
        }

        CollapsibleSettingsCard(
            title = s.t(StringKey.IMPORT_ORGANIZATION),
            icon = Icons.Default.Folder,
            expanded = organizationExpanded,
            onToggle = { organizationExpanded = !organizationExpanded },
            summary = s.configSummary(configuration),
        ) {
            OrganizationSettingsSection(
                configuration = configuration,
                onConfigChange = onConfigChange,
                collapsible = false,
            )
        }

        CollapsibleSettingsCard(
            title = s.t(StringKey.IMPORT_SETTINGS_LABEL),
            icon = Icons.Default.Tune,
            expanded = expanded,
            onToggle = onToggle,
            summary =
                buildString {
                    if (
                        configuration.detectVisualDuplicates ||
                            configuration.detectTransferredByHash ||
                            configuration.detectTransferredByExif
                    ) {
                        append(s.t(StringKey.IMPORT_DEDUPLICATION))
                    }
                    if (isNotEmpty()) append(s.t(StringKey.IMPORT_SUMMARY_SEPARATOR))
                    append(s.t(StringKey.IMPORT_ADVANCED))
                },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DeduplicationSettingsSection(configuration, onConfigChange)
                AdvancedSettingsSection(
                    configuration = configuration,
                    onConfigChange = onConfigChange,
                    onClearCache = onClearCache,
                    sourcePath = sourcePath,
                    destinationPath = destinationPath,
                    onStartWatchFolder = onStartWatchFolder,
                    watchStatus = watchStatus,
                    scope = scope,
                )
            }
        }
    }
}
