package org.kryspetrie.fileimport.ui.screens.wizard.photoscan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

import androidx.compose.ui.unit.dp
import java.io.File
import org.kryspetrie.fileimport.ui.components.FolderSelectionField
import org.kryspetrie.fileimport.ui.components.SettingsToggle
import org.kryspetrie.fileimport.ui.components.SourcePathField
import org.kryspetrie.fileimport.ui.components.isImageFile
import org.kryspetrie.fileimport.ui.components.pickFolder
import org.kryspetrie.fileimport.ui.components.pickImageFile

@Composable
fun ScanModeCard(
    cvAutoDetectEnabled: Boolean,
    onCvAutoDetectChange: (Boolean) -> Unit,
    singlePhotoMode: Boolean,
    onSinglePhotoModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
    ) {
        Row(modifier = Modifier.padding(10.dp)) {
            Column(Modifier.weight(1f)) {
                SettingsToggle(
                    checked = cvAutoDetectEnabled,
                    onCheckedChange = onCvAutoDetectChange,
                    label = "Auto-detect",
                    description = "Find and align corners",
                    icon = Icons.Default.AutoAwesome,
                )
            }
            Column(Modifier.weight(1f)) {
                SettingsToggle(
                    checked = singlePhotoMode,
                    onCheckedChange = onSinglePhotoModeChange,
                    label = "Single Photo",
                    description = "Skip multi-photo detection",
                    icon = Icons.Default.PhotoCamera,
                )
            }
        }
    }
}

@Composable
fun SourceDestRow(
    sourcePath: String,
    onSourcePathChange: (String) -> Unit,
    sourceFile: File?,
    destinationPath: String,
    onDestinationPathChange: (String) -> Unit,
    destValid: Boolean,
    destCanCreate: Boolean = false,
    destDirName: String?,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SourcePathField(
            value = sourcePath,
            onValueChange = onSourcePathChange,
            onPickFile = { pickImageFile("Select Image File")?.let(onSourcePathChange) },
            onPickFolder = { pickFolder("Select Source Folder")?.let(onSourcePathChange) },
            modifier = Modifier.weight(1f),
            label = "Source",
            placeholder = "Select source file or folder...",
            isError = sourcePath.isNotBlank() && sourceFile == null,
            supportingText = {
                when {
                    sourcePath.isBlank() -> Text("File or folder of images")
                    sourceFile == null ->
                        Text("Path not found", color = MaterialTheme.colorScheme.error)
                    sourceFile.isDirectory -> {
                        val imageCount =
                            sourceFile.listFiles { f -> f.isFile && isImageFile(f) }?.size ?: 0
                        Text("$imageCount image(s)")
                    }
                    else -> Text(sourceFile.name)
                }
            },
        )

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
                    destinationPath.isBlank() -> Text("Paste a path or browse")
                    !destValid && !destCanCreate ->
                        Text(
                            "Path not accessible",
                            color = MaterialTheme.colorScheme.error,
                        )
                    !destValid && destCanCreate ->
                        Text("Will be created", color = MaterialTheme.colorScheme.primary)
                    else -> Text(destDirName.orEmpty())
                }
            },
        )
    }
}