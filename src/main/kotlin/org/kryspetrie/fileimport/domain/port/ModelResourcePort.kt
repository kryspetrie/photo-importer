package org.kryspetrie.fileimport.domain.port

import java.io.InputStream

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
}

/** Thrown when a required ML model cannot be found or loaded. */
class ModelNotFoundException(message: String, cause: Throwable? = null) : Exception(message, cause)
