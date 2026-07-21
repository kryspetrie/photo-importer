package org.kryspetrie.fileimport.infrastructure.photoscan

import org.kryspetrie.fileimport.domain.model.FaceCrop
import org.kryspetrie.fileimport.domain.model.NormalizedRect
import org.kryspetrie.fileimport.domain.model.ProcessedImage
import org.kryspetrie.fileimport.domain.port.DetectedFace
import org.kryspetrie.fileimport.domain.port.FaceAlignmentPort
import org.kryspetrie.fileimport.domain.port.ModelResourcePort
import org.kryspetrie.fileimport.infrastructure.adapter.toBufferedImage

/**
 * Phase 1 face alignment adapter — metadata-only FaceCrops without ONNX inference.
 *
 * ⚠️ **NOT REGISTERED IN DI** — This adapter is not registered in [AppModule] because
 * [FaceEmbeddingAdapter] handles its own alignment internally. This adapter will be
 * registered and refactored for Phase 2 (landmark-based affine alignment).
 *
 * ## Phase 1 Limitations
 *
 * In Phase 1, this adapter produces metadata-only [FaceCrop] objects with normalized bounding
 * box coordinates but does **not** perform actual pixel-level alignment (crop + resize to 112×112).
 * The [FaceEmbeddingAdapter] handles its own center-crop alignment internally via
 * [MobileFaceNetEmbeddingService.cropAndAlignFace], so this adapter is unnecessary for the
 * current pipeline.
 *
 * No ONNX session is created — this adapter is purely computational geometry (coordinate
 * normalization), which avoids the ~4MB model load and session creation overhead.
 *
 * ## Phase 2 Plans
 *
 * When landmark-based alignment is added:
 * 1. A dedicated alignment model (e.g., LFPAlight, ~2MB) will detect 5-point facial landmarks
 * 2. An affine transform will normalize face pose to frontal
 * 3. This adapter will produce actual aligned face bitmaps, not just metadata
 * 4. Register in [AppModule] as `single<FaceAlignmentPort>`
 *
 * @see FaceAlignmentPort
 * @see FaceEmbeddingAdapter
 */
class FaceAlignmentAdapter(
    private val modelResourcePort: ModelResourcePort,
) : FaceAlignmentPort {

    override suspend fun alignFace(sourceImage: ProcessedImage, faceRegion: DetectedFace): FaceCrop? {
        val image = sourceImage.toBufferedImage()

        // Check minimum face size
        val faceWidthPx = (faceRegion.x2 - faceRegion.x1).toInt()
        val faceHeightPx = (faceRegion.y2 - faceRegion.y1).toInt()
        if (faceWidthPx < MIN_FACE_SIZE_PX || faceHeightPx < MIN_FACE_SIZE_PX) {
            return null
        }

        // Convert pixel bounding box to normalized coordinates
        val normalizedRegion = NormalizedRect.fromDetectedFace(
            faceRegion, image.width, image.height,
        )

        return FaceCrop(
            sourcePath = "",  // Set by caller which has the path context
            sourceRegion = normalizedRegion,
            alignedWidth = ALIGNED_SIZE,
            alignedHeight = ALIGNED_SIZE,
            yaw = 0f, // No yaw estimation in Phase 1
            detectionConfidence = faceRegion.confidence,
        )
    }

    override suspend fun alignFaces(
        sourceImage: ProcessedImage,
        faceRegions: List<DetectedFace>,
    ): List<FaceCrop?> {
        val image = sourceImage.toBufferedImage()

        return faceRegions.map { faceRegion ->
            val faceWidthPx = (faceRegion.x2 - faceRegion.x1).toInt()
            val faceHeightPx = (faceRegion.y2 - faceRegion.y1).toInt()
            if (faceWidthPx < MIN_FACE_SIZE_PX || faceHeightPx < MIN_FACE_SIZE_PX) {
                return@map null
            }

            val normalizedRegion = NormalizedRect.fromDetectedFace(
                faceRegion, image.width, image.height,
            )

            FaceCrop(
                sourcePath = "",  // Set by caller which has the path context
                sourceRegion = normalizedRegion,
                alignedWidth = ALIGNED_SIZE,
                alignedHeight = ALIGNED_SIZE,
                yaw = 0f,
                detectionConfidence = faceRegion.confidence,
            )
        }
    }

    override fun isAlignmentAvailable(): Boolean {
        // Phase 1: Alignment is always available — it's metadata-only (no ONNX model needed).
        // In Phase 2, this will check for a dedicated alignment model.
        return true
    }

    override suspend fun preload(): Boolean {
        // Phase 1: No ONNX session to preload — alignment is metadata-only
        return isAlignmentAvailable()
    }

    companion object {
        /** Minimum face size in pixels to attempt alignment. Faces smaller than this are rejected. */
        const val MIN_FACE_SIZE_PX = 50

        /** Aligned face crop size (112×112 for MobileFaceNet input). */
        private const val ALIGNED_SIZE = 112
    }
}