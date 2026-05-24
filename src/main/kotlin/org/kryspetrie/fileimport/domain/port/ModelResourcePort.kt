package org.kryspetrie.fileimport.domain.port

import java.io.InputStream

/**
 * Port interface for loading ML model resources.
 *
 * Abstracts the loading of ONNX model files so that the domain/infrastructure layer does not need
 * to know whether models are bundled on the classpath, stored in a user directory, or fetched from
 * a remote location.
 *
 * ## Motivation
 *
 * YOLO-based photo detection requires two ONNX models:
 * - **Detection model** — finds bounding boxes of photos in a scan
 * - **Pose model** — refines bounding boxes into 4-corner keypoints for perspective correction
 *
 * By keeping model loading behind a port, we can:
 * - Ship models bundled in the JAR (classpath resources)
 * - Allow users to configure custom model paths (file system)
 * - Swap implementations for testing without touching real model files
 *
 * ## Usage
 *
 * ```kotlin
 * val modelLoader: ModelResourcePort = koinInject()
 * val detectionBytes = modelLoader.loadDetectionModel()
 * val poseBytes = modelLoader.loadPoseModel()
 * // Pass bytes to ONNX Runtime for inference
 * ```
 *
 * @see ClasspathModelResourceAdapter Default implementation that loads from classpath resources
 */
interface ModelResourcePort {

    /**
     * Loads the YOLO detection model bytes.
     *
     * The detection model takes a 640×640 image and outputs bounding boxes with confidence scores.
     *
     * @return Raw model bytes suitable for ONNX Runtime `SessionOptions`
     * @throws ModelNotFoundException if the model resource cannot be found or read
     */
    fun loadDetectionModel(): ByteArray

    /**
     * Loads the YOLO pose model bytes.
     *
     * The pose model takes a 640×640 image and outputs bounding boxes with 4 keypoint coordinates
     * (LL/UL/UR/LR corners of each detected photo).
     *
     * @return Raw model bytes suitable for ONNX Runtime `SessionOptions`
     * @throws ModelNotFoundException if the model resource cannot be found or read
     */
    fun loadPoseModel(): ByteArray

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
     * Returns whether models are available and ready to use.
     *
     * Returns `true` if both detection and pose models can be found and loaded. Used by the UI to
     * enable/disable YOLO detection modes.
     */
    fun isModelAvailable(): Boolean

    /**
     * Returns the version identifier for the bundled detection model.
     *
     * Used for cache invalidation and diagnostic info. The version is derived from the resource
     * metadata or filename (e.g., "detection_model_v1").
     */
    fun detectionModelVersion(): String

    /**
     * Returns the version identifier for the bundled pose model.
     *
     * Used for cache invalidation and diagnostic info. The version is derived from the resource
     * metadata or filename (e.g., "pose_model_v1").
     */
    fun poseModelVersion(): String
}

/** Thrown when a required ML model cannot be found or loaded. */
class ModelNotFoundException(message: String, cause: Throwable? = null) : Exception(message, cause)
