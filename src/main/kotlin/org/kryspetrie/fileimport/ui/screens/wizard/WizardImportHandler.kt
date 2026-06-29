package org.kryspetrie.fileimport.ui.screens.wizard

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.kryspetrie.fileimport.domain.port.DispatcherProvider
import org.kryspetrie.fileimport.domain.port.PhotoScanDetectorPort
import org.kryspetrie.fileimport.infrastructure.adapter.toProcessedImage
import org.kryspetrie.fileimport.infrastructure.logging.AppLogger
import org.kryspetrie.fileimport.infrastructure.logging.OperationType
import org.kryspetrie.fileimport.domain.model.geometry.BoundingBox
import org.kryspetrie.fileimport.domain.model.geometry.BoundingBoxCorners
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration
import org.kryspetrie.fileimport.ui.wizard.state.PhotoScanWizardState
import org.kryspetrie.fileimport.domain.model.geometry.Point
import org.kryspetrie.fileimport.ui.components.isImageFile

/**
 * Handles image loading, detection, and batch navigation for the photo scan wizard.
 *
 * Extracted from [WizardContainer] to keep UI composable code separate from orchestration logic.
 */

/** Collects image files from the given folder, sorted by name. Returns an empty list if the folder contains no supported image files. */
fun collectImageFiles(folder: File): List<File> {
    return folder.listFiles { f -> f.isFile && isImageFile(f) }?.sortedBy { it.name }?.toList()
        ?: emptyList()
}

/** Loads an image from file and runs detection if auto-detect is enabled. */
suspend fun loadImageAndDetect(
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
                                        photo.topLeft.toPoint(),
                                        photo.topRight.toPoint(),
                                        photo.bottomRight.toPoint(),
                                        photo.bottomLeft.toPoint(),
                                    )
                            )
                        }
                    // Carry detection mode from each DetectedPhoto into per-box configuration
                    val configs =
                        detectedPhotos.map { photo ->
                            PhotoScanConfiguration(
                                detectionMode = photo.detectionMode,
                                perspectiveCorrectionEnabled = photo.applyPerspectiveCorrection,
                            )
                        }

                    // Update state directly — Compose MutableState is thread-safe, and
                    // using withContext(Dispatchers.Main) drops MonotonicFrameClock from the
                    // coroutine context, which crashes Compose animation APIs.
                    if (boxes.isNotEmpty()) {
                        state.setDetectedBoxes(boxes, configs)
                        appLogger.logOperationComplete(
                            OperationType.IMAGE_DETECTION,
                            "Detected ${boxes.size} ${if (boxes.size == 1) "photo" else "photos"}",
                        )
                        onMessage("Detected ${boxes.size} ${if (boxes.size == 1) "photo" else "photos"}")
                    } else {
                        appLogger.info("No photos detected in ${file.name} - user can add manually")
                        onMessage("No photos detected. Add bounding boxes manually.")
                    }
                    onComplete()
                }
            } else {
                appLogger.info("CV auto-detection disabled - manual box placement expected")
                onMessage("Image loaded. Add bounding boxes manually.")
                onComplete()
            }
        } else {
            appLogger.logOperationFailed(OperationType.IMAGE_LOAD, "Unsupported image format: ${file.name}")
            // Direct call — Compose MutableState is thread-safe; withContext(Dispatchers.Main)
            // drops MonotonicFrameClock, crashing Compose animation components.
            onError("Failed to load image: unsupported format")
        }
    } catch (e: Exception) {
        appLogger.logOperationFailed(OperationType.IMAGE_LOAD, e.message ?: "Unknown error", e)
        onError("Error loading image: ${e.message}")
    } finally {
        isLoading(false)
    }
}

/**
 * Starts a new import from the given file, optionally with a batch of files for folder mode.
 * Resets wizard state and launches the load+detect pipeline.
 */
fun startNewImport(
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
        val isSinglePhoto = state.importSettings.singlePhotoMode.value && batchFiles == null
        if (batchFiles != null && batchFiles.size > 1) {
            state.batch.initializeBatch(batchFiles)
        }
        if (isSinglePhoto) {
            // Single photo mode: load image, skip detection, go straight to Quick Edit
            withContext(dispatcherProvider.io) {
                try {
                    isLoading(true)
                    onMessage("Loading image...")
                    val image = ImageIO.read(file)
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
                cvAutoDetect = state.importSettings.cvAutoDetectEnabled.value,
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
fun continueToNextBatchPhoto(
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
    var nextFile = state.batch.advanceToNextBatchFile()
    while (nextFile != null && state.batch.skippedBatchIndices.value.contains(state.batch.currentImageIndex.value)) {
        nextFile = state.batch.advanceToNextBatchFile()
    }
    if (nextFile == null) return
    state.resetPerImageState()
    scope.launch {
        loadImageAndDetect(
            state = state,
            file = nextFile,
            detectorService = detectorService,
            cvAutoDetect = state.importSettings.cvAutoDetectEnabled.value,
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
fun skipNextBatchPhoto(state: PhotoScanWizardState) {
    state.batch.skipNextBatchFile()
}