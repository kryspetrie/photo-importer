package org.kryspetrie.fileimport.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for the adversarial analysis fixes:
 * - C1: isUsableForMatching() filtering in matchScore()
 * - C2: trimGalleryToSize() for gallery overflow
 * - C3: Deduplication when sourcePath is empty
 * - C5: Quality tiebreaker in diversity eviction
 * - I3: Gallery quality ordering
 * - I7: removeEmbedding (withoutEmbedding) for single-embedding undo
 */
@DisplayName("Adversarial Analysis Fixes")
class AdversarialAnalysisFixesTest {

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
    //  C1: isUsableForMatching() filtering in matchScore()
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("C1: Low-quality embeddings skipped in matchScore")
    inner class LowQualityMatchingFilter {

        @Test
        @DisplayName("matchScore skips embeddings below MIN_QUALITY_FOR_MATCHING")
        fun skipsLowQualityEmbeddings() {
            val lowQuality = testEmbedding(
                values = unitVec(idx = 0),
                quality = 0.1f, // Below MIN_QUALITY_FOR_MATCHING (0.3)
                id = "low-q",
            )
            val person = testPerson(name = "Alice", gallery = listOf(lowQuality))
            val probe = testEmbedding(values = unitVec(idx = 0))

            // Should return 0f because the only gallery embedding is low-quality
            assertThat(person.matchScore(probe)).isEqualTo(0f)
        }

        @Test
        @DisplayName("matchScore considers only usable embeddings, ignoring low-quality ones")
        fun considersOnlyUsableEmbeddings() {
            val lowQuality = testEmbedding(
                values = unitVec(idx = 0),
                quality = 0.1f,
                id = "low-q",
            )
            val highQuality = testEmbedding(
                values = unitVec(idx = 1),
                quality = 0.95f,
                id = "high-q",
            )
            val person = testPerson(name = "Alice", gallery = listOf(lowQuality, highQuality))
            val probe = testEmbedding(values = unitVec(idx = 1)) // matches highQuality

            // Should match only the high-quality embedding
            val score = person.matchScore(probe)
            assertThat(score).isCloseTo(1.0f, within(0.001f))
        }

        @Test
        @DisplayName("matchScore returns 0 when all embeddings are low-quality")
        fun returnsZeroWhenAllLowQuality() {
            val low1 = testEmbedding(values = unitVec(idx = 0), quality = 0.2f, id = "l1")
            val low2 = testEmbedding(values = unitVec(idx = 1), quality = 0.1f, id = "l2")
            val person = testPerson(name = "Alice", gallery = listOf(low1, low2))
            val probe = testEmbedding(values = unitVec(idx = 0))

            assertThat(person.matchScore(probe)).isEqualTo(0f)
        }

        @Test
        @DisplayName("findBestMatch skips person with only low-quality embeddings")
        fun findBestMatchSkipsLowQualityOnlyPerson() {
            val lowQuality = testEmbedding(values = unitVec(idx = 0), quality = 0.2f)
            val person = testPerson(id = "p1", name = "LowQuality", gallery = listOf(lowQuality))
            val dir = PersonDirectory(persons = listOf(person))
            val probe = testEmbedding(values = unitVec(idx = 0))

            // Person exists with matching vector but unusable quality → no match
            assertThat(dir.findBestMatch(probe)).isNull()
        }

        @Test
        @DisplayName("findBestMatch still finds person with mix of usable and unusable embeddings")
        fun findBestMatchWithMix() {
            val lowQuality = testEmbedding(values = unitVec(idx = 5), quality = 0.1f, id = "lq")
            val highQuality = testEmbedding(values = unitVec(idx = 0), quality = 0.9f, id = "hq")
            val person = testPerson(id = "p1", name = "Alice", gallery = listOf(lowQuality, highQuality))
            val dir = PersonDirectory(persons = listOf(person))
            val probe = testEmbedding(values = unitVec(idx = 0))

            val result = dir.findBestMatch(probe)
            assertThat(result).isEqualTo(person)
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  C2: trimGalleryToSize() for gallery overflow on config change
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("C2: trimGalleryToSize")
    inner class TrimGalleryToSize {

        @Test
        @DisplayName("trimGalleryToSize reduces gallery to specified size")
        fun reducesToSpecifiedSize() {
            val gallery = (1..10).map { i ->
                testEmbedding(values = unitVec(idx = i), id = "emb-$i")
            }
            val person = testPerson(name = "Alice", gallery = gallery)

            val trimmed = person.trimGalleryToSize(5)
            assertThat(trimmed.gallery.size).isEqualTo(5)
        }

        @Test
        @DisplayName("trimGalleryToSize returns same person if already within limit")
        fun noTrimNeeded() {
            val gallery = (1..5).map { i ->
                testEmbedding(values = unitVec(idx = i), id = "emb-$i")
            }
            val person = testPerson(name = "Alice", gallery = gallery)

            val trimmed = person.trimGalleryToSize(10)
            assertThat(trimmed.gallery.size).isEqualTo(5)
            assertThat(trimmed).isEqualTo(person)
        }

        @Test
        @DisplayName("trimGalleryToSize keeps diverse embeddings, removes redundant")
        fun keepsDiverseRemovesRedundant() {
            // Two identical embeddings (redundant) + one orthogonal (diverse)
            val sameDir = unitVec(idx = 0)
            val e1 = testEmbedding(values = sameDir, id = "emb-1")
            val e2 = testEmbedding(values = sameDir.copyOf(), id = "emb-2") // identical to e1
            val e3 = testEmbedding(values = unitVec(idx = 1), id = "emb-3") // orthogonal
            val person = testPerson(name = "Alice", gallery = listOf(e1, e2, e3))

            val trimmed = person.trimGalleryToSize(2)
            assertThat(trimmed.gallery.size).isEqualTo(2)
            // e3 (the diverse one) must be kept
            assertThat(trimmed.gallery.any { it.id == "emb-3" }).isTrue()
        }

        @Test
        @DisplayName("trimGalleryToSize prefers keeping higher quality when redundancy is equal")
        fun prefersHigherQuality() {
            // Two embeddings with same direction but different quality
            val highQ = testEmbedding(values = unitVec(idx = 0), quality = 0.95f, id = "high-q")
            val lowQ = testEmbedding(values = unitVec(idx = 0), quality = 0.35f, id = "low-q")
            val diverse = testEmbedding(values = unitVec(idx = 1), quality = 0.9f, id = "diverse")
            val person = testPerson(name = "Alice", gallery = listOf(highQ, lowQ, diverse))

            val trimmed = person.trimGalleryToSize(2)
            // The low-quality redundant one should be evicted
            assertThat(trimmed.gallery.any { it.id == "high-q" }).isTrue()
            assertThat(trimmed.gallery.any { it.id == "diverse" }).isTrue()
        }

        @Test
        @DisplayName("trimGalleryToSize updates updatedAt timestamp when trimming occurs")
        fun updatesTimestamp() {
            val e1 = testEmbedding(values = unitVec(idx = 0), id = "emb-1")
            val e2 = testEmbedding(values = unitVec(idx = 1), id = "emb-2")
            val person = testPerson(name = "Alice", gallery = listOf(e1, e2), updatedAt = 1000L)
            val trimmed = person.trimGalleryToSize(1)
            assertThat(trimmed.updatedAt).isGreaterThan(1000L)
            assertThat(trimmed.gallery.size).isEqualTo(1) // actually trimmed
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  C3: Deduplication with empty sourcePath
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("C3: Deduplication with empty sourcePath")
    inner class DeduplicationEmptySourcePath {

        @Test
        @DisplayName("withEmbedding deduplicates by cosine similarity when sourcePath is empty")
        fun dedupByCosineWhenSourcePathEmpty() {
            val vec = unitVec(idx = 0)
            val existing = testEmbedding(values = vec, sourcePath = "", id = "existing")
            val person = testPerson(name = "Alice", gallery = listOf(existing))

            // New embedding with same vector, empty sourcePath → should be deduped
            val duplicate = testEmbedding(values = vec.copyOf(), sourcePath = "", id = "duplicate")
            val result = person.withEmbedding(duplicate)

            // Gallery size should not increase
            assertThat(result.gallery.size).isEqualTo(1)
        }

        @Test
        @DisplayName("withEmbedding adds when vectors are different even with empty sourcePath")
        fun addsDifferentVectorsWithEmptySourcePath() {
            val existing = testEmbedding(values = unitVec(idx = 0), sourcePath = "", id = "existing")
            val person = testPerson(name = "Alice", gallery = listOf(existing))

            // Different direction → should be added
            val different = testEmbedding(values = unitVec(idx = 1), sourcePath = "", id = "different")
            val result = person.withEmbedding(different)

            assertThat(result.gallery.size).isEqualTo(2)
        }

        @Test
        @DisplayName("withEmbedding deduplicates by source path when sourcePath matches")
        fun dedupBySourcePathWhenAvailable() {
            val existing = testEmbedding(
                values = unitVec(idx = 0),
                sourcePath = "/photos/img.jpg",
                id = "existing",
            )
            val person = testPerson(name = "Alice", gallery = listOf(existing))

            // Same source path + similar vector → should be deduped
            val duplicate = testEmbedding(
                values = unitVec(idx = 0).copyOf(),
                sourcePath = "/photos/img.jpg",
                id = "duplicate",
            )
            val result = person.withEmbedding(duplicate)

            assertThat(result.gallery.size).isEqualTo(1)
        }

        @Test
        @DisplayName("withEmbedding allows same source path with different face regions")
        fun allowsSameSourcePathDifferentFace() {
            val existing = testEmbedding(
                values = unitVec(idx = 0),
                sourcePath = "/photos/img.jpg",
                id = "existing",
            )
            val person = testPerson(name = "Alice", gallery = listOf(existing))

            // Same source path but completely different face (orthogonal) → should be added
            val different = testEmbedding(
                values = unitVec(idx = 1),
                sourcePath = "/photos/img.jpg",
                id = "different",
            )
            val result = person.withEmbedding(different)

            assertThat(result.gallery.size).isEqualTo(2)
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  I7: withoutEmbedding (single-embedding removal)
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("I7: withoutEmbedding (single-embedding undo)")
    inner class WithoutEmbedding {

        @Test
        @DisplayName("removes a specific embedding by ID")
        fun removesSpecificEmbedding() {
            val e1 = testEmbedding(values = unitVec(idx = 0), id = "emb-1")
            val e2 = testEmbedding(values = unitVec(idx = 1), id = "emb-2")
            val e3 = testEmbedding(values = unitVec(idx = 2), id = "emb-3")
            val person = testPerson(name = "Alice", gallery = listOf(e1, e2, e3))

            val result = person.withoutEmbedding("emb-2")
            assertThat(result.gallery).hasSize(2)
            assertThat(result.gallery.map { it.id }).containsExactly("emb-1", "emb-3")
        }

        @Test
        @DisplayName("returns unchanged person if embedding ID not found")
        fun returnsUnchangedIfNotFound() {
            val e1 = testEmbedding(id = "emb-1")
            val person = testPerson(name = "Alice", gallery = listOf(e1))

            val result = person.withoutEmbedding("nonexistent")
            assertThat(result.gallery).hasSize(1)
            assertThat(result.gallery[0].id).isEqualTo("emb-1")
        }

        @Test
        @DisplayName("updates updatedAt timestamp on removal")
        fun updatesTimestamp() {
            val e1 = testEmbedding(id = "emb-1")
            val person = testPerson(name = "Alice", gallery = listOf(e1), updatedAt = 1000L)

            val result = person.withoutEmbedding("emb-1")
            assertThat(result.updatedAt).isGreaterThan(1000L)
            assertThat(result.gallery).isEmpty()
        }

        @Test
        @DisplayName("removing from empty gallery returns unchanged")
        fun removeFromEmptyGallery() {
            val person = testPerson(name = "Alice", gallery = emptyList())
            val result = person.withoutEmbedding("any-id")
            assertThat(result.gallery).isEmpty()
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PersonDirectory.trimAllGalleriesToSize
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("PersonDirectory.trimAllGalleriesToSize")
    inner class TrimAllGalleries {

        @Test
        @DisplayName("trims all persons' galleries to max size")
        fun trimsAllGalleries() {
            val gallery1 = (1..10).map { i ->
                testEmbedding(values = unitVec(idx = i), id = "e1-$i")
            }
            val gallery2 = (1..8).map { i ->
                testEmbedding(values = unitVec(idx = i), id = "e2-$i")
            }
            val p1 = testPerson(id = "p1", name = "Alice", gallery = gallery1)
            val p2 = testPerson(id = "p2", name = "Bob", gallery = gallery2)
            val dir = PersonDirectory(persons = listOf(p1, p2))

            val trimmed = dir.trimAllGalleriesToSize(5)
            assertThat(trimmed.personById("p1")!!.gallery.size).isEqualTo(5)
            assertThat(trimmed.personById("p2")!!.gallery.size).isEqualTo(5)
        }

        @Test
        @DisplayName("does not change galleries within limit")
        fun noChangeWithinLimit() {
            val gallery = (1..3).map { i ->
                testEmbedding(values = unitVec(idx = i), id = "e-$i")
            }
            val person = testPerson(id = "p1", name = "Alice", gallery = gallery)
            val dir = PersonDirectory(persons = listOf(person))

            val trimmed = dir.trimAllGalleriesToSize(10)
            assertThat(trimmed.personById("p1")!!.gallery.size).isEqualTo(3)
        }

        @Test
        @DisplayName("empty directory is unchanged")
        fun emptyDirectoryUnchanged() {
            val dir = PersonDirectory()
            val trimmed = dir.trimAllGalleriesToSize(5)
            assertThat(trimmed.persons).isEmpty()
        }
    }
}