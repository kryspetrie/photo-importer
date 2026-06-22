package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.awt.Cursor
import java.awt.image.BufferedImage
import org.kryspetrie.fileimport.application.PerspectiveCorrectionService
import org.kryspetrie.fileimport.infrastructure.wizard.BoundingBox
import org.kryspetrie.fileimport.infrastructure.wizard.BoundingBoxList
import org.kryspetrie.fileimport.infrastructure.wizard.PhotoConfiguration
import org.kryspetrie.fileimport.infrastructure.wizard.PhotoScanWizardState
import org.kryspetrie.fileimport.ui.components.PreviewCache
import org.kryspetrie.fileimport.domain.model.DetectionMode
import org.kryspetrie.fileimport.ui.screens.wizard.summary.AspectRatioDropdown
import org.kryspetrie.fileimport.ui.screens.wizard.summary.DetectionModeBadge
import org.kryspetrie.fileimport.ui.screens.wizard.summary.DetectionModeDropdown
import org.kryspetrie.fileimport.ui.screens.wizard.summary.ExportBottomBar

/**
 * Summary screen showing all detected photos as a scrolling grid of image tiles. Each tile displays
 * the cropped+rotated preview with inline rotation and aspect ratio controls. Warp-stretch
 * perspective correction is always applied. Uses [PreviewCache] to avoid recomputing perspective
 * correction on every recomposition, and supports full-screen preview on tile click.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    state: PhotoScanWizardState,
    image: BufferedImage,
    perspectiveService: PerspectiveCorrectionService,
    previewCache: PreviewCache,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onSkipMetadata: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val boundingBoxList by state.boundingBoxList.collectAsState()
    val photoConfigurations by state.photoConfigurations.collectAsState()

    Scaffold(
        topBar = {
            SummaryTopAppBar(
                photoCount = boundingBoxList.size(),
                onRotateAllCW = { state.rotateAllBoxesCW() },
                onRotateAllCCW = { state.rotateAllBoxesCCW() },
                onClearAll = { state.clearAllConfigurations() },
            )
        },
        content = { paddingValues ->
            PhotoGrid(
                image = image,
                perspectiveService = perspectiveService,
                previewCache = previewCache,
                boundingBoxList = boundingBoxList,
                photoConfigurations = photoConfigurations,
                onConfigChange = { boxId, config -> state.setPhotoConfiguration(boxId, config) },
                onDetectionModeChange = { boxId, mode ->
                    val current = photoConfigurations[boxId] ?: PhotoConfiguration()
                    state.setPhotoConfiguration(boxId, current.copy(detectionMode = mode))
                },
                modifier = modifier.padding(paddingValues),
            )
        },
        bottomBar = {
            ExportBottomBar(
                photoCount = boundingBoxList.size(),
                onBack = onBack,
                onExport = onExport,
                onSkipMetadata = onSkipMetadata,
            )
        },
    )
}

/**
 * Top app bar with rotation controls and a destructive "Reset" button that requires confirmation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SummaryTopAppBar(
    photoCount: Int,
    onRotateAllCW: () -> Unit,
    onRotateAllCCW: () -> Unit,
    onClearAll: () -> Unit,
) {
    var showResetConfirmDialog by remember { mutableStateOf(false) }

    TopAppBar(
        title = { Text("Crop & Rotate") },
        actions = {
            OutlinedButton(onClick = onRotateAllCCW, modifier = Modifier.height(32.dp)) {
                Icon(Icons.AutoMirrored.Filled.RotateLeft, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("All CCW", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.width(4.dp))
            OutlinedButton(onClick = onRotateAllCW, modifier = Modifier.height(32.dp)) {
                Icon(Icons.AutoMirrored.Filled.RotateRight, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("All CW", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.width(4.dp))
            OutlinedButton(
                onClick = { showResetConfirmDialog = true },
                modifier = Modifier.height(32.dp),
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
            ) {
                Text("Reset", style = MaterialTheme.typography.labelSmall)
            }
        },
    )

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = { Text("Reset All Rotations?") },
            text = {
                Text(
                    "This will clear all rotation and correction settings for " +
                        "$photoCount photo(s). You can still use Undo after resetting."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAll()
                        showResetConfirmDialog = false
                    }
                ) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) { Text("Cancel") }
            },
        )
    }
}

/**
 * Scrolling grid of photo tiles. Each tile shows the perspective-corrected and rotated preview
 * image with rotation and aspect ratio controls. Clicking a tile opens the full-screen preview
 * dialog. Uses [PreviewCache] for efficient thumbnail rendering.
 */
@Composable
private fun PhotoGrid(
    image: BufferedImage,
    perspectiveService: PerspectiveCorrectionService,
    previewCache: PreviewCache,
    boundingBoxList: BoundingBoxList,
    photoConfigurations: Map<String, PhotoConfiguration>,
    onConfigChange: (String, PhotoConfiguration) -> Unit,
    onDetectionModeChange: (String, DetectionMode?) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Full-screen preview state
    var fullscreenBoxIndex by remember { mutableStateOf<Int?>(null) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 220.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(boundingBoxList.boxes) { index, box ->
            val config = photoConfigurations[box.id] ?: PhotoConfiguration()
            val thumbnail = remember(image, box, config) {
                previewCache.getThumbnail(image, box, config)
            }

            PhotoTile(
                index = index,
                box = box,
                config = config,
                thumbnail = thumbnail,
                onRotateCW = { onConfigChange(box.id, config.cycleRotationCW()) },
                onRotateCCW = { onConfigChange(box.id, config.cycleRotationCCW()) },
                onAspectRatioChange = { newRatio ->
                    onConfigChange(box.id, config.copy(aspectRatio = newRatio))
                },
                onDetectionModeChange = { mode ->
                    onDetectionModeChange(box.id, mode)
                },
                onPreviewClick = { fullscreenBoxIndex = index },
            )
        }
    }

    // Full-screen preview overlay
    if (fullscreenBoxIndex != null && fullscreenBoxIndex!! < boundingBoxList.size()) {
        val idx = fullscreenBoxIndex!!
        val box = boundingBoxList.boxes[idx]
        val config = photoConfigurations[box.id] ?: PhotoConfiguration()
        val fullPreview = remember(image, box, config) {
            previewCache.getFullPreview(image, box, config)
        }
        val fullBitmap = remember(fullPreview) {
            fullPreview?.toComposeImageBitmap()
        }

        SummaryFullscreenPreviewDialog(
            photoIndex = idx,
            totalCount = boundingBoxList.size(),
            rotationDegrees = config.rotationDegrees,
            bitmap = fullBitmap,
            onDismiss = { fullscreenBoxIndex = null },
            onPrevious = {
                if (idx > 0) fullscreenBoxIndex = idx - 1
            },
            onNext = {
                if (idx < boundingBoxList.size() - 1) fullscreenBoxIndex = idx + 1
            },
        )
    }
}

/**
 * A single tile in the photo grid. Shows the cached thumbnail with rotation and aspect ratio
 * controls, and a click-to-zoom hint overlay.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoTile(
    index: Int,
    box: BoundingBox,
    config: PhotoConfiguration,
    thumbnail: ImageBitmap?,
    onRotateCW: () -> Unit,
    onRotateCCW: () -> Unit,
    onAspectRatioChange: (Double) -> Unit,
    onDetectionModeChange: (DetectionMode?) -> Unit,
    onPreviewClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column {
            // Image area — clickable to open full-screen preview
            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR)))
                        .clickable(onClick = onPreviewClick),
                contentAlignment = Alignment.Center,
            ) {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail,
                        contentDescription = "Photo ${index + 1} preview",
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                        contentScale = ContentScale.Fit,
                    )
                    // Detection mode badge (top-left corner)
                    if (config.detectionMode != null) {
                        DetectionModeBadge(
                            mode = config.detectionMode,
                            modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                        )
                    }
                    // Zoom hint overlay (bottom-right corner)
                    Surface(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Black.copy(alpha = 0.5f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.ZoomIn,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = Color.White,
                            )
                            Text(
                                "Zoom",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                            )
                        }
                    }
                } else {
                    Text(
                        "Could not render preview",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Controls bar: rotation + aspect ratio
            Surface(
                tonalElevation = 1.dp,
                shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    // Row 1: rotation controls and photo label
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Left: rotate CCW
                        IconButton(onClick = onRotateCCW, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.AutoMirrored.Filled.RotateLeft,
                                "Rotate counter-clockwise",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }

                        // Center: photo label + rotation state
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Photo ${index + 1}", style = MaterialTheme.typography.labelSmall)
                            if (config.rotationDegrees != 0) {
                                Text(
                                    "${config.rotationDegrees}°",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        // Right: rotate CW
                        IconButton(onClick = onRotateCW, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.AutoMirrored.Filled.RotateRight,
                                "Rotate clockwise",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    // Row 2: aspect ratio dropdown
                    AspectRatioDropdown(
                        selectedRatio = config.aspectRatio,
                        onRatioChange = onAspectRatioChange,
                        boxAspectRatio = box.aspectRatio(),
                    )

                    // Row 3: detection mode dropdown
                    DetectionModeDropdown(
                        selectedMode = config.detectionMode,
                        onModeChange = onDetectionModeChange,
                    )
                }
            }
        }
    }
}

/**
 * Full-screen preview dialog shown when a photo tile is clicked. Supports navigation between
 * photos with Previous/Next buttons.
 */
@Composable
private fun SummaryFullscreenPreviewDialog(
    photoIndex: Int,
    totalCount: Int,
    rotationDegrees: Int,
    bitmap: ImageBitmap?,
    onDismiss: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
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
                modifier = Modifier.fillMaxSize().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Title bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Photo ${photoIndex + 1} of $totalCount",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                    if (rotationDegrees != 0) {
                        Text(
                            "${rotationDegrees}° rotation",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.6f),
                        )
                    }
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
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = "Photo ${photoIndex + 1} full preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Text(
                            "Could not render preview",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }

                // Navigation bar
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onPrevious,
                        enabled = photoIndex > 0,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.RotateLeft, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Previous")
                    }
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(
                        onClick = onNext,
                        enabled = photoIndex < totalCount - 1,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    ) {
                        Text("Next")
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.RotateRight, null, Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}