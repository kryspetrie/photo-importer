package org.kryspetrie.fileimport.domain.port

import org.kryspetrie.fileimport.domain.model.DetectedPhoto
import org.kryspetrie.fileimport.domain.model.FaceRegion
import org.kryspetrie.fileimport.domain.model.FilePath

/**
 * Port interface for transforming face region coordinates from a source image's XMP metadata to
 * match cropped and corrected output photos.
 *
 * Source images (e.g., scanned pages) may contain MWG-RS face regions with normalized coordinates.
 * When individual photos are extracted via perspective correction and cropping, those face region
 * coordinates must be transformed to match the output photo dimensions.
 *
 * @see FaceRegion The face region model with normalized coordinates
 */
interface FaceRegionTransformerPort {

    /**
     * Reads face regions from a source image's XMP metadata and transforms them to match a specific
     * cropped output photo.
     *
     * @param sourceFile The source image file containing XMP metadata
     * @param detectedPhoto The detected photo defining the crop region
     * @param outputWidth Width of the output photo in pixels
     * @param outputHeight Height of the output photo in pixels
     * @param sourceWidth Width of the source image in pixels
     * @param sourceHeight Height of the source image in pixels
     * @param marginFraction Margin fraction applied during perspective correction (default 0.02)
     * @return List of transformed face regions in output photo coordinates
     */
    fun transformFaceRegionsFromSource(
        sourceFile: FilePath,
        detectedPhoto: DetectedPhoto,
        outputWidth: Int,
        outputHeight: Int,
        sourceWidth: Int,
        sourceHeight: Int,
        marginFraction: Double = 0.02,
    ): List<FaceRegion>

    /**
     * Reads MWG-RS face regions from a JPEG file's XMP metadata.
     *
     * @param file The JPEG file path to read metadata from
     * @return List of face regions found in the XMP metadata
     */
    fun readFaceRegionsFromXmp(file: FilePath): List<FaceRegion>
}
