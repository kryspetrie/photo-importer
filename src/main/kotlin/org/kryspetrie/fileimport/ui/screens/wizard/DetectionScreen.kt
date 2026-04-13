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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.awt.image.BufferedImage
import org.kryspetrie.fileimport.infrastructure.wizard.*

/**
 * Detection Screen - High-performance screen for viewing and adjusting detected photo bounding
 * boxes.
 *
 * Key optimizations:
 * - Local mutable state for drag operations (no StateFlow reads during drag)
 * - Direct Canvas drawing without observeWhileReading
 * - Simple pointerInput keys to avoid handler restarts
 * - Large hit tolerance for easy corner selection
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetectionScreen(
    state: PhotoScanWizardState,
    onBack: () -> Unit,
    onToSummary: () -> Unit,
    modifier: Modifier = Modifier
) {
  // Collect state flows ONCE at screen level
  val image by state.image.collectAsState()
  val boundingBoxList by state.boundingBoxList.collectAsState()
  val selectedBoxIndex by state.selectedBoxIndex.collectAsState()
  val fourPointState by state.fourPointState.collectAsState()
  val wizardMode by state.wizardMode.collectAsState()

  var containerSize by remember { mutableStateOf(IntSize.Zero) }
  var showHelpDialog by remember { mutableStateOf(false) }
  var showBoxRejectedMessage by remember { mutableStateOf(false) }
  val snackbarHostState = remember { SnackbarHostState() }

  // Box count derived state
  val boxCount = remember(boundingBoxList) { boundingBoxList.size() }

  // Show rejection snackbar
  LaunchedEffect(showBoxRejectedMessage) {
    if (showBoxRejectedMessage) {
      snackbarHostState.showSnackbar(
          message = "Box too small - image must be at least 100x67 pixels",
          duration = SnackbarDuration.Short)
      showBoxRejectedMessage = false
    }
  }

  Scaffold(
      snackbarHost = { SnackbarHost(snackbarHostState) },
      topBar = {
        TopAppBar(
            title = { Text("Photo Detection") },
            navigationIcon = {
              IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            },
            actions = {
              // Mode indicator
              when (wizardMode) {
                WizardMode.FOUR_POINT -> {
                  Surface(
                      color = MaterialTheme.colorScheme.tertiaryContainer,
                      shape = RoundedCornerShape(4.dp)) {
                        Text(
                            "4-Point Mode",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium)
                      }
                }
                WizardMode.ADD_BOX -> {
                  Surface(
                      color = MaterialTheme.colorScheme.secondaryContainer,
                      shape = RoundedCornerShape(4.dp)) {
                        Text(
                            "Add Box",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium)
                      }
                }
                else -> {}
              }

              Spacer(Modifier.weight(1f))

              // Delete selected box
              if (selectedBoxIndex >= 0) {
                IconButton(onClick = { state.removeSelectedBox() }) {
                  Icon(
                      Icons.Default.Delete,
                      "Delete selected",
                      tint = MaterialTheme.colorScheme.error)
                }
              }

              // Help button
              IconButton(onClick = { showHelpDialog = true }) { Icon(Icons.Default.Info, "Help") }
            })
      },
      content = { paddingValues ->
        Column(modifier = modifier.fillMaxSize().padding(paddingValues)) {
          // Toolbar row
          DetectionToolbar(
              onBack = onBack,
              state = state,
              wizardMode = wizardMode,
              selectedBoxIndex = selectedBoxIndex,
              boxCount = boxCount)

          // Image canvas area - main detection interface
          Box(
              modifier =
                  Modifier.weight(1f)
                      .fillMaxWidth()
                      .background(Color.DarkGray)
                      .onSizeChanged { containerSize = it }
                      .withWizardKeyboardShortcuts(
                          wizardState = state, onProceed = onToSummary, onCancel = onBack)) {
                if (image != null && containerSize.width > 0) {
                  SimpleDetectionCanvas(
                      image = image!!,
                      boundingBoxList = boundingBoxList,
                      selectedBoxIndex = selectedBoxIndex,
                      wizardMode = wizardMode,
                      fourPointState = fourPointState,
                      containerSize = containerSize,
                      state = state,
                      onBoxRejected = { showBoxRejectedMessage = true })
                } else {
                  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                    Text(
                        "Loading image...",
                        color = Color.White,
                        modifier = Modifier.padding(top = 60.dp))
                  }
                }

                // 4-Point status bar
                if (wizardMode == WizardMode.FOUR_POINT) {
                  FourPointStatusBar(
                      state = fourPointState,
                      onRemoveLast = { state.removeLastFourPoint() },
                      onConfirm = { state.confirmFourPoint() },
                      onCancel = { state.exitFourPointMode() },
                      modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
                }
              }

          // Bottom status bar
          DetectionStatusBar(
              state = state,
              boxCount = boxCount,
              selectedIndex = selectedBoxIndex,
              onToSummary = onToSummary)
        }
      })

  // Help dialog
  if (showHelpDialog) {
    KeyboardShortcutHelpDialog(onDismiss = { showHelpDialog = false })
  }
}

@Composable
private fun DetectionToolbar(
    onBack: () -> Unit,
    state: PhotoScanWizardState,
    wizardMode: WizardMode,
    selectedBoxIndex: Int,
    boxCount: Int
) {
  Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically) {

          // Back button - goes to import step
          OutlinedButton(onClick = onBack, modifier = Modifier.height(36.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Back", style = MaterialTheme.typography.labelMedium)
          }

          // 4-Point toggle button
          when (wizardMode) {
            WizardMode.FOUR_POINT -> {
              Button(
                  onClick = { state.exitFourPointMode() },
                  colors =
                      ButtonDefaults.buttonColors(
                          containerColor = MaterialTheme.colorScheme.tertiary),
                  modifier = Modifier.height(36.dp)) {
                    Text("Cancel 4-Point", style = MaterialTheme.typography.labelMedium)
                  }
            }
            else -> {
              OutlinedButton(
                  onClick = { state.enterFourPointMode() }, modifier = Modifier.height(36.dp)) {
                    Text("4-Point", style = MaterialTheme.typography.labelMedium)
                  }
            }
          }

          // Add Box toggle button
          when (wizardMode) {
            WizardMode.ADD_BOX -> {
              Button(
                  onClick = { state.exitAddBoxMode() },
                  colors =
                      ButtonDefaults.buttonColors(
                          containerColor = MaterialTheme.colorScheme.secondary),
                  modifier = Modifier.height(36.dp)) {
                    Text("Cancel", style = MaterialTheme.typography.labelMedium)
                  }
            }
            else -> {
              OutlinedButton(
                  onClick = { state.enterAddBoxMode() }, modifier = Modifier.height(36.dp)) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Box", style = MaterialTheme.typography.labelMedium)
                  }
            }
          }

          Spacer(Modifier.weight(1f))

          // Box count display
          Text(
              "$boxCount box(es)",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant)

          if (selectedBoxIndex >= 0) {
            Text(
                " | Selected: ${selectedBoxIndex + 1}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
          }
        }
  }
}

/**
 * Simple, high-performance detection canvas.
 *
 * Key design decisions:
 * - ALL drag state is LOCAL (not observed from StateFlow)
 * - StateFlow is only read at composition time
 * - pointerInput uses Unit key (never restarts)
 * - Large hit radius for easy corner selection
 * - Direct coordinate conversion
 */
@Composable
private fun SimpleDetectionCanvas(
    image: BufferedImage,
    boundingBoxList: BoundingBoxList,
    selectedBoxIndex: Int,
    wizardMode: WizardMode,
    fourPointState: FourPointState,
    containerSize: IntSize,
    state: PhotoScanWizardState,
    onBoxRejected: () -> Unit
) {
  // Calculate display params ONCE
  val displayParams: DisplayParams =
      remember(containerSize, image.width, image.height) {
        calculateDisplayParams(containerSize, image.width, image.height)
      }

  // Create sampled image ONCE
  val sampledBitmap: androidx.compose.ui.graphics.ImageBitmap? =
      remember(image, displayParams.scale) { createSampledBitmap(image, displayParams.scale) }

  // Local mutable state for dragging - this does NOT trigger recomposition
  var localDraggedCorner by remember { mutableStateOf<Corner?>(null) }
  var localDraggedBox by remember { mutableStateOf<Int?>(null) }
  var localDragStart by remember { mutableStateOf(Offset.Zero) }

  // Copy boundingBoxList to local scope for use in lambda (avoid observeWhileReading)
  val localBoxes = boundingBoxList

  Canvas(
      modifier =
          Modifier.fillMaxSize()
              .pointerInput(Unit) {
                // CRITICAL: Use Unit key - NEVER restart this handler
                // This prevents gesture cancellation during drag
                detectTapGestures(
                    onTap = { tapOffset ->
                      when (wizardMode) {
                        WizardMode.FOUR_POINT -> {
                          val imagePoint = screenToImagePoint(tapOffset, displayParams)
                          state.addFourPoint(Point(imagePoint.x, imagePoint.y))
                        }
                        WizardMode.ADD_BOX -> {
                          val imagePoint = screenToImagePoint(tapOffset, displayParams)
                          if (!state.createBoxAtCenter(imagePoint.x, imagePoint.y)) {
                            onBoxRejected()
                          }
                        }
                        else -> {
                          // Find corner hit with LARGE tolerance
                          val hit = findCornerHitLarge(tapOffset, localBoxes, displayParams)
                          if (hit != null) {
                            state.selectBox(hit.first)
                            state.selectCorner(hit.second)
                          } else {
                            // Find box hit
                            val boxHit = findBoxHitSimple(tapOffset, localBoxes, displayParams)
                            if (boxHit >= 0) {
                              state.selectBox(boxHit)
                            } else {
                              state.deselectAll()
                            }
                          }
                        }
                      }
                    },
                    onDoubleTap = { tapOffset ->
                      if (wizardMode == WizardMode.NORMAL) {
                        val boxHit = findBoxHitSimple(tapOffset, localBoxes, displayParams)
                        if (boxHit >= 0) {
                          state.selectBox(boxHit)
                          state.enterRefinement(boxHit)
                        }
                      }
                    })
              }
              .pointerInput(Unit) {
                // Second pointerInput for drag - also uses Unit key
                detectDragGestures(
                    onDragStart = { startOffset ->
                      localDragStart = startOffset

                      if (wizardMode == WizardMode.NORMAL && selectedBoxIndex >= 0) {
                        // Check for corner hit with LARGE tolerance (50 pixels!)
                        val hit = findCornerHitLarge(startOffset, localBoxes, displayParams)
                        if (hit != null && hit.first == selectedBoxIndex) {
                          localDraggedCorner = hit.second
                          state.selectCorner(hit.second)
                        } else if (isInsideBox(
                            startOffset, selectedBoxIndex, localBoxes, displayParams)) {
                          // Start dragging the box
                          localDraggedBox = selectedBoxIndex
                        }
                      }
                    },
                    onDrag = { change, _ ->
                      if (localDraggedCorner != null && selectedBoxIndex >= 0) {
                        // Move the corner - this updates state
                        val imagePoint = screenToImagePoint(change.position, displayParams)
                        state.moveCorner(
                            selectedBoxIndex, localDraggedCorner!!, imagePoint.x, imagePoint.y)
                      } else if (localDraggedBox != null) {
                        // Move the entire box
                        val deltaX = (change.position.x - localDragStart.x) / displayParams.scale
                        val deltaY = (change.position.y - localDragStart.y) / displayParams.scale
                        state.moveSelectedBox(deltaX.toDouble(), deltaY.toDouble())
                        localDragStart = change.position
                      }
                    },
                    onDragEnd = {
                      localDraggedCorner = null
                      localDraggedBox = null
                    })
              }) {
        // === DRAWING CODE (runs on every frame) ===

        // Draw background
        drawRect(
            color = Color.DarkGray,
            topLeft = Offset(displayParams.offsetX, displayParams.offsetY),
            size = Size(image.width * displayParams.scale, image.height * displayParams.scale))

        // Draw sampled image
        if (sampledBitmap != null) {
          drawImage(sampledBitmap, topLeft = Offset(displayParams.offsetX, displayParams.offsetY))
        }

        // Draw all bounding boxes
        for (i in 0 until localBoxes.size()) {
          val box = localBoxes.boxes[i]
          val isSelected = i == selectedBoxIndex
          drawBoxOverlay(box, isSelected, displayParams)
        }

        // Draw 4-point preview
        if (wizardMode == WizardMode.FOUR_POINT &&
            fourPointState.mode != FourPointState.Mode.INACTIVE) {
          drawFourPointPreviewSimple(fourPointState, displayParams)
        }
      }
}

/** Calculate display parameters. */
private data class DisplayParams(val scale: Float, val offsetX: Float, val offsetY: Float)

private fun calculateDisplayParams(
    containerSize: IntSize,
    imageWidth: Int,
    imageHeight: Int
): DisplayParams {
  if (containerSize.width <= 0 || containerSize.height <= 0) {
    return DisplayParams(1f, 0f, 0f)
  }
  val scaleX = containerSize.width.toFloat() / imageWidth
  val scaleY = containerSize.height.toFloat() / imageHeight
  val scale = minOf(scaleX, scaleY)
  val offsetX = (containerSize.width - imageWidth * scale) / 2f
  val offsetY = (containerSize.height - imageHeight * scale) / 2f
  return DisplayParams(scale, offsetX, offsetY)
}

/** Convert screen offset to image coordinates. */
private fun screenToImagePoint(screenPos: Offset, params: DisplayParams): Point {
  return Point(
      ((screenPos.x - params.offsetX) / params.scale).toDouble(),
      ((screenPos.y - params.offsetY) / params.scale).toDouble())
}

/** Find corner hit with LARGE tolerance (50 pixels base). */
private fun findCornerHitLarge(
    offset: Offset,
    boxes: BoundingBoxList,
    params: DisplayParams
): Pair<Int, Corner>? {
  // VERY LARGE hit radius - 50 pixels scaled
  val hitRadius = 50f * params.scale

  for (i in 0 until boxes.size()) {
    val box = boxes.boxes[i]
    val corners =
        listOf(
            Corner.TOP_LEFT to box.corners.topLeft,
            Corner.TOP_RIGHT to box.corners.topRight,
            Corner.BOTTOM_LEFT to box.corners.bottomLeft,
            Corner.BOTTOM_RIGHT to box.corners.bottomRight)

    for ((corner, point) in corners) {
      val screenX = params.offsetX + (point.x * params.scale).toFloat()
      val screenY = params.offsetY + (point.y * params.scale).toFloat()
      val distance =
          kotlin.math.sqrt(
              (offset.x - screenX) * (offset.x - screenX) +
                  (offset.y - screenY) * (offset.y - screenY))
      if (distance < hitRadius) {
        return i to corner
      }
    }
  }
  return null
}

/** Simple box hit test. */
private fun findBoxHitSimple(offset: Offset, boxes: BoundingBoxList, params: DisplayParams): Int {
  for (i in 0 until boxes.size()) {
    if (isInsideBox(offset, i, boxes, params)) {
      return i
    }
  }
  return -1
}

/** Check if offset is inside a specific box. */
private fun isInsideBox(
    offset: Offset,
    boxIndex: Int,
    boxes: BoundingBoxList,
    params: DisplayParams
): Boolean {
  if (boxIndex < 0 || boxIndex >= boxes.size()) return false
  val box = boxes.boxes[boxIndex]

  val screenPoints =
      listOf(
          params.offsetX + (box.corners.topLeft.x * params.scale).toFloat(),
          params.offsetY + (box.corners.topLeft.y * params.scale).toFloat(),
          params.offsetX + (box.corners.topRight.x * params.scale).toFloat(),
          params.offsetY + (box.corners.topRight.y * params.scale).toFloat(),
          params.offsetX + (box.corners.bottomLeft.x * params.scale).toFloat(),
          params.offsetY + (box.corners.bottomLeft.y * params.scale).toFloat(),
          params.offsetX + (box.corners.bottomRight.x * params.scale).toFloat(),
          params.offsetY + (box.corners.bottomRight.y * params.scale).toFloat())

  val minX = minOf(screenPoints[0], screenPoints[2], screenPoints[4], screenPoints[6])
  val maxX = maxOf(screenPoints[0], screenPoints[2], screenPoints[4], screenPoints[6])
  val minY = minOf(screenPoints[1], screenPoints[3], screenPoints[5], screenPoints[7])
  val maxY = maxOf(screenPoints[1], screenPoints[3], screenPoints[5], screenPoints[7])

  return offset.x >= minX && offset.x <= maxX && offset.y >= minY && offset.y <= maxY
}

/** Draw bounding box overlay. */
private fun DrawScope.drawBoxOverlay(box: BoundingBox, isSelected: Boolean, params: DisplayParams) {
  val outlineColor = if (isSelected) Color(0xFF2196F3) else Color(0xFF4CAF50)
  val fillColor = outlineColor.copy(alpha = 0.2f)

  val tlX = params.offsetX + (box.corners.topLeft.x * params.scale).toFloat()
  val tlY = params.offsetY + (box.corners.topLeft.y * params.scale).toFloat()
  val trX = params.offsetX + (box.corners.topRight.x * params.scale).toFloat()
  val trY = params.offsetY + (box.corners.topRight.y * params.scale).toFloat()
  val blX = params.offsetX + (box.corners.bottomLeft.x * params.scale).toFloat()
  val blY = params.offsetY + (box.corners.bottomLeft.y * params.scale).toFloat()
  val brX = params.offsetX + (box.corners.bottomRight.x * params.scale).toFloat()
  val brY = params.offsetY + (box.corners.bottomRight.y * params.scale).toFloat()

  // Draw fill
  val path =
      Path().apply {
        moveTo(tlX, tlY)
        lineTo(trX, trY)
        lineTo(brX, brY)
        lineTo(blX, blY)
        close()
      }
  drawPath(path, fillColor, style = Fill)
  drawPath(path, outlineColor, style = Stroke(width = if (isSelected) 3f else 2f))

  // Draw corners - LARGE for easy selection
  val cornerRadius = if (isSelected) 12f else 8f
  listOf(Offset(tlX, tlY), Offset(trX, trY), Offset(blX, blY), Offset(brX, brY)).forEach { corner ->
    drawCircle(outlineColor, radius = cornerRadius, center = corner)
    drawCircle(Color.White, radius = cornerRadius - 2, center = corner)
  }

  // Center indicator
  if (isSelected) {
    val centerX = (tlX + trX + blX + brX) / 4
    val centerY = (tlY + trY + blY + brY) / 4
    drawCircle(outlineColor.copy(alpha = 0.5f), radius = 8f, center = Offset(centerX, centerY))
    drawCircle(Color.White, radius = 4f, center = Offset(centerX, centerY))
  }
}

/** Draw 4-point preview. */
private fun DrawScope.drawFourPointPreviewSimple(
    fourPointState: FourPointState,
    params: DisplayParams
) {
  val points = fourPointState.points
  val accentColor = Color(0xFFFF9800)

  points.forEach { point ->
    val screenX = params.offsetX + (point.x * params.scale).toFloat()
    val screenY = params.offsetY + (point.y * params.scale).toFloat()
    drawCircle(accentColor, radius = 15f, center = Offset(screenX, screenY))
    drawCircle(Color.White, radius = 12f, center = Offset(screenX, screenY))
  }

  if (points.size >= 2) {
    val path =
        Path().apply {
          val first =
              Offset(
                  params.offsetX + (points[0].x * params.scale).toFloat(),
                  params.offsetY + (points[0].y * params.scale).toFloat())
          moveTo(first.x, first.y)
          for (i in 1 until points.size) {
            val pt =
                Offset(
                    params.offsetX + (points[i].x * params.scale).toFloat(),
                    params.offsetY + (points[i].y * params.scale).toFloat())
            lineTo(pt.x, pt.y)
          }
        }
    drawPath(path, accentColor, style = Stroke(width = 2f))
  }
}

/** Create sampled bitmap for display. */
private fun createSampledBitmap(
    image: BufferedImage,
    scale: Float
): androidx.compose.ui.graphics.ImageBitmap? {
  return try {
    val targetWidth = (image.width * scale).toInt().coerceIn(100, 2000)
    val targetHeight = (image.height * scale).toInt().coerceIn(100, 2000)
    if (targetWidth <= 0 || targetHeight <= 0) return null

    val scaled = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB)
    val g = scaled.createGraphics()
    g.drawImage(image, 0, 0, targetWidth, targetHeight, null)
    g.dispose()

    scaled.toComposeImageBitmap()
  } catch (e: Exception) {
    null
  }
}

@Composable
private fun DetectionStatusBar(
    state: PhotoScanWizardState,
    boxCount: Int,
    selectedIndex: Int,
    onToSummary: () -> Unit
) {
  Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {

          // Info
          Text(
              "$boxCount photo(s) detected",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant)

          // Action buttons
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Refine button (when box selected)
            if (selectedIndex >= 0) {
              OutlinedButton(
                  onClick = { state.enterRefinement(selectedIndex) },
                  modifier = Modifier.height(36.dp)) {
                    Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Refine", style = MaterialTheme.typography.labelMedium)
                  }
            }

            // Next button - navigates to Summary step
            Button(
                onClick = { state.goToSummary() },
                enabled = boxCount > 0,
                modifier = Modifier.height(36.dp)) {
                  Text("Next", style = MaterialTheme.typography.labelMedium)
                  Spacer(Modifier.width(4.dp))
                  Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp))
                }
          }
        }
  }
}
