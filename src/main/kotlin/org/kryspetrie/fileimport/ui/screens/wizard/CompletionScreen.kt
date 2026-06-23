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
import org.kryspetrie.fileimport.ui.screens.wizard.ExportResult
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
    skippedCount: Int = 0,
    failedCount: Int = 0,
    exportResults: List<ExportResult> = emptyList(),
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
                        if (failedCount > 0) "$photoCount ${if (photoCount == 1) "photo" else "photos"} exported ($failedCount failed)"
                        else "$photoCount ${if (photoCount == 1) "photo" else "photos"} exported",
                        style = MaterialTheme.typography.titleMedium,
                        color =
                            if (failedCount > 0) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
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
                        if (skippedCount > 0) {
                            Text(
                                "($skippedCount skipped as photo backs)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Per-photo export results summary
            if (exportResults.isNotEmpty()) {
                ExportResultsSummary(results = exportResults, modifier = Modifier.fillMaxWidth(0.6f))
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

/** Preview load state: loading, success, or error. */
private sealed class PreviewState {
    data object Loading : PreviewState()

    data class Success(val image: BufferedImage) : PreviewState()

    data class Error(val message: String) : PreviewState()
}

/** Shows a small preview of the next photo to be processed. Clicking opens fullscreen. */
@Suppress("InjectDispatcher")
@Composable
private fun NextPhotoPreview(file: File, modifier: Modifier = Modifier) {
    var previewState by remember { mutableStateOf<PreviewState>(PreviewState.Loading) }
    var showFullscreen by remember { mutableStateOf(false) }

    // Load preview thumbnail asynchronously with error tracking
    LaunchedEffect(file.absolutePath) {
        previewState = PreviewState.Loading
        withContext(Dispatchers.IO) {
            try {
                val image = ImageIO.read(file)
                if (image != null) {
                    previewState = PreviewState.Success(image)
                } else {
                    previewState = PreviewState.Error("Unsupported format")
                }
            } catch (e: Exception) {
                previewState = PreviewState.Error(e.message ?: "Failed to load")
            }
        }
    }

    Card(
        modifier =
            modifier
                .width(240.dp)
                .clip(RoundedCornerShape(8.dp))
                .then(
                    if (previewState is PreviewState.Success) {
                        Modifier.pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR)))
                            .clickable { showFullscreen = true }
                    } else {
                        Modifier
                    }
                ),
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
                when (val state = previewState) {
                    is PreviewState.Success -> {
                        Image(
                            bitmap = state.image.toComposeImageBitmap(),
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
                    }
                    is PreviewState.Error -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Error",
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                "Could not load preview",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                state.message,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    is PreviewState.Loading -> {
                        Text(
                            "Loading preview...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
    if (showFullscreen && previewState is PreviewState.Success) {
        FullscreenImageDialog(
            image = (previewState as PreviewState.Success).image,
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

/**
 * Displays a compact summary of per-photo export results, showing success/fail status and output
 * path for each photo.
 */
@Composable
private fun ExportResultsSummary(
    results: List<ExportResult>,
    modifier: Modifier = Modifier,
) {
    if (results.isEmpty()) return

    val successCount = results.count { it is ExportResult.Success }
    val failureCount = results.count { it is ExportResult.Failure }

    Card(modifier = modifier, shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Summary header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Export Results", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (successCount > 0) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color(0xFF4CAF50),
                            )
                            Text(
                                "$successCount ${if (successCount == 1) "success" else "successes"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF4CAF50),
                            )
                        }
                    }
                    if (failureCount > 0) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                "$failureCount ${if (failureCount == 1) "failure" else "failures"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            // Per-photo result rows
            results.forEachIndexed { index, result ->
                val isSuccess = result is ExportResult.Success
                val icon = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Close
                val iconTint = if (isSuccess) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error

                Surface(
                    tonalElevation = if (isSuccess) 0.dp else 1.dp,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = iconTint)
                        Text("Photo ${index + 1}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(60.dp))
                        when (result) {
                            is ExportResult.Success -> {
                                val fileName = result.outputPath.substringAfterLast('/')
                                Text(
                                    fileName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "${result.dimensions.first}×${result.dimensions.second}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            is ExportResult.Failure -> {
                                Text(
                                    result.errorMessage,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
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
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth(0.5f).height(40.dp)) {
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
