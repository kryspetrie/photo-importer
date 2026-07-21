package org.kryspetrie.fileimport.infrastructure.photoscan

import org.kryspetrie.fileimport.domain.model.FaceEmbedding
import org.kryspetrie.fileimport.domain.model.NormalizedRect
import org.kryspetrie.fileimport.domain.model.ProcessedImage
import org.kryspetrie.fileimport.domain.port.DetectedFace
import org.kryspetrie.fileimport.domain.port.FaceEmbeddingPort
import org.kryspetrie.fileimport.domain.port.ModelResourcePort
import org.kryspetrie.fileimport.infrastructure.adapter.OrtSessionFactory
import org.kryspetrie.fileimport.infrastructure.adapter.toBufferedImage
import org.kryspetrie.fileimport.infrastructure.logging.AppLogger
import org.kryspetrie.fileimport.infrastructure.photoscan.yolo.MobileFaceNetEmbeddingService
import kotlin.math.sqrt

/**
 * Infrastructure adapter implementing [FaceEmbeddingPort] using ArcFace MobileFaceNet ONNX inference.
 *
 * Orchestrates the face embedding extraction pipeline:
 * 1. **Align**: Crop face region with 20% expansion and resize to 112×112
 * 2. **Embed**: Extract embedding vector via ArcFace MobileFaceNet (512-dim or 128-dim)
 * 3. **Package**: Create [FaceEmbedding] data class with quality, model name, and region metadata
 *
 * ## Model
 *
 * ArcFace MobileFaceNet (~8MB ONNX) produces 512-dimensional L2-normalized embeddings.
 * Earlier MobileFaceNet models produced 128-dimensional embeddings; the dimension is determined
 * dynamically from the model output. Two embeddings of the same person should have cosine
 * similarity > 0.65 (configurable via [FaceMatchingConfig]).
 *
 * @see FaceEmbeddingPort
 * @see MobileFaceNetEmbeddingService
 */
class FaceEmbeddingAdapter(
    private val modelResourcePort: ModelResourcePort,
    private val ortSessionFactory: OrtSessionFactory,
    private val appLogger: AppLogger,
) : FaceEmbeddingPort {

    @Volatile
    private var embeddingService: MobileFaceNetEmbeddingService? = null
    private val lock = Any()

    override suspend fun extractEmbedding(
        sourceImage: ProcessedImage,
        faceRegion: DetectedFace,
    ): FaceEmbedding? {
        val results = extractEmbeddings(sourceImage, listOf(faceRegion))
        return results.firstOrNull()
    }

    override suspend fun extractEmbeddings(
        sourceImage: ProcessedImage,
        detectedFaces: List<DetectedFace>,
    ): List<FaceEmbedding?> {
        val service = getEmbeddingService() ?: return detectedFaces.map { null }
        val image = sourceImage.toBufferedImage()

        return detectedFaces.map { faceRegion ->
            try {
                // Check minimum face size
                val faceWidthPx = (faceRegion.x2 - faceRegion.x1).toInt()
                val faceHeightPx = (faceRegion.y2 - faceRegion.y1).toInt()
                if (faceWidthPx < MIN_FACE_SIZE_PX || faceHeightPx < MIN_FACE_SIZE_PX) {
                    return@map null
                }

                // Crop and align the face region (center-crop with 20% expansion)
                val alignedCrop = service.cropAndAlignFace(
                    image, faceRegion.x1, faceRegion.y1, faceRegion.x2, faceRegion.y2,
                )

                // Extract 128-dimensional embedding
                val embeddingVector = service.extractEmbedding(alignedCrop)

                // Check for zero/near-zero embeddings (bad face crop)
                val norm = sqrt(embeddingVector.fold(0.0) { acc, v -> acc + v * v.toDouble() }.toFloat())
                if (norm < MIN_EMBEDDING_NORM) return@map null

                // Create structured FaceEmbedding with metadata
                FaceEmbedding(
                    embeddingVector = embeddingVector,
                    quality = faceRegion.confidence,
                    estimatedYaw = 0f, // No yaw estimation in Phase 1
                    modelName = MODEL_NAME,
                    sourcePath = "",  // Set by caller (FaceGroupingService) which has the path
                    sourceRegion = NormalizedRect.fromDetectedFace(
                        faceRegion, image.width, image.height,
                    ),
                )
            } catch (e: Exception) {
                appLogger.warn("Face embedding extraction failed for face at (${faceRegion.x1},${faceRegion.y1}): ${e.message}")
                null
            }
        }
    }

    override fun isEmbeddingAvailable(): Boolean =
        modelResourcePort.isFaceEmbeddingModelAvailable()

    override suspend fun preload(): Boolean {
        return getEmbeddingService() != null
    }

    /** Thread-safe lazy initialization of the embedding service (creates ONNX session). */
    private fun getEmbeddingService(): MobileFaceNetEmbeddingService? {
        embeddingService?.let { return it }
        synchronized(lock) {
            // Double-check after acquiring lock
            embeddingService?.let { return it }
            if (!modelResourcePort.isFaceEmbeddingModelAvailable()) return null
            return try {
                val env = ai.onnxruntime.OrtEnvironment.getEnvironment()
                val session = ortSessionFactory.createSession(modelResourcePort.loadFaceEmbeddingModel())
                MobileFaceNetEmbeddingService(env, session).also {
                    embeddingService = it
                }
            } catch (e: Exception) {
                appLogger.error("Failed to initialize MobileFaceNet ONNX session", e)
                null
            }
        }
    }

    /**
     * Save a face thumbnail (cropped face image) to the thumbnails directory.
     *
     * Crops the face region from the source image using the detected face coordinates,
     * resizes to [THUMBNAIL_SIZE]×[THUMBNAIL_SIZE], and saves as JPEG.
     *
     * @return The absolute path to the saved thumbnail, or null if saving fails.
     */
    override fun saveFaceThumbnail(
        sourceImage: ProcessedImage,
        face: DetectedFace,
        personId: String,
        embeddingId: String,
    ): String? {
        return try {
            val image = sourceImage.toBufferedImage()
            val service = getEmbeddingService() ?: return null

            // Crop and align the face (reuse the same alignment pipeline as embedding)
            val faceCrop = service.cropAndAlignFace(
                image, face.x1, face.y1, face.x2, face.y2,
            )

            // Resize to thumbnail size
            val thumbnail = java.awt.image.BufferedImage(
                THUMBNAIL_SIZE, THUMBNAIL_SIZE, java.awt.image.BufferedImage.TYPE_INT_RGB,
            )
            val g2d = thumbnail.createGraphics()
            g2d.setRenderingHint(
                java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR,
            )
            g2d.drawImage(faceCrop, 0, 0, THUMBNAIL_SIZE, THUMBNAIL_SIZE, null)
            g2d.dispose()

            // Save to thumbnails directory
            val thumbnailFile = java.io.File(
                org.kryspetrie.fileimport.infrastructure.adapter.PeoplePaths.thumbnailsDir,
                "${personId}_${embeddingId}.jpg",
            )
            javax.imageio.ImageIO.write(thumbnail, "jpg", thumbnailFile)
            thumbnailFile.absolutePath
        } catch (e: Exception) {
            appLogger.warn("Failed to save face thumbnail for $personId: ${e.message}")
            null
        }
    }

    companion object {
        /** Model identifier stored in FaceEmbedding.modelName for cross-model guard. */
        const val MODEL_NAME = "arcface_mobilefacenet"

        /** Minimum face size in pixels to attempt embedding extraction. */
        private const val MIN_FACE_SIZE_PX = 50

        /** Minimum L2 norm of embedding vector to be considered valid. */
        private const val MIN_EMBEDDING_NORM = 0.01f

        /** Thumbnail size in pixels for face crop images shown in the People screen. */
        const val THUMBNAIL_SIZE = 96
    }
}