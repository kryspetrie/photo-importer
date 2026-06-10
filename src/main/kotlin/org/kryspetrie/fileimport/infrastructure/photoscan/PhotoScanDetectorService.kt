package org.kryspetrie.fileimport.infrastructure.photoscan

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.awt.image.BufferedImage
import org.kryspetrie.fileimport.domain.model.DetectedPhoto
import org.kryspetrie.fileimport.domain.model.DetectionMode
import org.kryspetrie.fileimport.domain.model.ProcessedImage
import org.kryspetrie.fileimport.domain.port.ModelResourcePort
import org.kryspetrie.fileimport.domain.port.PhotoScanDetectorPort
import org.kryspetrie.fileimport.infrastructure.adapter.toBufferedImage
import org.kryspetrie.fileimport.infrastructure.adapter.toProcessedImage
import org.kryspetrie.fileimport.infrastructure.logging.AppLogger
import org.kryspetrie.fileimport.infrastructure.photoscan.yolo.YoloCornerRegressionService
import org.kryspetrie.fileimport.infrastructure.photoscan.yolo.YoloDetectionService
import org.kryspetrie.fileimport.infrastructure.photoscan.yolo.YoloPhotoScanPipeline
import org.kryspetrie.fileimport.infrastructure.photoscan.yolo.YoloPoseService

/**
 * Service for detecting photo boundaries in scanned images.
 *
 * Supports two detection modes:
 * - **CV mode**: Classical computer vision (RectangleDetector → HybridCornerDetector)
 * - **YOLO mode**: Neural network detection (YoloPhotoScanPipeline) — high accuracy, matches
 *   photocrop.py behavior
 *
 * When YOLO models are available, YOLO mode is preferred. Falls back to CV mode otherwise.
 *
 * @param rectangleDetector Edge-based rectangle detector (for CV mode)
 * @param maxPhotos Maximum number of photos to detect (default 4)
 * @param modelResourcePort ONNX model loading interface (for YOLO mode)
 * @param appLogger Optional logger for diagnostic output
 */
class PhotoScanDetectorService(
    private val rectangleDetector: RectangleDetector = RectangleDetector(),
    private val maxPhotos: Int = 4,
    private val modelResourcePort: ModelResourcePort? = null,
    private val appLogger: AppLogger? = null,
) : PhotoScanDetectorPort {

    /** Port implementation — delegates to the [BufferedImage] overload after conversion. */
    override fun detectPhotos(image: ProcessedImage): List<DetectedPhoto> =
        detectPhotos(image.toBufferedImage())

    private val cvDetector = HybridCornerDetector(rectangleDetector)

    /** Lazily initialized YOLO pipeline (only when models are available) */
    private val yoloPipeline: YoloPhotoScanPipeline? by lazy { initYoloPipeline() }

    /**
     * Detects rectangular photo regions in a scanned image.
     *
     * Uses YOLO mode if models are available, otherwise falls back to CV mode.
     *
     * @param image The scanned image
     * @return [DetectedPhoto] objects with corners ordered TL→TR→BR→BL.
     */
    fun detectPhotos(image: BufferedImage): List<DetectedPhoto> {
        val pipeline = yoloPipeline
        if (pipeline != null) {
            appLogger?.info("PhotoScanDetectorService: Using YOLO detection pipeline")
            return pipeline.detectPhotos(image = image)
        }

        appLogger?.info(
            "PhotoScanDetectorService: Using classical CV detection (YOLO models not available)"
        )
        cvDetector.targetPhotoCount = maxPhotos
        return cvDetector.detectPhotos(image.toProcessedImage()).map {
            it.copy(detectionMode = DetectionMode.COMPUTER_VISION)
        }
    }

    /**
     * Detects photos using YOLO mode with custom configuration.
     *
     * @param image The scanned image
     * @param config Pipeline configuration
     * @return [DetectedPhoto] objects with corners ordered TL→TR→BR→BL
     * @throws IllegalStateException if YOLO models are not available
     */
    fun detectPhotosWithConfig(
        image: BufferedImage,
        config: YoloPhotoScanPipeline.PipelineConfig,
    ): List<DetectedPhoto> {
        val pipeline = yoloPipeline ?: error("YOLO detection not available — models not loaded")
        return pipeline.detectPhotos(image, config)
    }

    /** Detects photos using classical CV mode (regardless of YOLO availability). */
    fun detectPhotosCv(image: BufferedImage): List<DetectedPhoto> {
        cvDetector.targetPhotoCount = maxPhotos
        return cvDetector.detectPhotos(image.toProcessedImage()).map {
            it.copy(detectionMode = DetectionMode.COMPUTER_VISION)
        }
    }

    /** Returns true if YOLO models are available and loaded. */
    fun isYoloAvailable(): Boolean = modelResourcePort?.isModelAvailable() == true

    private fun initYoloPipeline(): YoloPhotoScanPipeline? {
        val mlp = modelResourcePort ?: return null
        if (!mlp.isModelAvailable()) return null

        return try {
            val env = OrtEnvironment.getEnvironment()
            val detOpts = OrtSession.SessionOptions()
            val poseOpts = OrtSession.SessionOptions()
            val cornerOpts = OrtSession.SessionOptions()
            val detSession = env.createSession(mlp.loadDetectionModel(), detOpts)
            val poseSession = env.createSession(mlp.loadPoseModel(), poseOpts)
            val cornerSession = env.createSession(mlp.loadCornerRegressionModel(), cornerOpts)

            val detectionService = YoloDetectionService(env, detSession)
            val poseService = YoloPoseService(env, poseSession)
            val cornerService = YoloCornerRegressionService(env, cornerSession)

            appLogger?.info("PhotoScanDetectorService: YOLO pipeline initialized successfully")
            YoloPhotoScanPipeline(detectionService, poseService, cornerService)
        } catch (e: Exception) {
            appLogger?.error(
                "PhotoScanDetectorService: Failed to initialize YOLO pipeline: ${e.message}"
            )
            null
        }
    }
}
