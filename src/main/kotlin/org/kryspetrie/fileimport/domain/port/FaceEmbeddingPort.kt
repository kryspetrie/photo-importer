package org.kryspetrie.fileimport.domain.port

import org.kryspetrie.fileimport.domain.model.FaceCrop
import org.kryspetrie.fileimport.domain.model.FaceEmbedding
import org.kryspetrie.fileimport.domain.model.ProcessedImage

/**
 * Port for extracting face embeddings from aligned face crops.
 *
 * Takes an aligned [FaceCrop] and produces a [FaceEmbedding] — a fixed-length
 * numeric vector that represents the face's identity for similarity comparison.
 *
 * **Model-agnostic design**: The port interface doesn't mandate a specific model.
 * The initial implementation uses MobileFaceNet (128-dim, ~4MB, ~5ms), but
 * any ONNX-compatible face recognition model can be swapped in by creating
 * a new adapter. The [FaceEmbedding.modelName] field records which model produced
 * each embedding, preventing invalid cross-model comparisons.
 *
 * ## Pipeline
 *
 * The full face identification pipeline is:
 * 1. **Detect**: [FaceDetectionPort.detectFaces] → `List<DetectedFace>` (bounding boxes)
 * 2. **Align**: [FaceAlignmentPort.alignFaces] → `List<FaceCrop?>` (112×112 aligned crops)
 * 3. **Embed**: [FaceEmbeddingPort.extractEmbeddings] → `List<FaceEmbedding?>` (128-dim vectors)
 * 4. **Match**: [PersonDirectory.findBestMatch] → `Person?` (known person or null)
 *
 * @see FaceCrop
 * @see FaceEmbedding
 * @see FaceAlignmentPort
 */
interface FaceEmbeddingPort {

    /**
     * Extract a face embedding from a single aligned face crop.
     *
     * Note: This method requires the source image to be loadable from [FaceCrop.sourcePath].
     * For batch extraction from a single source image, use [extractEmbeddings] which is
     * more efficient (loads the image once).
     *
     * @param sourceImage The source image containing the face.
     * @param faceRegion The detected face bounding box (pixel coordinates).
     * @return A face embedding, or null if extraction fails or the model is unavailable.
     */
    suspend fun extractEmbedding(sourceImage: ProcessedImage, faceRegion: DetectedFace): FaceEmbedding?

    /**
     * Extract embeddings for multiple detected faces from a single source image.
     *
     * More efficient than calling [extractEmbedding] individually because the source image
     * only needs to be loaded once.
     *
     * @param sourceImage The image containing the faces.
     * @param detectedFaces Bounding boxes of detected faces (pixel coordinates).
     * @return Embedding vectors for each face (null for failed extractions).
     *   Results are in the same order as [detectedFaces].
     */
    suspend fun extractEmbeddings(
        sourceImage: ProcessedImage,
        detectedFaces: List<DetectedFace>,
    ): List<FaceEmbedding?>

    /**
     * Whether the embedding model is available and ready.
     *
     * Call this before [extractEmbedding] to check if the feature is available.
     * The model may need to be downloaded first (see [ModelDownloadPort]).
     */
    fun isEmbeddingAvailable(): Boolean

    /**
     * Pre-load the embedding model eagerly.
     *
     * Without preloading, the first call to [extractEmbedding] pays the cost of loading
     * ~4MB of model bytes and creating an ONNX session. Call this early in the application
     * lifecycle to front-load that cost. Idempotent — calling it after the model is loaded
     * is a no-op.
     *
     * @return true if the model loaded successfully, false if unavailable.
     */
    suspend fun preload(): Boolean

    /**
     * Save a face thumbnail (cropped face image) for display in the People screen.
     *
     * Uses the same alignment pipeline as embedding extraction to produce a consistent
     * face crop, then resizes to a fixed thumbnail size and saves as JPEG.
     *
     * Implementations that don't support thumbnail generation should return null.
     *
     * @param sourceImage The source image containing the face.
     * @param face The detected face bounding box (pixel coordinates).
     * @param personId The person ID to use in the thumbnail filename.
     * @param embeddingId The embedding ID for uniqueness in the filename.
     * @return The absolute path to the saved thumbnail, or null if saving fails.
     */
    fun saveFaceThumbnail(
        sourceImage: ProcessedImage,
        face: DetectedFace,
        personId: String,
        embeddingId: String,
    ): String?
}