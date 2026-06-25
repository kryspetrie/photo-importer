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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
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
import org.kryspetrie.fileimport.domain.port.PerspectiveCorrectionPort
import org.kryspetrie.fileimport.domain.model.AspectRatio
import org.kryspetrie.fileimport.domain.model.geometry.BoundingBox
import org.kryspetrie.fileimport.domain.model.geometry.BoundingBoxList
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration
import org.kryspetrie.fileimport.ui.wizard.state.PhotoScanWizardState
import org.kryspetrie.fileimport.ui.wizard.state.WizardStep
import org.kryspetrie.fileimport.ui.components.PreviewCache
import org.kryspetrie.fileimport.ui.components.WizardStepIndicator
import org.kryspetrie.fileimport.ui.screens.wizard.summary.AspectRatioDropdown
import org.kryspetrie.fileimport.ui.screens.wizard.summary.BulkActionButtons
import org.kryspetrie.fileimport.ui.screens.wizard.summary.CorrectionStrategyDropdown
import org.kryspetrie.fileimport.ui.screens.wizard.summary.ExportBottomBar
import androidx.compose.material.icons.automirrored.filled.ArrowLeft
import androidx.compose.material.icons.automirrored.filled.ArrowRight

/**
 * Summary screen with a two-panel layout: scrollable photo list on the left, large preview on the
 * right. Each list item shows a thumbnail with metadata; the right panel shows a large
 * perspective-corrected preview with rotation, aspect ratio, and correction strategy controls.
 * Uses [PreviewCache] to avoid recomputing perspective correction. Supports full-screen preview on
 * image click.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    state: PhotoScanWizardState,
    image: BufferedImage,
    perspectiveService: PerspectiveCorrectionPort,
    previewCache: PreviewCache,
    onBack: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val boundingBoxList by state.boundingBoxList.collectAsState()
    val photoConfigurations by state.photoConfigurations.collectAsState()
    var selectedIndex by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            SummaryTopAppBar(
                photoCount = boundingBoxList.size(),
                onRotateAllCW = { state.configs.rotateAllBoxesCW() },
                onRotateAllCCW = { state.configs.rotateAllBoxesCCW() },
                onClearAll = { state.configs.clearAllConfigurations() },
                currentStep = WizardStep.SUMMARY,
            )
        },
        content = { paddingValues ->
            SummaryScreenContent(
                modifier = modifier.padding(paddingValues),
                image = image,
                previewCache = previewCache,
                boundingBoxList = boundingBoxList,
                photoConfigurations = photoConfigurations,
                selectedIndex = selectedIndex,
                onSelectedIndexChange = { selectedIndex = it },
                onConfigChange = { boxId, config -> state.configs.setPhotoScanConfiguration(boxId, config) },
                onBoxDelete = { index ->
                    state.boxes.removeBox(index)
                    val newSize = boundingBoxList.size() - 1
                    if (selectedIndex >= newSize && newSize > 0) {
                        selectedIndex = newSize - 1
                    }
                },
                onRotateAllCW = { state.configs.rotateAllBoxesCW() },
                onRotateAllCCW = { state.configs.rotateAllBoxesCCW() },
                onClearAllConfigurations = { state.configs.clearAllConfigurations() },
                state = state,
            )
        },
        bottomBar = {
            ExportBottomBar(
                photoCount = boundingBoxList.size(),
                onBack = onBack,
                onExport = onExport,
            )
        },
    )
}

/** Content area of the summary screen: either an empty-state message or the two-panel layout. */
@Composable
private fun SummaryScreenContent(
    modifier: Modifier,
    image: BufferedImage,
    previewCache: PreviewCache,
    boundingBoxList: BoundingBoxList,
    photoConfigurations: Map<String, PhotoScanConfiguration>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    onConfigChange: (String, PhotoScanConfiguration) -> Unit,
    onBoxDelete: (Int) -> Unit,
    onRotateAllCW: () -> Unit,
    onRotateAllCCW: () -> Unit,
    onClearAllConfigurations: () -> Unit,
    state: PhotoScanWizardState,
) {
    if (boundingBoxList.isEmpty()) {
        EmptyPhotoState(modifier)
    } else {
        val clampedIndex = selectedIndex.coerceIn(0, boundingBoxList.size() - 1)
        TwoPanelLayout(
            modifier = modifier,
            image = image,
            previewCache = previewCache,
            boundingBoxList = boundingBoxList,
            photoConfigurations = photoConfigurations,
            selectedIndex = clampedIndex,
            onSelectedIndexChange = onSelectedIndexChange,
            onConfigChange = onConfigChange,
            onBoxDelete = onBoxDelete,
            onRotateAllCW = onRotateAllCW,
            onRotateAllCCW = onRotateAllCCW,
            onClearAllConfigurations = onClearAllConfigurations,
            state = state,
        )
    }
}

/** Empty state shown when no photos are detected. */
@Composable
private fun EmptyPhotoState(modifier: Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "No photos detected. Go back and add bounding boxes.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Two-panel layout: photo sidebar list on left, detail preview on right. */
@Composable
private fun TwoPanelLayout(
    modifier: Modifier,
    image: BufferedImage,
    previewCache: PreviewCache,
    boundingBoxList: BoundingBoxList,
    photoConfigurations: Map<String, PhotoScanConfiguration>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    onConfigChange: (String, PhotoScanConfiguration) -> Unit,
    onBoxDelete: (Int) -> Unit,
    onRotateAllCW: () -> Unit,
    onRotateAllCCW: () -> Unit,
    onClearAllConfigurations: () -> Unit,
    state: PhotoScanWizardState,
) {
    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        PhotoSidebarList(
            image = image,
            previewCache = previewCache,
            boundingBoxList = boundingBoxList,
            photoConfigurations = photoConfigurations,
            selectedIndex = selectedIndex,
            onSelectedIndexChange = onSelectedIndexChange,
            onDelete = onBoxDelete,
            onRotateAllCW = onRotateAllCW,
            onRotateAllCCW = onRotateAllCCW,
            onClearAll = onClearAllConfigurations,
            modifier = Modifier.weight(0.35f).fillMaxHeight(),
        )

        val selectedBox = boundingBoxList.boxes.getOrNull(selectedIndex)
        val selectedConfig =
            selectedBox?.let { photoConfigurations[it.id] ?: PhotoScanConfiguration() }
                ?: PhotoScanConfiguration()

        DetailPreviewPanel(
            image = image,
            previewCache = previewCache,
            box = selectedBox,
            config = selectedConfig,
            index = selectedIndex,
            totalPhotos = boundingBoxList.size(),
            onConfigChange = { config ->
                selectedBox?.let { onConfigChange(it.id, config) }
            },
            onRotateCW = {
                selectedBox?.let {
                    val current = photoConfigurations[it.id] ?: PhotoScanConfiguration()
                    onConfigChange(it.id, current.cycleRotationCW())
                }
            },
            onRotateCCW = {
                selectedBox?.let {
                    val current = photoConfigurations[it.id] ?: PhotoScanConfiguration()
                    onConfigChange(it.id, current.cycleRotationCCW())
                }
            },
            onPrev = { if (selectedIndex > 0) onSelectedIndexChange(selectedIndex - 1) },
            onNext = { if (selectedIndex < boundingBoxList.size() - 1) onSelectedIndexChange(selectedIndex + 1) },
            modifier = Modifier.weight(0.65f).fillMaxHeight(),
        )
    }
}

/** Top app bar with rotation controls and a destructive "Reset" button that requires confirmation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SummaryTopAppBar(
    photoCount: Int,
    onRotateAllCW: () -> Unit,
    onRotateAllCCW: () -> Unit,
    onClearAll: () -> Unit,
    currentStep: WizardStep,
) {
    var showResetConfirmDialog by remember { mutableStateOf(false) }

    TopAppBar(
        title = { Text("Crop & Rotate") },
        navigationIcon = { WizardStepIndicator(currentStep = currentStep) },
        actions = {
            TopAppBarActions(
                onRotateAllCCW = onRotateAllCCW,
                onRotateAllCW = onRotateAllCW,
                onReset = { showResetConfirmDialog = true },
            )
        },
    )

    if (showResetConfirmDialog) {
        ResetConfirmDialog(
            photoCount = photoCount,
            onConfirm = {
                onClearAll()
                showResetConfirmDialog = false
            },
            onDismiss = { showResetConfirmDialog = false },
        )
    }
}

/** Action buttons in the top app bar. */
@Composable
private fun TopAppBarActions(
    onRotateAllCCW: () -> Unit,
    onRotateAllCW: () -> Unit,
    onReset: () -> Unit,
) {
    IconButton(onClick = onRotateAllCCW) {
        Icon(Icons.AutoMirrored.Filled.RotateLeft, "Rotate all counter-clockwise")
    }
    IconButton(onClick = onRotateAllCW) {
        Icon(Icons.AutoMirrored.Filled.RotateRight, "Rotate all clockwise")
    }
    OutlinedButton(
        onClick = onReset,
        modifier = Modifier.height(32.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) {
        Text("Reset", style = MaterialTheme.typography.labelSmall)
    }
}

/** Confirmation dialog for the destructive "Reset" action. */
@Composable
private fun ResetConfirmDialog(photoCount: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reset All Rotations?") },
        text = {
            Text(
                "This will clear all rotation and correction settings for " +
                    "$photoCount ${if (photoCount == 1) "photo" else "photos"}. You can still use Undo after resetting."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Reset", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/**
 * Left sidebar: scrollable list of photo cards with thumbnails, bulk action buttons, and
 * selection state.
 */
@Composable
private fun PhotoSidebarList(
    image: BufferedImage,
    previewCache: PreviewCache,
    boundingBoxList: BoundingBoxList,
    photoConfigurations: Map<String, PhotoScanConfiguration>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onRotateAllCW: () -> Unit,
    onRotateAllCCW: () -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingDeleteIndex by remember { mutableStateOf<Int?>(null) }

    Column(modifier = modifier) {
        BulkActionButtons(
            onRotateAllCW = onRotateAllCW,
            onRotateAllCCW = onRotateAllCCW,
            onClearAll = onClearAll,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier =
                Modifier.weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(4.dp),
        ) {
            itemsIndexed(boundingBoxList.boxes) { index, box ->
                val config = photoConfigurations[box.id] ?: PhotoScanConfiguration()
                val thumbnail = remember(image, box, config) { previewCache.getThumbnail(image, box, config) }

                SidebarPhotoCard(
                    index = index,
                    box = box,
                    config = config,
                    thumbnail = thumbnail,
                    isSelected = index == selectedIndex,
                    onSelect = { onSelectedIndexChange(index) },
                    onDelete = { pendingDeleteIndex = index },
                )
            }
        }
    }

    // Delete confirmation dialog
    if (pendingDeleteIndex != null) {
        val deleteIndex = pendingDeleteIndex!!
        AlertDialog(
            onDismissRequest = { pendingDeleteIndex = null },
            title = { Text("Delete Photo?") },
            text = { Text("Remove Photo ${deleteIndex + 1} from the scan? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(deleteIndex)
                    pendingDeleteIndex = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteIndex = null }) { Text("Cancel") }
            },
        )
    }
}

/**
 * A single card in the sidebar list. Shows a small thumbnail, photo number, and rotation state.
 * Selected cards are highlighted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SidebarPhotoCard(
    index: Int,
    box: BoundingBox,
    config: PhotoScanConfiguration,
    thumbnail: ImageBitmap?,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    val borderColor =
        if (isSelected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.surface
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 2.dp else 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SidebarThumbnail(index = index, thumbnail = thumbnail)
            SidebarInfoColumn(index = index, box = box, config = config, modifier = Modifier.weight(1f))
            IconButton(onClick = onDelete, modifier = Modifier.height(24.dp).width(24.dp)) {
                Icon(
                    Icons.Default.Delete,
                    "Delete photo",
                    modifier = Modifier.height(16.dp).width(16.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** Thumbnail box within a sidebar card. */
@Composable
private fun SidebarThumbnail(index: Int, thumbnail: ImageBitmap?) {
    Box(
        modifier =
            Modifier.width(60.dp)
                .height(60.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.DarkGray),
        contentAlignment = Alignment.Center,
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail,
                contentDescription = "Photo ${index + 1}",
                modifier = Modifier.fillMaxSize().padding(2.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text("?", style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Info column within a sidebar card showing photo name and dimensions. */
@Composable
private fun SidebarInfoColumn(index: Int, box: BoundingBox, config: PhotoScanConfiguration, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Photo ${index + 1}", style = MaterialTheme.typography.labelMedium)
            if (config.rotationDegrees != 0) {
                Text(
                    "${config.rotationDegrees}°",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (config.aspectRatio != 0.0) {
                val ratioLabel = AspectRatio.entries.find { it.value == config.aspectRatio }?.displayName
                    ?: String.format("%.2f", config.aspectRatio)
                Text(
                    ratioLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            if (config.correctionStrategy != null) {
                Text(
                    config.correctionStrategy.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            "${box.width().toInt()} × ${box.height().toInt()} px",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Right panel: large preview of the selected photo with detailed controls. Clicking the image
 * opens a full-screen preview dialog.
 */
@Composable
private fun DetailPreviewPanel(
    image: BufferedImage,
    previewCache: PreviewCache,
    box: BoundingBox?,
    config: PhotoScanConfiguration,
    index: Int,
    totalPhotos: Int,
    onConfigChange: (PhotoScanConfiguration) -> Unit,
    onRotateCW: () -> Unit,
    onRotateCCW: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showFullscreen by remember { mutableStateOf(false) }
    val preview = box?.let { remember(image, it, config) { previewCache.getFullPreview(image, it, config) } }
    val previewBitmap = remember(preview) { preview?.toComposeImageBitmap() }

    Surface(modifier = modifier, tonalElevation = 1.dp, shape = RoundedCornerShape(0.dp)) {
        if (box != null) {
            Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                DetailPreviewImage(
                    previewBitmap = previewBitmap,
                    index = index,
                    onImageClick = { showFullscreen = true },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                DetailControlsRow(
                    config = config,
                    box = box,
                    index = index,
                    totalPhotos = totalPhotos,
                    onPrev = onPrev,
                    onNext = onNext,
                    onConfigChange = onConfigChange,
                    onRotateCW = onRotateCW,
                    onRotateCCW = onRotateCCW,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }

            if (showFullscreen && previewBitmap != null) {
                SummaryFullscreenPreviewDialog(
                    photoIndex = index,
                    totalCount = totalPhotos,
                    rotationDegrees = config.rotationDegrees,
                    bitmap = previewBitmap,
                    onDismiss = { showFullscreen = false },
                )
            }
        } else {
            EmptyPreviewPlaceholder()
        }
    }
}

/** Image area in the detail preview panel, with a zoom hint overlay. */
@Composable
private fun DetailPreviewImage(
    previewBitmap: ImageBitmap?,
    index: Int,
    onImageClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.DarkGray)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR)))
            .clickable(onClick = onImageClick),
        contentAlignment = Alignment.Center,
    ) {
        if (previewBitmap != null) {
            Image(
                bitmap = previewBitmap,
                contentDescription = "Photo ${index + 1} preview",
                modifier = Modifier.fillMaxSize().padding(8.dp),
                contentScale = ContentScale.Fit,
            )
            ZoomHintOverlay(modifier = Modifier.align(Alignment.BottomEnd))
        } else {
            Text(
                "Could not render preview",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Small "Click to zoom" hint overlay in the bottom-right of the preview. */
@Composable
private fun ZoomHintOverlay(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.padding(8.dp),
        shape = RoundedCornerShape(4.dp),
        color = Color.Black.copy(alpha = 0.5f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.ZoomIn, contentDescription = null, modifier = Modifier.height(14.dp).width(14.dp), tint = Color.White)
            Spacer(Modifier.width(4.dp))
            Text("Click to zoom", style = MaterialTheme.typography.labelSmall, color = Color.White)
        }
    }
}

/** Controls row below the detail preview: photo label, navigation, rotation, dropdowns. */
@Composable
private fun DetailControlsRow(
    config: PhotoScanConfiguration,
    box: BoundingBox,
    index: Int,
    totalPhotos: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onConfigChange: (PhotoScanConfiguration) -> Unit,
    onRotateCW: () -> Unit,
    onRotateCCW: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(tonalElevation = 2.dp, shape = RoundedCornerShape(8.dp), modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DetailLabelAndRotation(
                config = config,
                index = index,
                totalPhotos = totalPhotos,
                onPrev = onPrev,
                onNext = onNext,
                onRotateCW = onRotateCW,
                onRotateCCW = onRotateCCW,
            )
            DetailDropdownRow(
                config = config,
                box = box,
                onConfigChange = onConfigChange,
            )
        }
    }
}

/** Photo label with prev/next navigation and rotation buttons. */
@Composable
private fun DetailLabelAndRotation(
    config: PhotoScanConfiguration,
    index: Int,
    totalPhotos: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onRotateCW: () -> Unit,
    onRotateCCW: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrev, enabled = index > 0) {
                Icon(Icons.AutoMirrored.Filled.ArrowLeft, "Previous photo")
            }
            Text("Photo ${index + 1} of $totalPhotos", style = MaterialTheme.typography.titleSmall)
            IconButton(onClick = onNext, enabled = index < totalPhotos - 1) {
                Icon(Icons.AutoMirrored.Filled.ArrowRight, "Next photo")
            }
            if (config.rotationDegrees != 0) {
                Text(
                    "${config.rotationDegrees}°",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = onRotateCCW) {
                Icon(Icons.AutoMirrored.Filled.RotateLeft, "Rotate counter-clockwise", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onRotateCW) {
                Icon(Icons.AutoMirrored.Filled.RotateRight, "Rotate clockwise", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** Row of dropdown controls: aspect ratio and correction strategy. */
@Composable
private fun DetailDropdownRow(
    config: PhotoScanConfiguration,
    box: BoundingBox,
    onConfigChange: (PhotoScanConfiguration) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AspectRatioDropdown(
            selectedRatio = config.aspectRatio,
            onRatioChange = { ratio -> onConfigChange(config.copy(aspectRatio = ratio)) },
            boxAspectRatio = box.aspectRatio(),
        )
        CorrectionStrategyDropdown(
            selectedStrategy = config.correctionStrategy,
            onStrategyChange = { strategy -> onConfigChange(config.copy(correctionStrategy = strategy)) },
        )
    }
}

/** Placeholder shown when no photo is selected in the detail panel. */
@Composable
private fun EmptyPreviewPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "Select a photo to preview",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Full-screen preview dialog shown when a photo preview is clicked. */
@Composable
private fun SummaryFullscreenPreviewDialog(
    photoIndex: Int,
    totalCount: Int,
    rotationDegrees: Int,
    bitmap: ImageBitmap,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.95f)).clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FullscreenHeaderRow(
                    photoIndex = photoIndex,
                    rotationDegrees = rotationDegrees,
                    totalCount = totalCount,
                    onDismiss = onDismiss,
                )
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Photo ${photoIndex + 1} full preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** Header row in the fullscreen preview dialog. */
@Composable
private fun FullscreenHeaderRow(
    photoIndex: Int,
    totalCount: Int,
    rotationDegrees: Int,
    onDismiss: () -> Unit,
) {
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
            Text("${rotationDegrees}° rotation", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.8f))
        }
    }
}