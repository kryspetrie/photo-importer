@file:Suppress("TooManyFunctions", "MagicNumber")

package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import java.awt.Cursor
import java.awt.image.BufferedImage
import org.kryspetrie.fileimport.ui.wizard.state.ZoomController

/** Radius of corner circles for quad selection (in pixels). */
private const val CORNER_RADIUS = 8f
/** Hit-test radius for dragging quad corners (in pixels). */
private const val CORNER_HIT_RADIUS = 16f

/**
 * Canvas composable that renders the back image with zoom/pan, crop overlay, and quad selection.
 */
@Composable
internal fun BackImageCanvas(
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
