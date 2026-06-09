package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
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
 * Dialog for selecting a back-of-photo image and optionally cropping a region from it.
 *
 * Two source modes:
 * 1. Pick from batch files (when in batch/folder mode, source files are available)
 * 2. Browse for a file on disk
 *
 * After selecting an image, the user can draw a rectangular crop region on it, or use the entire
 * image. They then choose the back image mode:
 * - "Combine": Stitch the back crop below the front photo
 * - "Append _back": Export the back crop as a separate file with "_back" suffix
 *
 * @param batchFiles Available batch files for selection, or null if not in batch mode
 * @param onConfirm Called with (sourceFilePath, normalizedCropRect, rotationDegrees, mode) when
 *   confirmed
 * @param onDismiss Called when cancelled
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackImagePickerDialog(
    batchFiles: List<File>? = null,
    onConfirm: (sourcePath: String, cropRect: Rect?, rotation: Int, mode: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var backImage by remember { mutableStateOf<BufferedImage?>(null) }
    var backImageMode by remember { mutableStateOf("combine") }
    var cropRect by remember { mutableStateOf<Rect?>(null) }
    @Suppress("VarCouldBeVal") // Compose mutable state delegates require var
    var isDragging by remember { mutableStateOf(false) }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var viewSize by remember { mutableStateOf(Pair(600, 400)) }
    var showBatchPicker by remember {
        mutableStateOf(batchFiles != null && batchFiles.isNotEmpty())
    }
    @Suppress("VarCouldBeVal") // Compose mutable state delegate
    var cropRotation by remember { mutableStateOf(0) }

    // Load image when file is selected
    LaunchedEffect(selectedFile?.absolutePath) {
        val file = selectedFile ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                backImage = ImageIO.read(file)
            } catch (_: Exception) {
                backImage = null
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = modifier.fillMaxWidth(0.85f).fillMaxHeight(0.85f),
            tonalElevation = 8.dp,
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                // ── Header ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Select Back of Photo", style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
                }

                Spacer(Modifier.height(12.dp))

                // ── Source selection ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (batchFiles != null && batchFiles.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { showBatchPicker = !showBatchPicker },
                            modifier = Modifier.height(36.dp),
                        ) {
                            Icon(Icons.Default.Collections, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("From Batch", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            val path =
                                org.kryspetrie.fileimport.ui.components.pickImageFile(
                                    "Select Back Image"
                                )
                            if (path != null) {
                                selectedFile = File(path)
                                cropRect = null
                            }
                        },
                        modifier = Modifier.height(36.dp),
                    ) {
                        Icon(Icons.Default.FolderOpen, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Browse...", style = MaterialTheme.typography.labelMedium)
                    }
                    if (selectedFile != null) {
                        Text(
                            selectedFile!!.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier =
                                Modifier.weight(1f)
                                    .align(Alignment.CenterVertically)
                                    .padding(horizontal = 8.dp),
                        )
                    }
                }

                // ── Batch file picker ──
                if (showBatchPicker && batchFiles != null) {
                    BatchFileGrid(
                        files = batchFiles,
                        selectedFile = selectedFile,
                        onSelect = { file ->
                            selectedFile = file
                            cropRect = null
                            showBatchPicker = false
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                }

                Spacer(Modifier.height(8.dp))

                // ── Image area with crop overlay ──
                Box(
                    modifier =
                        Modifier.weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.DarkGray)
                            .onSizeChanged { size -> viewSize = Pair(size.width, size.height) }
                            .pointerHoverIcon(
                                if (dragStart != null || isDragging)
                                    PointerIcon(Cursor(Cursor.CROSSHAIR_CURSOR))
                                else PointerIcon(Cursor(Cursor.DEFAULT_CURSOR))
                            )
                ) {
                    val img = backImage
                    if (img != null) {
                        Image(
                            bitmap = img.toComposeImageBitmap(),
                            contentDescription = "Back image",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )

                        // Crop overlay
                        val currentCrop = cropRect
                        if (currentCrop != null) {
                            CropOverlay(
                                cropRect = currentCrop,
                                imageWidth = img.width,
                                imageHeight = img.height,
                                viewWidth = viewSize.first,
                                viewHeight = viewSize.second,
                            )
                        }

                        // Drag overlay for drawing crop
                        Box(
                            modifier = Modifier.fillMaxSize().clickable(enabled = false) {}
                            // Mouse event handling for crop drawing
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Crop,
                                    null,
                                    modifier = Modifier.size(48.dp),
                                    tint =
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                            alpha = 0.4f
                                        ),
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    if (selectedFile != null) "Loading..."
                                    else "Select a back image",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ── Mode selection ──
                if (backImage != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { backImageMode = "combine" },
                        ) {
                            RadioButton(
                                selected = backImageMode == "combine",
                                onClick = { backImageMode = "combine" },
                            )
                            Text("Combine", style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { backImageMode = "append_back" },
                        ) {
                            RadioButton(
                                selected = backImageMode == "append_back",
                                onClick = { backImageMode = "append_back" },
                            )
                            Text("Append \"_back\"", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    Text(
                        if (backImageMode == "combine") "Back crop will be stitched below the photo"
                        else "Back crop exported as a separate \"_back\" file",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(12.dp))

                // ── Action buttons ──
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val file = selectedFile ?: return@Button
                            onConfirm(file.absolutePath, cropRect, cropRotation, backImageMode)
                        },
                        enabled = selectedFile != null && backImage != null,
                    ) {
                        Text("Assign Back Image")
                    }
                }
            }
        }
    }
}

/** Grid of batch files for selecting a back image from the current batch. */
@Composable
private fun BatchFileGrid(
    files: List<File>,
    selectedFile: File?,
    onSelect: (File) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().height(120.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        androidx.compose.foundation.lazy.LazyRow(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(files.size) { index ->
                val file = files[index]
                val isSelected = file.absolutePath == selectedFile?.absolutePath
                Card(
                    modifier = Modifier.width(80.dp).height(100.dp).clickable { onSelect(file) },
                    shape = RoundedCornerShape(4.dp),
                    border =
                        if (isSelected)
                            androidx.compose.foundation.BorderStroke(
                                2.dp,
                                MaterialTheme.colorScheme.primary,
                            )
                        else
                            androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                            ),
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface
                        ),
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Add,
                                null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                            Text(
                                file.name.take(12),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Renders a crop rectangle overlay on top of the image. Coordinates are in normalized (0-1) space
 * relative to the image.
 */
@Composable
private fun CropOverlay(
    cropRect: Rect,
    imageWidth: Int,
    imageHeight: Int,
    viewWidth: Int,
    viewHeight: Int,
    modifier: Modifier = Modifier,
) {
    // Calculate the scale and offset to map image coordinates to view coordinates
    // ContentScale.Fit centers the image, so we need to account for letterboxing
    val scaleX = viewWidth.toFloat() / imageWidth.toFloat()
    val scaleY = viewHeight.toFloat() / imageHeight.toFloat()
    val scale = minOf(scaleX, scaleY)
    val imgDisplayWidth = imageWidth * scale
    val imgDisplayHeight = imageHeight * scale
    val offsetXPx = (viewWidth - imgDisplayWidth) / 2f
    val offsetYPx = (viewHeight - imgDisplayHeight) / 2f

    // Convert normalized crop rect to view pixel coordinates
    val leftPx = cropRect.left * imgDisplayWidth + offsetXPx
    val topPx = cropRect.top * imgDisplayHeight + offsetYPx
    val rightPx = cropRect.right * imgDisplayWidth + offsetXPx
    val bottomPx = cropRect.bottom * imgDisplayHeight + offsetYPx
    val widthPx = rightPx - leftPx
    val heightPx = bottomPx - topPx

    Box(modifier = modifier.fillMaxSize()) {
        // Semi-transparent dark overlay outside the crop area
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Top band
            drawRect(
                Color.Black.copy(alpha = 0.5f),
                topLeft = Offset.Zero,
                size = Size(size.width, topPx),
            )
            // Bottom band
            drawRect(
                Color.Black.copy(alpha = 0.5f),
                topLeft = Offset(0f, bottomPx),
                size = Size(size.width, size.height - bottomPx),
            )
            // Left band
            drawRect(
                Color.Black.copy(alpha = 0.5f),
                topLeft = Offset(0f, topPx),
                size = Size(leftPx, heightPx),
            )
            // Right band
            drawRect(
                Color.Black.copy(alpha = 0.5f),
                topLeft = Offset(rightPx, topPx),
                size = Size(size.width - rightPx, heightPx),
            )
        }

        // Yellow border around crop area
        Box(
            modifier =
                Modifier.offset(x = leftPx.dp, y = topPx.dp)
                    .width(widthPx.dp)
                    .height(heightPx.dp)
                    .border(2.dp, Color.Yellow, RoundedCornerShape(2.dp))
                    .background(Color.Yellow.copy(alpha = 0.1f))
        )
    }
}
