package org.kryspetrie.fileimport.ui.screens.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import org.kryspetrie.fileimport.application.WatchFolderConfig
import org.kryspetrie.fileimport.application.WatchFolderService
import org.kryspetrie.fileimport.application.WatchFolderStatus
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.model.ConflictResolution
import org.kryspetrie.fileimport.domain.model.DateSource
import org.kryspetrie.fileimport.domain.model.FilenamePresets
import org.kryspetrie.fileimport.domain.model.FolderPresets
import org.kryspetrie.fileimport.domain.model.ImportConfiguration
import org.kryspetrie.fileimport.domain.model.NamePlaceholders
import org.kryspetrie.fileimport.domain.model.RawJpegPairMode
import org.kryspetrie.fileimport.ui.components.PlaceholderHelpTooltip
import org.kryspetrie.fileimport.ui.components.SectionLabel

@Suppress("UnusedParameter")
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
            AnimatedVisibility(expanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {

                        // ── Subsection 1: Organization ──
                        var orgExpanded by remember { mutableStateOf(true) }
                        CollapsibleSubsection(
                            title = "Organization",
                            icon = Icons.Default.FolderCopy,
                            expanded = orgExpanded,
                            onToggle = { orgExpanded = !orgExpanded },
                        ) {
                            // Folder organization
                            SectionLabel("Folder Organization")
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    configuration.createSubfolders,
                                    { onConfigChange(configuration.copy(createSubfolders = it)) },
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "Create date-based subfolders",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
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
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    FolderPresets.builtIn.take(4).forEach { preset ->
                                        FilterChip(
                                            configuration.folderPattern == preset.pattern,
                                            {
                                                onConfigChange(
                                                    configuration.copy(
                                                        folderPattern = preset.pattern
                                                    )
                                                )
                                            },
                                            label = {
                                                Text(
                                                    preset.name,
                                                    style = MaterialTheme.typography.labelSmall,
                                                )
                                            },
                                            modifier = Modifier.height(28.dp),
                                        )
                                    }
                                }
                                PlaceholderHelpTooltip(NamePlaceholders.folderPlaceholders)
                            }

                            Spacer(Modifier.height(6.dp))

                            // Filename
                            SectionLabel("Filename")
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    configuration.preserveOriginalName,
                                    {
                                        onConfigChange(
                                            configuration.copy(
                                                preserveOriginalName = it,
                                                fileNamePattern =
                                                    if (it) "{original}"
                                                    else configuration.fileNamePattern,
                                            )
                                        )
                                    },
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "Preserve original filename",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
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
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    FilenamePresets.builtIn.take(4).forEach { preset ->
                                        FilterChip(
                                            configuration.fileNamePattern == preset.pattern,
                                            {
                                                onConfigChange(
                                                    configuration.copy(
                                                        fileNamePattern = preset.pattern
                                                    )
                                                )
                                            },
                                            label = {
                                                Text(
                                                    preset.name,
                                                    style = MaterialTheme.typography.labelSmall,
                                                )
                                            },
                                            modifier = Modifier.height(28.dp),
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(6.dp))

                            // Conflict resolution
                            SectionLabel("Conflict Resolution")
                            Row(Modifier.fillMaxWidth()) {
                                ConflictResolution.entries.forEach { r ->
                                    Row(
                                        Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        RadioButton(
                                            configuration.conflictResolution == r,
                                            {
                                                onConfigChange(
                                                    configuration.copy(conflictResolution = r)
                                                )
                                            },
                                        )
                                        Text(
                                            r.name.replace("_", " ").lowercase().replaceFirstChar {
                                                it.uppercase()
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }

                            // Date source
                            SectionLabel("Date Source")
                            Row(Modifier.fillMaxWidth()) {
                                DateSource.entries.forEach { s ->
                                    Row(
                                        Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        RadioButton(
                                            configuration.dateSource == s,
                                            { onConfigChange(configuration.copy(dateSource = s)) },
                                        )
                                        Text(
                                            s.name.replace("_", " ").lowercase().replaceFirstChar {
                                                it.uppercase()
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }

                        // ── Subsection 2: Deduplication ──
                        var dedupExpanded by remember { mutableStateOf(false) }
                        CollapsibleSubsection(
                            title = "Deduplication",
                            icon = Icons.Default.FindReplace,
                            expanded = dedupExpanded,
                            onToggle = { dedupExpanded = !dedupExpanded },
                        ) {
                            // Already-transferred detection
                            SectionLabel("Already-Transferred Detection")
                            Text(
                                "How to detect files that have already been copied to the destination.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(Modifier.fillMaxWidth()) {
                                Column(Modifier.weight(1f)) {
                                    CompactCheck(
                                        configuration.detectTransferredByHash,
                                        {
                                            onConfigChange(
                                                configuration.copy(detectTransferredByHash = it)
                                            )
                                        },
                                        "Match by file hash (MD5)",
                                    )
                                }
                                Column(Modifier.weight(1f)) {
                                    CompactCheck(
                                        configuration.detectTransferredByExif,
                                        {
                                            onConfigChange(
                                                configuration.copy(detectTransferredByExif = it)
                                            )
                                        },
                                        "Match by EXIF data",
                                    )
                                }
                            }

                            Spacer(Modifier.height(6.dp))

                            // Visual duplicates
                            SectionLabel("Visual Duplicate Detection")
                            CompactCheck(
                                configuration.detectVisualDuplicates,
                                { onConfigChange(configuration.copy(detectVisualDuplicates = it)) },
                                "Detect visual/resolution duplicates among source files",
                            )
                            if (configuration.detectVisualDuplicates) {
                                CompactCheck(
                                    configuration.useSurfMatching,
                                    { onConfigChange(configuration.copy(useSurfMatching = it)) },
                                    "Use SURF feature matching (slow, high accuracy)",
                                )
                                if (configuration.useSurfMatching) {
                                    OutlinedCard(Modifier.fillMaxWidth()) {
                                        Row(
                                            Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Icon(
                                                Icons.Default.Warning,
                                                null,
                                                Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.error,
                                            )
                                            Text(
                                                "SURF matching is CPU-intensive and will be " +
                                                    "slow for large collections. It compares " +
                                                    "visual features between images to find " +
                                                    "near-duplicates regardless of resolution " +
                                                    "or format differences.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // ── Subsection 3: Advanced ──
                        var advancedExpanded by remember { mutableStateOf(false) }
                        CollapsibleSubsection(
                            title = "Advanced",
                            icon = Icons.Default.SettingsApplications,
                            expanded = advancedExpanded,
                            onToggle = { advancedExpanded = !advancedExpanded },
                        ) {
                            // RAW+JPEG pair handling
                            SectionLabel("RAW+JPEG Pairs")
                            Text(
                                "How to handle cameras that shoot RAW+JPEG simultaneously.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(Modifier.fillMaxWidth()) {
                                RawJpegPairMode.entries.forEach { mode ->
                                    Row(
                                        Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        RadioButton(
                                            configuration.rawJpegPairMode == mode,
                                            {
                                                onConfigChange(
                                                    configuration.copy(rawJpegPairMode = mode)
                                                )
                                            },
                                        )
                                        Text(
                                            when (mode) {
                                                RawJpegPairMode.IMPORT_BOTH -> "Import both"
                                                RawJpegPairMode.RAW_ONLY -> "RAW only"
                                                RawJpegPairMode.JPEG_ONLY -> "JPEG only"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                            if (configuration.rawJpegPairMode == RawJpegPairMode.IMPORT_BOTH) {
                                CompactCheck(
                                    configuration.keepPairsTogether,
                                    { onConfigChange(configuration.copy(keepPairsTogether = it)) },
                                    "Keep RAW+JPEG pairs in the same folder",
                                )
                            }

                            Spacer(Modifier.height(6.dp))

                            // Sidecar files
                            SectionLabel("Sidecar Files")
                            CompactCheck(
                                configuration.importSidecars,
                                { onConfigChange(configuration.copy(importSidecars = it)) },
                                "Import sidecar files (.xmp, .thm, .lrv, .aae, etc.)",
                            )
                            if (configuration.importSidecars) {
                                Text(
                                    "Sidecar files will be copied alongside their parent media file " +
                                    "to the same destination folder.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            Spacer(Modifier.height(6.dp))

                            // Post-import
                            SectionLabel("Post-Import")
                            Row(Modifier.fillMaxWidth()) {
                                Column(Modifier.weight(1f)) {
                                    CompactCheck(
                                        configuration.verifyAfterCopy,
                                        {
                                            onConfigChange(configuration.copy(verifyAfterCopy = it))
                                        },
                                        "Verify copies (hash check)",
                                    )
                                }
                                Column(Modifier.weight(1f)) {
                                    CompactCheck(
                                        configuration.deleteAfterImport,
                                        {
                                            onConfigChange(
                                                configuration.copy(deleteAfterImport = it)
                                            )
                                        },
                                        "Delete source after import",
                                    )
                                }
                            }
                            if (configuration.deleteAfterImport) {
                                Text(
                                    "Warning: Source files will be deleted after successful copy and verification.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }

                            Spacer(Modifier.height(6.dp))

                            // Watch folder
                            SectionLabel("Watch Folder")
                            if (!watchStatus.isWatching) {
                                val canWatch =
                                    sourcePath.isNotBlank() && destinationPath.isNotBlank()
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

                            Spacer(Modifier.height(6.dp))

                            // Cache management
                            SectionLabel("Index Cache")
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(onClick = onClearCache) {
                                    Icon(Icons.Default.Delete, null, Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Clear Cache")
                                }
                            }
                        }
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
