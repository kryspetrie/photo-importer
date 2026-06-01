package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Post-export completion screen. Shows context-dependent next actions:
 * - **Batch mode (folder import), more images remaining**: "Cancel Import" / "Continue to Next
 *   Photo"
 * - **Batch mode (folder import), last image**: "Done" / "Import File" / "Import Folder"
 * - **Single file import**: "Done" / "Import File" / "Import Folder"
 */
@Composable
fun CompletionScreen(
    photoCount: Int,
    exportDestination: String,
    isBatchMode: Boolean,
    hasMoreBatchImages: Boolean,
    currentBatchIndex: Int,
    batchTotal: Int,
    onDone: () -> Unit,
    onImportFile: () -> Unit,
    onImportFolder: () -> Unit,
    onContinueToNextPhoto: () -> Unit,
    onCancelImport: () -> Unit,
    onOpenFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            // Success icon
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary,
            )

            Text("Complete", style = MaterialTheme.typography.headlineMedium)

            // Export details card
            Card(modifier = Modifier.fillMaxWidth(0.6f)) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "$photoCount photo(s) exported",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    if (exportDestination.isNotBlank()) {
                        Text(
                            exportDestination,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Batch progress indicator
                    if (isBatchMode) {
                        val progress = (currentBatchIndex + 1).toFloat() / batchTotal
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                        Text(
                            "Photo ${currentBatchIndex + 1} of $batchTotal",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Open folder button
            OutlinedButton(onClick = onOpenFolder) {
                Icon(Icons.Default.FolderOpen, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Open Folder")
            }

            // Context-dependent action buttons
            if (isBatchMode && hasMoreBatchImages) {
                // Batch mode: more images to process
                BatchActions(
                    onCancelImport = onCancelImport,
                    onContinueToNextPhoto = onContinueToNextPhoto,
                )
            } else {
                // Single file or last image in batch
                FinishedActions(
                    onDone = onDone,
                    onImportFile = onImportFile,
                    onImportFolder = onImportFolder,
                )
            }
        }
    }
}

@Composable
private fun BatchActions(
    onCancelImport: () -> Unit,
    onContinueToNextPhoto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "More photos in folder to process",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(onClick = onCancelImport) { Text("Cancel Import") }
            Button(onClick = onContinueToNextPhoto) { Text("Continue to Next Photo") }
        }
    }
}

@Composable
private fun FinishedActions(
    onDone: () -> Unit,
    onImportFile: () -> Unit,
    onImportFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth(0.5f).height(48.dp)) {
            Text("Done")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(onClick = onImportFile) {
                Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Import File")
            }
            OutlinedButton(onClick = onImportFolder) {
                Icon(Icons.Default.CreateNewFolder, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Import Folder")
            }
        }
    }
}
