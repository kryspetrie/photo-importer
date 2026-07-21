package org.kryspetrie.fileimport.domain.port

import java.io.InputStream
import org.kryspetrie.fileimport.domain.model.RotationAngle
/**
 * Port interface for loading ML model resources.
 *
 * Abstracts the loading of ONNX model files so that the domain/infrastructure layer does not need
 * to know whether models are bundled on the classpath, stored in a user directory, or fetched from
 * a remote location.
 *
 * ## Models
 *
 * Three ONNX models are required for the YOLO photo detection pipeline:
 * - **Detection model** — finds bounding boxes of photos in a scan (640×640 input)
 * - **Pose model** — refines bounding boxes into 4-corner keypoints for perspective correction
 *   (640×640 input)
 * - **Corner regression model** — per-corner refinement for sub-pixel accuracy (320×320 input)
 *
 * By keeping model loading behind a port, we can:
 * - Ship models bundled in the JAR (classpath resources)
 * - Allow users to configure custom model paths (file system)
 * - Swap implementations for testing without touching real model files
 *
 * @see ClasspathModelResourceAdapter Default implementation that loads from classpath resources
 */
interface ModelResourcePort {

    /**
     * Loads the YOLO detection model bytes.
     *
     * The detection model takes a 640×640 letterboxed image and outputs bounding boxes with
     * confidence scores.
     *
     * @return Raw model bytes suitable for ONNX Runtime `SessionOptions`
     * @throws ModelNotFoundException if the model resource cannot be found or read
     */
    fun loadDetectionModel(): ByteArray

    /**
     * Loads the YOLO pose model bytes.
     *
     * The pose model takes a 640×640 image crop and outputs bounding boxes with 4 keypoint
     * coordinates (LL/UL/UR/LR corners of each detected photo) plus visibility scores.
     *
     * @return Raw model bytes suitable for ONNX Runtime `SessionOptions`
     * @throws ModelNotFoundException if the model resource cannot be found or read
     */
    fun loadPoseModel(): ByteArray

    /**
     * Loads the corner regression model bytes.
     *
     * The corner regression model takes a 320×320 image crop and outputs bounding boxes with 1
     * keypoint at the exact corner position. Used for sub-pixel corner refinement.
     *
     * @return Raw model bytes suitable for ONNX Runtime `SessionOptions`
     * @throws ModelNotFoundException if the model resource cannot be found or read
     */
    fun loadCornerRegressionModel(): ByteArray

    /**
     * Opens an input stream for the detection model.
     *
     * Useful when the consumer needs streaming access rather than loading the entire model into
     * memory at once.
     *
     * @return InputStream for the detection model
     * @throws ModelNotFoundException if the model resource cannot be found
     */
    fun detectionModelStream(): InputStream

    /**
     * Opens an input stream for the pose model.
     *
     * Useful when the consumer needs streaming access rather than loading the entire model into
     * memory at once.
     *
     * @return InputStream for the pose model
     * @throws ModelNotFoundException if the model resource cannot be found
     */
    fun poseModelStream(): InputStream

    /**
     * Opens an input stream for the corner regression model.
     *
     * @return InputStream for the corner regression model
     * @throws ModelNotFoundException if the model resource cannot be found
     */
    fun cornerRegressionModelStream(): InputStream

    /**
     * Returns whether models are available and ready to use.
     *
     * Returns `true` if detection, pose, and corner regression models can all be found and loaded.
     * Used by the UI to enable/disable YOLO detection modes.
     */
    fun isModelAvailable(): Boolean

    /**
     * Returns the version identifier for the bundled detection model.
     *
     * Used for cache invalidation and diagnostic info.
     */
    fun detectionModelVersion(): String

    /**
     * Returns the version identifier for the bundled pose model.
     *
     * Used for cache invalidation and diagnostic info.
     */
    fun poseModelVersion(): String

    /**
     * Returns the version identifier for the bundled corner regression model.
     *
     * Used for cache invalidation and diagnostic info.
     */
    fun cornerRegressionModelVersion(): String

    /**
     * Loads the face detection model bytes.
     *
     * The face detection model takes a 640×640 letterboxed image and outputs face bounding boxes
     * with confidence scores in NMS-filtered `[1, 300, 6]` format (x1, y1, x2, y2, confidence,
     * class).
     *
     * @return Raw model bytes suitable for ONNX Runtime `SessionOptions`
     * @throws ModelNotFoundException if the model resource cannot be found or read
     */
    fun loadFaceDetectionModel(): ByteArray

    /**
     * Returns whether the face detection model is available.
     *
     * This is separate from [isModelAvailable] because face detection is optional — the application
     * functions fully without it. Returns true if the face detection model file exists on the
     * classpath or configured location.
     */
    fun isFaceDetectionModelAvailable(): Boolean

    // ── Orientation Detection Model ──────────────────────────────────────

    /**
     * Loads the orientation detection model bytes.
     *
     * The orientation detection model (deep-image-orientation-angle-detection) takes a 224×224
     * image and predicts a continuous orientation angle in the range [0°, 360°).
     *
     * @return Raw model bytes suitable for ONNX Runtime [ai.onnxruntime.OrtSession]
     * @throws ModelNotFoundException if the model resource cannot be found or read
     */
    fun loadOrientationModel(): ByteArray

    /**
     * Returns whether the orientation detection model is available.
     *
     * Orientation detection is optional — the application functions fully without it. Returns true
     * if the orientation model file exists on the classpath or configured location.
     */
    fun isOrientationDetectionModelAvailable(): Boolean

    /**
     * Returns the version identifier for the bundled orientation detection model.
     *
     * Used for cache invalidation and diagnostic info.
     */
    fun orientationModelVersion(): String

    // ── Face Embedding Model ──────────────────────────────────────────────

    /**
     * Loads the face embedding model bytes.
     *
     * The face embedding model (MobileFaceNet) takes a 112×112 RGB face crop and outputs a
     * 128-dimensional embedding vector used for face identification and similarity comparison.
     *
     * This model is optional — the application functions fully without it, but face
     * identification features will be unavailable.
     *
     * @return Raw model bytes suitable for ONNX Runtime [ai.onnxruntime.OrtSession]
     * @throws ModelNotFoundException if the model resource cannot be found or read
     */
    fun loadFaceEmbeddingModel(): ByteArray

    /**
     * Returns whether the face embedding model is available.
     *
     * Face embedding is optional — the application functions without it (face detection still works).
     * Returns true if the face embedding model file exists on the classpath or in the download directory.
     */
    fun isFaceEmbeddingModelAvailable(): Boolean

    /**
     * Returns the version identifier for the face embedding model.
     *
     * Used for cache invalidation and diagnostic info.
     */
    fun faceEmbeddingModelVersion(): String
}

/** Thrown when a required ML model cannot be found or loaded. */
class ModelNotFoundException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Map a continuous orientation angle (in degrees) to the nearest [RotationAngle].
 *
 * The orientation model predicts a continuous angle in [0°, 360°). This function rounds to the
 * nearest 90° increment:
 * - 315°–360° and 0°–45° → [RotationAngle.NONE] (image is upright)
 * - 45°–135° → [RotationAngle.CW_90] (image needs CW rotation to correct)
 * - 135°–225° → [RotationAngle.CW_180] (image is upside down)
 * - 225°–315° → [RotationAngle.CCW_90] (image needs CCW rotation to correct)
 */
fun Float.toNearestRotationAngle(): RotationAngle {
    val normalized = ((this % 360f) + 360f) % 360f
    return when {
        normalized < 45f || normalized >= 315f -> RotationAngle.NONE
        normalized < 135f -> RotationAngle.CW_90
        normalized < 225f -> RotationAngle.CW_180
        else -> RotationAngle.CCW_90
    }
}
