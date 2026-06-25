package org.kryspetrie.fileimport.application.export

import java.awt.image.BufferedImage
import java.io.File
import org.kryspetrie.fileimport.domain.port.FaceRegionTransformerPort
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.DetectedPhoto
import org.kryspetrie.fileimport.domain.model.ExifValueResolver
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration

/**
 * Writes an image to a JPEG file and then layers EXIF, IPTC, and XMP metadata on top.
 *
 * The pipeline is:
 * 1. Write plain JPEG (via [JpegImageWriter])
 * 2. Layer EXIF overrides (via [ExifMetadataWriter])
 * 3. Layer IPTC keywords/location (via [IptcMetadataWriter])
 * 4. Layer XMP face regions (via [XmpMetadataWriter])
 *
 * @param faceRegionTransformer used to transform source face regions into output coordinates.
 */
class MetadataWritingService(private val faceRegionTransformer: FaceRegionTransformerPort) {

    /**
     * Writes [image] to [outputFile] as JPEG, then layers EXIF, IPTC, and XMP metadata.
     *
     * @param image the final composited image to write
     * @param outputFile destination JPEG file
     * @param config photo scan configuration with metadata overrides
     * @param sourceFile optional source scan file for baseline EXIF and face region extraction
     * @param detectedPhoto the (possibly margin-expanded) detected photo for face region transforms
     * @param marginFraction the margin fraction applied to the detected photo
     * @param sourceImage the original source image dimensions (for face region transformation)
     * @param preRotationWidth width of the image before rotation (for face region transformation)
     * @param preRotationHeight height of the image before rotation (for face region transformation)
     * @param jpegQuality JPEG compression quality (0.0 – 1.0)
     */
    fun writeImageWithMetadata(
        image: BufferedImage,
        outputFile: File,
        config: PhotoScanConfiguration,
        sourceFile: File? = null,
        detectedPhoto: DetectedPhoto? = null,
        marginFraction: Double = 0.02,
        sourceImage: BufferedImage? = null,
        preRotationWidth: Int = image.width,
        preRotationHeight: Int = image.height,
        jpegQuality: Float = 0.95f,
    ) {
        // Step 1: Write plain JPEG
        jpegImageWriter.writeJpegImage(image, outputFile, quality = jpegQuality)

        // Step 2: Layer EXIF
        if (config.hasExifOverrides()) {
            ExifMetadataWriter.writeExifMetadata(outputFile, config, sourceFile)
        }

        // Step 3: Layer IPTC
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

        // Step 4: Layer XMP face regions
        val allFaceRegions = mutableListOf(config.faceRegions)

        if (sourceFile != null && detectedPhoto != null && sourceImage != null) {
            try {
                val transformedRegions =
                    faceRegionTransformer.transformFaceRegionsFromSource(
                        sourceFile = FilePath(sourceFile.absolutePath),
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

    companion object {
        private val jpegImageWriter = JpegImageWriter()
    }
}