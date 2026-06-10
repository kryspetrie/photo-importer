/**
 * Collapsible custom settings section for the photo scan import screen.
 *
 * Contains folder organization, filename, conflict resolution, and date source controls. Extracted
 * from [PhotoScanImportScreen] to reduce method length and complexity.
 */
package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.text.style.TextOverflow
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
            AnimatedVisibility(
                settingsExpanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        var orgExpanded by remember { mutableStateOf(true) }
                        PhotoScanCollapsibleSubsection(
                            title = "Organization",
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
        Column(Modifier.weight(1f)) {
            Text("Custom Settings", style = MaterialTheme.typography.titleSmall)
            if (!settingsExpanded) {
                Text(
                    configSummary(config),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            if (settingsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            "Toggle",
            Modifier.size(18.dp),
        )
    }
}

@Composable
private fun OrganizationSection(
    config: ImportConfiguration,
    onConfigChange: (ImportConfiguration) -> Unit,
) {
    // Folder organization
    SectionLabel("Folder Organization")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(config.createSubfolders, { onConfigChange(config.copy(createSubfolders = it)) })
        Spacer(Modifier.width(4.dp))
        Text("Create date-based subfolders", style = MaterialTheme.typography.bodyMedium)
    }
    if (config.createSubfolders) {
        FolderOrganizationField(config = config, onConfigChange = onConfigChange)
    }

    Spacer(Modifier.height(6.dp))

    // Filename
    SectionLabel("Filename")
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
        FilenamePatternField(config = config, onConfigChange = onConfigChange)
    }

    Spacer(Modifier.height(6.dp))

    // Conflict resolution
    SectionLabel("Conflict Resolution")
    ConflictResolutionField(config = config, onConfigChange = onConfigChange)

    // Date source
    SectionLabel("Date Source")
    DateSourceField(config = config, onConfigChange = onConfigChange)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderOrganizationField(
    config: ImportConfiguration,
    onConfigChange: (ImportConfiguration) -> Unit,
) {
    OutlinedTextField(
        config.folderPattern,
        { onConfigChange(config.copy(folderPattern = it)) },
        label = { Text("Folder Pattern") },
        textStyle = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        FolderPresets.builtIn.take(4).forEach { preset ->
            FilterChip(
                config.folderPattern == preset.pattern,
                { onConfigChange(config.copy(folderPattern = preset.pattern)) },
                label = { Text(preset.name, style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.height(28.dp),
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
                modifier = Modifier.height(28.dp),
            )
        }
    }
}

@Composable
private fun ConflictResolutionField(
    config: ImportConfiguration,
    onConfigChange: (ImportConfiguration) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ConflictResolution.entries.forEach { r ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    config.conflictResolution == r,
                    { onConfigChange(config.copy(conflictResolution = r)) },
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(r.displayName, style = MaterialTheme.typography.bodySmall)
                    Text(
                        r.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private val ConflictResolution.displayName: String
    get() =
        when (this) {
            ConflictResolution.RENAME -> "Rename"
            ConflictResolution.SKIP -> "Skip"
            ConflictResolution.REPLACE -> "Replace"
            ConflictResolution.ASK_USER -> "Ask me"
        }

private val ConflictResolution.description: String
    get() =
        when (this) {
            ConflictResolution.RENAME -> "Add a number suffix to avoid conflicts (safest)"
            ConflictResolution.SKIP -> "Don't import if a file with the same name exists"
            ConflictResolution.REPLACE -> "Overwrite existing files (irreversible!)"
            ConflictResolution.ASK_USER -> "Prompt for each conflict individually"
        }

@Composable
private fun DateSourceField(
    config: ImportConfiguration,
    onConfigChange: (ImportConfiguration) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DateSource.entries.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(config.dateSource == s, { onConfigChange(config.copy(dateSource = s)) })
                Column(modifier = Modifier.weight(1f)) {
                    Text(s.displayName, style = MaterialTheme.typography.bodySmall)
                    Text(
                        s.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private val DateSource.displayName: String
    get() =
        when (this) {
            DateSource.EXIF_DATE -> "EXIF date taken"
            DateSource.FILE_MODIFIED_DATE -> "File modified"
            DateSource.FILE_CREATED_DATE -> "File created"
        }

private val DateSource.description: String
    get() =
        when (this) {
            DateSource.EXIF_DATE ->
                "Use photo capture date from EXIF metadata (falls back to file date)"
            DateSource.FILE_MODIFIED_DATE -> "Use file's last modified timestamp"
            DateSource.FILE_CREATED_DATE -> "Use file creation timestamp (may change when copying)"
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
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.labelLarge)
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                null,
                Modifier.size(16.dp),
            )
        }
        AnimatedVisibility(expanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column(content = content)
        }
    }
}

private fun configSummary(c: ImportConfiguration): String = buildString {
    if (c.createSubfolders) append("${c.folderPattern} • ")
    if (c.preserveOriginalName) append("Original name") else append(c.fileNamePattern)
    append(" • ${c.conflictResolution.name.lowercase().replace("_", " ")}")
}
