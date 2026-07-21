package org.kryspetrie.fileimport.domain.model

import org.kryspetrie.fileimport.domain.port.DetectedFace

/**
 * A face detection result with auto-suggestion information.
 *
 * Produced by [org.kryspetrie.fileimport.application.FaceGroupingService] after running the
 * detect → embed → match pipeline. Each suggestion maps a detected face to its best-matching
 * person in the directory, along with a confidence score.
 *
 * ## Construction
 *
 * Prefer using [create] or [createWithAlternatives] to construct instances — these compute
 * [isConfident] and [isPotential] from [confidence] and the provided thresholds, preventing
 * data inconsistency. Direct construction is available but requires manually ensuring that
 * the boolean flags match the confidence score against the thresholds used at call site.
 *
 * ## Alternative Matches
 *
 * [alternativeMatches] provides the top-N matching persons (excluding the best match) sorted
 * by similarity descending. This is useful for showing "Did you mean…?" suggestions when the
 * best match has medium confidence — siblings and lookalikes may appear as alternatives.
 *
 * @property detectedFace The detected face bounding box.
 * @property embedding The face embedding vector (null if embedding model is unavailable).
 * @property suggestedPerson The best-matching known person (null if no match above threshold).
 * @property confidence Cosine similarity score (0.0–1.0) between the embedding and the suggested person.
 * @property isConfident Whether the confidence is high enough for auto-tagging.
 * @property isPotential Whether the confidence is high enough for suggestion.
 * @property alternativeMatches Additional matches sorted by similarity descending (excluding the best match).
 */
data class FaceSuggestion(
    val detectedFace: DetectedFace,
    val embedding: FaceEmbedding?,
    val suggestedPerson: Person?,
    val confidence: Float,
    val isConfident: Boolean,
    val isPotential: Boolean,
    val alternativeMatches: List<Pair<Person, Float>> = emptyList(),
) {
    companion object {
        /**
         * Create a [FaceSuggestion] with threshold-derived boolean flags.
         *
         * This is the preferred constructor — it computes [isConfident] and [isPotential]
         * from [confidence] against the provided thresholds, preventing data inconsistency
         * between the score and the flags.
         *
         * @param detectedFace The detected face bounding box.
         * @param embedding The face embedding vector (null if unavailable).
         * @param suggestedPerson The best-matching person (null if no match).
         * @param confidence Cosine similarity score (0.0–1.0).
         * @param matchThreshold Threshold for "potential match" suggestion (default 0.65).
         * @param autoTagThreshold Threshold for "confident match" auto-tagging (default 0.75).
         */
        fun create(
            detectedFace: DetectedFace,
            embedding: FaceEmbedding?,
            suggestedPerson: Person?,
            confidence: Float,
            matchThreshold: Float = FaceEmbedding.MATCH_THRESHOLD,
            autoTagThreshold: Float = FaceEmbedding.AUTO_TAG_THRESHOLD,
        ): FaceSuggestion = FaceSuggestion(
            detectedFace = detectedFace,
            embedding = embedding,
            suggestedPerson = suggestedPerson,
            confidence = confidence,
            isConfident = confidence >= autoTagThreshold,
            isPotential = confidence >= matchThreshold,
        )

        /**
         * Create a [FaceSuggestion] with threshold-derived boolean flags and alternative matches.
         *
         * Use this when the caller has already computed multiple matches (e.g., from
         * [PersonDirectory.findAllMatches]) and wants to include alternative suggestions
         * for "Did you mean…?" UX.
         *
         * @param detectedFace The detected face bounding box.
         * @param embedding The face embedding vector (null if unavailable).
         * @param suggestedPerson The best-matching person (null if no match).
         * @param confidence Cosine similarity score (0.0–1.0).
         * @param matchThreshold Threshold for "potential match" suggestion (default 0.65).
         * @param autoTagThreshold Threshold for "confident match" auto-tagging (default 0.75).
         * @param alternativeMatches Additional matches (Person, score) sorted by score descending.
         *   Should exclude the best match to avoid duplication.
         */
        fun createWithAlternatives(
            detectedFace: DetectedFace,
            embedding: FaceEmbedding?,
            suggestedPerson: Person?,
            confidence: Float,
            matchThreshold: Float = FaceEmbedding.MATCH_THRESHOLD,
            autoTagThreshold: Float = FaceEmbedding.AUTO_TAG_THRESHOLD,
            alternativeMatches: List<Pair<Person, Float>> = emptyList(),
        ): FaceSuggestion = FaceSuggestion(
            detectedFace = detectedFace,
            embedding = embedding,
            suggestedPerson = suggestedPerson,
            confidence = confidence,
            isConfident = confidence >= autoTagThreshold,
            isPotential = confidence >= matchThreshold,
            alternativeMatches = alternativeMatches,
        )
    }
}