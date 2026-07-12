@file:Suppress("TooManyFunctions", "MagicNumber")

package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
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
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
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
import org.kryspetrie.fileimport.ui.wizard.state.ZoomController

/** Mode for the back-image crop overlay interaction. */
private enum class BackImageInteractionMode(val displayName: String) {
    VIEW("View"),
    CROP("Crop"),
}

/**
 * Transform a normalized crop rectangle for a 90° clockwise image rotation.
 *
 * When the displayed image rotates 90° CW, normalized coordinates transform as (x, y) → (1-y, x),
 * and width/height swap. This matches [FaceRegion.rotate90CW].
 */
internal fun Rect.rotate90CW(): Rect {
    // Transform all four corners
    val x1 = 1.0f - bottom // top-left.x from (1-y_of_bottom)
    val y1 = left // top-left.y from (x_of_left)
    val x2 = 1.0f - top // bottom-right.x from (1-y_of_top)
    val y2 = right // bottom-right.y from (x_of_right)
    return Rect(
        left = minOf(x1, x2),
        top = minOf(y1, y2),
        right = maxOf(x1, x2),
        bottom = maxOf(y1, y2),
    )
}

/**
 * Transform a normalized crop rectangle for a 90° counter-clockwise image rotation.
 *
 * When the displayed image rotates 90° CCW, normalized coordinates transform as (x, y) → (y, 1-x).
 * This matches [FaceRegion.rotate90CCW].
 */
internal fun Rect.rotate90CCW(): Rect {
    val x1 = top // top-left.x from (y_of_top)
    val y1 = 1.0f - right // top-left.y from (1-x_of_right)
    val x2 = bottom // bottom-right.x from (y_of_bottom)
    val y2 = 1.0f - left // bottom-right.y from (1-x_of_left)
    return Rect(
        left = minOf(x1, x2),
        top = minOf(y1, y2),
        right = maxOf(x1, x2),
        bottom = maxOf(y1, y2),
    )
}

/**
 * Transform a normalized crop rectangle for a 180° image rotation.
 *
 * When the displayed image rotates 180°, normalized coordinates transform as (x, y) → (1-x, 1-y).
 * This matches [FaceRegion.rotate180].
 */
internal fun Rect.rotate180(): Rect {
    return Rect(left = 1.0f - right, top = 1.0f - bottom, right = 1.0f - left, bottom = 1.0f - top)
}

/**
 * Dialog for selecting a back-of-photo image and optionally cropping a region from it.
 *
 * Two source modes:
 * 1. Pick from batch files (when in batch/folder mode, source files are available)
 * 2. Browse for a file on disk
 *
 * After selecting an image, the user can:
 * - Zoom and pan to find small text (mouse wheel to zoom, drag to pan in VIEW mode)
 * - Switch to Crop mode and drag to define a rectangular crop region
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
    preSelectedPath: String? = null,
    onConfirm: (sourcePath: String, cropRect: Rect?, rotation: Int, mode: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Auto-select the pre-selected path or next sequential batch file
    val initialFile =
        remember(preSelectedPath, batchFiles) {
            if (preSelectedPath != null) {
                File(preSelectedPath).takeIf { it.exists() }
            } else {
                // Default to next sequential file from batch after the current (often the back
                // photo)
                batchFiles?.firstOrNull()
            }
        }
    var selectedFile by remember { mutableStateOf(initialFile) }
    var backImage by remember { mutableStateOf<BufferedImage?>(null) }
    var backImageMode by remember { mutableStateOf("combine") }
    var cropRect by remember { mutableStateOf<Rect?>(null) }
    // Auto-enter crop mode when a file is pre-selected (happy path: user wants to crop the back)
    var interactionMode by remember {
        mutableStateOf(
            if (initialFile != null) BackImageInteractionMode.CROP
            else BackImageInteractionMode.VIEW
        )
    }
    var cropRotation by remember { mutableStateOf(0) }

    // Zoom/pan state — local to this dialog, not shared with wizard
    var zoomController by remember { mutableStateOf(ZoomController()) }

    // Track view container size for coordinate mapping
    var viewSize by remember { mutableStateOf(IntSize.Zero) }

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

    // Fit image to view when image loads or rotation changes
    val displayImage =
        remember(backImage, cropRotation) {
            val img = backImage ?: return@remember null
            val rotation = rotationFromDegrees(cropRotation)
            if (rotation != org.kryspetrie.fileimport.domain.model.RotationAngle.NONE) {
                rotateBufferedImage(img, rotation)
            } else {
                img
            }
        }

    // Auto-fit when the image or viewport size changes
    LaunchedEffect(displayImage, viewSize) {
        val img = displayImage ?: return@LaunchedEffect
        if (viewSize.width > 0 && viewSize.height > 0) {
            zoomController =
                ZoomController.fit(
                    img.width.toDouble(),
                    img.height.toDouble(),
                    viewSize.width.toDouble(),
                    viewSize.height.toDouble(),
                )
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

                Spacer(Modifier.height(8.dp))

                // ── Source selection row (compact: file name + Browse) ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selectedFile != null) {
                        Text(
                            selectedFile!!.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Text(
                            "No back image selected",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
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
                        modifier = Modifier.height(28.dp),
                    ) {
                        Icon(Icons.Default.FolderOpen, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Browse...", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Spacer(Modifier.height(8.dp))

                // ── Image area with zoom/pan and crop overlay ──
                Box(
                    modifier =
                        Modifier.weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.DarkGray)
                            .onGloballyPositioned { coords -> viewSize = coords.size }
                ) {
                    val img = displayImage
                    if (img != null) {
                        // Canvas-based rendering with zoom/pan support
                        BackImageCanvas(
                            image = img,
                            cropRect = cropRect,
                            zoomController = zoomController,
                            onZoomControllerChange = { zoomController = it },
                            interactionMode = interactionMode,
                            onCropUpdate = { rect -> cropRect = rect },
                            onCropEnd = { /* crop is already set */ },
                            modifier = Modifier.fillMaxSize(),
                        )

                        // Zoom controls overlay (top-right)
                        BackImageZoomControls(
                            zoomController = zoomController,
                            onZoomIn = {
                                val cx = viewSize.width.toDouble() / 2
                                val cy = viewSize.height.toDouble() / 2
                                zoomController = zoomController.zoomIn(cx, cy)
                            },
                            onZoomOut = {
                                val cx = viewSize.width.toDouble() / 2
                                val cy = viewSize.height.toDouble() / 2
                                zoomController = zoomController.zoomOut(cx, cy)
                            },
                            onFitToView = {
                                if (viewSize.width > 0 && viewSize.height > 0) {
                                    zoomController =
                                        ZoomController.fit(
                                            img.width.toDouble(),
                                            img.height.toDouble(),
                                            viewSize.width.toDouble(),
                                            viewSize.height.toDouble(),
                                        )
                                }
                            },
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
                                onClick = {
                                    cropRotation = (cropRotation - 90 + 360) % 360
                                    cropRect = cropRect?.rotate90CCW()
                                },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.RotateLeft,
                                    "Rotate CCW",
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            IconButton(
                                onClick = {
                                    cropRotation = (cropRotation + 180) % 360
                                    cropRect = cropRect?.rotate180()
                                },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    "Rotate 180°",
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            IconButton(
                                onClick = {
                                    cropRotation = (cropRotation + 90) % 360
                                    cropRect = cropRect?.rotate90CW()
                                },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.RotateRight,
                                    "Rotate CW",
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            // Fixed-width degree text to avoid jumping
                            Text(
                                "${cropRotation}°",
                                style = MaterialTheme.typography.labelSmall,
                                color =
                                    if (cropRotation != 0) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(36.dp),
                            )
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

                Spacer(Modifier.height(8.dp))

                // ── Nearby files strip ──
                if (batchFiles != null && batchFiles.isNotEmpty()) {
                    NearbyFilesStrip(
                        files = batchFiles,
                        selectedFile = selectedFile,
                        onSelect = { file ->
                            selectedFile = file
                            cropRect = null
                            cropRotation = 0
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                }

                Spacer(Modifier.height(4.dp))

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
 * Canvas composable that renders the back image with zoom/pan support, crop overlay, and gesture
 * handling.
 *
 * Uses the same approach as OverviewCanvas: draw the image at offset+scale determined by
 * ZoomController, then overlay the crop rectangle in screen coordinates, and handle pointer events
 * for scrolling (zoom), panning (VIEW mode), and cropping (CROP mode).
 */
@Composable
private fun BackImageCanvas(
    image: BufferedImage,
    cropRect: Rect?,
    zoomController: ZoomController,
    onZoomControllerChange: (ZoomController) -> Unit,
    interactionMode: BackImageInteractionMode,
    onCropUpdate: (Rect) -> Unit,
    onCropEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val currentCrop = cropRect

    // Drag state for crop mode
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var isDragging by remember { mutableStateOf(false) }
    var totalMovement by remember { mutableStateOf(0f) }

    // Use sampled image for performance when zoomed out
    val scale = zoomController.zoom.toFloat()
    val panX = zoomController.panX.toFloat()
    val panY = zoomController.panY.toFloat()

    val displayBitmap =
        remember(image, scale) {
            val sampled =
                org.kryspetrie.fileimport.ui.screens.wizard.overview.createSampledImage(
                    image,
                    scale.toDouble(),
                )
            sampled?.toComposeImageBitmap()
        }

    Canvas(
        modifier =
            modifier
                .pointerInput(interactionMode, zoomController) {
                    // Unified pointer handler for zoom/pan/crop
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val pos = event.changes.firstOrNull()?.position ?: continue

                            when (event.type) {
                                PointerEventType.Press -> {
                                    dragStart = pos
                                    isDragging = true
                                    totalMovement = 0f
                                }
                                PointerEventType.Move -> {
                                    if (isDragging && dragStart != null) {
                                        val delta =
                                            pos -
                                                (event.changes.firstOrNull()?.previousPosition
                                                    ?: pos)
                                        totalMovement += delta.getDistance()

                                        if (interactionMode == BackImageInteractionMode.CROP) {
                                            // In crop mode, dragging defines the crop rectangle
                                            val start =
                                                zoomController.screenToImage(
                                                    dragStart!!.x.toDouble(),
                                                    dragStart!!.y.toDouble(),
                                                )
                                            val current =
                                                zoomController.screenToImage(
                                                    pos.x.toDouble(),
                                                    pos.y.toDouble(),
                                                )
                                            // Convert to normalized [0,1] coordinates
                                            val x1 = (start.x / image.width).coerceIn(0.0, 1.0)
                                            val y1 = (start.y / image.height).coerceIn(0.0, 1.0)
                                            val x2 = (current.x / image.width).coerceIn(0.0, 1.0)
                                            val y2 = (current.y / image.height).coerceIn(0.0, 1.0)
                                            onCropUpdate(
                                                Rect(
                                                    left = minOf(x1, x2).toFloat(),
                                                    top = minOf(y1, y2).toFloat(),
                                                    right = maxOf(x1, x2).toFloat(),
                                                    bottom = maxOf(y1, y2).toFloat(),
                                                )
                                            )
                                        } else {
                                            // In view mode, drag to pan
                                            val newZoom =
                                                zoomController.pan(
                                                    delta.x.toDouble(),
                                                    delta.y.toDouble(),
                                                )
                                            onZoomControllerChange(newZoom)
                                        }
                                    }
                                }
                                PointerEventType.Release -> {
                                    isDragging = false
                                    dragStart = null
                                    // If we were crop-dragging and released, finalize
                                    if (
                                        interactionMode == BackImageInteractionMode.CROP &&
                                            totalMovement > 5f
                                    ) {
                                        onCropEnd()
                                    }
                                }
                                PointerEventType.Scroll -> {
                                    // Mouse wheel zoom around cursor position
                                    val scrollDelta =
                                        event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                    if (scrollDelta != 0f) {
                                        val newZoom =
                                            if (scrollDelta > 0) {
                                                zoomController.zoomIn(
                                                    pos.x.toDouble(),
                                                    pos.y.toDouble(),
                                                )
                                            } else {
                                                zoomController.zoomOut(
                                                    pos.x.toDouble(),
                                                    pos.y.toDouble(),
                                                )
                                            }
                                        onZoomControllerChange(newZoom)
                                    }
                                }
                            }
                        }
                    }
                }
                .pointerHoverIcon(
                    if (interactionMode == BackImageInteractionMode.CROP)
                        androidx.compose.ui.input.pointer.PointerIcon(
                            java.awt.Cursor(java.awt.Cursor.CROSSHAIR_CURSOR)
                        )
                    else
                        androidx.compose.ui.input.pointer.PointerIcon(
                            java.awt.Cursor(java.awt.Cursor.HAND_CURSOR)
                        )
                )
    ) {
        // Draw background
        drawRect(color = Color(0xFF404040.toInt())) // Dark gray background

        // Draw image at zoom/pan position
        if (displayBitmap != null) {
            drawImage(
                image = displayBitmap,
                srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
                srcSize =
                    androidx.compose.ui.unit.IntSize(displayBitmap.width, displayBitmap.height),
                dstOffset = androidx.compose.ui.unit.IntOffset(panX.toInt(), panY.toInt()),
                dstSize =
                    androidx.compose.ui.unit.IntSize(
                        (image.width * scale).toInt(),
                        (image.height * scale).toInt(),
                    ),
            )
        }

        // Draw crop overlay if a crop rect is defined
        val crop = currentCrop
        if (crop != null && image.width > 0 && image.height > 0) {
            // Convert normalized crop coordinates to screen coordinates using zoom/pan
            val cropLeftPx = (crop.left * image.width * scale) + panX
            val cropTopPx = (crop.top * image.height * scale) + panY
            val cropRightPx = (crop.right * image.width * scale) + panX
            val cropBottomPx = (crop.bottom * image.height * scale) + panY
            val cropWidthPx = cropRightPx - cropLeftPx
            val cropHeightPx = cropBottomPx - cropTopPx

            // Dim outside the crop
            val dimColor = Color.Black.copy(alpha = 0.55f)
            // Top band
            drawRect(dimColor, topLeft = Offset(0f, 0f), size = Size(size.width, cropTopPx))
            // Bottom band
            drawRect(
                dimColor,
                topLeft = Offset(0f, cropBottomPx),
                size = Size(size.width, size.height - cropBottomPx),
            )
            // Left band
            drawRect(
                dimColor,
                topLeft = Offset(0f, cropTopPx),
                size = Size(cropLeftPx, cropHeightPx),
            )
            // Right band
            drawRect(
                dimColor,
                topLeft = Offset(cropRightPx, cropTopPx),
                size = Size(size.width - cropRightPx, cropHeightPx),
            )

            // Yellow border around crop area
            drawRect(
                color = Color.Yellow,
                topLeft = Offset(cropLeftPx, cropTopPx),
                size = Size(cropWidthPx, cropHeightPx),
                style = Stroke(width = 2.5f),
            )

            // Crop dimension label
            val pctLabel = "%.0f%% × %.0f%%".format(crop.width * 100, crop.height * 100)
            val labelLayout =
                textMeasurer.measure(pctLabel, TextStyle(color = Color.White, fontSize = 11.sp))
            drawText(
                textLayoutResult = labelLayout,
                topLeft = Offset(cropLeftPx + 4f, cropTopPx + 2f),
            )
        }

        // Draw drag preview while actively cropping
        if (isDragging && dragStart != null && interactionMode == BackImageInteractionMode.CROP) {
            // Light crosshair at start position
            drawLine(
                color = Color.White.copy(alpha = 0.5f),
                start = Offset(dragStart!!.x - 10f, dragStart!!.y),
                end = Offset(dragStart!!.x + 10f, dragStart!!.y),
                strokeWidth = 1f,
            )
            drawLine(
                color = Color.White.copy(alpha = 0.5f),
                start = Offset(dragStart!!.x, dragStart!!.y - 10f),
                end = Offset(dragStart!!.x, dragStart!!.y + 10f),
                strokeWidth = 1f,
            )
        }
    }
}

/** Zoom controls overlay for the back image canvas. */
@Composable
private fun BackImageZoomControls(
    zoomController: ZoomController,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onFitToView: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(4.dp),
            ) {
                IconButton(onClick = onZoomOut, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ZoomOut, "Zoom out", Modifier.size(18.dp))
                }

                Text(
                    zoomController.zoomPercent(),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(48.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )

                IconButton(onClick = onZoomIn, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ZoomIn, "Zoom in", Modifier.size(18.dp))
                }

                IconButton(onClick = onFitToView, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.FitScreen, "Fit to view", Modifier.size(18.dp))
                }
            }
        }
    }
}

/** Horizontal scrolling strip of nearby batch files for quick back-image selection. */
@Suppress("InjectDispatcher")
@Composable
private fun NearbyFilesStrip(
    files: List<File>,
    selectedFile: File?,
    onSelect: (File) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Show up to 20 files around the currently selected file, or from the start
    val selectedIndex = files.indexOf(selectedFile).coerceAtLeast(0)
    val windowSize = 20
    val startIdx = (selectedIndex - windowSize / 2).coerceIn(0, maxOf(0, files.size - windowSize))
    val endIdx = minOf(startIdx + windowSize, files.size)
    val nearbyFiles = files.subList(startIdx, endIdx)

    val listState =
        rememberLazyListState(initialFirstVisibleItemIndex = maxOf(0, selectedIndex - startIdx - 2))

    Column(modifier = modifier.fillMaxWidth()) {
        Text("Nearby files:", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(nearbyFiles) { _, file ->
                val isSelected = file.absolutePath == selectedFile?.absolutePath
                val thumbnail =
                    remember(file.absolutePath) {
                        // Load a small thumbnail asynchronously
                        mutableStateOf<BufferedImage?>(null)
                    }
                LaunchedEffect(file.absolutePath) {
                    withContext(Dispatchers.IO) {
                        try {
                            val img = ImageIO.read(file)
                            if (img != null) {
                                // Scale down to thumbnail size
                                val maxDim = 60
                                val scale =
                                    minOf(
                                        maxDim.toDouble() / img.width,
                                        maxDim.toDouble() / img.height,
                                    )
                                val w = (img.width * scale).toInt().coerceAtLeast(1)
                                val h = (img.height * scale).toInt().coerceAtLeast(1)
                                val scaled =
                                    java.awt.image.BufferedImage(
                                        w,
                                        h,
                                        java.awt.image.BufferedImage.TYPE_INT_RGB,
                                    )
                                val g = scaled.createGraphics()
                                g.drawImage(
                                    img.getScaledInstance(w, h, java.awt.Image.SCALE_FAST),
                                    0,
                                    0,
                                    null,
                                )
                                g.dispose()
                                thumbnail.value = scaled
                            }
                        } catch (_: Exception) {
                            thumbnail.value = null
                        }
                    }
                }
                Card(
                    modifier = Modifier.width(64.dp).height(80.dp).clickable { onSelect(file) },
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
                        val img = thumbnail.value
                        if (img != null) {
                            Image(
                                bitmap = img.toComposeImageBitmap(),
                                contentDescription = file.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                            )
                        } else {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                        }
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
