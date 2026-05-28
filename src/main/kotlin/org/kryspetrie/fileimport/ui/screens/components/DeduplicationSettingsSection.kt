package org.kryspetrie.fileimport.ui.screens.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.ImportConfiguration
import org.kryspetrie.fileimport.ui.components.SectionLabel

@Composable
fun DeduplicationSettingsSection(
    configuration: ImportConfiguration,
    onConfigChange: (ImportConfiguration) -> Unit,
) {
    var dedupExpanded by remember { mutableStateOf(false) }
    CollapsibleSubsection(
        title = "Deduplication",
        icon = Icons.Default.FindReplace,
        expanded = dedupExpanded,
        onToggle = { dedupExpanded = !dedupExpanded },
    ) {
        TransferredDetectionSection(configuration, onConfigChange)
        Spacer(Modifier.height(6.dp))
        VisualDuplicateSection(configuration, onConfigChange)
    }
}

@Composable
private fun TransferredDetectionSection(
    configuration: ImportConfiguration,
    onConfigChange: (ImportConfiguration) -> Unit,
) {
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
                { onConfigChange(configuration.copy(detectTransferredByHash = it)) },
                "Match by file hash (MD5)",
            )
        }
        Column(Modifier.weight(1f)) {
            CompactCheck(
                configuration.detectTransferredByExif,
                { onConfigChange(configuration.copy(detectTransferredByExif = it)) },
                "Match by EXIF data",
            )
        }
    }
}

@Composable
private fun VisualDuplicateSection(
    configuration: ImportConfiguration,
    onConfigChange: (ImportConfiguration) -> Unit,
) {
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
