package org.kryspetrie.fileimport.application

import java.awt.image.BufferedImage
import java.io.File
import java.util.Locale
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.plugins.jpeg.JPEGImageWriteParam
import org.kryspetrie.fileimport.application.export.ExifMetadataWriter
import org.kryspetrie.fileimport.application.export.FilenameResolver
import org.kryspetrie.fileimport.application.export.ImageTransformer
import org.kryspetrie.fileimport.application.export.IptcMetadataWriter
import org.kryspetrie.fileimport.application.export.XmpMetadataWriter
import org.kryspetrie.fileimport.domain.model.CorrectionStrategy
import org.kryspetrie.fileimport.domain.model.DetectedPhoto
import org.kryspetrie.fileimport.domain.model.ExifValueResolver
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.GeometryUtils
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration
import org.kryspetrie.fileimport.domain.model.PhotoScanExportResult
import org.kryspetrie.fileimport.domain.model.PhotoScanExportedFile
import org.kryspetrie.fileimport.domain.model.PhotoScanSingleExportResult
import org.kryspetrie.fileimport.domain.model.ProcessedImage
import org.kryspetrie.fileimport.domain.model.RotationAngle
import org.kryspetrie.fileimport.domain.model.determineCorrectionStrategy
import org.kryspetrie.fileimport.domain.port.PhotoScanExportPort
import org.kryspetrie.fileimport.infrastructure.adapter.toBufferedImage

/**
 * Service for exporting extracted photos with EXIF metadata preservation and modification.
 *
 * Orchestrates the complete export pipeline:
 * 1. Perspective correction of the extracted photo (via [PerspectiveCorrectionService])
 * 2. Image transformation — crop and rotation (via [ImageTransformer])
 * 3. EXIF metadata write (via [ExifMetadataWriter])
 * 4. IPTC metadata write (via [IptcMetadataWriter])
 * 5. XMP face region write (via [XmpMetadataWriter])
 * 6. Filename conflict resolution (via [FilenameResolver])
 *
 * Back-of-photo image handling:
 * - `"combine"` mode: Stitches the back crop below the front photo with a 2px separator
 * - `"append_back"` mode: Exports the back crop as a separate `_back.jpg` file
 *
 * @see DetectedPhoto
 * @see PhotoScanConfiguration
 * @see ExifMetadataWriter
 */
class PhotoScanExportService(
    private val perspectiveService: PerspectiveCorrectionService,
    private val faceRegionTransformer: FaceRegionTransformer,
) : PhotoScanExportPort {

    /** JPEG quality for output images (0.0 - 1.0). */
    var jpegQuality = 0.95f

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

        val marginFraction = 0.02

        for ((index, photo) in detectedPhotos.withIndex()) {
            try {
                val marginedPhoto = GeometryUtils.applyMargin(photo, marginFraction)
                val strategy =
                    photo.configuration.correctionStrategy
                        ?: if (marginedPhoto.applyPerspectiveCorrection)
                            CorrectionStrategy.PERSPECTIVE
                        else determineCorrectionStrategy(marginedPhoto.toListOfCorners())

                val correctedImage =
                    when (strategy) {
                        CorrectionStrategy.PERSPECTIVE ->
                            perspectiveService.correctPerspective(bufferedImage, marginedPhoto)
                        CorrectionStrategy.CROP_AND_ROTATE ->
                            ImageTransformer.cropAxisAligned(bufferedImage, marginedPhoto)
                        CorrectionStrategy.CROP ->
                            ImageTransformer.cropAxisAligned(bufferedImage, marginedPhoto)
                    }

                val finalImage =
                    if (photo.rotation != RotationAngle.NONE) {
                        ImageTransformer.rotateImage(correctedImage, photo.rotation)
                    } else {
                        correctedImage
                    }

                // Handle back-of-photo image based on mode
                val backMode = photo.configuration.backImageMode
                val compositeImage =
                    if (backMode == "combine" && photo.configuration.hasBackImage()) {
                        compositeBackImage(finalImage, photo.configuration)
                    } else {
                        finalImage
                    }

                val fileName =
                    if (detectedPhotos.size > 1) "${baseFileName}_${index + 1}.jpg"
                    else "${baseFileName}.jpg"

                val resolvedPath =
                    FilenameResolver.resolveFilenameConflict(File(destinationPath), fileName)
                val outputFile = File(resolvedPath)

                writeImageWithMetadata(
                    compositeImage,
                    outputFile,
                    photo.configuration,
                    sourceJavaFile,
                    detectedPhoto = marginedPhoto,
                    marginFraction = marginFraction,
                    sourceImage = bufferedImage,
                    preRotationWidth = correctedImage.width,
                    preRotationHeight = correctedImage.height,
                )

                exportedFiles.add(
                    ExportedFile(
                        sourceFile = sourceFile,
                        destinationPath = resolvedPath,
                        photoId = photo.id,
                        width = compositeImage.width,
                        height = compositeImage.height,
                        fileSize = outputFile.length(),
                    )
                )

                // Export back image as separate "_back" file if mode is append_back
                if (backMode == "append_back" && photo.configuration.hasBackImage()) {
                    val backImageResult = prepareBackImage(photo.configuration)
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
                        writeJpegImage(backImageResult, backOutputFile)
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
        val marginFraction = 0.02

        return try {
            val marginedPhoto = GeometryUtils.applyMargin(detectedPhoto, marginFraction)
            val strategy =
                detectedPhoto.configuration.correctionStrategy
                    ?: if (marginedPhoto.applyPerspectiveCorrection) CorrectionStrategy.PERSPECTIVE
                    else determineCorrectionStrategy(marginedPhoto.toListOfCorners())

            val correctedImage =
                when (strategy) {
                    CorrectionStrategy.PERSPECTIVE ->
                        perspectiveService.correctPerspective(bufferedImage, marginedPhoto)
                    CorrectionStrategy.CROP_AND_ROTATE ->
                        ImageTransformer.cropAxisAligned(bufferedImage, marginedPhoto)
                    CorrectionStrategy.CROP ->
                        ImageTransformer.cropAxisAligned(bufferedImage, marginedPhoto)
                }

            val finalImage =
                if (detectedPhoto.rotation != RotationAngle.NONE) {
                    ImageTransformer.rotateImage(correctedImage, detectedPhoto.rotation)
                } else {
                    correctedImage
                }

            // Handle back-of-photo image based on mode
            val backMode = detectedPhoto.configuration.backImageMode
            val compositeImage =
                if (backMode == "combine" && detectedPhoto.configuration.hasBackImage()) {
                    compositeBackImage(finalImage, detectedPhoto.configuration)
                } else {
                    finalImage
                }

            val resolvedPath =
                FilenameResolver.resolveFilenameConflict(File(destinationPath), "$baseFileName.jpg")
            val outputFile = File(resolvedPath)

            writeImageWithMetadata(
                compositeImage,
                outputFile,
                detectedPhoto.configuration,
                sourceJavaFile,
                detectedPhoto = marginedPhoto,
                marginFraction = marginFraction,
                sourceImage = bufferedImage,
                preRotationWidth = correctedImage.width,
                preRotationHeight = correctedImage.height,
            )

            // Export back image as separate "_back" file if mode is append_back
            if (backMode == "append_back" && detectedPhoto.configuration.hasBackImage()) {
                val backImageResult = prepareBackImage(detectedPhoto.configuration)
                if (backImageResult != null) {
                    val backResolvedPath =
                        FilenameResolver.resolveFilenameConflict(
                            File(destinationPath),
                            "${baseFileName}_back.jpg",
                        )
                    val backOutputFile = File(backResolvedPath)
                    writeJpegImage(backImageResult, backOutputFile)
                }
            }

            SingleExportResult(
                success = true,
                destinationPath = resolvedPath,
                width = compositeImage.width,
                height = compositeImage.height,
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
     * Writes an image to file as a JPEG with the configured quality. Used for back-of-photo images
     * that don't need metadata.
     */
    private fun writeJpegImage(image: BufferedImage, outputFile: File) {
        outputFile.parentFile?.mkdirs()
        val writer = ImageIO.getImageWritersByFormatName("jpg").next()
        val writeParam =
            JPEGImageWriteParam(Locale.US).apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionQuality = jpegQuality
            }
        val fileOs = ImageIO.createImageOutputStream(outputFile)
        fileOs.use {
            writer.output = it
            writer.write(null, IIOImage(image, null, null), writeParam)
        }
        writer.dispose()
    }

    /**
     * Writes an image to file with EXIF, IPTC, and XMP metadata.
     * 1. Write plain JPEG
     * 2. Layer EXIF overrides (via [ExifMetadataWriter])
     * 3. Layer IPTC keywords/location (via [IptcMetadataWriter])
     * 4. Layer XMP face regions (via [XmpMetadataWriter])
     */
    private fun writeImageWithMetadata(
        image: BufferedImage,
        outputFile: File,
        config: PhotoScanConfiguration,
        sourceFile: File? = null,
        detectedPhoto: DetectedPhoto? = null,
        marginFraction: Double = 0.02,
        sourceImage: BufferedImage? = null,
        preRotationWidth: Int = image.width,
        preRotationHeight: Int = image.height,
    ) {
        outputFile.parentFile?.mkdirs()

        // Write the plain JPEG image
        val writer = ImageIO.getImageWritersByFormatName("jpg").next()
        val writeParam =
            JPEGImageWriteParam(Locale.US).apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionQuality = jpegQuality
            }

        val fileOs = ImageIO.createImageOutputStream(outputFile)
        fileOs.use {
            writer.output = it
            writer.write(null, IIOImage(image, null, null), writeParam)
        }
        writer.dispose()

        // Layer 1: EXIF
        if (config.hasExifOverrides()) {
            ExifMetadataWriter.writeExifMetadata(outputFile, config, sourceFile)
        }

        // Layer 2: IPTC
        val keywordsValue = ExifValueResolver.resolveKeywords(config)
        val hasLocationData =
            config.locationName.isNotBlank() ||
                config.city.isNotBlank() ||
                config.state.isNotBlank() ||
                config.country.isNotBlank() ||
                config.subjects.isNotBlank()
        if (keywordsValue != null || hasLocationData) {
            IptcMetadataWriter.writeIptcData(outputFile, keywordsValue, config)
        }

        // Layer 3: XMP face regions
        val allFaceRegions = mutableListOf(config.faceRegions)

        if (sourceFile != null && detectedPhoto != null && sourceImage != null) {
            try {
                val transformedRegions =
                    faceRegionTransformer.transformFaceRegionsFromSource(
                        sourceFile = sourceFile,
                        detectedPhoto = detectedPhoto,
                        outputWidth = preRotationWidth,
                        outputHeight = preRotationHeight,
                        sourceWidth = sourceImage.width,
                        sourceHeight = sourceImage.height,
                        marginFraction = marginFraction,
                    )
                if (transformedRegions.isNotEmpty()) {
                    allFaceRegions.add(transformedRegions)
                }
            } catch (_: Exception) {
                // Source face region transformation is best-effort
            }
        }

        val mergedConfig = config.copy(faceRegions = allFaceRegions.flatten())
        if (mergedConfig.faceRegions.isNotEmpty()) {
            XmpMetadataWriter.writeXmpFaceRegions(outputFile, mergedConfig)
        }
    }

    /**
     * Prepares the back-of-photo image: loads, crops, and rotates it. Returns null if the back
     * image cannot be loaded.
     */
    private fun prepareBackImage(config: PhotoScanConfiguration): BufferedImage? {
        val sourcePath = config.backImageSourcePath ?: return null
        val sourceFile = File(sourcePath)
        if (!sourceFile.exists()) return null

        val backImage =
            try {
                ImageIO.read(sourceFile) ?: return null
            } catch (_: Exception) {
                return null
            }

        // Apply crop if normalized crop coordinates are provided
        val croppedBack =
            if (config.backCropNormalized != null && config.backCropNormalized.size == 4) {
                val (left, top, right, bottom) = config.backCropNormalized
                val cropX = (left * backImage.width).toInt().coerceIn(0, backImage.width)
                val cropY = (top * backImage.height).toInt().coerceIn(0, backImage.height)
                val cropW =
                    ((right - left) * backImage.width).toInt().coerceIn(1, backImage.width - cropX)
                val cropH =
                    ((bottom - top) * backImage.height)
                        .toInt()
                        .coerceIn(1, backImage.height - cropY)
                backImage.getSubimage(cropX, cropY, cropW, cropH)
            } else {
                backImage
            }

        // Apply rotation (0, 90, 180, 270 degrees)
        return when (config.backCropRotation) {
            90 -> ImageTransformer.rotateImage(croppedBack, RotationAngle.CW_90)
            180 -> ImageTransformer.rotateImage(croppedBack, RotationAngle.CW_180)
            270 -> ImageTransformer.rotateImage(croppedBack, RotationAngle.CCW_90)
            else -> croppedBack
        }
    }

    /**
     * Composites a back-of-photo image below the front (extracted) photo.
     *
     * The back image is loaded from [PhotoScanConfiguration.backImageSourcePath], optionally
     * cropped using [PhotoScanConfiguration.backCropNormalized] coordinates, and optionally rotated
     * by [PhotoScanConfiguration.backCropRotation]. The front and back images are stacked
     * vertically with the back image scaled to match the front image width.
     */
    private fun compositeBackImage(
        frontImage: BufferedImage,
        config: PhotoScanConfiguration,
    ): BufferedImage {
        val preparedBack = prepareBackImage(config) ?: return frontImage

        // Scale back image to match front image width
        val targetWidth = frontImage.width
        val scale = targetWidth.toFloat() / preparedBack.width.toFloat()
        val targetHeight = (preparedBack.height * scale).toInt()

        val scaledBack =
            java.awt.image.BufferedImage(
                targetWidth,
                targetHeight,
                java.awt.image.BufferedImage.TYPE_INT_RGB,
            )
        val g2d = scaledBack.createGraphics()
        g2d.drawImage(preparedBack, 0, 0, targetWidth, targetHeight, null)
        g2d.dispose()

        // Stack front and back vertically with a 2px separator
        val separatorHeight = 2
        val compositeWidth = frontImage.width
        val compositeHeight = frontImage.height + separatorHeight + scaledBack.height

        val composite =
            java.awt.image.BufferedImage(
                compositeWidth,
                compositeHeight,
                java.awt.image.BufferedImage.TYPE_INT_RGB,
            )
        val g = composite.createGraphics()
        // Draw front image at top
        g.drawImage(frontImage, 0, 0, null)
        // Draw separator line
        g.color = java.awt.Color.LIGHT_GRAY
        g.fillRect(0, frontImage.height, compositeWidth, separatorHeight)
        // Draw back image below
        g.drawImage(scaledBack, 0, frontImage.height + separatorHeight, null)
        g.dispose()

        return composite
    }
}
