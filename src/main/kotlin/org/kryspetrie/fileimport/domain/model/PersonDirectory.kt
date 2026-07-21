package org.kryspetrie.fileimport.domain.model

import kotlinx.serialization.Serializable

/**
 * Result of a best-match search in the person directory.
 *
 * Returned by [PersonDirectory.findBestMatchWithScore] to avoid re-computing
 * the match score after finding the person.
 *
 * @property person The matched person.
 * @property score The cosine similarity score between the query embedding and the best
 *           matching embedding in this person's gallery.
 */
data class MatchResult(
    val person: Person,
    val score: Float,
)

/**
 * Directory of known persons for face identification and auto-tagging.
 *
 * Persists across sessions and grows as the user confirms face identifications.
 * Each person has a gallery of embeddings from different angles and conditions,
 * enabling progressive accuracy improvement (gallery enrichment).
 *
 * ## Matching & Scale
 *
 * [findBestMatch] and [findAllMatches] perform a linear scan over all persons × gallery embeddings.
 * For P persons with G embeddings each and D-dimensional vectors, the cost is O(P × G × D).
 * At P=100, G=20, D=128 this is ~256K operations (~1-2ms). For P>500, consider adding an
 * approximate nearest-neighbor index (e.g., HNSW) — see the performance note in [FaceEmbedding].
 *
 * [FaceMatchingConfig.maxDirectorySize] limits the total number of persons to prevent
 * unbounded growth. The import validation enforces this limit.
 *
 * @property persons All known persons in the directory.
 * @property version Schema version for future migrations.
 */
@Serializable
data class PersonDirectory(
    val persons: List<Person> = emptyList(),
    val version: Int = CURRENT_VERSION,
) {
    companion object {
        /** Current schema version. Increment when the serialization format changes. */
        const val CURRENT_VERSION = 1

        /** Maximum total persons allowed in a directory (safety limit for import validation). */
        const val MAX_DIRECTORY_SIZE = 500

        /** Maximum total embeddings across all persons (safety limit for import validation). */
        const val MAX_TOTAL_EMBEDDINGS = 10000

        /**
         * Valid embedding dimensions for face recognition models.
         * Updated when new model architectures are supported.
         */
        val VALID_EMBEDDING_DIMENSIONS = setOf(
            FaceEmbedding.DIM_MOBILEFACENET,    // 128 (classic MobileFaceNet)
            FaceEmbedding.DIM_ARCFACE_MOBILEFACENET,  // 512 (ArcFace MobileFaceNet, Hailo)
            FaceEmbedding.DIM_ARCFACE_R50,      // 512 (ArcFace ResNet-50)
        )
    }

    /** Find a person by ID. */
    fun personById(id: String): Person? = persons.find { it.id == id }

    /** Find a person by name (case-insensitive exact match). */
    fun personByName(name: String): Person? =
        persons.find { it.name.equals(name.trim(), ignoreCase = true) }

    /**
     * Check if a name is available (not already used by another person).
     *
     * Case-insensitive comparison. Returns true if the name is not taken, or is taken only by
     * the person with [excludeId] (useful for rename operations where a person keeps their own name).
     */
    fun isNameAvailable(name: String, excludeId: String? = null): Boolean {
        val trimmed = name.trim()
        return persons.none {
            it.name.equals(trimmed, ignoreCase = true) && it.id != excludeId
        }
    }

    /**
     * Validate this directory for import safety.
     *
     * Returns a list of validation errors. An empty list means the directory is valid.
     * Checks directory size, total embeddings, person name validity, and version compatibility.
     */
    fun validateForImport(): List<String> {
        val errors = mutableListOf<String>()
        if (persons.size > MAX_DIRECTORY_SIZE) {
            errors.add("Directory has ${persons.size} persons, maximum is $MAX_DIRECTORY_SIZE")
        }
        val totalEmbeddings = persons.sumOf { it.gallery.size }
        if (totalEmbeddings > MAX_TOTAL_EMBEDDINGS) {
            errors.add("Directory has $totalEmbeddings total embeddings, maximum is $MAX_TOTAL_EMBEDDINGS")
        }
        if (version > CURRENT_VERSION) {
            errors.add("Directory version $version is newer than supported version $CURRENT_VERSION")
        }
        // Check for duplicate names (case-insensitive) — importing duplicates would break
        // the uniqueness invariant that [isNameAvailable] enforces for creates/renames.
        val nameCounts = persons.groupingBy { it.name.trim().lowercase() }.eachCount()
        nameCounts.filter { it.value > 1 }.forEach { (name, count) ->
            errors.add("Duplicate person name \"$name\" appears $count times — names must be unique (case-insensitive)")
        }
        persons.forEach { person ->
            val nameError = Person.validateName(person.name)
            if (nameError != null) {
                errors.add("Person '${person.id}': $nameError")
            }
            person.gallery.forEach { embedding ->
                if (embedding.vector.isNotEmpty()) {
                    if (embedding.vector.size !in VALID_EMBEDDING_DIMENSIONS
                    ) {
                        errors.add(
                            "Person '${person.name}': embedding ${embedding.id} has invalid " +
                                "dimension ${embedding.vector.size} " +
                                "(expected one of $VALID_EMBEDDING_DIMENSIONS)"
                        )
                    }
                }
            }
        }
        return errors
    }

    /**
     * Find the best-matching person for an embedding.
     *
     * Returns the person with the highest [Person.matchScore] above [threshold],
     * or null if no person matches above the threshold.
     *
     * Performance: O(P × G × D) where P=persons, G=gallery size, D=vector dimensions.
     * See class-level docs for scale limits and HNSW notes.
     */
    fun findBestMatch(
        embedding: FaceEmbedding,
        threshold: Float = FaceEmbedding.MATCH_THRESHOLD,
    ): Person? = findBestMatchWithScore(embedding, threshold)?.person

    /**
     * Find the best-matching person for an embedding, returning both the person and the score.
     *
     * Prefer this over [findBestMatch] to avoid re-computing the match score, which could
     * produce a stale result if the directory changes between the find and the score computation.
     *
     * Persons with no usable embeddings in their gallery are skipped — only embeddings passing
     * [FaceEmbedding.isUsableForMatching] are considered for matching (quality ≥ 0.3, non-empty vector).
     *
     * @return The best match as a [MatchResult], or null if no person matches above threshold.
     */
    fun findBestMatchWithScore(
        embedding: FaceEmbedding,
        threshold: Float = FaceEmbedding.MATCH_THRESHOLD,
    ): MatchResult? {
        return persons
            .filter { it.gallery.any { emb -> emb.isUsableForMatching() } }
            .mapNotNull { person ->
                val score = person.matchScore(embedding)
                if (score >= threshold) MatchResult(person, score) else null
            }
            .maxByOrNull { it.score }
    }

    /**
     * Find all persons matching an embedding, sorted by similarity descending.
     *
     * Returns pairs of (Person, similarityScore) for all matches above [threshold].
     * Persons with no usable embeddings in their gallery are skipped.
     */
    fun findAllMatches(
        embedding: FaceEmbedding,
        threshold: Float = FaceEmbedding.MATCH_THRESHOLD,
    ): List<Pair<Person, Float>> {
        return persons
            .filter { it.gallery.any { emb -> emb.isUsableForMatching() } }
            .mapNotNull { person ->
                val score = person.matchScore(embedding)
                if (score >= threshold) person to score else null
            }
            .sortedByDescending { (_, score) -> score }
    }

    /** Add or update a person. Validates the person name before adding. */
    fun withPerson(person: Person): PersonDirectory {
        val nameError = Person.validateName(person.name)
        require(nameError == null) { "Invalid person name: $nameError" }
        return copy(
            persons = if (personById(person.id) != null) {
                persons.map { if (it.id == person.id) person else it }
            } else {
                persons + person
            }
        )
    }

    /** Remove a person by ID. */
    fun withoutPerson(personId: String): PersonDirectory =
        copy(persons = persons.filter { it.id != personId })

    /**
     * Merge two persons using diversity-aware gallery merge.
     *
     * Combines both galleries using [Person.mergeGallery] (which selects the most diverse
     * embeddings rather than simple truncation), keeps the name of [targetId], and removes
     * the source person from the directory.
     */
    fun mergePersons(targetId: String, sourceId: String): PersonDirectory {
        val target = personById(targetId) ?: return this
        val source = personById(sourceId) ?: return this

        val merged = target.mergeGallery(source)
        return copy(persons = persons.map { if (it.id == targetId) merged else it })
            .withoutPerson(sourceId)
    }

    /** All unique source paths across all persons. */
    val allSourcePaths: List<String> get() = persons.flatMap { it.sourcePaths }.distinct()

    /** Total number of face embeddings across all persons. */
    val totalEmbeddings: Int get() = persons.sumOf { it.gallery.size }

    /** Whether the directory is empty. */
    val isEmpty: Boolean get() = persons.isEmpty()

    /**
     * Trim all person galleries to [maxGallerySize] using diversity-aware eviction.
     *
     * Called when the user decreases [FaceMatchingConfig.maxGallerySize] — existing galleries
     * may exceed the new limit. Returns a new directory with all galleries trimmed.
     */
    fun trimAllGalleriesToSize(maxGallerySize: Int): PersonDirectory {
        val trimmed = persons.map { it.trimGalleryToSize(maxGallerySize) }
        return copy(persons = trimmed)
    }
}