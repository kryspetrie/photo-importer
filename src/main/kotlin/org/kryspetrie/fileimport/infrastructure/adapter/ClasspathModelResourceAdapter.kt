package org.kryspetrie.fileimport.infrastructure.adapter

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
 * | File                          | Purpose                               | Approx. size |
 * |-------------------------------|---------------------------------------|--------------|
 * | `models/detection_model.onnx` | YOLO detection — finds bounding boxes | ~10 MB       |
 * | `models/pose_model.onnx`      | YOLO pose — finds 4-corner keypoints  | ~10 MB       |
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
        const val BUFFER_SIZE = 8192
    }

    /** Cached detection model bytes, loaded lazily. */
    private val detectionModelBytes: ByteArray by lazy { loadResource(DETECTION_MODEL_PATH) }

    /** Cached pose model bytes, loaded lazily. */
    private val poseModelBytes: ByteArray by lazy { loadResource(POSE_MODEL_PATH) }

    override fun loadDetectionModel(): ByteArray = detectionModelBytes

    override fun loadPoseModel(): ByteArray = poseModelBytes

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

    override fun isModelAvailable(): Boolean {
        return javaClass.classLoader.getResource(DETECTION_MODEL_PATH) != null &&
            javaClass.classLoader.getResource(POSE_MODEL_PATH) != null
    }

    override fun detectionModelVersion(): String {
        // Version derived from model filename — will be updated when models are retrained
        return "detection_model_v1"
    }

    override fun poseModelVersion(): String {
        return "pose_model_v1"
    }

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
}
