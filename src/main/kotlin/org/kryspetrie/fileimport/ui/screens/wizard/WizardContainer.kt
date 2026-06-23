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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
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
import org.kryspetrie.fileimport.application.FaceRegionTransformer
import org.kryspetrie.fileimport.application.PerspectiveCorrectionService
import org.kryspetrie.fileimport.application.PhotoScanExportService
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.model.DetectedPhoto
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.PhotoCorner
import org.kryspetrie.fileimport.domain.model.RecentMetadataSet
import org.kryspetrie.fileimport.domain.port.DispatcherProvider
import org.kryspetrie.fileimport.domain.port.PhotoScanDetectorPort
import org.kryspetrie.fileimport.domain.port.SettingsPort
import org.kryspetrie.fileimport.infrastructure.adapter.AppPaths
import org.kryspetrie.fileimport.infrastructure.adapter.toProcessedImage
import org.kryspetrie.fileimport.infrastructure.logging.AppLogger
import org.kryspetrie.fileimport.infrastructure.logging.OperationType
import org.kryspetrie.fileimport.infrastructure.wizard.BoundingBox
import org.kryspetrie.fileimport.infrastructure.wizard.BoundingBoxCorners
import org.kryspetrie.fileimport.infrastructure.wizard.PhotoConfiguration
import org.kryspetrie.fileimport.infrastructure.wizard.PhotoScanConstants
import org.kryspetrie.fileimport.infrastructure.wizard.PhotoScanWizardState
import org.kryspetrie.fileimport.infrastructure.wizard.WizardStep
import org.kryspetrie.fileimport.infrastructure.wizard.Point
import org.kryspetrie.fileimport.ui.components.PreviewCache
import org.kryspetrie.fileimport.ui.components.isImageFile
import org.kryspetrie.fileimport.ui.components.pickFolder
import org.kryspetrie.fileimport.ui.components.pickImageFile

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
    detectorService: PhotoScanDetectorPort = koinInject(),
    exportService: PhotoScanExportService = koinInject(),
    perspectiveService: PerspectiveCorrectionService = koinInject(),
    appLogger: AppLogger = koinInject(),
    settingsPort: SettingsPort = koinInject(),
    dispatcherProvider: DispatcherProvider = koinInject(),
    faceRegionTransformer: FaceRegionTransformer = koinInject(),
) {
    val state = remember { PhotoScanWizardState() }
    state.setLogger(appLogger)
    val currentStep by state.currentStep.collectAsState()
    var isLoading by remember { mutableStateOf(false) }
    var loadingMessage by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var processingProgress by remember { mutableFloatStateOf(0f) }
    var processingCurrentFile by remember { mutableStateOf("") }
    var failedExportCount by remember { mutableStateOf(0) }
    var exportResults by remember { mutableStateOf<List<ExportResult>>(emptyList()) }
    val settings by settingsPort.observeSettings().collectAsState()
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
    val previewCache = remember { PreviewCache(perspectiveService) }

    // Clear preview cache when the source image changes (new import)
    val sourceImage by state.image.collectAsState()
    LaunchedEffect(sourceImage) {
        previewCache.clear()
    }

    // Initialize correction strategy from persisted settings (once)
    LaunchedEffect(Unit) { state.setDefaultCorrectionStrategy(settings.lastCorrectionStrategy) }

    Box(modifier = modifier.fillMaxSize()) {
        WizardStepContent(
            currentStep = currentStep,
            state = state,
            settingsPort = settingsPort,
            settings = settings,
            detectorService = detectorService,
            exportService = exportService,
            perspectiveService = perspectiveService,
            previewCache = previewCache,
            appLogger = appLogger,
            dispatcherProvider = dispatcherProvider,
            faceRegionTransformer = faceRegionTransformer,
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
            failedExportCount = failedExportCount,
            onFailedCountChange = { count -> failedExportCount = count },
            onExportResults = { results -> exportResults = results },
            exportResults = exportResults,
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
    currentStep: WizardStep,
    state: PhotoScanWizardState,
    settingsPort: SettingsPort,
    settings: AppSettings,
    detectorService: PhotoScanDetectorPort,
    exportService: PhotoScanExportService,
    perspectiveService: PerspectiveCorrectionService,
    previewCache: PreviewCache,
    appLogger: AppLogger,
    dispatcherProvider: DispatcherProvider,
    faceRegionTransformer: FaceRegionTransformer,
    scope: kotlinx.coroutines.CoroutineScope,
    isLoading: (Boolean) -> Unit,
    onMessage: (String) -> Unit,
    onError: (String) -> Unit,
    exportDestination: String,
    onExportDestinationChange: (String) -> Unit,
    processingProgress: Float,
    processingCurrentFile: String,
    onProgress: (Float, String) -> Unit,
    failedExportCount: Int,
    onFailedCountChange: (Int) -> Unit,
    onExportResults: (List<ExportResult>) -> Unit,
    exportResults: List<ExportResult>,
    onComplete: (List<ProcessedPhoto>) -> Unit,
    onCancel: () -> Unit,
) {
    // Shared completion handler for all export flows
    val handleExportComplete: (List<ProcessedPhoto>) -> Unit = { processedPhotos ->
        onFailedCountChange(processedPhotos.count { it.isError })
        onExportResults(processedPhotos.map { it.toExportResult() })
        appLogger.logOperationComplete(
            OperationType.EXPORT_COMPLETE,
            "Exported ${processedPhotos.size} ${if (processedPhotos.size == 1) "photo" else "photos"} to $exportDestination",
        )
        state.goToComplete()
    }

    when (currentStep) {
        WizardStep.IMPORT -> {
            PhotoScanImportScreen(
                state = state,
                settingsPort = settingsPort,
                onSettingsChange = { newSettings ->
                    scope.launch { settingsPort.saveSettings(newSettings) }
                },
                onImageSelected = { file, batchFiles ->
                    startNewImport(
                        state,
                        file,
                        batchFiles,
                        detectorService,
                        appLogger,
                        dispatcherProvider,
                        isLoading,
                        onMessage,
                        onError,
                        scope,
                    )
                },
                onCancel = onCancel,
            )
        }

        WizardStep.OVERVIEW -> {
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

        WizardStep.REFINEMENT -> {
            // Refinement is now handled inline in Overview — redirect immediately
            LaunchedEffect(Unit) { state.goToOverview() }
            // Don't render content while redirecting; the step will change on next frame
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        WizardStep.SUMMARY -> {
            val image = state.image.collectAsState().value
            if (image != null) {
                SummaryScreen(
                    state = state,
                    image = image,
                    perspectiveService = perspectiveService,
                    previewCache = previewCache,
                    onBack = { state.goToOverview() },
                    onExport = { state.goToEdit() },
                    onSkipMetadata = {
                        scope.launch {
                            onFailedCountChange(0)
                            onExportResults(emptyList())
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
                                onComplete = handleExportComplete,
                            )
                        }
                    },
                )
            } else {
                LoadingContent(message = "Loading image...")
            }
        }

        WizardStep.EDIT -> {
            val image = state.image.collectAsState().value
            if (image != null) {
                EditScreen(
                    state = state,
                    image = image,
                    perspectiveService = perspectiveService,
                    previewCache = previewCache,
                    metadataHistory = settings.metadataHistory,
                    onMetadataHistoryUpdate = { fieldKey, value ->
                        scope.launch {
                            val currentSettings = settingsPort.observeSettings().first()
                            val updated = currentSettings.addMetadataHistory(fieldKey, value)
                            settingsPort.saveSettings(updated)
                        }
                    },
                    onMetadataHistoryRemove = { fieldKey, value ->
                        scope.launch {
                            val currentSettings = settingsPort.observeSettings().first()
                            val updated = currentSettings.removeMetadataHistory(fieldKey, value)
                            settingsPort.saveSettings(updated)
                        }
                    },
                    onRecordMetadataSet = { set ->
                        scope.launch {
                            val currentSettings = settingsPort.observeSettings().first()
                            val updated = currentSettings.addMetadataSet(set)
                            settingsPort.saveSettings(updated)
                        }
                    },
                    onBack = { state.goToSummary() },
                    onExport = {
                        scope.launch {
                            onFailedCountChange(0)
                            onExportResults(emptyList())
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
                                onComplete = handleExportComplete,
                            )
                        }
                    },
                    onSkipToExport = {
                        scope.launch {
                            onFailedCountChange(0)
                            onExportResults(emptyList())
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
                                onComplete = handleExportComplete,
                            )
                        }
                    },
                    startWithMetadata = settings.alwaysEditMetadata,
                    faceRegionTransformer = faceRegionTransformer,
                )
            } else {
                LoadingContent(message = "Loading image...")
            }
        }

        WizardStep.PROCESSING -> {
            ProcessingScreen(
                progress = processingProgress,
                currentFile = processingCurrentFile,
                totalPhotos = state.boxCount(),
                destination = exportDestination,
                onBack = { state.resetToImportStep() },
            )
        }

        WizardStep.COMPLETE -> {
            CompletionScreen(
                photoCount = state.boxCount(),
                exportDestination = exportDestination,
                isBatchMode = state.isBatchMode,
                hasMoreBatchImages = state.hasMoreBatchImages,
                currentBatchIndex = state.currentImageIndex.value,
                batchTotal = state.batchTotal,
                skippedCount = state.skippedBatchIndices.value.size,
                failedCount = failedExportCount,
                exportResults = exportResults,
                onDone = {
                    state.resetToImportStep()
                    onCancel()
                },
                onImportFile = {
                    val path = pickImageFile("Select Image File")
                    if (path != null) {
                        startNewImport(
                            state,
                            File(path),
                            null,
                            detectorService,
                            appLogger,
                            dispatcherProvider,
                            isLoading,
                            onMessage,
                            onError,
                            scope,
                        )
                    }
                },
                onImportFolder = {
                    val path = pickFolder("Select Source Folder")
                    if (path != null) {
                        val folder = File(path)
                        val batchFiles = collectImageFiles(folder)
                        if (batchFiles.isNotEmpty()) {
                            val batch = if (batchFiles.size > 1) batchFiles else null
                            startNewImport(
                                state,
                                batchFiles.first(),
                                batch,
                                detectorService,
                                appLogger,
                                dispatcherProvider,
                                isLoading,
                                onMessage,
                                onError,
                                scope,
                            )
                        }
                    }
                },
                nextBatchFile = state.peekNextBatchFile(),
                onContinueToNextPhoto = {
                    continueToNextBatchPhoto(
                        state,
                        detectorService,
                        appLogger,
                        dispatcherProvider,
                        isLoading,
                        onMessage,
                        onError,
                        scope,
                    )
                },
                onSkipNextPhoto =
                    if (state.hasMoreBatchImages) {
                        { skipNextBatchPhoto(state) }
                    } else null,
                onCancelImport = { state.resetToImportStep() },
                onOpenFolder = { openExportFolder(exportDestination) },
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
    detectorService: PhotoScanDetectorPort,
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
                    val detectedPhotos = detectorService.detectPhotos(image.toProcessedImage())

                    // Convert DetectedPhoto to BoundingBox with per-photo detection config
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
                    // Carry detection mode from each DetectedPhoto into per-box configuration
                    val configs =
                        detectedPhotos.map { photo ->
                            PhotoConfiguration(
                                detectionMode = photo.detectionMode,
                                perspectiveCorrectionEnabled = photo.applyPerspectiveCorrection,
                            )
                        }

                    withContext(Dispatchers.Main) {
                        if (boxes.isNotEmpty()) {
                            state.setDetectedBoxes(boxes, configs)
                            appLogger.logOperationComplete(
                                OperationType.IMAGE_DETECTION,
                                "Detected ${boxes.size} ${if (boxes.size == 1) "photo" else "photos"}",
                            )
                            onMessage("Detected ${boxes.size} ${if (boxes.size == 1) "photo" else "photos"}")
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
/**
 * Collects image files from the given folder, sorted by name. Returns an empty list if the folder
 * contains no supported image files.
 */
private fun collectImageFiles(folder: File): List<File> {
    return folder.listFiles { f -> f.isFile && isImageFile(f) }?.sortedBy { it.name }?.toList()
        ?: emptyList()
}

/**
 * Opens the export destination folder in the system file browser. Best-effort — silently skips on
 * failure.
 */
private fun openExportFolder(exportDestination: String) {
    try {
        val dir = File(exportDestination)
        if (!dir.exists()) dir.mkdirs()
        if (java.awt.Desktop.isDesktopSupported()) {
            java.awt.Desktop.getDesktop().open(dir)
        }
    } catch (_: Exception) {
        // Best effort — if Desktop.open fails, just skip
    }
}

/**
 * Starts a new import from the given file, optionally with a batch of files for folder mode. Resets
 * wizard state and launches the load+detect pipeline.
 */
private fun startNewImport(
    state: PhotoScanWizardState,
    file: File,
    batchFiles: List<File>?,
    detectorService: PhotoScanDetectorPort,
    appLogger: AppLogger,
    dispatcherProvider: DispatcherProvider,
    isLoading: (Boolean) -> Unit,
    onMessage: (String) -> Unit,
    onError: (String) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    state.resetToImportStep()
    scope.launch {
        val isSinglePhoto = state.singlePhotoMode.value && batchFiles == null
        if (batchFiles != null && batchFiles.size > 1) {
            state.initializeBatch(batchFiles)
        }
        if (isSinglePhoto) {
            // Single photo mode: load image, skip detection, go straight to Quick Edit
            withContext(dispatcherProvider.io) {
                try {
                    isLoading(true)
                    onMessage("Loading image...")
                    val image = javax.imageio.ImageIO.read(file)
                    if (image != null) {
                        state.initializeSinglePhoto(image, file)
                        isLoading(false)
                    } else {
                        isLoading(false)
                        onError("Failed to load image: unsupported format")
                    }
                } catch (e: Exception) {
                    isLoading(false)
                    onError("Failed to load image: ${e.message}")
                }
            }
        } else {
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
    }
}

/**
 * Continues to the next photo in a batch folder import. Advances the batch index, resets per-image
 * state, and launches the load+detect pipeline for the next file.
 */
private fun continueToNextBatchPhoto(
    state: PhotoScanWizardState,
    detectorService: PhotoScanDetectorPort,
    appLogger: AppLogger,
    dispatcherProvider: DispatcherProvider,
    isLoading: (Boolean) -> Unit,
    onMessage: (String) -> Unit,
    onError: (String) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    // Auto-skip files that have been marked as "backs" of other photos
    var nextFile = state.advanceToNextBatchFile()
    while (
        nextFile != null && state.skippedBatchIndices.value.contains(state.currentImageIndex.value)
    ) {
        nextFile = state.advanceToNextBatchFile()
    }
    if (nextFile == null) return
    state.resetPerImageState()
    scope.launch {
        loadImageAndDetect(
            state = state,
            file = nextFile,
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
}

/**
 * Skips the next photo in the batch by advancing the batch index without processing it. The user
 * stays on the COMPLETE screen — the UI recomposes with an updated preview of the next photo and
 * updated progress. This is for skipping photos that shouldn't be processed (e.g., the back of a
 * physical photo).
 */
private fun skipNextBatchPhoto(state: PhotoScanWizardState) {
    state.skipNextBatchFile()
}

private fun validateExportDestination(destinationPath: String): String? {
    val outputDir = File(destinationPath)
    return when {
        outputDir.exists() && !outputDir.isDirectory ->
            "Path exists but is not a directory: $destinationPath"
        outputDir.exists() && !outputDir.canWrite() -> "Cannot write to folder: $destinationPath"
        outputDir.exists() && !outputDir.canExecute() -> "Cannot access folder: $destinationPath"
        !outputDir.exists() && File(destinationPath).parentFile?.canWrite() != true ->
            "Cannot create folder in: ${File(destinationPath).parentFile?.absolutePath ?: destinationPath}"
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
): ExportResult {
    val progress = (index + 1).toFloat() / totalCount
    onProgress(progress * 0.9f, fileName)

    val perspectiveEnabled = state.perspectiveCorrectionEnabled.value
    val marginFraction = state.exportMarginPercent.value

    val corrections = mutableListOf<String>()
    corrections.add(if (perspectiveEnabled) "Warp-stretch" else "Simple crop")
    if (marginFraction > 0) corrections.add("Margin ${(marginFraction * 100).toInt()}%")
    if (config.rotationDegrees != 0) corrections.add("Rotation ${config.rotationDegrees}°")

    appLogger.logOperationStart(
        OperationType.EXPORT_PHOTO,
        "Photo ${index + 1}/${totalCount}: $fileName " +
            "(${corrections.joinToString(", ").ifEmpty { "no corrections" }})",
    )

    // PhotoConfiguration is now a typealias for PhotoScanConfiguration — no bridge needed.
    val scanConfig = config

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
            applyPerspectiveCorrection = perspectiveEnabled,
            rotation = rotationFromDegrees(config.rotationDegrees),
            configuration = scanConfig,
        )

    val sourceFile = state.imageFile.value

    return withContext(dispatcherProvider.default) {
        try {
            val result =
                exportService.exportSinglePhoto(
                    image.toProcessedImage(),
                    detectedPhoto,
                    outputDir.absolutePath,
                    fileName,
                    sourceFile = sourceFile?.let { FilePath(it.absolutePath) },
                )
            ExportResult.Success(
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
            ExportResult.Failure(
                originalFile = state.imageFile.value ?: File(""),
                errorMessage = e.message ?: "Unknown error",
                correctionsApplied = listOf("Failed: ${e.message}"),
            )
        }
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
    onComplete: (List<ProcessedPhoto>) -> Unit,
    dispatcherProvider: DispatcherProvider,
) {
    isLoading(true)

    try {
        val boxes = state.boxes
        val configurations = state.photoConfigurations.value

        appLogger.logOperationStart(
            OperationType.EXPORT_START,
            "Destination: $destinationPath, ${boxes.size} ${if (boxes.size == 1) "photo" else "photos"}",
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
        val globalStrategy = state.defaultCorrectionStrategy.value
        val results = mutableListOf<ExportResult>()

        boxes.forEachIndexed { index, box ->
            val fileName = if (boxes.size > 1) "${baseName}_${index + 1}" else baseName
            val rawConfig = configurations[box.id] ?: PhotoConfiguration()
            // Apply global default strategy when per-photo strategy is not set
            val config =
                if (rawConfig.correctionStrategy == null)
                    rawConfig.copy(correctionStrategy = globalStrategy)
                else rawConfig
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
        onComplete(results.map { it.toProcessedPhoto() })
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

/**
 * In-progress processing screen. Shows a progress indicator and current file. Completion UI is on
 * the separate [CompletionScreen] at the COMPLETE wizard step.
 *
 * Canceling requires confirmation since it discards all export progress.
 */
@Composable
private fun ProcessingScreen(
    progress: Float,
    currentFile: String,
    totalPhotos: Int,
    destination: String,
    onBack: () -> Unit,
) {
    var showCancelConfirm by remember { mutableStateOf(false) }

    // Derive current photo index from progress fraction
    val currentIndex = (progress * totalPhotos).toInt().coerceIn(1, totalPhotos).let {
        if (progress >= 1f) totalPhotos else it
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                Icons.Default.HourglassEmpty,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )

            Text("Exporting", style = MaterialTheme.typography.headlineSmall)

            if (totalPhotos > 0) {
                Text(
                    "Photo $currentIndex of $totalPhotos",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(0.8f),
            )

            Text(
                if (currentFile.isNotEmpty()) "Processing: $currentFile" else "Finalizing...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (destination.isNotBlank()) {
                Text(
                    "→ $destination",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedButton(
                onClick = { showCancelConfirm = true },
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
            ) {
                Text("Cancel")
            }
        }
    }

    // Cancel confirmation dialog
    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text("Cancel Export?") },
            text = {
                Text(
                    "Canceling will discard all progress and return to the import screen. " +
                        "Any photos already exported to disk will remain."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelConfirm = false
                        onBack()
                    }
                ) {
                    Text("Cancel Export", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }) { Text("Continue Export") }
            },
        )
    }
}

