package org.kryspetrie.fileimport.domain.port

import org.kryspetrie.fileimport.domain.model.ProcessedImage

/**
 * A detected face with a bounding box and confidence score.
 *
 * Coordinates are in the original image's pixel space (not normalized).
 *
 * @property x1 Left edge of the bounding box in pixels
 * @property y1 Top edge of the bounding box in pixels
 * @property x2 Right edge of the bounding box in pixels
 * @property y2 Bottom edge of the bounding box in pixels
 * @property confidence Detection confidence in the range [0.0, 1.0]
 */
data class DetectedFace(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val confidence: Float,
)

/**
 * Port interface for detecting faces in images.
 *
 * Abstracts face detection so that the domain layer does not need to know whether faces are found
 * via an ONNX model, an external service, or a stub for testing.
 *
 * Uses [ProcessedImage] as input to maintain the hexagonal architecture boundary. Implementations
 * convert to `BufferedImage` internally (see [FaceDetectionService]).
 *
 * @see DetectedFace
 * @see FaceRegionTransformerPort
 */
interface FaceDetectionPort {

    /**
     * Detects faces in the given image.
     *
     * @param image The source image to scan for faces
     * @param confThreshold Minimum confidence threshold for a detection to be kept (default 0.5)
     * @param iouThreshold Intersection-over-union threshold for non-maximum suppression
     *   (default 0.45); lower values produce fewer overlapping boxes
     * @return List of detected faces sorted by descending confidence
     */
    fun detectFaces(
        image: ProcessedImage,
        confThreshold: Float = 0.5f,
        iouThreshold: Float = 0.45f,
    ): List<DetectedFace>

    /**
     * Returns whether the face detection model is available and ready to use.
     *
     * Used by the UI to enable/disable face detection features without attempting to load
     * the model.
     */
    fun isFaceDetectionAvailable(): Boolean

    /**
     * Preloads the face detection model eagerly.
     *
     * Without preloading, the first call to [detectFaces] pays the cost of loading ~10 MB
     * of model bytes + ONNX session creation. Call this early in the application lifecycle
     * to front-load that cost. Idempotent — calling it after the model is loaded is a no-op.
     *
     * @return true if the face detection service was successfully initialized, false if the
     *   model is unavailable
     */
    fun preload(): Boolean = false
}
