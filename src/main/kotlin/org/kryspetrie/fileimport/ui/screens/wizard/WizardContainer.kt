package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.kryspetrie.fileimport.application.PerspectiveCorrectionService
import org.kryspetrie.fileimport.application.PhotoScanExportService
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.model.DetectedPhoto
import org.kryspetrie.fileimport.domain.model.PhotoCorner
import org.kryspetrie.fileimport.domain.model.RotationAngle
import org.kryspetrie.fileimport.domain.port.DispatcherProvider
import org.kryspetrie.fileimport.domain.port.SettingsPort
import org.kryspetrie.fileimport.infrastructure.adapter.AppPaths
import org.kryspetrie.fileimport.infrastructure.logging.AppLogger
import org.kryspetrie.fileimport.infrastructure.logging.OperationType
import org.kryspetrie.fileimport.infrastructure.photoscan.PhotoScanDetectorService
import org.kryspetrie.fileimport.infrastructure.wizard.BoundingBox
import org.kryspetrie.fileimport.infrastructure.wizard.BoundingBoxCorners
import org.kryspetrie.fileimport.infrastructure.wizard.PhotoConfiguration
import org.kryspetrie.fileimport.infrastructure.wizard.PhotoScanConstants
import org.kryspetrie.fileimport.infrastructure.wizard.PhotoScanWizardState
import org.kryspetrie.fileimport.infrastructure.wizard.Point

/**
 * Main container for the Photo Import Wizard. Manages the step-by-step workflow: Import → Overview
 * → Summary → Processing → Complete
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WizardContainer(
    onComplete: (List<ProcessedPhoto>) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    detectorService: PhotoScanDetectorService = koinInject(),
    exportService: PhotoScanExportService = koinInject(),
    perspectiveService: PerspectiveCorrectionService = koinInject(),
    appLogger: AppLogger = koinInject(),
    settingsPort: SettingsPort = koinInject(),
    dispatcherProvider: DispatcherProvider = koinInject(),
) {
    val state = remember { PhotoScanWizardState() }
    state.setLogger(appLogger)
    val currentStep by state.currentStep.collectAsState()
    var isLoading by remember { mutableStateOf(false) }
    var loadingMessage by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var processingProgress by remember { mutableFloatStateOf(0f) }
    var processingCurrentFile by remember { mutableStateOf("") }
    val settings by settingsPort.observeSettings().collectAsState(initial = AppSettings())
    var exportDestination by remember {
        mutableStateOf(
            settings.photoScanImportTabSettings.lastDestinationPath.ifBlank {
                AppPaths.defaultDestination.absolutePath
            }
        )
    }
    // Sync exportDestination when settings change (e.g. user changed destination on import screen)
    LaunchedEffect(settings.photoScanImportTabSettings.lastDestinationPath) {
        val settingsDest = settings.photoScanImportTabSettings.lastDestinationPath
        if (settingsDest.isNotBlank() && settingsDest != exportDestination) {
            exportDestination = settingsDest
        }
    }
    val scope = rememberCoroutineScope()

    Box(modifier = modifier.fillMaxSize()) {
        WizardStepContent(
            currentStep = currentStep,
            state = state,
            settingsPort = settingsPort,
            detectorService = detectorService,
            exportService = exportService,
            perspectiveService = perspectiveService,
            appLogger = appLogger,
            dispatcherProvider = dispatcherProvider,
            scope = scope,
            isLoading = { isLoading = it },
            onMessage = { loadingMessage = it },
            onError = { errorMessage = it },
            exportDestination = exportDestination,
            onExportDestinationChange = { newDest ->
                exportDestination = newDest
                // Persist to settings so the import screen stays in sync
                scope.launch {
                    val currentSettings = settingsPort.observeSettings().first()
                    val updated =
                        currentSettings.withPhotoScanImportTabSettings(
                            currentSettings.photoScanImportTabSettings.withRecentDestinationPath(
                                newDest
                            )
                        )
                    settingsPort.saveSettings(updated)
                }
            },
            processingProgress = processingProgress,
            processingCurrentFile = processingCurrentFile,
            onProgress = { progress, file ->
                processingProgress = progress
                processingCurrentFile = file
                appLogger.debug("Export progress: ${(progress * 100).toInt()}% - $file")
            },
            onComplete = onComplete,
            onCancel = onCancel,
        )

        if (isLoading) {
            LoadingOverlay(message = loadingMessage)
        }

        ErrorSnackbar(
            errorMessage = errorMessage,
            onDismiss = {
                errorMessage = null
                if (state.image.value != null) {
                    state.goToOverview()
                } else {
                    state.resetToImportStep()
                }
            },
        )
    }
}

@Composable
private fun WizardStepContent(
    currentStep: PhotoScanWizardState.WizardStep,
    state: PhotoScanWizardState,
    settingsPort: SettingsPort,
    detectorService: PhotoScanDetectorService,
    exportService: PhotoScanExportService,
    perspectiveService: PerspectiveCorrectionService,
    appLogger: AppLogger,
    dispatcherProvider: DispatcherProvider,
    scope: kotlinx.coroutines.CoroutineScope,
    isLoading: (Boolean) -> Unit,
    onMessage: (String) -> Unit,
    onError: (String) -> Unit,
    exportDestination: String,
    onExportDestinationChange: (String) -> Unit,
    processingProgress: Float,
    processingCurrentFile: String,
    onProgress: (Float, String) -> Unit,
    onComplete: (List<ProcessedPhoto>) -> Unit,
    onCancel: () -> Unit,
) {
    when (currentStep) {
        PhotoScanWizardState.WizardStep.IMPORT -> {
            PhotoScanImportScreen(
                state = state,
                settingsPort = settingsPort,
                onSettingsChange = { newSettings ->
                    scope.launch { settingsPort.saveSettings(newSettings) }
                },
                onImageSelected = { file ->
                    scope.launch {
                        loadImageAndDetect(
                            state = state,
                            file = file,
                            detectorService = detectorService,
                            cvAutoDetect = state.cvAutoDetectEnabled.value,
                            appLogger = appLogger,
                            dispatcherProvider = dispatcherProvider,
                            isLoading = isLoading,
                            onMessage = onMessage,
                            onError = onError,
                            onComplete = { state.goToOverview() },
                        )
                    }
                },
                onCancel = onCancel,
            )
        }

        PhotoScanWizardState.WizardStep.OVERVIEW -> {
            val image = state.image.collectAsState().value
            if (image != null) {
                OverviewScreen(
                    state = state,
                    onBack = {
                        state.resetToImportStep()
                        onCancel()
                    },
                    onToSummary = { state.goToSummary() },
                )
            } else {
                LoadingContent(message = "Loading image...")
            }
        }

        PhotoScanWizardState.WizardStep.REFINEMENT -> {
            // Refinement is now handled inline in Overview — redirect to Overview
            state.goToOverview()
            LoadingContent(message = "Redirecting...")
        }

        PhotoScanWizardState.WizardStep.SUMMARY -> {
            val image = state.image.collectAsState().value
            if (image != null) {
                SummaryScreen(
                    state = state,
                    image = image,
                    perspectiveService = perspectiveService,
                    exportDestination = exportDestination,
                    onDestinationChange = onExportDestinationChange,
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
                                dispatcherProvider = dispatcherProvider,
                                isLoading = isLoading,
                                onMessage = onMessage,
                                onError = onError,
                                onProgress = onProgress,
                                onComplete = { processedPhotos ->
                                    appLogger.logOperationComplete(
                                        OperationType.EXPORT_COMPLETE,
                                        "Exported ${processedPhotos.size} photo(s) to $exportDestination",
                                    )
                                    // Stay on PROCESSING screen — progress >= 1f triggers the
                                    // completion UI inline so the user can choose what to do next.
                                    onComplete(processedPhotos)
                                },
                            )
                        }
                    },
                )
            } else {
                LoadingContent(message = "Loading image...")
            }
        }

        PhotoScanWizardState.WizardStep.PROCESSING -> {
            ProcessingScreen(
                progress = processingProgress,
                currentFile = processingCurrentFile,
                isComplete = processingProgress >= 1f,
                boxCount = state.boxCount(),
                exportDestination = exportDestination,
                onScanAnother = { state.resetToImportStep() },
                onDone = {
                    state.resetToImportStep()
                    onCancel()
                },
                onOpenFolder = {
                    try {
                        val dir = java.io.File(exportDestination)
                        if (dir.exists()) {
                            if (java.awt.Desktop.isDesktopSupported()) {
                                java.awt.Desktop.getDesktop().open(dir)
                            }
                        } else {
                            dir.mkdirs()
                            if (java.awt.Desktop.isDesktopSupported()) {
                                java.awt.Desktop.getDesktop().open(dir)
                            }
                        }
                    } catch (_: Exception) {
                        // Best effort — if Desktop.open fails, just skip
                    }
                },
            )
        }

        PhotoScanWizardState.WizardStep.COMPLETE -> {
            // COMPLETE step is no longer used — results are shown inline on the PROCESSING screen.
            // If we somehow land here, redirect back to PROCESSING to show the completion UI.
            ProcessingScreen(
                progress = 1f,
                currentFile = "",
                isComplete = true,
                boxCount = state.boxCount(),
                exportDestination = exportDestination,
                onScanAnother = { state.resetToImportStep() },
                onDone = {
                    state.resetToImportStep()
                    onCancel()
                },
                onOpenFolder = {
                    try {
                        val dir = java.io.File(exportDestination)
                        if (dir.exists()) {
                            if (java.awt.Desktop.isDesktopSupported()) {
                                java.awt.Desktop.getDesktop().open(dir)
                            }
                        }
                    } catch (_: Exception) {
                        // Best effort
                    }
                },
            )
        }
    }
}

@Composable
private fun BoxScope.ErrorSnackbar(errorMessage: String?, onDismiss: () -> Unit) {
    errorMessage?.let { error ->
        Snackbar(
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            action = { TextButton(onClick = onDismiss) { Text("Dismiss & Retry") } },
        ) {
            Text(error)
        }
    }
}

private suspend fun loadImageAndDetect(
    state: PhotoScanWizardState,
    file: File,
    detectorService: PhotoScanDetectorService,
    cvAutoDetect: Boolean,
    appLogger: AppLogger,
    dispatcherProvider: DispatcherProvider,
    isLoading: (Boolean) -> Unit,
    onMessage: (String) -> Unit,
    onError: (String) -> Unit,
    onComplete: () -> Unit,
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
                withContext(dispatcherProvider.default) {
                    onMessage("Detecting photo boundaries...")
                    val detectedPhotos = detectorService.detectPhotos(image)

                    // Convert DetectedPhoto to BoundingBox
                    val boxes =
                        detectedPhotos.map { photo ->
                            BoundingBox(
                                corners =
                                    BoundingBoxCorners(
                                        Point(
                                            photo.topLeft.x.toDouble(),
                                            photo.topLeft.y.toDouble(),
                                        ),
                                        Point(
                                            photo.topRight.x.toDouble(),
                                            photo.topRight.y.toDouble(),
                                        ),
                                        Point(
                                            photo.bottomRight.x.toDouble(),
                                            photo.bottomRight.y.toDouble(),
                                        ),
                                        Point(
                                            photo.bottomLeft.x.toDouble(),
                                            photo.bottomLeft.y.toDouble(),
                                        ),
                                    )
                            )
                        }

                    withContext(Dispatchers.Main) {
                        if (boxes.isNotEmpty()) {
                            state.setDetectedBoxes(boxes)
                            appLogger.logOperationComplete(
                                OperationType.IMAGE_DETECTION,
                                "Detected ${boxes.size} photo(s)",
                            )
                            onMessage("Detected ${boxes.size} photo(s)")
                        } else {
                            appLogger.info(
                                "No photos detected in ${file.name} - user can add manually"
                            )
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
            appLogger.logOperationFailed(
                OperationType.IMAGE_LOAD,
                "Unsupported image format: ${file.name}",
            )
            withContext(Dispatchers.Main) { onError("Failed to load image: unsupported format") }
        }
    } catch (e: Exception) {
        appLogger.logOperationFailed(OperationType.IMAGE_LOAD, e.message ?: "Unknown error", e)
        withContext(Dispatchers.Main) { onError("Error loading image: ${e.message}") }
    } finally {
        isLoading(false)
    }
}

/**
 * Validates the export destination directory. Returns an error string if invalid, or null if valid.
 */
private fun validateExportDestination(destinationPath: String): String? {
    val outputDir = File(destinationPath)
    return when {
        outputDir.exists() && !outputDir.isDirectory ->
            "Path exists but is not a directory: $destinationPath"
        outputDir.exists() && !outputDir.canWrite() -> "Cannot write to folder: $destinationPath"
        outputDir.exists() && !outputDir.canExecute() -> "Cannot access folder: $destinationPath"
        !outputDir.exists() && !File(destinationPath).parentFile?.canWrite()!! ->
            "Cannot create folder in: ${File(destinationPath).parentFile?.absolutePath}"
        else -> null
    }
}

/** Exports a single photo from the scan image based on the given bounding box and configuration. */
private suspend fun exportSinglePhoto(
    image: BufferedImage,
    box: BoundingBox,
    config: PhotoConfiguration,
    outputDir: File,
    fileName: String,
    index: Int,
    totalCount: Int,
    state: PhotoScanWizardState,
    exportService: PhotoScanExportService,
    appLogger: AppLogger,
    dispatcherProvider: DispatcherProvider,
    onProgress: (Float, String) -> Unit,
): ProcessedPhoto {
    val progress = (index + 1).toFloat() / totalCount
    onProgress(progress * 0.9f, fileName)

    val corrections = mutableListOf<String>()
    corrections.add("Warp-stretch")
    if (config.rotationDegrees != 0) corrections.add("Rotation ${config.rotationDegrees}°")

    appLogger.logOperationStart(
        OperationType.EXPORT_PHOTO,
        "Photo ${index + 1}/${totalCount}: $fileName " +
            "(${corrections.joinToString(", ").ifEmpty { "no corrections" }})",
    )

    val detectedPhoto =
        DetectedPhoto(
            topLeft = PhotoCorner(box.corners.topLeft.x.toFloat(), box.corners.topLeft.y.toFloat()),
            topRight =
                PhotoCorner(box.corners.topRight.x.toFloat(), box.corners.topRight.y.toFloat()),
            bottomLeft =
                PhotoCorner(box.corners.bottomLeft.x.toFloat(), box.corners.bottomLeft.y.toFloat()),
            bottomRight =
                PhotoCorner(
                    box.corners.bottomRight.x.toFloat(),
                    box.corners.bottomRight.y.toFloat(),
                ),
            applyPerspectiveCorrection = true, // Always warp-stretch
            rotation = rotationFromDegrees(config.rotationDegrees),
        )

    return withContext(dispatcherProvider.default) {
        try {
            val result =
                exportService.exportSinglePhoto(
                    image,
                    detectedPhoto,
                    outputDir.absolutePath,
                    fileName,
                )
            ProcessedPhoto(
                    originalFile = state.imageFile.value ?: File(""),
                    outputPath = result.destinationPath,
                    dimensions = result.width to result.height,
                    correctionsApplied = corrections,
                )
                .also {
                    appLogger.logOperationComplete(
                        OperationType.EXPORT_PHOTO,
                        "Exported: ${result.destinationPath} (${result.width}x${result.height})",
                    )
                }
        } catch (e: Exception) {
            appLogger.logOperationFailed(
                OperationType.EXPORT_FAILED,
                "Photo ${index + 1} failed: ${e.message}",
                e,
            )
            ProcessedPhoto(
                originalFile = state.imageFile.value ?: File(""),
                outputPath = "ERROR: ${e.message}",
                dimensions = 0 to 0,
                correctionsApplied = listOf("Failed: ${e.message}"),
            )
        }
    }
}

@Suppress("ReturnCount")
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
    onComplete: (List<ProcessedPhoto>) -> Unit,
    dispatcherProvider: DispatcherProvider,
) {
    isLoading(true)

    try {
        val boxes = state.boxes
        val configurations = state.photoConfigurations.value

        appLogger.logOperationStart(
            OperationType.EXPORT_START,
            "Destination: $destinationPath, ${boxes.size} photo(s)",
        )

        if (boxes.isEmpty()) {
            appLogger.logOperationFailed(OperationType.EXPORT_FAILED, "No photos to export")
            onError("No photos to export")
            isLoading(false)
            return
        }

        // Validate destination directory
        val validationError = validateExportDestination(destinationPath)
        if (validationError != null) {
            appLogger.logOperationFailed(OperationType.VALIDATION_ERROR, validationError)
            onError(validationError)
            isLoading(false)
            return
        }

        // Create directory if it doesn't exist
        val outputDir = File(destinationPath)
        if (!outputDir.exists()) {
            if (!outputDir.mkdirs()) {
                appLogger.logOperationFailed(
                    OperationType.EXPORT_FAILED,
                    "Failed to create folder: $destinationPath",
                )
                onError("Failed to create folder: $destinationPath")
                isLoading(false)
                return
            }
            appLogger.info("Created export folder: $destinationPath")
        }

        // Check available disk space
        val minSpacePerPhoto = PhotoScanConstants.ESTIMATED_SPACE_PER_PHOTO_BYTES
        val requiredSpace = minSpacePerPhoto * boxes.size
        val freeSpace = outputDir.freeSpace
        if (freeSpace < requiredSpace) {
            val errorMsg =
                "Insufficient disk space. Need ${requiredSpace / (1024 * 1024)}MB, have ${freeSpace / (1024 * 1024)}MB"
            appLogger.logOperationFailed(OperationType.VALIDATION_ERROR, errorMsg)
            onError(errorMsg)
            isLoading(false)
            return
        }

        val baseName = state.imageFile.value?.nameWithoutExtension ?: "scan"
        val results = mutableListOf<ProcessedPhoto>()

        boxes.forEachIndexed { index, box ->
            val fileName = if (boxes.size > 1) "${baseName}_${index + 1}" else baseName
            val config = configurations[box.id] ?: PhotoConfiguration()
            val result =
                exportSinglePhoto(
                    image = image,
                    box = box,
                    config = config,
                    outputDir = outputDir,
                    fileName = fileName,
                    index = index,
                    totalCount = boxes.size,
                    state = state,
                    exportService = exportService,
                    appLogger = appLogger,
                    dispatcherProvider = dispatcherProvider,
                    onProgress = onProgress,
                )
            results.add(result)
        }

        onMessage("Export complete!")
        onProgress(1f, "")
        appLogger.logOperationComplete(
            OperationType.EXPORT_COMPLETE,
            "Successfully exported ${results.size} of ${boxes.size} photos",
        )
        onComplete(results)
    } catch (e: Exception) {
        appLogger.logOperationFailed(
            OperationType.EXPORT_FAILED,
            "Unexpected error: ${e.message}",
            e,
        )
        onError("Export failed: ${e.message}")
    } finally {
        isLoading(false)
    }
}

/** Converts degrees (0, 90, 180, 270) to RotationAngle. */
private fun rotationFromDegrees(degrees: Int): RotationAngle {
    return when (degrees) {
        90 -> RotationAngle.CW_90
        180 -> RotationAngle.CW_180
        270 -> RotationAngle.CCW_90
        -90 -> RotationAngle.CCW_90
        else -> RotationAngle.NONE
    }
}

@Composable
private fun LoadingContent(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Animated loading indicator
            AnimatedLoadingIndicator()
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun AnimatedLoadingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val rotation by
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 1500, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "rotation",
        )

    Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.fillMaxSize(), strokeWidth = 4.dp)
    }
}

@Composable
private fun LoadingOverlay(message: String) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Card(modifier = Modifier.padding(32.dp)) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AnimatedLoadingIndicator()
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ProcessingScreen(
    progress: Float,
    currentFile: String,
    isComplete: Boolean,
    boxCount: Int,
    exportDestination: String,
    onScanAnother: () -> Unit,
    onDone: () -> Unit,
    onOpenFolder: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            if (isComplete) {
                // Completion state — show results and action buttons on the same screen
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )

                Text("Export Complete", style = MaterialTheme.typography.headlineMedium)

                Card(modifier = Modifier.fillMaxWidth(0.6f)) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "$boxCount photo(s) exported",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )

                        Text(
                            exportDestination,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(onClick = onDone) { Text("Done") }
                    OutlinedButton(onClick = onOpenFolder) {
                        Icon(Icons.Default.FolderOpen, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Open Folder")
                    }
                    Button(onClick = onScanAnother) { Text("Scan Another") }
                }
            } else {
                // In-progress state — show progress spinner and file name
                Icon(
                    Icons.Default.HourglassEmpty,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )

                Text("Processing Photos", style = MaterialTheme.typography.headlineSmall)

                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(0.8f),
                )

                Text(
                    if (currentFile.isNotEmpty()) "Processing: $currentFile" else "Finalizing...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Result of processed photo export. */
data class ProcessedPhoto(
    val originalFile: File,
    val outputPath: String,
    val dimensions: Pair<Int, Int>,
    val correctionsApplied: List<String>,
)
