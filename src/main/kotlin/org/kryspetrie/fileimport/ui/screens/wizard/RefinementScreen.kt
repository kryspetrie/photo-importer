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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.awt.image.BufferedImage
import org.kryspetrie.fileimport.infrastructure.wizard.*

/**
 * Refinement screen showing a zoomed view of a single bounding box for precise corner adjustment.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefinementScreen(
    state: PhotoScanWizardState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
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
  val currentBox = remember(refinementBoxIndex, boundingBoxList) {
    if (refinementBoxIndex >= 0 && refinementBoxIndex < boundingBoxList.size()) {
      boundingBoxList.boxes[refinementBoxIndex]
    } else null
  }

  // Fit to box when canvas size is known
  LaunchedEffect(canvasSize, refinementBoxIndex) {
    if (canvasSize.width > 0 && canvasSize.height > 0) {
      state.fitToBox(canvasSize.width.toDouble(), canvasSize.height.toDouble())
    }
  }

  Scaffold(
      topBar = {
        TopAppBar(
            title = { Text("Refine Bounding Box") },
            navigationIcon = {
              IconButton(onClick = {
                state.exitRefinement()
                onBack()
              }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to Overview")
              }
            },
            actions = {
              IconButton(onClick = { state.undo() }) { Icon(Icons.Default.Undo, "Undo") }
              IconButton(onClick = { state.redo() }) { Icon(Icons.Default.Redo, "Redo") }
              IconButton(onClick = {
                if (refinementBoxIndex >= 0) {
                  state.removeBox(refinementBoxIndex)
                  state.exitRefinement()
                  onBack()
                }
              }) {
                Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
              }
            })
      },
      content = { paddingValues ->
        Column(
            modifier = modifier.fillMaxSize().padding(paddingValues),
            verticalArrangement = Arrangement.SpaceBetween) {
              // Main canvas area
              RefinementCanvas(
                  state = state,
                  image = image,
                  box = currentBox,
                  boxIndex = refinementBoxIndex,
                  canvasSize = canvasSize,
                  onCanvasSizeChanged = { canvasSize = it },
                  modifier = Modifier.weight(1f).fillMaxWidth())

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
                      state.fitToBox(canvasSize.width.toDouble(), canvasSize.height.toDouble())
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
                  })
            }
      })
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
    onContract: () -> Unit
) {
  Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(12.dp)) {
      // Navigation row
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
              IconButton(onClick = onPrevious, enabled = currentIndex > 0, modifier = Modifier.size(32.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous", Modifier.size(18.dp))
              }
              Text("Image ${currentIndex + 1} of $totalCount", style = MaterialTheme.typography.titleMedium)
              IconButton(onClick = onNext, enabled = currentIndex < totalCount - 1, modifier = Modifier.size(32.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next", Modifier.size(18.dp))
              }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
              if (selectedCorner != null) {
                Text("Selected: ${selectedCorner.name}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                OutlinedButton(onClick = onDeselect, modifier = Modifier.height(32.dp)) {
                  Text("Deselect", style = MaterialTheme.typography.labelSmall)
                }
              } else {
                Text("Click a corner to select", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          }

      HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

      // Controls row
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          verticalAlignment = Alignment.CenterVertically) {
            // Zoom
            OutlinedButton(onClick = onZoomOut, modifier = Modifier.height(32.dp)) {
              Icon(Icons.Default.ZoomOut, null, Modifier.size(16.dp))
            }
            Text("${(zoomLevel * 100).toInt()}%", modifier = Modifier.width(44.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
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
                color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
    }
  }
}

@Composable
private fun RefinementCanvas(
    state: PhotoScanWizardState,
    image: BufferedImage?,
    box: BoundingBox?,
    boxIndex: Int,
    canvasSize: IntSize,
    onCanvasSizeChanged: (IntSize) -> Unit,
    modifier: Modifier = Modifier
) {
  val zoomController by state.zoomController.collectAsState()
  val selectedCorner by state.selectedCorner.collectAsState()

  // Drag state
  var draggingCorner by remember { mutableStateOf<Corner?>(null) }
  var draggingBackground by remember { mutableStateOf(false) }
  var lastPosition by remember { mutableStateOf(Offset.Zero) }

  // Get zoom/pan values - recalculated on every recomposition
  val zoom = zoomController.zoom.toFloat()
  val panX = zoomController.panX.toFloat()
  val panY = zoomController.panY.toFloat()

  // Sampled image cache
  val sampledImage = remember(zoom) {
    if (image != null && zoom > 0.1) {
      createSampledImageForRefinement(image, zoom.toDouble())
    } else null
  }

  Box(
      modifier = modifier
          .background(Color.DarkGray)
          .onSizeChanged { onCanvasSizeChanged(it) }
          .onKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown) {
              val delta = 10.0  // Use 10px for now
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
          .pointerInput(state, box, selectedCorner) {
            detectTapGestures { offset ->
              if (box != null) {
                val hit = findCornerHit(offset, box, zoom, panX, panY)
                if (hit != null) {
                  state.selectCorner(hit)
                } else {
                  state.deselectCorner()
                }
              }
            }
          }
          .pointerInput(state, box, zoom) {
            detectDragGestures(
                onDragStart = { offset ->
                  lastPosition = offset
                  if (box != null) {
                    val hit = findCornerHit(offset, box, zoom, panX, panY)
                    if (hit != null) {
                      draggingCorner = hit
                      state.selectCorner(hit)
                    } else {
                      draggingBackground = true
                    }
                  }
                },
                onDrag = { change, _ ->
                  if (draggingCorner != null && box != null) {
                    val pos = screenToImage(change.position, zoom, panX, panY)
                    state.moveCornerWithValidation(boxIndex, draggingCorner!!, pos.x, pos.y)
                  } else if (draggingBackground) {
                    val dx = (change.position.x - lastPosition.x).toDouble()
                    val dy = (change.position.y - lastPosition.y).toDouble()
                    state.pan(dx, dy)
                    lastPosition = change.position
                  }
                },
                onDragEnd = {
                  draggingCorner = null
                  draggingBackground = false
                })
          }
          .withRefinementKeyboardShortcuts(state)) {
    // Draw content
    Canvas(modifier = Modifier.fillMaxSize()) {
      // Background
      drawRect(Color.DarkGray, Offset(panX, panY), Size(image?.width?.toFloat() ?: 0f, image?.height?.toFloat() ?: 0f))
      
      // Image
      sampledImage?.let {
        drawImage(it.toComposeImageBitmap(), Offset(panX, panY))
      }
      
      // Box and handles
      if (box != null) {
        drawRefinementBox(box, selectedCorner, zoom, panX, panY)
      }
    }
  }
}

/** Keyboard shortcuts for refinement mode */
private fun Modifier.withRefinementKeyboardShortcuts(state: PhotoScanWizardState): Modifier {
  return this
}

private class ShiftCheckingKeyEvent(val delegate: Any) {
  fun isShiftPressed(): Boolean {
    return try {
      val method = delegate.javaClass.getMethod("isShiftPressed")
      method.invoke(delegate) as Boolean
    } catch (e: Exception) { false }
  }
}

internal fun findCornerHit(offset: Offset, box: BoundingBox, zoom: Float, panX: Float, panY: Float): Corner? {
  val hitRadius = 25f
  val corners = listOf(
    Corner.TOP_LEFT to box.corners.topLeft,
    Corner.TOP_RIGHT to box.corners.topRight,
    Corner.BOTTOM_LEFT to box.corners.bottomLeft,
    Corner.BOTTOM_RIGHT to box.corners.bottomRight
  )
  
  for ((corner, point) in corners) {
    val screenPos = imageToScreen(point, zoom, panX, panY)
    if ((offset - screenPos).getDistance() < hitRadius) {
      return corner
    }
  }
  return null
}

internal fun screenToImage(screen: Offset, zoom: Float, panX: Float, panY: Float): Point {
  return Point(((screen.x - panX) / zoom).toDouble(), ((screen.y - panY) / zoom).toDouble())
}

internal fun imageToScreen(point: Point, zoom: Float, panX: Float, panY: Float): Offset {
  return Offset((panX + point.x * zoom).toFloat(), (panY + point.y * zoom).toFloat())
}

private fun DrawScope.drawRefinementBox(box: BoundingBox, selected: Corner?, zoom: Float, panX: Float, panY: Float) {
  fun toScreen(p: Point) = imageToScreen(p, zoom, panX, panY)
  
  val tl = toScreen(box.corners.topLeft)
  val tr = toScreen(box.corners.topRight)
  val bl = toScreen(box.corners.bottomLeft)
  val br = toScreen(box.corners.bottomRight)
  
  // Fill
  val path = Path().apply {
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
  listOf(Corner.TOP_LEFT to tl, Corner.TOP_RIGHT to tr, Corner.BOTTOM_LEFT to bl, Corner.BOTTOM_RIGHT to br).forEach { (corner, pos) ->
    val r = if (corner == selected) handleRadius * 1.3f else handleRadius
    drawCircle(Color(0xFF2196F3), r, pos)
    drawCircle(Color.White, r - 3f, pos)
  }
}

internal fun createSampledImageForRefinement(image: BufferedImage, scale: Double): BufferedImage? {
  return try {
    val w = (image.width * scale).toInt().coerceIn(100, 2000)
    val h = (image.height * scale).toInt().coerceIn(100, 2000)
    if (w <= 0 || h <= 0) return null
    val result = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
    val g = result.createGraphics()
    g.drawImage(image, 0, 0, w, h, null)
    g.dispose()
    result
  } catch (e: Exception) { null }
}
