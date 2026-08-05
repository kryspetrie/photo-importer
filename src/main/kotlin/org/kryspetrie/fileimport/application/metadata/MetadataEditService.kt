package org.kryspetrie.fileimport.application.metadata

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.kryspetrie.fileimport.application.export.MetadataRotationHelper
import org.kryspetrie.fileimport.application.export.MetadataWriteException
import org.kryspetrie.fileimport.application.export.MetadataWritingService
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.ImageFile
import org.kryspetrie.fileimport.domain.model.ImageFileType
import org.kryspetrie.fileimport.domain.model.MetadataEditEntry
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration
import org.kryspetrie.fileimport.domain.model.ProcessedImage
import org.kryspetrie.fileimport.domain.model.RotationAngle
import org.kryspetrie.fileimport.domain.port.FileSystemPort
import org.kryspetrie.fileimport.domain.port.ImageProcessingPort
import org.kryspetrie.fileimport.domain.port.ImageRepositoryPort

/**
 * Application service for saving metadata edits in the bulk metadata editor.
 *
 * Extracts the save/write logic from the UI layer so that the composable only orchestrates UI state
 * transitions. This service handles:
 * - Creating backups before overwrite
 * - Writing metadata (EXIF/IPTC/XMP) via [MetadataWritingService]
 * - Physically rotating image pixels when [PhotoScanConfiguration.rotationDegrees] is non-zero
 * - Writing back-of-photo images
 * - Recording journal entries for undo/redo
 * - Optimizing: when only metadata changed (no rotation/perspective/back-image), uses the lossless
 *   metadata-only write path and skips loading the full image into memory.
 *
 * ## Rotation handling
 *
 * JPEG/TIFF: non-zero [PhotoScanConfiguration.rotationDegrees] physically rotates pixels (lossy for
 * JPEG), then sets EXIF Orientation to Normal (1).
 *
 * RAW and other in-place metadata formats: rotation updates EXIF Orientation only — sensor data is
 * never re-encoded.
 *
 * Follows the hexagonal architecture: application layer depends only on domain ports and domain
 * models, never on UI or infrastructure adapters.
 */
class MetadataEditService(
    private val metadataWritingService: MetadataWritingService,
    private val imageProcessing: ImageProcessingPort,
    private val imageRepository: ImageRepositoryPort,
    private val fileSystem: FileSystemPort,
    private val undoService: MetadataEditUndoService,
) {

    /**
     * Result of saving a single file's metadata.
     *
     * @property entry The journal entry recording what was done (for undo support).
     * @property outputFilePath The output file path (non-null for SAVE_NEW mode).
     * @property backImageOutputPath The back image output path, if a back image was written.
     */
    data class SaveResult(
        val entry: MetadataEditEntry,
        val outputFilePath: String? = null,
        val backImageOutputPath: String? = null,
    )

    /**
     * Saves metadata for a single file.
     *
     * Handles both OVERWRITE and SAVE_NEW modes:
     * - OVERWRITE: creates a backup, overwrites the original file.
     * - SAVE_NEW: writes to the output directory.
     *
     * Optimizes for metadata-only edits: when only metadata changed (no rotation, perspective
     * correction, or back-image), the image is not decoded from JPEG — metadata writers operate
     * directly on the file bytes, preserving image quality.
     *
     * When rotation is applied on JPEG/TIFF, pixels are physically rotated and orientation is reset
     * to Normal. On RAW and similar formats, only the EXIF Orientation tag is updated.
     *
     * @param file The source image file.
     * @param config The metadata configuration to apply.
     * @param outputMode "OVERWRITE" or "SAVE_NEW".
     * @param outputDirectory The output directory for SAVE_NEW mode (unused for OVERWRITE).
     * @return The save result with journal entry, or null if the image could not be read.
     */
    suspend fun saveFile(
        file: File,
        config: PhotoScanConfiguration,
        outputMode: String,
        outputDirectory: String,
    ): SaveResult? {
        // Create backup before overwrite (for undo support)
        var backImageBackupPath: String? = null
        var backImageOutputPath: String? = null
        var entryOutputPath: String? = null

        val backupPath =
            if (outputMode == "OVERWRITE") {
                val bp = undoService.createBackup(file.absolutePath)
                if (bp == null) {
                    System.err.println(
                        "[MetadataEditService] ERROR: Backup creation failed for ${file.absolutePath}. Aborting save to prevent data loss."
                    )
                    return null
                }
                bp
            } else null

        // Determine if we need to decode the image at all.
        val fileType = ImageFileType.fromExtension(file.extension)
        val rotationAngle = rotationDegreesToAngle(config.rotationDegrees)
        val pixelRotation =
            MetadataRotationHelper.usesPixelRotation(fileType, config.rotationDegrees)
        val imageModified =
            pixelRotation || config.perspectiveCorrectionEnabled || config.hasBackImage()
        val needsImageDecoding = imageModified

        if (needsImageDecoding) {
            // ── Image-modifying path: decode, optionally rotate pixels, then write ──
            val sourceImage =
                withContext(Dispatchers.IO) {
                    imageProcessing.readImage(FilePath(file.absolutePath))
                } ?: return null

            // Pre-rotation dimensions for face region coordinate transforms.
            val preRotationWidth = sourceImage.width
            val preRotationHeight = sourceImage.height

            // Physically rotate pixels only when the format supports it (JPEG/TIFF).
            val finalImage =
                if (pixelRotation) {
                    imageProcessing.rotateImage(sourceImage, rotationAngle)
                } else {
                    sourceImage
                }

            // After physical rotation, pixels are upright — clear rotationDegrees for the write.
            val writeConfig =
                if (pixelRotation) {
                    config.copy(rotationDegrees = 0)
                } else {
                    config
                }

            when (outputMode) {
                "OVERWRITE" -> {
                    val outputPath = FilePath(file.absolutePath)
                    val backFile = File(file.parent, file.nameWithoutExtension + "_back.jpg")
                    if (backFile.exists() && backupPath != null) {
                        backImageBackupPath =
                            withContext(Dispatchers.IO) {
                                undoService.createBackup(backFile.absolutePath)
                            }
                    }
                    metadataWritingService.writeImageWithMetadata(
                        image = finalImage,
                        outputPath = outputPath,
                        config = writeConfig,
                        sourcePath = FilePath(file.absolutePath),
                        preRotationWidth = preRotationWidth,
                        preRotationHeight = preRotationHeight,
                        physicalPixelRotationApplied = pixelRotation,
                    )
                    if (config.hasBackImage()) {
                        backImageOutputPath =
                            writeBackImage(
                                finalImage,
                                config,
                                file.parent,
                                file.nameWithoutExtension,
                            )
                    }
                }
                "SAVE_NEW" -> {
                    val outDir = outputDirectory.ifBlank { file.parent }
                    val outputFileName = file.nameWithoutExtension + ".jpg"
                    val outputPath = FilePath(File(outDir, outputFileName).absolutePath)
                    entryOutputPath = outputPath.path
                    File(outDir).mkdirs()
                    metadataWritingService.writeImageWithMetadata(
                        image = finalImage,
                        outputPath = outputPath,
                        config = writeConfig,
                        sourcePath = FilePath(file.absolutePath),
                        preRotationWidth = preRotationWidth,
                        preRotationHeight = preRotationHeight,
                        physicalPixelRotationApplied = pixelRotation,
                    )
                    if (config.hasBackImage()) {
                        val backOutDir =
                            if (outputDirectory.isNotBlank()) outputDirectory else file.parent
                        backImageOutputPath =
                            writeBackImage(
                                finalImage,
                                config,
                                backOutDir,
                                file.nameWithoutExtension,
                            )
                    }
                }
            }
        } else {
            // ── Metadata-only path: in-place write, no pixel re-encoding ──
            val meta =
                withContext(Dispatchers.IO) {
                    imageRepository.getMetadata(
                        ImageFile(path = FilePath(file.absolutePath), fileSize = file.length())
                    )
                }
            val imageWidth = meta?.imageWidth ?: 0
            val imageHeight = meta?.imageHeight ?: 0

            when (outputMode) {
                "OVERWRITE" -> {
                    val outputPath = FilePath(file.absolutePath)
                    val backFile = File(file.parent, file.nameWithoutExtension + "_back.jpg")
                    if (backFile.exists() && backupPath != null) {
                        backImageBackupPath =
                            withContext(Dispatchers.IO) {
                                undoService.createBackup(backFile.absolutePath)
                            }
                    }
                    metadataWritingService.writeMetadataOnly(
                        outputPath = outputPath,
                        config = config,
                        sourcePath = FilePath(file.absolutePath),
                        preRotationWidth = imageWidth,
                        preRotationHeight = imageHeight,
                    )
                }
                "SAVE_NEW" -> {
                    val outDir = outputDirectory.ifBlank { file.parent }
                    val outputFileName = file.name
                    val outputPath = FilePath(File(outDir, outputFileName).absolutePath)
                    entryOutputPath = outputPath.path
                    File(outDir).mkdirs()
                    fileSystem.copy(FilePath(file.absolutePath), outputPath)
                    metadataWritingService.writeMetadataOnly(
                        outputPath = outputPath,
                        config = config,
                        sourcePath = FilePath(file.absolutePath),
                        preRotationWidth = imageWidth,
                        preRotationHeight = imageHeight,
                    )
                }
            }
        }

        val entry =
            MetadataEditEntry(
                filePath = file.absolutePath,
                backupPath = backupPath ?: "",
                configSnapshot = config,
                wasSavedNew = outputMode == "SAVE_NEW",
                outputFilePath = entryOutputPath ?: "",
                backImageBackupPath = backImageBackupPath,
                backImageOutputPath = backImageOutputPath,
            )

        return SaveResult(
            entry = entry,
            outputFilePath = entryOutputPath,
            backImageOutputPath = backImageOutputPath,
        )
    }

    /**
     * Re-applies a saved metadata configuration without creating journal entries.
     *
     * Used by undo/redo when the caller has already created a fresh backup.
     */
    suspend fun reapplyMetadata(
        filePath: FilePath,
        config: PhotoScanConfiguration,
        exifSourcePath: FilePath?,
    ) {
        val file = File(filePath.path)
        val fileType = ImageFileType.fromExtension(file.extension)
        val rotationAngle = rotationDegreesToAngle(config.rotationDegrees)
        val pixelRotation =
            MetadataRotationHelper.usesPixelRotation(fileType, config.rotationDegrees)
        val needsImageDecoding =
            pixelRotation || config.perspectiveCorrectionEnabled || config.hasBackImage()

        if (needsImageDecoding) {
            val sourceImage =
                withContext(Dispatchers.IO) {
                    imageProcessing.readImage(FilePath(file.absolutePath))
                } ?: throw MetadataWriteException("Could not read image: ${file.absolutePath}")

            val preRotationWidth = sourceImage.width
            val preRotationHeight = sourceImage.height
            val finalImage =
                if (pixelRotation) {
                    imageProcessing.rotateImage(sourceImage, rotationAngle)
                } else {
                    sourceImage
                }
            val writeConfig =
                if (pixelRotation) {
                    config.copy(rotationDegrees = 0)
                } else {
                    config
                }
            metadataWritingService.writeImageWithMetadata(
                image = finalImage,
                outputPath = filePath,
                config = writeConfig,
                sourcePath = exifSourcePath ?: filePath,
                preRotationWidth = preRotationWidth,
                preRotationHeight = preRotationHeight,
                physicalPixelRotationApplied = pixelRotation,
            )
        } else {
            val meta =
                withContext(Dispatchers.IO) {
                    imageRepository.getMetadata(
                        ImageFile(path = FilePath(file.absolutePath), fileSize = file.length())
                    )
                }
            metadataWritingService.writeMetadataOnly(
                outputPath = filePath,
                config = config,
                sourcePath = exifSourcePath ?: filePath,
                preRotationWidth = meta?.imageWidth ?: 0,
                preRotationHeight = meta?.imageHeight ?: 0,
            )
        }
    }

    /**
     * Saves a back-of-photo image.
     *
     * @param processedImage The front image (used for max dimensions).
     * @param config The configuration containing back image settings.
     * @param outputDir The directory to write the back image to.
     * @param baseName The base name (without extension) for the output file.
     * @return The output file path, or null if no back image was written.
     */
    private suspend fun writeBackImage(
        processedImage: ProcessedImage,
        config: PhotoScanConfiguration,
        outputDir: String,
        baseName: String,
    ): String? {
        val backImageResult =
            imageProcessing.prepareBackImage(
                config,
                maxWidth = processedImage.width,
                maxHeight = processedImage.height,
            ) ?: return null

        val backFileName = "${baseName}_back.jpg"
        val backOutputFilePath = FilePath(File(outputDir, backFileName).absolutePath)
        File(outputDir).mkdirs()
        imageProcessing.writeJpegImage(backImageResult, backOutputFilePath)
        return backOutputFilePath.path
    }

    /**
     * Saves a batch of journal entries and returns the journal file path.
     *
     * @param sourceFolderPath The source folder/file path being edited.
     * @param outputMode The output mode name ("OVERWRITE" or "SAVE_NEW").
     * @param entries The list of journal entries from individual save operations.
     * @return The journal file path, or null if saving failed.
     */
    fun saveJournal(
        sourceFolderPath: String,
        outputMode: String,
        entries: List<MetadataEditEntry>,
    ): String? {
        return undoService.saveJournalPath(
            sourceFolderPath = sourceFolderPath,
            outputMode = outputMode,
            entries = entries,
        )
    }

    /** Convert integer rotation degrees (0, 90, 180, 270) to a [RotationAngle] enum. */
    internal fun rotationDegreesToAngle(degrees: Int): RotationAngle =
        when ((degrees % 360 + 360) % 360) {
            90 -> RotationAngle.CW_90
            180 -> RotationAngle.CW_180
            270 -> RotationAngle.CCW_90
            else -> RotationAngle.NONE
        }
}
