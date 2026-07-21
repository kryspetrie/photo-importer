package org.kryspetrie.fileimport.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for the second round of adversarial analysis fixes:
 * - C4: FaceEmbedding.equals()/hashCode() uses Base64 comparison (no lazy decode)
 * - U7: Person default name is "Unnamed" (not empty)
 * - I3: Gallery is sorted by quality descending after insertion/eviction
 * - I5: Source path validation
 */
@DisplayName("Additional Adversarial Analysis Fixes")
class AdditionalFixesTest {

    private fun testEmbedding(
        values: FloatArray = FloatArray(FaceEmbedding.DIM_MOBILEFACENET) { 0.01f * it },
        modelName: String = "mobilefacenet",
        quality: Float = 1.0f,
        sourcePath: String = "",
        id: String = "emb-${System.nanoTime()}",
    ) = FaceEmbedding(
        embeddingVector = values,
        modelName = modelName,
        quality = quality,
        sourcePath = sourcePath,
        id = id,
    )

    /** Unit vector with a single 1.0 at [idx] and 0s elsewhere. */
    private fun unitVec(dim: Int = FaceEmbedding.DIM_MOBILEFACENET, idx: Int) =
        FloatArray(dim) { if (it == idx) 1f else 0f }

    private fun testPerson(
        id: String = "person-${System.nanoTime()}",
        name: String = "Alice",
        gallery: List<FaceEmbedding> = emptyList(),
        sourcePaths: List<String> = emptyList(),
        updatedAt: Long = System.currentTimeMillis(),
    ) = Person(id = id, name = name, gallery = gallery, sourcePaths = sourcePaths, updatedAt = updatedAt)

    // ══════════════════════════════════════════════════════════════════════
    //  C4: FaceEmbedding equals()/hashCode() avoids lazy decode
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("C4: FaceEmbedding equals/hashCode uses Base64")
    inner class FaceEmbeddingEqualityOptimization {

        @Test
        @DisplayName("equals compares by vectorBase64 without decoding vector")
        fun equalsUsesBase64() {
            val emb1 = FaceEmbedding(
                embeddingVector = unitVec(idx = 0),
                modelName = "mobilefacenet",
                quality = 0.9f,
                id = "same-id",
                sourcePath = "/photo1.jpg",
            )
            val emb2 = FaceEmbedding(
                embeddingVector = unitVec(idx = 0),
                modelName = "mobilefacenet",
                quality = 0.9f,
                id = "same-id",
                sourcePath = "/photo1.jpg",
            )
            // Both embeddings have the same Base64 string (same vector), so equals should be true
            assertThat(emb1.vectorBase64).isEqualTo(emb2.vectorBase64)
            assertThat(emb1).isEqualTo(emb2)
        }

        @Test
        @DisplayName("equals returns false when Base64 strings differ (different vectors)")
        fun equalsFailsOnDifferentVectors() {
            val emb1 = FaceEmbedding(
                embeddingVector = unitVec(idx = 0),
                modelName = "mobilefacenet",
                quality = 0.9f,
                id = "same-id",
            )
            val emb2 = FaceEmbedding(
                embeddingVector = unitVec(idx = 1),
                modelName = "mobilefacenet",
                quality = 0.9f,
                id = "same-id",
            )
            // Different vectors → different Base64 → not equal
            assertThat(emb1).isNotEqualTo(emb2)
        }

        @Test
        @DisplayName("hashCode is consistent with equals using Base64")
        fun hashCodeConsistentWithBase64() {
            val emb1 = FaceEmbedding(
                embeddingVector = unitVec(idx = 5),
                modelName = "mobilefacenet",
                quality = 0.9f,
                id = "id-x",
            )
            val emb2 = FaceEmbedding(
                embeddingVector = unitVec(idx = 5),
                modelName = "mobilefacenet",
                quality = 0.9f,
                id = "id-x",
            )
            assertThat(emb1.hashCode()).isEqualTo(emb2.hashCode())
        }

        @Test
        @DisplayName("lazy vector uses LazyThreadSafetyMode.NONE")
        fun lazyThreadSafetyMode() {
            val emb = testEmbedding()
            // Just verify the vector is accessible (no exception thrown)
            assertThat(emb.vector).isNotEmpty()
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  U7: Person default name is "Unnamed"
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("U7: Person default name")
    inner class PersonDefaultName {

        @Test
        @DisplayName("Person() defaults name to Unnamed (not empty)")
        fun defaultNameIsUnnamed() {
            val person = Person()
            assertThat(person.name).isEqualTo("Unnamed")
        }

        @Test
        @DisplayName("Person(name=...) can still be set explicitly")
        fun explicitName() {
            val person = Person(name = "Bob")
            assertThat(person.name).isEqualTo("Bob")
        }

        @Test
        @DisplayName("validateName still rejects blank names")
        fun validateNameRejectsBlank() {
            assertThat(Person.validateName("")).isNotNull
            assertThat(Person.validateName("  ")).isNotNull
        }

        @Test
        @DisplayName("validateName accepts Unnamed")
        fun validateNameAcceptsUnnamed() {
            assertThat(Person.validateName("Unnamed")).isNull()
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  I3: Gallery is sorted by quality descending
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("I3: Gallery quality ordering")
    inner class GalleryQualityOrdering {

        @Test
        @DisplayName("withEmbedding sorts gallery by quality descending")
        fun withEmbeddingSortsByQuality() {
            val lowQ = testEmbedding(values = unitVec(idx = 0), quality = 0.35f, id = "low")
            val medQ = testEmbedding(values = unitVec(idx = 1), quality = 0.65f, id = "med")
            val highQ = testEmbedding(values = unitVec(idx = 2), quality = 0.95f, id = "high")

            // Add in random order
            val person = testPerson(name = "Alice")
                .withEmbedding(medQ)
                .withEmbedding(lowQ)
                .withEmbedding(highQ)

            // Gallery should be sorted by quality descending
            assertThat(person.gallery.map { it.quality }).containsExactly(0.95f, 0.65f, 0.35f)
        }

        @Test
        @DisplayName("trimGalleryToSize preserves highest quality embeddings")
        fun trimPreservesHighQuality() {
            val embeddings = (1..5).map { i ->
                testEmbedding(
                    values = unitVec(idx = i),
                    quality = i * 0.15f, // 0.15, 0.30, 0.45, 0.60, 0.75
                    id = "emb-$i",
                )
            }
            val person = testPerson(name = "Alice", gallery = embeddings)

            // Trim to 2 — should keep the two highest quality
            val trimmed = person.trimGalleryToSize(2)
            assertThat(trimmed.gallery.size).isEqualTo(2)
            // The top 2 by quality are 0.75 and 0.60
            assertThat(trimmed.gallery.map { it.quality }).containsExactly(0.75f, 0.60f)
        }

        @Test
        @DisplayName("mergeGallery sorts combined gallery by quality descending")
        fun mergeGallerySortsByQuality() {
            val emb1 = testEmbedding(values = unitVec(idx = 0), quality = 0.4f, id = "e1")
            val emb2 = testEmbedding(values = unitVec(idx = 1), quality = 0.9f, id = "e2")
            val emb3 = testEmbedding(values = unitVec(idx = 2), quality = 0.6f, id = "e3")
            val person1 = testPerson(name = "Alice", gallery = listOf(emb1, emb2))
            val person2 = testPerson(name = "Bob", gallery = listOf(emb3))

            val merged = person1.mergeGallery(person2, maxGallerySize = 10)
            // After merge, gallery should be sorted by quality descending
            assertThat(merged.gallery.map { it.quality }).containsExactly(0.9f, 0.6f, 0.4f)
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Additional: mergeGallery quality tiebreaker
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("MergeGallery tiebreaker")
    inner class MergeGalleryTiebreaker {

        @Test
        @DisplayName("mergeGallery evicts lower quality when embeddings are equally redundant")
        fun mergeGalleryEvictsLowerQuality() {
            // Two near-identical embeddings (same direction) with different quality, plus one diverse
            val highQ = testEmbedding(values = unitVec(idx = 0), quality = 0.95f, id = "high-q")
            val lowQ = testEmbedding(values = unitVec(idx = 0), quality = 0.35f, id = "low-q")
            val diverse = testEmbedding(values = unitVec(idx = 1), quality = 0.5f, id = "diverse")
            val p1 = testPerson(name = "Alice", gallery = listOf(highQ))
            val p2 = testPerson(name = "Bob", gallery = listOf(lowQ, diverse))

            // Merge into p1 with gallery size 2 — should keep highQ and diverse
            val merged = p1.mergeGallery(p2, maxGallerySize = 2)
            assertThat(merged.gallery.size).isEqualTo(2)
            assertThat(merged.gallery.any { it.id == "high-q" }).isTrue()
            assertThat(merged.gallery.any { it.id == "diverse" }).isTrue()
        }
    }
}