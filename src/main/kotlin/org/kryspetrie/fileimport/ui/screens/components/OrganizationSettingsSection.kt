package org.kryspetrie.fileimport.ui.screens.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import org.kryspetrie.fileimport.domain.model.ConflictResolution
import org.kryspetrie.fileimport.domain.model.DateSource
import org.kryspetrie.fileimport.domain.model.FilenamePresets
import org.kryspetrie.fileimport.domain.model.FolderPresets
import org.kryspetrie.fileimport.domain.model.ImportConfiguration
import org.kryspetrie.fileimport.domain.model.NamePlaceholders
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.components.PlaceholderHelpTooltip
import org.kryspetrie.fileimport.ui.components.SectionLabel
import org.kryspetrie.fileimport.ui.components.SettingsToggle
import org.kryspetrie.fileimport.ui.i18n.conflictResolutionLabel
import org.kryspetrie.fileimport.ui.i18n.dateSourceLabel
import org.kryspetrie.fileimport.ui.i18n.strings

@Composable
fun OrganizationSettingsSection(
    configuration: ImportConfiguration,
    onConfigChange: (ImportConfiguration) -> Unit,
) {
    val s = strings()
    var orgExpanded by remember { mutableStateOf(true) }
    CollapsibleSubsection(
        title = s.t(StringKey.SETTINGS_ORG),
        icon = Icons.Default.FolderCopy,
        expanded = orgExpanded,
        onToggle = { orgExpanded = !orgExpanded },
    ) {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                SettingsToggle(
                    checked = configuration.createSubfolders,
                    onCheckedChange = { onConfigChange(configuration.copy(createSubfolders = it)) },
                    label = s.t(StringKey.SETTINGS_ORG_SUBFOLDERS),
                )
            }
            Column(Modifier.weight(1f)) {
                SettingsToggle(
                    checked = configuration.preserveOriginalName,
                    onCheckedChange = {
                        onConfigChange(
                            configuration.copy(
                                preserveOriginalName = it,
                                fileNamePattern =
                                    if (it) "{original}" else configuration.fileNamePattern,
                            )
                        )
                    },
                    label = s.t(StringKey.SETTINGS_ORG_PRESERVE_NAMES),
                )
            }
        }
        if (configuration.createSubfolders) {
            FolderPatternField(configuration, onConfigChange)
        }
        if (!configuration.preserveOriginalName) {
            FilenamePatternField(configuration, onConfigChange)
        }
        Spacer(Modifier.height(6.dp))
        SectionLabel(s.t(StringKey.SETTINGS_ORG_CONFLICT_RESOLUTION))
        ConflictResolutionRadioGroup(configuration, onConfigChange)
        SectionLabel(s.t(StringKey.SETTINGS_ORG_DATE_SOURCE))
        DateSourceRadioGroup(configuration, onConfigChange)
    }
}

@Composable
private fun FolderPatternField(
    configuration: ImportConfiguration,
    onConfigChange: (ImportConfiguration) -> Unit,
) {
    val s = strings()
    OutlinedTextField(
        configuration.folderPattern,
        { onConfigChange(configuration.copy(folderPattern = it)) },
        label = { Text(s.t(StringKey.SETTINGS_ORG_FOLDER_PATTERN)) },
        textStyle = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth(),
    )
    FolderPresets.examples[configuration.folderPattern]?.let {
        Text(
            s.t(StringKey.PLACEHOLDER_EXAMPLE, "path" to it),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        FolderPresets.builtIn.take(4).forEach { preset ->
            FilterChip(
                configuration.folderPattern == preset.pattern,
                { onConfigChange(configuration.copy(folderPattern = preset.pattern)) },
                label = { Text(preset.name, style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.height(28.dp),
            )
        }
    }
    PlaceholderHelpTooltip(NamePlaceholders.folderPlaceholders)
}

@Composable
private fun FilenamePatternField(
    configuration: ImportConfiguration,
    onConfigChange: (ImportConfiguration) -> Unit,
) {
    val s = strings()
    OutlinedTextField(
        configuration.fileNamePattern,
        { onConfigChange(configuration.copy(fileNamePattern = it)) },
        enabled = !configuration.preserveOriginalName,
        label = { Text(s.t(StringKey.SETTINGS_ORG_FILENAME_PATTERN)) },
        textStyle = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        FilenamePresets.builtIn.take(4).forEach { preset ->
            FilterChip(
                configuration.fileNamePattern == preset.pattern,
                { onConfigChange(configuration.copy(fileNamePattern = preset.pattern)) },
                label = { Text(preset.name, style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.height(28.dp),
            )
        }
    }
}

@Composable
private fun ConflictResolutionRadioGroup(
    configuration: ImportConfiguration,
    onConfigChange: (ImportConfiguration) -> Unit,
) {
    val s = strings()
    Row(Modifier.fillMaxWidth()) {
        ConflictResolution.entries.forEach { entry ->
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    configuration.conflictResolution == entry,
                    { onConfigChange(configuration.copy(conflictResolution = entry)) },
                )
                Text(
                    s.conflictResolutionLabel(entry),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun DateSourceRadioGroup(
    configuration: ImportConfiguration,
    onConfigChange: (ImportConfiguration) -> Unit,
) {
    val s = strings()
    Row(Modifier.fillMaxWidth()) {
        DateSource.entries.forEach { entry ->
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    configuration.dateSource == entry,
                    { onConfigChange(configuration.copy(dateSource = entry)) },
                )
                Text(
                    s.dateSourceLabel(entry),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            }
        }
    }
}
