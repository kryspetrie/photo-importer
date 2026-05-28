package org.kryspetrie.fileimport.ui.screens.mediaimport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.ui.components.FolderSelectionField

/**
 * Source and destination folder path selection fields with validation for the media import screen.
 *
 * Uses the consistent [FolderSelectionField] component for both source and destination, with
 * trailing browse buttons and validation indicators.
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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Source field
        FolderSelectionField(
            value = sourcePath,
            onValueChange = onSourcePathChange,
            modifier = Modifier.fillMaxWidth(),
            label = "Source Folder",
            placeholder = "Select source...",
            title = "Select Source Folder",
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

        // Destination field
        FolderSelectionField(
            value = destinationPath,
            onValueChange = onDestinationPathChange,
            label = "Destination Folder",
            placeholder = "Select destination...",
            title = "Select Destination Folder",
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
    }
}
