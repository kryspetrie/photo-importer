package org.kryspetrie.fileimport.ui.screens.reorganize

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.ConflictResolution
import org.kryspetrie.fileimport.domain.model.FilenamePresets
import org.kryspetrie.fileimport.domain.model.FolderPresets
import org.kryspetrie.fileimport.domain.model.ImportConfiguration

@Composable
fun ReorganizeSettingsSection(
    config: ImportConfiguration,
    onConfigChange: (ImportConfiguration) -> Unit,
    settingsExpanded: Boolean,
    onSettingsExpandedChange: (Boolean) -> Unit,
    renameOnly: Boolean,
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier.fillMaxWidth()
                    .clickable { onSettingsExpandedChange(!settingsExpanded) }
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
                Text(
                    "Organization Settings",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (settingsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    "Toggle",
                    Modifier.size(18.dp),
                )
            }
            if (settingsExpanded) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FolderPatternField(config, onConfigChange, renameOnly)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        FilenamePatternField(config, onConfigChange)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        ConflictResolutionField(config, onConfigChange)
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderPatternField(
    config: ImportConfiguration,
    onConfigChange: (ImportConfiguration) -> Unit,
    renameOnly: Boolean,
) {
    Text(
        "Folder Pattern",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    if (!renameOnly) {
        OutlinedTextField(
            config.folderPattern,
            { onConfigChange(config.copy(folderPattern = it)) },
            label = { Text("Folder Pattern") },
            textStyle = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FolderPresets.builtIn.take(5).forEach { preset ->
                FilterChip(
                    config.folderPattern == preset.pattern,
                    { onConfigChange(config.copy(folderPattern = preset.pattern)) },
                    label = { Text(preset.name, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.height(28.dp),
                )
            }
        }
    }
}

@Composable
private fun FilenamePatternField(
    config: ImportConfiguration,
    onConfigChange: (ImportConfiguration) -> Unit,
) {
    Text(
        "Filename",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            config.preserveOriginalName,
            {
                onConfigChange(
                    config.copy(
                        preserveOriginalName = it,
                        fileNamePattern = if (it) "{original}" else config.fileNamePattern,
                    )
                )
            },
        )
        Spacer(Modifier.width(4.dp))
        Text("Preserve original filename", style = MaterialTheme.typography.bodyMedium)
    }
    OutlinedTextField(
        config.fileNamePattern,
        { onConfigChange(config.copy(fileNamePattern = it)) },
        enabled = !config.preserveOriginalName,
        label = { Text("Filename Pattern") },
        textStyle = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth(),
    )
    if (!config.preserveOriginalName) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilenamePresets.builtIn.take(4).forEach { preset ->
                FilterChip(
                    config.fileNamePattern == preset.pattern,
                    { onConfigChange(config.copy(fileNamePattern = preset.pattern)) },
                    label = { Text(preset.name, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.height(28.dp),
                )
            }
        }
    }
}

@Composable
private fun ConflictResolutionField(
    config: ImportConfiguration,
    onConfigChange: (ImportConfiguration) -> Unit,
) {
    Text(
        "Conflict Resolution",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Row(Modifier.fillMaxWidth()) {
        ConflictResolution.entries.forEach { r ->
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    config.conflictResolution == r,
                    { onConfigChange(config.copy(conflictResolution = r)) },
                )
                Text(
                    r.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            }
        }
    }
}
