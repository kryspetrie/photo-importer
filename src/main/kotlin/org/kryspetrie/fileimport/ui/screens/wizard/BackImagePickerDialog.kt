@file:Suppress("TooManyFunctions", "MagicNumber")

package org.kryspetrie.fileimport.ui.screens.wizard

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.i18n.strings
import org.kryspetrie.fileimport.ui.wizard.state.ZoomController

/** Mode for the back-image overlay interaction. */
internal enum class BackImageInteractionMode {
    VIEW,
    CROP,
    /** 4-point perspective selection — click to place corners, drag to adjust. */
    QUAD,
}

// ─── Rect rotation transforms (normalized crop coordinates) ────────────────────────────────

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
    val s = strings()
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
                    Text(
                        s.t(StringKey.META_SELECT_BACK_OF_PHOTO),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, s.t(StringKey.ACTION_CLOSE))
                    }
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
                            s.t(StringKey.WIZARD_NO_BACK_SELECTED),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            val path =
                                org.kryspetrie.fileimport.ui.components.pickImageFile(
                                    s.t(StringKey.WIZARD_SELECT_BACK_IMAGE)
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
                        Text(
                            s.t(StringKey.ACTION_BROWSE),
                            style = MaterialTheme.typography.labelSmall,
                        )
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
                                    if (selectedFile != null) s.t(StringKey.WIZARD_LOADING_IMAGE)
                                    else s.t(StringKey.WIZARD_SELECT_BACK_TO_CROP),
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
                                    contentDescription = s.t(StringKey.WIZARD_CROP_MODE),
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (isInCropMode) s.t(StringKey.META_DONE)
                                    else s.t(StringKey.WIZARD_CROP),
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
                                    contentDescription = s.t(StringKey.WIZARD_FOUR_POINT_MODE),
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (isInQuadMode) s.t(StringKey.META_DONE)
                                    else s.t(StringKey.WIZARD_FOUR_POINT),
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
                                            s.t(StringKey.META_REMOVE),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                                quadCorners != null && isInQuadMode -> {
                                    if (quadPointsPlaced < 4) {
                                        Text(
                                            s.t(
                                                StringKey.WIZARD_POINT_OF_FOUR,
                                                "current" to (quadPointsPlaced + 1).toString(),
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    } else {
                                        Text(
                                            s.t(StringKey.WIZARD_DRAG_CORNERS),
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
                                            s.t(StringKey.META_REMOVE),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                                isInCropMode -> {
                                    Text(
                                        s.t(StringKey.WIZARD_CROP_HINT),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                isInQuadMode -> {
                                    Text(
                                        s.t(StringKey.WIZARD_FOUR_POINT_HINT),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                else -> {
                                    Text(
                                        s.t(StringKey.WIZARD_SELECT_MODE),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        // Right: Rotation controls
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                s.t(StringKey.META_ROTATE_LABEL),
                                style = MaterialTheme.typography.labelMedium,
                            )
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
                                    s.t(StringKey.ACC_ROTATE_CCW),
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
                                    s.t(StringKey.FIELD_ROTATE_180),
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
                                    s.t(StringKey.ACC_ROTATE_CW),
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
                            Text(
                                s.t(StringKey.WIZARD_COMBINE),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { backImageMode = "append_back" },
                        ) {
                            RadioButton(
                                selected = backImageMode == "append_back",
                                onClick = { backImageMode = "append_back" },
                            )
                            Text(
                                s.t(StringKey.WIZARD_APPEND_BACK),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    Text(
                        if (backImageMode == "combine") s.t(StringKey.WIZARD_COMBINE_DESC)
                        else s.t(StringKey.WIZARD_APPEND_DESC),
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
                    TextButton(onClick = onDismiss) { Text(s.cancel) }
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
                        Text(s.t(StringKey.WIZARD_ASSIGN_BACK))
                    }
                }
            }
        }
    }
}

// ─── Canvas composable ────────────────────────────────────────────────────────────────────
