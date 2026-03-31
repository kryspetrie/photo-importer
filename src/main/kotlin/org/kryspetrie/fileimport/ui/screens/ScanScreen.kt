package org.kryspetrie.fileimport.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.kryspetrie.fileimport.application.ScanService
import org.kryspetrie.fileimport.domain.model.*
import org.kryspetrie.fileimport.domain.model.DetectedPhoto
import org.kryspetrie.fileimport.domain.model.PhotoCorner
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration
import org.kryspetrie.fileimport.domain.port.ImageRepositoryPort
import org.kryspetrie.fileimport.domain.port.NamingPort

/**
 * Screen for photo scan preview and editing.
 *
 * Shows the detected photos within a scanned image, allows corner manipulation, metadata override,
 * and exports individual photos.
 */
@Composable
fun ScanScreen(
    filepaths: List<String>,
    destinationPath: String,
    onFinished: () -> Unit,
    scanService: ScanService,
    namingPort: NamingPort,
    imageRepository: ImageRepositoryPort
) {
  var currentFileIndex by remember { mutableStateOf(0) }
  var currentDetectedPhotos by remember { mutableStateOf<List<DetectedPhoto>>(emptyList()) }
  var exportProgress by remember { mutableStateOf(0) }
  var exportTotal by remember { mutableStateOf(0) }

  // Load detected photos when file changes
  LaunchedEffect(currentFileIndex) {
    if (currentFileIndex < filepaths.size) {
      val detectedPhotos = scanService.detectPhotos(filepaths[currentFileIndex])
      currentDetectedPhotos = detectedPhotos
    }
  }

  val currentFilePath = if (currentFileIndex < filepaths.size) filepaths[currentFileIndex] else ""
  val currentFile = if (currentFilePath.isNotEmpty()) File(currentFilePath) else null

  // Current image for display
  val currentImage: BufferedImage? =
      if (currentFile != null && currentFile.exists()) {
        try {
          ImageIO.read(currentFile)
        } catch (_: Exception) {
          null
        }
      } else {
        null
      }

  // Export all photos
  val exportAllPhotos = {
    if (currentFile != null && currentFileIndex < filepaths.size) {
      exportTotal = currentDetectedPhotos.size
      exportProgress = 0

      currentDetectedPhotos.forEachIndexed { index, photo ->
        val outputFile =
            scanService.exportPhoto(
                BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB),
                destinationPath,
                currentFile,
                index + 1,
                photo.configuration)
        exportProgress = index + 1
      }
    }
  }

  val nextFile: () -> Unit = {
    if (currentFileIndex < filepaths.size - 1) {
      currentFileIndex++
    } else {
      onFinished()
    }
  }

  val skipFile: () -> Unit = { currentFileIndex++ }

  val addPhotoDetection = {
    val bounds = currentImage?.let { img -> img.width to img.height } ?: (1000 to 1000)
    val newPhoto =
        DetectedPhoto(
            topLeft = PhotoCorner.create(100, 100),
            topRight = PhotoCorner.create(bounds.first - 100, 100),
            bottomLeft = PhotoCorner.create(100, bounds.second - 100),
            bottomRight = PhotoCorner.create(bounds.first - 100, bounds.second - 100))
    currentDetectedPhotos = currentDetectedPhotos + newPhoto
  }

  val removePhotoDetection = { index: Int ->
    currentDetectedPhotos = currentDetectedPhotos.filterIndexed { i, _ -> i != index }
  }

  // Corner dragging state
  var draggedCornerPhotoIndex by remember { mutableStateOf<Int?>(null) }
  var draggedCornerName by remember { mutableStateOf<String?>(null) }

  val updatePhotoCorner = { index: Int, corner: String, x: Float, y: Float ->
    val photo = currentDetectedPhotos[index]
    val updatedPhoto =
        when (corner) {
          "topLeft" -> photo.copy(topLeft = PhotoCorner.create(x.toInt(), y.toInt()))
          "topRight" -> photo.copy(topRight = PhotoCorner.create(x.toInt(), y.toInt()))
          "bottomLeft" -> photo.copy(bottomLeft = PhotoCorner.create(x.toInt(), y.toInt()))
          "bottomRight" -> photo.copy(bottomRight = PhotoCorner.create(x.toInt(), y.toInt()))
          else -> photo
        }
    currentDetectedPhotos =
        currentDetectedPhotos.mapIndexed { i, p -> if (i == index) updatedPhoto else p }
  }

  // Metadata override handlers
  val updatePhotoMetadata = { index: Int, config: PhotoScanConfiguration ->
    val photo = currentDetectedPhotos[index]
    currentDetectedPhotos =
        currentDetectedPhotos.mapIndexed { i, p ->
          if (i == index) photo.copy(configuration = config) else p
        }
  }

  // Photo editing modal
  var editingPhotoIndex by remember { mutableStateOf<Int?>(null) }

  editingPhotoIndex?.let { index ->
    val photo = currentDetectedPhotos.getOrNull(index) ?: return@let
    EditPhotoDialog(
        photo = photo,
        onClose = { editingPhotoIndex = null },
        onConfigChange = { config -> updatePhotoMetadata(index, config) })
  }

  // Main UI
  if (currentImage == null) {
    if (currentFileIndex >= filepaths.size) {
      Text("All scans processed!")
      Button(onClick = onFinished) { Text("Finish") }
    } else {
      Text("Could not load image: ${currentFilePath}")
    }
    return
  }

  // Image preview with corner drag support
  var imagePreviewBounds by remember { mutableStateOf(Rect.Zero) }

  Card(modifier = Modifier.fillMaxWidth()) {
    Box(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
      // Image display with corner drag
      Box(
          modifier =
              Modifier.fillMaxWidth()
                  .aspectRatio(currentImage.width.toFloat() / currentImage.height.toFloat())
                  .onGloballyPositioned { layoutCoordinates ->
                    imagePreviewBounds =
                        Rect(
                            0f,
                            0f,
                            layoutCoordinates.size.width.toFloat(),
                            layoutCoordinates.size.height.toFloat())
                  }
                  .pointerInput(currentDetectedPhotos) {
                    detectDragGestures { change, _ ->
                      if (draggedCornerPhotoIndex != null && draggedCornerName != null) {
                        val x = change.position.x
                        val y = change.position.y
                        updatePhotoCorner(draggedCornerPhotoIndex!!, draggedCornerName!!, x, y)
                      }
                    }
                  }
                  .clip(RoundedCornerShape(8.dp))
                  .background(Color.Gray)
                  .clickable { /* Open for detailed editing */ }) {
            // Placeholder for actual image rendering
            if (currentImage != null) {
              Text(
                  "Image: ${currentFile?.name} (${currentImage.width}x${currentImage.height})",
                  modifier = Modifier.align(Alignment.Center))
            }
            // Draw corner circles for each photo
            currentDetectedPhotos.forEachIndexed { i, photo ->
              DrawCorners(
                  photo.topLeft.x,
                  photo.topLeft.y,
                  photo.topRight.x,
                  photo.topRight.y,
                  photo.bottomLeft.x,
                  photo.bottomLeft.y,
                  photo.bottomRight.x,
                  photo.bottomRight.y,
                  imagePreviewBounds.width,
                  imagePreviewBounds.height,
                  draggedCornerPhotoIndex == i,
                  {
                    draggedCornerPhotoIndex = i
                    draggedCornerName = "topLeft"
                  },
                  {
                    draggedCornerPhotoIndex = i
                    draggedCornerName = "topRight"
                  },
                  {
                    draggedCornerPhotoIndex = i
                    draggedCornerName = "bottomLeft"
                  },
                  {
                    draggedCornerPhotoIndex = i
                    draggedCornerName = "bottomRight"
                  })
            }
          }
    }
  }

  // Photo list
  Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
    Column(modifier = Modifier.padding(8.dp)) {
      Text(
          "Detected Photos: ${currentDetectedPhotos.size}",
          style = MaterialTheme.typography.titleMedium)
      currentDetectedPhotos.forEachIndexed { i, photo ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { editingPhotoIndex = i }) {
              Text(
                  "Photo ${i + 1}",
                  style = MaterialTheme.typography.bodyMedium,
                  modifier = Modifier.weight(1f))
              Row {
                Button(
                    onClick = { removePhotoDetection(i) }, contentPadding = PaddingValues(4.dp)) {
                      Icon(Icons.Default.Delete, "Remove", Modifier.size(16.dp))
                    }
              }
            }
      }
    }
  }

  // Export progress
  if (exportTotal > 0) {
    Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
      Column(modifier = Modifier.padding(8.dp)) {
        Text(
            "Export Progress: $exportProgress / $exportTotal",
            style = MaterialTheme.typography.bodyMedium)
        LinearProgressIndicator(
            progress = { if (exportTotal > 0) exportProgress.toFloat() / exportTotal else 0f })
      }
    }
  }

  // Action buttons
  Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        OutlinedButton(
            onClick = { currentDetectedPhotos = emptyList() }, modifier = Modifier.weight(1f)) {
              Icon(Icons.Default.Repeat, "Re-detect")
              Text("Re-detect")
            }
        OutlinedButton(onClick = addPhotoDetection, modifier = Modifier.weight(1f)) {
          Icon(Icons.Default.Add, "Add")
          Text("Add Photo")
        }
        OutlinedButton(onClick = skipFile, modifier = Modifier.weight(1f)) { Text("Skip") }
        OutlinedButton(onClick = exportAllPhotos, modifier = Modifier.weight(1f)) {
          Icon(Icons.Default.Upload, "Export")
          Text("Export All")
        }
      }
  Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        OutlinedButton(
            onClick = nextFile,
            enabled = currentFileIndex < filepaths.size - 1,
            modifier = Modifier.weight(1f)) {
              Icon(Icons.Default.NavigateNext, "Next")
              Text("Next")
            }
      }
}

/** Draw corner indicators on the photo. */
@Composable
fun DrawCorners(
    tlX: Float,
    tlY: Float,
    trX: Float,
    trY: Float,
    blX: Float,
    blY: Float,
    brX: Float,
    brY: Float,
    width: Float,
    height: Float,
    isDragging: Boolean,
    onTLClick: () -> Unit,
    onTRClick: () -> Unit,
    onBLClick: () -> Unit,
    onBRClick: () -> Unit
) {
  // Draw corner circles for dragging
  Box(
      modifier =
          Modifier.size(12.dp)
              .offset { IntOffset(x = tlX.toInt(), y = tlY.toInt()) }
              .background(Color.Red)
              .clickable { onTLClick() })
  Box(
      modifier =
          Modifier.size(12.dp)
              .offset { IntOffset(x = trX.toInt(), y = trY.toInt()) }
              .background(Color.Red)
              .clickable { onTRClick() })
  Box(
      modifier =
          Modifier.size(12.dp)
              .offset { IntOffset(x = blX.toInt(), y = blY.toInt()) }
              .background(Color.Red)
              .clickable { onBLClick() })
  Box(
      modifier =
          Modifier.size(12.dp)
              .offset { IntOffset(x = brX.toInt(), y = brY.toInt()) }
              .background(Color.Red)
              .clickable { onBRClick() })
}

/** Dialog for editing photo detection and metadata. */
@Composable
fun EditPhotoDialog(
    photo: DetectedPhoto,
    onClose: () -> Unit,
    onConfigChange: (PhotoScanConfiguration) -> Unit
) {
  var originalDate by remember { mutableStateOf(photo.configuration.originalDateOverride ?: "") }
  var originalYear by remember { mutableStateOf(photo.configuration.originalYearOverride ?: "") }
  var originalMonth by remember { mutableStateOf(photo.configuration.originalMonthOverride ?: "") }
  var tags by remember { mutableStateOf(photo.configuration.tags) }
  var notes by remember { mutableStateOf(photo.configuration.notes) }

  AlertDialog(
      onDismissRequest = onClose,
      title = { Text("Edit Photo") },
      text = {
        Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
          // Metadata overrides
          OutlinedTextField(
              value = originalDate,
              onValueChange = { originalDate = it },
              label = { Text("Original Date") },
              placeholder = { Text("YYYY-MM-DD HH:MM:SS") },
              modifier = Modifier.fillMaxWidth())

          OutlinedTextField(
              value = originalYear,
              onValueChange = { originalYear = it },
              label = { Text("Year") },
              placeholder = { Text("YYYY") },
              modifier = Modifier.fillMaxWidth())

          OutlinedTextField(
              value = originalMonth,
              onValueChange = { originalMonth = it },
              label = { Text("Month") },
              placeholder = { Text("MM") },
              modifier = Modifier.fillMaxWidth())

          OutlinedTextField(
              value = tags,
              onValueChange = { tags = it },
              label = { Text("Tags (comma-separated)") },
              placeholder = { Text("tag1, tag2") },
              modifier = Modifier.fillMaxWidth())

          OutlinedTextField(
              value = notes,
              onValueChange = { notes = it },
              label = { Text("Notes") },
              placeholder = { Text("Additional notes") },
              modifier = Modifier.fillMaxWidth())

          // Photo corners display
          Text(
              "Corners:",
              style = MaterialTheme.typography.titleSmall,
              modifier = Modifier.padding(top = 16.dp))
          Text(
              "Drag corners on the image to adjust.",
              style = MaterialTheme.typography.bodySmall,
              color = Color(0xFF666666))

          Text(
              "Top Left: (${photo.topLeft.x.toInt()}, ${photo.topLeft.y.toInt()})",
              style = MaterialTheme.typography.bodySmall)
          Text(
              "Top Right: (${photo.topRight.x.toInt()}, ${photo.topRight.y.toInt()})",
              style = MaterialTheme.typography.bodySmall)
          Text(
              "Bottom Left: (${photo.bottomLeft.x.toInt()}, ${photo.bottomLeft.y.toInt()})",
              style = MaterialTheme.typography.bodySmall)
          Text(
              "Bottom Right: (${photo.bottomRight.x.toInt()}, ${photo.bottomRight.y.toInt()})",
              style = MaterialTheme.typography.bodySmall)
          Text(
              "Size: ${photo.getBounds().getWidth()}x${photo.getBounds().getHeight()} px",
              style = MaterialTheme.typography.bodySmall)
        }
      },
      confirmButton = {
        Button(
            onClick = {
              val newConfig =
                  photo.configuration.copy(
                      originalDateOverride = if (originalDate.isNotBlank()) originalDate else null,
                      originalYearOverride = if (originalYear.isNotBlank()) originalYear else null,
                      originalMonthOverride =
                          if (originalMonth.isNotBlank()) originalMonth else null,
                      tags = tags,
                      notes = notes)
              onConfigChange(newConfig)
              onClose()
            }) {
              Text("Save")
            }
      },
      dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } })
}
