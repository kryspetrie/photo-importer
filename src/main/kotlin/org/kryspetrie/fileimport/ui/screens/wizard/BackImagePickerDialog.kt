@file:Suppress("TooManyFunctions", "MagicNumber")

package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.RotateRight
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.awt.Cursor
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Mode for the back-image crop overlay interaction. */
private enum class BackImageInteractionMode(val displayName: String) {
    VIEW("View"),
    CROP("Crop"),
}

/**
 * Dialog for selecting a back-of-photo image and optionally cropping a region from it.
 *
 * Two source modes:
 * 1. Pick from batch files (when in batch/folder mode, source files are available)
 * 2. Browse for a file on disk
 *
 * After selecting an image, the user can:
 * - Switch to Crop mode and drag to define a rectangular crop region on the image
 * - Rotate the back image in 90° increments
 * - Choose the back image mode (combine or append_back)
 *
 * @param batchFiles Available batch files for selection, or null if not in batch mode
 * @param onConfirm Called with (sourceFilePath, normalizedCropRect, rotationDegrees, mode) when
 *   confirmed. cropRect is in normalized image coordinates (0-1), or null for the full image.
 * @param onDismiss Called when cancelled
 */
@Suppress("InjectDispatcher")
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
    var interactionMode by remember { mutableStateOf(BackImageInteractionMode.VIEW) }
    var cropRotation by remember { mutableStateOf(0) }
    var showBatchPicker by remember {
        mutableStateOf(batchFiles != null && batchFiles.isNotEmpty())
    }
    // Track view container size for coordinate mapping
    var viewWidthPx by remember { mutableStateOf(0) }
    var viewHeightPx by remember { mutableStateOf(0) }

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

                // ── Source selection row ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
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
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        )
                    }
                }

                // ── Batch file picker ──
                if (showBatchPicker && batchFiles != null) {
                    Spacer(Modifier.height(8.dp))
                    BatchFileGrid(
                        files = batchFiles,
                        selectedFile = selectedFile,
                        onSelect = { file ->
                            selectedFile = file
                            cropRect = null
                            showBatchPicker = false
                        },
                    )
                }

                Spacer(Modifier.height(12.dp))

                // ── Image area with crop overlay ──
                Box(
                    modifier =
                        Modifier.weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.DarkGray)
                            .onGloballyPositioned { coords ->
                                viewWidthPx = coords.size.width
                                viewHeightPx = coords.size.height
                            }
                ) {
                    val img = backImage
                    if (img != null) {
                        Image(
                            bitmap = img.toComposeImageBitmap(),
                            contentDescription = "Back image",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )

                        // Crop overlay canvas — renders the dimming and crop rectangle
                        val currentCrop = cropRect
                        CropOverlayCanvas(
                            cropRect = currentCrop,
                            imageWidth = img.width,
                            imageHeight = img.height,
                            viewWidthPx = viewWidthPx,
                            viewHeightPx = viewHeightPx,
                        )

                        // Drag overlay for defining crop region (only in CROP mode)
                        if (interactionMode == BackImageInteractionMode.CROP) {
                            CropDragOverlay(
                                imageWidth = img.width,
                                imageHeight = img.height,
                                viewWidthPx = viewWidthPx,
                                viewHeightPx = viewHeightPx,
                                onCropUpdate = { rect -> cropRect = rect },
                                onCropEnd = { /* crop is already set */ },
                            )
                        }
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
                                    else "Select a back image to crop",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ── Crop & rotation controls ──
                if (backImage != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Left: Crop mode toggle + clear
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Crop mode toggle button
                            val isInCropMode = interactionMode == BackImageInteractionMode.CROP
                            OutlinedButton(
                                onClick = {
                                    interactionMode =
                                        if (isInCropMode) BackImageInteractionMode.VIEW
                                        else BackImageInteractionMode.CROP
                                    if (interactionMode == BackImageInteractionMode.VIEW) {
                                        // Don't clear crop when switching back to view
                                    }
                                },
                                modifier = Modifier.height(28.dp),
                            ) {
                                Icon(
                                    if (isInCropMode) Icons.Default.CropFree
                                    else Icons.Default.Crop,
                                    contentDescription = "Crop mode",
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (isInCropMode) "Done Cropping" else "Crop",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            if (cropRect != null) {
                                Text(
                                    "%.0f%% × %.0f%%"
                                        .format(
                                            (cropRect!!.width * 100),
                                            (cropRect!!.height * 100),
                                        ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(4.dp))
                                TextButton(
                                    onClick = { cropRect = null },
                                    contentPadding =
                                        androidx.compose.foundation.layout.PaddingValues(
                                            horizontal = 4.dp
                                        ),
                                ) {
                                    Text(
                                        "Remove Crop",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            } else if (!isInCropMode) {
                                Text(
                                    "Click Crop to define a region",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Text(
                                    "Drag on the image to crop",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        // Right: Rotation controls
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Rotate:", style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.width(4.dp))
                            IconButton(
                                onClick = { cropRotation = (cropRotation + 90) % 360 },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    Icons.Default.RotateRight,
                                    "${cropRotation}°",
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Text("${cropRotation}°", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                // ── Mode selection ──
                if (backImage != null) {
                    Spacer(Modifier.height(8.dp))
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

/**
 * Canvas overlay that renders the crop rectangle with dimmed regions outside. All coordinates are
 * in normalized (0-1) space relative to the image, mapped to view pixels accounting for
 * ContentScale.Fit centering.
 */
@Composable
private fun CropOverlayCanvas(
    cropRect: Rect?,
    imageWidth: Int,
    imageHeight: Int,
    viewWidthPx: Int,
    viewHeightPx: Int,
) {
    val currentCrop = cropRect ?: return
    if (viewWidthPx <= 0 || viewHeightPx <= 0) return

    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = Modifier.fillMaxSize()) {
        val (offset, scale) = computeFitMapping(imageWidth, imageHeight, viewWidthPx, viewHeightPx)

        // Pixel coordinates of the crop rect on screen
        val leftPx = currentCrop.left * imageWidth * scale + offset.x
        val topPx = currentCrop.top * imageHeight * scale + offset.y
        val rightPx = currentCrop.right * imageWidth * scale + offset.x
        val bottomPx = currentCrop.bottom * imageHeight * scale + offset.y
        val cropWidthPx = rightPx - leftPx
        val cropHeightPx = bottomPx - topPx

        // Dim outside the crop
        val dimColor = Color.Black.copy(alpha = 0.55f)
        // Top band
        drawRect(dimColor, topLeft = Offset.Zero, size = Size(size.width, topPx))
        // Bottom band
        drawRect(
            dimColor,
            topLeft = Offset(0f, bottomPx),
            size = Size(size.width, size.height - bottomPx),
        )
        // Left band
        drawRect(dimColor, topLeft = Offset(0f, topPx), size = Size(leftPx, cropHeightPx))
        // Right band
        drawRect(
            dimColor,
            topLeft = Offset(rightPx, topPx),
            size = Size(size.width - rightPx, cropHeightPx),
        )

        // Yellow border around crop area
        drawRect(
            color = Color.Yellow,
            topLeft = Offset(leftPx, topPx),
            size = Size(cropWidthPx, cropHeightPx),
            style = Stroke(width = 2.5f),
        )

        // Crop dimension label (percentage of image)
        val pctLabel = "%.0f%% × %.0f%%".format(currentCrop.width * 100, currentCrop.height * 100)
        val labelLayout =
            textMeasurer.measure(pctLabel, TextStyle(color = Color.White, fontSize = 11.sp))
        drawText(textLayoutResult = labelLayout, topLeft = Offset(leftPx + 4f, topPx + 2f))
    }
}

/**
 * Draggable overlay for defining a crop region. Converts view-pixel coordinates to normalized image
 * coordinates accounting for ContentScale.Fit centering.
 */
@Composable
private fun CropDragOverlay(
    imageWidth: Int,
    imageHeight: Int,
    viewWidthPx: Int,
    viewHeightPx: Int,
    onCropUpdate: (Rect) -> Unit,
    onCropEnd: () -> Unit,
) {
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var isDragging by remember { mutableStateOf(false) }

    Box(
        modifier =
            Modifier.fillMaxSize()
                .pointerHoverIcon(PointerIcon(Cursor(Cursor.CROSSHAIR_CURSOR)))
                .pointerInput(imageWidth, imageHeight) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            dragStart = offset
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val start = dragStart ?: return@detectDragGestures
                            val (imgOffset, imgScale) =
                                computeFitMapping(
                                    imageWidth,
                                    imageHeight,
                                    viewWidthPx,
                                    viewHeightPx,
                                )

                            // Convert view-pixel coordinates to normalized [0,1] image coordinates
                            val x1 =
                                ((start.x - imgOffset.x) / (imageWidth * imgScale)).coerceIn(0f, 1f)
                            val y1 =
                                ((start.y - imgOffset.y) / (imageHeight * imgScale)).coerceIn(
                                    0f,
                                    1f,
                                )
                            val x2 =
                                ((change.position.x - imgOffset.x) / (imageWidth * imgScale))
                                    .coerceIn(0f, 1f)
                            val y2 =
                                ((change.position.y - imgOffset.y) / (imageHeight * imgScale))
                                    .coerceIn(0f, 1f)

                            onCropUpdate(
                                Rect(
                                    left = minOf(x1, x2),
                                    top = minOf(y1, y2),
                                    right = maxOf(x1, x2),
                                    bottom = maxOf(y1, y2),
                                )
                            )
                        },
                        onDragEnd = {
                            isDragging = false
                            dragStart = null
                            onCropEnd()
                        },
                        onDragCancel = {
                            isDragging = false
                            dragStart = null
                        },
                    )
                }
    )
}

/**
 * Computes the offset and scale for ContentScale.Fit mapping from image coordinates to view pixels.
 * Account for the letterboxing that ContentScale.Fit introduces (centering the image within the
 * container with possible padding on sides or top/bottom).
 *
 * @return Pair of (pixel offset of image top-left in the view, scale factor from image pixels to
 *   view pixels)
 */
private fun computeFitMapping(
    imageWidth: Int,
    imageHeight: Int,
    viewWidthPx: Int,
    viewHeightPx: Int,
): Pair<Offset, Float> {
    val scaleX = viewWidthPx.toFloat() / imageWidth.toFloat()
    val scaleY = viewHeightPx.toFloat() / imageHeight.toFloat()
    val scale = minOf(scaleX, scaleY)
    val imgDisplayW = imageWidth * scale
    val imgDisplayH = imageHeight * scale
    val offsetX = (viewWidthPx - imgDisplayW) / 2f
    val offsetY = (viewHeightPx - imgDisplayH) / 2f
    return Pair(Offset(offsetX, offsetY), scale)
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
