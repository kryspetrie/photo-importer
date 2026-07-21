package org.kryspetrie.fileimport.infrastructure.adapter

import java.io.File
import java.io.InputStream
import org.kryspetrie.fileimport.domain.port.ModelNotFoundException
import org.kryspetrie.fileimport.domain.port.ModelResourcePort

/**
 * Loads ONNX model files from the JVM classpath.
 *
 * Models are bundled in `src/main/resources/models/` and packaged inside the JAR. This is the
 * default strategy — models ship with the application and require no user configuration.
 *
 * ## Model files
 * |File                                     |Purpose                                |Approx. size|Source   |
 * |-----------------------------------------|---------------------------------------|------------|---------|
 * |`models/detection_model.onnx`            |YOLO detection — finds bounding boxes  |~10 MB      |Bundled  |
 * |`models/pose_model.onnx`                 |YOLO pose — finds 4-corner keypoints   |~38 MB      |Bundled  |
 * |`models/face_detection_model.onnx`       |YOLO12n face detection — bounding boxes|~10 MB      |Bundled  |
 * |`models/corner_regression_model.onnx`    |Corner regression — refines keypoints  |~9.5 MB     |Bundled  |
 * |`models/orientation_detection_model.onnx`|ViT orientation — predicts angle       |~346 MB     |Download*|
 *
 * *The orientation detection model is lazy-downloaded from HuggingFace on first use. Use
 * [isOrientationDetectionModelAvailable] to check before calling [loadOrientationModel]. See
 * [MODEL_MANAGEMENT.md](../../../../../docs/MODEL_MANAGEMENT.md) for manual installation.
 *
 * ## Error handling
 *
 * If a model file is missing from the classpath (corrupted build, partial packaging), a
 * [ModelNotFoundException] is thrown. Callers should check [isModelAvailable] before attempting
 * inference.
 *
 * ## Caching
 *
 * Model bytes are lazily loaded on first access and then cached in memory. This avoids repeated I/O
 * and classloader lookups when inference is called multiple times (e.g., scanning several pages in
 * a row).
 *
 * @see ModelResourcePort
 */
class ClasspathModelResourceAdapter : ModelResourcePort {

    private companion object {
        const val DETECTION_MODEL_PATH = "models/detection_model.onnx"
        const val POSE_MODEL_PATH = "models/pose_model.onnx"
        const val CORNER_REGRESSION_MODEL_PATH = "models/corner_regression_model.onnx"
        const val FACE_DETECTION_MODEL_PATH = "models/face_detection_model.onnx"
        const val ORIENTATION_MODEL_PATH = "models/orientation_detection_model.onnx"
        const val ORIENTATION_MODEL_FILENAME = "orientation_detection_model.onnx"

        val DOWNLOADED_MODEL_DIR = File(System.getProperty("user.home"), ".petrie-importer/models")
        const val BUFFER_SIZE = 8192
    }

    /** Cached detection model bytes, loaded lazily. */
    private val detectionModelBytes: ByteArray by lazy { loadResource(DETECTION_MODEL_PATH) }

    /** Cached pose model bytes, loaded lazily. */
    private val poseModelBytes: ByteArray by lazy { loadResource(POSE_MODEL_PATH) }

    /** Cached corner regression model bytes, loaded lazily. */
    private val cornerRegressionModelBytes: ByteArray by lazy {
        loadResource(CORNER_REGRESSION_MODEL_PATH)
    }

    /** Cached face detection model bytes, loaded lazily. May not be available. */
    private val faceDetectionModelBytes: ByteArray? by lazy {
        loadResourceOrNull(FACE_DETECTION_MODEL_PATH)
    }

    /** Cached orientation detection model bytes, loaded lazily. May not be available. */
    private val orientationModelBytes: ByteArray? by lazy {
        loadResourceOrNull(ORIENTATION_MODEL_PATH)
            ?: loadDownloadedModel(ORIENTATION_MODEL_FILENAME)
    }

    override fun loadDetectionModel(): ByteArray = detectionModelBytes

    override fun loadPoseModel(): ByteArray = poseModelBytes

    override fun loadCornerRegressionModel(): ByteArray = cornerRegressionModelBytes

    override fun detectionModelStream(): InputStream {
        val stream =
            javaClass.classLoader.getResourceAsStream(DETECTION_MODEL_PATH)
                ?: throw ModelNotFoundException(
                    "Detection model not found on classpath: $DETECTION_MODEL_PATH"
                )
        return stream
    }

    override fun poseModelStream(): InputStream {
        val stream =
            javaClass.classLoader.getResourceAsStream(POSE_MODEL_PATH)
                ?: throw ModelNotFoundException(
                    "Pose model not found on classpath: $POSE_MODEL_PATH"
                )
        return stream
    }

    override fun cornerRegressionModelStream(): InputStream {
        val stream =
            javaClass.classLoader.getResourceAsStream(CORNER_REGRESSION_MODEL_PATH)
                ?: throw ModelNotFoundException(
                    "Corner regression model not found on classpath: $CORNER_REGRESSION_MODEL_PATH"
                )
        return stream
    }

    override fun isModelAvailable(): Boolean {
        return javaClass.classLoader.getResource(DETECTION_MODEL_PATH) != null &&
            javaClass.classLoader.getResource(POSE_MODEL_PATH) != null &&
            javaClass.classLoader.getResource(CORNER_REGRESSION_MODEL_PATH) != null
    }

    override fun detectionModelVersion(): String = "detection_ep47"

    override fun poseModelVersion(): String = "pose_single_ep42"

    override fun cornerRegressionModelVersion(): String = "corner_regression_v2"

    override fun loadFaceDetectionModel(): ByteArray =
        faceDetectionModelBytes
            ?: throw ModelNotFoundException(
                "Face detection model not found on classpath: $FACE_DETECTION_MODEL_PATH"
            )

    override fun isFaceDetectionModelAvailable(): Boolean =
        javaClass.classLoader.getResource(FACE_DETECTION_MODEL_PATH) != null

    // ── Orientation Detection Model ──────────────────────────────────────

    override fun loadOrientationModel(): ByteArray =
        orientationModelBytes
            ?: throw ModelNotFoundException(
                "Orientation detection model not found on classpath: $ORIENTATION_MODEL_PATH"
            )

    override fun isOrientationDetectionModelAvailable(): Boolean =
        javaClass.classLoader.getResource(ORIENTATION_MODEL_PATH) != null ||
            File(DOWNLOADED_MODEL_DIR, ORIENTATION_MODEL_FILENAME).exists()

    override fun orientationModelVersion(): String = "orientation_vit_v1"

    /**
     * Loads a classpath resource fully into a byte array.
     *
     * @throws ModelNotFoundException if the resource is not found or cannot be read
     */
    private fun loadResource(path: String): ByteArray {
        val stream =
            javaClass.classLoader.getResourceAsStream(path)
                ?: throw ModelNotFoundException("Model resource not found on classpath: $path")

        return try {
            stream.use { it.readBytes() }
        } catch (e: Exception) {
            throw ModelNotFoundException("Failed to read model resource: $path", e)
        }
    }

    /**
     * Loads a classpath resource into a byte array, returning null if not found.
     *
     * Used for optional models that may not be bundled in all builds.
     */
    private fun loadResourceOrNull(path: String): ByteArray? {
        val stream = javaClass.classLoader.getResourceAsStream(path) ?: return null
        return try {
            stream.use { it.readBytes() }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Loads a model from the downloaded model directory (`~/.petrie-importer/models/`).
     *
     * Returns null if the file doesn't exist or can't be read. This is used as a fallback when the
     * model is not bundled on the classpath (e.g., the orientation model is lazy-downloaded).
     */
    private fun loadDownloadedModel(fileName: String): ByteArray? {
        val file = File(DOWNLOADED_MODEL_DIR, fileName)
        if (!file.exists() || file.length() == 0L) return null
        return try {
            file.readBytes()
        } catch (_: Exception) {
            null
        }
    }
}
