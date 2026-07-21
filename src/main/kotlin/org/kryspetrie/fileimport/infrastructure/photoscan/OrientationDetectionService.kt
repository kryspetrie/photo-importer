@file:Suppress("MagicNumber")

package org.kryspetrie.fileimport.infrastructure.photoscan

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.nio.FloatBuffer
import org.kryspetrie.fileimport.domain.model.OrientationResult
import org.kryspetrie.fileimport.domain.model.ProcessedImage
import org.kryspetrie.fileimport.domain.model.RotationAngle
import org.kryspetrie.fileimport.domain.port.ModelResourcePort
import org.kryspetrie.fileimport.domain.port.OrientationDetectionPort
import org.kryspetrie.fileimport.domain.port.toNearestRotationAngle
import org.kryspetrie.fileimport.infrastructure.adapter.OrtSessionFactory
import org.kryspetrie.fileimport.infrastructure.adapter.toBufferedImage

/**
 * Infrastructure adapter implementing [OrientationDetectionPort] using the
 * deep-image-orientation-angle-detection ONNX model.
 *
 * The model is a Vision Transformer (ViT) fine-tuned from `google/vit-base-patch16-224` that
 * predicts a continuous orientation angle (0°–359.9°). The output represents how much the image is
 * rotated **clockwise from upright**. To correct the image, rotate it by the negation
 * (counter-clockwise) of the predicted angle.
 *
 * ## Model angle semantics
 *
 * The model output `y` represents the **orientation angle** (CW rotation from upright):
 * - y ≈ 0° → image is upright → no correction needed
 * - y ≈ 90° → image was rotated 90° CW → correct by rotating 90° CCW (or 270° CW)
 * - y ≈ 180° → image is upside down → correct by rotating 180°
 * - y ≈ 270° → image was rotated 270° CW (= 90° CCW) → correct by rotating 90° CW
 *
 * The **correction angle** (how much to rotate CW to fix the image) is: `correction = (360° - y) %
 * 360°`
 *
 * This correction maps to the nearest [RotationAngle] for the metadata editor's rotation system.
 *
 * ## Preprocessing
 *
 * Matches the `ViTImageProcessor` preprocessing from `google/vit-base-patch16-224`:
 * 1. Resize the input image to 224×224 (bicubic interpolation) — **not** letterbox padding
 * 2. Convert RGB pixels to float and normalize to **[-1, 1]** using: `normalized = (pixel / 255.0 -
 *    0.5) / 0.5` This matches `image_mean = (0.5, 0.5, 0.5)` and `image_std = (0.5, 0.5, 0.5)`
 * 3. Arrange in NCHW format: `[1, 3, 224, 224]` with RGB channel order
 *
 * @param modelResourcePort Model loading interface for obtaining the ONNX model bytes
 * @param ortSessionFactory Factory for creating GPU-accelerated ONNX sessions
 * @see OrientationDetectionPort
 * @see OrientationResult
 */
class OrientationDetectionService(
    private val modelResourcePort: ModelResourcePort,
    private val ortSessionFactory: OrtSessionFactory,
) : OrientationDetectionPort {

    /** Lazily initialized ONNX session (only when model is available). */
    private val session: OrtSession? by lazy { initSession() }

    companion object {
        /** ViT input size — the model expects 224×224 images. */
        private const val INPUT_SIZE = 224

        /** ViT normalization: mean and std for each channel (RGB). */
        private val IMAGE_MEAN = floatArrayOf(0.5f, 0.5f, 0.5f)
        private val IMAGE_STD = floatArrayOf(0.5f, 0.5f, 0.5f)

        /** Minimum confidence for a detection to be considered reliable. */
        private const val DEFAULT_CONFIDENCE_THRESHOLD = 0.3f
    }

    override fun preload(): Boolean = session != null

    override fun isOrientationDetectionAvailable(): Boolean =
        modelResourcePort.isOrientationDetectionModelAvailable()

    override fun detectOrientation(
        image: ProcessedImage,
        confidenceThreshold: Float,
    ): OrientationResult? {
        val onnxSession =
            session
                ?: error(
                    "Orientation detection model is not available. " +
                        "Call isOrientationDetectionAvailable() first."
                )
        val bufferedImage = image.toBufferedImage()
        return detectOrientationFromBufferedImage(bufferedImage, onnxSession, confidenceThreshold)
    }

    /**
     * Run orientation detection on a [BufferedImage].
     *
     * Preprocesses the image to 224×224 NCHW format with ViT normalization, runs ONNX inference,
     * and maps the predicted angle to the nearest [RotationAngle].
     */
    private fun detectOrientationFromBufferedImage(
        image: BufferedImage,
        onnxSession: OrtSession,
        confidenceThreshold: Float,
    ): OrientationResult? {
        val env = OrtEnvironment.getEnvironment()

        // Step 1: Preprocess — resize to 224×224 with ViT normalization
        val inputTensor = preprocessImage(image, env)

        // Step 2: Run inference
        val inputName = onnxSession.inputNames.iterator().next()
        val results = onnxSession.run(mapOf(inputName to inputTensor))

        // Step 3: Parse output — single float angle prediction
        @Suppress("UNCHECKED_CAST") val output = results[0].value as? Array<FloatArray>

        if (output == null || output.isEmpty() || output[0].isEmpty()) return null

        val predictedAngle = output[0][0]

        // The model predicts the CW correction angle — how much to rotate CW to make the
        // image upright. For example, y ≈ 270° means "rotate 270° CW (= 90° CCW) to correct".
        // y ≈ 0° means the image is already upright.
        // Normalize to [0, 360).
        val correctionDegrees = ((predictedAngle % 360f) + 360f) % 360f

        // The "orientation" angle (how much the image is rotated CW from upright) is the
        // complement: orientation = (360 - correction) % 360.
        val orientationDegrees = ((360f - correctionDegrees) + 360f) % 360f

        // Confidence proxy: measure how close the orientation angle is to a 90° boundary.
        // Near a boundary (e.g., 45°, 135°) means the model is more uncertain about which
        // direction the image is rotated. Closer to 0°/90°/180°/270° = higher confidence.
        val distanceToNearest90 = orientationDegrees % 90f
        val distanceFromBoundary = distanceToNearest90.coerceAtMost(90f - distanceToNearest90)
        val confidence = 1f - (distanceFromBoundary / 45f)

        if (confidence < confidenceThreshold) return null

        return OrientationResult(
            orientationDegrees = orientationDegrees,
            confidence = confidence,
            nearestRotation = correctionDegrees.toNearestRotationAngle(),
            correctionDegrees = correctionDegrees,
        )
    }

    /**
     * Preprocess an image for ViT orientation detection.
     *
     * Matches the `google/vit-base-patch16-224` `ViTImageProcessor` preprocessing:
     * 1. Resize to 224×224 using bicubic interpolation (no letterbox padding)
     * 2. Convert RGB pixels to float, normalize to [-1, 1]: `normalized = (pixel / 255.0 - mean) /
     *    std` where mean=0.5, std=0.5
     * 3. Arrange in NCHW format: [1, 3, 224, 224] with RGB channel order
     */
    private fun preprocessImage(image: BufferedImage, env: OrtEnvironment): OnnxTensor {
        // Resize to 224×224 using bicubic interpolation
        val resized = BufferedImage(INPUT_SIZE, INPUT_SIZE, BufferedImage.TYPE_INT_RGB)
        val g2d = resized.createGraphics()
        g2d.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BICUBIC,
        )
        g2d.drawImage(image, 0, 0, INPUT_SIZE, INPUT_SIZE, null)
        g2d.dispose()

        // Convert to NCHW float [1, 3, 224, 224] with ViT normalization
        val floatArray = FloatArray(1 * 3 * INPUT_SIZE * INPUT_SIZE)
        var idx = 0
        for (c in 0 until 3) {
            for (y in 0 until INPUT_SIZE) {
                for (x in 0 until INPUT_SIZE) {
                    val rgb = resized.getRGB(x, y)
                    val channel =
                        when (c) {
                            0 -> (rgb shr 16) and 0xFF // R
                            1 -> (rgb shr 8) and 0xFF // G
                            2 -> rgb and 0xFF // B
                            else -> 0
                        }
                    // Normalize: (pixel/255 - mean) / std = (pixel/255 - 0.5) / 0.5
                    floatArray[idx++] = (channel / 255.0f - IMAGE_MEAN[c]) / IMAGE_STD[c]
                }
            }
        }

        val shape = longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(floatArray), shape)
    }

    /** Initialize the ONNX session for orientation detection. */
    private fun initSession(): OrtSession? {
        if (!modelResourcePort.isOrientationDetectionModelAvailable()) return null
        return try {
            ortSessionFactory.createSession(modelResourcePort.loadOrientationModel())
        } catch (_: Exception) {
            null
        }
    }
}
