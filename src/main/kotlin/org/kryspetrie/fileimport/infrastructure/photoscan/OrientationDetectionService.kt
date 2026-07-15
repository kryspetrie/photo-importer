@file:Suppress("MagicNumber")

package org.kryspetrie.fileimport.infrastructure.photoscan

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
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
 * predicts a continuous orientation angle (0°–359.9°). The output is a single float representing
 * the predicted clockwise rotation angle. To correct a misoriented image, it should be rotated by
 * the **negative** (i.e., CCW) of the predicted angle.
 *
 * ## Preprocessing
 *
 * 1. Resize the input image to 224×224 (maintaining aspect ratio with letterbox padding)
 * 2. Convert to RGB float32 in [0, 1] range
 * 3. Transpose to NCHW format: [1, 3, 224, 224]
 *
 * ## Output
 *
 * The model outputs a single float value: the predicted orientation angle in degrees
 * (0 = upright, 90 = rotated 90° CW from upright, etc.). We map this to the nearest
 * [RotationAngle] for compatibility with the existing rotation system.
 *
 * ## GPU acceleration
 *
 * ONNX sessions are created through [OrtSessionFactory] which enables GPU acceleration (CoreML on
 * macOS, CUDA on Linux, DirectML on Windows) when available.
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

        /** Letterbox fill color (mid-gray, matching ViT preprocessing conventions). */
        private const val PAD_COLOR = 114

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
            session ?: error("Orientation detection model is not available. Call isOrientationDetectionAvailable() first.")
        val bufferedImage = image.toBufferedImage()
        return detectOrientationFromBufferedImage(bufferedImage, onnxSession, confidenceThreshold)
    }

    /**
     * Run orientation detection on a [BufferedImage].
     *
     * Preprocesses the image to 224×224 NCHW format, runs ONNX inference, and maps the predicted
     * angle to the nearest [RotationAngle].
     */
    private fun detectOrientationFromBufferedImage(
        image: BufferedImage,
        onnxSession: OrtSession,
        confidenceThreshold: Float,
    ): OrientationResult? {
        val env = OrtEnvironment.getEnvironment()

        // Step 1: Preprocess — resize to 224×224 with letterbox padding
        val inputTensor = preprocessImage(image, env)

        // Step 2: Run inference
        val inputName = onnxSession.inputNames.iterator().next()
        val results = onnxSession.run(mapOf(inputName to inputTensor))

        // Step 3: Parse output — single float angle prediction
        @Suppress("UNCHECKED_CAST") val output = results[0].value as? Array<FloatArray>


        if (output == null || output.isEmpty() || output[0].isEmpty()) return null

        val predictedAngle = output[0][0]

        // The model outputs the clockwise rotation angle. For orientation correction,
        // we need to normalize to [0, 360) and map to nearest RotationAngle.
        val normalizedAngle = ((predictedAngle % 360f) + 360f) % 360f

        // Confidence: the model doesn't output a separate confidence score, so we use
        // distance from a 90° boundary as a proxy. Closer to a 90° boundary = more ambiguous.
        val distanceToNearest90 = normalizedAngle % 90f
        val confidence = 1f - (distanceToNearest90.coerceAtMost(90f - distanceToNearest90)) / 45f

        if (confidence < confidenceThreshold) return null

        return OrientationResult(
            angleDegrees = normalizedAngle,
            confidence = confidence,
            nearestRotation = normalizedAngle.toNearestRotationAngle(),
        )
    }

    /**
     * Preprocess an image for ViT orientation detection.
     *
     * Applies letterbox resize to 224×224 with gray padding, normalizes to [0, 1], and arranges
     * in NCHW format [1, 3, 224, 224].
     */
    private fun preprocessImage(image: BufferedImage, env: OrtEnvironment): OnnxTensor {
        val origW = image.width
        val origH = image.height

        // Letterbox resize: scale to fit 224×224 while maintaining aspect ratio
        val scale = minOf(
            INPUT_SIZE.toFloat() / origW,
            INPUT_SIZE.toFloat() / origH,
        )
        val scaledW = (origW * scale).toInt()
        val scaledH = (origH * scale).toInt()
        val padW = (INPUT_SIZE - scaledW) / 2
        val padH = (INPUT_SIZE - scaledH) / 2

        // Create padded image
        val padded = BufferedImage(INPUT_SIZE, INPUT_SIZE, BufferedImage.TYPE_INT_RGB)
        val g = padded.createGraphics()
        g.color = java.awt.Color(PAD_COLOR, PAD_COLOR, PAD_COLOR)
        g.fillRect(0, 0, INPUT_SIZE, INPUT_SIZE)
        g.drawImage(image, padW, padH, scaledW, scaledH, null)
        g.dispose()

        // Convert to NCHW float [1, 3, 224, 224], normalized to [0, 1]
        val floatArray = FloatArray(1 * 3 * INPUT_SIZE * INPUT_SIZE)
        var idx = 0
        for (c in 0 until 3) {
            for (y in 0 until INPUT_SIZE) {
                for (x in 0 until INPUT_SIZE) {
                    val rgb = padded.getRGB(x, y)
                    val channel = when (c) {
                        0 -> (rgb shr 16) and 0xFF // R
                        1 -> (rgb shr 8) and 0xFF  // G
                        2 -> rgb and 0xFF           // B
                        else -> 0
                    }
                    floatArray[idx++] = channel / 255.0f
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