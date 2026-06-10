package org.kryspetrie.fileimport.ui.screens.wizard.overview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import java.awt.image.BufferedImage
import org.kryspetrie.fileimport.domain.model.DetectionMode
import org.kryspetrie.fileimport.infrastructure.wizard.BoundingBox
import org.kryspetrie.fileimport.infrastructure.wizard.BoundingBoxList
import org.kryspetrie.fileimport.infrastructure.wizard.Corner
import org.kryspetrie.fileimport.infrastructure.wizard.FourPointState
import org.kryspetrie.fileimport.infrastructure.wizard.PhotoConfiguration
import org.kryspetrie.fileimport.infrastructure.wizard.PhotoScanWizardState
import org.kryspetrie.fileimport.infrastructure.wizard.Point
import org.kryspetrie.fileimport.infrastructure.wizard.WizardMode
import org.kryspetrie.fileimport.infrastructure.wizard.ZoomController

@Composable
fun OverviewCanvas(
    state: PhotoScanWizardState,
    image: BufferedImage,
    containerSize: androidx.compose.ui.unit.IntSize,
    wizardMode: WizardMode,
    fourPointState: FourPointState,
    onBoxRejected: () -> Unit,
) {
    val boundingBoxList by state.boundingBoxList.collectAsState()
    val selectedBoxIndex by state.selectedBoxIndex.collectAsState()
    val zoomController by state.zoomController.collectAsState()
    val photoConfigurations by state.photoConfigurations.collectAsState()

    // Track Shift key state for scroll modifiers (e.g., Shift+scroll to rotate).
    // AWT Toolkit.getLockingKeyState(VK_SHIFT) throws "Invalid key for toolkit" on some platforms
    // because Shift is not a locking/toggle key. We track it via Compose key events instead.
    var isShiftHeld by remember { mutableStateOf(false) }

    Canvas(
        modifier =
            Modifier.fillMaxSize()
                .onPreviewKeyEvent { keyEvent ->
                    // Track Shift key state from any key event
                    isShiftHeld = keyEvent.isShiftPressed
                    false // Don't consume — let other handlers process the event
                }
                .then(canvasPointerHandler(state, wizardMode) { isShiftHeld })
    ) {
        drawCanvasContent(
            image = image,
            boundingBoxList = boundingBoxList,
            selectedBoxIndex = selectedBoxIndex,
            wizardMode = wizardMode,
            fourPointState = fourPointState,
            zoomController = zoomController,
            photoConfigurations = photoConfigurations,
        )
    }
}

/**
 * Unified pointer handler for the overview canvas. Merges mouse position tracking, tap selection,
 * corner/box dragging, panning, and scroll-zoom into a single `awaitPointerEventScope`.
 *
 * Why a single handler? Previously, `detectTapGestures` in a separate `pointerInput` consumed the
 * Press event, preventing the drag handler from ever seeing it. This meant corner drags and box
 * drags never started — the user could only pan (background drag) because the drag handler fell
 * through to the else branch on every move. The fix: handle everything in one event loop so Press
 * is seen by the same code that handles Move/Release.
 *
 * Tap detection: On Release, if the total movement distance is below a threshold (8px), we treat it
 * as a tap and perform selection/4-point placement. If movement exceeded the threshold, the
 * interaction was a drag (corner move, box move, or pan) and we skip the tap logic.
 *
 * Undo behavior: A single undo snapshot is saved on Press (before the drag starts). Move events use
 * no-undo variants so intermediate positions don't fill the undo buffer. One undo = one complete
 * drag operation.
 */
private fun canvasPointerHandler(
    state: PhotoScanWizardState,
    wizardMode: WizardMode,
    isShiftHeld: () -> Boolean,
): Modifier =
    Modifier.pointerInput(state, wizardMode) {
        awaitPointerEventScope {
            var isDragging = false
            var isCornerDrag = false
            var isPhotoDrag = false
            var draggedCorner: Corner? = null
            var draggedBoxIndex = -1
            var pressPos = Offset.Zero
            var lastDragPos = Offset.Zero
            var totalMovement = 0f

            while (true) {
                val event = awaitPointerEvent()
                val pos = event.changes.firstOrNull()?.position ?: continue

                when (event.type) {
                    PointerEventType.Press -> {
                        pressPos = pos
                        lastDragPos = pos
                        totalMovement = 0f
                        isDragging = true

                        if (wizardMode == WizardMode.NORMAL) {
                            val boundingBoxList = state.boundingBoxList.value
                            val zoomController = state.zoomController.value
                            // Check if pressing a corner of any box
                            val cornerHit = findCornerHit(pos, boundingBoxList, zoomController)
                            if (cornerHit != null) {
                                // Select the box and start dragging the corner
                                state.selectBox(cornerHit.first)
                                state.selectCorner(cornerHit.second)
                                // Save one undo snapshot before the drag starts
                                state.saveBoxUndoSnapshot(cornerHit.first)
                                draggedCorner = cornerHit.second
                                isCornerDrag = true
                            } else {
                                // Check if pressing inside a box
                                val boxHit = findBoxHit(pos, boundingBoxList, zoomController)
                                if (boxHit >= 0) {
                                    state.selectBox(boxHit)
                                    // Save one undo snapshot before the drag starts
                                    state.saveBoxUndoSnapshot(boxHit)
                                    isPhotoDrag = true
                                    draggedBoxIndex = boxHit
                                }
                            }
                        }
                    }
                    PointerEventType.Move -> {
                        if (isDragging) {
                            totalMovement += (pos - lastDragPos).getDistance()

                            // Update mouse position for 4-point creation preview
                            if (wizardMode == WizardMode.FOUR_POINT) {
                                val zoomController = state.zoomController.value
                                val point =
                                    zoomController.screenToImage(pos.x.toDouble(), pos.y.toDouble())
                                state.updateCreationMousePosition(Point(point.x, point.y))
                            }

                            val zoomController = state.zoomController.value
                            val currentBoxIndex = state.selectedBoxIndex.value
                            when {
                                isCornerDrag && draggedCorner != null && currentBoxIndex >= 0 -> {
                                    val screenPos =
                                        zoomController.screenToImage(
                                            pos.x.toDouble(),
                                            pos.y.toDouble(),
                                        )
                                    // Use withoutUndo variant — undo was saved on Press
                                    state.moveCornerWithoutUndo(
                                        currentBoxIndex,
                                        draggedCorner,
                                        screenPos.x,
                                        screenPos.y,
                                    )
                                }
                                isPhotoDrag && draggedBoxIndex >= 0 -> {
                                    val deltaX = (pos.x - lastDragPos.x) / zoomController.zoom
                                    val deltaY = (pos.y - lastDragPos.y) / zoomController.zoom
                                    // Use withoutUndo variant — undo was saved on Press
                                    state.moveSelectedBoxWithoutUndo(deltaX, deltaY)
                                }
                                else -> {
                                    val deltaX = (pos.x - lastDragPos.x).toDouble()
                                    val deltaY = (pos.y - lastDragPos.y).toDouble()
                                    state.pan(deltaX, deltaY)
                                }
                            }
                            lastDragPos = pos
                        }
                    }
                    PointerEventType.Release -> {
                        if (isDragging) {
                            val tapThreshold = 8f
                            if (totalMovement < tapThreshold) {
                                // Treat as tap — perform selection / 4-point placement
                                val boundingBoxList = state.boundingBoxList.value
                                val zoomController = state.zoomController.value
                                when (wizardMode) {
                                    WizardMode.FOUR_POINT -> {
                                        val point =
                                            zoomController.screenToImage(
                                                pressPos.x.toDouble(),
                                                pressPos.y.toDouble(),
                                            )
                                        state.addFourPoint(Point(point.x, point.y))
                                    }
                                    else -> {
                                        // Only do tap selection if this wasn't already a
                                        // corner/box drag that started on Press
                                        if (!isCornerDrag && !isPhotoDrag) {
                                            val cornerHit =
                                                findCornerHit(
                                                    pressPos,
                                                    boundingBoxList,
                                                    zoomController,
                                                )
                                            if (cornerHit != null) {
                                                state.selectBox(cornerHit.first)
                                                state.selectCorner(cornerHit.second)
                                            } else {
                                                val boxHit =
                                                    findBoxHit(
                                                        pressPos,
                                                        boundingBoxList,
                                                        zoomController,
                                                    )
                                                if (boxHit >= 0) {
                                                    state.selectBox(boxHit)
                                                } else {
                                                    state.deselectAll()
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        isDragging = false
                        isCornerDrag = false
                        isPhotoDrag = false
                        draggedCorner = null
                        draggedBoxIndex = -1
                    }
                    PointerEventType.Scroll -> {
                        val scrollDelta = event.changes.firstOrNull()?.scrollDelta
                        if (scrollDelta != null) {
                            val boundingBoxList = state.boundingBoxList.value
                            val zoomCtrl = state.zoomController.value
                            val hoveredBoxIndex = findBoxHit(pos, boundingBoxList, zoomCtrl)
                            when {
                                isShiftHeld() && hoveredBoxIndex >= 0 -> {
                                    // Shift+scroll over box: rotate the box around its center
                                    val rotationStep = 2.0 // degrees per scroll tick
                                    state.selectBox(hoveredBoxIndex)
                                    if (scrollDelta.y < 0) {
                                        state.rotateSelectedBox(-rotationStep)
                                    } else {
                                        state.rotateSelectedBox(rotationStep)
                                    }
                                }
                                hoveredBoxIndex >= 0 && state.selectedBoxIndex.value >= 0 -> {
                                    // Scroll over box (no shift): expand/contract the selected box
                                    val scaleFactor = if (scrollDelta.y < 0) 1.05 else 0.95
                                    state.expandSelectedBox(scaleFactor)
                                }
                                else -> {
                                    // Scroll on empty space: zoom
                                    if (scrollDelta.y < 0) {
                                        state.zoomIn(pos.x.toDouble(), pos.y.toDouble())
                                    } else {
                                        state.zoomOut(pos.x.toDouble(), pos.y.toDouble())
                                    }
                                }
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
    }

/** Draws all canvas content: background, image, bounding boxes, and 4-point preview. */
private fun DrawScope.drawCanvasContent(
    image: BufferedImage,
    boundingBoxList: BoundingBoxList,
    selectedBoxIndex: Int,
    wizardMode: WizardMode,
    fourPointState: FourPointState,
    zoomController: ZoomController,
    photoConfigurations: Map<String, PhotoConfiguration>,
) {
    val scale = zoomController.zoom.toFloat()
    val panX = zoomController.panX.toFloat()
    val panY = zoomController.panY.toFloat()

    // Draw background
    drawRect(
        color = Color.DarkGray,
        topLeft = Offset(panX, panY),
        size = Size(image.width * scale, image.height * scale),
    )

    // Draw image (sampled for performance when zoomed out; full resolution when zoomed in)
    val displayImage = createSampledImage(image, scale.toDouble())
    if (displayImage != null) {
        drawImage(
            image = displayImage.toComposeImageBitmap(),
            srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
            srcSize = androidx.compose.ui.unit.IntSize(displayImage.width, displayImage.height),
            dstOffset = androidx.compose.ui.unit.IntOffset(panX.toInt(), panY.toInt()),
            dstSize =
                androidx.compose.ui.unit.IntSize(
                    (image.width * scale).toInt(),
                    (image.height * scale).toInt(),
                ),
        )
    }

    // Draw bounding boxes
    for (i in 0 until boundingBoxList.size()) {
        val box = boundingBoxList.boxes[i]
        val isSelected = i == selectedBoxIndex
        val config = photoConfigurations[box.id]
        val detectionMode = config?.detectionMode
        drawBoundingBox(box, isSelected, scale, panX, panY, detectionMode)
    }

    // Draw 4-point state if active
    if (
        wizardMode == WizardMode.FOUR_POINT && fourPointState.mode != FourPointState.Mode.INACTIVE
    ) {
        drawFourPointPreview(fourPointState, scale, panX, panY)
    }
}

fun findCornerHit(
    offset: Offset,
    boxes: BoundingBoxList,
    zoom: ZoomController,
): Pair<Int, Corner>? {
    // Both offset and screenPos are in screen/pixel coordinates, so the hit radius
    // must also be in screen space (NOT multiplied by zoom). Multiplying by zoom made
    // corners nearly impossible to click when zoomed out (e.g. 30 * 0.2 = 6px).
    val hitRadius = 30f
    for (i in 0 until boxes.size()) {
        val box = boxes.boxes[i]
        val corners = box.corners
        val cornerList =
            listOf(
                Corner.TOP_LEFT to corners.topLeft,
                Corner.TOP_RIGHT to corners.topRight,
                Corner.BOTTOM_LEFT to corners.bottomLeft,
                Corner.BOTTOM_RIGHT to corners.bottomRight,
            )
        for ((corner, point) in cornerList) {
            val screenPos = zoom.imageToScreen(point.x, point.y)
            val cornerScreen = Offset(screenPos.x.toFloat(), screenPos.y.toFloat())
            if ((offset - cornerScreen).getDistance() < hitRadius) {
                return i to corner
            }
        }
    }
    return null
}

fun findBoxHit(offset: Offset, boxes: BoundingBoxList, zoom: ZoomController): Int {
    for (i in 0 until boxes.size()) {
        val box = boxes.boxes[i]
        val corners = box.corners
        val screenPoints =
            listOf(corners.topLeft, corners.topRight, corners.bottomRight, corners.bottomLeft)
                .map { p ->
                    val screen = zoom.imageToScreen(p.x, p.y)
                    Offset(screen.x.toFloat(), screen.y.toFloat())
                }

        val minX = screenPoints.minOf { it.x }
        val maxX = screenPoints.maxOf { it.x }
        val minY = screenPoints.minOf { it.y }
        val maxY = screenPoints.maxOf { it.y }

        val isXInRange = offset.x >= minX && offset.x <= maxX
        val isYInRange = offset.y >= minY && offset.y <= maxY
        if (isXInRange && isYInRange) {
            return i
        }
    }
    return -1
}

fun DrawScope.drawBoundingBox(
    box: BoundingBox,
    isSelected: Boolean,
    scale: Float,
    panX: Float,
    panY: Float,
    detectionMode: DetectionMode? = null,
) {
    val outlineColor = if (isSelected) Color(0xFF2196F3) else Color(0xFF4CAF50)
    val fillColor = outlineColor.copy(alpha = 0.2f)

    fun toScreen(p: Point) = Offset((panX + p.x * scale).toFloat(), (panY + p.y * scale).toFloat())

    val tl = toScreen(box.corners.topLeft)
    val tr = toScreen(box.corners.topRight)
    val bl = toScreen(box.corners.bottomLeft)
    val br = toScreen(box.corners.bottomRight)

    // Draw fill
    val path =
        Path().apply {
            moveTo(tl.x, tl.y)
            lineTo(tr.x, tr.y)
            lineTo(br.x, br.y)
            lineTo(bl.x, bl.y)
            close()
        }
    drawPath(path, fillColor, style = Fill)
    drawPath(path, outlineColor, style = Stroke(width = if (isSelected) 3f else 2f))

    // Draw corners
    val cornerRadius = if (isSelected) 10f else 6f
    listOf(tl, tr, bl, br).forEach { corner ->
        drawCircle(outlineColor, radius = cornerRadius, center = corner)
        drawCircle(Color.White, radius = cornerRadius - 2, center = corner)
    }

    // Draw center indicator when selected
    if (isSelected) {
        val centerX = (tl.x + tr.x + bl.x + br.x) / 4
        val centerY = (tl.y + tr.y + bl.y + br.y) / 4
        drawCircle(outlineColor.copy(alpha = 0.5f), radius = 8f, center = Offset(centerX, centerY))
    }

    // Draw detection mode badge in top-left corner of the box
    if (detectionMode != null) {
        val badgeColor =
            when (detectionMode) {
                DetectionMode.COMPUTER_VISION -> Color(0xFF4CAF50) // Green
                DetectionMode.BOUNDING_BOX -> Color(0xFF2196F3) // Blue
                DetectionMode.PERSPECTIVE_CORRECTION -> Color(0xFFFF9800) // Orange
                DetectionMode.HYBRID -> Color(0xFF9C27B0) // Purple
            }
        val badgeX = tl.x + 4f
        val badgeY = tl.y + 4f
        // Small colored dot indicating detection mode
        drawCircle(badgeColor, radius = 5f, center = Offset(badgeX + 5f, badgeY + 5f))
        drawCircle(Color.White, radius = 3f, center = Offset(badgeX + 5f, badgeY + 5f))
    }
}

fun DrawScope.drawFourPointPreview(
    fourPointState: FourPointState,
    scale: Float,
    panX: Float,
    panY: Float,
) {
    val points = fourPointState.points
    val pendingPoint = fourPointState.pendingPoint
    val accentColor = Color(0xFFFF9800)

    fun toScreen(p: Point) = Offset((panX + p.x * scale).toFloat(), (panY + p.y * scale).toFloat())

    // Draw placed points with numbers
    points.forEachIndexed { index, point ->
        val screen = toScreen(point)
        drawCircle(accentColor, radius = 15f, center = screen)
        drawCircle(Color.White, radius = 12f, center = screen)
    }

    // Draw connecting lines between placed points
    if (points.size >= 2) {
        val path = Path()
        path.moveTo(toScreen(points[0]).x, toScreen(points[0]).y)
        for (i in 1 until points.size) {
            path.lineTo(toScreen(points[i]).x, toScreen(points[i]).y)
        }
        if (points.size == 4) {
            path.lineTo(toScreen(points[0]).x, toScreen(points[0]).y)
        }
        drawPath(path, accentColor, style = Stroke(width = 2f))
    }

    // Draw preview from last placed point to mouse cursor
    if (pendingPoint != null && points.isNotEmpty()) {
        val lastPoint = points.last()
        val lastScreen = toScreen(lastPoint)
        val pendingScreen = toScreen(pendingPoint)

        if (fourPointState.isRectangle() && points.size == 1) {
            val previewPath = Path()
            previewPath.moveTo(lastScreen.x, lastScreen.y)
            previewPath.lineTo(pendingScreen.x, lastScreen.y)
            previewPath.lineTo(pendingScreen.x, pendingScreen.y)
            previewPath.lineTo(lastScreen.x, pendingScreen.y)
            previewPath.close()
            drawPath(previewPath, accentColor.copy(alpha = 0.15f), style = Fill)
            drawPath(previewPath, accentColor, style = Stroke(width = 2f))
        } else {
            val path = Path()
            path.moveTo(lastScreen.x, lastScreen.y)
            path.lineTo(pendingScreen.x, pendingScreen.y)
            drawPath(path, accentColor.copy(alpha = 0.7f), style = Stroke(width = 2f))

            if (points.size >= 2) {
                val firstScreen = toScreen(points.first())
                val closingPath = Path()
                closingPath.moveTo(pendingScreen.x, pendingScreen.y)
                closingPath.lineTo(firstScreen.x, firstScreen.y)
                drawPath(
                    closingPath,
                    accentColor.copy(alpha = 0.4f),
                    style =
                        Stroke(
                            width = 1.5f,
                            pathEffect =
                                androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                    floatArrayOf(10f, 10f)
                                ),
                        ),
                )
            }
        }
    }
}
