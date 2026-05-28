package org.kryspetrie.fileimport.ui.screens.wizard.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Dialog for selecting an export destination folder with quick-access buttons. */
@Composable
fun FolderPickerDialog(
    initialPath: String,
    onPathSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var folderPath by remember { mutableStateOf(initialPath) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Export Destination") },
        text = {
            FolderPickerContent(folderPath = folderPath, onFolderPathChange = { folderPath = it })
        },
        confirmButton = {
            Button(
                onClick = {
                    val path = folderPath.trim()
                    if (path.isNotBlank()) {
                        val dir = java.io.File(path)
                        if (!dir.exists()) {
                            dir.mkdirs()
                        }
                        onPathSelected(path)
                    }
                },
                enabled = folderPath.isNotBlank(),
            ) {
                Text("Select")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun FolderPickerContent(folderPath: String, onFolderPathChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Enter the folder path where photos will be exported:",
            style = MaterialTheme.typography.bodyMedium,
        )

        OutlinedTextField(
            value = folderPath,
            onValueChange = onFolderPathChange,
            label = { Text("Folder path") },
            placeholder = { Text("/path/to/folder") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        QuickAccessRow(onFolderPathChange = onFolderPathChange)
    }
}

/** Row of quick-access buttons for common folder paths. */
@Composable
private fun QuickAccessRow(onFolderPathChange: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        TextButton(
            onClick = { onFolderPathChange(System.getProperty("user.home") + "/Pictures") }
        ) {
            Text("Pictures")
        }
        TextButton(
            onClick = {
                onFolderPathChange(System.getProperty("user.home") + "/Pictures/PhotoScan")
            }
        ) {
            Text("PhotoScan")
        }
        TextButton(onClick = { onFolderPathChange(System.getProperty("user.home") + "/Desktop") }) {
            Text("Desktop")
        }
    }
}
