package org.kryspetrie.fileimport.domain.port

import org.kryspetrie.fileimport.domain.model.DetectedPhoto
import org.kryspetrie.fileimport.domain.model.ProcessedImage

/**
 * Port interface for perspective correction of detected photos in scanned images.
 *
 * Applies homography (perspective warp) to extract rectangular photos from quadrilateral regions,
 * producing a flat, corrected output image.
 *
 * @see DetectedPhoto The detected photo with corner coordinates
 */
interface PerspectiveCorrectionPort {

    /**
     * Corrects the perspective of a detected photo in the source image.
     *
     * @param sourceImage The scanned image containing the photo
     * @param detectedPhoto The detected photo with corner coordinates
     * @return The perspective-corrected image, cropped to the detected photo's size
     */
    fun correctPerspective(
        sourceImage: ProcessedImage,
        detectedPhoto: DetectedPhoto,
    ): ProcessedImage

    /**
     * Calculates the ideal output dimensions for the detected photo based on its corner positions.
     *
     * @param detectedPhoto The detected photo with corner coordinates
     * @return Pair of (width, height) in pixels
     */
    fun calculateOutputDimensions(detectedPhoto: DetectedPhoto): Pair<Int, Int>

    /**
     * Calculates the aspect ratio (width / height) of the detected photo.
     *
     * @param detectedPhoto The detected photo with corner coordinates
     * @return Aspect ratio as a float
     */
    fun calculateAspectRatio(detectedPhoto: DetectedPhoto): Float

    /**
     * Checks if the detected photo's corners form a valid (convex) quadrilateral.
     *
     * @param detectedPhoto The detected photo with corner coordinates
     * @return True if the corners form a valid convex quadrilateral
     */
    fun isValidQuadrilateral(detectedPhoto: DetectedPhoto): Boolean
}
