package org.kryspetrie.fileimport.infrastructure.photoscan

import ai.onnxruntime.OrtEnvironment
import java.awt.image.BufferedImage
import org.kryspetrie.fileimport.domain.model.ProcessedImage
import org.kryspetrie.fileimport.domain.port.DetectedFace
import org.kryspetrie.fileimport.domain.port.FaceDetectionPort
import org.kryspetrie.fileimport.domain.port.ModelResourcePort
import org.kryspetrie.fileimport.infrastructure.adapter.OrtSessionFactory
import org.kryspetrie.fileimport.infrastructure.adapter.toBufferedImage
import org.kryspetrie.fileimport.infrastructure.photoscan.yolo.YoloFaceDetectionService

/**
 * Infrastructure adapter implementing [FaceDetectionPort] using ONNX Runtime YOLO face detection model.
 *
 * Lazily initializes the ONNX session when face detection is first requested. If the model file is
 * not available on the classpath, [isFaceDetectionAvailable] returns false and [detectFaces] throws.
 *
 * ## Model
 *
 * The face detection model (`models/face_detection_model.onnx`) is a YOLO12n-face model that outputs
 * face bounding boxes with confidence scores in NMS-filtered `[1, 300, 6]` format
 * (x1, y1, x2, y2, confidence, class).
 *
 * ## GPU Acceleration
 *
 * ONNX sessions are created through [OrtSessionFactory] which enables GPU acceleration (CoreML on
 * macOS, CUDA on Linux, DirectML on Windows) when available. The [OrtSessionFactory] is shared with
 * other YOLO services to ensure consistent execution provider selection.
 *
 * ## Threading
 *
 * ONNX Runtime sessions are thread-safe for concurrent inference calls. The [ai.onnx.runtime.OrtEnvironment]
 * is shared with other YOLO services (detection, pose, corner regression).
 *
 * @param modelResourcePort Model loading interface for obtaining the ONNX model bytes
 * @param ortSessionFactory Factory for creating GPU-accelerated ONNX sessions
 * @see FaceDetectionPort
 * @see YoloFaceDetectionService
 */
class FaceDetectionService(
    private val modelResourcePort: ModelResourcePort,
    private val ortSessionFactory: OrtSessionFactory,
) : FaceDetectionPort {

    /** Lazily initialized face detection service (only when model is available). */
    private val faceService: YoloFaceDetectionService? by lazy { initFaceService() }

    /**
     * Preload the face detection model eagerly.
     *
     * Call this early in the application lifecycle to front-load the model loading cost.
     * Without preloading, the first call to [detectFaces] pays the cost of classpath I/O
     * (~10 MB) + ONNX session creation + GPU provider probing.
     *
     * This method is idempotent — calling it after the service is already loaded is a no-op.
     *
     * @return true if the face detection service was successfully initialized, false if the
     *   model is unavailable or initialization failed
     */
    override fun preload(): Boolean {
        return faceService != null
    }

    override fun isFaceDetectionAvailable(): Boolean =
        modelResourcePort.isFaceDetectionModelAvailable()

    override fun detectFaces(
        image: ProcessedImage,
        confThreshold: Float,
        iouThreshold: Float,
    ): List<DetectedFace> {
        val service =
            faceService
                ?: error(
                    "Face detection model is not available. Call isFaceDetectionAvailable() first."
                )
        val bufferedImage = image.toBufferedImage()
        return service.detectFaces(bufferedImage, confThreshold, iouThreshold).map { det ->
            DetectedFace(
                x1 = det.x1,
                y1 = det.y1,
                x2 = det.x2,
                y2 = det.y2,
                confidence = det.confidence,
            )
        }
    }

    /** Initialize the YOLO face detection service with ONNX model + GPU acceleration. */
    private fun initFaceService(): YoloFaceDetectionService? {
        if (!modelResourcePort.isFaceDetectionModelAvailable()) return null
        return try {
            val env = OrtEnvironment.getEnvironment()
            val session = ortSessionFactory.createSession(modelResourcePort.loadFaceDetectionModel())
            YoloFaceDetectionService(env, session)
        } catch (_: Exception) {
            null
        }
    }
}
