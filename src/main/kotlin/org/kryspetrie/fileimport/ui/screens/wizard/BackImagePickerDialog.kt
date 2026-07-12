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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
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
import androidx.compose.ui.text.style.TextAlign
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

/** Mode for the back-image overlay interaction. */
private enum class BackImageInteractionMode(val displayName: String) {
    VIEW("View"),
    CROP("Crop"),
    /** 4-point perspective selection — click to place corners, drag to adjust. */
    QUAD("4-Point"),
}

// ─── Rect rotation transforms (normalized crop coordinates) ────────────────────────────────

/**
 * Transform a normalized crop rectangle for a 90° clockwise image rotation.
 *
 * When the displayed image rotates 90° CW, normalized coordinates transform as (x, y) → (1-y, x).
 * This matches [FaceRegion.rotate90CW].
 */
internal fun Rect.rotate90CW(): Rect {
    val x1 = 1.0f - bottom
    val y1 = left
    val x2 = 1.0f - top
    val y2 = right
    return Rect(
        left = minOf(x1, x2),
        top = minOf(y1, y2),
        right = maxOf(x1, x2),
        bottom = maxOf(y1, y2),
    )
}

/** Transform a normalized crop rectangle for a 90° counter-clockwise image rotation. */
internal fun Rect.rotate90CCW(): Rect {
    val x1 = top
    val y1 = 1.0f - right
    val x2 = bottom
    val y2 = 1.0f - left
    return Rect(
        left = minOf(x1, x2),
        top = minOf(y1, y2),
        right = maxOf(x1, x2),
        bottom = maxOf(y1, y2),
    )
}

/** Transform a normalized crop rectangle for a 180° image rotation. */
internal fun Rect.rotate180(): Rect {
    return Rect(left = 1.0f - right, top = 1.0f - bottom, right = 1.0f - left, bottom = 1.0f - top)
}

// ─── Quad (4-point) rotation transforms ───────────────────────────────────────────────────

/** Quad as 4 normalized (x,y) points: topLeft, topRight, bottomRight, bottomLeft. */
data class QuadCorners(
    val topLeft: Offset,
    val topRight: Offset,
    val bottomRight: Offset,
    val bottomLeft: Offset,
) {
    /** Convert to flat list: [tl_x, tl_y, tr_x, tr_y, br_x, br_y, bl_x, bl_y]. */
    fun toFlatList(): List<Float> =
        listOf(
            topLeft.x,
            topLeft.y,
            topRight.x,
            topRight.y,
            bottomRight.x,
            bottomRight.y,
            bottomLeft.x,
            bottomLeft.y,
        )

    /** Rotate quad corners 90° CW when the displayed image rotates. */
    fun rotate90CW(): QuadCorners {
        // When image rotates 90° CW: (x,y) → (1-y, x) for each point
        // And corner labels shift: topLeft ← topRight, topRight ← bottomRight, etc.
        val t = { p: Offset -> Offset(1f - p.y, p.x) }
        return QuadCorners(
            topLeft = t(topRight),
            topRight = t(bottomRight),
            bottomRight = t(bottomLeft),
            bottomLeft = t(topLeft),
        )
    }

    /** Rotate quad corners 90° CCW when the displayed image rotates. */
    fun rotate90CCW(): QuadCorners {
        // (x,y) → (y, 1-x) for each point
        // Corner labels shift: topLeft ← bottomLeft, topRight ← topLeft, etc.
        val t = { p: Offset -> Offset(p.y, 1f - p.x) }
        return QuadCorners(
            topLeft = t(bottomLeft),
            topRight = t(topLeft),
            bottomRight = t(topRight),
            bottomLeft = t(bottomRight),
        )
    }

    /** Rotate quad corners 180° when the displayed image rotates. */
    fun rotate180(): QuadCorners {
        // (x,y) → (1-x, 1-y) for each point
        // Corner labels shift: topLeft ← bottomRight, topRight ← bottomLeft, etc.
        val t = { p: Offset -> Offset(1f - p.x, 1f - p.y) }
        return QuadCorners(
            topLeft = t(bottomRight),
            topRight = t(bottomLeft),
            bottomRight = t(topLeft),
            bottomLeft = t(topRight),
        )
    }

    companion object {
        /** Create from a flat list: [tl_x, tl_y, tr_x, tr_y, br_x, br_y, bl_x, bl_y]. */
        fun fromFlatList(values: List<Float>): QuadCorners {
            require(values.size == 8) { "Expected 8 values, got ${values.size}" }
            return QuadCorners(
                topLeft = Offset(values[0], values[1]),
                topRight = Offset(values[2], values[3]),
                bottomRight = Offset(values[4], values[5]),
                bottomLeft = Offset(values[6], values[7]),
            )
        }
    }
}

// ─── Crop result data class ───────────────────────────────────────────────────────────────

/**
 * Result of a back-image crop/quad selection.
 * - [rect]: Normalized rectangle crop (left, top, right, bottom) if rectangular crop was used.
 * - [quad]: 4-point perspective corners if quad crop was used.
 * - Exactly one of [rect] or [quad] should be non-null (or both null for full image).
 */
data class BackImageCropResult(val rect: Rect? = null, val quad: QuadCorners? = null) {
    /**
     * Convert to flat list for storage.
     * - 4 values: rect crop [left, top, right, bottom]
     * - 8 values: quad crop [tl_x, tl_y, tr_x, tr_y, br_x, br_y, bl_x, bl_y]
     * - null: full image (no crop)
     */
    fun toNormalizedList(): List<Float>? {
        return when {
            quad != null -> quad.toFlatList()
            rect != null -> listOf(rect.left, rect.top, rect.right, rect.bottom)
            else -> null
        }
    }
}

// ─── Main dialog ──────────────────────────────────────────────────────────────────────────

/**
 * Dialog for selecting a back-of-photo image and optionally cropping a region from it.
 *
 * Three selection modes:
 * - **Crop** — drag to define a rectangular crop region
 * - **4-Point** — click 4 corners for perspective-warp selection
 * - **View** — zoom and pan without selecting
 *
 * @param batchFiles Available batch files for selection, or null if not in batch mode
 * @param onConfirm Called with (sourceFilePath, cropResult, rotationDegrees, mode) when confirmed
 * @param onDismiss Called when cancelled
 */
@Suppress("InjectDispatcher")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackImagePickerDialog(
    batchFiles: List<File>? = null,
    preSelectedPath: String? = null,
    onConfirm:
        (sourcePath: String, cropResult: BackImageCropResult?, rotation: Int, mode: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val initialFile =
        remember(preSelectedPath, batchFiles) {
            if (preSelectedPath != null) {
                File(preSelectedPath).takeIf { it.exists() }
            } else {
                batchFiles?.firstOrNull()
            }
        }
    var selectedFile by remember { mutableStateOf(initialFile) }
    var backImage by remember { mutableStateOf<BufferedImage?>(null) }
    var backImageMode by remember { mutableStateOf("combine") }
    var cropRect by remember { mutableStateOf<Rect?>(null) }
    var quadCorners by remember { mutableStateOf<QuadCorners?>(null) }
    var interactionMode by remember {
        mutableStateOf(
            if (initialFile != null) BackImageInteractionMode.CROP
            else BackImageInteractionMode.VIEW
        )
    }
    var cropRotation by remember { mutableStateOf(0) }

    // Quad placement state: how many points placed (0–3), -1 means complete/editing
    var quadPointsPlaced by remember { mutableStateOf(0) }
    // Pending mouse position for preview line during quad placement
    var quadPendingPoint by remember { mutableStateOf<Offset?>(null) }

    // Zoom/pan state — local to this dialog
    var zoomController by remember { mutableStateOf(ZoomController()) }
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

    // Auto-fit when image or viewport changes
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

                // ── Source selection row ──
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
                                quadCorners = null
                                quadPointsPlaced = 0
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

                // ── Image area with zoom/pan and selection overlays ──
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
                        BackImageCanvas(
                            image = img,
                            cropRect = cropRect,
                            quadCorners = quadCorners,
                            quadPointsPlaced = quadPointsPlaced,
                            quadPendingPoint = quadPendingPoint,
                            zoomController = zoomController,
                            onZoomControllerChange = { zoomController = it },
                            interactionMode = interactionMode,
                            onCropUpdate = { rect -> cropRect = rect },
                            onCropEnd = {},
                            onQuadPointAdd = { point ->
                                val current = quadCorners
                                if (current == null) {
                                    quadCorners =
                                        QuadCorners(
                                            topLeft = point,
                                            topRight = point,
                                            bottomRight = point,
                                            bottomLeft = point,
                                        )
                                    quadPointsPlaced = 1
                                } else {
                                    // Place next corner
                                    quadCorners =
                                        when (quadPointsPlaced) {
                                            1 -> current.copy(topRight = point)
                                            2 -> current.copy(bottomRight = point)
                                            3 ->
                                                current.copy(bottomLeft = point).also {
                                                    quadPointsPlaced = 4
                                                }
                                            else -> current // shouldn't happen
                                        }
                                    quadPointsPlaced = minOf(quadPointsPlaced + 1, 4)
                                }
                            },
                            onQuadCornerMove = { cornerIndex, newPos ->
                                val current = quadCorners ?: return@BackImageCanvas
                                quadCorners =
                                    when (cornerIndex) {
                                        0 -> current.copy(topLeft = newPos)
                                        1 -> current.copy(topRight = newPos)
                                        2 -> current.copy(bottomRight = newPos)
                                        3 -> current.copy(bottomLeft = newPos)
                                        else -> current
                                    }
                            },
                            onQuadPendingUpdate = { quadPendingPoint = it },
                            modifier = Modifier.fillMaxSize(),
                        )

                        // Zoom controls overlay
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

                // ── Selection mode & rotation controls ──
                if (backImage != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Left: Mode toggles + remove/clear
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Crop mode toggle
                            val isInCropMode = interactionMode == BackImageInteractionMode.CROP
                            OutlinedButton(
                                onClick = {
                                    interactionMode =
                                        if (isInCropMode) BackImageInteractionMode.VIEW
                                        else BackImageInteractionMode.CROP
                                    quadCorners = null
                                    quadPointsPlaced = 0
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
                                    if (isInCropMode) "Done" else "Crop",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            // 4-Point mode toggle
                            val isInQuadMode = interactionMode == BackImageInteractionMode.QUAD
                            OutlinedButton(
                                onClick = {
                                    interactionMode =
                                        if (isInQuadMode) BackImageInteractionMode.VIEW
                                        else BackImageInteractionMode.QUAD
                                    cropRect = null
                                    if (!isInQuadMode) {
                                        // Entering quad mode — reset placement
                                        quadCorners = null
                                        quadPointsPlaced = 0
                                    }
                                },
                                modifier = Modifier.height(28.dp),
                            ) {
                                Icon(
                                    Icons.Default.CropFree,
                                    contentDescription = "4-Point mode",
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (isInQuadMode) "Done" else "4-Point",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            // Status / remove buttons
                            when {
                                cropRect != null -> {
                                    Text(
                                        "%.0f%% × %.0f%%"
                                            .format(
                                                cropRect!!.width * 100,
                                                cropRect!!.height * 100,
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
                                            "Remove",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                                quadCorners != null && isInQuadMode -> {
                                    if (quadPointsPlaced < 4) {
                                        Text(
                                            "Click to set point ${quadPointsPlaced + 1} of 4",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    } else {
                                        Text(
                                            "Drag corners to adjust",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    Spacer(Modifier.width(4.dp))
                                    TextButton(
                                        onClick = {
                                            quadCorners = null
                                            quadPointsPlaced = 0
                                        },
                                        contentPadding =
                                            androidx.compose.foundation.layout.PaddingValues(
                                                horizontal = 4.dp
                                            ),
                                    ) {
                                        Text(
                                            "Remove",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                                isInCropMode -> {
                                    Text(
                                        "Drag on the image to crop",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                isInQuadMode -> {
                                    Text(
                                        "Click 4 corners for perspective",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                else -> {
                                    Text(
                                        "Select Crop or 4-Point",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
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
                                    quadCorners = quadCorners?.rotate90CCW()
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
                                    quadCorners = quadCorners?.rotate180()
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
                                    quadCorners = quadCorners?.rotate90CW()
                                },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.RotateRight,
                                    "Rotate CW",
                                    modifier = Modifier.size(18.dp),
                                )
                            }
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
                            quadCorners = null
                            quadPointsPlaced = 0
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
                            val cropResult =
                                when {
                                    quadCorners != null && quadPointsPlaced == 4 ->
                                        BackImageCropResult(quad = quadCorners)
                                    cropRect != null -> BackImageCropResult(rect = cropRect)
                                    else -> null
                                }
                            onConfirm(file.absolutePath, cropResult, cropRotation, backImageMode)
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

// ─── Canvas composable ────────────────────────────────────────────────────────────────────

/** Radius of corner circles for quad selection (in pixels). */
private const val CORNER_RADIUS = 8f
/** Hit-test radius for dragging quad corners (in pixels). */
private const val CORNER_HIT_RADIUS = 16f

/**
 * Canvas composable that renders the back image with zoom/pan, crop overlay, and quad selection.
 */
@Composable
private fun BackImageCanvas(
    image: BufferedImage,
    cropRect: Rect?,
    quadCorners: QuadCorners?,
    quadPointsPlaced: Int,
    quadPendingPoint: Offset?,
    zoomController: ZoomController,
    onZoomControllerChange: (ZoomController) -> Unit,
    interactionMode: BackImageInteractionMode,
    onCropUpdate: (Rect) -> Unit,
    onCropEnd: () -> Unit,
    onQuadPointAdd: (Offset) -> Unit,
    onQuadCornerMove: (cornerIndex: Int, newPos: Offset) -> Unit,
    onQuadPendingUpdate: (Offset?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val currentCrop = cropRect
    val currentQuad = quadCorners

    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var isDragging by remember { mutableStateOf(false) }
    var totalMovement by remember { mutableStateOf(0f) }
    var draggedCornerIndex by remember { mutableStateOf(-1) } // for quad corner dragging

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
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val pos = event.changes.firstOrNull()?.position ?: continue

                            when (event.type) {
                                PointerEventType.Press -> {
                                    // Check if pressing a quad corner first (in any mode that has a
                                    // quad)
                                    if (currentQuad != null && quadPointsPlaced == 4) {
                                        val hitCorner =
                                            findQuadCornerHit(
                                                pos,
                                                currentQuad,
                                                scale,
                                                panX,
                                                panY,
                                                image.width,
                                                image.height,
                                            )
                                        if (hitCorner >= 0) {
                                            draggedCornerIndex = hitCorner
                                            isDragging = true
                                            dragStart = pos
                                            totalMovement = 0f
                                            continue
                                        }
                                    }

                                    dragStart = pos
                                    isDragging = true
                                    totalMovement = 0f
                                    draggedCornerIndex = -1

                                    // Quad placement: tap to place a corner
                                    if (
                                        interactionMode == BackImageInteractionMode.QUAD &&
                                            quadPointsPlaced < 4
                                    ) {
                                        val imgPoint =
                                            zoomController.screenToImage(
                                                pos.x.toDouble(),
                                                pos.y.toDouble(),
                                            )
                                        // Only accept clicks within the image bounds
                                        if (
                                            imgPoint.x >= 0 &&
                                                imgPoint.x <= image.width &&
                                                imgPoint.y >= 0 &&
                                                imgPoint.y <= image.height
                                        ) {
                                            val normalized =
                                                Offset(
                                                    (imgPoint.x / image.width)
                                                        .toFloat()
                                                        .coerceIn(0f, 1f),
                                                    (imgPoint.y / image.height)
                                                        .toFloat()
                                                        .coerceIn(0f, 1f),
                                                )
                                            onQuadPointAdd(normalized)
                                        }
                                    }
                                }
                                PointerEventType.Move -> {
                                    if (isDragging && dragStart != null) {
                                        val delta =
                                            pos -
                                                (event.changes.firstOrNull()?.previousPosition
                                                    ?: pos)
                                        totalMovement += delta.getDistance()

                                        // Quad corner dragging
                                        if (draggedCornerIndex >= 0) {
                                            val imgPoint =
                                                zoomController.screenToImage(
                                                    pos.x.toDouble(),
                                                    pos.y.toDouble(),
                                                )
                                            val normalized =
                                                Offset(
                                                    (imgPoint.x / image.width)
                                                        .toFloat()
                                                        .coerceIn(0f, 1f),
                                                    (imgPoint.y / image.height)
                                                        .toFloat()
                                                        .coerceIn(0f, 1f),
                                                )
                                            onQuadCornerMove(draggedCornerIndex, normalized)
                                            continue
                                        }

                                        when (interactionMode) {
                                            BackImageInteractionMode.CROP -> {
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
                                                val x1 = (start.x / image.width).coerceIn(0.0, 1.0)
                                                val y1 = (start.y / image.height).coerceIn(0.0, 1.0)
                                                val x2 =
                                                    (current.x / image.width).coerceIn(0.0, 1.0)
                                                val y2 =
                                                    (current.y / image.height).coerceIn(0.0, 1.0)
                                                onCropUpdate(
                                                    Rect(
                                                        left = minOf(x1, x2).toFloat(),
                                                        top = minOf(y1, y2).toFloat(),
                                                        right = maxOf(x1, x2).toFloat(),
                                                        bottom = maxOf(y1, y2).toFloat(),
                                                    )
                                                )
                                            }
                                            BackImageInteractionMode.VIEW,
                                            BackImageInteractionMode.QUAD -> {
                                                // Pan in both VIEW and QUAD (placement) modes
                                                val newZoom =
                                                    zoomController.pan(
                                                        delta.x.toDouble(),
                                                        delta.y.toDouble(),
                                                    )
                                                onZoomControllerChange(newZoom)
                                            }
                                        }
                                    }

                                    // Quad pending point preview
                                    if (
                                        interactionMode == BackImageInteractionMode.QUAD &&
                                            quadPointsPlaced in 0 until 4 &&
                                            !isDragging
                                    ) {
                                        val imgPoint =
                                            zoomController.screenToImage(
                                                pos.x.toDouble(),
                                                pos.y.toDouble(),
                                            )
                                        if (
                                            imgPoint.x >= 0 &&
                                                imgPoint.x <= image.width &&
                                                imgPoint.y >= 0 &&
                                                imgPoint.y <= image.height
                                        ) {
                                            onQuadPendingUpdate(
                                                Offset(
                                                    (imgPoint.x / image.width).toFloat(),
                                                    (imgPoint.y / image.height).toFloat(),
                                                )
                                            )
                                        } else {
                                            onQuadPendingUpdate(null)
                                        }
                                    }
                                }
                                PointerEventType.Release -> {
                                    if (isDragging && draggedCornerIndex >= 0) {
                                        // Finished dragging a quad corner
                                        draggedCornerIndex = -1
                                    } else if (isDragging) {
                                        if (
                                            interactionMode == BackImageInteractionMode.CROP &&
                                                totalMovement > 5f
                                        ) {
                                            onCropEnd()
                                        }
                                    }
                                    isDragging = false
                                    dragStart = null
                                }
                                PointerEventType.Scroll -> {
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
        drawRect(color = Color(0xFF404040.toInt()))

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

        // ── Draw rectangular crop overlay ──
        val crop = currentCrop
        if (crop != null && image.width > 0 && image.height > 0) {
            val cropLeftPx = (crop.left * image.width * scale) + panX
            val cropTopPx = (crop.top * image.height * scale) + panY
            val cropRightPx = (crop.right * image.width * scale) + panX
            val cropBottomPx = (crop.bottom * image.height * scale) + panY
            val cropWidthPx = cropRightPx - cropLeftPx
            val cropHeightPx = cropBottomPx - cropTopPx

            val dimColor = Color.Black.copy(alpha = 0.55f)
            drawRect(dimColor, topLeft = Offset(0f, 0f), size = Size(size.width, cropTopPx))
            drawRect(
                dimColor,
                topLeft = Offset(0f, cropBottomPx),
                size = Size(size.width, size.height - cropBottomPx),
            )
            drawRect(
                dimColor,
                topLeft = Offset(0f, cropTopPx),
                size = Size(cropLeftPx, cropHeightPx),
            )
            drawRect(
                dimColor,
                topLeft = Offset(cropRightPx, cropTopPx),
                size = Size(size.width - cropRightPx, cropHeightPx),
            )

            drawRect(
                color = Color.Yellow,
                topLeft = Offset(cropLeftPx, cropTopPx),
                size = Size(cropWidthPx, cropHeightPx),
                style = Stroke(width = 2.5f),
            )

            val pctLabel = "%.0f%% × %.0f%%".format(crop.width * 100, crop.height * 100)
            val labelLayout =
                textMeasurer.measure(pctLabel, TextStyle(color = Color.White, fontSize = 11.sp))
            drawText(
                textLayoutResult = labelLayout,
                topLeft = Offset(cropLeftPx + 4f, cropTopPx + 2f),
            )
        }

        // ── Draw quad (4-point) overlay ──
        val quad = currentQuad
        if (quad != null && image.width > 0 && image.height > 0) {
            // Convert normalized corners to screen coordinates
            val corners = listOf(quad.topLeft, quad.topRight, quad.bottomRight, quad.bottomLeft)
            val screenCorners =
                corners.map { norm ->
                    Offset(
                        norm.x * image.width * scale + panX,
                        norm.y * image.height * scale + panY,
                    )
                }

            // Draw filled quad outline when all 4 points placed, or partial lines for in-progress
            if (quadPointsPlaced >= 2) {
                // Draw connecting lines
                val path = Path()
                val startCorner =
                    if (quadPointsPlaced >= 4) 0 else 0 // always start from first placed point
                path.moveTo(screenCorners[startCorner].x, screenCorners[startCorner].y)
                val cornersToDraw = if (quadPointsPlaced >= 4) 4 else quadPointsPlaced
                for (i in 1 until cornersToDraw) {
                    path.lineTo(screenCorners[i].x, screenCorners[i].y)
                }
                if (quadPointsPlaced >= 4) {
                    path.close()
                } else if (quadPendingPoint != null) {
                    // Draw line to pending point
                    val pendingScreen =
                        Offset(
                            quadPendingPoint.x * image.width * scale + panX,
                            quadPendingPoint.y * image.height * scale + panY,
                        )
                    path.lineTo(pendingScreen.x, pendingScreen.y)
                }
                drawPath(path, color = Color.Yellow, style = Stroke(width = 2.5f))
            }

            // Draw placed corner circles
            for ((index, screenCorner) in screenCorners.withIndex()) {
                if (index < quadPointsPlaced) {
                    drawCircle(
                        color = Color.White,
                        radius = CORNER_RADIUS,
                        center = screenCorner,
                        style = Fill,
                    )
                    drawCircle(
                        color = Color.Yellow,
                        radius = CORNER_RADIUS,
                        center = screenCorner,
                        style = Stroke(width = 2f),
                    )
                }
            }

            // Draw pending point preview
            if (
                quadPendingPoint != null &&
                    interactionMode == BackImageInteractionMode.QUAD &&
                    quadPointsPlaced < 4 &&
                    !isDragging
            ) {
                val pendingScreen =
                    Offset(
                        quadPendingPoint.x * image.width * scale + panX,
                        quadPendingPoint.y * image.height * scale + panY,
                    )
                drawCircle(
                    color = Color.White.copy(alpha = 0.6f),
                    radius = CORNER_RADIUS * 0.7f,
                    center = pendingScreen,
                    style = Fill,
                )
            }

            // Draw line from last placed corner to pending point when placing points
            if (quadPointsPlaced in 1 until 4 && quadPendingPoint != null && !isDragging) {
                val lastCorner = screenCorners[quadPointsPlaced - 1]
                val pendingScreen =
                    Offset(
                        quadPendingPoint.x * image.width * scale + panX,
                        quadPendingPoint.y * image.height * scale + panY,
                    )
                drawLine(
                    color = Color.Yellow.copy(alpha = 0.5f),
                    start = lastCorner,
                    end = pendingScreen,
                    strokeWidth = 1.5f,
                )
            }
        }
    }
}

/** Find which quad corner is under the cursor, or -1 if none. */
private fun findQuadCornerHit(
    pos: Offset,
    quad: QuadCorners,
    scale: Float,
    panX: Float,
    panY: Float,
    imageWidth: Int,
    imageHeight: Int,
): Int {
    val corners = listOf(quad.topLeft, quad.topRight, quad.bottomRight, quad.bottomLeft)
    for ((index, norm) in corners.withIndex()) {
        val screenX = norm.x * imageWidth * scale + panX
        val screenY = norm.y * imageHeight * scale + panY
        val dx = pos.x - screenX
        val dy = pos.y - screenY
        if (dx * dx + dy * dy <= CORNER_HIT_RADIUS * CORNER_HIT_RADIUS) {
            return index
        }
    }
    return -1
}

// ─── Zoom controls overlay ────────────────────────────────────────────────────────────────

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
                    textAlign = TextAlign.Center,
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

// ─── Nearby files strip ───────────────────────────────────────────────────────────────────

@Suppress("InjectDispatcher")
@Composable
private fun NearbyFilesStrip(
    files: List<File>,
    selectedFile: File?,
    onSelect: (File) -> Unit,
    modifier: Modifier = Modifier,
) {
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
                val thumbnail = remember(file.absolutePath) { mutableStateOf<BufferedImage?>(null) }
                LaunchedEffect(file.absolutePath) {
                    withContext(Dispatchers.IO) {
                        try {
                            val img = ImageIO.read(file)
                            if (img != null) {
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
