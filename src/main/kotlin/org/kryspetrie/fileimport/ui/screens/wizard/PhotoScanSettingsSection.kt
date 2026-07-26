/**
 * Collapsible custom settings section for the photo scan import screen.
 *
 * Contains folder organization, filename, conflict resolution, and date source controls. Extracted
 * from [PhotoScanImportScreen] to reduce method length and complexity.
 */
package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import org.kryspetrie.fileimport.ui.i18n.configSummary
import org.kryspetrie.fileimport.ui.i18n.conflictResolutionLabel
import org.kryspetrie.fileimport.ui.i18n.dateSourceDescription
import org.kryspetrie.fileimport.ui.i18n.dateSourceLabel
import org.kryspetrie.fileimport.ui.i18n.strings

@Composable
fun PhotoScanSettingsSection(
    config: ImportConfiguration,
    onConfigChange: (ImportConfiguration) -> Unit,
    settingsExpanded: Boolean,
    onSettingsExpandedChange: (Boolean) -> Unit,
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column {
            SettingsCardHeader(
                settingsExpanded = settingsExpanded,
                onSettingsExpandedChange = onSettingsExpandedChange,
                config = config,
            )
            if (settingsExpanded) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(
                        Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        var orientationExpanded by remember { mutableStateOf(false) }
                        PhotoScanCollapsibleSubsection(
                            title = strings().t(StringKey.SETTINGS_ORIENTATION),
                            icon = Icons.Default.AutoFixHigh,
                            expanded = orientationExpanded,
                            onToggle = { orientationExpanded = !orientationExpanded },
                        ) {
                            OrientationSection(config = config, onConfigChange = onConfigChange)
                        }
                        var orgExpanded by remember { mutableStateOf(true) }
                        PhotoScanCollapsibleSubsection(
                            title = strings().t(StringKey.SETTINGS_ORG),
                            icon = Icons.Default.FolderCopy,
                            expanded = orgExpanded,
                            onToggle = { orgExpanded = !orgExpanded },
                        ) {
                            OrganizationSection(config = config, onConfigChange = onConfigChange)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCardHeader(
    settingsExpanded: Boolean,
    onSettingsExpandedChange: (Boolean) -> Unit,
    config: ImportConfiguration,
) {
    val s = strings()
    Row(
        Modifier.fillMaxWidth()
            .clickable { onSettingsExpandedChange(!settingsExpanded) }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            Icons.Default.Tune,
            null,
            Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(Modifier.weight(1f)) {
            Text(
                s.t(StringKey.IMPORT_SETTINGS_LABEL),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            )
            if (!settingsExpanded) {
                Text(
                    s.configSummary(config),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            if (settingsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            s.t(StringKey.ACC_TOGGLE),
            Modifier.size(16.dp),
        )
    }
}

@Composable
private fun OrientationSection(
    config: ImportConfiguration,
    onConfigChange: (ImportConfiguration) -> Unit,
) {
    val s = strings()
    SectionLabel(s.t(StringKey.SETTINGS_ORIENTATION_AUTO_ORIENT))
    SettingsToggle(
        checked = config.autoOrientEnabled,
        onCheckedChange = { onConfigChange(config.copy(autoOrientEnabled = it)) },
        label = s.t(StringKey.SETTINGS_ORIENTATION_AUTO_ORIENT),
        description = s.t(StringKey.SETTINGS_ORIENTATION_AUTO_ORIENT_DESC),
    )
    if (config.autoOrientEnabled) {
        Text(
            s.t(StringKey.SETTINGS_ORIENTATION_AUTO_ORIENT_ENABLED_NOTE),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OrganizationSection(
    config: ImportConfiguration,
    onConfigChange: (ImportConfiguration) -> Unit,
) {
    val s = strings()
    SectionLabel(s.t(StringKey.SETTINGS_ORG))
    SettingsToggle(
        checked = config.createSubfolders,
        onCheckedChange = { onConfigChange(config.copy(createSubfolders = it)) },
        label = s.t(StringKey.SETTINGS_ORG_SUBFOLDERS),
    )
    if (config.createSubfolders) {
        FolderOrganizationField(config = config, onConfigChange = onConfigChange)
    }

    Spacer(Modifier.height(4.dp))

    SectionLabel(s.t(StringKey.SETTINGS_ORG_FILENAME))
    SettingsToggle(
        checked = config.preserveOriginalName,
        onCheckedChange = {
            onConfigChange(
                config.copy(
                    preserveOriginalName = it,
                    fileNamePattern = if (it) "{original}" else config.fileNamePattern,
                )
            )
        },
        label = s.t(StringKey.SETTINGS_ORG_PRESERVE_NAMES),
    )
    OutlinedTextField(
        config.fileNamePattern,
        { onConfigChange(config.copy(fileNamePattern = it)) },
        enabled = !config.preserveOriginalName,
        label = { Text(s.t(StringKey.SETTINGS_ORG_FILENAME_PATTERN)) },
        textStyle = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth(),
    )
    if (!config.preserveOriginalName) {
        FilenamePatternField(config = config, onConfigChange = onConfigChange)
    }

    Spacer(Modifier.height(4.dp))

    ConflictResolutionField(config = config, onConfigChange = onConfigChange)

    DateSourceField(config = config, onConfigChange = onConfigChange)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderOrganizationField(
    config: ImportConfiguration,
    onConfigChange: (ImportConfiguration) -> Unit,
) {
    val s = strings()
    OutlinedTextField(
        config.folderPattern,
        { onConfigChange(config.copy(folderPattern = it)) },
        label = { Text(s.t(StringKey.SETTINGS_ORG_FOLDER_PATTERN)) },
        textStyle = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        FolderPresets.builtIn.take(4).forEach { preset ->
            FilterChip(
                config.folderPattern == preset.pattern,
                { onConfigChange(config.copy(folderPattern = preset.pattern)) },
                label = { Text(preset.name, style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.height(24.dp),
            )
        }
    }
    PlaceholderHelpTooltip(NamePlaceholders.folderPlaceholders)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilenamePatternField(
    config: ImportConfiguration,
    onConfigChange: (ImportConfiguration) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        FilenamePresets.builtIn.take(4).forEach { preset ->
            FilterChip(
                config.fileNamePattern == preset.pattern,
                { onConfigChange(config.copy(fileNamePattern = preset.pattern)) },
                label = { Text(preset.name, style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.height(24.dp),
            )
        }
    }
}

@Composable
private fun ConflictResolutionField(
    config: ImportConfiguration,
    onConfigChange: (ImportConfiguration) -> Unit,
) {
    val s = strings()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ConflictResolution.entries.forEach { r ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier.clickable { onConfigChange(config.copy(conflictResolution = r)) },
            ) {
                RadioButton(
                    config.conflictResolution == r,
                    { onConfigChange(config.copy(conflictResolution = r)) },
                )
                Text(s.conflictResolutionLabel(r), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun DateSourceField(
    config: ImportConfiguration,
    onConfigChange: (ImportConfiguration) -> Unit,
) {
    val s = strings()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        DateSource.entries.forEach { source ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onConfigChange(config.copy(dateSource = source)) },
            ) {
                RadioButton(
                    config.dateSource == source,
                    { onConfigChange(config.copy(dateSource = source)) },
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(s.dateSourceLabel(source), style = MaterialTheme.typography.labelSmall)
                    Text(
                        s.dateSourceDescription(source),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoScanCollapsibleSubsection(
    title: String,
    icon: ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            Text(
                title,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            )
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                null,
                Modifier.size(14.dp),
            )
        }
        if (expanded) {
            Column(content = content)
        }
    }
}
