package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.kryspetrie.fileimport.application.PhotoScanExportService
import org.kryspetrie.fileimport.domain.model.DetectedPhoto
import org.kryspetrie.fileimport.domain.model.PhotoCorner
import org.kryspetrie.fileimport.domain.model.RotationAngle
import org.kryspetrie.fileimport.infrastructure.logging.AppLogger
import org.kryspetrie.fileimport.infrastructure.logging.OperationType
import org.kryspetrie.fileimport.infrastructure.photoscan.PhotoScanDetectorService
import org.kryspetrie.fileimport.infrastructure.wizard.*
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.koin.compose.koinInject

/**
 * Main container for the Photo Import Wizard. Manages the step-by-step workflow: Import → Overview
 * → Refinement → Summary → Processing → Complete
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WizardContainer(
    onComplete: (List<ProcessedPhoto>) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    detectorService: PhotoScanDetectorService = koinInject(),
    exportService: PhotoScanExportService = koinInject(),
    appLogger: AppLogger = koinInject()
) {
  // Create the wizard state
  val state = remember { PhotoScanWizardState() }
  
  // Set logger for operation tracking
  state.setLogger(appLogger)

  // Current step
  val currentStep by state.currentStep.collectAsState()

  // Loading state
  var isLoading by remember { mutableStateOf(false) }
  var loadingMessage by remember { mutableStateOf("") }

  // Error state
  var errorMessage by remember { mutableStateOf<String?>(null) }

  // Processing progress (0.0 to 1.0)
  var processingProgress by remember { mutableFloatStateOf(0f) }
  var processingCurrentFile by remember { mutableStateOf("") }

  // Export destination
  var exportDestination by remember { mutableStateOf(System.getProperty("user.home") + "/Pictures/PhotoScan") }

  // Coroutine scope for background operations
  val scope = rememberCoroutineScope()

  Scaffold(
      topBar = {
        // Only show top bar when not on IMPORT step
        if (currentStep != PhotoScanWizardState.WizardStep.IMPORT &&
            currentStep != PhotoScanWizardState.WizardStep.PROCESSING &&
            currentStep != PhotoScanWizardState.WizardStep.COMPLETE) {
          TopAppBar(
              title = { Text(getStepTitle(currentStep)) },
              navigationIcon = {
                IconButton(
                    onClick = {
                      when (currentStep) {
                        PhotoScanWizardState.WizardStep.OVERVIEW -> {
                          state.resetToImportStep()
                          onCancel()
                        }
                        PhotoScanWizardState.WizardStep.REFINEMENT -> state.exitRefinement()
                        PhotoScanWizardState.WizardStep.SUMMARY -> state.goToOverview()
                        else -> {}
                      }
                    }) {
                      Icon(Icons.Default.Close, "Cancel")
                    }
              },
              colors =
                  TopAppBarDefaults.topAppBarColors(
                      containerColor = MaterialTheme.colorScheme.surface))
        }
      },
      content = { paddingValues ->
        Box(modifier = modifier.fillMaxSize().padding(paddingValues)) {
          // Show content based on current step
          when (currentStep) {
            PhotoScanWizardState.WizardStep.IMPORT -> {
              ImportScreen(
                  state = state,
                  onImageSelected = { file ->
                    scope.launch {
                      loadImageAndDetect(
                          state = state,
                          file = file,
                          detectorService = detectorService,
                          cvAutoDetect = state.cvAutoDetectEnabled.value,
                          appLogger = appLogger,
                          isLoading = { isLoading = it },
                          onMessage = { loadingMessage = it },
                          onError = { errorMessage = it },
                          onComplete = { state.goToOverview() })
                    }
                  },
                  onCancel = onCancel)
            }

            PhotoScanWizardState.WizardStep.OVERVIEW -> {
              val image = state.image.collectAsState().value
              if (image != null) {
                OverviewScreen(
                    state = state, onBack = { state.resetToImportStep(); onCancel() }, onToSummary = { state.goToSummary() })
              } else {
                LoadingContent(message = "Loading image...")
              }
            }

            PhotoScanWizardState.WizardStep.REFINEMENT -> {
              val image = state.image.collectAsState().value
              if (image != null) {
                RefinementScreen(state = state, onBack = { state.goToOverview() })
              } else {
                LoadingContent(message = "Loading image...")
              }
            }

            PhotoScanWizardState.WizardStep.SUMMARY -> {
              val image = state.image.collectAsState().value
              if (image != null) {
                SummaryScreen(
                    state = state,
                    image = image,
                    exportDestination = exportDestination,
                    onDestinationChange = { exportDestination = it },
                    onBack = { state.goToOverview() },
                    onExport = {
                      scope.launch {
                        state.goToProcessing()
                        exportPhotos(
                            state = state,
                            image = image,
                            exportService = exportService,
                            destinationPath = exportDestination,
                            appLogger = appLogger,
                            isLoading = { isLoading = it },
                            onMessage = { loadingMessage = it },
                            onError = { errorMessage = it },
                            onProgress = { progress, file ->
                              processingProgress = progress
                              processingCurrentFile = file
                              appLogger.debug("Export progress: ${(progress * 100).toInt()}% - $file")
                            },
                            onComplete = { processedPhotos ->
                              processingProgress = 1f
                              appLogger.logOperationComplete(
                                  OperationType.EXPORT_COMPLETE, 
                                  "Exported ${processedPhotos.size} photo(s) to $exportDestination")
                              state.goToComplete()
                              onComplete(processedPhotos)
                            })
                      }
                    })
              } else {
                LoadingContent(message = "Loading image...")
              }
            }

            PhotoScanWizardState.WizardStep.PROCESSING -> {
              ProcessingScreen(
                  progress = processingProgress,
                  currentFile = processingCurrentFile)
            }

            PhotoScanWizardState.WizardStep.COMPLETE -> {
              CompleteScreen(
                  boxCount = state.boxCount(),
                  onFinish = {
                    // User clicked "Scan Another" - just reset the wizard
                    state.resetToImportStep()
                    // Don't call onComplete here - results were already handled
                  },
                  onCancel = {
                    // User clicked "Close" - reset and return to import tab
                    state.resetToImportStep()
                    onCancel()
                  })
            }
          }

          // Loading overlay
          if (isLoading) {
            LoadingOverlay(message = loadingMessage)
          }

          // Error snackbar
          errorMessage?.let { error ->
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                action = {
                  TextButton(
                      onClick = {
                        errorMessage = null
                        // If we have an image, go back to overview; otherwise back to import
                        if (state.image.value != null) {
                          state.goToOverview()
                        } else {
                          state.resetToImportStep()
                        }
                      }) {
                        Text("Dismiss & Retry")
                      }
                }) {
                  Text(error)
                }
          }
        }
      })
}

private fun getStepTitle(step: PhotoScanWizardState.WizardStep): String {
  return when (step) {
    PhotoScanWizardState.WizardStep.IMPORT -> "Import Photos"
    PhotoScanWizardState.WizardStep.OVERVIEW -> "Bounding Box Overview"
    PhotoScanWizardState.WizardStep.REFINEMENT -> "Refine Bounding Box"
    PhotoScanWizardState.WizardStep.SUMMARY -> "Photo Summary"
    PhotoScanWizardState.WizardStep.PROCESSING -> "Processing"
    PhotoScanWizardState.WizardStep.COMPLETE -> "Complete"
  }
}

private suspend fun loadImageAndDetect(
    state: PhotoScanWizardState,
    file: File,
    detectorService: PhotoScanDetectorService,
    cvAutoDetect: Boolean,
    appLogger: AppLogger,
    isLoading: (Boolean) -> Unit,
    onMessage: (String) -> Unit,
    onError: (String) -> Unit,
    onComplete: () -> Unit
) {
  isLoading(true)
  onMessage("Loading image...")
  appLogger.logOperationStart(OperationType.IMAGE_LOAD, "File: ${file.name}")

  try {
    val image = ImageIO.read(file)
    if (image != null) {
      state.initializeWithImage(image, file)
      onMessage("Image loaded. Analyzing...")

      // Run CV detection in background if enabled
      if (cvAutoDetect) {
        appLogger.logOperationStart(OperationType.IMAGE_DETECTION, "File: ${file.name}")
        withContext(Dispatchers.Default) {
          onMessage("Detecting photo boundaries...")
          val detectedPhotos = detectorService.detectPhotos(image)

          // Convert DetectedPhoto to BoundingBox
          val boxes =
              detectedPhotos.map { photo ->
                BoundingBox(
                    corners =
                        BoundingBoxCorners(
                            Point(photo.topLeft.x.toDouble(), photo.topLeft.y.toDouble()),
                            Point(photo.topRight.x.toDouble(), photo.topRight.y.toDouble()),
                            Point(photo.bottomRight.x.toDouble(), photo.bottomRight.y.toDouble()),
                            Point(photo.bottomLeft.x.toDouble(), photo.bottomLeft.y.toDouble())))
              }

          withContext(Dispatchers.Main) {
            if (boxes.isNotEmpty()) {
              state.setDetectedBoxes(boxes)
              appLogger.logOperationComplete(OperationType.IMAGE_DETECTION, "Detected ${boxes.size} photo(s)")
              onMessage("Detected ${boxes.size} photo(s)")
            } else {
              appLogger.info("No photos detected in ${file.name} - user can add manually")
              onMessage("No photos detected. Add bounding boxes manually.")
            }
            onComplete()
          }
        }
      } else {
        appLogger.info("CV auto-detection disabled - manual box placement expected")
        onMessage("Image loaded. Add bounding boxes manually.")
        onComplete()
      }
    } else {
      appLogger.logOperationFailed(OperationType.IMAGE_LOAD, "Unsupported image format: ${file.name}")
      withContext(Dispatchers.Main) { onError("Failed to load image: unsupported format") }
    }
  } catch (e: Exception) {
    appLogger.logOperationFailed(OperationType.IMAGE_LOAD, e.message ?: "Unknown error", e)
    withContext(Dispatchers.Main) { onError("Error loading image: ${e.message}") }
  } finally {
    isLoading(false)
  }
}

private suspend fun exportPhotos(
    state: PhotoScanWizardState,
    image: BufferedImage,
    exportService: PhotoScanExportService,
    destinationPath: String,
    appLogger: AppLogger,
    isLoading: (Boolean) -> Unit,
    onMessage: (String) -> Unit,
    onError: (String) -> Unit,
    onProgress: (Float, String) -> Unit,
    onComplete: (List<ProcessedPhoto>) -> Unit
) {
  isLoading(true)

  try {
    val boxes = state.boxes
    val configurations = state.photoConfigurations.value
    
    appLogger.logOperationStart(OperationType.EXPORT_START, "Destination: $destinationPath, ${boxes.size} photo(s)")

    if (boxes.isEmpty()) {
      appLogger.logOperationFailed(OperationType.EXPORT_FAILED, "No photos to export")
      onError("No photos to export")
      isLoading(false)
      return
    }

    // Validate and prepare output directory
    val outputDir = File(destinationPath)
    
    // First, validate if we can write to the path
    // Check various scenarios that would prevent writing
    val validationError = when {
      outputDir.exists() && !outputDir.isDirectory -> 
        "Path exists but is not a directory: $destinationPath"
      outputDir.exists() && !outputDir.canWrite() -> 
        "Cannot write to folder: $destinationPath"
      outputDir.exists() && !outputDir.canExecute() -> 
        "Cannot access folder: $destinationPath"
      !outputDir.exists() && !File(destinationPath).parentFile?.canWrite()!! -> 
        "Cannot create folder in: ${File(destinationPath).parentFile?.absolutePath}"
      else -> null
    }
    
    if (validationError != null) {
      appLogger.logOperationFailed(OperationType.VALIDATION_ERROR, validationError)
      onError(validationError)
      isLoading(false)
      return
    }
    
    // Create directory if it doesn't exist
    if (!outputDir.exists()) {
      if (!outputDir.mkdirs()) {
        appLogger.logOperationFailed(OperationType.EXPORT_FAILED, "Failed to create folder: $destinationPath")
        onError("Failed to create folder: $destinationPath")
        isLoading(false)
        return
      }
      appLogger.info("Created export folder: $destinationPath")
    }

    // Check available disk space (estimate: 50MB per photo for high-resolution exports)
    val minSpacePerPhoto = PhotoScanConstants.ESTIMATED_SPACE_PER_PHOTO_BYTES
    val requiredSpace = minSpacePerPhoto * boxes.size
    val freeSpace = outputDir.freeSpace
    if (freeSpace < requiredSpace) {
      val errorMsg = "Insufficient disk space. Need ${requiredSpace / (1024 * 1024)}MB, have ${freeSpace / (1024 * 1024)}MB"
      appLogger.logOperationFailed(OperationType.VALIDATION_ERROR, errorMsg)
      onError(errorMsg)
      isLoading(false)
      return
    }

    val baseName = state.imageFile.value?.nameWithoutExtension ?: "scan"
    val results = mutableListOf<ProcessedPhoto>()

    boxes.forEachIndexed { index, box ->
      val progress = (index + 1).toFloat() / boxes.size
      val fileName = if (boxes.size > 1) "${baseName}_${index + 1}" else baseName
      onProgress(progress * 0.9f, fileName) // Reserve 10% for finalization

      // Get configuration for this box
      val config = configurations[box.id] ?: PhotoConfiguration()

      val corrections = mutableListOf<String>()
      if (config.perspectiveCorrectionEnabled) corrections.add("Perspective")
      if (config.rotationDegrees != 0) corrections.add("Rotation ${config.rotationDegrees}°")
      
      appLogger.logOperationStart(OperationType.EXPORT_PHOTO, "Photo ${index + 1}/${boxes.size}: $fileName (${corrections.joinToString(", ").ifEmpty { "no corrections" }})")

      // Convert BoundingBox to DetectedPhoto format
      val detectedPhoto =
          DetectedPhoto(
              topLeft = PhotoCorner(box.corners.topLeft.x.toFloat(), box.corners.topLeft.y.toFloat()),
              topRight = PhotoCorner(box.corners.topRight.x.toFloat(), box.corners.topRight.y.toFloat()),
              bottomLeft = PhotoCorner(box.corners.bottomLeft.x.toFloat(), box.corners.bottomLeft.y.toFloat()),
              bottomRight = PhotoCorner(box.corners.bottomRight.x.toFloat(), box.corners.bottomRight.y.toFloat()),
              applyPerspectiveCorrection = config.perspectiveCorrectionEnabled,
              rotation = rotationFromDegrees(config.rotationDegrees))

      // Export using the service
      withContext(Dispatchers.Default) {
        try {
          val result = exportService.exportSinglePhoto(image, detectedPhoto, outputDir.absolutePath, fileName)
          
          withContext(Dispatchers.Main) {
            results.add(
                ProcessedPhoto(
                    originalFile = state.imageFile.value ?: File(""),
                    outputPath = result.destinationPath,
                    dimensions = result.width to result.height,
                    correctionsApplied = corrections))
            appLogger.logOperationComplete(OperationType.EXPORT_PHOTO, "Exported: ${result.destinationPath} (${result.width}x${result.height})")
          }
        } catch (e: Exception) {
          appLogger.logOperationFailed(OperationType.EXPORT_FAILED, "Photo ${index + 1} failed: ${e.message}", e)
          withContext(Dispatchers.Main) {
            results.add(
                ProcessedPhoto(
                    originalFile = state.imageFile.value ?: File(""),
                    outputPath = "ERROR: ${e.message}",
                    dimensions = 0 to 0,
                    correctionsApplied = listOf("Failed: ${e.message}")))
          }
        }
      }
    }

    onMessage("Export complete!")
    onProgress(1f, "")
    appLogger.logOperationComplete(OperationType.EXPORT_COMPLETE, "Successfully exported ${results.size} of ${boxes.size} photos")
    onComplete(results)
  } catch (e: Exception) {
    appLogger.logOperationFailed(OperationType.EXPORT_FAILED, "Unexpected error: ${e.message}", e)
    onError("Export failed: ${e.message}")
  } finally {
    isLoading(false)
  }
}

/**
 * Converts degrees (-90, 90, 180) to RotationAngle.
 */
private fun rotationFromDegrees(degrees: Int): RotationAngle {
  return when (degrees) {
    -90, -1 -> RotationAngle.CCW_90
    90, 1 -> RotationAngle.CW_90
    180, -180 -> RotationAngle.CW_180
    else -> RotationAngle.NONE
  }
}

@Composable
private fun LoadingContent(message: String) {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
          // Animated loading indicator
          AnimatedLoadingIndicator()
          Text(message, style = MaterialTheme.typography.bodyMedium)
        }
  }
}

@Composable
private fun AnimatedLoadingIndicator() {
  val infiniteTransition = rememberInfiniteTransition(label = "loading")
  val rotation by infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 360f,
      animationSpec = infiniteRepeatable(
          animation = tween(durationMillis = 1500, easing = LinearEasing),
          repeatMode = RepeatMode.Restart
      ),
      label = "rotation"
  )

  Box(
      modifier = Modifier.size(48.dp),
      contentAlignment = Alignment.Center) {
    CircularProgressIndicator(
        modifier = Modifier.fillMaxSize(),
        strokeWidth = 4.dp
    )
  }
}

@Composable
private fun LoadingOverlay(message: String) {
  Box(
      modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
      contentAlignment = Alignment.Center) {
        Card(modifier = Modifier.padding(32.dp)) {
          Column(
              modifier = Modifier.padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(16.dp)) {
                AnimatedLoadingIndicator()
                Text(message, style = MaterialTheme.typography.bodyMedium)
              }
        }
      }
}

@Composable
private fun ProcessingScreen(progress: Float, currentFile: String) {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.padding(32.dp)) {
          Icon(
              Icons.Default.HourglassEmpty,
              contentDescription = null,
              modifier = Modifier.size(64.dp),
              tint = MaterialTheme.colorScheme.primary)

          Text("Processing Photos", style = MaterialTheme.typography.headlineSmall)

          LinearProgressIndicator(
              progress = { progress.coerceIn(0f, 1f) },
              modifier = Modifier.fillMaxWidth(0.8f))

          Text(
              if (currentFile.isNotEmpty()) "Processing: $currentFile" else "Finalizing...",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
  }
}

@Composable
private fun CompleteScreen(boxCount: Int, onFinish: () -> Unit, onCancel: () -> Unit) {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.padding(32.dp)) {
          Icon(
              Icons.Default.CheckCircle,
              contentDescription = null,
              modifier = Modifier.size(80.dp),
              tint = MaterialTheme.colorScheme.primary)

          Text("Photo Import Complete", style = MaterialTheme.typography.headlineMedium)

          Card(modifier = Modifier.fillMaxWidth(0.6f)) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  Text(
                      "$boxCount photo(s) extracted",
                      style = MaterialTheme.typography.titleMedium,
                      color = MaterialTheme.colorScheme.primary)

                  Text(
                      "Photos have been saved to the destination folder",
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
          }

          Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(onClick = onCancel) { Text("Close") }
            Button(onClick = onFinish) { Text("Scan Another") }
          }
        }
  }
}

/** Result of processed photo export. */
data class ProcessedPhoto(
    val originalFile: File,
    val outputPath: String,
    val dimensions: Pair<Int, Int>,
    val correctionsApplied: List<String>
)
