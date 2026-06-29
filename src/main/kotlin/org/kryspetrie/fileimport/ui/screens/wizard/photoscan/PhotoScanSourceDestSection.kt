package org.kryspetrie.fileimport.ui.screens.wizard.photoscan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
        Column(modifier = Modifier.padding(16.dp)) {
            SettingsToggle(
                checked = cvAutoDetectEnabled,
                onCheckedChange = onCvAutoDetectChange,
                label = "Auto-detect photo boundaries",
                description = "Automatically finds and aligns corners for each photo",
                icon = Icons.Default.AutoAwesome,
            )
        }
    }
}

@Composable
fun SinglePhotoModeCard(
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
        Column(modifier = Modifier.padding(16.dp)) {
            SettingsToggle(
                checked = singlePhotoMode,
                onCheckedChange = onSinglePhotoModeChange,
                label = "Single Photo mode",
                description = "Import one photo directly — skip multi-photo detection",
                icon = Icons.Default.PhotoCamera,
            )
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

        SourcePathField(
            value = sourcePath,
            onValueChange = onSourcePathChange,
            onPickFile = { pickImageFile("Select Image File")?.let(onSourcePathChange) },
            onPickFolder = { pickFolder("Select Source Folder")?.let(onSourcePathChange) },
            modifier = Modifier.fillMaxWidth(),
            label = "Source",
            placeholder = "Select source file or folder...",
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
    }
}

@Composable
fun DestinationSelectionSection(
    destinationPath: String,
    onDestinationPathChange: (String) -> Unit,
    destValid: Boolean,
    destCanCreate: Boolean = false,
    destDirName: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Destination", style = MaterialTheme.typography.titleMedium)

        FolderSelectionField(
            value = destinationPath,
            onValueChange = onDestinationPathChange,
            modifier = Modifier.fillMaxWidth(),
            label = "Destination Folder",
            placeholder = "Select destination...",
            title = "Select Destination Folder",
            isError = destinationPath.isNotBlank() && !destValid && !destCanCreate,
            supportingText = {
                when {
                    destinationPath.isBlank() -> Text("Paste a path or browse")
                    !destValid && !destCanCreate ->
                        Text(
                            "Drive or parent path not accessible",
                            color = MaterialTheme.colorScheme.error,
                        )
                    !destValid && destCanCreate ->
                        Text("Folder will be created", color = MaterialTheme.colorScheme.primary)
                    else -> Text(destDirName.orEmpty())
                }
            },
        )
    }
}