package org.kryspetrie.fileimport.ui.screens.duplicatescanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.ui.components.FolderSelectionField
import org.kryspetrie.fileimport.ui.components.SettingsToggle

@Composable
fun DuplicateScanSetup(
    folderPath: String,
    onFolderPathChange: (String) -> Unit,
    enableHash: Boolean,
    onEnableHashChange: (Boolean) -> Unit,
    enableExif: Boolean,
    onEnableExifChange: (Boolean) -> Unit,
    enableSurf: Boolean,
    onEnableSurfChange: (Boolean) -> Unit,
    errorMessage: String?,
) {
    // Folder selection
    FolderSelectionField(
        value = folderPath,
        onValueChange = onFolderPathChange,
        modifier = Modifier.fillMaxWidth(),
        label = "Library Folder",
        placeholder = "Select folder to scan...",
        title = "Select Library Folder",
        supportingText = {
            Text("Paste a path or browse", style = MaterialTheme.typography.labelSmall)
        },
    )

    // Detection methods
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Detection Methods", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    SettingsToggle(
                        checked = enableHash,
                        onCheckedChange = onEnableHashChange,
                        label = "Exact hash match",
                        description = "Compare file content (MD5)",
                    )
                }
                Column(Modifier.weight(1f)) {
                    SettingsToggle(
                        checked = enableExif,
                        onCheckedChange = onEnableExifChange,
                        label = "EXIF metadata match",
                        description = "Compare EXIF data",
                    )
                }
            }
            SettingsToggle(
                checked = enableSurf,
                onCheckedChange = onEnableSurfChange,
                label = "SURF visual matching",
                description = "Slow but detects near-duplicates",
            )
        }
    }

    // Error
    errorMessage?.let {
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
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}