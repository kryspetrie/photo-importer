package org.kryspetrie.fileimport.ui.screens.duplicatescanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.ui.components.pickFolder

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
    OutlinedTextField(
        value = folderPath,
        onValueChange = onFolderPathChange,
        label = { Text("Library Folder") },
        placeholder = { Text("Select folder to scan...") },
        modifier = Modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodyMedium,
        singleLine = true,
        trailingIcon = {
            IconButton(onClick = { pickFolder("Select Library Folder")?.let(onFolderPathChange) }) {
                Icon(Icons.Default.FolderOpen, "Browse", Modifier.size(20.dp))
            }
        },
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp),
                    ) {
                        Checkbox(enableHash, onEnableHashChange, Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Exact hash match", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp),
                    ) {
                        Checkbox(enableExif, onEnableExifChange, Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("EXIF metadata match", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 2.dp),
            ) {
                Checkbox(enableSurf, onEnableSurfChange, Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("SURF visual matching (slow)", style = MaterialTheme.typography.bodySmall)
            }
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
