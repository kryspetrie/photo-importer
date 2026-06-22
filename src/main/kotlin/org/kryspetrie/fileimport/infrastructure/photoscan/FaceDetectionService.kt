package org.kryspetrie.fileimport.infrastructure.photoscan

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.awt.image.BufferedImage
import org.kryspetrie.fileimport.domain.model.ProcessedImage
import org.kryspetrie.fileimport.domain.port.DetectedFace
import org.kryspetrie.fileimport.domain.port.FaceDetectionPort
import org.kryspetrie.fileimport.domain.port.ModelResourcePort
import org.kryspetrie.fileimport.infrastructure.adapter.toBufferedImage
import org.kryspetrie.fileimport.infrastructure.photoscan.yolo.YoloFaceDetectionService

/**
 * Infrastructure adapter implementing [FaceDetectionPort] using ONNX Runtime YOLOv8-face model.
 *
 * Lazily initializes the ONNX session when face detection is first requested. If the model file is
 * not available on the classpath, [isFaceDetectionAvailable] returns false and [detectFaces] throws.
 *
 * ## Model
 *
 * The face detection model (`models/face_detection_model.onnx`) is a YOLOv8-face model that outputs
 * bounding boxes plus 5 facial keypoints (left eye, right eye, nose, left mouth, right mouth).
 *
 * ## Threading
 *
 * ONNX Runtime sessions are thread-safe for concurrent inference calls. The [ai.onnx.runtime.OrtEnvironment]
 * is shared with other YOLO services (detection, pose, corner regression).
 *
 * @param modelResourcePort Model loading interface for obtaining the ONNX model bytes
 * @see FaceDetectionPort
 * @see YoloFaceDetectionService
 */
class FaceDetectionService(
    private val modelResourcePort: ModelResourcePort,
) : FaceDetectionPort {

    /** Lazily initialized face detection service (only when model is available). */
    private val faceService: YoloFaceDetectionService? by lazy { initFaceService() }

    override fun isFaceDetectionAvailable(): Boolean =
        modelResourcePort.isFaceDetectionModelAvailable()

    override fun detectFaces(
        image: ProcessedImage,
        confThreshold: Float,
        iouThreshold: Float,
    ): List<DetectedFace> {
        val service =
            faceService
                ?: throw IllegalStateException(
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

    /** Initialize the YOLO face detection service with the ONNX model. */
    private fun initFaceService(): YoloFaceDetectionService? {
        if (!modelResourcePort.isFaceDetectionModelAvailable()) return null
        return try {
            val env = OrtEnvironment.getEnvironment()
            val opts = OrtSession.SessionOptions()
            val session = env.createSession(modelResourcePort.loadFaceDetectionModel(), opts)
            YoloFaceDetectionService(env, session)
        } catch (_: Exception) {
            null
        }
    }
}