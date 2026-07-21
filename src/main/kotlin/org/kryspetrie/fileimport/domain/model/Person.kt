package org.kryspetrie.fileimport.domain.model

import kotlinx.serialization.Serializable

/**
 * A known person in the face identification directory.
 *
 * Each person has a name and a gallery of face embeddings accumulated from confirmed
 * identifications. The gallery grows over time as more photos are tagged — different angles,
 * expressions, and lighting conditions improve recognition accuracy (progressive gallery enrichment).
 *
 * ## Matching Thresholds
 *
 * All threshold constants are defined in [FaceEmbedding.Companion] as the single source of truth.
 * This class references [FaceEmbedding.MATCH_THRESHOLD] and [FaceEmbedding.AUTO_TAG_THRESHOLD]
 * so thresholds are never defined in multiple places.
 *
 * @property id Unique person identifier.
 * @property name Display name (e.g., "Grandma", "Dad", "Uncle Bob"). Must be non-blank,
 *           trimmed of leading/trailing whitespace, and unique within the directory.
 * @property gallery Face embeddings from confirmed identifications. Ordered by quality descending.
 * @property thumbnailPath Path to a representative face crop image for the directory view.
 * @property sourcePaths File paths of photos containing this person (for "open folder" feature).
 * @property createdAt Epoch millis when this person was first identified.
 * @property updatedAt Epoch millis when this person was last updated (new embedding, rename, etc.).
 */
@Serializable
data class Person(
    val id: String = DomainDefaults.generateId(),
    /** Person display name. Must be non-blank — validated by [Companion.validateName]. */
    val name: String = "Unnamed",
    val gallery: List<FaceEmbedding> = emptyList(),
    val thumbnailPath: String = "",
    val sourcePaths: List<String> = emptyList(),
    val createdAt: Long = DomainDefaults.currentTimeMillis(),
    val updatedAt: Long = DomainDefaults.currentTimeMillis(),
) {
    companion object {
        /**
         * Maximum gallery size per person. When the gallery exceeds this, diversity-aware
         * eviction removes the most redundant embedding. Configurable via [FaceMatchingConfig.maxGallerySize].
         */
        const val DEFAULT_MAX_GALLERY_SIZE = 20

        /**
         * Validate a person name. Returns null if valid, or an error message if invalid.
         *
         * Rules:
         * - Must be non-blank after trimming
         * - Must not exceed [FaceMatchingConfig.maxPersonNameLength] characters (default 100)
         * - Must not contain control characters
         */
        fun validateName(name: String, maxLength: Int = 100): String? {
            val trimmed = name.trim()
            return when {
                trimmed.isBlank() -> "Name cannot be blank"
                trimmed.length > maxLength -> "Name cannot exceed $maxLength characters"
                trimmed.any { it.isISOControl() } -> "Name cannot contain control characters"
                else -> null
            }
        }
    }

    /**
     * Number of photos this person appears in. Computed from [sourcePaths] to avoid
     * denormalized state divergence — always equals [sourcePaths.size].
     */
    val photoCount: Int get() = sourcePaths.size

    /**
     * Returns the best similarity score between [candidate] and any usable embedding in this person's
     * gallery. Uses max-similarity matching so a profile face matches against the profile embedding
     * and a frontal face matches against the frontal embedding.
     *
     * Embeddings that fail [FaceEmbedding.isUsableForMatching] (low quality or empty vector) are
     ** skipped — low-quality embeddings produce unreliable similarity scores and would poison
     * matching with false positives.
     *
     * Returns 0f if the gallery is empty or contains no usable embeddings.
     */
    fun matchScore(candidate: FaceEmbedding): Float {
        if (gallery.isEmpty()) return 0f
        val usableGallery = gallery.filter { it.isUsableForMatching() }
        if (usableGallery.isEmpty()) return 0f
        return usableGallery.maxOf { it.cosineSimilarity(candidate) }
    }

    /** Whether [candidate] is likely this person (above threshold). Uses configurable [threshold]. */
    fun isLikelyMatch(candidate: FaceEmbedding, threshold: Float = FaceEmbedding.MATCH_THRESHOLD): Boolean =
        matchScore(candidate) >= threshold

    /** Whether [candidate] is confidently this person (above threshold). Uses configurable [threshold]. */
    fun isConfidentMatch(candidate: FaceEmbedding, threshold: Float = FaceEmbedding.AUTO_TAG_THRESHOLD): Boolean =
        matchScore(candidate) >= threshold

    /**
     * Adds an embedding to this person's gallery, evicting the most redundant one if at capacity.
     *
     * Diversity-aware eviction: the embedding with the highest max-similarity to any other gallery
     * member is removed, preserving angle/expression diversity. When multiple embeddings are equally
     * redundant, the one with the lowest quality score is evicted first (tiebreaker).
     *
     * **Deduplication**: if a near-identical embedding (cosine similarity > 0.99) from the same
     * source path already exists, the new embedding is skipped. When [sourcePath] is empty,
     * deduplication still works by comparing cosine similarity alone — this prevents duplicate
     * gallery entries from re-importing the same photo when the source path is unavailable.
     *
     * Embeddings below [FaceEmbedding.MIN_QUALITY_FOR_MATCHING] are still accepted into the gallery
     * (they may be the only reference for a person), but they are skipped during matching via
     * [matchScore] which filters by [FaceEmbedding.isUsableForMatching].
     *
     * @param maxGallerySize Maximum gallery capacity (default 20). Configurable via FaceMatchingConfig.
     */
    fun withEmbedding(embedding: FaceEmbedding, maxGallerySize: Int = DEFAULT_MAX_GALLERY_SIZE): Person {
        // Deduplication: skip if a near-identical embedding already exists.
        // If sourcePath matches, dedup by source+similarity (fast path).
        // If sourcePath is empty or no source match, dedup by cosine similarity alone.
        val duplicate = gallery.find { existing ->
            if (embedding.sourcePath.isNotEmpty() && existing.sourcePath == embedding.sourcePath) {
                // Same source file — dedup by source path + similarity
                existing.cosineSimilarity(embedding) > 0.99f
            } else {
                // Different source or empty sourcePath — dedup by similarity alone
                existing.cosineSimilarity(embedding) > 0.99f
            }
        }
        if (duplicate != null) return this

        val newGallery = if (gallery.size >= maxGallerySize) {
            // Find the most redundant embedding (highest max-similarity to another gallery member).
            // Tiebreaker: eject the one with the lowest quality score among equally-redundant entries.
            val mostRedundantIdx = gallery.indices.maxByOrNull { idx ->
                val redundancy = gallery.filterIndexed { i, _ -> i != idx }
                    .maxOfOrNull { gallery[idx].cosineSimilarity(it) } ?: 0f
                // Combine redundancy and quality: higher redundancy = more ejectable,
                // and among same redundancy, lower quality = more ejectable.
                // Scale quality contribution to be smaller than redundancy gaps.
                redundancy - gallery[idx].quality * 0.001f
            } ?: -1
            if (mostRedundantIdx >= 0) gallery.filterIndexed { i, _ -> i != mostRedundantIdx } + embedding
            else gallery + embedding
        } else {
            gallery + embedding
        }
        // Sort gallery by quality descending to maintain the documented invariant.
        // After eviction, the highest-quality embeddings should appear first.
        val sortedGallery = newGallery.sortedByDescending { it.quality }
        return copy(
            gallery = sortedGallery,
            updatedAt = DomainDefaults.currentTimeMillis(),
        )
    }

    /**
     * Merge this person's gallery with another person's gallery, using diversity-aware selection
     * instead of simple truncation.
     *
     * Combines both galleries and selects the [maxGallerySize] most diverse embeddings by
     * iteratively removing the most redundant one (highest max-similarity to any other member).
     */
    fun mergeGallery(other: Person, maxGallerySize: Int = DEFAULT_MAX_GALLERY_SIZE): Person {
        // Dedup by source path: keep the highest-quality embedding per source path
        val sourceGroups = (gallery + other.gallery).groupBy { it.sourcePath }
        val deduped = sourceGroups.flatMap { (sourcePath, embeddings) ->
            if (sourcePath.isNotEmpty() && embeddings.size > 1) {
                listOf(embeddings.maxBy { it.quality })
            } else {
                embeddings
            }
        }
        // Diversity-aware gallery selection: iteratively remove the most redundant embedding.
        // Tiebreaker: among equally-redundant embeddings, evict the one with lowest quality.
        var combined = deduped.toMutableList()
        while (combined.size > maxGallerySize && combined.size > 1) {
            val mostRedundantIdx = combined.indices.maxByOrNull { idx ->
                val redundancy = combined.filterIndexed { i, _ -> i != idx }
                    .maxOfOrNull { combined[idx].cosineSimilarity(it) } ?: 0f
                redundancy - combined[idx].quality * 0.001f
            } ?: break
            combined.removeAt(mostRedundantIdx)
        }
        // Sort gallery by quality descending to maintain the documented invariant.
        combined.sortByDescending { it.quality }
        return copy(
            gallery = combined.toList(),
            sourcePaths = (sourcePaths + other.sourcePaths).distinct(),
            updatedAt = DomainDefaults.currentTimeMillis(),
        )
    }

    /** Adds a source photo path. Duplicates are silently ignored. */
    fun withSourcePath(path: String): Person {
        if (path in sourcePaths) return this
        return copy(
            sourcePaths = sourcePaths + path,
            updatedAt = DomainDefaults.currentTimeMillis(),
        )
    }

    /** Removes a source photo path. */
    fun withoutSourcePath(path: String): Person {
        return copy(
            sourcePaths = sourcePaths.filter { it != path },
            updatedAt = DomainDefaults.currentTimeMillis(),
        )
    }

    /**
     * Trim the gallery to [maxGallerySize] using diversity-aware eviction.
     *
     * Called when the user decreases [FaceMatchingConfig.maxGallerySize] — existing galleries
     * may exceed the new limit. Evicts the most redundant embeddings first (highest max-similarity
     * to any other gallery member), using quality as a tiebreaker (lower quality evicted first).
     *
     * @return This person with gallery trimmed to size, or this person if already within limit.
     */
    fun trimGalleryToSize(maxGallerySize: Int): Person {
        var currentGallery = gallery
        while (currentGallery.size > maxGallerySize && currentGallery.size > 1) {
            val mostRedundantIdx = currentGallery.indices.maxByOrNull { idx ->
                val redundancy = currentGallery.filterIndexed { i, _ -> i != idx }
                    .maxOfOrNull { currentGallery[idx].cosineSimilarity(it) } ?: 0f
                // Combine redundancy and quality: higher redundancy = more ejectable,
                // and among same redundancy, lower quality = more ejectable.
                redundancy - currentGallery[idx].quality * 0.001f
            } ?: break
            currentGallery = currentGallery.filterIndexed { i, _ -> i != mostRedundantIdx }
        }
        return if (currentGallery == gallery) this
        else copy(
            gallery = currentGallery.sortedByDescending { it.quality },
            updatedAt = DomainDefaults.currentTimeMillis(),
        )
    }

    /**
     * Remove a specific embedding from the gallery by its ID.
     *
     * Used for single-embedding undo/removal without clearing the entire gallery.
     *
     * @return This person with the embedding removed, or this person if not found.
     */
    fun withoutEmbedding(embeddingId: String): Person {
        val idx = gallery.indexOfFirst { it.id == embeddingId }
        if (idx < 0) return this
        return copy(
            gallery = gallery.filterIndexed { i, _ -> i != idx },
            updatedAt = DomainDefaults.currentTimeMillis(),
        )
    }
}