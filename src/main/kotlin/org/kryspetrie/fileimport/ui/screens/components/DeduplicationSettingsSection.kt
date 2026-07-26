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
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.components.SectionLabel
import org.kryspetrie.fileimport.ui.components.SettingsToggle
import org.kryspetrie.fileimport.ui.i18n.strings

@Composable
fun DeduplicationSettingsSection(
    configuration: ImportConfiguration,
    onConfigChange: (ImportConfiguration) -> Unit,
) {
    val s = strings()
    var dedupExpanded by remember { mutableStateOf(false) }
    CollapsibleSubsection(
        title = s.t(StringKey.SETTINGS_DEDUPE),
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
    val s = strings()
    SectionLabel(s.t(StringKey.SETTINGS_DEDUPE_TRANSFERRED))
    Text(
        "How to detect files that have already been copied to the destination.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            SettingsToggle(
                checked = configuration.detectTransferredByHash,
                onCheckedChange = {
                    onConfigChange(configuration.copy(detectTransferredByHash = it))
                },
                label = s.t(StringKey.SETTINGS_DEDUPE_HASH),
                description = s.t(StringKey.SETTINGS_DEDUPE_HASH_DESC),
            )
        }
        Column(Modifier.weight(1f)) {
            SettingsToggle(
                checked = configuration.detectTransferredByExif,
                onCheckedChange = {
                    onConfigChange(configuration.copy(detectTransferredByExif = it))
                },
                label = s.t(StringKey.SETTINGS_DEDUPE_EXIF),
                description = s.t(StringKey.SETTINGS_DEDUPE_EXIF_DESC),
            )
        }
    }
}

@Composable
private fun VisualDuplicateSection(
    configuration: ImportConfiguration,
    onConfigChange: (ImportConfiguration) -> Unit,
) {
    val s = strings()
    SettingsToggle(
        checked = configuration.detectVisualDuplicates,
        onCheckedChange = { onConfigChange(configuration.copy(detectVisualDuplicates = it)) },
        label = s.t(StringKey.SETTINGS_DEDUPE_VISUAL),
        description = s.t(StringKey.SETTINGS_DEDUPE_VISUAL_DESC),
    )
    if (configuration.detectVisualDuplicates) {
        SettingsToggle(
            checked = configuration.useSurfMatching,
            onCheckedChange = { onConfigChange(configuration.copy(useSurfMatching = it)) },
            label = s.t(StringKey.SETTINGS_DEDUPE_SURF),
            description = s.t(StringKey.SETTINGS_DEDUPE_SURF_DESC),
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
                        s.t(StringKey.SETTINGS_DEDUPE_SURF_WARNING),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
