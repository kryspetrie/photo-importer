package org.kryspetrie.fileimport.ui.screens.mediaimport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
 * Laid out side-by-side in a single row for compact desktop display.
 *
 * Validation rules:
 * - **Source**: Must be an existing directory (the source must exist to read from it).
 * - **Destination**: Must be on an accessible drive — the folder will be created if it doesn't
 *   exist. Only shows an error if the parent path/root is inaccessible.
 */
@Composable
fun SourceDestinationFields(
    sourcePath: String,
    onSourcePathChange: (String) -> Unit,
    destinationPath: String,
    onDestinationPathChange: (String) -> Unit,
    sourceValid: Boolean,
    destValid: Boolean,
    destCanCreate: Boolean,
    sourceDirName: String?,
    destDirName: String?,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Source field
        FolderSelectionField(
            value = sourcePath,
            onValueChange = onSourcePathChange,
            modifier = Modifier.weight(1f),
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
                            sourceDirName.orEmpty(),
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
            modifier = Modifier.weight(1f),
            label = "Destination",
            placeholder = "Select destination...",
            title = "Select Destination Folder",
            isError = destinationPath.isNotBlank() && !destValid && !destCanCreate,
            supportingText = {
                when {
                    destinationPath.isBlank() ->
                        Text("Paste a path or browse", style = MaterialTheme.typography.labelSmall)
                    !destValid && !destCanCreate ->
                        Text(
                            "Path not accessible",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    !destValid && destCanCreate ->
                        Text(
                            "Will be created",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF1565C0),
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
