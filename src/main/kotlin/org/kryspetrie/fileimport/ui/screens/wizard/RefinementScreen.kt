package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.awt.image.BufferedImage
import org.kryspetrie.fileimport.infrastructure.wizard.*

/**
 * Refinement screen showing a zoomed view of a single bounding box for precise corner adjustment.
 * Features include corner dragging, arrow key movement, scroll expand/rotate, and undo/redo.
 *
 * Performance optimized with:
 * - Pre-calculated display parameters
 * - Efficient drag handling
 * - Tolerance-based corner selection
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefinementScreen(
    state: PhotoScanWizardState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
  // Collect state flows
  val image by state.image.collectAsState()
  val refinementBoxIndex by state.refinementBoxIndex.collectAsState()
  val selectedCorner by state.selectedCorner.collectAsState()
  val zoomController by state.zoomController.collectAsState()
  val boundingBoxList by state.boundingBoxList.collectAsState()
  val boxCount by remember { derivedStateOf { boundingBoxList.size() } }

  var containerSize by remember { mutableStateOf(IntSize.Zero) }
  var showHelpDialog by remember { mutableStateOf(false) }

  // Get current box
  val currentBox =
      remember(refinementBoxIndex, boundingBoxList) {
        if (refinementBoxIndex >= 0 && refinementBoxIndex < boundingBoxList.size()) {
          boundingBoxList.boxes[refinementBoxIndex]
        } else null
      }

  Scaffold(
      topBar = {
        TopAppBar(
            title = { Text("Refining Box ${refinementBoxIndex + 1} of $boxCount") },
            navigationIcon = {
              IconButton(
                  onClick = {
                    state.exitRefinement()
                    onBack()
                  }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to Overview")
                  }
            },
            actions = {
              // Previous box button
              IconButton(onClick = { state.previousBox() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous box")
              }

              // Next box button
              IconButton(onClick = { state.nextBox() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next box")
              }

              Spacer(Modifier.weight(1f))

              // Undo button
              @Suppress("DEPRECATION") val canUndo = state.canUndo()
              IconButton(onClick = { state.undo() }, enabled = canUndo) {
                Icon(
                    Icons.Default.Undo,
                    "Undo",
                    tint =
                        if (canUndo) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
              }

              // Redo button
              @Suppress("DEPRECATION") val canRedo = state.canRedo()
              IconButton(onClick = { state.redo() }, enabled = canRedo) {
                Icon(
                    Icons.Default.Redo,
                    "Redo",
                    tint =
                        if (canRedo) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
              }

              // Help button
              IconButton(onClick = { showHelpDialog = true }) { Icon(Icons.Default.Info, "Help") }

              // Delete button
              IconButton(
                  onClick = {
                    state.removeBox(refinementBoxIndex)
                    state.exitRefinement()
                    onBack()
                  }) {
                    Icon(
                        Icons.Default.Delete,
                        "Delete this box",
                        tint = MaterialTheme.colorScheme.error)
                  }
            })
      },
      content = { paddingValues ->
        Column(modifier = modifier.fillMaxSize().padding(paddingValues)) {
          // Toolbar
          RefinementToolbar(
              currentBox = currentBox,
              selectedCorner = selectedCorner,
              zoomLevel = zoomController.zoom,
              onZoomIn = { state.zoomIn() },
              onZoomOut = { state.zoomOut() },
              onFitToBox = {
                val w = containerSize.width.toDouble()
                val h = containerSize.height.toDouble()
                if (w > 0 && h > 0) state.fitToBox(w, h)
              },
              onRotateLeft = {
                if (refinementBoxIndex >= 0) {
                  state.rotateBox(refinementBoxIndex, -5.0)
                }
              },
              onRotateRight = {
                if (refinementBoxIndex >= 0) {
                  state.rotateBox(refinementBoxIndex, 5.0)
                }
              },
              onExpand = {
                if (refinementBoxIndex >= 0) {
                  state.expandBox(refinementBoxIndex, 1.05)
                }
              },
              onContract = {
                if (refinementBoxIndex >= 0) {
                  state.expandBox(refinementBoxIndex, 0.95)
                }
              })

          // Image canvas area
          Box(
              modifier =
                  Modifier.weight(1f)
                      .fillMaxWidth()
                      .background(Color.DarkGray)
                      .onSizeChanged { containerSize = it }
                      .withWizardKeyboardShortcuts(
                          wizardState = state,
                          onProceed = { /* In refinement, Enter could save and exit */ },
                          onCancel = onBack)) {
                if (image != null && currentBox != null && containerSize.width > 0) {
                  RefinementCanvas(
                      state = state,
                      image = image!!,
                      box = currentBox,
                      boxIndex = refinementBoxIndex,
                      containerSize = containerSize)
                } else {
                  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                    Text(
                        "Loading...", color = Color.White, modifier = Modifier.padding(top = 60.dp))
                  }
                }

                // Zoom controls (top-right)
                ZoomControls(
                    zoomController = zoomController,
                    onZoomIn = { state.zoomIn() },
                    onZoomOut = { state.zoomOut() },
                    onFitToView = {
                      val w = containerSize.width.toDouble()
                      val h = containerSize.height.toDouble()
                      if (w > 0 && h > 0) state.fitToBox(w, h)
                    },
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))

                // Help tooltip
                Surface(
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.75f)) {
                      Row(
                          modifier = Modifier.padding(8.dp),
                          horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                "Drag corners to adjust  |  Scroll to expand  |  Shift+scroll to rotate",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White)
                            Text(
                                "Arrows: 1px  |  Shift+Arrows: 10px",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.8f))
                          }
                    }
              }

          // Selected corner info
          if (selectedCorner != null) {
            Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
              Row(
                  modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Selected: ${selectedCorner?.name}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Text(
                        "Use arrow keys to move (1px per press)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
            }
          }
        }
      })

  // Help dialog
  if (showHelpDialog) {
    KeyboardShortcutHelpDialog(onDismiss = { showHelpDialog = false })
  }
}

@Composable
private fun RefinementToolbar(
    currentBox: BoundingBox?,
    selectedCorner: Corner?,
    zoomLevel: Double,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onFitToBox: () -> Unit,
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit,
    onExpand: () -> Unit,
    onContract: () -> Unit
) {
  Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically) {
          // Zoom controls
          OutlinedButton(onClick = onZoomOut, modifier = Modifier.height(32.dp)) {
            Icon(Icons.Default.ZoomOut, null, Modifier.size(16.dp))
          }
          Text(
              "${(zoomLevel * 100).toInt()}%",
              style = MaterialTheme.typography.labelMedium,
              modifier = Modifier.width(48.dp),
              textAlign = TextAlign.Center)
          OutlinedButton(onClick = onZoomIn, modifier = Modifier.height(32.dp)) {
            Icon(Icons.Default.ZoomIn, null, Modifier.size(16.dp))
          }
          OutlinedButton(onClick = onFitToBox, modifier = Modifier.height(32.dp)) {
            Icon(Icons.Default.FitScreen, null, Modifier.size(16.dp))
          }

          VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))

          // Transform controls
          OutlinedButton(onClick = onRotateLeft, modifier = Modifier.height(32.dp)) {
            Icon(Icons.Default.RotateRight, null, Modifier.size(16.dp))
            Spacer(Modifier.width(2.dp))
            Text("-5°", style = MaterialTheme.typography.labelSmall)
          }
          OutlinedButton(onClick = onRotateRight, modifier = Modifier.height(32.dp)) {
            Text("+5°", style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(2.dp))
            Icon(Icons.Default.RotateLeft, null, Modifier.size(16.dp))
          }

          VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))

          // Size controls
          OutlinedButton(onClick = onContract, modifier = Modifier.height(32.dp)) {
            Icon(Icons.Default.ZoomOut, null, Modifier.size(16.dp))
            Spacer(Modifier.width(2.dp))
            Text("Smaller", style = MaterialTheme.typography.labelSmall)
          }
          OutlinedButton(onClick = onExpand, modifier = Modifier.height(32.dp)) {
            Text("Larger", style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(2.dp))
            Icon(Icons.Default.ZoomIn, null, Modifier.size(16.dp))
          }

          Spacer(Modifier.weight(1f))

          // Box dimensions
          if (currentBox != null) {
            Text(
                "${currentBox.width().toInt()} x ${currentBox.height().toInt()} px",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
  }
}

/**
 * High-performance refinement canvas with optimized drag handling.
 *
 * Features:
 * - Zoom/pan support for precise adjustment
 * - Corner drag with tolerance
 * - Box drag to move entire bounding box
 * - Tap to select corner
 */
@Composable
private fun RefinementCanvas(
    state: PhotoScanWizardState,
    image: BufferedImage,
    box: BoundingBox,
    boxIndex: Int,
    containerSize: IntSize
) {
  val zoomController by state.zoomController.collectAsState()
  val selectedCorner by state.selectedCorner.collectAsState()

  // Pre-calculate display parameters
  val scale = remember(zoomController) { zoomController.zoom.toFloat() }
  val panX = remember(zoomController) { zoomController.panX.toFloat() }
  val panY = remember(zoomController) { zoomController.panY.toFloat() }

  // Drag state - mutable for performance
  var draggedCorner by remember { mutableStateOf<Corner?>(null) }
  var isDraggingBox by remember { mutableStateOf(false) }
  var lastDragPos by remember { mutableStateOf(Offset.Zero) }

  // Pre-render sampled image
  val sampledImage =
      remember(image, scale) { createSampledImageRefinement(image, scale.toDouble()) }

  // Calculate display params for hit testing
  val displayParams = remember(panX, panY, scale) { RefinementDisplayParams(scale, panX, panY) }

  Canvas(
      modifier =
          Modifier.fillMaxSize()
              .pointerInput(state, box) {
                detectTapGestures(
                    onTap = { offset: Offset ->
                      // Check for corner hit
                      val cornerHit = findCornerHit(offset, box, displayParams)
                      if (cornerHit != null) {
                        state.selectCorner(cornerHit)
                      }
                    })
              }
              .pointerInput(state, box, selectedCorner) {
                detectDragGestures(
                    onDragStart = { offset: Offset ->
                      lastDragPos = offset

                      // Check for corner hit first
                      val cornerHit = findCornerHit(offset, box, displayParams)
                      if (cornerHit != null) {
                        state.selectCorner(cornerHit)
                        draggedCorner = cornerHit
                      } else if (isInsideBox(offset, box, displayParams)) {
                        // Check if inside box (for box drag)
                        isDraggingBox = true
                      }
                    },
                    onDrag = { change: androidx.compose.ui.input.pointer.PointerInputChange, _ ->
                      if (draggedCorner != null) {
                        // Corner dragging - move to new position
                        val pos = screenToImage(change.position, displayParams)
                        state.moveCorner(boxIndex, draggedCorner!!, pos.x, pos.y)
                      } else if (isDraggingBox) {
                        // Box dragging - move entire box
                        val deltaX =
                            ((change.position.x - lastDragPos.x) / displayParams.scale).toDouble()
                        val deltaY =
                            ((change.position.y - lastDragPos.y) / displayParams.scale).toDouble()
                        state.moveSelectedBox(deltaX, deltaY)
                        lastDragPos = change.position
                      }
                    },
                    onDragEnd = {
                      draggedCorner = null
                      isDraggingBox = false
                    })
              }) {
        // Draw background
        drawRect(
            color = Color.DarkGray,
            topLeft = Offset(displayParams.offsetX, displayParams.offsetY),
            size =
                androidx.compose.ui.geometry.Size(
                    image.width * displayParams.scale, image.height * displayParams.scale))

        // Draw image
        if (sampledImage != null) {
          drawImage(
              sampledImage.toComposeImageBitmap(),
              topLeft = Offset(displayParams.offsetX, displayParams.offsetY))
        }

        // Draw the bounding box
        drawRefinementBox(box, selectedCorner, displayParams)

        // Draw guides/grid
        drawGuides(box, displayParams)
      }
}

/** Display parameters for refinement canvas. */
private data class RefinementDisplayParams(val scale: Float, val offsetX: Float, val offsetY: Float)

/** Convert screen coordinates to image coordinates. */
private fun screenToImage(screenPos: Offset, params: RefinementDisplayParams): Point {
  return Point(
      ((screenPos.x - params.offsetX) / params.scale).toDouble(),
      ((screenPos.y - params.offsetY) / params.scale).toDouble())
}

/** Find corner hit with tolerance. */
private fun findCornerHit(
    offset: Offset,
    box: BoundingBox,
    params: RefinementDisplayParams
): Corner? {
  // Hit radius in screen pixels
  val hitRadius = 25f

  val corners =
      listOf(
          Corner.TOP_LEFT to box.corners.topLeft,
          Corner.TOP_RIGHT to box.corners.topRight,
          Corner.BOTTOM_LEFT to box.corners.bottomLeft,
          Corner.BOTTOM_RIGHT to box.corners.bottomRight)

  for ((corner, point) in corners) {
    val screenPos = imageToScreen(point, params)
    val distance = (offset - screenPos).getDistance()
    if (distance < hitRadius) {
      return corner
    }
  }
  return null
}

/** Check if point is inside the box. */
private fun isInsideBox(
    offset: Offset,
    box: BoundingBox,
    params: RefinementDisplayParams
): Boolean {
  val corners =
      listOf(
          imageToScreen(box.corners.topLeft, params),
          imageToScreen(box.corners.topRight, params),
          imageToScreen(box.corners.bottomRight, params),
          imageToScreen(box.corners.bottomLeft, params))

  val minX = corners.minOf { it.x }
  val maxX = corners.maxOf { it.x }
  val minY = corners.minOf { it.y }
  val maxY = corners.maxOf { it.y }

  return offset.x >= minX && offset.x <= maxX && offset.y >= minY && offset.y <= maxY
}

/** Convert image coordinates to screen coordinates. */
private fun imageToScreen(point: Point, params: RefinementDisplayParams): Offset {
  return Offset(
      (params.offsetX + point.x * params.scale).toFloat(),
      (params.offsetY + point.y * params.scale).toFloat())
}

private fun DrawScope.drawRefinementBox(
    box: BoundingBox,
    selectedCorner: Corner?,
    params: RefinementDisplayParams
) {
  fun toScreen(p: Point) = imageToScreen(p, params)

  val tl = toScreen(box.corners.topLeft)
  val tr = toScreen(box.corners.topRight)
  val bl = toScreen(box.corners.bottomLeft)
  val br = toScreen(box.corners.bottomRight)

  // Draw fill
  val fillColor = Color(0xFF2196F3).copy(alpha = 0.15f)
  val path =
      Path().apply {
        moveTo(tl.x, tl.y)
        lineTo(tr.x, tr.y)
        lineTo(br.x, br.y)
        lineTo(bl.x, bl.y)
        close()
      }
  drawPath(path, fillColor, style = Fill)

  // Draw outline
  val outlineColor = Color(0xFF2196F3)
  drawPath(path, outlineColor, style = Stroke(width = 2f))

  // Draw center cross
  val centerX = (tl.x + tr.x + bl.x + br.x) / 4
  val centerY = (tl.y + tr.y + bl.y + br.y) / 4
  drawLine(
      outlineColor.copy(alpha = 0.5f),
      Offset(centerX - 15f, centerY),
      Offset(centerX + 15f, centerY),
      strokeWidth = 1f)
  drawLine(
      outlineColor.copy(alpha = 0.5f),
      Offset(centerX, centerY - 15f),
      Offset(centerX, centerY + 15f),
      strokeWidth = 1f)

  // Draw corner handles
  val corners =
      listOf(
          Corner.TOP_LEFT to tl,
          Corner.TOP_RIGHT to tr,
          Corner.BOTTOM_LEFT to bl,
          Corner.BOTTOM_RIGHT to br)

  corners.forEach { (corner, position) ->
    val isSelected = corner == selectedCorner
    val baseRadius = 12f
    val radius = if (isSelected) baseRadius * 1.3f else baseRadius

    // Outer circle
    drawCircle(outlineColor, radius = radius, center = position)
    // Inner circle (white)
    drawCircle(Color.White, radius = radius - 3f, center = position)

    // Selection indicator
    if (isSelected) {
      drawCircle(
          Color(0xFFFF9800), radius = radius + 4f, center = position, style = Stroke(width = 2f))
    }
  }
}

private fun DrawScope.drawGuides(box: BoundingBox, params: RefinementDisplayParams) {
  fun toScreen(p: Point) = imageToScreen(p, params)

  // Draw center lines extending to edges
  val center = toScreen(box.center())
  val guideColor = Color(0xFF2196F3).copy(alpha = 0.3f)

  // Horizontal center line
  drawLine(
      guideColor,
      Offset(center.x, params.offsetY),
      Offset(center.x, (params.offsetY + box.height() * params.scale).toFloat()),
      strokeWidth = 1f)
  // Vertical center line
  drawLine(
      guideColor,
      Offset(params.offsetX, center.y),
      Offset((params.offsetX + box.width() * params.scale).toFloat(), center.y),
      strokeWidth = 1f)
}

private fun createSampledImageRefinement(image: BufferedImage, scale: Double): BufferedImage? {
  return try {
    val targetWidth = (image.width * scale).toInt().coerceIn(100, 2000)
    val targetHeight = (image.height * scale).toInt().coerceIn(100, 2000)
    if (targetWidth <= 0 || targetHeight <= 0) return null

    val sampled = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB)
    val graphics = sampled.createGraphics()
    graphics.drawImage(image, 0, 0, targetWidth, targetHeight, null)
    graphics.dispose()
    sampled
  } catch (e: Exception) {
    null
  }
}
