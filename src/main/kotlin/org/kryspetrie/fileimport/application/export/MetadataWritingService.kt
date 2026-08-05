package org.kryspetrie.fileimport.application.export

import com.petrielabs.metadataeditor.domain.MetadataWriteResult
import com.petrielabs.metadataeditor.ports.inbound.MetadataEditorPort
import java.nio.file.Paths
import org.kryspetrie.fileimport.domain.model.DetectedPhoto
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration
import org.kryspetrie.fileimport.domain.model.ProcessedImage
import org.kryspetrie.fileimport.domain.port.FaceRegionTransformerPort
import org.kryspetrie.fileimport.domain.port.ImageProcessingPort

/**
 * Writes image pixels (when needed) and metadata via the photo-metadata-editor library (ExifTool).
 *
 * ExifTool supports in-place metadata writes for JPEG, TIFF, and all major RAW formats. Each write
 * is selective (only changed tags) and wrapped in a sibling backup with automatic rollback on
 * failure by [MetadataEditorPort].
 */
class MetadataWritingService(
    private val metadataEditor: MetadataEditorPort,
    private val faceRegionTransformer: FaceRegionTransformerPort,
    private val imageProcessing: ImageProcessingPort,
) {

    /** Writes metadata into an existing image file without re-encoding pixels. */
    fun writeMetadataOnly(
        outputPath: FilePath,
        config: PhotoScanConfiguration,
        sourcePath: FilePath? = null,
        detectedPhoto: DetectedPhoto? = null,
        marginFraction: Double = 0.02,
        sourceImage: ProcessedImage? = null,
        preRotationWidth: Int = 0,
        preRotationHeight: Int = 0,
        physicalPixelRotationApplied: Boolean = false,
    ): Boolean {
        val mergedConfig =
            mergeFaceRegions(
                config = config,
                sourcePath = sourcePath,
                detectedPhoto = detectedPhoto,
                marginFraction = marginFraction,
                sourceImage = sourceImage,
                preRotationWidth = preRotationWidth,
                preRotationHeight = preRotationHeight,
            )
        return writeViaExifTool(
            outputPath = outputPath,
            config = mergedConfig,
            sourcePath = sourcePath,
            preRotationWidth = preRotationWidth,
            preRotationHeight = preRotationHeight,
            physicalPixelRotationApplied = physicalPixelRotationApplied,
        )
    }

    /** Writes [image] to [outputPath] as JPEG, then layers metadata via ExifTool. */
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
        physicalPixelRotationApplied: Boolean = false,
    ) {
        imageProcessing.writeJpegImage(image, outputPath, quality = jpegQuality)
        writeMetadataOnly(
            outputPath = outputPath,
            config = config,
            sourcePath = sourcePath,
            detectedPhoto = detectedPhoto,
            marginFraction = marginFraction,
            sourceImage = sourceImage,
            preRotationWidth = preRotationWidth,
            preRotationHeight = preRotationHeight,
            physicalPixelRotationApplied = physicalPixelRotationApplied,
        )
    }

    private fun writeViaExifTool(
        outputPath: FilePath,
        config: PhotoScanConfiguration,
        sourcePath: FilePath?,
        preRotationWidth: Int,
        preRotationHeight: Int,
        physicalPixelRotationApplied: Boolean = false,
    ): Boolean {
        val mapped =
            PhotoScanMetadataMapper.map(
                filePath = Paths.get(outputPath.path),
                config = config,
                preRotationWidth = preRotationWidth,
                preRotationHeight = preRotationHeight,
                faceRegions = config.faceRegions,
                physicalPixelRotationApplied = physicalPixelRotationApplied,
            )

        val changes =
            if (config.copyOriginalExif && sourcePath != null) {
                val sourceTags =
                    try {
                        metadataEditor.read(Paths.get(sourcePath.path)).tags
                    } catch (_: Exception) {
                        emptyList()
                    }
                SourceExifBaselineMerger.merge(sourceTags, mapped.command.changes)
            } else {
                mapped.command.changes
            }

        if (changes.isEmpty()) {
            return true
        }

        val command =
            mapped.command.copy(
                changes = changes,
                allowProtectedWrites =
                    mapped.command.allowProtectedWrites ||
                        (config.copyOriginalExif && sourcePath != null),
            )

        when (val result = metadataEditor.write(command)) {
            is MetadataWriteResult.Success -> return true
            is MetadataWriteResult.Failure ->
                throw MetadataWriteException(result.message, result.cause)
        }
    }

    private fun mergeFaceRegions(
        config: PhotoScanConfiguration,
        sourcePath: FilePath?,
        detectedPhoto: DetectedPhoto?,
        marginFraction: Double,
        sourceImage: ProcessedImage?,
        preRotationWidth: Int,
        preRotationHeight: Int,
    ): PhotoScanConfiguration {
        val allFaceRegions = config.faceRegions.toMutableList()
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
                    allFaceRegions.addAll(transformedRegions)
                }
            } catch (_: Exception) {
                // Source face region transformation is best-effort
            }
        }
        return config.copy(faceRegions = allFaceRegions)
    }
}
