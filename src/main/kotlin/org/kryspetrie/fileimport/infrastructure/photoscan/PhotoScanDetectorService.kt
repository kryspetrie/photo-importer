package org.kryspetrie.fileimport.infrastructure.photoscan

import java.awt.image.BufferedImage
import org.kryspetrie.fileimport.domain.model.DetectedPhoto

/**
 * Service for detecting photo boundaries in scanned images.
 *
 * Delegates to [HybridCornerDetector] which uses edge-based classical computer vision with
 * [RectangleDetector] for robust detection.
 *
 * @param rectangleDetector Edge-based rectangle detector
 * @param maxPhotos Maximum number of photos to detect (default 4)
 */
class PhotoScanDetectorService(
    private val rectangleDetector: RectangleDetector = RectangleDetector(),
    private val maxPhotos: Int = 4,
) {

    private val detector = HybridCornerDetector(rectangleDetector)

    /**
     * Detects rectangular photo regions in a scanned image.
     *
     * @param image The scanned image
     * @return [DetectedPhoto] objects with corners ordered TL→TR→BR→BL.
     */
    fun detectPhotos(image: BufferedImage): List<DetectedPhoto> {
        detector.targetPhotoCount = maxPhotos
        return detector.detectPhotos(image)
    }
}
