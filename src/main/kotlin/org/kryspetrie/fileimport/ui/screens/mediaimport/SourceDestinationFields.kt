package org.kryspetrie.fileimport.ui.screens.mediaimport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.ui.components.pickFolder

/**
 * Source and destination folder path selection fields with validation for the media import screen.
 *
 * Shows two text fields with browse buttons, path validation indicators, and folder names.
 * Extracted from [MediaImportScreen] to reduce method length and complexity.
 */
@Composable
fun SourceDestinationFields(
    sourcePath: String,
    onSourcePathChange: (String) -> Unit,
    destinationPath: String,
    onDestinationPathChange: (String) -> Unit,
    sourceValid: Boolean,
    destValid: Boolean,
    sourceDirName: String?,
    destDirName: String?,
) {
    // Source field
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = sourcePath,
            onValueChange = { onSourcePathChange(it) },
            label = { Text("Source Folder") },
            placeholder = { Text("Select source...") },
            modifier = Modifier.weight(1f),
            textStyle = MaterialTheme.typography.bodyMedium,
            singleLine = true,
            isError = sourcePath.isNotBlank() && !sourceValid,
            supportingText = {
                when {
                    sourcePath.isBlank() ->
                        Text("Paste a path or browse", style = MaterialTheme.typography.labelSmall)
                    !sourceValid ->
                        Text(
                            "Folder not found",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    else ->
                        Text(
                            sourceDirName ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF2E7D32),
                        )
                }
            },
        )
        OutlinedButton(
            onClick = { pickFolder("Select Source Folder")?.let { onSourcePathChange(it) } },
            modifier = Modifier.height(56.dp),
        ) {
            Icon(Icons.Default.FolderOpen, null, Modifier.size(20.dp))
            Spacer(Modifier.width(4.dp))
            Text("Browse")
        }
    }

    // Destination field
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = destinationPath,
            onValueChange = { onDestinationPathChange(it) },
            label = { Text("Destination Folder") },
            placeholder = { Text("Select destination...") },
            modifier = Modifier.weight(1f),
            textStyle = MaterialTheme.typography.bodyMedium,
            singleLine = true,
            isError = destinationPath.isNotBlank() && !destValid,
            supportingText = {
                when {
                    destinationPath.isBlank() ->
                        Text("Paste a path or browse", style = MaterialTheme.typography.labelSmall)
                    !destValid ->
                        Text(
                            "Folder not found",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    else ->
                        Text(
                            destDirName ?: destinationPath,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF2E7D32),
                        )
                }
            },
        )
        OutlinedButton(
            onClick = {
                pickFolder("Select Destination Folder")?.let { onDestinationPathChange(it) }
            },
            modifier = Modifier.height(56.dp),
        ) {
            Icon(Icons.Default.CreateNewFolder, null, Modifier.size(20.dp))
            Spacer(Modifier.width(4.dp))
            Text("Browse")
        }
    }
}
