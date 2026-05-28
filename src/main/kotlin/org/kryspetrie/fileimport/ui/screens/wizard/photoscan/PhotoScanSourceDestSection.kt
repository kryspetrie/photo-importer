package org.kryspetrie.fileimport.ui.screens.wizard.photoscan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File
import org.kryspetrie.fileimport.ui.components.isImageFile
import org.kryspetrie.fileimport.ui.components.pickFolder
import org.kryspetrie.fileimport.ui.components.pickImageFile

@Composable
fun AutoDetectCard(
    cvAutoDetectEnabled: Boolean,
    onCvAutoDetectChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Auto-detect photo boundaries", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Automatically finds and aligns corners for each photo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = cvAutoDetectEnabled, onCheckedChange = onCvAutoDetectChange)
        }
    }
}

@Composable
fun SourceSelectionSection(
    sourcePath: String,
    onSourcePathChange: (String) -> Unit,
    sourceFile: File?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Source", style = MaterialTheme.typography.titleMedium)

        OutlinedTextField(
            value = sourcePath,
            onValueChange = onSourcePathChange,
            label = { Text("Source") },
            placeholder = { Text("Select source file or folder...") },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyMedium,
            singleLine = true,
            isError = sourcePath.isNotBlank() && sourceFile == null,
            supportingText = {
                when {
                    sourcePath.isBlank() -> Text("Select a scanned image file or folder of images")
                    sourceFile == null ->
                        Text("Path not found", color = MaterialTheme.colorScheme.error)
                    sourceFile.isDirectory -> {
                        val imageCount =
                            sourceFile.listFiles { f -> f.isFile && isImageFile(f) }?.size ?: 0
                        Text("Folder: $imageCount image(s)")
                    }
                    else -> Text("File: ${sourceFile.name}")
                }
            },
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { pickImageFile("Select Image File")?.let { onSourcePathChange(it) } },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Image, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Select File")
            }
            OutlinedButton(
                onClick = { pickFolder("Select Folder")?.let { onSourcePathChange(it) } },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Folder, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Select Folder")
            }
        }
    }
}

@Composable
fun DestinationSelectionSection(
    destinationPath: String,
    onDestinationPathChange: (String) -> Unit,
    destValid: Boolean,
    destDirName: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Destination", style = MaterialTheme.typography.titleMedium)

        OutlinedTextField(
            value = destinationPath,
            onValueChange = onDestinationPathChange,
            label = { Text("Destination Folder") },
            placeholder = { Text("Select destination...") },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyMedium,
            singleLine = true,
            isError = destinationPath.isNotBlank() && !destValid,
            supportingText = {
                when {
                    destinationPath.isBlank() -> Text("Paste a path or browse")
                    !destValid -> Text("Folder not found", color = MaterialTheme.colorScheme.error)
                    else -> Text(destDirName ?: "")
                }
            },
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = {
                    pickFolder("Select Destination Folder")?.let { onDestinationPathChange(it) }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.CreateNewFolder, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Select Destination Folder")
            }
        }
    }
}
