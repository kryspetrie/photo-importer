@file:Suppress("TooManyFunctions", "MagicNumber")

package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.awt.Cursor
import java.awt.image.BufferedImage
import kotlin.math.pow
import kotlin.math.sqrt
import org.kryspetrie.fileimport.domain.model.FaceRegion
import org.kryspetrie.fileimport.domain.model.RegionType
import org.kryspetrie.fileimport.ui.wizard.state.FaceSize
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration
import org.kryspetrie.fileimport.ui.wizard.state.PhotoScanWizardState

/** Color for each region type when drawn on the canvas. */
private fun regionTypeColor(type: RegionType): Color =
    when (type) {
        RegionType.FACE -> Color.Yellow
        RegionType.PET -> Color(0xFF4FC3F7) // Light blue
        RegionType.BODY -> Color(0xFF81C784) // Green
        RegionType.OBJECT -> Color(0xFFFFB74D) // Orange
    }

/** Returns an appropriate Material icon for the given [RegionType]. */
fun regionTypeIcon(type: RegionType): ImageVector =
    when (type) {
        RegionType.FACE -> Icons.Default.Face
        RegionType.PET -> Icons.Default.Pets
        RegionType.BODY -> Icons.Default.Accessibility
        RegionType.OBJECT -> Icons.Default.Category
    }

/**
 * Immutable snapshot of face region data needed for rendering. Avoids recomposition when unrelated
 * state changes.
 */
@Immutable
private data class FaceRenderData(
    val name: String,
    val type: String,
    val x: Double,
    val y: Double,
    val w: Double,
    val h: Double,
)

/** Convert a [FaceRegion] to a lightweight render data object. */
private fun FaceRegion.toRenderData(): FaceRenderData =
    FaceRenderData(name = name, type = type, x = x, y = y, w = w, h = h)

/**
 * The face selection overlay, drawn inside a Dialog.
 *
 * Interaction model:
 * - Hover over empty space → yellow circle cursor preview (size matches selected Small/Medium/Large)
 * - Hover over a face circle → cursor changes to move/select (HAND_CURSOR)
 * - Click on empty space → add a new face (calls [onPlaceFace])
 * - Click on a face → select it for inline naming (text box auto-focused)
 * - Drag a face → move it
 * - Enter → commit name and close the naming popup
 * - Tab / Shift+Tab → advance/go back for naming
 * - Escape → close naming field, or close overlay if no naming active
 * - Delete/Backspace (empty name) → delete currently selected face
 * - Large X button near edge of each face circle → remove that face
 *
 * @param fullPreview The full-resolution perspective-corrected image preview
 * @param idx The index of the photo being edited
 * @param photoConfig The current photo configuration containing face regions
 * @param state The wizard state for mutating face regions
 * @param selectedRegionType Currently selected region type for new placements
 * @param selectedFaceSize Currently selected face size for new placements
 * @param onRegionTypeChange Callback when region type changes
 * @param onFaceSizeChange Callback when face size changes
 * @param onPlaceFace Callback when user places a face at normalized coordinates
 * @param onDismiss Callback to dismiss the overlay
 * @param inheritedFaceRegions Face regions inherited from the source image's XMP
 * @param onAutoDetectFaces Callback to trigger auto-detection of faces (null if model unavailable)
 * @param autoStartNaming If true, auto-opens naming on the first face (or first unnamed face)
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun FaceSelectorOverlay(
    fullPreview: BufferedImage,
    idx: Int,
    photoConfig: PhotoScanConfiguration,
    state: PhotoScanWizardState,
    selectedRegionType: RegionType,
    selectedFaceSize: FaceSize,
    onRegionTypeChange: (RegionType) -> Unit,
    onFaceSizeChange: (FaceSize) -> Unit,
    onPlaceFace: (Double, Double) -> Unit,
    onDismiss: () -> Unit,
    inheritedFaceRegions: List<FaceRegion>,
    onAutoDetectFaces: (() -> Unit)? = null,
    autoStartNaming: Boolean = false,
) {
    // Cache the image bitmap to avoid recomputing on every recomposition (e.g. hover, drag)
    val imageBitmap = remember(fullPreview) { fullPreview.toComposeImageBitmap() }
    var imageDisplayBounds by remember { mutableStateOf(Rect(0f, 0f, 0f, 0f)) }
    var hoverOffset by remember { mutableStateOf<Offset?>(null) }
    var draggingFaceIdx by remember { mutableStateOf(-1) }
    // Local drag offset in pixels — accumulated during drag, committed to state on drag end
    var dragOffsetPx by remember { mutableStateOf(Offset.Zero) }
    // Track which face the cursor is hovering over (-1 = none)
    var hoveredFaceIdx by remember { mutableStateOf(-1) }
    val faceRegions = photoConfig.faceRegions

    // ── Naming state ──
    // Which face is currently selected for naming (-1 = none)
    var namingFaceIndex by remember { mutableStateOf(-1) }
    var namingInput by remember { mutableStateOf("") }

    // Focus requester for auto-focusing the naming text field
    val namingFocusRequester = remember { FocusRequester() }

    // Auto-focus the naming field when a face is selected for naming
    LaunchedEffect(namingFaceIndex) {
        if (namingFaceIndex >= 0) {
            namingFocusRequester.requestFocus()
        }
    }

    // Auto-start naming: if requested, select first unnamed (or first) face on initial composition
    if (autoStartNaming && namingFaceIndex < 0) {
        val firstUnnamed = faceRegions.indexOfFirst { it.name.isBlank() }
        namingFaceIndex = if (firstUnnamed >= 0) firstUnnamed else if (faceRegions.isNotEmpty()) 0 else -1
        namingInput =
            if (namingFaceIndex in faceRegions.indices) faceRegions[namingFaceIndex].name else ""
    }

    // Advance to the next face for naming (unnamed preferred, wraps around)
    fun advanceToNextFace() {
        if (faceRegions.isEmpty()) {
            namingFaceIndex = -1
            return
        }
        // Commit current name
        if (namingFaceIndex in faceRegions.indices && namingInput.isNotBlank()) {
            state.faceRegions.updateFaceRegionName(idx, namingFaceIndex, namingInput.trim())
        }
        // Find next unnamed face after current
        var nextIdx = -1
        for (i in (namingFaceIndex + 1) until faceRegions.size) {
            val name = if (i == namingFaceIndex) namingInput.trim() else faceRegions[i].name
            if (name.isBlank()) {
                nextIdx = i
                break
            }
        }
        // Wrap around: find first unnamed face from start
        if (nextIdx < 0) {
            for (i in 0 until namingFaceIndex) {
                if (faceRegions[i].name.isBlank()) {
                    nextIdx = i
                    break
                }
            }
        }
        // If still no unnamed, just go to next face (cycling)
        if (nextIdx < 0) {
            nextIdx = (namingFaceIndex + 1) % faceRegions.size
        }
        namingFaceIndex = nextIdx
        namingInput = faceRegions.getOrNull(nextIdx)?.name ?: ""
    }

    // Go back to the previous face for naming
    fun goToPreviousFace() {
        if (faceRegions.isEmpty()) {
            namingFaceIndex = -1
            return
        }
        // Commit current name first
        if (namingFaceIndex in faceRegions.indices && namingInput.isNotBlank()) {
            state.faceRegions.updateFaceRegionName(idx, namingFaceIndex, namingInput.trim())
        }
        val prevIdx = if (namingFaceIndex <= 0) faceRegions.size - 1 else namingFaceIndex - 1
        namingFaceIndex = prevIdx.coerceIn(0, faceRegions.size - 1)
        namingInput = faceRegions.getOrNull(namingFaceIndex)?.name ?: ""
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
        ) {
            // ── LEFT SIDEBAR — matches map picker style ──────────────────────
            Surface(
                modifier = Modifier.fillMaxHeight().width(220.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                tonalElevation = 4.dp,
                shape = RoundedCornerShape(0.dp),
            ) {
                Column(
                    modifier = Modifier.padding(8.dp)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // ── Header with close button ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Default.Face,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            "Face Editor",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                        )
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, "Close", modifier = Modifier.size(16.dp))
                        }
                    }

                    HorizontalDivider()

                    // ── Help text ──
                    Text(
                        if (namingFaceIndex in faceRegions.indices) {
                            val named = faceRegions.count { it.name.isNotBlank() }
                            "Naming ${namingFaceIndex + 1}/${faceRegions.size} ($named named)"
                        } else if (faceRegions.isEmpty()) {
                            "Click to place a ${selectedRegionType.displayName.lowercase()}"
                        } else {
                            "Click to place • Drag to move • Tap face to name"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    HorizontalDivider()

                    // ── Region type selector ──
                    Text(
                        "Region Type",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        RegionType.entries.forEach { type ->
                            val isSelected = selectedRegionType == type
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable { onRegionTypeChange(type) },
                                shape = RoundedCornerShape(4.dp),
                                color =
                                    if (isSelected) regionTypeColor(type).copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                border =
                                    if (isSelected)
                                        androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            regionTypeColor(type),
                                        )
                                    else null,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        regionTypeIcon(type),
                                        contentDescription = type.displayName,
                                        modifier = Modifier.size(14.dp),
                                        tint = if (isSelected) regionTypeColor(type) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        type.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    // ── Size selector ──
                    Text(
                        "Size",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FaceSize.entries.forEach { size ->
                            val isSelected = selectedFaceSize == size
                            val circleColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            Surface(
                                modifier = Modifier.clickable { onFaceSizeChange(size) },
                                shape = CircleShape,
                                color =
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                border =
                                    if (isSelected)
                                        androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                    else null,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Canvas(modifier = Modifier.size(10.dp)) {
                                        val radius = size.radius.toFloat() * 60f
                                        drawCircle(
                                            color = circleColor,
                                            radius = radius.coerceIn(2f, 5f),
                                        )
                                    }
                                    Text(
                                        size.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp,
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    // ── Auto-Detect button ──
                    if (onAutoDetectFaces != null) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable { onAutoDetectFaces() },
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.Face,
                                    contentDescription = "Auto-detect",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    "Auto-Detect Faces",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }

                    // ── Clear All button ──
                    if (faceRegions.isNotEmpty()) {
                        Surface(
                            modifier =
                                Modifier.fillMaxWidth().clickable {
                                    state.faceRegions.clearAllFaceRegions(idx)
                                    namingFaceIndex = -1
                                    namingInput = ""
                                },
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Clear All",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                                Text(
                                    "Clear All",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }

                    // ── Spacer to push inherited faces down ──
                    Spacer(modifier = Modifier.weight(1f, fill = true))

                    // ── Inherited faces ──
                    if (inheritedFaceRegions.isNotEmpty()) {
                        HorizontalDivider()
                        Text(
                            "Inherited (${inheritedFaceRegions.size})",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                        Text(
                            "Click to adopt",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            inheritedFaceRegions.forEach { region ->
                                Surface(
                                    modifier =
                                        Modifier.clickable {
                                            state.faceRegions.addFaceRegion(
                                                idx,
                                                region.name,
                                                region.x,
                                                region.y,
                                                RegionType.fromMwgRs(region.type),
                                            )
                                        },
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            regionTypeIcon(RegionType.fromMwgRs(region.type)),
                                            contentDescription = null,
                                            modifier = Modifier.size(10.dp),
                                            tint = MaterialTheme.colorScheme.tertiary,
                                        )
                                        Text(
                                            region.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── IMAGE AREA ─────────────────────────────────────────────────
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                // ── Naming input panel (bottom center, over the image) ────
                if (namingFaceIndex in faceRegions.indices) {
                    val currentRegion = faceRegions[namingFaceIndex]
                    Surface(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        shape = RoundedCornerShape(8.dp),
                        tonalElevation = 4.dp,
                    ) {
                        Row(
                            modifier =
                                Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                    .onPreviewKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown) {
                                            when (keyEvent.key) {
                                                Key.Tab -> {
                                                    if (keyEvent.isShiftPressed) {
                                                        goToPreviousFace()
                                                    } else {
                                                        advanceToNextFace()
                                                    }
                                                    true
                                                }
                                                Key.Enter -> {
                                                    // Commit name and CLOSE naming field
                                                    if (namingInput.isNotBlank()) {
                                                        state.faceRegions.updateFaceRegionName(
                                                            idx,
                                                            namingFaceIndex,
                                                            namingInput.trim(),
                                                        )
                                                    }
                                                    namingFaceIndex = -1
                                                    true
                                                }
                                                Key.Escape -> {
                                                    namingFaceIndex = -1
                                                    true
                                                }
                                                Key.Delete -> {
                                                    // Delete currently named face
                                                    if (namingFaceIndex in faceRegions.indices) {
                                                        state.faceRegions.removeFaceRegion(
                                                            idx,
                                                            namingFaceIndex,
                                                        )
                                                        // Adjust naming index if needed
                                                        val newIndex =
                                                            namingFaceIndex.coerceAtMost(
                                                                faceRegions.size - 1
                                                            )
                                                        namingFaceIndex =
                                                            if (faceRegions.isEmpty()) -1 else newIndex
                                                        namingInput =
                                                            if (namingFaceIndex in faceRegions.indices)
                                                                faceRegions[namingFaceIndex].name
                                                            else ""
                                                    }
                                                    true
                                                }
                                                Key.Backspace -> {
                                                    // Only delete face if naming input is empty
                                                    if (namingInput.isEmpty() && namingFaceIndex in faceRegions.indices) {
                                                        state.faceRegions.removeFaceRegion(
                                                            idx,
                                                            namingFaceIndex,
                                                        )
                                                        val newIndex =
                                                            namingFaceIndex.coerceAtMost(
                                                                faceRegions.size - 1
                                                            )
                                                        namingFaceIndex =
                                                            if (faceRegions.isEmpty()) -1 else newIndex
                                                        namingInput =
                                                            if (namingFaceIndex in faceRegions.indices)
                                                                faceRegions[namingFaceIndex].name
                                                            else ""
                                                        true
                                                    } else {
                                                        false
                                                    }
                                                }
                                                else -> false
                                            }
                                        } else {
                                            false
                                        }
                                    },
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                regionTypeIcon(RegionType.fromMwgRs(currentRegion.type)),
                                contentDescription = currentRegion.type,
                                modifier = Modifier.size(16.dp),
                                tint = regionTypeColor(RegionType.fromMwgRs(currentRegion.type)),
                            )
                            Text(
                                "Face ${namingFaceIndex + 1}/${faceRegions.size}:",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                            )
                            OutlinedTextField(
                                value = namingInput,
                                onValueChange = { namingInput = it },
                                placeholder = { Text("Name...", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.width(120.dp).focusRequester(namingFocusRequester),
                                textStyle = MaterialTheme.typography.labelSmall,
                                singleLine = true,
                            )
                            Text(
                                "Enter→save • Tab→next • Esc→done",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp,
                            )
                        }
                    }
                }

                // ── Image + overlays ────────────────────────────────────
                Box(
                    modifier =
                        Modifier.fillMaxSize()
                            // Cursor: move/select when hovering a face, crosshair for empty space
                            .pointerHoverIcon(
                                PointerIcon(
                                    if (hoveredFaceIdx >= 0) Cursor(Cursor.MOVE_CURSOR)
                                    else Cursor(Cursor.CROSSHAIR_CURSOR)
                                )
                            )
                            .onGloballyPositioned { layoutCoords ->
                                val imgW = fullPreview.width.toFloat()
                                val imgH = fullPreview.height.toFloat()
                                val containerW = layoutCoords.size.width.toFloat()
                                val containerH = layoutCoords.size.height.toFloat()
                                if (imgW > 0f && imgH > 0f && containerW > 0f && containerH > 0f) {
                                    val scale = minOf(containerW / imgW, containerH / imgH)
                                    val drawW = imgW * scale
                                    val drawH = imgH * scale
                                    val offsetX = (containerW - drawW) / 2f
                                    val offsetY = (containerH - drawH) / 2f
                                    imageDisplayBounds =
                                        Rect(offsetX, offsetY, offsetX + drawW, offsetY + drawH)
                                }
                            }
                            // ── Hover tracking ──
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        when (event.type) {
                                            PointerEventType.Move -> {
                                                val pos = event.changes.firstOrNull()?.position
                                                hoverOffset = pos
                                                if (pos != null && imageDisplayBounds.width > 0f) {
                                                    hoveredFaceIdx =
                                                        findClosestFace(
                                                            pos,
                                                            faceRegions,
                                                            imageDisplayBounds,
                                                        )
                                                } else {
                                                    hoveredFaceIdx = -1
                                                }
                                            }
                                            PointerEventType.Exit -> {
                                                hoverOffset = null
                                                hoveredFaceIdx = -1
                                            }
                                            else -> { /* no-op */ }
                                        }
                                    }
                                }
                            }
                            // ── Drag gestures → move face ──
                            .pointerInput(faceRegions.toList()) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        val closestIdx =
                                            findClosestFace(offset, faceRegions, imageDisplayBounds)
                                        if (closestIdx >= 0) {
                                            draggingFaceIdx = closestIdx
                                            dragOffsetPx = Offset.Zero
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        if (
                                            draggingFaceIdx >= 0 &&
                                                draggingFaceIdx < faceRegions.size
                                        ) {
                                            // Accumulate pixel offset locally — no state update per frame
                                            dragOffsetPx =
                                                Offset(
                                                    dragOffsetPx.x + dragAmount.x,
                                                    dragOffsetPx.y + dragAmount.y,
                                                )
                                        }
                                    },
                                    onDragEnd = {
                                        if (
                                            draggingFaceIdx >= 0 &&
                                                draggingFaceIdx < faceRegions.size
                                        ) {
                                            val bounds = imageDisplayBounds
                                            if (bounds.width > 0f && bounds.height > 0f) {
                                                val region = faceRegions[draggingFaceIdx]
                                                val newX =
                                                    (region.x +
                                                            dragOffsetPx.x.toDouble() /
                                                                bounds.width.toDouble())
                                                        .coerceIn(0.0, 1.0)
                                                val newY =
                                                    (region.y +
                                                            dragOffsetPx.y.toDouble() /
                                                                bounds.height.toDouble())
                                                        .coerceIn(0.0, 1.0)
                                                // Single state commit on drag end
                                                state.faceRegions.updateFaceRegion(
                                                    idx,
                                                    draggingFaceIdx,
                                                    x = newX,
                                                    y = newY,
                                                )
                                            }
                                        }
                                        draggingFaceIdx = -1
                                        dragOffsetPx = Offset.Zero
                                    },
                                    onDragCancel = {
                                        draggingFaceIdx = -1
                                        dragOffsetPx = Offset.Zero
                                    },
                                )
                            }
                            // ── Tap gestures → place face, name face, or delete face ──
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    val bounds = imageDisplayBounds
                                    if (bounds.width > 0f && bounds.height > 0f) {
                                        val closestIdx =
                                            findClosestFace(offset, faceRegions, bounds)
                                        if (closestIdx >= 0) {
                                            // Clicked on a face — check if it's the delete X button
                                            val region = faceRegions[closestIdx]
                                            val cx =
                                                bounds.left + (region.x * bounds.width).toFloat()
                                            val cy =
                                                bounds.top + (region.y * bounds.height).toFloat()
                                            val radius =
                                                (region.w / 2.0 * bounds.height).toFloat()
                                            // Delete X is at the edge of the circle (right side)
                                            val deleteX = cx + radius * 0.75f
                                            val deleteY = cy - radius * 0.75f
                                            val distToDelete =
                                                sqrt(
                                                    (offset.x - deleteX).pow(2) +
                                                        (offset.y - deleteY).pow(2)
                                                )
                                            if (distToDelete < 22f) {
                                                // Clicked on the delete X
                                                state.faceRegions.removeFaceRegion(idx, closestIdx)
                                                // Adjust naming index if needed
                                                if (namingFaceIndex == closestIdx) {
                                                    namingFaceIndex = -1
                                                    namingInput = ""
                                                } else if (namingFaceIndex > closestIdx) {
                                                    namingFaceIndex--
                                                }
                                            } else {
                                                // Click on face → start naming it
                                                namingFaceIndex = closestIdx
                                                namingInput = faceRegions[closestIdx].name
                                            }
                                        } else {
                                            // Click on empty space → place a new face
                                            val normX =
                                                ((offset.x - bounds.left) / bounds.width)
                                                    .toDouble()
                                                    .coerceIn(0.0, 1.0)
                                            val normY =
                                                ((offset.y - bounds.top) / bounds.height)
                                                    .toDouble()
                                                    .coerceIn(0.0, 1.0)
                                            if (normX in 0.0..1.0 && normY in 0.0..1.0) {
                                                onPlaceFace(normX, normY)
                                            }
                                        }
                                    }
                                }
                            }
                            // ── Global keyboard shortcuts (Escape) ──
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.key) {
                                        Key.Escape -> {
                                            if (namingFaceIndex >= 0) {
                                                // Close naming field
                                                if (namingFaceIndex in faceRegions.indices && namingInput.isNotBlank()) {
                                                    state.faceRegions.updateFaceRegionName(
                                                        idx,
                                                        namingFaceIndex,
                                                        namingInput.trim(),
                                                    )
                                                }
                                                namingFaceIndex = -1
                                                namingInput = ""
                                                true
                                            } else {
                                                // Close overlay
                                                onDismiss()
                                                true
                                            }
                                        }
                                        Key.Tab -> {
                                            if (namingFaceIndex >= 0 && faceRegions.isNotEmpty()) {
                                                if (keyEvent.isShiftPressed) {
                                                    goToPreviousFace()
                                                } else {
                                                    advanceToNextFace()
                                                }
                                                true
                                            } else {
                                                false
                                            }
                                        }
                                        Key.Delete -> {
                                            if (namingFaceIndex in faceRegions.indices) {
                                                state.faceRegions.removeFaceRegion(
                                                    idx,
                                                    namingFaceIndex,
                                                )
                                                val newIndex =
                                                    namingFaceIndex.coerceAtMost(faceRegions.size - 1)
                                                namingFaceIndex =
                                                    if (faceRegions.isEmpty()) -1 else newIndex
                                                namingInput =
                                                    if (namingFaceIndex in faceRegions.indices)
                                                        faceRegions[namingFaceIndex].name
                                                    else ""
                                                true
                                            } else {
                                                false
                                            }
                                        }
                                        else -> false
                                    }
                                } else {
                                    false
                                }
                            }
                ) {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = "Select faces on photo ${idx + 1}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )

                    // ── Canvas overlay: face circles, hover preview, inherited faces ──
                    val textMeasurer = rememberTextMeasurer()
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val bounds = imageDisplayBounds
                        if (bounds.width <= 0f || bounds.height <= 0f) return@Canvas

                        // ── Draw existing face regions ──
                        for (faceIdx in faceRegions.indices) {
                            val region = faceRegions[faceIdx]
                            val renderData = region.toRenderData()
                            val color = regionTypeColor(RegionType.fromMwgRs(renderData.type))
                            val isDragging = faceIdx == draggingFaceIdx
                            val isNamingSelected = faceIdx == namingFaceIndex
                            val isHovered = faceIdx == hoveredFaceIdx
                            val currentDragOffset = if (isDragging) dragOffsetPx else Offset.Zero

                            val cx = bounds.left + (renderData.x * bounds.width).toFloat() + currentDragOffset.x
                            val cy = bounds.top + (renderData.y * bounds.height).toFloat() + currentDragOffset.y
                            val radius = (renderData.w / 2.0 * bounds.height).toFloat()

                            // ── Fill ──
                            when {
                                isDragging -> drawCircle(color = color.copy(alpha = 0.4f), radius = radius, center = Offset(cx, cy))
                                isNamingSelected -> drawCircle(color = color.copy(alpha = 0.25f), radius = radius, center = Offset(cx, cy))
                                isHovered -> drawCircle(color = color.copy(alpha = 0.15f), radius = radius, center = Offset(cx, cy))
                            }

                            // ── Outline ──
                            drawCircle(
                                color = color,
                                radius = radius,
                                center = Offset(cx, cy),
                                style = Stroke(width = if (isNamingSelected) 3f else 2f),
                            )

                            // ── Naming halo ──
                            if (isNamingSelected) {
                                drawCircle(
                                    color = Color.White,
                                    radius = radius + 4f,
                                    center = Offset(cx, cy),
                                    style = Stroke(width = 2f),
                                )
                            }

                            // ── Name label ──
                            val nameLabel = renderData.name
                            if (nameLabel.isNotBlank()) {
                                val nameLayout = textMeasurer.measure(
                                    nameLabel,
                                    TextStyle(color = Color.White, fontSize = 11.sp)
                                )
                                val labelWidth = nameLayout.size.width.toFloat() + 10f
                                val labelHeight = nameLayout.size.height.toFloat() + 4f
                                val labelX = cx - labelWidth / 2f
                                val labelY = cy - radius - 20f

                                drawRoundRect(
                                    color = color.copy(alpha = 0.85f),
                                    topLeft = Offset(labelX, labelY),
                                    size = Size(labelWidth, labelHeight),
                                )
                                drawText(
                                    textLayoutResult = nameLayout,
                                    topLeft = Offset(cx - nameLayout.size.width.toFloat() / 2f, labelY + 2f),
                                )
                            }

                            // ── Delete X button near edge of circle if not dragging ──
                            if (!isDragging) {
                                // Position at top-right edge of the circle
                                val angle = -Math.PI / 4.0 // 45 degrees top-right
                                val deleteX = cx + (radius * kotlin.math.cos(angle)).toFloat()
                                val deleteY = cy + (radius * kotlin.math.sin(angle)).toFloat()
                                val btnRadius = if (isHovered || isNamingSelected) 16f else 12f
                                val xSize = if (isHovered || isNamingSelected) 8f else 6f
                                val xStroke = if (isHovered || isNamingSelected) 3f else 2f
                                val btnAlpha = if (isHovered || isNamingSelected) 1.0f else 0.85f

                                drawCircle(
                                    color = Color.Red.copy(alpha = btnAlpha),
                                    radius = btnRadius,
                                    center = Offset(deleteX, deleteY),
                                )
                                drawLine(
                                    color = Color.White,
                                    start = Offset(deleteX - xSize, deleteY - xSize),
                                    end = Offset(deleteX + xSize, deleteY + xSize),
                                    strokeWidth = xStroke,
                                    cap = StrokeCap.Round,
                                )
                                drawLine(
                                    color = Color.White,
                                    start = Offset(deleteX + xSize, deleteY - xSize),
                                    end = Offset(deleteX - xSize, deleteY + xSize),
                                    strokeWidth = xStroke,
                                    cap = StrokeCap.Round,
                                )
                            }
                        }

                        // ── Yellow circle cursor preview when hovering over empty space ──
                        if (hoverOffset != null && hoveredFaceIdx < 0 && imageDisplayBounds.width > 0f) {
                            val previewRadius = (selectedFaceSize.radius * imageDisplayBounds.height).toFloat()
                            drawCircle(
                                color = regionTypeColor(selectedRegionType).copy(alpha = 0.5f),
                                radius = previewRadius,
                                center = hoverOffset!!,
                                style = Stroke(width = 2f),
                            )
                        }

                        // ── Inherited face regions ──
                        for (region in inheritedFaceRegions) {
                            val cx = bounds.left + (region.x * bounds.width).toFloat()
                            val cy = bounds.top + (region.y * bounds.height).toFloat()
                            val radius = (region.w / 2.0 * bounds.height).toFloat()

                            drawCircle(
                                color = Color.Cyan,
                                radius = radius,
                                center = Offset(cx, cy),
                                style = Stroke(width = 1.5f),
                            )

                            val inheritedLabel = region.name
                            val inheritedLayout =
                                textMeasurer.measure(
                                    inheritedLabel,
                                    TextStyle(color = Color.White, fontSize = 10.sp),
                                )
                            drawRoundRect(
                                color = Color.Cyan.copy(alpha = 0.7f),
                                topLeft =
                                    Offset(
                                        cx - inheritedLayout.size.width.toFloat() / 2f - 4f,
                                        cy - radius - 18f,
                                    ),
                                size =
                                    Size(
                                        inheritedLayout.size.width.toFloat() + 8f,
                                        inheritedLayout.size.height.toFloat() + 4f,
                                    ),
                            )
                            drawText(
                                textLayoutResult = inheritedLayout,
                                topLeft =
                                    Offset(
                                        cx - inheritedLayout.size.width.toFloat() / 2f,
                                        cy - radius - 16f,
                                    ),
                            )

                            // Plus icon at bottom (adopt indicator)
                            val plusX = cx
                            val plusY = cy + radius + 8f
                            drawCircle(
                                color = Color.Cyan.copy(alpha = 0.7f),
                                radius = 8f,
                                center = Offset(plusX, plusY),
                            )
                            drawLine(
                                color = Color.White,
                                start = Offset(plusX - 4f, plusY),
                                end = Offset(plusX + 4f, plusY),
                                strokeWidth = 2f,
                                cap = StrokeCap.Round,
                            )
                            drawLine(
                                color = Color.White,
                                start = Offset(plusX, plusY - 4f),
                                end = Offset(plusX, plusY + 4f),
                                strokeWidth = 2f,
                                cap = StrokeCap.Round,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Find the face region closest to the given screen offset, if within its radius. Returns -1 if no
 * face is close enough.
 */
private fun findClosestFace(offset: Offset, faceRegions: List<FaceRegion>, bounds: Rect): Int {
    if (bounds.width <= 0f || bounds.height <= 0f) return -1

    var bestIdx = -1
    var bestDist = Float.MAX_VALUE

    for (idx in faceRegions.indices) {
        val region = faceRegions[idx]
        val cx = bounds.left + (region.x * bounds.width).toFloat()
        val cy = bounds.top + (region.y * bounds.height).toFloat()
        val radius = (region.w / 2.0 * bounds.height).toFloat()
        val dist = sqrt((offset.x - cx).pow(2) + (offset.y - cy).pow(2))
        if (dist < radius + 10f && dist < bestDist) {
            bestDist = dist
            bestIdx = idx
        }
    }
    return bestIdx
}