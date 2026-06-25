package org.kryspetrie.fileimport.ui.screens.wizard

import java.awt.image.BufferedImage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.kryspetrie.fileimport.application.PhotoScanExportService
import org.kryspetrie.fileimport.domain.model.DetectedPhoto
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.PhotoCorner
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration
import org.kryspetrie.fileimport.domain.port.DispatcherProvider
import org.kryspetrie.fileimport.infrastructure.adapter.toProcessedImage
import org.kryspetrie.fileimport.infrastructure.logging.AppLogger
import org.kryspetrie.fileimport.infrastructure.logging.OperationType
import org.kryspetrie.fileimport.ui.wizard.state.BoundingBox
import org.kryspetrie.fileimport.ui.wizard.state.PhotoScanConstants
import org.kryspetrie.fileimport.ui.wizard.state.PhotoScanWizardState
import org.kryspetrie.fileimport.ui.screens.wizard.rotationFromDegrees

/**
 * Orchestrates the photo scan export pipeline: validates destination, iterates through detected
 * boxes, applies configuration, and delegates to [PhotoScanExportService].
 *
 * Extracted from [WizardContainer] to keep UI composable code separate from orchestration logic.
 */

/** Validates the export destination directory. Returns an error string if invalid, or null if valid. */
fun validateExportDestination(destinationPath: String): String? {
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

/** Opens the export destination folder in the system file browser. Best-effort — silently skips on failure. */
fun openExportFolder(exportDestination: String) {
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
 * Exports a single photo from the scan image based on the given bounding box and configuration.
 *
 * @return [ExportResult.Success] with output path and dimensions, or [ExportResult.Failure] with
 *   error message.
 */
suspend fun exportSinglePhoto(
    image: BufferedImage,
    box: BoundingBox,
    config: PhotoScanConfiguration,
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

    val perspectiveEnabled = state.exportSettings.perspectiveCorrectionEnabled.value
    val marginFraction = state.exportSettings.exportMarginPercent.value

    val corrections = mutableListOf<String>()
    corrections.add(if (perspectiveEnabled) "Warp-stretch" else "Simple crop")
    if (marginFraction > 0) corrections.add("Margin ${(marginFraction * 100).toInt()}%")
    if (config.rotationDegrees != 0) corrections.add("Rotation ${config.rotationDegrees}°")

    appLogger.logOperationStart(
        OperationType.EXPORT_PHOTO,
        "Photo ${index + 1}/${totalCount}: $fileName " +
            "(${corrections.joinToString(", ").ifEmpty { "no corrections" }})",
    )

    // PhotoScanConfiguration is now a typealias for PhotoScanConfiguration — no bridge needed.
    val scanConfig = config

    val detectedPhoto =
        DetectedPhoto(
            topLeft = PhotoCorner(box.corners.topLeft.x.toFloat(), box.corners.topLeft.y.toFloat()),
            topRight =
                PhotoCorner(box.corners.topRight.x.toFloat(), box.corners.topRight.y.toFloat()),
            bottomLeft =
                PhotoCorner(box.corners.bottomLeft.x.toFloat(), box.corners.bottomLeft.y.toFloat()),
            bottomRight =
                PhotoCorner(box.corners.bottomRight.x.toFloat(), box.corners.bottomRight.y.toFloat()),
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

/**
 * Exports all detected photos from the scan image. Validates the destination, checks disk space,
 * iterates through bounding boxes, and calls [exportSinglePhoto] for each.
 */
suspend fun exportPhotos(
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
        val boxes = state.configs.boxes
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
        val globalStrategy = state.exportSettings.defaultCorrectionStrategy.value
        val results = mutableListOf<ExportResult>()

        boxes.forEachIndexed { index, box ->
            val fileName = if (boxes.size > 1) "${baseName}_${index + 1}" else baseName
            val rawConfig = configurations[box.id] ?: PhotoScanConfiguration()
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