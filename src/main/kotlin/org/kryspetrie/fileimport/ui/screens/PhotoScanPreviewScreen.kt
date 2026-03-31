package org.kryspetrie.fileimport.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import java.awt.image.BufferedImage
import org.kryspetrie.fileimport.domain.model.CornerType
import org.kryspetrie.fileimport.domain.model.DetectedPhoto
import org.kryspetrie.fileimport.domain.model.PhotoCorner
import org.kryspetrie.fileimport.domain.model.PhotoScanState
import org.kryspetrie.fileimport.domain.model.RotationAngle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoScanPreviewScreen(
    scanState: PhotoScanState,
    image: BufferedImage,
    onCornerMove: (photoId: String, corner: CornerType, x: Float, y: Float) -> Unit,
    onMovePhoto: (photoId: String, deltaX: Float, deltaY: Float) -> Unit = { _, _, _ -> },
    onAddPhoto: (DetectedPhoto) -> Unit,
    onRemovePhoto: (photoId: String) -> Unit,
    onSelectPhoto: (photoId: String?) -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
    onRescan: () -> Unit = {},
    onTogglePerspectiveCorrection: (photoId: String, enabled: Boolean) -> Unit = { _, _ -> },
    onRotateCW: (photoId: String) -> Unit = {},
    onRotateCCW: (photoId: String) -> Unit = {}
) {
  var imageSize by remember { mutableStateOf(IntSize.Zero) }
  var zoomLevel by remember { mutableFloatStateOf(1f) }
  var showFullScreenEditor by remember { mutableStateOf(false) }

  // Collect state flows
  val selectedPhotoId by scanState.selectedPhotoId.collectAsState()
  val selectedCorner by scanState.selectedCorner.collectAsState()
  val photos by scanState.currentPhotos.collectAsState()

  // Image dimensions
  val imageWidth = image.width.toFloat()
  val imageHeight = image.height.toFloat()

  // Calculate display parameters
  val displayParams = remember(imageSize, zoomLevel, imageWidth, imageHeight) {
    calculateDisplayParams(imageSize, imageWidth, imageHeight, zoomLevel)
  }

  // Pre-render cropped photos for the table
  val croppedPhotos by remember(photos, image) {
    derivedStateOf {
      photos.associate { photo -> photo.id to cropAndRotateImage(image, photo) }
    }
  }

  Column(modifier = Modifier.fillMaxSize()) {
    TopAppBar(
        title = { Text("Photo Scan Preview") },
        actions = {
          Text(
              "${scanState.currentIndex.value + 1}/${scanState.images.value.size}",
              style = MaterialTheme.typography.labelLarge,
              modifier = Modifier.padding(horizontal = 16.dp))
        })

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      // Image preview
      Box(
          modifier =
              Modifier
                  .weight(0.55f)
                  .fillMaxWidth()
                  .background(Color.DarkGray)
                  .onSizeChanged { imageSize = it }
      ) {
        PhotoCanvas(
            image = image,
            photos = photos,
            selectedPhotoId = selectedPhotoId,
            displayParams = displayParams,
            modifier = Modifier.fillMaxSize()
        )

        GestureLayer(
            photos = photos,
            selectedPhotoId = selectedPhotoId,
            selectedCorner = selectedCorner,
            displayParams = displayParams,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            onCornerMove = onCornerMove,
            onMovePhoto = onMovePhoto,
            onAddPhoto = onAddPhoto,
            onSelectPhoto = onSelectPhoto,
            onSelectCorner = { scanState.selectCorner(it) },
            onClearCorner = { scanState.selectCorner(null) }
        )

        Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              IconButton(onClick = { zoomLevel = (zoomLevel * 0.8f).coerceAtLeast(0.25f) }) {
                Icon(Icons.Default.ZoomOut, "Zoom out")
              }
              IconButton(onClick = { zoomLevel = (zoomLevel * 1.25f).coerceAtMost(4f) }) {
                Icon(Icons.Default.ZoomIn, "Zoom in")
              }
              IconButton(onClick = { zoomLevel = 1f }) {
                Icon(Icons.Default.FitScreen, "Fit to screen")
              }
              // Full-screen editor button
              if (selectedPhotoId != null) {
                IconButton(onClick = { showFullScreenEditor = true }) {
                  Icon(Icons.Default.OpenInFull, "Full screen editor")
                }
              }
            }

        // Photo count controls
        Row(
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
          Surface(
              shape = MaterialTheme.shapes.small,
              color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
          ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                  "Photos:",
                  style = MaterialTheme.typography.labelMedium
              )
              IconButton(
                  onClick = { scanState.decrementTargetPhotoCount() },
                  modifier = Modifier.size(28.dp)
              ) {
                Icon(Icons.Default.Remove, "Decrease photo count", modifier = Modifier.size(16.dp))
              }
              // Show editable field - "Auto" if null, otherwise number 1-20
              val targetDisplay = scanState.targetPhotoCount.value?.toString() ?: "Auto"
              OutlinedTextField(
                  value = targetDisplay,
                  onValueChange = { newValue ->
                    val count = newValue.toIntOrNull()
                    scanState.setTargetPhotoCount(count)
                  },
                  modifier = Modifier.width(70.dp),
                  textStyle = MaterialTheme.typography.labelLarge.copy(textAlign = TextAlign.Center),
                  singleLine = true
              )
              IconButton(
                  onClick = { scanState.incrementTargetPhotoCount() },
                  modifier = Modifier.size(28.dp)
              ) {
                Icon(Icons.Default.Add, "Increase photo count", modifier = Modifier.size(16.dp))
              }
              IconButton(
                  onClick = onRescan,
                  modifier = Modifier.size(28.dp)
              ) {
                Icon(Icons.Default.Refresh, "Rescan", modifier = Modifier.size(16.dp))
              }
            }
          }
        }
      }

      PhotoTable(
          photos = photos,
          croppedPhotos = croppedPhotos,
          selectedPhotoId = selectedPhotoId,
          onSelectPhoto = onSelectPhoto,
          onRemovePhoto = onRemovePhoto,
          onTogglePerspectiveCorrection = onTogglePerspectiveCorrection,
          onRotateCW = onRotateCW,
          onRotateCCW = onRotateCCW,
          modifier = Modifier.weight(0.45f)
      )
    }

    BottomControls(
        photos = photos,
        selectedPhotoId = selectedPhotoId,
        onAddPhoto = {
          val newPhoto = createDefaultPhoto(image.width, image.height)
          onAddPhoto(newPhoto)
          onSelectPhoto(newPhoto.id)
        },
        onRemovePhoto = { selectedPhotoId?.let { onRemovePhoto(it) } },
        onBack = onBack,
        onSkip = onSkip,
        onNext = onNext,
        currentImageName = scanState.currentImage?.file?.name ?: ""
    )

    // Full-screen editor dialog
    if (showFullScreenEditor && selectedPhotoId != null) {
      val selectedPhoto = photos.find { it.id == selectedPhotoId }
      if (selectedPhoto != null) {
        FullScreenPhotoEditor(
            image = image,
            photo = selectedPhoto,
            onDismiss = { showFullScreenEditor = false },
            onCornerMove = { corner, x, y -> onCornerMove(selectedPhotoId!!, corner, x, y) },
            onMovePhoto = { deltaX, deltaY -> onMovePhoto(selectedPhotoId!!, deltaX, deltaY) }
        )
      }
    }
  }
}

private data class DisplayParams(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float
)

private fun calculateDisplayParams(
    imageSize: IntSize,
    imageWidth: Float,
    imageHeight: Float,
    zoomLevel: Float
): DisplayParams {
  if (imageSize.width <= 0 || imageSize.height <= 0) {
    return DisplayParams(1f, 0f, 0f)
  }
  val scaleX = imageSize.width / imageWidth
  val scaleY = imageSize.height / imageHeight
  val fitScale = minOf(scaleX, scaleY) * zoomLevel
  val offsetX = (imageSize.width - imageWidth * fitScale) / 2
  val offsetY = (imageSize.height - imageHeight * fitScale) / 2
  return DisplayParams(fitScale, offsetX, offsetY)
}

@Composable
private fun PhotoTable(
    photos: List<DetectedPhoto>,
    croppedPhotos: Map<String, BufferedImage>,
    selectedPhotoId: String?,
    onSelectPhoto: (String?) -> Unit,
    onRemovePhoto: (String) -> Unit,
    onTogglePerspectiveCorrection: (String, Boolean) -> Unit,
    onRotateCW: (String) -> Unit,
    onRotateCCW: (String) -> Unit,
    modifier: Modifier = Modifier
) {
  if (photos.isEmpty()) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
      Text("No photos detected. Double-click on the image to add a photo.",
          style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    return
  }

  Card(modifier = modifier.fillMaxWidth()) {
    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("Detected Photos", style = MaterialTheme.typography.titleSmall)
        Text("${photos.size} photo(s)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      Spacer(Modifier.height(8.dp))
      HorizontalDivider()
      Spacer(Modifier.height(8.dp))
      Row(
          modifier = Modifier.fillMaxWidth().weight(1f).horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        photos.forEach { photo ->
          PhotoTableRow(
              photo = photo,
              croppedImage = croppedPhotos[photo.id],
              isSelected = photo.id == selectedPhotoId,
              onSelect = { onSelectPhoto(photo.id) },
              onRemove = { onRemovePhoto(photo.id) },
              onTogglePerspective = { enabled -> onTogglePerspectiveCorrection(photo.id, enabled) },
              onRotateCW = { onRotateCW(photo.id) },
              onRotateCCW = { onRotateCCW(photo.id) }
          )
        }
      }
    }
  }
}

@Composable
private fun PhotoTableRow(
    photo: DetectedPhoto,
    croppedImage: BufferedImage?,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
    onTogglePerspective: (Boolean) -> Unit,
    onRotateCW: () -> Unit,
    onRotateCCW: () -> Unit
) {
  val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
  val borderWidth = if (isSelected) 2.dp else 1.dp

  Card(
      modifier = Modifier.width(180.dp).fillMaxHeight().clickable(onClick = onSelect),
      border = androidx.compose.foundation.BorderStroke(borderWidth, borderColor),
      colors = CardDefaults.cardColors(
          containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
          else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
  ) {
    Column(modifier = Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Box(
          modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(Color.LightGray)
      ) {
        if (croppedImage != null) {
          val rotationDegrees = when (photo.rotation) {
            RotationAngle.NONE -> 0f
            RotationAngle.CW_90 -> 90f
            RotationAngle.CW_180 -> 180f
            RotationAngle.CCW_90 -> -90f
          }
          Image(
              bitmap = croppedImage.toComposeImageBitmap(),
              contentDescription = "Cropped photo preview",
              modifier = Modifier.fillMaxSize().graphicsLayer { this.rotationZ = rotationDegrees },
              contentScale = ContentScale.Fit
          )
        } else {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
          }
        }

        if (photo.rotation != RotationAngle.NONE) {
          Surface(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp), shape = RoundedCornerShape(4.dp),
              color = MaterialTheme.colorScheme.tertiaryContainer) {
            Text("${photo.rotation.degrees}°", modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
          }
        }
        if (!photo.applyPerspectiveCorrection) {
          Surface(modifier = Modifier.align(Alignment.TopStart).padding(4.dp), shape = RoundedCornerShape(4.dp),
              color = MaterialTheme.colorScheme.secondaryContainer) {
            Text("Crop", modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
          }
        }
      }

      Text("${photo.getWidth()}×${photo.getHeight()} px", style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant)

      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onTogglePerspective(!photo.applyPerspectiveCorrection) }, modifier = Modifier.size(32.dp)) {
          Icon(if (photo.applyPerspectiveCorrection) Icons.Default.CropFree else Icons.Default.Crop,
              contentDescription = "Toggle perspective", modifier = Modifier.size(18.dp),
              tint = if (photo.applyPerspectiveCorrection) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onRotateCCW, modifier = Modifier.size(32.dp)) {
          Icon(Icons.Default.RotateLeft, contentDescription = "Rotate CCW", modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onRotateCW, modifier = Modifier.size(32.dp)) {
          Icon(Icons.Default.RotateRight, contentDescription = "Rotate CW", modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
          Icon(Icons.Default.Delete, contentDescription = "Remove", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
        }
      }
    }
  }
}

@Composable
private fun PhotoCanvas(
    image: BufferedImage,
    photos: List<DetectedPhoto>,
    selectedPhotoId: String?,
    displayParams: DisplayParams,
    modifier: Modifier = Modifier
) {
  // Create a sampled display image for performance
  val displayImage = remember(image, displayParams.scale) {
    createSampledImage(image, displayParams.scale)
  }

  Canvas(modifier = modifier) {
    // Draw background
    drawRect(color = Color.LightGray, topLeft = Offset(displayParams.offsetX, displayParams.offsetY),
        size = androidx.compose.ui.geometry.Size(image.width * displayParams.scale, image.height * displayParams.scale))

    // Draw sampled image
    if (displayImage != null) {
      val imageBitmap = displayImage.toComposeImageBitmap()
      drawImage(imageBitmap, topLeft = Offset(displayParams.offsetX, displayParams.offsetY))
    }

    // Draw photo overlays
    photos.forEach { photo ->
      drawPhotoOverlay(photo, photo.id == selectedPhotoId, displayParams)
    }
  }
}

private fun createSampledImage(image: BufferedImage, scale: Float): BufferedImage? {
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

private fun DrawScope.drawPhotoOverlay(photo: DetectedPhoto, isSelected: Boolean, params: DisplayParams) {
  val outlineColor = when {
    photo.rotation != RotationAngle.NONE -> Color(0xFFFF9800)
    isSelected -> Color.Blue
    else -> Color.Green
  }
  val fillColor = outlineColor.copy(alpha = 0.2f)

  val tl = Offset(params.offsetX + photo.topLeft.x * params.scale, params.offsetY + photo.topLeft.y * params.scale)
  val tr = Offset(params.offsetX + photo.topRight.x * params.scale, params.offsetY + photo.topRight.y * params.scale)
  val bl = Offset(params.offsetX + photo.bottomLeft.x * params.scale, params.offsetY + photo.bottomLeft.y * params.scale)
  val br = Offset(params.offsetX + photo.bottomRight.x * params.scale, params.offsetY + photo.bottomRight.y * params.scale)

  drawPhotoOverlayShapes(photo, isSelected, outlineColor, fillColor, tl, tr, bl, br, params.scale)
}

private fun DrawScope.drawPhotoOverlayShapes(
    photo: DetectedPhoto,
    isSelected: Boolean,
    outlineColor: Color,
    fillColor: Color,
    tl: Offset,
    tr: Offset,
    bl: Offset,
    br: Offset,
    scale: Float
) {
  val path = Path().apply {
    moveTo(tl.x, tl.y)
    lineTo(tr.x, tr.y)
    lineTo(br.x, br.y)
    lineTo(bl.x, bl.y)
    close()
  }
  drawPath(path, fillColor, style = Fill)
  drawPath(path, outlineColor, style = Stroke(width = if (isSelected) 3f else 2f))

  // Draw center indicator when selected
  if (isSelected) {
    val centerX = (tl.x + tr.x + bl.x + br.x) / 4
    val centerY = (tl.y + tr.y + bl.y + br.y) / 4
    drawCircle(color = outlineColor.copy(alpha = 0.5f), radius = 8f, center = Offset(centerX, centerY))
    drawCircle(color = Color.White, radius = 4f, center = Offset(centerX, centerY))
  }

  val cornerRadius = if (isSelected) 10f else 6f
  listOf(tl, tr, bl, br).forEach { corner ->
    drawCircle(color = outlineColor, radius = cornerRadius, center = corner)
    drawCircle(color = Color.White, radius = cornerRadius - 2, center = corner)
  }
}

@Composable
private fun GestureLayer(
    photos: List<DetectedPhoto>,
    selectedPhotoId: String?,
    selectedCorner: CornerType?,
    displayParams: DisplayParams,
    imageWidth: Float,
    imageHeight: Float,
    onCornerMove: (photoId: String, corner: CornerType, x: Float, y: Float) -> Unit,
    onMovePhoto: (photoId: String, deltaX: Float, deltaY: Float) -> Unit,
    onAddPhoto: (DetectedPhoto) -> Unit,
    onSelectPhoto: (photoId: String?) -> Unit,
    onSelectCorner: (CornerType) -> Unit,
    onClearCorner: () -> Unit
) {
  val cornerFinder = remember { CornerFinder() }
  var draggedPhotoId by remember { mutableStateOf<String?>(null) }
  var lastDragPosition by remember { mutableStateOf(Offset.Zero) }

  Box(modifier = Modifier.fillMaxSize()) {
    // Tap layer for selection and double-tap for adding
    Box(
        modifier = Modifier.fillMaxSize().pointerInput(Unit) {
          detectTapGestures(
              onTap = { offset ->
                val clickedCorner = cornerFinder.findClosestCorner(offset, photos, displayParams)
                if (clickedCorner != null) {
                  onSelectPhoto(clickedCorner.first)
                  onSelectCorner(clickedCorner.second)
                } else {
                  val tappedPhoto = cornerFinder.findTappedPhoto(offset, photos, displayParams)
                  onSelectPhoto(tappedPhoto?.id)
                }
              },
              onDoubleTap = { offset ->
                val x = ((offset.x - displayParams.offsetX) / displayParams.scale).coerceIn(0f, imageWidth)
                val y = ((offset.y - displayParams.offsetY) / displayParams.scale).coerceIn(0f, imageHeight)
                onAddPhoto(createPhotoAtPosition(x, y, imageWidth, imageHeight))
              }
          )
        }
    )

    // Drag layer for corner manipulation and center-drag
    Box(
        modifier = Modifier.fillMaxSize().pointerInput(photos) {
          detectDragGestures(
              onDragStart = { offset ->
                // Check if clicking on a corner first
                val clickedCorner = cornerFinder.findClosestCorner(offset, photos, displayParams)
                if (clickedCorner != null) {
                  onSelectPhoto(clickedCorner.first)
                  onSelectCorner(clickedCorner.second)
                  draggedPhotoId = null  // Corner dragging
                } else {
                  // Check if clicking inside a photo (for center-drag)
                  val tappedPhoto = cornerFinder.findTappedPhoto(offset, photos, displayParams)
                  if (tappedPhoto != null) {
                    onSelectPhoto(tappedPhoto.id)
                    draggedPhotoId = tappedPhoto.id
                    lastDragPosition = offset
                  }
                }
              },
              onDrag = { change, _ ->
                if (selectedCorner != null && selectedPhotoId != null && draggedPhotoId == null) {
                  // Corner dragging
                  val x = ((change.position.x - displayParams.offsetX) / displayParams.scale).coerceIn(0f, imageWidth)
                  val y = ((change.position.y - displayParams.offsetY) / displayParams.scale).coerceIn(0f, imageHeight)
                  onCornerMove(selectedPhotoId, selectedCorner, x, y)
                } else if (draggedPhotoId != null) {
                  // Center dragging - move the entire photo
                  val id = draggedPhotoId!!
                  val deltaX = (change.position.x - lastDragPosition.x) / displayParams.scale
                  val deltaY = (change.position.y - lastDragPosition.y) / displayParams.scale
                  onMovePhoto(id, deltaX, deltaY)
                  lastDragPosition = change.position
                }
              },
              onDragEnd = {
                onClearCorner()
                draggedPhotoId = null
              }
          )
        }
    )
  }
}

private class CornerFinder {
  fun findClosestCorner(position: Offset, photos: List<DetectedPhoto>, params: DisplayParams): Pair<String, CornerType>? {
    var closest: Pair<String, CornerType>? = null
    var minDist = Float.MAX_VALUE
    photos.forEach { photo ->
      listOf(CornerType.TOP_LEFT to photo.topLeft, CornerType.TOP_RIGHT to photo.topRight,
          CornerType.BOTTOM_LEFT to photo.bottomLeft, CornerType.BOTTOM_RIGHT to photo.bottomRight).forEach { (cornerType, coord) ->
        val cornerScreenPos = Offset(params.offsetX + coord.x * params.scale, params.offsetY + coord.y * params.scale)
        val dist = (position - cornerScreenPos).getDistance()
        if (dist < minDist && dist < 30f * params.scale) {
          minDist = dist
          closest = photo.id to cornerType
        }
      }
    }
    return closest
  }

  fun findTappedPhoto(position: Offset, photos: List<DetectedPhoto>, params: DisplayParams): DetectedPhoto? {
    return photos.find { photo ->
      val corners = listOf(
          Offset(params.offsetX + photo.topLeft.x * params.scale, params.offsetY + photo.topLeft.y * params.scale),
          Offset(params.offsetX + photo.topRight.x * params.scale, params.offsetY + photo.topRight.y * params.scale),
          Offset(params.offsetX + photo.bottomLeft.x * params.scale, params.offsetY + photo.bottomLeft.y * params.scale),
          Offset(params.offsetX + photo.bottomRight.x * params.scale, params.offsetY + photo.bottomRight.y * params.scale)
      )
      val minX = corners.minOf { it.x }
      val maxX = corners.maxOf { it.x }
      val minY = corners.minOf { it.y }
      val maxY = corners.maxOf { it.y }
      position.x >= minX && position.x <= maxX && position.y >= minY && position.y <= maxY
    }
  }
}

@Composable
private fun BottomControls(
    photos: List<DetectedPhoto>,
    selectedPhotoId: String?,
    onAddPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit,
    currentImageName: String
) {
  Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 2.dp) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onAddPhoto, modifier = Modifier.weight(1f)) {
          Icon(Icons.Default.Add, null, Modifier.size(18.dp))
          Spacer(Modifier.width(4.dp))
          Text("Add Photo")
        }
        OutlinedButton(
            onClick = onRemovePhoto, enabled = selectedPhotoId != null, modifier = Modifier.weight(1f),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
          Icon(Icons.Default.Delete, null, Modifier.size(18.dp))
          Spacer(Modifier.width(4.dp))
          Text("Remove Selected")
        }
      }
      HorizontalDivider()
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) {
          Icon(Icons.Default.ArrowBack, null, Modifier.size(18.dp))
          Spacer(Modifier.width(4.dp))
          Text("Back")
        }
        Spacer(Modifier.weight(1f))
        Text(currentImageName, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onSkip) { Text("Skip") }
        Button(onClick = onNext, enabled = photos.isNotEmpty()) {
          Text("Next")
          Spacer(Modifier.width(4.dp))
          Icon(Icons.Default.ArrowForward, null, Modifier.size(18.dp))
        }
      }
    }
  }
}

private fun cropAndRotateImage(sourceImage: BufferedImage, photo: DetectedPhoto): BufferedImage {
  val bounds = photo.getBounds()
  val cropX = bounds.minX.coerceIn(0, sourceImage.width - 1)
  val cropY = bounds.minY.coerceIn(0, sourceImage.height - 1)
  val cropWidth = (bounds.maxX - bounds.minX).coerceIn(1, sourceImage.width - cropX)
  val cropHeight = (bounds.maxY - bounds.minY).coerceIn(1, sourceImage.height - cropY)

  val cropped = if (cropX + cropWidth <= sourceImage.width && cropY + cropHeight <= sourceImage.height) {
    sourceImage.getSubimage(cropX, cropY, cropWidth, cropHeight)
  } else sourceImage

  return if (photo.rotation != RotationAngle.NONE) {
    rotateImage(cropped, photo.rotation)
  } else cropped
}

private fun rotateImage(image: BufferedImage, rotation: RotationAngle): BufferedImage {
  val radians = rotation.radians
  val newWidth: Int
  val newHeight: Int

  if (rotation == RotationAngle.CW_90 || rotation == RotationAngle.CCW_90) {
    newWidth = image.height
    newHeight = image.width
  } else {
    val cos = kotlin.math.cos(kotlin.math.abs(radians)).toFloat()
    val sin = kotlin.math.sin(kotlin.math.abs(radians)).toFloat()
    newWidth = (image.width * cos + image.height * sin).toInt()
    newHeight = (image.width * sin + image.height * cos).toInt()
  }

  val rotated = BufferedImage(newWidth.coerceAtLeast(1), newHeight.coerceAtLeast(1), BufferedImage.TYPE_INT_RGB)
  val graphics = rotated.createGraphics()
  graphics.background = java.awt.Color.BLACK

  if (rotation == RotationAngle.CW_90) {
    graphics.translate(newWidth, 0)
    graphics.rotate(radians)
  } else if (rotation == RotationAngle.CCW_90) {
    graphics.translate(0, newHeight)
    graphics.rotate(radians)
  } else {
    graphics.translate(newWidth / 2.0, newHeight / 2.0)
    graphics.rotate(radians)
    graphics.translate(-image.width / 2.0, -image.height / 2.0)
  }
  graphics.drawImage(image, 0, 0, null)
  graphics.dispose()
  return rotated
}

private fun createDefaultPhoto(imageWidth: Int, imageHeight: Int): DetectedPhoto {
  val centerX = imageWidth / 2f
  val centerY = imageHeight / 2f
  val size = minOf(imageWidth, imageHeight) / 3f
  return DetectedPhoto(
      topLeft = PhotoCorner(centerX - size, centerY - size),
      topRight = PhotoCorner(centerX + size, centerY - size),
      bottomLeft = PhotoCorner(centerX - size, centerY + size),
      bottomRight = PhotoCorner(centerX + size, centerY + size))
}

private fun createPhotoAtPosition(x: Float, y: Float, imageWidth: Float, imageHeight: Float): DetectedPhoto {
  val size = minOf(imageWidth, imageHeight) / 4f
  val halfSize = size / 2
  return DetectedPhoto(
      topLeft = PhotoCorner((x - halfSize).coerceAtLeast(0f), (y - halfSize).coerceAtLeast(0f)),
      topRight = PhotoCorner((x + halfSize).coerceAtMost(imageWidth), (y - halfSize).coerceAtLeast(0f)),
      bottomLeft = PhotoCorner((x - halfSize).coerceAtLeast(0f), (y + halfSize).coerceAtMost(imageHeight)),
      bottomRight = PhotoCorner((x + halfSize).coerceAtMost(imageWidth), (y + halfSize).coerceAtMost(imageHeight))
  )
}

/**
 * Full-screen overlay for precise corner adjustment of a detected photo.
 * Uses a Box overlay to ensure it appears on top of other UI.
 */
@Composable
fun FullScreenPhotoEditor(
    image: BufferedImage,
    photo: DetectedPhoto,
    onDismiss: () -> Unit,
    onCornerMove: (CornerType, Float, Float) -> Unit,
    onMovePhoto: (Float, Float) -> Unit
) {
  var zoomLevel by remember { mutableFloatStateOf(2f) }
  var containerSize by remember { mutableStateOf(IntSize.Zero) }

  // Use a Box with fillMaxSize to overlay on top of everything
  Box(
      modifier = Modifier.fillMaxSize()
          .background(Color.Black.copy(alpha = 0.95f))
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      // Top bar with controls
      Surface(
          modifier = Modifier.fillMaxWidth(),
          color = Color.Black.copy(alpha = 0.8f)
      ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
              "Edit Photo Corners",
              style = MaterialTheme.typography.titleMedium,
              color = Color.White
          )

          Row(
              horizontalArrangement = Arrangement.spacedBy(4.dp),
              verticalAlignment = Alignment.CenterVertically
          ) {
            IconButton(onClick = { zoomLevel = (zoomLevel * 0.8f).coerceAtLeast(0.5f) }) {
              Icon(Icons.Default.ZoomOut, "Zoom out", tint = Color.White)
            }
            Text("${(zoomLevel * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = Color.White)
            IconButton(onClick = { zoomLevel = (zoomLevel * 1.25f).coerceAtMost(10f) }) {
              Icon(Icons.Default.ZoomIn, "Zoom in", tint = Color.White)
            }
            IconButton(onClick = { zoomLevel = 2f }) {
              Icon(Icons.Default.CenterFocusWeak, "Reset view", tint = Color.White)
            }
            Divider(
                modifier = Modifier.height(24.dp).padding(horizontal = 8.dp),
                color = Color.White.copy(alpha = 0.3f)
            )
            IconButton(onClick = onDismiss) {
              Icon(Icons.Default.Close, "Close", tint = Color.White)
            }
          }
        }
      }

      // Instructions
      Surface(
          modifier = Modifier.fillMaxWidth(),
          color = Color.Black.copy(alpha = 0.6f)
      ) {
        Text(
            "Drag corners to adjust • Drag inside box to move • Use zoom for precision",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.8f)
        )
      }

      // Image canvas with zoom
      Box(
          modifier = Modifier.fillMaxSize().background(Color.Black)
              .onSizeChanged { containerSize = it }
      ) {
        val fullScreenParams = remember(containerSize, zoomLevel, image.width, image.height) {
          calculateFullScreenParams(containerSize, image.width.toFloat(), image.height.toFloat(), zoomLevel)
        }

        FullScreenCanvas(
            image = image,
            photo = photo,
            params = fullScreenParams,
            onCornerMove = onCornerMove,
            onMovePhoto = onMovePhoto
        )
      }
    }
  }
}

private data class FullScreenParams(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float
)

private fun calculateFullScreenParams(
    containerSize: IntSize,
    imageWidth: Float,
    imageHeight: Float,
    zoomLevel: Float
): FullScreenParams {
  if (containerSize.width <= 0 || containerSize.height <= 0) {
    return FullScreenParams(1f, 0f, 0f)
  }

  val baseScaleX = containerSize.width / imageWidth
  val baseScaleY = containerSize.height / imageHeight
  val fitScale = minOf(baseScaleX, baseScaleY)
  val scale = fitScale * zoomLevel

  val imageDisplayWidth = imageWidth * scale
  val imageDisplayHeight = imageHeight * scale

  val offsetX = (containerSize.width - imageDisplayWidth) / 2
  val offsetY = (containerSize.height - imageDisplayHeight) / 2

  return FullScreenParams(scale, offsetX, offsetY)
}

@Composable
private fun FullScreenCanvas(
    image: BufferedImage,
    photo: DetectedPhoto,
    params: FullScreenParams,
    onCornerMove: (CornerType, Float, Float) -> Unit,
    onMovePhoto: (Float, Float) -> Unit
) {
  var draggedCorner by remember { mutableStateOf<CornerType?>(null) }
  var isDraggingPhoto by remember { mutableStateOf(false) }
  var lastPosition by remember { mutableStateOf(Offset.Zero) }

  Canvas(
      modifier = Modifier.fillMaxSize()
          .pointerInput(photo) {
            detectDragGestures(
                onDragStart = { offset ->
                  lastPosition = offset
                  // Check for corner hit first
                  val corner = findCornerAtPosition(offset, photo, params)
                  if (corner != null) {
                    draggedCorner = corner
                  } else if (isInsidePhoto(offset, photo, params)) {
                    isDraggingPhoto = true
                  }
                },
                onDrag = { change, _ ->
                  if (draggedCorner != null) {
                    val x = ((change.position.x - params.offsetX) / params.scale).coerceIn(0f, image.width.toFloat())
                    val y = ((change.position.y - params.offsetY) / params.scale).coerceIn(0f, image.height.toFloat())
                    onCornerMove(draggedCorner!!, x, y)
                  } else if (isDraggingPhoto) {
                    val deltaX = (change.position.x - lastPosition.x) / params.scale
                    val deltaY = (change.position.y - lastPosition.y) / params.scale
                    onMovePhoto(deltaX, deltaY)
                    lastPosition = change.position
                  }
                },
                onDragEnd = {
                  draggedCorner = null
                  isDraggingPhoto = false
                }
            )
          }
  ) {
    // Draw background
    drawRect(
        color = Color.DarkGray,
        topLeft = Offset(params.offsetX, params.offsetY),
        size = androidx.compose.ui.geometry.Size(image.width * params.scale, image.height * params.scale)
    )

    // Draw image
    val imageBitmap = image.toComposeImageBitmap()
    drawImage(imageBitmap, topLeft = Offset(params.offsetX, params.offsetY))

    // Draw photo overlay with handles
    drawFullScreenPhotoOverlay(photo, true, params)
  }
}

private fun DrawScope.drawFullScreenPhotoOverlay(photo: DetectedPhoto, isSelected: Boolean, params: FullScreenParams) {
  val outlineColor = when {
    photo.rotation != RotationAngle.NONE -> Color(0xFFFF9800)
    isSelected -> Color.Blue
    else -> Color.Green
  }
  val fillColor = outlineColor.copy(alpha = 0.2f)

  val tl = Offset(params.offsetX + photo.topLeft.x * params.scale, params.offsetY + photo.topLeft.y * params.scale)
  val tr = Offset(params.offsetX + photo.topRight.x * params.scale, params.offsetY + photo.topRight.y * params.scale)
  val bl = Offset(params.offsetX + photo.bottomLeft.x * params.scale, params.offsetY + photo.bottomLeft.y * params.scale)
  val br = Offset(params.offsetX + photo.bottomRight.x * params.scale, params.offsetY + photo.bottomRight.y * params.scale)

  drawPhotoOverlayShapes(photo, isSelected, outlineColor, fillColor, tl, tr, bl, br, params.scale)
}

private fun findCornerAtPosition(position: Offset, photo: DetectedPhoto, params: FullScreenParams): CornerType? {
  val corners = listOf(
      CornerType.TOP_LEFT to photo.topLeft,
      CornerType.TOP_RIGHT to photo.topRight,
      CornerType.BOTTOM_LEFT to photo.bottomLeft,
      CornerType.BOTTOM_RIGHT to photo.bottomRight
  )

  val hitRadius = 25f
  for ((cornerType, corner) in corners) {
    val screenPos = Offset(params.offsetX + corner.x * params.scale, params.offsetY + corner.y * params.scale)
    if ((position - screenPos).getDistance() < hitRadius) {
      return cornerType
    }
  }
  return null
}

private fun isInsidePhoto(position: Offset, photo: DetectedPhoto, params: FullScreenParams): Boolean {
  val corners = listOf(
      Offset(params.offsetX + photo.topLeft.x * params.scale, params.offsetY + photo.topLeft.y * params.scale),
      Offset(params.offsetX + photo.topRight.x * params.scale, params.offsetY + photo.topRight.y * params.scale),
      Offset(params.offsetX + photo.bottomLeft.x * params.scale, params.offsetY + photo.bottomLeft.y * params.scale),
      Offset(params.offsetX + photo.bottomRight.x * params.scale, params.offsetY + photo.bottomRight.y * params.scale)
  )

  val minX = corners.minOf { it.x }
  val maxX = corners.maxOf { it.x }
  val minY = corners.minOf { it.y }
  val maxY = corners.maxOf { it.y }

  return position.x >= minX && position.x <= maxX && position.y >= minY && position.y <= maxY
}
