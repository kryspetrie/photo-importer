package org.kryspetrie.fileimport.application.export

import org.kryspetrie.fileimport.domain.model.DetectedPhoto
import org.kryspetrie.fileimport.domain.model.ExifValueResolver
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration
import org.kryspetrie.fileimport.domain.model.ProcessedImage
import org.kryspetrie.fileimport.domain.port.FaceRegionTransformerPort
import org.kryspetrie.fileimport.domain.port.FileSystemPort
import org.kryspetrie.fileimport.domain.port.ImageProcessingPort

/**
 * Writes an image to a JPEG file and then layers EXIF, IPTC, and XMP metadata on top.
 *
 * The pipeline is:
 * 1. Write plain JPEG (via [ImageProcessingPort])
 * 2. Layer EXIF overrides (via [ExifMetadataWriter])
 * 3. Layer IPTC keywords/location (via [IptcMetadataWriter])
 * 4. Layer XMP face regions (via [XmpMetadataWriter])
 *
 * @param faceRegionTransformer used to transform source face regions into output coordinates
 * @param imageProcessing port for writing JPEG images
 * @param fileSystem port for file I/O operations
 */
class MetadataWritingService(
    private val faceRegionTransformer: FaceRegionTransformerPort,
    private val imageProcessing: ImageProcessingPort,
    private val fileSystem: FileSystemPort,
) {

    private val exifWriter = ExifMetadataWriter(fileSystem)
    private val iptcWriter = IptcMetadataWriter(fileSystem)
    private val xmpWriter = XmpMetadataWriter(fileSystem)

    /**
     * Writes [image] to [outputPath] as JPEG, then layers EXIF, IPTC, and XMP metadata.
     *
     * @param image the final composited image to write
     * @param outputPath destination JPEG file path
     * @param config photo scan configuration with metadata overrides
     * @param sourcePath optional source scan file path for baseline EXIF and face region extraction
     * @param detectedPhoto the (possibly margin-expanded) detected photo for face region transforms
     * @param marginFraction the margin fraction applied to the detected photo
     * @param sourceImage the original source image (for face region transformation dimensions)
     * @param preRotationWidth width of the image before rotation (for face region transformation)
     * @param preRotationHeight height of the image before rotation (for face region transformation)
     * @param jpegQuality JPEG compression quality (0.0 – 1.0)
     */
    fun writeImageWithMetadata(
        image: ProcessedImage,
        outputPath: FilePath,
        config: PhotoScanConfiguration,
        sourcePath: FilePath? = null,
        detectedPhoto: DetectedPhoto? = null,
        marginFraction: Double = 0.02,
        sourceImage: ProcessedImage? = null,
        preRotationWidth: Int = image.width,
        preRotationHeight: Int = image.height,
        jpegQuality: Float = 0.95f,
    ) {
        // Step 1: Write plain JPEG
        imageProcessing.writeJpegImage(image, outputPath, quality = jpegQuality)

        // Step 2: Layer EXIF
        if (config.hasExifOverrides()) {
            exifWriter.writeExifMetadata(outputPath, config, sourcePath)
        }

        // Step 3: Layer IPTC
        val keywordsValue = ExifValueResolver.resolveKeywords(config)
        val hasLocationData =
            config.address.isNotBlank() ||
                config.locationName.isNotBlank() ||
                config.city.isNotBlank() ||
                config.state.isNotBlank() ||
                config.country.isNotBlank() ||
                config.subjects.isNotBlank()
        if (keywordsValue != null || hasLocationData) {
            iptcWriter.writeIptcData(outputPath, keywordsValue, config)
        }

        // Step 4: Layer XMP face regions
        val allFaceRegions = mutableListOf(config.faceRegions)

        if (sourcePath != null && detectedPhoto != null && sourceImage != null) {
            try {
                val transformedRegions =
                    faceRegionTransformer.transformFaceRegionsFromSource(
                        sourceFile = sourcePath,
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
            xmpWriter.writeXmpFaceRegions(outputPath, mergedConfig)
        }
    }
}