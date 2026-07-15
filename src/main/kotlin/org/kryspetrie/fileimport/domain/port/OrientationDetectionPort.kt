package org.kryspetrie.fileimport.domain.port

import org.kryspetrie.fileimport.domain.model.OrientationResult
import org.kryspetrie.fileimport.domain.model.ProcessedImage

/**
 * Port interface for detecting image orientation angle using ML models.
 *
 * Abstracts orientation detection so that the domain and application layers never depend on ONNX
 * Runtime or specific model implementations. Infrastructure adapters handle model loading,
 * preprocessing, and inference.
 *
 * The orientation model predicts a continuous angle (0°–359°) indicating how the image is rotated
 * from its correct upright orientation. To correct the image, rotate it by the **negative** of
 * the predicted angle (or equivalently, apply [OrientationResult.nearestRotation]).
 *
 * ## Classification mapping
 *
 * For the common case of photos taken at 0°, 90°, 180°, or 270°:
 * - 0° detected → image is already upright → [RotationAngle.NONE]
 * - ~90° detected → image needs CW rotation to correct → [RotationAngle.CW_90]
 * - ~180° detected → image needs 180° rotation → [RotationAngle.CW_180]
 * - ~270° detected → image needs CCW rotation → [RotationAngle.CCW_90]
 *
 * @see OrientationResult
 * @see ProcessedImage
 */
interface OrientationDetectionPort {

    /**
     * Detects the orientation angle of the given image.
     *
     * @param image The source image to analyze
     * @param confidenceThreshold Minimum confidence to accept a detection (default 0.3). Results
     *   below this threshold return null.
     * @return The detected orientation result, or null if the model is unavailable, detection
     *   failed, or confidence was below threshold
     */
    fun detectOrientation(
        image: ProcessedImage,
        confidenceThreshold: Float = 0.3f,
    ): OrientationResult?

    /**
     * Returns whether the orientation detection model is available and ready to use.
     *
     * Used by the UI to enable/disable the auto-rotation feature without attempting to load the
     * model.
     */
    fun isOrientationDetectionAvailable(): Boolean

    /**
     * Preloads the orientation detection model eagerly.
     *
     * Without preloading, the first call to [detectOrientation] pays the cost of loading the model
     * bytes + ONNX session creation. Call this early in the application lifecycle to front-load
     * that cost. Idempotent — calling after the model is loaded is a no-op.
     *
     * @return true if the model was successfully initialized, false if the model is unavailable
     */
    fun preload(): Boolean = false
}