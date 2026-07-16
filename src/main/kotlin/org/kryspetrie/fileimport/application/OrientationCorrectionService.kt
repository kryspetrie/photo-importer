package org.kryspetrie.fileimport.application

import org.kryspetrie.fileimport.domain.model.ProcessedImage
import org.kryspetrie.fileimport.domain.model.RotationAngle
import org.kryspetrie.fileimport.domain.port.ImageProcessingPort
import org.kryspetrie.fileimport.domain.port.OrientationDetectionPort

/**
 * Application service for auto-detecting and correcting image orientation.
 *
 * Uses an ML model (deep-image-orientation-angle-detection) to predict the orientation angle of an
 * image and provides the corresponding [RotationAngle] needed to correct it. The service delegates
 * to [OrientationDetectionPort] for model inference and [ImageProcessingPort] for pixel rotation.
 *
 * ## Angle semantics
 *
 * The ML model outputs an **orientation angle** (how much the image is rotated CW from upright).
 * This service converts it to a **correction angle** (how much to rotate CW to fix the image):
 * - Image oriented 90° CW → correction = 270° CW (= 90° CCW) → [RotationAngle.CCW_90]
 * - Image oriented 180° → correction = 180° → [RotationAngle.CW_180]
 * - Image oriented 270° CW → correction = 90° CW → [RotationAngle.CW_90]
 * - Image upright (0°) → correction = 0° → [RotationAngle.NONE]
 *
 * The [CorrectionResult.nearestRotation] field contains the correction rotation, so it can be used
 * directly to update the metadata rotation field.
 *
 * ## JPEG lossy rotation warning
 *
 * Rotating a JPEG image involves full re-encoding, which is lossy. This service provides a
 * [isJpegFile] check so that the UI layer can warn users before applying pixel rotation to JPEG
 * files. For lossless orientation, the EXIF orientation tag should be set instead (which this
 * service does NOT do — that is handled by the metadata writing pipeline).
 *
 * @param orientationDetection The ML model adapter for angle prediction
 * @param imageProcessing The image processing port for pixel rotation
 */
class OrientationCorrectionService(
    private val orientationDetection: OrientationDetectionPort,
    private val imageProcessing: ImageProcessingPort,
) {

    /**
     * Result of orientation detection and correction.
     *
     * @property orientationDegrees The detected orientation angle (0°–359.9°) — how much the
     *   image is rotated CW from upright
     * @property confidence Detection confidence (typically 0.5–1.0 for clear detections)
     * @property nearestRotation The nearest discrete [RotationAngle] that would **correct** the
     *   image orientation (i.e., how much to rotate the image to make it upright)
     * @property isJpeg Whether the file appears to be a JPEG (for lossy rotation warnings)
     * @property correctedImage The pixel-rotated image (only non-null if [correctPixels] was true
     *   and the rotation was not [RotationAngle.NONE])
     */
    data class CorrectionResult(
        val orientationDegrees: Float,
        val confidence: Float,
        val nearestRotation: RotationAngle,
        val correctionDegrees: Float,
        val isJpeg: Boolean,
        val correctedImage: ProcessedImage? = null,
    )

    /**
     * Detects the orientation of an image and optionally rotates the pixels.
     *
     * @param image The source image to analyze
     * @param filePath The file path (used only for JPEG detection to warn about lossy rotation)
     * @param correctPixels If true, rotate the image pixels by the detected angle; if false, only
     *   detect and return the result without modifying the image
     * @param confidenceThreshold Minimum model confidence to accept a detection (default 0.3)
     * @return The correction result, or null if the model is unavailable or confidence is below
     *   threshold
     */
    fun detectAndCorrect(
        image: ProcessedImage,
        filePath: String = "",
        correctPixels: Boolean = false,
        confidenceThreshold: Float = 0.3f,
    ): CorrectionResult? {
        val detection = orientationDetection.detectOrientation(image, confidenceThreshold)
            ?: return null

        val isJpeg = filePath.lowercase().endsWith(".jpg") ||
            filePath.lowercase().endsWith(".jpeg")

        val correctedImage = if (correctPixels && detection.nearestRotation != RotationAngle.NONE) {
            imageProcessing.rotateImage(image, detection.nearestRotation)
        } else null

        return CorrectionResult(
            orientationDegrees = detection.orientationDegrees,
            confidence = detection.confidence,
            nearestRotation = detection.nearestRotation,
            correctionDegrees = detection.correctionDegrees,
            isJpeg = isJpeg,
            correctedImage = correctedImage,
        )
    }

    /**
     * Detects the orientation angle of an image without applying any correction.
     *
     * Convenience method for [detectAndCorrect] with `correctPixels = false`.
     */
    fun detectOnly(
        image: ProcessedImage,
        confidenceThreshold: Float = 0.3f,
    ): CorrectionResult? = detectAndCorrect(
        image = image,
        filePath = "",
        correctPixels = false,
        confidenceThreshold = confidenceThreshold,
    )

    /**
     * Returns whether the orientation detection model is available.
     */
    fun isAvailable(): Boolean = orientationDetection.isOrientationDetectionAvailable()

    companion object {
        /** Common JPEG file extensions (lowercase). */
        private val JPEG_EXTENSIONS = setOf(".jpg", ".jpeg", ".jpe", ".jfif")

        /**
         * Check if a file path looks like a JPEG file.
         *
         * Used for UI warnings about lossy JPEG re-encoding when rotating.
         */
        fun isJpegFile(filePath: String): Boolean {
            val lower = filePath.lowercase()
            return JPEG_EXTENSIONS.any { lower.endsWith(it) }
        }
    }
}