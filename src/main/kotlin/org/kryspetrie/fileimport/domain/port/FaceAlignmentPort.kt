package org.kryspetrie.fileimport.domain.port

import org.kryspetrie.fileimport.domain.model.FaceCrop
import org.kryspetrie.fileimport.domain.model.ProcessedImage

/**
 * Port for aligning detected face regions prior to embedding extraction.
 *
 * Face alignment is critical for embedding accuracy — without it, a 20° yaw can reduce
 * cosine similarity by 0.10-0.15, enough to push a true match below threshold.
 *
 * The alignment pipeline:
 * 1. Detect facial landmarks (eyes, nose, mouth) in the face region (**Phase 2**)
 * 2. Compute an affine transformation that normalizes the face to a frontal pose
 * 3. Crop and resize the aligned face to a standard size (112×112 for MobileFaceNet)
 *
 * ## Alignment Strategy (Two-Phase)
 *
 * **Phase 1 (Current)**: Center-crop with expansion. The face bounding box is expanded by 20%
 * on each side (to capture the full head) and resized to 112×112. This provides reasonable
 * accuracy for frontal faces (~50-65% on scanned family photos) but lower accuracy for
 * profile/angled faces.
 *
 * **Phase 2 (Future)**: Landmark-based affine alignment. A dedicated alignment model (e.g.,
 * LFPAlight, ~2MB) will detect 5-point facial landmarks and apply an affine transform to
 * normalize face pose. This is expected to improve accuracy to ~70-80% on family photos.
 *
 * ## Integration with Face Detection
 *
 * The current YOLO12n-face model (`face_detection_model.onnx`) outputs only bounding boxes
 * (x1, y1, x2, y2, confidence) — no landmarks. Phase 2 alignment requires upgrading to a
 * model that outputs 5-point landmarks (e.g., YOLOv8-face) or adding a separate landmark model.
 *
 * @see FaceCrop
 * @see DetectedFace
 */
interface FaceAlignmentPort {

    /**
     * Align a detected face from the source image.
     *
     * Takes the source image and a face region (from [FaceDetectionPort]), and returns a
     * [FaceCrop] with alignment metadata. The actual aligned face bitmap is produced
     * internally and used for embedding extraction.
     *
     * @param sourceImage The image containing the face.
     * @param faceRegion Bounding box of the detected face (pixel coordinates).
     * @return An aligned [FaceCrop] with metadata, or null if alignment fails
     *   (e.g., face too small, landmarks not detectable).
     */
    suspend fun alignFace(sourceImage: ProcessedImage, faceRegion: DetectedFace): FaceCrop?

    /**
     * Align multiple faces from a single source image.
     *
     * More efficient than calling [alignFace] individually when multiple faces are detected
     * in the same image.
     *
     * @param sourceImage The image containing the faces.
     * @param faceRegions Bounding boxes of detected faces (pixel coordinates).
     * @return Aligned face crops for each successfully aligned face. Results are in the
     *   same order as [faceRegions], with null entries for failed alignments.
     */
    suspend fun alignFaces(
        sourceImage: ProcessedImage,
        faceRegions: List<DetectedFace>,
    ): List<FaceCrop?>

    /**
     * Whether face alignment is available (model loaded, landmarks detectable).
     *
     * If alignment is not available, embeddings should still be extracted using
     * a simple center-crop, but with lower expected accuracy.
     */
    fun isAlignmentAvailable(): Boolean

    /**
     * Pre-load the alignment model eagerly.
     *
     * @return true if alignment is available after preloading.
     */
    suspend fun preload(): Boolean
}