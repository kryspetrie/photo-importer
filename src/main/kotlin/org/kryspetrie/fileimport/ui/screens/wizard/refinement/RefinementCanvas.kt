package org.kryspetrie.fileimport.ui.screens.wizard.refinement

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import java.awt.image.BufferedImage
import kotlinx.coroutines.delay
import org.kryspetrie.fileimport.infrastructure.wizard.BoundingBox
import org.kryspetrie.fileimport.infrastructure.wizard.Corner
import org.kryspetrie.fileimport.infrastructure.wizard.DEBUG_TIMING
import org.kryspetrie.fileimport.infrastructure.wizard.PhotoScanWizardState

/**
 * High-performance refinement canvas using GraphicsLayer for zoom/pan and separate drawing layers.
 */
@Suppress("UnusedParameter")
@Composable
internal fun RefinementCanvas(
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
    val boundingBoxList by state.boundingBoxList.collectAsState()

    // Get zoom/pan values
    val zoom = zoomController.zoom.toFloat()
    val panX = zoomController.panX.toFloat()
    val panY = zoomController.panY.toFloat()

    // Throttled display state - only updates during dragging
    val displayBox by state.displayRefinementBox.collectAsState()

    // Track whether we're dragging (for 30Hz throttle)
    var isDragging by remember { mutableStateOf(false) }

    // Use displayBox during dragging (throttled preview), actual box otherwise (immediate)
    val drawBox = if (isDragging) displayBox else box

    // Cache the ImageBitmap - conversion from AWT BufferedImage is EXPENSIVE (~700ms!)
    // When zoomed in (>= 1.0), use full-resolution image; when zoomed out (< 1.0), use a
    // downsampled version for performance (matches OverviewCanvas behavior).
    val fullResBitmap by remember(image) { derivedStateOf { image?.toComposeImageBitmap() } }
    val zoomQuantized = (zoom * 10).toInt() // Quantize to 0.1 increments
    val sampledImageBitmap by
        remember(image, zoomQuantized) {
            derivedStateOf {
                if (zoom < 1.0f && image != null) {
                    val sampled = createSampledImageForRefinement(image, zoom.toDouble())
                    sampled?.toComposeImageBitmap()
                } else null
            }
        }
    val cachedImageBitmap = if (zoom < 1.0f) sampledImageBitmap else fullResBitmap

    // Precompute corner positions in screen space for the ACTIVE box (for drag hit testing)
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

    // Precompute corner positions for ALL boxes (for click-to-switch hit testing)
    val allBoxCornerPositions =
        remember(boundingBoxList, zoom, panX, panY) {
            boundingBoxList.boxes.mapIndexed { index, b ->
                index to
                    listOf(
                        Corner.TOP_LEFT to imageToScreen(b.corners.topLeft, zoom, panX, panY),
                        Corner.TOP_RIGHT to imageToScreen(b.corners.topRight, zoom, panX, panY),
                        Corner.BOTTOM_LEFT to imageToScreen(b.corners.bottomLeft, zoom, panX, panY),
                        Corner.BOTTOM_RIGHT to
                            imageToScreen(b.corners.bottomRight, zoom, panX, panY),
                    )
            }
        }

    // 30Hz ticker to sync pending drag position to display state
    LaunchedEffect(isDragging, boxIndex) {
        while (isDragging) {
            state.syncPendingDrag(boxIndex)
            delay(33L) // 30Hz = every 33ms
        }
    }

    // Request focus when canvas is displayed — handled by parent RefinementScreen

    Box(
        modifier =
            modifier
                .background(Color.DarkGray)
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
                                    // 1. Check for corner hit on the active (refinement) box
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
                                        // 2. Check for corner hit on ANY other box
                                        // (click-to-switch)
                                        var switchedBox = false
                                        for ((bIndex, corners) in allBoxCornerPositions) {
                                            if (bIndex == boxIndex) continue
                                            val cornerHit =
                                                corners.find { (_, screenPos) ->
                                                    (pos - screenPos).getDistance() < 25f
                                                }
                                            if (cornerHit != null) {
                                                // Switch to that box and select that corner
                                                state.enterRefinement(bIndex)
                                                state.selectCorner(cornerHit.first)
                                                switchedBox = true
                                                break
                                            }
                                        }
                                        // 3. Check for interior hit on any other box
                                        if (!switchedBox) {
                                            val imgX = ((pos.x - currentPanX) / zoom).toDouble()
                                            val imgY = ((pos.y - currentPanY) / zoom).toDouble()
                                            for (i in boundingBoxList.boxes.indices) {
                                                if (i == boxIndex) continue
                                                val otherBox = boundingBoxList.boxes[i]
                                                if (isPointInBox(imgX, imgY, otherBox)) {
                                                    state.enterRefinement(i)
                                                    switchedBox = true
                                                    break
                                                }
                                            }
                                        }
                                        // 4. If no hit on other boxes, start background pan
                                        if (!switchedBox) {
                                            dragging = true
                                            isCornerDrag = false
                                            lastDragX = pos.x.toDouble()
                                            lastDragY = pos.y.toDouble()
                                        }
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
                                        // Commit final position to state (immediate, not
                                        // throttled)
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
                                    val scrollDelta = event.changes.firstOrNull()?.scrollDelta
                                    if (scrollDelta != null) {
                                        if (scrollDelta.y < 0) {
                                            state.zoomIn(pos.x.toDouble(), pos.y.toDouble())
                                        } else if (scrollDelta.y > 0) {
                                            state.zoomOut(pos.x.toDouble(), pos.y.toDouble())
                                        }
                                    }
                                }
                                else -> {}
                            }
                        }
                    }
                }
    ) {
        RefinementCanvasContent(
            image = image,
            cachedImageBitmap = cachedImageBitmap,
            useSampledBitmap = zoom < 1.0f,
            drawBox = drawBox,
            otherBoxes =
                boundingBoxList.boxes.mapIndexedNotNull { i, b ->
                    if (i != boxIndex) i to b else null
                },
            selectedCorner = selectedCorner,
            zoom = zoom,
            panX = panX,
            panY = panY,
        )
    }
}

@Composable
private fun RefinementCanvasContent(
    image: BufferedImage?,
    cachedImageBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    useSampledBitmap: Boolean,
    drawBox: BoundingBox?,
    otherBoxes: List<Pair<Int, BoundingBox>>,
    selectedCorner: Corner?,
    zoom: Float,
    panX: Float,
    panY: Float,
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
            // frame. When zoomed out, use a downsampled bitmap for performance; when zoomed in,
            // use the full-resolution bitmap for quality.
            if (image != null && cachedImageBitmap != null) {
                val drawStart = if (DEBUG_TIMING) System.nanoTime() else 0L
                val bitmap = cachedImageBitmap
                // When using a sampled bitmap, srcSize must match the bitmap's actual dimensions.
                // dstSize is always (image.width*scale) x (image.height*scale) so that the image
                // aligns exactly with the bounding box coordinates which use the same scale factor.
                val srcW = if (useSampledBitmap) bitmap.width else image.width
                val srcH = if (useSampledBitmap) bitmap.height else image.height
                drawImage(
                    bitmap,
                    srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
                    srcSize = androidx.compose.ui.unit.IntSize(srcW, srcH),
                    dstOffset = androidx.compose.ui.unit.IntOffset.Zero,
                    dstSize =
                        androidx.compose.ui.unit.IntSize(
                            (imgW * zoom).toInt(),
                            (imgH * zoom).toInt(),
                        ),
                )
                val drawElapsed = if (DEBUG_TIMING) (System.nanoTime() - drawStart) / 1000 else 0L
                if (DEBUG_TIMING && drawElapsed > 100) {
                    println("  📷 Image draw: ${drawElapsed}μs (cached, no conversion)")
                }
            }

            // Draw other bounding boxes as faint outlines (clickable to switch)
            for ((_, otherBox) in otherBoxes) {
                drawOtherBoxOutline(otherBox, zoom)
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
