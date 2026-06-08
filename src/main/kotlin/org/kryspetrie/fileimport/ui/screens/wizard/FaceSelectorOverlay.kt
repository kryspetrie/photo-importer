@file:Suppress("TooManyFunctions", "MagicNumber")

package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import java.awt.image.BufferedImage
import kotlin.math.pow
import kotlin.math.sqrt
import org.kryspetrie.fileimport.domain.model.FaceRegion
import org.kryspetrie.fileimport.domain.model.RegionType
import org.kryspetrie.fileimport.infrastructure.wizard.FaceSize
import org.kryspetrie.fileimport.infrastructure.wizard.PhotoConfiguration
import org.kryspetrie.fileimport.infrastructure.wizard.PhotoScanWizardState

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
 * The face selection overlay, drawn inside a Popup.
 *
 * Features:
 * - Left side toolbar for region type (Face/Pet/Body/Object) and size (S/M/L) selection
 * - Canvas overlay for drawing circles, labels, hover preview
 * - Tap to place, drag to move existing circles
 * - Inherited face regions shown in cyan with "adopt" click support
 */
@Composable
fun FaceSelectorOverlay(
    fullPreview: BufferedImage,
    idx: Int,
    photoConfig: PhotoConfiguration,
    state: PhotoScanWizardState,
    selectedRegionType: RegionType,
    selectedFaceSize: FaceSize,
    onRegionTypeChange: (RegionType) -> Unit,
    onFaceSizeChange: (FaceSize) -> Unit,
    onPlaceFace: (Double, Double) -> Unit,
    onDismiss: () -> Unit,
    inheritedFaceRegions: List<FaceRegion>,
) {
    var imageDisplayBounds by remember { mutableStateOf(Rect(0f, 0f, 0f, 0f)) }
    var hoverOffset by remember { mutableStateOf<Offset?>(null) }
    var draggingFaceIdx by remember { mutableStateOf(-1) }
    // Store the starting normalized position of the face being dragged, so we can
    // compute the final position as: startPos + cumulativePixelOffset / imageDimension.
    // This avoids drift caused by adding per-frame deltas to an already-updated position.
    var dragStartNormX by remember { mutableStateOf(0.0) }
    var dragStartNormY by remember { mutableStateOf(0.0) }
    var dragTotalPixelDx by remember { mutableStateOf(0f) }
    var dragTotalPixelDy by remember { mutableStateOf(0f) }
    val faceRegions = photoConfig.faceRegions
    val textMeasurer = rememberTextMeasurer()

    Popup(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            // ── Side toolbar ──────────────────────────────────────────────
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                color = Color.Black.copy(alpha = 0.8f),
                shape = RoundedCornerShape(8.dp),
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Close button
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp),
                        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                    ) {
                        Icon(Icons.Default.Close, "Close", modifier = Modifier.size(20.dp))
                    }

                    Text("Type", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    // Region type buttons
                    RegionType.entries.forEach { type ->
                        val isSelected = selectedRegionType == type
                        Surface(
                            modifier = Modifier.clickable { onRegionTypeChange(type) },
                            shape = RoundedCornerShape(6.dp),
                            color =
                                if (isSelected) Color.White.copy(alpha = 0.9f)
                                else Color.White.copy(alpha = 0.2f),
                            border =
                                if (isSelected)
                                    androidx.compose.foundation.BorderStroke(
                                        2.dp,
                                        regionTypeColor(type),
                                    )
                                else null,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    regionTypeIcon(type),
                                    contentDescription = type.displayName,
                                    modifier = Modifier.size(14.dp),
                                    tint = if (isSelected) Color.Black else Color.White,
                                )
                                Text(
                                    type.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) Color.Black else Color.White,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.size(4.dp))
                    Text("Size", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    // Size buttons
                    FaceSize.entries.forEach { size ->
                        val isSelected = selectedFaceSize == size
                        Surface(
                            modifier = Modifier.clickable { onFaceSizeChange(size) },
                            shape = CircleShape,
                            color =
                                if (isSelected) Color.White.copy(alpha = 0.9f)
                                else Color.White.copy(alpha = 0.2f),
                            border =
                                if (isSelected)
                                    androidx.compose.foundation.BorderStroke(2.dp, Color.Yellow)
                                else null,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Preview circle proportional to size
                                Canvas(modifier = Modifier.size(16.dp)) {
                                    val radius = size.radius.toFloat() * 60f
                                    drawCircle(
                                        color = if (isSelected) Color.Black else Color.White,
                                        radius = radius.coerceIn(2f, 8f),
                                    )
                                }
                                Text(
                                    size.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) Color.Black else Color.White,
                                )
                            }
                        }
                    }

                    // Clear all button
                    if (faceRegions.isNotEmpty()) {
                        Spacer(Modifier.size(4.dp))
                        Text(
                            "Clear All",
                            color = Color(0xFFFF6666),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.clickable { state.clearAllFaceRegions(idx) },
                        )
                    }
                }
            }

            // ── Inherited faces in toolbar ────────────────────────────────
            if (inheritedFaceRegions.isNotEmpty()) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                    color = Color.Black.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            "Inherited (${inheritedFaceRegions.size}) — click to adopt",
                            color = Color.Cyan,
                            style = MaterialTheme.typography.labelSmall,
                        )
                        inheritedFaceRegions.forEach { region ->
                            Surface(
                                modifier =
                                    Modifier.clickable {
                                        state.addFaceRegion(
                                            idx,
                                            region.name,
                                            region.x,
                                            region.y,
                                            RegionType.fromMwgRs(region.type),
                                        )
                                    },
                                shape = RoundedCornerShape(4.dp),
                                color = Color.Cyan.copy(alpha = 0.3f),
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
                                        tint = Color.Cyan,
                                    )
                                    Text(
                                        region.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Help text at top center ──────────────────────────────────
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(8.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Face,
                        null,
                        tint = Color.Yellow,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        "Click to place • Drag to move",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            // ── Image + Canvas overlay ────────────────────────────────────
            Box(
                modifier =
                    Modifier.fillMaxSize()
                        .padding(start = 100.dp, top = 40.dp, end = 16.dp, bottom = 16.dp)
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
                        .pointerInput(Unit) {
                            // Track hover position for preview circle
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.type == PointerEventType.Move) {
                                        hoverOffset = event.changes.firstOrNull()?.position
                                    } else if (event.type == PointerEventType.Exit) {
                                        hoverOffset = null
                                    }
                                }
                            }
                        }
                        .pointerInput(faceRegions.toList()) {
                            // Combined drag (for moving existing faces) and tap (for placing new
                            // faces)
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val bounds = imageDisplayBounds
                                    if (bounds.width > 0f && bounds.height > 0f) {
                                        val bestIdx = findClosestFace(offset, faceRegions, bounds)
                                        if (bestIdx >= 0) {
                                            draggingFaceIdx = bestIdx
                                            // Store start position so we compute:
                                            // final = start + totalPixelDrag / imageDimension
                                            dragStartNormX = faceRegions[bestIdx].x
                                            dragStartNormY = faceRegions[bestIdx].y
                                            dragTotalPixelDx = 0f
                                            dragTotalPixelDy = 0f
                                        }
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    if (
                                        draggingFaceIdx >= 0 && draggingFaceIdx < faceRegions.size
                                    ) {
                                        dragTotalPixelDx += dragAmount.x
                                        dragTotalPixelDy += dragAmount.y
                                        val bounds = imageDisplayBounds
                                        if (bounds.width > 0f && bounds.height > 0f) {
                                            val newX =
                                                (dragStartNormX +
                                                        dragTotalPixelDx.toDouble() /
                                                            bounds.width.toDouble())
                                                    .coerceIn(0.0, 1.0)
                                            val newY =
                                                (dragStartNormY +
                                                        dragTotalPixelDy.toDouble() /
                                                            bounds.height.toDouble())
                                                    .coerceIn(0.0, 1.0)
                                            state.updateFaceRegion(
                                                idx,
                                                draggingFaceIdx,
                                                x = newX,
                                                y = newY,
                                            )
                                        }
                                    }
                                },
                                onDragEnd = { draggingFaceIdx = -1 },
                                onDragCancel = { draggingFaceIdx = -1 },
                            )
                        }
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val bounds = imageDisplayBounds
                                if (bounds.width > 0f && bounds.height > 0f) {
                                    // Check if tapping on an existing face circle's delete zone
                                    val tappedIdx = findClosestFace(offset, faceRegions, bounds)
                                    if (tappedIdx >= 0) {
                                        val region = faceRegions[tappedIdx]
                                        val cx = bounds.left + (region.x * bounds.width).toFloat()
                                        val cy = bounds.top + (region.y * bounds.height).toFloat()
                                        val radius = (region.w / 2.0 * bounds.height).toFloat()
                                        // Delete X at bottom of circle
                                        val deleteX = cx
                                        val deleteY = cy + radius + 14f
                                        val distToDelete =
                                            sqrt(
                                                (offset.x - deleteX).pow(2) +
                                                    (offset.y - deleteY).pow(2)
                                            )
                                        if (distToDelete < 14f) {
                                            state.removeFaceRegion(idx, tappedIdx)
                                            return@detectTapGestures
                                        }
                                    }

                                    // Place a new face
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
            ) {
                Image(
                    bitmap = fullPreview.toComposeImageBitmap(),
                    contentDescription = "Select faces on photo ${idx + 1}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )

                // Canvas overlay for face circles, labels, and hover preview
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val bounds = imageDisplayBounds
                    if (bounds.width <= 0f || bounds.height <= 0f) return@Canvas

                    // ── Draw existing face regions ──────────────────────
                    for (faceIdx in faceRegions.indices) {
                        val region = faceRegions[faceIdx]
                        val cx = bounds.left + (region.x * bounds.width).toFloat()
                        val cy = bounds.top + (region.y * bounds.height).toFloat()
                        val radius = (region.w / 2.0 * bounds.height).toFloat()
                        val color = regionTypeColor(RegionType.fromMwgRs(region.type))

                        // Circle outline (or filled if dragging)
                        if (faceIdx == draggingFaceIdx) {
                            drawCircle(
                                color = color.copy(alpha = 0.4f),
                                radius = radius,
                                center = Offset(cx, cy),
                            )
                        }
                        drawCircle(
                            color = color,
                            radius = radius,
                            center = Offset(cx, cy),
                            style = Stroke(width = 2f),
                        )

                        // Name label above the circle
                        val nameLabel = region.name
                        val nameLayout =
                            textMeasurer.measure(
                                nameLabel,
                                TextStyle(color = Color.Black, fontSize = 11.sp),
                            )
                        drawRoundRect(
                            color = color.copy(alpha = 0.85f),
                            topLeft =
                                Offset(
                                    cx - nameLayout.size.width.toFloat() / 2f - 4f,
                                    cy - radius - 20f,
                                ),
                            size =
                                Size(
                                    nameLayout.size.width.toFloat() + 8f,
                                    nameLayout.size.height.toFloat() + 4f,
                                ),
                        )
                        drawText(
                            textLayoutResult = nameLayout,
                            topLeft =
                                Offset(cx - nameLayout.size.width.toFloat() / 2f, cy - radius - 18f),
                        )

                        // Region type icon indicator (small colored dot)
                        drawCircle(
                            color = color,
                            radius = 5f,
                            center =
                                Offset(
                                    cx - nameLayout.size.width.toFloat() / 2f - 12f,
                                    cy - radius - 12f,
                                ),
                        )

                        // Delete X at bottom of circle
                        val deleteX = cx
                        val deleteY = cy + radius + 10f
                        drawCircle(
                            color = Color.Red.copy(alpha = 0.85f),
                            radius = 10f,
                            center = Offset(deleteX, deleteY),
                        )
                        drawLine(
                            color = Color.White,
                            start = Offset(deleteX - 5f, deleteY - 5f),
                            end = Offset(deleteX + 5f, deleteY + 5f),
                            strokeWidth = 2f,
                            cap = StrokeCap.Round,
                        )
                        drawLine(
                            color = Color.White,
                            start = Offset(deleteX + 5f, deleteY - 5f),
                            end = Offset(deleteX - 5f, deleteY + 5f),
                            strokeWidth = 2f,
                            cap = StrokeCap.Round,
                        )
                    }

                    // ── Draw inherited face regions (cyan) ──────────────
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

                    // ── Draw hover preview ────────────────────────────
                    val currentHoverOffset = hoverOffset
                    if (currentHoverOffset != null && draggingFaceIdx < 0) {
                        val bounds2 = imageDisplayBounds
                        if (bounds2.width > 0f && bounds2.height > 0f) {
                            val normX =
                                ((currentHoverOffset.x - bounds2.left) / bounds2.width)
                                    .toDouble()
                                    .coerceIn(0.0, 1.0)
                            val normY =
                                ((currentHoverOffset.y - bounds2.top) / bounds2.height)
                                    .toDouble()
                                    .coerceIn(0.0, 1.0)
                            if (normX in 0.0..1.0 && normY in 0.0..1.0) {
                                val previewCx = bounds2.left + (normX * bounds2.width).toFloat()
                                val previewCy = bounds2.top + (normY * bounds2.height).toFloat()
                                val previewRadius =
                                    (selectedFaceSize.radius * bounds2.height).toFloat()
                                val previewColor = regionTypeColor(selectedRegionType)

                                drawCircle(
                                    color = previewColor.copy(alpha = 0.35f),
                                    radius = previewRadius,
                                    center = Offset(previewCx, previewCy),
                                )
                                drawCircle(
                                    color = previewColor.copy(alpha = 0.8f),
                                    radius = previewRadius,
                                    center = Offset(previewCx, previewCy),
                                    style = Stroke(width = 2f),
                                )
                            }
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
