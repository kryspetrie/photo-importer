package org.kryspetrie.fileimport.application

import java.awt.image.BufferedImage
import java.io.File
import org.kryspetrie.fileimport.application.export.BackImageService
import org.kryspetrie.fileimport.application.export.FilenameResolver
import org.kryspetrie.fileimport.application.export.ImageTransformer
import org.kryspetrie.fileimport.application.export.JpegImageWriter
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
import org.kryspetrie.fileimport.domain.port.PerspectiveCorrectionPort
import org.kryspetrie.fileimport.domain.port.PhotoScanExportPort
import org.kryspetrie.fileimport.infrastructure.adapter.toBufferedImage
import org.kryspetrie.fileimport.infrastructure.adapter.toProcessedImage


/**
 * Thin orchestrator for exporting extracted photos with EXIF metadata preservation and modification.
 *
 * Delegates to specialized services:
 * - [PerspectiveCorrectionPort] — perspective warping
 * - [ImageTransformer] — axis-aligned crop and rotation
 * - [JpegImageWriter] — writing JPEG bytes
 * - [BackImageService] — loading, cropping, rotating, and compositing back-of-photo images
 * - [MetadataWritingService] — layering EXIF, IPTC, and XMP metadata onto JPEG files
 * - [FilenameResolver] — filename conflict resolution
 *
 * @see DetectedPhoto
 * @see PhotoScanConfiguration
 */
class PhotoScanExportService(
    private val perspectiveService: PerspectiveCorrectionPort,
    private val metadataWritingService: MetadataWritingService,
    private val jpegImageWriter: JpegImageWriter,
    private val backImageService: BackImageService,
) : PhotoScanExportPort {

    // Type aliases for backward compatibility — actual types now live in domain/model
    typealias ExportResult = PhotoScanExportResult

    typealias SingleExportResult = PhotoScanSingleExportResult

    typealias ExportedFile = PhotoScanExportedFile

    /** Exports all detected photos from a scanned image. */
    override fun exportPhotos(
        sourceFile: FilePath,
        image: ProcessedImage,
        detectedPhotos: List<DetectedPhoto>,
        destinationPath: String,
        baseFileName: String,
    ): ExportResult {
        val sourceJavaFile = sourceFile.toFile()
        val bufferedImage = image.toBufferedImage()
        val errors = mutableListOf<String>()
        val exportedFiles = mutableListOf<ExportedFile>()

        // Validate source file exists before proceeding
        if (!sourceJavaFile.exists()) {
            errors.add("Source file does not exist: ${sourceJavaFile.absolutePath}")
            return ExportResult(success = false, errors = errors)
        }

        for ((index, photo) in detectedPhotos.withIndex()) {
            try {
                val result = processPhoto(
                    sourceImage = bufferedImage,
                    detectedPhoto = photo,
                    sourceJavaFile = sourceJavaFile,
                    marginFraction = 0.02,
                )

                val fileName =
                    if (detectedPhotos.size > 1) "${baseFileName}_${index + 1}.jpg"
                    else "${baseFileName}.jpg"

                val resolvedPath =
                    FilenameResolver.resolveFilenameConflict(File(destinationPath), fileName)
                val outputFile = File(resolvedPath)

                metadataWritingService.writeImageWithMetadata(
                    image = result.compositedImage,
                    outputFile = outputFile,
                    config = photo.configuration,
                    sourceFile = sourceJavaFile,
                    detectedPhoto = result.marginedPhoto,
                    marginFraction = 0.02,
                    sourceImage = bufferedImage,
                    preRotationWidth = result.preRotationWidth,
                    preRotationHeight = result.preRotationHeight,
                    jpegQuality = jpegImageWriter.jpegQuality,
                )

                exportedFiles.add(
                    ExportedFile(
                        sourceFile = sourceFile,
                        destinationPath = resolvedPath,
                        photoId = photo.id,
                        width = result.compositedImage.width,
                        height = result.compositedImage.height,
                        fileSize = outputFile.length(),
                    )
                )

                // Export back image as separate "_back" file if mode is append_back
                if (result.backMode == "append_back" && photo.configuration.hasBackImage()) {
                    val backImageResult = backImageService.prepareBackImage(photo.configuration)
                    if (backImageResult != null) {
                        val backFileName =
                            if (detectedPhotos.size > 1) "${baseFileName}_${index + 1}_back.jpg"
                            else "${baseFileName}_back.jpg"
                        val backResolvedPath =
                            FilenameResolver.resolveFilenameConflict(
                                File(destinationPath),
                                backFileName,
                            )
                        val backOutputFile = File(backResolvedPath)
                        jpegImageWriter.writeJpegImage(backImageResult, backOutputFile)
                        exportedFiles.add(
                            ExportedFile(
                                sourceFile = sourceFile,
                                destinationPath = backResolvedPath,
                                photoId = photo.id,
                                width = backImageResult.width,
                                height = backImageResult.height,
                                fileSize = backOutputFile.length(),
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
    override fun exportSinglePhoto(
        sourceImage: ProcessedImage,
        detectedPhoto: DetectedPhoto,
        destinationPath: String,
        baseFileName: String,
        sourceFile: FilePath?,
    ): SingleExportResult {
        val bufferedImage = sourceImage.toBufferedImage()
        val sourceJavaFile = sourceFile?.toFile()

        return try {
            val result = processPhoto(
                sourceImage = bufferedImage,
                detectedPhoto = detectedPhoto,
                sourceJavaFile = sourceJavaFile,
                marginFraction = 0.02,
            )

            val resolvedPath =
                FilenameResolver.resolveFilenameConflict(File(destinationPath), "$baseFileName.jpg")
            val outputFile = File(resolvedPath)

            metadataWritingService.writeImageWithMetadata(
                image = result.compositedImage,
                outputFile = outputFile,
                config = detectedPhoto.configuration,
                sourceFile = sourceJavaFile,
                detectedPhoto = result.marginedPhoto,
                marginFraction = 0.02,
                sourceImage = bufferedImage,
                preRotationWidth = result.preRotationWidth,
                preRotationHeight = result.preRotationHeight,
                jpegQuality = jpegImageWriter.jpegQuality,
            )

            // Export back image as separate "_back" file if mode is append_back
            if (result.backMode == "append_back" && detectedPhoto.configuration.hasBackImage()) {
                val backImageResult = backImageService.prepareBackImage(detectedPhoto.configuration)
                if (backImageResult != null) {
                    val backResolvedPath =
                        FilenameResolver.resolveFilenameConflict(
                            File(destinationPath),
                            "${baseFileName}_back.jpg",
                        )
                    val backOutputFile = File(backResolvedPath)
                    jpegImageWriter.writeJpegImage(backImageResult, backOutputFile)
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
        sourceImage: BufferedImage,
        detectedPhoto: DetectedPhoto,
        sourceJavaFile: File?,
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
                    perspectiveService.correctPerspective(sourceImage.toProcessedImage(), marginedPhoto).toBufferedImage()
                CorrectionStrategy.CROP_AND_ROTATE ->
                    ImageTransformer.cropAxisAligned(sourceImage, marginedPhoto)
                CorrectionStrategy.CROP ->
                    ImageTransformer.cropAxisAligned(sourceImage, marginedPhoto)
            }

        val finalImage =
            if (detectedPhoto.rotation != RotationAngle.NONE) {
                ImageTransformer.rotateImage(correctedImage, detectedPhoto.rotation)
            } else {
                correctedImage
            }

        // Handle back-of-photo compositing
        val backMode = detectedPhoto.configuration.backImageMode
        val compositedImage =
            if (backMode == "combine" && detectedPhoto.configuration.hasBackImage()) {
                backImageService.compositeBackImage(finalImage, detectedPhoto.configuration)
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
        val compositedImage: BufferedImage,
        val backMode: String?,
    )
}