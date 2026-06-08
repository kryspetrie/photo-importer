package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.awt.Cursor
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Post-export completion screen. Shows context-dependent next actions:
 * - **Batch mode (folder import), more images remaining**: Preview of next photo with "Skip" /
 *   "Continue" buttons
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
    nextBatchFile: File? = null,
    onDone: () -> Unit,
    onImportFile: () -> Unit,
    onImportFolder: () -> Unit,
    onContinueToNextPhoto: () -> Unit,
    onSkipNextPhoto: (() -> Unit)? = null,
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
                    nextBatchFile = nextBatchFile,
                    onCancelImport = onCancelImport,
                    onContinueToNextPhoto = onContinueToNextPhoto,
                    onSkipNextPhoto = onSkipNextPhoto,
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
    nextBatchFile: File?,
    onCancelImport: () -> Unit,
    onContinueToNextPhoto: () -> Unit,
    onSkipNextPhoto: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Next photo preview
        if (nextBatchFile != null) {
            NextPhotoPreview(file = nextBatchFile)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancelImport) { Text("Cancel Import") }
            if (onSkipNextPhoto != null) {
                OutlinedButton(onClick = onSkipNextPhoto) {
                    Icon(Icons.Default.SkipNext, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Skip")
                }
            }
            Button(onClick = onContinueToNextPhoto) { Text("Continue") }
        }
    }
}

/** Shows a small preview of the next photo to be processed. Clicking opens fullscreen. */
@Composable
private fun NextPhotoPreview(file: File, modifier: Modifier = Modifier) {
    var previewBitmap by remember { mutableStateOf<BufferedImage?>(null) }
    var showFullscreen by remember { mutableStateOf(false) }

    // Load preview thumbnail
    LaunchedEffect(file.absolutePath) {
        withContext(Dispatchers.IO) {
            try {
                previewBitmap = ImageIO.read(file)
            } catch (_: Exception) {
                previewBitmap = null
            }
        }
    }

    Card(
        modifier =
            modifier
                .width(240.dp)
                .clip(RoundedCornerShape(8.dp))
                .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR)))
                .clickable { showFullscreen = true },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Next photo:", style = MaterialTheme.typography.labelMedium)

            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .aspectRatio(1.5f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.DarkGray),
                contentAlignment = Alignment.Center,
            ) {
                val img = previewBitmap
                if (img != null) {
                    Image(
                        bitmap = img.toComposeImageBitmap(),
                        contentDescription = "Next photo preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )

                    // Zoom hint overlay
                    Surface(
                        modifier =
                            Modifier.align(Alignment.BottomEnd)
                                .padding(4.dp)
                                .clip(RoundedCornerShape(4.dp)),
                        color = Color.Black.copy(alpha = 0.5f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.ZoomIn,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = Color.White,
                            )
                            Text(
                                "Click to enlarge",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                            )
                        }
                    }
                } else {
                    Text(
                        "Loading preview...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                file.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    // Fullscreen dialog
    if (showFullscreen && previewBitmap != null) {
        FullscreenImageDialog(
            image = previewBitmap!!,
            fileName = file.name,
            onDismiss = { showFullscreen = false },
        )
    }
}

@Composable
private fun FullscreenImageDialog(image: BufferedImage, fileName: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f))
                    .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize().padding(20.dp),
            ) {
                // Title bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        fileName,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.8f),
                        )
                    }
                }

                // Image
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = image.toComposeImageBitmap(),
                        contentDescription = fileName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }

                Spacer(Modifier.height(8.dp))
            }
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
