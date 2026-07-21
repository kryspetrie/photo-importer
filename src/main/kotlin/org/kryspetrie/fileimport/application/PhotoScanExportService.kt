package org.kryspetrie.fileimport.application

import org.kryspetrie.fileimport.application.export.FilenameResolver
import org.kryspetrie.fileimport.application.export.MetadataWritingService
import org.kryspetrie.fileimport.domain.model.CorrectionStrategy
import org.kryspetrie.fileimport.domain.model.DetectedPhoto
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.GeometryUtils
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration
import org.kryspetrie.fileimport.domain.model.PhotoScanExportResult
import org.kryspetrie.fileimport.domain.model.PhotoScanExportedFile
import org.kryspetrie.fileimport.domain.model.PhotoScanSingleExportResult
import org.kryspetrie.fileimport.domain.model.ProcessedImage
import org.kryspetrie.fileimport.domain.model.RotationAngle
import org.kryspetrie.fileimport.domain.model.determineCorrectionStrategy
import org.kryspetrie.fileimport.domain.port.FileSystemPort
import org.kryspetrie.fileimport.domain.port.ImageProcessingPort
import org.kryspetrie.fileimport.domain.port.PerspectiveCorrectionPort
import org.kryspetrie.fileimport.domain.port.PhotoScanExportPort

/**
 * Thin orchestrator for exporting extracted photos with EXIF metadata preservation and
 * modification.
 *
 * Delegates to specialized services and ports:
 * - [PerspectiveCorrectionPort] — perspective warping
 * - [ImageProcessingPort] — crop, rotate, composite, and JPEG writing
 * - [MetadataWritingService] — layering EXIF, IPTC, and XMP metadata onto JPEG files
 * - [FilenameResolver] — filename conflict resolution
 *
 * All image operations use [ProcessedImage] via [ImageProcessingPort], keeping this service free of
 * `java.awt.image.BufferedImage` imports. All file operations use [FilePath] via [FileSystemPort],
 * keeping this service free of `java.io.File` imports.
 *
 * @see DetectedPhoto
 * @see PhotoScanConfiguration
 */
class PhotoScanExportService(
    private val perspectiveService: PerspectiveCorrectionPort,
    private val metadataWritingService: MetadataWritingService,
    private val imageProcessing: ImageProcessingPort,
    private val fileSystem: FileSystemPort,
) : PhotoScanExportPort {

    // Type aliases for backward compatibility — actual types now live in domain/model
    typealias ExportResult = PhotoScanExportResult

    typealias SingleExportResult = PhotoScanSingleExportResult

    typealias ExportedFile = PhotoScanExportedFile

    /** Exports all detected photos from a scanned image. */
    override suspend fun exportPhotos(
        sourceFile: FilePath,
        image: ProcessedImage,
        detectedPhotos: List<DetectedPhoto>,
        destinationPath: String,
        baseFileName: String,
    ): ExportResult {
        val errors = mutableListOf<String>()
        val exportedFiles = mutableListOf<ExportedFile>()

        // Validate source file exists before proceeding
        if (!fileSystem.exists(sourceFile)) {
            errors.add("Source file does not exist: ${fileSystem.absolutePath(sourceFile)}")
            return ExportResult(success = false, errors = errors)
        }

        val destDir = FilePath(destinationPath)
        // Ensure destination directory exists
        fileSystem.mkdirs(destDir)

        for ((index, photo) in detectedPhotos.withIndex()) {
            try {
                val result =
                    processPhoto(sourceImage = image, detectedPhoto = photo, marginFraction = photo.configuration.cropMarginFraction.toDouble())

                val fileName =
                    if (detectedPhotos.size > 1) "${baseFileName}_${index + 1}.jpg"
                    else "${baseFileName}.jpg"

                val resolvedPath =
                    FilenameResolver.resolveFilenameConflict(fileSystem, destDir, fileName)

                metadataWritingService.writeImageWithMetadata(
                    image = result.compositedImage,
                    outputPath = FilePath(resolvedPath),
                    config = photo.configuration,
                    sourcePath = sourceFile,
                    detectedPhoto = result.marginedPhoto,
                    marginFraction = photo.configuration.cropMarginFraction.toDouble(),
                    sourceImage = image,
                    preRotationWidth = result.preRotationWidth,
                    preRotationHeight = result.preRotationHeight,
                )

                exportedFiles.add(
                    ExportedFile(
                        sourceFile = sourceFile,
                        destinationPath = resolvedPath,
                        photoId = photo.id,
                        width = result.compositedImage.width,
                        height = result.compositedImage.height,
                        fileSize = fileSystem.length(FilePath(resolvedPath)),
                    )
                )

                // Export back image as separate "_back" file if mode is append_back
                if (result.backMode == "append_back" && photo.configuration.hasBackImage()) {
                    val backImageResult =
                        imageProcessing.prepareBackImage(
                            photo.configuration,
                            maxWidth = result.compositedImage.width,
                            maxHeight = result.compositedImage.height,
                        )
                    if (backImageResult != null) {
                        val backFileName =
                            if (detectedPhotos.size > 1) "${baseFileName}_${index + 1}_back.jpg"
                            else "${baseFileName}_back.jpg"
                        val backResolvedPath =
                            FilenameResolver.resolveFilenameConflict(
                                fileSystem,
                                destDir,
                                backFileName,
                            )
                        imageProcessing.writeJpegImage(backImageResult, FilePath(backResolvedPath))
                        exportedFiles.add(
                            ExportedFile(
                                sourceFile = sourceFile,
                                destinationPath = backResolvedPath,
                                photoId = photo.id,
                                width = backImageResult.width,
                                height = backImageResult.height,
                                fileSize = fileSystem.length(FilePath(backResolvedPath)),
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                errors.add("Failed to export photo ${index + 1}: ${e.message}")
            }
        }

        return ExportResult(
            success = errors.isEmpty(),
            exportedFiles = exportedFiles,
            errors = errors,
        )
    }

    /** Exports a single photo with the given configuration. */
    override suspend fun exportSinglePhoto(
        sourceImage: ProcessedImage,
        detectedPhoto: DetectedPhoto,
        destinationPath: String,
        baseFileName: String,
        sourceFile: FilePath?,
    ): SingleExportResult {
        return try {
            val result =
                processPhoto(
                    sourceImage = sourceImage,
                    detectedPhoto = detectedPhoto,
                    marginFraction = detectedPhoto.configuration.cropMarginFraction.toDouble(),
                )

            val destDir = FilePath(destinationPath)
            fileSystem.mkdirs(destDir)

            val resolvedPath =
                FilenameResolver.resolveFilenameConflict(fileSystem, destDir, "$baseFileName.jpg")

            metadataWritingService.writeImageWithMetadata(
                image = result.compositedImage,
                outputPath = FilePath(resolvedPath),
                config = detectedPhoto.configuration,
                sourcePath = sourceFile,
                detectedPhoto = result.marginedPhoto,
                marginFraction = detectedPhoto.configuration.cropMarginFraction.toDouble(),
                sourceImage = sourceImage,
                preRotationWidth = result.preRotationWidth,
                preRotationHeight = result.preRotationHeight,
            )

            // Export back image as separate "_back" file if mode is append_back
            if (result.backMode == "append_back" && detectedPhoto.configuration.hasBackImage()) {
                val backImageResult =
                    imageProcessing.prepareBackImage(
                        detectedPhoto.configuration,
                        maxWidth = result.compositedImage.width,
                        maxHeight = result.compositedImage.height,
                    )
                if (backImageResult != null) {
                    val backResolvedPath =
                        FilenameResolver.resolveFilenameConflict(
                            fileSystem,
                            destDir,
                            "${baseFileName}_back.jpg",
                        )
                    imageProcessing.writeJpegImage(backImageResult, FilePath(backResolvedPath))
                }
            }

            SingleExportResult(
                success = true,
                destinationPath = resolvedPath,
                width = result.compositedImage.width,
                height = result.compositedImage.height,
            )
        } catch (e: Exception) {
            SingleExportResult(
                success = false,
                destinationPath = "",
                width = 0,
                height = 0,
                error = e.message,
            )
        }
    }

    /**
     * Shared processing pipeline for a single photo: apply margin, determine correction strategy,
     * correct/crop, rotate, and handle back-image compositing.
     *
     * Returns a [ProcessedPhoto] containing all intermediate and final results needed for metadata
     * writing and file export.
     */
    private fun processPhoto(
        sourceImage: ProcessedImage,
        detectedPhoto: DetectedPhoto,
        marginFraction: Double,
    ): ProcessedPhoto {
        val marginedPhoto = GeometryUtils.applyMargin(detectedPhoto, marginFraction)
        val strategy =
            detectedPhoto.configuration.correctionStrategy
                ?: if (marginedPhoto.applyPerspectiveCorrection) CorrectionStrategy.PERSPECTIVE
                else determineCorrectionStrategy(marginedPhoto.toListOfCorners())

        val correctedImage =
            when (strategy) {
                CorrectionStrategy.PERSPECTIVE ->
                    perspectiveService.correctPerspective(sourceImage, marginedPhoto)
                CorrectionStrategy.CROP_AND_ROTATE ->
                    imageProcessing.cropAxisAligned(sourceImage, marginedPhoto)
                CorrectionStrategy.CROP ->
                    imageProcessing.cropAxisAligned(sourceImage, marginedPhoto)
            }

        val finalImage =
            if (detectedPhoto.rotation != RotationAngle.NONE) {
                imageProcessing.rotateImage(correctedImage, detectedPhoto.rotation)
            } else {
                correctedImage
            }

        // Handle back-of-photo compositing
        val backMode = detectedPhoto.configuration.backImageMode
        val compositedImage =
            if (backMode == "combine" && detectedPhoto.configuration.hasBackImage()) {
                imageProcessing.compositeBackImage(finalImage, detectedPhoto.configuration)
            } else {
                finalImage
            }

        return ProcessedPhoto(
            marginedPhoto = marginedPhoto,
            preRotationWidth = correctedImage.width,
            preRotationHeight = correctedImage.height,
            compositedImage = compositedImage,
            backMode = backMode,
        )
    }

    /** Intermediate results from processing a single photo through the pipeline. */
    private data class ProcessedPhoto(
        val marginedPhoto: DetectedPhoto,
        val preRotationWidth: Int,
        val preRotationHeight: Int,
        val compositedImage: ProcessedImage,
        val backMode: String?,
    )
}
