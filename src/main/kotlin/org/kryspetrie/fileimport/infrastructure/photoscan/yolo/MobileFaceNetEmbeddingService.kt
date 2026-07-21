package org.kryspetrie.fileimport.infrastructure.photoscan.yolo

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.awt.image.BufferedImage
import java.nio.FloatBuffer
import kotlin.math.sqrt

/**
 * MobileFaceNet ONNX inference service — extracts face embedding vectors.
 *
 * Takes a 112×112 RGB face crop and produces an embedding vector. Two embeddings
 * of the same person should have high cosine similarity (>0.65), while different people should
 * have low similarity (<0.4).
 *
 * ## Model
 *
 * The default model (ArcFace MobileFaceNet from Hailo Model Zoo) is a lightweight face
 * recognition model (~8MB ONNX) that takes a 112×112×3 NCHW float32 input (normalized to
 * [−1, 1]) and outputs a 512-dimensional L2-normalized embedding vector.
 *
 * Earlier MobileFaceNet models produced 128-dimensional embeddings; the embedding dimension
 * is now determined dynamically from the model's output shape, and both 128-dim and 512-dim
 * outputs are supported.
 *
 * ## Preprocessing Pipeline
 *
 * 1. Crop face region from source image (done by caller)
 * 2. Resize to 112×112 RGB
 * 3. Normalize pixel values: `pixel = (pixel / 255.0 - 0.5) / 0.5` → [−1, 1]
 * 4. Convert to NCHW float32 tensor [1, 3, 112, 112]
 *
 * ## Alignment
 *
 * **Phase 1 (Current)**: Simple center-crop alignment. The face bounding box from YOLO detection
 * is expanded by 20% on each side (to capture full head) and then resized to 112×112. This
 * provides reasonable accuracy for frontal faces but lower accuracy for profile/angled faces.
 *
 * **Phase 2 (Future)**: Landmark-based affine alignment using 5-point facial landmarks. A
 * dedicated alignment model (e.g., LFPAlight) will detect eye/nose/mouth positions and apply
 * an affine transform to normalize face pose before embedding extraction.
 *
 * @param env ONNX Runtime environment (shared singleton)
 * @param session ONNX Runtime session for the MobileFaceNet model
 */
class MobileFaceNetEmbeddingService(private val env: OrtEnvironment, private val session: OrtSession) {

    companion object {
        /** MobileFaceNet input image size (112×112). */
        const val INPUT_SIZE = 112

        /**
         * Legacy embedding dimension constant for backward compatibility.
         *
         * The actual embedding dimension is determined dynamically from the model's output shape.
         * ArcFace MobileFaceNet produces 512-dimensional embeddings; earlier models produced 128.
         *
         * @see FaceEmbedding.DIM_MOBILEFACENET
         * @see FaceEmbedding.DIM_ARCFACE_R50
         */
        @Deprecated("Use FaceEmbedding.DIM_MOBILEFACENET or FaceEmbedding.DIM_ARCFACE_R50 instead")
        const val EMBEDDING_DIM = 128

        /** Bounding box expansion factor for center-crop alignment.
         *  0.2 = expand each side by 20% of the face size (40% total width/height increase). */
        const val CROP_EXPANSION = 0.2f
    }

    /**
     * Extract a face embedding from a pre-aligned 112×112 face crop.
     *
     * @param faceCrop A 112×112 RGB BufferedImage of the aligned face.
     * @return A float array (L2-normalized embedding vector). Dimension depends on the model
     *   (128 for classic MobileFaceNet, 512 for ArcFace MobileFaceNet).
     */
    fun extractEmbedding(faceCrop: BufferedImage): FloatArray {
        val inputTensor = preprocessFaceCrop(faceCrop)
        val results = session.run(mapOf(session.inputNames.iterator().next() to inputTensor))

        val output = results[0].value as Array<FloatArray>
        return output[0]
    }

    /**
     * Extract a face embedding from a face region within a larger image.
     *
     * Crops the face region (with expansion for context), resizes to 112×112, and runs inference.
     *
     * @param image Source image (any size)
     * @param x1 Left edge of face bounding box (pixels)
     * @param y1 Top edge of face bounding box (pixels)
     * @param x2 Right edge of face bounding box (pixels)
     * @param y2 Bottom edge of face bounding box (pixels)
     * @return A float array (L2-normalized embedding vector). Dimension depends on the model.
     */
    fun extractEmbeddingFromRegion(
        image: BufferedImage,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
    ): FloatArray {
        val faceCrop = cropAndAlignFace(image, x1, y1, x2, y2)
        return extractEmbedding(faceCrop)
    }

    /**
     * Crop a face region from an image with expansion and resize to 112×112.
     *
     * Phase 1 alignment strategy: simple center-crop with 20% expansion.
     * The expansion captures the full head (hair, ears) which improves embedding quality.
     *
     * @param image Source image
     * @param x1 Left edge of detected face bounding box (pixels)
     * @param y1 Top edge of detected face bounding box (pixels)
     * @param x2 Right edge of detected face bounding box (pixels)
     * @param y2 Bottom edge of detected face bounding box (pixels)
     * @return A 112×112 RGB BufferedImage of the cropped and aligned face
     */
    fun cropAndAlignFace(
        image: BufferedImage,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
    ): BufferedImage {
        val faceW = x2 - x1
        val faceH = y2 - y1

        // Expand bounding box by CROP_EXPANSION on each side
        val expandW = faceW * CROP_EXPANSION
        val expandH = faceH * CROP_EXPANSION

        // Clamp to image boundaries
        val cropX1 = maxOf(0f, x1 - expandW).toInt()
        val cropY1 = maxOf(0f, y1 - expandH).toInt()
        val cropX2 = minOf(image.width.toFloat(), x2 + expandW).toInt()
        val cropY2 = minOf(image.height.toFloat(), y2 + expandH).toInt()

        val cropW = cropX2 - cropX1
        val cropH = cropY2 - cropY1

        if (cropW <= 0 || cropH <= 0) {
            // Fallback: use the full image if the crop is invalid
            return resizeToInputSize(image)
        }

        val cropped = image.getSubimage(cropX1, cropY1, cropW, cropH)
        return resizeToInputSize(cropped)
    }

    /**
     * Preprocess a 112×112 face crop for MobileFaceNet inference.
     *
     * Normalizes pixel values to [−1, 1] range and arranges in NCHW format:
     * - N = 1 (batch size)
     * - C = 3 (RGB channels)
     * - H = 112 (input height)
     * - W = 112 (input width)
     */
    internal fun preprocessFaceCrop(faceCrop: BufferedImage): OnnxTensor {
        val w = faceCrop.width
        val h = faceCrop.height
        val flatArray = FloatArray(3 * INPUT_SIZE * INPUT_SIZE)

        for (y in 0 until INPUT_SIZE) {
            for (x in 0 until INPUT_SIZE) {
                // Map destination pixels to source pixels (handles resizing)
                val srcX = (x * w) / INPUT_SIZE
                val srcY = (y * h) / INPUT_SIZE
                val rgb = faceCrop.getRGB(
                    srcX.coerceIn(0, w - 1),
                    srcY.coerceIn(0, h - 1),
                )

                val r = ((rgb shr 16) and 0xFF) / 127.5f - 1.0f
                val g = ((rgb shr 8) and 0xFF) / 127.5f - 1.0f
                val b = (rgb and 0xFF) / 127.5f - 1.0f

                val idx = y * INPUT_SIZE + x
                flatArray[0 * INPUT_SIZE * INPUT_SIZE + idx] = r
                flatArray[1 * INPUT_SIZE * INPUT_SIZE + idx] = g
                flatArray[2 * INPUT_SIZE * INPUT_SIZE + idx] = b
            }
        }

        val shape = longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(flatArray), shape)
    }

    /** Resize a BufferedImage to 112×112 using bilinear interpolation. */
    private fun resizeToInputSize(image: BufferedImage): BufferedImage {
        val resized = BufferedImage(INPUT_SIZE, INPUT_SIZE, BufferedImage.TYPE_INT_RGB)
        val graphics = resized.createGraphics()
        graphics.drawImage(image, 0, 0, INPUT_SIZE, INPUT_SIZE, null)
        graphics.dispose()
        return resized
    }
}