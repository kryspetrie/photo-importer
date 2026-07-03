package org.kryspetrie.fileimport.infrastructure.photoscan

import ai.onnxruntime.OrtEnvironment
import java.awt.image.BufferedImage
import org.kryspetrie.fileimport.domain.model.DetectedPhoto
import org.kryspetrie.fileimport.domain.model.DetectionMode
import org.kryspetrie.fileimport.domain.model.ProcessedImage
import org.kryspetrie.fileimport.domain.port.ModelResourcePort
import org.kryspetrie.fileimport.domain.port.PhotoScanDetectorPort
import org.kryspetrie.fileimport.infrastructure.adapter.OrtSessionFactory
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
 * ONNX sessions are created through [OrtSessionFactory] which enables GPU acceleration (CoreML on
 * macOS, CUDA on Linux, DirectML on Windows) when available, falling back to CPU gracefully.
 *
 * @param rectangleDetector Edge-based rectangle detector (for CV mode)
 * @param maxPhotos Maximum number of photos to detect (default 4)
 * @param modelResourcePort ONNX model loading interface (for YOLO mode)
 * @param ortSessionFactory Factory for creating GPU-accelerated ONNX sessions
 * @param appLogger Optional logger for diagnostic output
 */
class PhotoScanDetectorService(
    private val rectangleDetector: RectangleDetector = RectangleDetector(),
    private val maxPhotos: Int = 4,
    private val modelResourcePort: ModelResourcePort? = null,
    private val ortSessionFactory: OrtSessionFactory? = null,
    private val appLogger: AppLogger? = null,
) : PhotoScanDetectorPort {

    /** Port implementation — delegates to the [BufferedImage] overload after conversion. */
    override fun detectPhotos(image: ProcessedImage): List<DetectedPhoto> =
        detectPhotos(image.toBufferedImage())

    private val cvDetector = HybridCornerDetector(rectangleDetector)

    /** Lazily initialized YOLO pipeline (only when models are available) */
    private val yoloPipeline: YoloPhotoScanPipeline? by lazy { initYoloPipeline() }

    /**
     * Preload the YOLO detection pipeline eagerly.
     *
     * Call this early in the application lifecycle (e.g. when the user first selects files)
     * to front-load the model loading cost. Without preloading, the first call to [detectPhotos]
     * pays the full cost of: classpath I/O (~57 MB of ONNX models) + ONNX session creation +
     * GPU provider probing + graph optimization, which adds 1-3 seconds of latency.
     *
     * After preloading, subsequent [detectPhotos] calls start immediately since the pipeline
     * is already initialized. This method is idempotent — calling it after the pipeline is
     * already loaded is a no-op.
     *
     * @return true if the YOLO pipeline was successfully initialized, false if models are
     *   unavailable or initialization failed
     */
    override fun preload(): Boolean {
        return yoloPipeline != null
    }

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
            val factory = ortSessionFactory ?: OrtSessionFactory()
            val env = OrtEnvironment.getEnvironment()

            val detSession = factory.createSession(mlp.loadDetectionModel())
            val poseSession = factory.createSession(mlp.loadPoseModel())
            val cornerSession = factory.createSession(mlp.loadCornerRegressionModel())

            val detectionService = YoloDetectionService(env, detSession)
            val poseService = YoloPoseService(env, poseSession)
            val cornerService = YoloCornerRegressionService(env, cornerSession)

            appLogger?.info(
                "PhotoScanDetectorService: YOLO pipeline initialized successfully " +
                    "(execution provider: ${factory.activeProvider.displayName})"
            )
            YoloPhotoScanPipeline(detectionService, poseService, cornerService)
        } catch (e: Exception) {
            appLogger?.error(
                "PhotoScanDetectorService: Failed to initialize YOLO pipeline: ${e.message}"
            )
            null
        }
    }
}
