package org.kryspetrie.fileimport.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.kryspetrie.fileimport.application.ScanService
import org.kryspetrie.fileimport.domain.model.ExportProgress
import org.kryspetrie.fileimport.domain.model.ImageFile
import org.kryspetrie.fileimport.domain.model.ImportConfiguration
import org.kryspetrie.fileimport.domain.model.PhotoScanState
import org.kryspetrie.fileimport.domain.model.PhotoScanState.Step

/**
 * Animated corner detection loading indicator.
 *
 * Creates a fun animation showing corners expanding and contracting, simulating the computer vision
 * detecting photo boundaries.
 */
@Composable
fun Loader(modifier: Modifier = Modifier, color: Color = Color(90, 164, 169)) {
  val infiniteTransition = rememberInfiniteTransition(label = "LoaderTransition")

  // Master timeline: 0.0f to 1.0f over 1000ms (1 second)
  val time by
      infiniteTransition.animateFloat(
          initialValue = 0f,
          targetValue = 1f,
          animationSpec =
              infiniteRepeatable(
                  animation = tween(1000, easing = LinearEasing), repeatMode = RepeatMode.Restart),
          label = "TimeProgress")

  // l32-1: Controls the expansion of the entire loader
  val currentSize =
      when {
        time <= 0.35f -> lerp(135f, 195f, time / 0.35f) // 0% to 35%: Expand
        time <= 0.65f -> 195f // 35% to 65%: Hold
        else -> lerp(195f, 135f, (time - 0.65f) / 0.35f) // 65% to 100%: Contract
      }

  // l32-2: Controls the square shifting. They shift only between 40% and 60% of the loop.
  val shiftProgress =
      when {
        time <= 0.40f -> 0f
        time <= 0.60f -> (time - 0.40f) / 0.20f
        else -> 1f
      }

  // The 8 perimeter positions mapped from the CSS percentages
  val outerPositions =
      listOf(
          Offset(0f, 0f), // Top Left
          Offset(0f, 0.5f), // Center Left
          Offset(0f, 1f), // Bottom Left
          Offset(0.5f, 1f), // Bottom Center
          Offset(1f, 1f), // Bottom Right
          Offset(1f, 0.5f), // Center Right
          Offset(1f, 0f), // Top Right
          Offset(0.5f, 0f) // Top Center
          )

  Box(modifier = modifier, contentAlignment = Alignment.Center) {
    Canvas(modifier = Modifier.size(currentSize.dp)) {
      val dotSize = 48.dp.toPx()

      // CSS background-position calculates percentages based on (Container Size - Item Size)
      val availableWidth = size.width - dotSize
      val availableHeight = size.height - dotSize

      // 1. Draw the static center dot (50% 50%)
      drawRect(
          color = color,
          topLeft = Offset(availableWidth * 0.5f, availableHeight * 0.5f),
          size = Size(dotSize, dotSize))

      // 2. Draw the 8 animated outer dots
      for (i in 0 until 8) {
        val start = outerPositions[i]
        val end = outerPositions[(i + 1) % 8]

        val currentXProgress = lerp(start.x, end.x, shiftProgress)
        val currentYProgress = lerp(start.y, end.y, shiftProgress)

        drawRect(
            color = color,
            topLeft =
                Offset(
                    x = currentXProgress * availableWidth, y = currentYProgress * availableHeight),
            size = Size(dotSize, dotSize))
      }
    }
  }
}

/**
 * Photo Scan Screen.
 *
 * Main screen for the photo scan workflow that:
 * 1. Displays detected photos from scanned images
 * 2. Allows corner editing via PhotoScanPreviewScreen
 * 3. Allows metadata editing via PhotoScanMetadataScreen
 * 4. Exports photos to the destination folder
 *
 * ## Workflow Steps
 * 1. **Detecting**: Loading image and detecting photo corners
 * 2. **Corner Editing**: Adjust detected corners on the preview
 * 3. **Metadata Editing**: Override EXIF metadata per photo
 * 4. **Exporting**: Writing corrected photos to destination
 * 5. **Complete**: All photos processed, showing summary
 *
 * @param sourcePath Source folder containing scanned images
 * @param destinationPath Destination folder for exported photos
 * @param configuration Import configuration with naming patterns
 * @param onComplete Callback when all photos have been processed
 * @param onCancel Callback when user cancels the workflow
 * @param selectedFiles Optional list of pre-selected files to process
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoScanScreen(
    sourcePath: String,
    destinationPath: String,
    configuration: ImportConfiguration,
    onComplete: () -> Unit,
    onCancel: () -> Unit,
    selectedFiles: List<ImageFile>? = null
) {
  val scanService: ScanService = koinInject()

  // Scan state
  val scanState = remember { PhotoScanState() }

  // Loading state
  var loadingMessage by remember { mutableStateOf("Initializing...") }

  // Initialize with image files - either selected files or all files from source path
  LaunchedEffect(sourcePath, selectedFiles) {
    val imageFiles: List<File>

    if (selectedFiles != null && selectedFiles.isNotEmpty()) {
      // Use the selected files
      imageFiles = selectedFiles.map { it.file }.filter { it.exists() }
    } else {
      // Read all image files from source directory
      val sourceDir = File(sourcePath)
      val imageExtensions = setOf("jpg", "jpeg", "png", "tiff", "tif", "bmp")
      imageFiles =
          sourceDir
              .listFiles()
              ?.filter { file -> imageExtensions.contains(file.extension.lowercase()) }
              ?.sortedBy { it.name } ?: emptyList()
    }

    if (imageFiles.isEmpty()) {
      onCancel()
      return@LaunchedEffect
    }

    scanState.initialize(imageFiles)
    loadingMessage = "Loading images..."
    CoroutineScope(Dispatchers.Main).launch {
      loadCurrentImage(
          scanState,
          scanService,
          onStatus = { loadingMessage = it },
          onError = { msg -> loadingMessage = msg ?: "Unknown error" })
    }
  }

  Scaffold(
      topBar = {
        TopAppBar(
            title = { Text("Photo Scan") },
            navigationIcon = {
              IconButton(onClick = onCancel) { Icon(Icons.Default.Close, "Cancel") }
            },
            actions = {
              // Progress indicator
              if (scanState.images.value.isNotEmpty()) {
                LinearProgressIndicator(
                    progress = { scanState.progressPercent },
                    modifier = Modifier.width(100.dp).padding(end = 16.dp))
                Text(
                    "${scanState.processedCount.value} processed",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(end = 16.dp))
              }
            })
      },
      content = { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
          when (scanState.step.value) {
            Step.DETECTING -> {
              Column(
                  modifier = Modifier.fillMaxSize(),
                  verticalArrangement = Arrangement.Center,
                  horizontalAlignment = Alignment.CenterHorizontally) {
                    Loader(
                        modifier = Modifier.size(80.dp), color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        loadingMessage,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Analyzing with Computer Vision...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
            }

            Step.COMPLETE -> {
              PhotoScanCompleteScreen(
                  processedCount = scanState.processedCount.value,
                  extractedCount = scanState.extractedCount.value,
                  errors = scanState.errors.value,
                  destinationPath = destinationPath,
                  onFinish = { onCancel() })
            }

            Step.EXPORTING -> {
              PhotoScanExportingScreen(
                  progress = scanState.exportProgress.value,
                  totalPhotos = scanState.currentPhotos.value.size,
                  currentImage = scanState.currentImage?.file?.name ?: "")
            }

            Step.METADATA_EDITING -> {
              PhotoScanMetadataScreen(
                  photos = scanState.currentPhotos.value,
                  selectedPhotoId = scanState.selectedPhotoId.value,
                  onPhotoSelect = { scanState.selectPhoto(it) },
                  onConfigurationChange = { photoId, config ->
                    scanState.updatePhotoConfiguration(photoId, config)
                  },
                  onApplyToAll = { config ->
                    scanState.currentPhotos.value.forEach { photo ->
                      scanState.updatePhotoConfiguration(photo.id, config)
                    }
                  },
                  onBack = { scanState.step.value = Step.CORNER_EDITING },
                  onExport = {
                    exportCurrentImage(scanState, scanService, destinationPath, onComplete)
                  })
            }

            Step.CORNER_EDITING -> {
              val currentImage = scanState.currentImage
              if (currentImage?.image != null) {
                PhotoScanPreviewScreen(
                    scanState = scanState,
                    image = currentImage.image,
                    onCornerMove = { photoId, corner, x, y ->
                      scanState.updatePhotoCorner(photoId, corner, x, y)
                    },
                    onMovePhoto = { photoId, deltaX, deltaY ->
                      scanState.movePhoto(photoId, deltaX, deltaY)
                    },
                    onAddPhoto = { photo ->
                      scanState.addPhoto(photo)
                      scanState.selectPhoto(photo.id)
                    },
                    onRemovePhoto = { photoId -> scanState.removePhoto(photoId) },
                    onSelectPhoto = { photoId -> scanState.selectPhoto(photoId) },
                    onNext = {
                      if (scanState.currentPhotos.value.isNotEmpty()) {
                        scanState.proceedToMetadata()
                      } else {
                        scanState.skipImage()
                      }
                    },
                    onSkip = { scanState.skipImage() },
                    onBack = {
                      if (scanState.currentIndex.value > 0) {
                        scanState.skipImage()
                      } else {
                        onCancel()
                      }
                    },
                    onRescan = {
                      // Re-launch detection
                      CoroutineScope(Dispatchers.Main).launch {
                        rescanCurrentImage(scanState, scanService)
                      }
                    },
                    onTogglePerspectiveCorrection = { photoId, enabled ->
                      scanState.togglePerspectiveCorrection(photoId, enabled)
                    },
                    onRotateCW = { photoId -> scanState.rotatePhotoCW(photoId) },
                    onRotateCCW = { photoId -> scanState.rotatePhotoCCW(photoId) })
              } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                      CircularProgressIndicator()
                      Spacer(modifier = Modifier.height(16.dp))
                      Text("Loading ${currentImage?.file?.name ?: "..."}")
                    }
              }
            }

            Step.SKIPPED -> {
              // Handle SKIPPED state - should transition quickly
              Column(
                  modifier = Modifier.fillMaxSize(),
                  verticalArrangement = Arrangement.Center,
                  horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Processing...")
                  }
            }
          }
        }
      })
}

/** Exporting progress screen. */
@Composable
private fun PhotoScanExportingScreen(
    progress: ExportProgress,
    totalPhotos: Int,
    currentImage: String
) {
  Column(
      modifier = Modifier.fillMaxSize().padding(32.dp),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Default.Download,
            null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(24.dp))
        Text("Exporting Photos", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Processing: $currentImage",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(24.dp))
        LinearProgressIndicator(
            progress = { progress.percent }, modifier = Modifier.fillMaxWidth(0.8f))
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "${progress.processedPhotos}/$totalPhotos photos",
            style = MaterialTheme.typography.labelMedium)
      }
}

/** Completion screen showing summary. */
@Composable
private fun PhotoScanCompleteScreen(
    processedCount: Int,
    extractedCount: Int,
    errors: List<String>,
    destinationPath: String,
    onFinish: () -> Unit
) {
  Column(
      modifier = Modifier.fillMaxSize().padding(32.dp),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Default.CheckCircle,
            null,
            modifier = Modifier.size(80.dp),
            tint =
                if (errors.isEmpty()) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.tertiary)
        Spacer(modifier = Modifier.height(24.dp))
        Text("Photo Scan Complete", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth(0.8f)) {
          Column(
              modifier = Modifier.padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly) {
                      Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "$processedCount",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary)
                        Text("Scans Processed", style = MaterialTheme.typography.labelMedium)
                      }
                      Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "$extractedCount",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary)
                        Text("Photos Extracted", style = MaterialTheme.typography.labelMedium)
                      }
                    }
              }
        }

        if (errors.isNotEmpty()) {
          Spacer(modifier = Modifier.height(16.dp))
          Card(
              modifier = Modifier.fillMaxWidth(0.8f),
              colors =
                  CardDefaults.cardColors(
                      containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(modifier = Modifier.padding(16.dp)) {
                  Text(
                      "${errors.size} error(s)",
                      style = MaterialTheme.typography.titleSmall,
                      color = MaterialTheme.colorScheme.error)
                  errors.take(5).forEach { error ->
                    Text(
                        "• $error",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer)
                  }
                  if (errors.size > 5) {
                    Text(
                        "... and ${errors.size - 5} more",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer)
                  }
                }
              }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Exported to: $destinationPath",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onFinish) { Text("Finish") }
      }
}

/** Loads the current image and runs detection. */
private suspend fun loadCurrentImage(
    scanState: PhotoScanState,
    scanService: ScanService,
    onStatus: (String) -> Unit = {},
    onError: (String?) -> Unit = {}
) {
  val currentImage = scanState.currentImage ?: return
  val file = currentImage.file

  try {
    onStatus("Loading image...")
    val image = ImageIO.read(file)
    if (image != null) {
      onStatus("Analyzing image with Computer Vision...")
      val detectedPhotos = scanService.detectPhotos(file.absolutePath)
      onStatus("Found ${detectedPhotos.size} photo(s)")
      scanState.setCurrentImageDetected(image, detectedPhotos)
    } else {
      onError("Failed to load image: ${file.name}")
      scanState.skipImage()
    }
  } catch (e: Exception) {
    onError("Error: ${e.message}")
    scanState.skipImage()
  }
}

/** Re-runs detection on the current image. */
private suspend fun rescanCurrentImage(
    scanState: PhotoScanState,
    scanService: ScanService,
    onStatus: (String) -> Unit = {},
    onError: (String?) -> Unit = {}
) {
  val currentImage = scanState.currentImage ?: return
  val file = currentImage.file
  val currentImageData = currentImage.image

  if (currentImageData == null) {
    // Need to reload the image
    loadCurrentImage(scanState, scanService, onStatus, onError)
    return
  }

  try {
    onStatus("Re-analyzing with Computer Vision...")
    val detectedPhotos = scanService.detectPhotos(file.absolutePath)
    onStatus("Found ${detectedPhotos.size} photo(s)")
    scanState.setCurrentImageDetected(currentImageData, detectedPhotos)
  } catch (e: Exception) {
    onError("Error: ${e.message}")
  }
}

/** Exports photos from the current image. */
private fun exportCurrentImage(
    scanState: PhotoScanState,
    scanService: ScanService,
    destinationPath: String,
    onComplete: () -> Unit
) {
  val currentImage = scanState.currentImage ?: return
  val image = currentImage.image ?: return
  val photos = scanState.currentPhotos.value

  if (photos.isEmpty()) {
    scanState.skipImage()
    return
  }

  scanState.step.value = Step.EXPORTING

  val baseName = currentImage.file.nameWithoutExtension

  scanState.updateExportProgress(
      ExportProgress(
          totalPhotos = photos.size,
          processedPhotos = 0,
          currentFile = currentImage.file.name,
          status = "Exporting..."))

  photos.forEachIndexed { index, photo ->
    val extractedImage = scanService.extractPhoto(image, photo)
    scanService.exportPhoto(
        photoImage = extractedImage,
        destinationPath = destinationPath,
        originalFile = currentImage.file,
        photoIndex = index,
        configuration = photo.configuration)
  }

  scanState.updateExportProgress(
      ExportProgress(
          totalPhotos = photos.size,
          processedPhotos = photos.size,
          currentFile = currentImage.file.name,
          status = "Complete"))

  scanState.nextImage()
}
