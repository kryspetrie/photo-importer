package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.awt.image.BufferedImage
import kotlinx.coroutines.delay
import org.kryspetrie.fileimport.infrastructure.wizard.BoundingBox
import org.kryspetrie.fileimport.infrastructure.wizard.Corner
import org.kryspetrie.fileimport.infrastructure.wizard.DEBUG_TIMING
import org.kryspetrie.fileimport.infrastructure.wizard.PhotoScanWizardState
import org.kryspetrie.fileimport.infrastructure.wizard.Point

/**
 * Refinement screen showing a zoomed view of a single bounding box for precise corner adjustment.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefinementScreen(
    state: PhotoScanWizardState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // State flows
    val image by state.image.collectAsState()
    val refinementBoxIndex by state.refinementBoxIndex.collectAsState()
    val selectedCorner by state.selectedCorner.collectAsState()
    val zoomController by state.zoomController.collectAsState()
    val boundingBoxList by state.boundingBoxList.collectAsState()
    val boxCount by remember { derivedStateOf { boundingBoxList.size() } }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // Get current box
    val currentBox =
        remember(refinementBoxIndex, boundingBoxList) {
            if (refinementBoxIndex >= 0 && refinementBoxIndex < boundingBoxList.size()) {
                boundingBoxList.boxes[refinementBoxIndex]
            } else null
        }

    // Fit to box when canvas size is known
    LaunchedEffect(canvasSize, refinementBoxIndex) {
        if (canvasSize.width > 0 && canvasSize.height > 0) {
            state.fitToBox(canvasSize.width.toDouble(), canvasSize.height.toDouble())
            state.syncDisplayBox()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Refine Bounding Box") },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            state.exitRefinement()
                            onBack()
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to Overview")
                    }
                },
                actions = {
                    IconButton(onClick = { state.undo() }) { Icon(Icons.Default.Undo, "Undo") }
                    IconButton(onClick = { state.redo() }) { Icon(Icons.Default.Redo, "Redo") }
                    IconButton(
                        onClick = {
                            if (refinementBoxIndex >= 0) {
                                state.removeBox(refinementBoxIndex)
                                state.exitRefinement()
                                onBack()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                },
            )
        },
        content = { paddingValues ->
            Column(
                modifier = modifier.fillMaxSize().padding(paddingValues),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Main canvas area
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    RefinementCanvas(
                        state = state,
                        image = image,
                        box = currentBox,
                        boxIndex = refinementBoxIndex,
                        canvasSize = canvasSize,
                        onCanvasSizeChanged = { canvasSize = it },
                        modifier = Modifier.fillMaxSize(),
                    )

                    // Zoom controls (top-right corner)
                    ZoomControls(
                        zoomController = zoomController,
                        onZoomIn = { state.zoomIn() },
                        onZoomOut = { state.zoomOut() },
                        onFitToView = {
                            if (canvasSize.width > 0) {
                                state.fitToBox(
                                    canvasSize.width.toDouble(),
                                    canvasSize.height.toDouble(),
                                )
                            }
                        },
                        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                    )
                }

                // Bottom controls
                RefinementControls(
                    currentIndex = refinementBoxIndex,
                    totalCount = boxCount,
                    zoomLevel = zoomController.zoom,
                    selectedCorner = selectedCorner,
                    onPrevious = { state.previousBox() },
                    onNext = { state.nextBox() },
                    onDeselect = { state.deselectCorner() },
                    onZoomIn = { state.zoomIn() },
                    onZoomOut = { state.zoomOut() },
                    onFitToView = {
                        if (canvasSize.width > 0) {
                            state.fitToBox(
                                canvasSize.width.toDouble(),
                                canvasSize.height.toDouble(),
                            )
                        }
                    },
                    onRotateLeft = {
                        if (refinementBoxIndex >= 0) state.rotateBox(refinementBoxIndex, -5.0)
                    },
                    onRotateRight = {
                        if (refinementBoxIndex >= 0) state.rotateBox(refinementBoxIndex, 5.0)
                    },
                    onExpand = {
                        if (refinementBoxIndex >= 0) state.expandBox(refinementBoxIndex, 1.05)
                    },
                    onContract = {
                        if (refinementBoxIndex >= 0) state.expandBox(refinementBoxIndex, 0.95)
                    },
                )
            }
        },
    )
}

@Composable
private fun RefinementControls(
    currentIndex: Int,
    totalCount: Int,
    zoomLevel: Double,
    selectedCorner: Corner?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDeselect: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onFitToView: () -> Unit,
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit,
    onExpand: () -> Unit,
    onContract: () -> Unit,
) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Navigation row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onPrevious,
                        enabled = currentIndex > 0,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous", Modifier.size(18.dp))
                    }
                    Text(
                        "Image ${currentIndex + 1} of $totalCount",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    IconButton(
                        onClick = onNext,
                        enabled = currentIndex < totalCount - 1,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next", Modifier.size(18.dp))
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selectedCorner != null) {
                        Text(
                            "Selected: ${selectedCorner.name}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        OutlinedButton(onClick = onDeselect, modifier = Modifier.height(32.dp)) {
                            Text("Deselect", style = MaterialTheme.typography.labelSmall)
                        }
                    } else {
                        Text(
                            "Click a corner to select",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Controls row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Zoom
                OutlinedButton(onClick = onZoomOut, modifier = Modifier.height(32.dp)) {
                    Icon(Icons.Default.ZoomOut, null, Modifier.size(16.dp))
                }
                Text(
                    "${(zoomLevel * 100).toInt()}%",
                    modifier = Modifier.width(44.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                )
                OutlinedButton(onClick = onZoomIn, modifier = Modifier.height(32.dp)) {
                    Icon(Icons.Default.ZoomIn, null, Modifier.size(16.dp))
                }
                OutlinedButton(onClick = onFitToView, modifier = Modifier.height(32.dp)) {
                    Icon(Icons.Default.FitScreen, null, Modifier.size(16.dp))
                }

                VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))

                // Rotate
                OutlinedButton(onClick = onRotateLeft, modifier = Modifier.height(32.dp)) {
                    Icon(Icons.Default.RotateRight, null, Modifier.size(16.dp))
                    Text("-5°", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(onClick = onRotateRight, modifier = Modifier.height(32.dp)) {
                    Text("+5°", style = MaterialTheme.typography.labelSmall)
                    Icon(Icons.Default.RotateLeft, null, Modifier.size(16.dp))
                }

                VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))

                // Size
                OutlinedButton(onClick = onContract, modifier = Modifier.height(32.dp)) {
                    Icon(Icons.Default.Remove, null, Modifier.size(16.dp))
                }
                OutlinedButton(onClick = onExpand, modifier = Modifier.height(32.dp)) {
                    Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                }

                Spacer(Modifier.weight(1f))

                // Help text
                Text(
                    "Drag corners to edit • Drag background to pan",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * High-performance refinement canvas using GraphicsLayer for zoom/pan and separate drawing layers.
 */
@Suppress("UnusedParameter")
@Composable
private fun RefinementCanvas(
    state: PhotoScanWizardState,
    image: BufferedImage?,
    box: BoundingBox?,
    boxIndex: Int,
    canvasSize: IntSize,
    onCanvasSizeChanged: (IntSize) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zoomController by state.zoomController.collectAsState()
    val selectedCorner by state.selectedCorner.collectAsState()

    // Get zoom/pan values
    val zoom = zoomController.zoom.toFloat()
    val panX = zoomController.panX.toFloat()
    val panY = zoomController.panY.toFloat()

    // Focus requester for auto-focus on load (so keyboard works immediately)
    val focusRequester = remember { FocusRequester() }

    // Throttled display state - only updates during dragging
    val displayBox by state.displayRefinementBox.collectAsState()

    // Track whether we're dragging (for 30Hz throttle)
    var isDragging by remember { mutableStateOf(false) }

    // Use displayBox during dragging (throttled preview), actual box otherwise (immediate)
    val drawBox = if (isDragging) displayBox else box

    // Cache the ImageBitmap - conversion from AWT BufferedImage is EXPENSIVE (~700ms!)
    val cachedImageBitmap by remember(image) { derivedStateOf { image?.toComposeImageBitmap() } }

    // Precompute corner positions in screen space (for hit testing)
    val cornerPositions =
        remember(box, zoom, panX, panY) {
            if (box != null) {
                listOf(
                    Corner.TOP_LEFT to imageToScreen(box.corners.topLeft, zoom, panX, panY),
                    Corner.TOP_RIGHT to imageToScreen(box.corners.topRight, zoom, panX, panY),
                    Corner.BOTTOM_LEFT to imageToScreen(box.corners.bottomLeft, zoom, panX, panY),
                    Corner.BOTTOM_RIGHT to imageToScreen(box.corners.bottomRight, zoom, panX, panY),
                )
            } else emptyList()
        }

    // 30Hz ticker to sync pending drag position to display state
    LaunchedEffect(isDragging, boxIndex) {
        while (isDragging) {
            state.syncPendingDrag(boxIndex)
            delay(33L) // 30Hz = every 33ms
        }
    }

    // Request focus when canvas is displayed (so keyboard works without clicking first)
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier =
            modifier
                .background(Color.DarkGray)
                .focusRequester(focusRequester) // For auto-focus
                .focusable() // Required for keyboard events to work
                .onSizeChanged { onCanvasSizeChanged(it) }
                .pointerInput(state, box, zoom) {
                    // Pointer handler for corner drag and background pan
                    // Does NOT depend on panX/panY to avoid restart during drag
                    awaitPointerEventScope {
                        var dragging = false
                        var isCornerDrag = false
                        var lastDragX = 0.0
                        var lastDragY = 0.0

                        while (true) {
                            val event = awaitPointerEvent()
                            val pos = event.changes.firstOrNull()?.position ?: continue
                            // Read current pan values inside loop to avoid handler restart
                            val currentPanX = zoomController.panX.toFloat()
                            val currentPanY = zoomController.panY.toFloat()

                            when (event.type) {
                                PointerEventType.Press -> {
                                    // Check for corner hit
                                    val hit =
                                        cornerPositions.find { (_, screenPos) ->
                                            (pos - screenPos).getDistance() < 25f
                                        }
                                    if (hit != null) {
                                        state.selectCorner(hit.first)
                                        isCornerDrag = true
                                        dragging = true
                                        isDragging = true
                                        // Initialize pending position
                                        lastDragX = ((pos.x - currentPanX) / zoom).toDouble()
                                        lastDragY = ((pos.y - currentPanY) / zoom).toDouble()
                                        state.updatePendingDrag(lastDragX, lastDragY)
                                    } else {
                                        // Background drag - start panning
                                        dragging = true
                                        isCornerDrag = false
                                        lastDragX = pos.x.toDouble()
                                        lastDragY = pos.y.toDouble()
                                    }
                                }
                                PointerEventType.Move -> {
                                    if (dragging && isCornerDrag) {
                                        // Corner drag
                                        val imgX = ((pos.x - currentPanX) / zoom).toDouble()
                                        val imgY = ((pos.y - currentPanY) / zoom).toDouble()
                                        // Skip if position hasn't changed meaningfully (reduces
                                        // processing)
                                        val dx = imgX - lastDragX
                                        val dy = imgY - lastDragY
                                        if (
                                            dx * dx + dy * dy > 0.25
                                        ) { // ~0.5 pixel threshold squared
                                            val updateStart =
                                                if (DEBUG_TIMING) System.nanoTime() else 0L
                                            state.updatePendingDrag(imgX, imgY)
                                            if (DEBUG_TIMING) {
                                                val elapsed =
                                                    (System.nanoTime() - updateStart) / 1000
                                                if (elapsed > 100) {
                                                    println("  📝 updatePendingDrag: ${elapsed}μs")
                                                }
                                            }
                                            lastDragX = imgX
                                            lastDragY = imgY
                                        }
                                    } else if (dragging && !isCornerDrag) {
                                        // Background pan
                                        val dx = (pos.x.toDouble() - lastDragX)
                                        val dy = (pos.y.toDouble() - lastDragY)
                                        state.pan(dx, dy)
                                        lastDragX = pos.x.toDouble()
                                        lastDragY = pos.y.toDouble()
                                    }
                                }
                                PointerEventType.Release -> {
                                    if (dragging && isCornerDrag) {
                                        // Commit final position to state (immediate, not throttled)
                                        val corner = state.selectedCorner.value
                                        if (corner != null) {
                                            state.moveCornerWithValidation(
                                                boxIndex,
                                                corner,
                                                state.pendingDragX,
                                                state.pendingDragY,
                                            )
                                            state.syncDisplayBox()
                                        }
                                    }
                                    isDragging = false
                                    dragging = false
                                    isCornerDrag = false
                                }
                                PointerEventType.Scroll -> {
                                    // Zoom with scroll wheel when no corner is selected
                                    if (selectedCorner == null && !dragging) {
                                        val scrollDelta = event.changes.firstOrNull()?.scrollDelta
                                        if (scrollDelta != null) {
                                            if (scrollDelta.y < 0) {
                                                state.zoomIn()
                                            } else if (scrollDelta.y > 0) {
                                                state.zoomOut()
                                            }
                                        }
                                    }
                                }
                                else -> {}
                            }
                        }
                    }
                }
                .onKeyEvent { event ->
                    // Arrow key handling
                    if (event.type == KeyEventType.KeyDown) {
                        val delta = 10.0
                        val panDelta = 50.0

                        when (event.key) {
                            Key.DirectionUp -> {
                                if (selectedCorner != null) {
                                    state.moveSelectedCorner(0.0, -delta)
                                } else {
                                    state.pan(0.0, panDelta)
                                }
                                true
                            }
                            Key.DirectionDown -> {
                                if (selectedCorner != null) {
                                    state.moveSelectedCorner(0.0, delta)
                                } else {
                                    state.pan(0.0, -panDelta)
                                }
                                true
                            }
                            Key.DirectionLeft -> {
                                if (selectedCorner != null) {
                                    state.moveSelectedCorner(-delta, 0.0)
                                } else {
                                    state.pan(panDelta, 0.0)
                                }
                                true
                            }
                            Key.DirectionRight -> {
                                if (selectedCorner != null) {
                                    state.moveSelectedCorner(delta, 0.0)
                                } else {
                                    state.pan(-panDelta, 0.0)
                                }
                                true
                            }
                            else -> false
                        }
                    } else false
                }
    ) {
        // Draw the content using Canvas with translate for pan
        Canvas(modifier = Modifier.fillMaxSize()) {
            val totalStart = if (DEBUG_TIMING) System.nanoTime() else 0L

            // Apply pan offset via translate
            translate(left = panX, top = panY) {
                // Draw background
                val imgW = (image?.width ?: 800).toFloat()
                val imgH = (image?.height ?: 600).toFloat()
                drawRect(Color.DarkGray, Offset.Zero, Size(imgW * zoom, imgH * zoom))

                // Draw image (scaled by zoom) - use cached bitmap to avoid 700ms conversion every
                // frame
                if (image != null) {
                    val drawStart = if (DEBUG_TIMING) System.nanoTime() else 0L
                    @Suppress("UnnecessaryNotNullOperator")
                    val bitmap = cachedImageBitmap!!
                    drawImage(
                        bitmap,
                        srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
                        srcSize = androidx.compose.ui.unit.IntSize(image.width, image.height),
                        dstOffset = androidx.compose.ui.unit.IntOffset.Zero,
                        dstSize =
                            androidx.compose.ui.unit.IntSize(
                                (imgW * zoom).toInt(),
                                (imgH * zoom).toInt(),
                            ),
                    )
                    val drawElapsed =
                        if (DEBUG_TIMING) (System.nanoTime() - drawStart) / 1000 else 0L
                    if (DEBUG_TIMING && drawElapsed > 100) {
                        println("  📷 Image draw: ${drawElapsed}μs (cached, no conversion)")
                    }
                }

                // Draw bounding box (use displayBox during drag for throttled preview, actualBox
                // otherwise)
                if (drawBox != null) {
                    val boxStart = if (DEBUG_TIMING) System.nanoTime() else 0L
                    drawRefinementBox(drawBox, selectedCorner, zoom)
                    val boxElapsed = if (DEBUG_TIMING) (System.nanoTime() - boxStart) / 1000 else 0L
                    if (DEBUG_TIMING && boxElapsed > 100) {
                        println("  📦 Box: ${boxElapsed}μs")
                    }
                }
            }

            val totalElapsed = if (DEBUG_TIMING) (System.nanoTime() - totalStart) / 1000 else 0L
            if (DEBUG_TIMING && totalElapsed > 500) {
                println("⏱️ Canvas TOTAL: ${totalElapsed}μs")
            }
        }
    }
}

internal fun imageToScreen(point: Point, zoom: Float, panX: Float, panY: Float): Offset {
    return Offset((panX + point.x * zoom).toFloat(), (panY + point.y * zoom).toFloat())
}

internal fun screenToImage(screen: Offset, zoom: Float, panX: Float, panY: Float): Point {
    return Point(((screen.x - panX) / zoom).toDouble(), ((screen.y - panY) / zoom).toDouble())
}

private fun DrawScope.drawRefinementBox(box: BoundingBox, selected: Corner?, zoom: Float) {
    fun toScreen(p: Point) = imageToScreen(p, zoom, 0f, 0f) // Pan is handled by translate()

    val tl = toScreen(box.corners.topLeft)
    val tr = toScreen(box.corners.topRight)
    val bl = toScreen(box.corners.bottomLeft)
    val br = toScreen(box.corners.bottomRight)

    // Fill
    val path =
        Path().apply {
            moveTo(tl.x, tl.y)
            lineTo(tr.x, tr.y)
            lineTo(br.x, br.y)
            lineTo(bl.x, bl.y)
            close()
        }
    drawPath(path, Color(0xFF2196F3).copy(alpha = 0.15f), style = Fill)
    drawPath(path, Color(0xFF2196F3), style = Stroke(width = 2f))

    // Center cross
    val cx = (tl.x + tr.x + bl.x + br.x) / 4
    val cy = (tl.y + tr.y + bl.y + br.y) / 4
    drawLine(Color(0xFF2196F3).copy(alpha = 0.5f), Offset(cx - 15f, cy), Offset(cx + 15f, cy), 1f)
    drawLine(Color(0xFF2196F3).copy(alpha = 0.5f), Offset(cx, cy - 15f), Offset(cx, cy + 15f), 1f)

    // Corner handles
    val handleRadius = 12f
    listOf(
            Corner.TOP_LEFT to tl,
            Corner.TOP_RIGHT to tr,
            Corner.BOTTOM_LEFT to bl,
            Corner.BOTTOM_RIGHT to br,
        )
        .forEach { (corner, pos) ->
            val r = if (corner == selected) handleRadius * 1.3f else handleRadius
            drawCircle(Color(0xFF2196F3), r, pos)
            drawCircle(Color.White, r - 3f, pos)
        }
}

internal fun findCornerHit(
    offset: Offset,
    box: BoundingBox,
    zoom: Float,
    panX: Float,
    panY: Float,
): Corner? {
    val hitRadius = 25f
    val corners =
        listOf(
            Corner.TOP_LEFT to box.corners.topLeft,
            Corner.TOP_RIGHT to box.corners.topRight,
            Corner.BOTTOM_LEFT to box.corners.bottomLeft,
            Corner.BOTTOM_RIGHT to box.corners.bottomRight,
        )

    for ((corner, point) in corners) {
        val screenPos = Offset((panX + point.x * zoom).toFloat(), (panY + point.y * zoom).toFloat())
        if ((offset - screenPos).getDistance() < hitRadius) {
            return corner
        }
    }
    return null
}

internal fun createSampledImageForRefinement(
    image: BufferedImage,
    scale: Double,
    targetWidth: Int = (image.width * scale).toInt(),
    targetHeight: Int = (image.height * scale).toInt(),
): BufferedImage? {
    return try {
        val w = targetWidth.coerceIn(100, 4000)
        val h = targetHeight.coerceIn(100, 4000)
        if (w <= 0 || h <= 0) return null
        val result = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = result.createGraphics()
        g.drawImage(image, 0, 0, w, h, null)
        g.dispose()
        result
    } catch (_: Exception) {
        null
    }
}
