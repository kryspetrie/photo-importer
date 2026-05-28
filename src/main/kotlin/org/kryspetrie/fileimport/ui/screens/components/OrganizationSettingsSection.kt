package org.kryspetrie.fileimport.ui.screens.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material3.Checkbox
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
import org.kryspetrie.fileimport.ui.components.PlaceholderHelpTooltip
import org.kryspetrie.fileimport.ui.components.SectionLabel

@Composable
fun OrganizationSettingsSection(
    configuration: ImportConfiguration,
    onConfigChange: (ImportConfiguration) -> Unit,
) {
    var orgExpanded by remember { mutableStateOf(true) }
    CollapsibleSubsection(
        title = "Organization",
        icon = Icons.Default.FolderCopy,
        expanded = orgExpanded,
        onToggle = { orgExpanded = !orgExpanded },
    ) {
        FolderPatternField(configuration, onConfigChange)
        Spacer(Modifier.height(6.dp))
        FilenamePatternField(configuration, onConfigChange)
        Spacer(Modifier.height(6.dp))
        SectionLabel("Conflict Resolution")
        EnumRadioButtonGroup(
            entries = ConflictResolution.entries,
            selected = configuration.conflictResolution,
            onSelect = { onConfigChange(configuration.copy(conflictResolution = it)) },
        )
        SectionLabel("Date Source")
        EnumRadioButtonGroup(
            entries = DateSource.entries,
            selected = configuration.dateSource,
            onSelect = { onConfigChange(configuration.copy(dateSource = it)) },
        )
    }
}

@Composable
private fun FolderPatternField(
    configuration: ImportConfiguration,
    onConfigChange: (ImportConfiguration) -> Unit,
) {
    SectionLabel("Folder Organization")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            configuration.createSubfolders,
            { onConfigChange(configuration.copy(createSubfolders = it)) },
        )
        Spacer(Modifier.width(4.dp))
        Text("Create date-based subfolders", style = MaterialTheme.typography.bodyMedium)
    }
    if (configuration.createSubfolders) {
        OutlinedTextField(
            configuration.folderPattern,
            { onConfigChange(configuration.copy(folderPattern = it)) },
            label = { Text("Folder Pattern") },
            textStyle = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth(),
        )
        FolderPresets.examples[configuration.folderPattern]?.let {
            Text(
                "Example: $it",
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
}

@Composable
private fun FilenamePatternField(
    configuration: ImportConfiguration,
    onConfigChange: (ImportConfiguration) -> Unit,
) {
    SectionLabel("Filename")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            configuration.preserveOriginalName,
            {
                onConfigChange(
                    configuration.copy(
                        preserveOriginalName = it,
                        fileNamePattern = if (it) "{original}" else configuration.fileNamePattern,
                    )
                )
            },
        )
        Spacer(Modifier.width(4.dp))
        Text("Preserve original filename", style = MaterialTheme.typography.bodyMedium)
    }
    OutlinedTextField(
        configuration.fileNamePattern,
        { onConfigChange(configuration.copy(fileNamePattern = it)) },
        enabled = !configuration.preserveOriginalName,
        label = { Text("Filename Pattern") },
        textStyle = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth(),
    )
    if (!configuration.preserveOriginalName) {
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
}

@Composable
private fun <T : Enum<T>> EnumRadioButtonGroup(
    entries: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Row(Modifier.fillMaxWidth()) {
        entries.forEach { entry ->
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected == entry, { onSelect(entry) })
                Text(
                    entry.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            }
        }
    }
}
