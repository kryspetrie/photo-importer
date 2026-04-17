package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.animation.core.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.awt.image.BufferedImage
import org.kryspetrie.fileimport.infrastructure.wizard.*

/**
 * Overview screen showing the full scanned image with all detected bounding boxes. Users can
 * select, add, remove, and navigate to refinement for individual boxes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    state: PhotoScanWizardState,
    onBack: () -> Unit,
    onToSummary: () -> Unit,
    modifier: Modifier = Modifier
) {
  // Collect state flows
  val wizardMode by state.wizardMode.collectAsState()
  val fourPointState by state.fourPointState.collectAsState()
  val boundingBoxList by state.boundingBoxList.collectAsState()
  val selectedBoxIndex by state.selectedBoxIndex.collectAsState()
  val zoomController by state.zoomController.collectAsState()
  val image by state.image.collectAsState()

  var containerSize by remember { mutableStateOf(IntSize.Zero) }
  var showHelpDialog by remember { mutableStateOf(false) }
  var showBoxRejectedMessage by remember { mutableStateOf(false) }
  val snackbarHostState = remember { SnackbarHostState() }

  // Launch snackbar when box is rejected
  LaunchedEffect(showBoxRejectedMessage) {
    if (showBoxRejectedMessage) {
      snackbarHostState.showSnackbar(
          message = "Box too small - image must be at least 100x67 pixels for a box",
          duration = SnackbarDuration.Short)
      showBoxRejectedMessage = false
    }
  }

  Scaffold(
      snackbarHost = { SnackbarHost(snackbarHostState) },
      topBar = {
        TopAppBar(
            title = {
              Text("Bounding Box Overview", maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            navigationIcon = {
              IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            },
            actions = {
              // Mode indicator
              when (wizardMode) {
                WizardMode.NORMAL -> {
                  // No mode indicator
                }
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
                            "Add Box Mode",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium)
                      }
                }
                WizardMode.REFINEMENT -> {
                  // Handled in refinement screen
                }
              }

              Spacer(Modifier.weight(1f))

              // Delete button (only when box selected)
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
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.surface)) {

              // TOP HALF: Image canvas takes all available space
              Box(
                  modifier =
                      Modifier.weight(1f)
                          .fillMaxWidth()
                          .background(Color.DarkGray)
                          .onSizeChanged { containerSize = it }
                          .withWizardKeyboardShortcuts(
                              wizardState = state, onProceed = onToSummary, onCancel = onBack)) {
                    if (image != null && containerSize.width > 0) {
                      OverviewCanvas(
                          state = state,
                          image = image!!,
                          containerSize = containerSize,
                          wizardMode = wizardMode,
                          fourPointState = fourPointState,
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

                    // Zoom controls (top-right corner)
                    ZoomControls(
                        zoomController = zoomController,
                        onZoomIn = { state.zoomIn() },
                        onZoomOut = { state.zoomOut() },
                        onFitToView = {
                          state.fitToView(
                              containerSize.width.toDouble(), containerSize.height.toDouble())
                        },
                        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp))

                    // 4-Point status overlay (bottom center)
                    if (wizardMode == WizardMode.FOUR_POINT) {
                      FourPointStatusBar(
                          state = fourPointState,
                          onRemoveLast = { state.removeLastFourPoint() },
                          onConfirm = { state.confirmFourPoint() },
                          onCancel = { state.exitFourPointMode() },
                          modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
                    }
                  }

              // BOTTOM HALF: All controls consolidated
              OverviewControlsPanel(
                  state = state,
                  wizardMode = wizardMode,
                  fourPointState = fourPointState,
                  selectedBoxIndex = selectedBoxIndex,
                  boxCount = boundingBoxList.size(),
                  zoomLevel = zoomController.zoom,
                  onBack = onBack,
                  onToSummary = onToSummary)
            }
      })

  // Help dialog
  if (showHelpDialog) {
    KeyboardShortcutHelpDialog(onDismiss = { showHelpDialog = false })
  }
}

/**
 * Consolidated controls panel for the bottom half of the overview screen. Contains all navigation,
 * mode selection, and action buttons.
 */
@Composable
private fun OverviewControlsPanel(
    state: PhotoScanWizardState,
    wizardMode: WizardMode,
    fourPointState: FourPointState,
    selectedBoxIndex: Int,
    boxCount: Int,
    zoomLevel: Double,
    onBack: () -> Unit,
    onToSummary: () -> Unit
) {
  Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
      // Row 1: Mode controls and box info
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically) {
            // 4-Point mode toggle
            when (wizardMode) {
              WizardMode.FOUR_POINT -> {
                Button(
                    onClick = { state.exitFourPointMode() },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary),
                    modifier = Modifier.height(40.dp)) {
                      Text("Cancel 4-Point")
                    }
              }
              else -> {
                OutlinedButton(
                    onClick = { state.enterFourPointMode() },
                    modifier = Modifier.height(40.dp)) {
                      Icon(Icons.Default.GridOn, null, Modifier.size(18.dp))
                      Spacer(Modifier.width(4.dp))
                      Text("4-Point")
                    }
              }
            }

            // Add Box mode toggle
            when (wizardMode) {
              WizardMode.ADD_BOX -> {
                Button(
                    onClick = { state.exitAddBoxMode() },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary),
                    modifier = Modifier.height(40.dp)) {
                      Text("Cancel")
                    }
              }
              else -> {
                OutlinedButton(
                    onClick = { state.enterAddBoxMode() },
                    modifier = Modifier.height(40.dp)) {
                      Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                      Spacer(Modifier.width(4.dp))
                      Text("Add Box")
                    }
              }
            }

            Spacer(Modifier.weight(1f))

            // Zoom indicator
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.height(40.dp)) {
                  Row(
                      modifier = Modifier.padding(horizontal = 12.dp),
                      verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.ZoomIn,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "${(zoomLevel * 100).toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                      }
                }

            Spacer(Modifier.width(8.dp))

            // Box count info
            Text(
                "$boxCount box${if (boxCount != 1) "es" else ""}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
          }

      Spacer(Modifier.height(12.dp))

      // Row 2: Action buttons
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically) {
            // Refine selected box
            if (selectedBoxIndex >= 0) {
              AssistChip(
                  onClick = { state.enterRefinement(selectedBoxIndex) },
                  label = { Text("Box ${selectedBoxIndex + 1} selected") },
                  leadingIcon = {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                  },
                  modifier = Modifier.height(36.dp))
              
              OutlinedButton(
                  onClick = { state.enterRefinement(selectedBoxIndex) },
                  modifier = Modifier.height(40.dp)) {
                    Icon(Icons.Default.Tune, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Refine")
                  }

              IconButton(onClick = { state.removeSelectedBox() }, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete selected box",
                    tint = MaterialTheme.colorScheme.error)
              }
            }

            Spacer(Modifier.weight(1f))

            // Cancel and Continue buttons
            OutlinedButton(onClick = onBack, modifier = Modifier.height(40.dp)) {
              Text("Cancel")
            }

            Button(
                onClick = onToSummary, enabled = boxCount > 0, modifier = Modifier.height(40.dp)) {
                  Text("Continue to Summary")
                  Spacer(Modifier.width(4.dp))
                  Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp))
                }
          }
    }
  }
}

@Composable
private fun OverviewCanvas(
    state: PhotoScanWizardState,
    image: BufferedImage,
    containerSize: IntSize,
    wizardMode: WizardMode,
    fourPointState: FourPointState,
    onBoxRejected: () -> Unit
) {
  val boundingBoxList by state.boundingBoxList.collectAsState()
  val selectedBoxIndex by state.selectedBoxIndex.collectAsState()
  val zoomController by state.zoomController.collectAsState()

  // Hit detection for corners
  var draggedCorner by remember { mutableStateOf<Corner?>(null) }
  var isDraggingPhoto by remember { mutableStateOf(false) }
  var lastDragPos by remember { mutableStateOf(Offset.Zero) }
  var draggedBoxIndex by remember { mutableStateOf(-1) }

  Canvas(
      modifier =
          Modifier.fillMaxSize()
              .pointerInput(state, wizardMode) {
                detectTapGestures(
                    onTap = { offset ->
                      when (wizardMode) {
                        WizardMode.FOUR_POINT -> {
                          val point =
                              zoomController.screenToImage(offset.x.toDouble(), offset.y.toDouble())
                          state.addFourPoint(Point(point.x, point.y))
                        }
                        WizardMode.ADD_BOX -> {
                          val point =
                              zoomController.screenToImage(offset.x.toDouble(), offset.y.toDouble())
                          val created = state.createBoxAtCenter(point.x, point.y)
                          // Show message if box was rejected (too small)
                          if (!created) {
                            onBoxRejected()
                          }
                        }
                        else -> {
                          // Check if tapping on a corner
                          val cornerHit = findCornerHit(offset, boundingBoxList, zoomController)
                          if (cornerHit != null) {
                            state.selectBox(cornerHit.first)
                            state.selectCorner(cornerHit.second)
                          } else {
                            // Check if tapping on a box
                            val boxHit = findBoxHit(offset, boundingBoxList, zoomController)
                            if (boxHit >= 0) {
                              state.selectBox(boxHit)
                              state.enterRefinement(boxHit)
                            } else {
                              // Deselect
                              state.deselectAll()
                            }
                          }
                        }
                      }
                    })
              }
              .pointerInput(state, wizardMode, selectedBoxIndex) {
                detectDragGestures(
                    onDragStart = { offset ->
                      lastDragPos = offset
                      if (wizardMode == WizardMode.NORMAL && selectedBoxIndex >= 0) {
                        // Check for corner hit first
                        val cornerHit = findCornerHit(offset, boundingBoxList, zoomController)
                        if (cornerHit != null && cornerHit.first == selectedBoxIndex) {
                          draggedCorner = cornerHit.second
                          state.selectCorner(cornerHit.second)
                        } else {
                          // Check if dragging inside selected box
                          val boxHit = findBoxHit(offset, boundingBoxList, zoomController)
                          if (boxHit == selectedBoxIndex) {
                            isDraggingPhoto = true
                            draggedBoxIndex = boxHit
                          }
                        }
                      }
                    },
                    onDrag = { change, _ ->
                      if (draggedCorner != null && selectedBoxIndex >= 0) {
                        val pos =
                            zoomController.screenToImage(
                                change.position.x.toDouble(), change.position.y.toDouble())
                        state.moveCorner(selectedBoxIndex, draggedCorner!!, pos.x, pos.y)
                      } else if (isDraggingPhoto && draggedBoxIndex >= 0) {
                        val deltaX = (change.position.x - lastDragPos.x) / zoomController.zoom
                        val deltaY = (change.position.y - lastDragPos.y) / zoomController.zoom
                        state.moveSelectedBox(deltaX, deltaY)
                        lastDragPos = change.position
                      }
                    },
                    onDragEnd = {
                      draggedCorner = null
                      isDraggingPhoto = false
                      draggedBoxIndex = -1
                    })
              }) {
        // Calculate display parameters
        val scale = zoomController.zoom.toFloat()
        val panX = zoomController.panX.toFloat()
        val panY = zoomController.panY.toFloat()

        // Draw background
        drawRect(
            color = Color.DarkGray,
            topLeft = Offset(panX, panY),
            size = Size(image.width * scale, image.height * scale))

        // Draw image (sampled for performance)
        val displayImage = createSampledImage(image, scale.toDouble())
        if (displayImage != null) {
          drawImage(displayImage.toComposeImageBitmap(), topLeft = Offset(panX, panY))
        }

        // Draw bounding boxes
        for (i in 0 until boundingBoxList.size()) {
          val box = boundingBoxList.boxes[i]
          val isSelected = i == selectedBoxIndex
          drawBoundingBox(box, isSelected, scale, panX, panY)
        }

        // Draw 4-point state if active
        if (wizardMode == WizardMode.FOUR_POINT &&
            fourPointState.mode != FourPointState.Mode.INACTIVE) {
          drawFourPointPreview(fourPointState, scale, panX, panY)
        }
      }
}

private fun findCornerHit(
    offset: Offset,
    boxes: BoundingBoxList,
    zoom: ZoomController
): Pair<Int, Corner>? {
  val hitRadius = 30f
  for (i in 0 until boxes.size()) {
    val box = boxes.boxes[i]
    val corners = box.corners
    val cornerList =
        listOf(
            Corner.TOP_LEFT to corners.topLeft,
            Corner.TOP_RIGHT to corners.topRight,
            Corner.BOTTOM_LEFT to corners.bottomLeft,
            Corner.BOTTOM_RIGHT to corners.bottomRight)
    for ((corner, point) in cornerList) {
      val screenPos = zoom.imageToScreen(point.x, point.y)
      val cornerScreen = Offset(screenPos.x.toFloat(), screenPos.y.toFloat())
      if ((offset - cornerScreen).getDistance() < hitRadius * zoom.zoom.toFloat()) {
        return i to corner
      }
    }
  }
  return null
}

private fun findBoxHit(offset: Offset, boxes: BoundingBoxList, zoom: ZoomController): Int {
  for (i in 0 until boxes.size()) {
    val box = boxes.boxes[i]
    val corners = box.corners
    val screenPoints =
        listOf(corners.topLeft, corners.topRight, corners.bottomRight, corners.bottomLeft).map { p
          ->
          val screen = zoom.imageToScreen(p.x, p.y)
          Offset(screen.x.toFloat(), screen.y.toFloat())
        }

    val minX = screenPoints.minOf { it.x }
    val maxX = screenPoints.maxOf { it.x }
    val minY = screenPoints.minOf { it.y }
    val maxY = screenPoints.maxOf { it.y }

    if (offset.x >= minX && offset.x <= maxX && offset.y >= minY && offset.y <= maxY) {
      return i
    }
  }
  return -1
}

private fun DrawScope.drawBoundingBox(
    box: BoundingBox,
    isSelected: Boolean,
    scale: Float,
    panX: Float,
    panY: Float
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
}

private fun DrawScope.drawFourPointPreview(
    fourPointState: FourPointState,
    scale: Float,
    panX: Float,
    panY: Float
) {
  val points = fourPointState.points
  val accentColor = Color(0xFFFF9800)

  fun toScreen(p: Point) = Offset((panX + p.x * scale).toFloat(), (panY + p.y * scale).toFloat())

  // Draw placed points with numbers
  points.forEachIndexed { index, point ->
    val screen = toScreen(point)
    // Draw circle
    drawCircle(accentColor, radius = 15f, center = screen)
    drawCircle(Color.White, radius = 12f, center = screen)
  }

  // Draw connecting lines
  if (points.size >= 2) {
    val path = Path()
    path.moveTo(toScreen(points[0]).x, toScreen(points[0]).y)
    for (i in 1 until points.size) {
      path.lineTo(toScreen(points[i]).x, toScreen(points[i]).y)
    }
    drawPath(path, accentColor, style = Stroke(width = 2f))
  }

  // Draw preview line closing to first point
  if (points.size >= 3) {
    val path = Path()
    path.moveTo(toScreen(points[0]).x, toScreen(points[0]).y)
    for (i in 1 until points.size) {
      path.lineTo(toScreen(points[i]).x, toScreen(points[i]).y)
    }
    path.lineTo(toScreen(points[0]).x, toScreen(points[0]).y)
    drawPath(path, accentColor.copy(alpha = 0.5f), style = Stroke(width = 2f))
  }
}

@Composable
fun ZoomControls(
    zoomController: ZoomController,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onFitToView: () -> Unit,
    modifier: Modifier = Modifier
) {
  Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)) {
          Row(
              horizontalArrangement = Arrangement.spacedBy(2.dp),
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(4.dp)) {
                IconButton(onClick = onZoomOut, modifier = Modifier.size(32.dp)) {
                  Icon(Icons.Default.ZoomOut, "Zoom out", Modifier.size(18.dp))
                }

                Text(
                    "${(zoomController.zoom * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(48.dp),
                    textAlign = TextAlign.Center)

                IconButton(onClick = onZoomIn, modifier = Modifier.size(32.dp)) {
                  Icon(Icons.Default.ZoomIn, "Zoom in", Modifier.size(18.dp))
                }

                IconButton(onClick = onFitToView, modifier = Modifier.size(32.dp)) {
                  Icon(Icons.Default.FitScreen, "Fit to view", Modifier.size(18.dp))
                }
              }
        }
  }
}

// 4-Point status indicator overlay
@Composable
internal fun FourPointStatusBar(
    state: FourPointState,
    onRemoveLast: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
  Surface(
      modifier = modifier,
      shape = RoundedCornerShape(8.dp),
      color = MaterialTheme.colorScheme.tertiaryContainer) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
              Text(
                  "Point ${state.points.size + 1} of 4",
                  style = MaterialTheme.typography.titleMedium)

              if (state.points.isNotEmpty()) {
                OutlinedButton(onClick = onRemoveLast, modifier = Modifier.height(32.dp)) {
                  Text("Undo", style = MaterialTheme.typography.labelSmall)
                }
              }

              if (state.canConfirm()) {
                Button(onClick = onConfirm, modifier = Modifier.height(32.dp)) {
                  Text("Confirm", style = MaterialTheme.typography.labelSmall)
                }
              }

              OutlinedButton(onClick = onCancel, modifier = Modifier.height(32.dp)) {
                Text("Cancel", style = MaterialTheme.typography.labelSmall)
              }
            }
      }
}

private fun createSampledImage(image: BufferedImage, scale: Double): BufferedImage? {
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
