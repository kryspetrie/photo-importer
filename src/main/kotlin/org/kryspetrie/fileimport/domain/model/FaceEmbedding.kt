package org.kryspetrie.fileimport.domain.model

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * A numeric face embedding vector extracted from a detected face region.
 *
 * Embeddings are fixed-length vectors (128 or 512 dimensions depending on the model) that
 * represent a face's identity in a way that enables similarity comparison via cosine distance.
 * Two photos of the same person should produce embeddings with high cosine similarity (>0.65),
 * while different people should produce low similarity (<0.4).
 *
 * ## Matching Thresholds
 *
 * All matching thresholds are defined here as the single source of truth:
 * - [MATCH_THRESHOLD] (0.65): cosine similarity above this suggests "likely same person"
 * - [AUTO_TAG_THRESHOLD] (0.75): high-confidence threshold for auto-tagging without human review
 *
 * These thresholds are intentionally conservative for scanned photos — lower-quality captures
 * with angle and lighting variation require more forgiving thresholds. See [FaceMatchingConfig]
 * in AppSettings for configurable overrides.
 *
 * ## Vector Storage
 *
 * Embedding vectors are stored as Base64-encoded byte arrays in JSON ([vectorBase64]) for
 * compact serialization (4x smaller than float-list JSON). The [vector] property decodes
 * on access; use [vectorBase64] for serialization and [vector] for computation.
 *
 * ## Scale Limits
 *
 * The linear-scan matching in [PersonDirectory.findBestMatch] is O(P × G × D) where
 * P = number of persons, G = gallery size (max 20), D = vector dimensions (128 or 512).
 * For P=100, G=20, D=128, this is ~256K multiply-adds per query (~1-2ms on modern hardware).
 * For P=500+, consider adding an approximate nearest-neighbor index (e.g., HNSW) as
 * noted in the performance comment in PersonDirectory.findBestMatch.
 *
 * @property id Unique identifier for this embedding instance.
 * @property vectorBase64 Base64-encoded embedding vector (compact storage). Decode via [vector].
 * @property quality Confidence/quality score of the face detection that produced this embedding.
 *           Higher values indicate a clearer, more frontal face. Embeddings below
 *           [MIN_QUALITY_FOR_MATCHING] should not be used for identification.
 * @property estimatedYaw Estimated head yaw angle in degrees (0 = frontal, positive = looking right).
 *           Used for gallery diversity tracking — we prefer keeping embeddings from different angles.
 * @property modelName Name of the model that produced this embedding (e.g., "mobilefacenet", "arcface-r50").
 *           Cross-model comparisons are invalid; embeddings from different models must not be compared.
 * @property sourcePath File path of the source image this embedding was extracted from.
 * @property sourceRegion Normalized bounding box in the source image that produced this embedding.
 * @property vector Lazily-decoded embedding vector. Prefer [vectorBase64] for serialization.
 */
@Serializable
data class FaceEmbedding(
    val id: String = DomainDefaults.generateId(),
    val vectorBase64: String = "",
    val quality: Float = 1.0f,
    val estimatedYaw: Float = 0f,
    val modelName: String = "",
    val sourcePath: String = "",
    val sourceRegion: NormalizedRect = NormalizedRect(),
) {
    companion object {
        /** Minimum detection quality score to produce a usable embedding. */
        const val MIN_QUALITY_FOR_MATCHING = 0.3f

        /** Minimum face size in pixels to attempt embedding extraction. */
        const val MIN_FACE_SIZE_PX = 50

        /** Dimensionality constant for MobileFaceNet (128-dim). */
        const val DIM_MOBILEFACENET = 128

        /** Dimensionality constant for ArcFace MobileFaceNet (512-dim, from Hailo Model Zoo). */
        const val DIM_ARCFACE_MOBILEFACENET = 512

        /** Dimensionality constant for ArcFace-R50 (512-dim). */
        const val DIM_ARCFACE_R50 = 512

        /**
         * Cosine similarity threshold for "likely same person" (used for suggestions).
         *
         * This is the ONLY threshold constant — Person.matchScore(), PersonDirectory.findBestMatch(),
         * and all matching code reference this value. Do not define duplicate thresholds elsewhere.
         *
         * The default (0.65) is conservative for scanned photos with angle/lighting variation.
         * For high-quality digital photos, 0.70 may be appropriate (configurable via FaceMatchingConfig).
         */
        const val MATCH_THRESHOLD = 0.65f

        /**
         * Higher threshold for confident auto-tagging (no human review needed).
         *
         * Matches above this threshold are confident enough to auto-fill person names.
         */
        const val AUTO_TAG_THRESHOLD = 0.75f

        /**
         * Encode a float array as a Base64 string for compact JSON storage.
         *
         * Each float is stored as 4 bytes (little-endian), producing 4*D bytes total.
         * Base64 encodes at 4:3 ratio, so a 128-dim vector = 512 bytes → ~684 Base64 chars,
         * vs JSON float-list encoding at ~12 chars/float = 1536 chars. Savings: ~2x.
         */
        @OptIn(ExperimentalEncodingApi::class)
        fun encodeVector(vector: FloatArray): String {
            val bytes = ByteArray(vector.size * 4)
            for (i in vector.indices) {
                val bits = java.lang.Float.floatToRawIntBits(vector[i])
                bytes[i * 4] = (bits and 0xFF).toByte()
                bytes[i * 4 + 1] = (bits shr 8 and 0xFF).toByte()
                bytes[i * 4 + 2] = (bits shr 16 and 0xFF).toByte()
                bytes[i * 4 + 3] = (bits shr 24 and 0xFF).toByte()
            }
            return Base64.encode(bytes)
        }

        /**
         * Decode a Base64-encoded vector back to a float array.
         *
         * Returns an empty array if [base64] is blank or has an invalid format.
         */
        @OptIn(ExperimentalEncodingApi::class)
        fun decodeVector(base64: String): FloatArray {
            if (base64.isBlank()) return FloatArray(0)
            return try {
                val bytes = Base64.decode(base64)
                if (bytes.size % 4 != 0) return FloatArray(0)
                val result = FloatArray(bytes.size / 4)
                for (i in result.indices) {
                    val bits = (bytes[i * 4].toInt() and 0xFF) or
                        (bytes[i * 4 + 1].toInt() and 0xFF shl 8) or
                        (bytes[i * 4 + 2].toInt() and 0xFF shl 16) or
                        (bytes[i * 4 + 3].toInt() and 0xFF shl 24)
                    result[i] = java.lang.Float.intBitsToFloat(bits)
                }
                result
            } catch (_: Exception) {
                FloatArray(0)
            }
        }
    }

    /**
     * Decoded embedding vector, computed on first access and cached.
     *
     * Uses [LazyThreadSafetyMode.NONE] because [FaceEmbedding] is a data class with only `val`
     * properties — effectively immutable after construction. Since `copy()` creates a new instance
     * with a fresh lazy delegate, there is no risk of concurrent decoding on the same instance.
     * This avoids the synchronization overhead of [LazyThreadSafetyMode.SYNCHRONIZED].
     *
     * Note: If `vectorBase64` is changed via `copy()`, the new instance correctly decodes the
     * updated Base64 string on first access because the lazy delegate is not carried over.
     */
    val vector: FloatArray by lazy(LazyThreadSafetyMode.NONE) {
        decodeVector(vectorBase64)
    }

    /**
     * Convenience constructor that accepts a float array and encodes it to Base64.
     *
     * Use this when creating embeddings from face extraction code:
     * ```
     * FaceEmbedding(embeddingVector = floats, sourcePath = "/path/to/img.jpg", ...)
     * ```
     */
    constructor(
        embeddingVector: FloatArray,
        quality: Float = 1.0f,
        estimatedYaw: Float = 0f,
        modelName: String = "",
        sourcePath: String = "",
        sourceRegion: NormalizedRect = NormalizedRect(),
        id: String = DomainDefaults.generateId(),
    ) : this(
        id = id,
        vectorBase64 = encodeVector(embeddingVector),
        quality = quality,
        estimatedYaw = estimatedYaw,
        modelName = modelName,
        sourcePath = sourcePath,
        sourceRegion = sourceRegion,
    )

    /**
     * Cosine similarity between this embedding and [other].
     *
     * Returns a value between -1.0 and 1.0:
     * - 1.0 = identical vectors
     * - 0.0 = orthogonal (unrelated)
     * - -1.0 = opposite
     *
     * In practice, face embeddings are always positive, so realistic range is ~0.0 to ~1.0.
     * Returns 0f if either vector is empty or zero-length.
     *
     * Cross-model comparison is invalid — embeddings from different models produce
     * meaningless similarity scores. When both [modelName] values are non-empty and
     * different, this method returns 0f.
     *
     * Performance: O(D) where D is the vector dimension. For 100 persons with 20 embeddings
     * each, a full directory scan is O(P × G × D) = O(100 × 20 × 128) = ~256K operations
     * (~1-2ms). For 500+ persons, consider replacing the linear scan with HNSW.
     */
    fun cosineSimilarity(other: FaceEmbedding): Float {
        val a = vector
        val b = other.vector
        if (a.isEmpty() || b.isEmpty()) return 0f
        if (a.size != b.size) return 0f // Dimension mismatch — comparison is meaningless
        if (modelName.isNotEmpty() && other.modelName.isNotEmpty() && modelName != other.modelName) {
            return 0f // Cross-model comparison is invalid
        }
        val len = a.size
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f
        for (i in 0 until len) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denominator = sqrt(normA) * sqrt(normB)
        if (denominator < 1e-6f) return 0f
        return dotProduct / denominator
    }

    /** Whether this embedding has sufficient quality for identification matching. */
    fun isUsableForMatching(): Boolean = quality >= MIN_QUALITY_FOR_MATCHING && vector.isNotEmpty()

    /**
     * Total bytes of the embedding vector in memory.
     * Useful for monitoring memory usage at scale (128-dim = 512 bytes, 512-dim = 2048 bytes).
     */
    val vectorSizeBytes: Int get() = vector.size * 4

    /**
     * Structural equality based on identity fields and vector content.
     *
     * Required because [FloatArray] uses reference equality by default — two embeddings
     * with identical vector values would not be equal without this override.
     *
     * **Performance**: Uses [vectorBase64] for comparison instead of decoding [vector].
     * Since [vectorBase64] is the canonical encoding and always populated when the embedding
     * is created via the float-array constructor, comparing Base64 strings avoids the O(D)
     * lazy decode cost on every equality check. Falls back to [vector.contentEquals] only
     * when both [vectorBase64] strings are empty (defensively, for manually-constructed instances).
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FaceEmbedding) return false
        // Compare Base64 representation to avoid O(D) lazy decode on every equals() call.
        // Both sides use the same Base64 encoding, so string equality ≡ vector equality.
        val vectorsEqual = if (vectorBase64.isNotEmpty() || other.vectorBase64.isNotEmpty()) {
            vectorBase64 == other.vectorBase64
        } else {
            // Defensive fallback: both Base64 strings are empty, compare decoded vectors.
            // This handles edge cases where vectors were set directly without Base64 encoding.
            vector.contentEquals(other.vector)
        }
        return id == other.id &&
            quality == other.quality &&
            estimatedYaw == other.estimatedYaw &&
            modelName == other.modelName &&
            sourcePath == other.sourcePath &&
            sourceRegion == other.sourceRegion &&
            vectorsEqual
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + vectorBase64.hashCode()
        result = 31 * result + quality.hashCode()
        result = 31 * result + estimatedYaw.hashCode()
        result = 31 * result + modelName.hashCode()
        result = 31 * result + sourcePath.hashCode()
        result = 31 * result + sourceRegion.hashCode()
        // Use vectorBase64 hash (already computed) instead of vector.contentHashCode()
        // which would trigger O(D) lazy decode. vectorBase64 is the canonical representation.
        return result
    }
}

/**
 * A normalized bounding box in [0.0, 1.0] coordinate space relative to an image.
 *
 * Used to identify the face region within a source image that produced an embedding.
 * This replaces the previous string-based [sourceRegion] field, providing type-safe
 * access to coordinates for alignment, crop, and display operations.
 *
 * @property x Left edge as fraction of image width (0.0-1.0).
 * @property y Top edge as fraction of image height (0.0-1.0).
 * @property w Width as fraction of image width (0.0-1.0).
 * @property h Height as fraction of image height (0.0-1.0).
 */
@Serializable
data class NormalizedRect(
    val x: Float = 0f,
    val y: Float = 0f,
    val w: Float = 0f,
    val h: Float = 0f,
) {
    /** Whether this rectangle has non-zero area. */
    val isValid: Boolean get() = w > 0f && h > 0f

    /** Center X as fraction of image width. */
    val centerX: Float get() = x + w / 2f

    /** Center Y as fraction of image height. */
    val centerY: Float get() = y + h / 2f

    /** Convert from normalized coordinates to pixel coordinates within an image of [imageWidth] × [imageHeight]. */
    fun toPixels(imageWidth: Int, imageHeight: Int): PixelRect =
        PixelRect(
            x = (x * imageWidth).roundToInt(),
            y = (y * imageHeight).roundToInt(),
            w = (w * imageWidth).roundToInt(),
            h = (h * imageHeight).roundToInt(),
        )

    companion object {
        /** Construct from a [DetectedFace] (pixel coordinates) by normalizing against image dimensions. */
        fun fromDetectedFace(
            face: org.kryspetrie.fileimport.domain.port.DetectedFace,
            imageWidth: Int,
            imageHeight: Int,
        ): NormalizedRect = NormalizedRect(
            x = face.x1 / imageWidth,
            y = face.y1 / imageHeight,
            w = (face.x2 - face.x1) / imageWidth,
            h = (face.y2 - face.y1) / imageHeight,
        )
    }
}

/**
 * A bounding box in pixel coordinate space.
 *
 * Used when converting from normalized coordinates back to pixel space for image cropping.
 */
@Serializable
data class PixelRect(
    val x: Int = 0,
    val y: Int = 0,
    val w: Int = 0,
    val h: Int = 0,
)

/**
 * A face crop extracted from a source image, ready for embedding extraction.
 *
 * This bridges the gap between [DetectedFace] (raw detection output with pixel coordinates)
 * and [FaceEmbedding] (numeric identity vector). The alignment pipeline produces a
 * FaceCrop with a standardized 112×112 aligned face bitmap suitable for MobileFaceNet input.
 *
 * @property sourcePath Original image file path.
 * @property sourceRegion The face region in the source image (normalized coordinates).
 * @property alignedWidth Width of the aligned face crop (112 for MobileFaceNet).
 * @property alignedHeight Height of the aligned face crop (112 for MobileFaceNet).
 * @property yaw Estimated head yaw angle in degrees (0 = frontal).
 * @property detectionConfidence Confidence score from the face detection step.
 */
@Serializable
data class FaceCrop(
    val sourcePath: String = "",
    val sourceRegion: NormalizedRect = NormalizedRect(),
    val alignedWidth: Int = 112,
    val alignedHeight: Int = 112,
    val yaw: Float = 0f,
    val detectionConfidence: Float = 1.0f,
)

/**
 * Configurable thresholds for face matching, allowing per-user tuning.
 *
 * Stored in [AppSettings.faceMatchingConfig] and referenced by all matching code.
 * Defaults match [FaceEmbedding.MATCH_THRESHOLD] and [FaceEmbedding.AUTO_TAG_THRESHOLD].
 *
 * Scanned photos with variable quality, angles, and lighting benefit from lower thresholds,
 * while high-quality digital photos can use higher thresholds for fewer false positives.
 *
 * @property matchThreshold Cosine similarity above this suggests "likely same person". Default 0.65.
 * @property autoTagThreshold High-confidence threshold for auto-tagging without review. Default 0.75.
 * @property maxGallerySize Maximum embeddings per person gallery. Default 20.
 * @property maxDirectorySize Safety limit on total persons in directory. Default 500.
 * @property maxPersonNameLength Maximum length for person names (prevents abuse). Default 100.
 */
@Serializable
data class FaceMatchingConfig(
    val matchThreshold: Float = FaceEmbedding.MATCH_THRESHOLD,
    val autoTagThreshold: Float = FaceEmbedding.AUTO_TAG_THRESHOLD,
    val maxGallerySize: Int = 20,
    val maxDirectorySize: Int = 500,
    val maxPersonNameLength: Int = 100,
) {
    /**
     * Validates this configuration and returns a list of error messages.
     * Empty list means valid configuration.
     */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (matchThreshold < 0f || matchThreshold > 1f) {
            errors.add("Match threshold must be between 0 and 1 (got $matchThreshold)")
        }
        if (autoTagThreshold < 0f || autoTagThreshold > 1f) {
            errors.add("Auto-tag threshold must be between 0 and 1 (got $autoTagThreshold)")
        }
        if (autoTagThreshold < matchThreshold) {
            errors.add("Auto-tag threshold ($autoTagThreshold) should be >= match threshold ($matchThreshold)")
        }
        if (maxGallerySize < 1) {
            errors.add("Max gallery size must be at least 1 (got $maxGallerySize)")
        }
        if (maxDirectorySize < 1) {
            errors.add("Max directory size must be at least 1 (got $maxDirectorySize)")
        }
        if (maxPersonNameLength < 1) {
            errors.add("Max person name length must be at least 1 (got $maxPersonNameLength)")
        }
        return errors
    }
}