package org.kryspetrie.fileimport.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Comprehensive tests for [Person] data class and its companion object methods.
 *
 * Covers name validation, gallery matching, embedding management, source path operations,
 * photo count, and default values.
 */
@DisplayName("Person")
class PersonTest {

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun testVector(vararg values: Float): FloatArray = values

    private fun testEmbedding(
        values: FloatArray = FloatArray(128) { 0.01f * it },
        modelName: String = "mobilefacenet",
        quality: Float = 1.0f,
    ) = FaceEmbedding(embeddingVector = values, modelName = modelName, quality = quality)

    /** Unit vector pointing in a specific "direction" for similarity tests. */
    private fun unitVector(dim: Int, index: Int): FloatArray {
        val v = FloatArray(dim)
        if (index in 0 until dim) v[index] = 1.0f
        return v
    }

    /** Normalized vector with given values — scales so L2 norm = 1. */
    private fun normalizedVector(vararg values: Float): FloatArray {
        val norm = kotlin.math.sqrt(values.fold(0f) { acc, v -> acc + v * v })
        return if (norm < 1e-6f) FloatArray(values.size) else FloatArray(values.size) { values[it] / norm }
    }

    // ── validateName ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("validateName")
    inner class ValidateName {

        @Test
        @DisplayName("returns error for blank name (empty string)")
        fun blankName_emptyString() {
            assertThat(Person.validateName("")).isEqualTo("Name cannot be blank")
        }

        @Test
        @DisplayName("returns error for blank name (whitespace only)")
        fun blankName_whitespaceOnly() {
            assertThat(Person.validateName("   ")).isEqualTo("Name cannot be blank")
        }

        @Test
        @DisplayName("returns error for blank name (tabs and newlines)")
        fun blankName_tabsAndNewlines() {
            assertThat(Person.validateName("\t\n ")).isEqualTo("Name cannot be blank")
        }

        @Test
        @DisplayName("returns error for name exceeding default maxLength (100)")
        fun nameTooLong_defaultMax() {
            val longName = "a".repeat(101)
            assertThat(Person.validateName(longName)).isEqualTo("Name cannot exceed 100 characters")
        }

        @Test
        @DisplayName("returns error for name exceeding custom maxLength")
        fun nameTooLong_customMax() {
            val name = "ab" // 2 chars
            assertThat(Person.validateName(name, maxLength = 1)).isEqualTo("Name cannot exceed 1 characters")
        }

        @Test
        @DisplayName("accepts name at exactly the maxLength boundary")
        fun nameAtExactBoundary() {
            val name = "a".repeat(100)
            assertThat(Person.validateName(name)).isNull()
        }

        @Test
        @DisplayName("accepts name at custom maxLength boundary")
        fun nameAtCustomBoundary() {
            val name = "abc" // 3 chars
            assertThat(Person.validateName(name, maxLength = 3)).isNull()
        }

        @Test
        @DisplayName("returns error for name containing control characters")
        fun nameWithControlCharacters() {
            assertThat(Person.validateName("Uncle\u0007Bob")).isEqualTo("Name cannot contain control characters")
        }

        @Test
        @DisplayName("returns error for name containing tab control character")
        fun nameWithTab() {
            // Tab is a control character per Char.isISOControl
            assertThat(Person.validateName("Uncle\tBob")).isEqualTo("Name cannot contain control characters")
        }

        @Test
        @DisplayName("returns error for name containing newline control character")
        fun nameWithNewline() {
            assertThat(Person.validateName("Uncle\nBob")).isEqualTo("Name cannot contain control characters")
        }

        @Test
        @DisplayName("returns null for a valid simple name")
        fun validSimpleName() {
            assertThat(Person.validateName("Grandma")).isNull()
        }

        @Test
        @DisplayName("returns null for whitespace-padded valid name (trimmed)")
        fun validName_whitespacePadded() {
            // validateName trims before checking, so "  Alice  " is valid
            assertThat(Person.validateName("  Alice  ")).isNull()
        }

        @Test
        @DisplayName("returns null for name with unicode characters")
        fun validName_unicode() {
            assertThat(Person.validateName("José")).isNull()
        }

        @Test
        @DisplayName("returns null for name with emoji (not ISO control)")
        fun validName_emoji() {
            assertThat(Person.validateName("Dad 🎉")).isNull()
        }

        @Test
        @DisplayName("counts length after trimming, so padded name at boundary is valid")
        fun trimmedNameAtBoundary() {
            // "  Alice  " trims to "Alice" (5 chars), which is within 100
            assertThat(Person.validateName("  Alice  ")).isNull()
        }
    }

    // ── matchScore ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("matchScore")
    inner class MatchScore {

        @Test
        @DisplayName("returns 0f when gallery is empty")
        fun emptyGallery() {
            val person = Person(name = "Alice", gallery = emptyList())
            val candidate = testEmbedding()
            assertThat(person.matchScore(candidate)).isEqualTo(0f)
        }

        @Test
        @DisplayName("returns cosine similarity for single embedding in gallery")
        fun singleEmbeddingInGallery() {
            // Two identical unit vectors → cosine similarity ≈ 1.0
            val vec = unitVector(128, 5)
            val galleryEmbedding = testEmbedding(values = vec)
            val candidate = testEmbedding(values = vec)
            val person = Person(name = "Alice", gallery = listOf(galleryEmbedding))

            assertThat(person.matchScore(candidate)).isCloseTo(1.0f, org.assertj.core.data.Offset.offset(0.001f))
        }

        @Test
        @DisplayName("returns max similarity when gallery has multiple embeddings")
        fun maxSimilarity_fromMultipleEmbeddings() {
            // Embedding pointing in direction 0 → similar to candidate in direction 0
            val embedding0 = testEmbedding(values = unitVector(128, 0))
            // Embedding pointing in direction 1 → less similar to candidate in direction 0
            val embedding1 = testEmbedding(values = unitVector(128, 1))
            // Candidate in direction 0
            val candidate = testEmbedding(values = unitVector(128, 0))

            val person = Person(name = "Alice", gallery = listOf(embedding1, embedding0))

            // Should pick embedding0 (same direction = high similarity),
            // not embedding1 (orthogonal ≈ 0 similarity)
            val score = person.matchScore(candidate)
            assertThat(score).isCloseTo(1.0f, org.assertj.core.data.Offset.offset(0.001f))
        }

        @Test
        @DisplayName("returns 0f when embedding model names mismatch")
        fun embeddingModelNameMismatch() {
            val vec = unitVector(128, 5)
            val galleryEmbedding = testEmbedding(values = vec, modelName = "mobilefacenet")
            val candidate = testEmbedding(values = vec, modelName = "arcface-r50")

            val person = Person(name = "Alice", gallery = listOf(galleryEmbedding))

            // cosineSimilarity returns 0f for mismatched model names
            assertThat(person.matchScore(candidate)).isEqualTo(0f)
        }

        @Test
        @DisplayName("returns 0f when candidate has empty vector against populated gallery")
        fun emptyCandidateVector() {
            val galleryEmbedding = testEmbedding(values = unitVector(128, 0))
            val candidate = testEmbedding(values = FloatArray(0), modelName = "mobilefacenet")

            val person = Person(name = "Alice", gallery = listOf(galleryEmbedding))
            assertThat(person.matchScore(candidate)).isEqualTo(0f)
        }

        @Test
        @DisplayName("returns correct similarity for orthogonal vectors")
        fun orthogonalVectors() {
            val embedding = testEmbedding(values = unitVector(128, 0))
            val candidate = testEmbedding(values = unitVector(128, 1))

            val person = Person(name = "Alice", gallery = listOf(embedding))

            // Orthogonal unit vectors → dot product = 0 → similarity = 0
            assertThat(person.matchScore(candidate)).isCloseTo(0f, org.assertj.core.data.Offset.offset(0.001f))
        }

        @Test
        @DisplayName("returns correct similarity for partially similar vectors")
        fun partiallySimilarVectors() {
            // Two vectors that share some components: normalized [1,1,0,...] vs [1,0,0,...]
            val vecA = normalizedVector(1f, 1f, *(FloatArray(126) { 0f }))
            val vecB = normalizedVector(1f, 0f, *(FloatArray(126) { 0f }))

            val embedding = testEmbedding(values = vecA)
            val candidate = testEmbedding(values = vecB)

            val person = Person(name = "Alice", gallery = listOf(embedding))
            // cos(45°) ≈ 0.707
            assertThat(person.matchScore(candidate)).isCloseTo(0.707f, org.assertj.core.data.Offset.offset(0.01f))
        }
    }

    // ── isLikelyMatch / isConfidentMatch ─────────────────────────────────────

    @Nested
    @DisplayName("isLikelyMatch / isConfidentMatch")
    inner class ThresholdMatching {

        @Test
        @DisplayName("isLikelyMatch returns true when matchScore >= 0.65 (MATCH_THRESHOLD)")
        fun likelyMatch_aboveThreshold() {
            // Create two embeddings that will have high cosine similarity
            val vec = unitVector(128, 0)
            val embedding = testEmbedding(values = vec)
            val candidate = testEmbedding(values = vec)

            val person = Person(name = "Alice", gallery = listOf(embedding))
            // Same vector → similarity = 1.0 → above MATCH_THRESHOLD (0.65)
            assertThat(person.isLikelyMatch(candidate)).isTrue()
        }

        @Test
        @DisplayName("isLikelyMatch returns false when matchScore < 0.65")
        fun likelyMatch_belowThreshold() {
            // Use orthogonal vectors → similarity ≈ 0.0 → below MATCH_THRESHOLD
            val embedding = testEmbedding(values = unitVector(128, 0))
            val candidate = testEmbedding(values = unitVector(128, 1))

            val person = Person(name = "Alice", gallery = listOf(embedding))
            assertThat(person.isLikelyMatch(candidate)).isFalse()
        }

        @Test
        @DisplayName("isLikelyMatch returns false when gallery is empty")
        fun likelyMatch_emptyGallery() {
            val candidate = testEmbedding()
            val person = Person(name = "Alice", gallery = emptyList())
            // matchScore = 0f → below threshold
            assertThat(person.isLikelyMatch(candidate)).isFalse()
        }

        @Test
        @DisplayName("isConfidentMatch returns true when matchScore >= 0.75 (AUTO_TAG_THRESHOLD)")
        fun confidentMatch_aboveThreshold() {
            val vec = unitVector(128, 0)
            val embedding = testEmbedding(values = vec)
            val candidate = testEmbedding(values = vec)

            val person = Person(name = "Alice", gallery = listOf(embedding))
            // Same vector → similarity = 1.0 → above AUTO_TAG_THRESHOLD (0.75)
            assertThat(person.isConfidentMatch(candidate)).isTrue()
        }

        @Test
        @DisplayName("isConfidentMatch returns false when matchScore is between 0.65 and 0.75")
        fun confidentMatch_betweenThresholds() {
            // Create a scenario where similarity is ~0.70 (between MATCH and AUTO_TAG thresholds)
            val vecA = normalizedVector(1f, 1f, *(FloatArray(126) { 0f }))
            val vecB = normalizedVector(1f, 0f, *(FloatArray(126) { 0f }))

            val embedding = testEmbedding(values = vecA)
            val candidate = testEmbedding(values = vecB)

            val person = Person(name = "Alice", gallery = listOf(embedding))
            // Similarity ≈ 0.707 → likely match (>=0.65), but not confident (<0.75)
            assertThat(person.isLikelyMatch(candidate)).isTrue()
            assertThat(person.isConfidentMatch(candidate)).isFalse()
        }

        @Test
        @DisplayName("isConfidentMatch returns false when matchScore < 0.65")
        fun confidentMatch_belowLikelyThreshold() {
            val embedding = testEmbedding(values = unitVector(128, 0))
            val candidate = testEmbedding(values = unitVector(128, 1))

            val person = Person(name = "Alice", gallery = listOf(embedding))
            // Similarity ≈ 0.0 → below both thresholds
            assertThat(person.isConfidentMatch(candidate)).isFalse()
            assertThat(person.isLikelyMatch(candidate)).isFalse()
        }

        @Test
        @DisplayName("isConfidentMatch returns false when gallery is empty")
        fun confidentMatch_emptyGallery() {
            val candidate = testEmbedding()
            val person = Person(name = "Alice", gallery = emptyList())
            assertThat(person.isConfidentMatch(candidate)).isFalse()
        }
    }

    // ── withEmbedding ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("withEmbedding")
    inner class WithEmbedding {

        @Test
        @DisplayName("adds embedding to empty gallery")
        fun addToEmptyGallery() {
            val person = Person(name = "Alice", gallery = emptyList())
            val embedding = testEmbedding()

            val updated = person.withEmbedding(embedding)
            assertThat(updated.gallery).hasSize(1)
            assertThat(updated.gallery.first()).isSameAs(embedding)
        }

        @Test
        @DisplayName("adds multiple embeddings sequentially")
        fun addMultipleEmbeddings() {
            val person = Person(name = "Alice", gallery = emptyList())
            val e1 = testEmbedding(values = unitVector(128, 0))
            val e2 = testEmbedding(values = unitVector(128, 1))
            val e3 = testEmbedding(values = unitVector(128, 2))

            val updated = person.withEmbedding(e1).withEmbedding(e2).withEmbedding(e3)
            assertThat(updated.gallery).hasSize(3)
        }

        @Test
        @DisplayName("evicts most redundant embedding when exceeding maxGallerySize")
        fun evictsMostRedundant() {
            // Two identical embeddings + one different → evict one of the identical ones
            val sameVec = unitVector(128, 0)
            val e1 = testEmbedding(values = sameVec)
            val e2 = testEmbedding(values = sameVec)
            val e3 = testEmbedding(values = unitVector(128, 1))

            val person = Person(name = "Alice", gallery = listOf(e1, e2, e3))

            // maxGallerySize = 3 currently, adding one more should evict the most redundant
            val newEmbedding = testEmbedding(values = unitVector(128, 2))
            val updated = person.withEmbedding(newEmbedding, maxGallerySize = 3)
            assertThat(updated.gallery).hasSize(3)
            assertThat(updated.gallery).contains(newEmbedding)
        }

        @Test
        @DisplayName("gallery of size 1 after withEmbedding on empty gallery")
        fun emptyGallery_plusOne() {
            val person = Person(name = "Alice")
            val embedding = testEmbedding()
            val updated = person.withEmbedding(embedding)

            assertThat(updated.gallery).hasSize(1)
        }

        @Test
        @DisplayName("does not evict when gallery is below maxGallerySize")
        fun noEvictionBelowMax() {
            val e1 = testEmbedding(values = unitVector(128, 0))
            val e2 = testEmbedding(values = unitVector(128, 1))
            val person = Person(name = "Alice", gallery = listOf(e1, e2))

            val newEmbedding = testEmbedding(values = unitVector(128, 2))
            val updated = person.withEmbedding(newEmbedding, maxGallerySize = 5)

            assertThat(updated.gallery).hasSize(3)
            assertThat(updated.gallery).containsExactly(e1, e2, newEmbedding)
        }

        @Test
        @DisplayName("evicts most redundant when all embeddings are identical")
        fun evictsWhenAllIdentical() {
            val sameVec = unitVector(128, 0)
            val e1 = testEmbedding(values = sameVec)
            val e2 = testEmbedding(values = sameVec)
            val person = Person(name = "Alice", gallery = listOf(e1, e2))

            val newEmbedding = testEmbedding(values = unitVector(128, 1))
            val updated = person.withEmbedding(newEmbedding, maxGallerySize = 2)

            // Gallery should have 2 items: one of e1/e2 + the newEmbedding
            assertThat(updated.gallery).hasSize(2)
            assertThat(updated.gallery).contains(newEmbedding)
        }

        @Test
        @DisplayName("updates updatedAt timestamp")
        fun updatesTimestamp() {
            val person = Person(name = "Alice", updatedAt = 1000L)
            val embedding = testEmbedding()

            // The updated timestamp should be different from the original
            val updated = person.withEmbedding(embedding)
            assertThat(updated.updatedAt).isGreaterThan(1000L)
        }

        @Test
        @DisplayName("preserves other properties when adding embedding")
        fun preservesOtherProperties() {
            val person = Person(
                id = "person-123",
                name = "Alice",
                thumbnailPath = "/thumb.jpg",
                sourcePaths = listOf("/photo1.jpg"),
                createdAt = 1000L,
            )
            val embedding = testEmbedding()
            val updated = person.withEmbedding(embedding)

            assertThat(updated.id).isEqualTo("person-123")
            assertThat(updated.name).isEqualTo("Alice")
            assertThat(updated.thumbnailPath).isEqualTo("/thumb.jpg")
            assertThat(updated.sourcePaths).containsExactly("/photo1.jpg")
            assertThat(updated.createdAt).isEqualTo(1000L)
        }
    }

    // ── mergeGallery ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("mergeGallery")
    inner class MergeGallery {

        @Test
        @DisplayName("combines galleries from two persons")
        fun combinesGalleries() {
            val e1 = testEmbedding(values = unitVector(128, 0))
            val e2 = testEmbedding(values = unitVector(128, 1))
            val personA = Person(name = "Alice", gallery = listOf(e1))
            val personB = Person(name = "Bob", gallery = listOf(e2))

            val merged = personA.mergeGallery(personB)
            assertThat(merged.gallery).hasSize(2)
            assertThat(merged.gallery).containsExactly(e1, e2)
        }

        @Test
        @DisplayName("combines and deduplicates sourcePaths")
        fun deduplicatesSourcePaths() {
            val personA = Person(name = "Alice", sourcePaths = listOf("/a.jpg", "/b.jpg"))
            val personB = Person(name = "Bob", sourcePaths = listOf("/b.jpg", "/c.jpg"))

            val merged = personA.mergeGallery(personB)
            assertThat(merged.sourcePaths).containsExactly("/a.jpg", "/b.jpg", "/c.jpg")
        }

        @Test
        @DisplayName("applies diversity-aware eviction when exceeding maxGallerySize")
        fun diversityEviction() {
            // Gallery A: all pointing same direction → mostly redundant
            val sameVec = unitVector(128, 0)
            val e1 = testEmbedding(values = sameVec)
            val e2 = testEmbedding(values = sameVec)

            // Gallery B: different direction → diverse
            val e3 = testEmbedding(values = unitVector(128, 1))

            val personA = Person(name = "Alice", gallery = listOf(e1, e2))
            val personB = Person(name = "Bob", gallery = listOf(e3))

            // With maxGallerySize = 2, should evict the most redundant
            val merged = personA.mergeGallery(personB, maxGallerySize = 2)
            assertThat(merged.gallery).hasSize(2)
            // e3 must be kept (it's the diverse one); one of e1/e2 should be evicted
            assertThat(merged.gallery).contains(e3)
        }

        @Test
        @DisplayName("merging with empty gallery keeps other person's gallery")
        fun mergeWithEmptyGallery() {
            val e1 = testEmbedding(values = unitVector(128, 0))
            val personA = Person(name = "Alice", gallery = emptyList())
            val personB = Person(name = "Bob", gallery = listOf(e1))

            val merged = personA.mergeGallery(personB)
            assertThat(merged.gallery).hasSize(1)
            assertThat(merged.gallery).containsExactly(e1)
        }

        @Test
        @DisplayName("merging two empty galleries results in empty gallery")
        fun mergeBothEmpty() {
            val personA = Person(name = "Alice", gallery = emptyList())
            val personB = Person(name = "Bob", gallery = emptyList())

            val merged = personA.mergeGallery(personB)
            assertThat(merged.gallery).isEmpty()
        }

        @Test
        @DisplayName("merge preserves personA's id and name")
        fun preservesIdentity() {
            val personA = Person(id = "id-a", name = "Alice", gallery = emptyList())
            val personB = Person(id = "id-b", name = "Bob", gallery = emptyList())

            val merged = personA.mergeGallery(personB)
            assertThat(merged.id).isEqualTo("id-a")
            assertThat(merged.name).isEqualTo("Alice")
        }

        @Test
        @DisplayName("merge updates updatedAt timestamp")
        fun mergeUpdatesTimestamp() {
            val personA = Person(name = "Alice", updatedAt = 1000L)
            val personB = Person(name = "Bob")

            val merged = personA.mergeGallery(personB)
            assertThat(merged.updatedAt).isGreaterThan(1000L)
        }
    }

    // ── withSourcePath / withoutSourcePath ───────────────────────────────────

    @Nested
    @DisplayName("withSourcePath / withoutSourcePath")
    inner class SourcePathOperations {

        @Test
        @DisplayName("adds a source path")
        fun addSourcePath() {
            val person = Person(name = "Alice", sourcePaths = emptyList())

            val updated = person.withSourcePath("/photo1.jpg")
            assertThat(updated.sourcePaths).containsExactly("/photo1.jpg")
        }

        @Test
        @DisplayName("adding a duplicate path does not duplicate it")
        fun addDuplicatePath() {
            val person = Person(name = "Alice", sourcePaths = listOf("/photo1.jpg"))

            val updated = person.withSourcePath("/photo1.jpg")
            assertThat(updated.sourcePaths).containsExactly("/photo1.jpg")
            assertThat(updated.sourcePaths).hasSize(1)
        }

        @Test
        @DisplayName("removing a source path")
        fun removeSourcePath() {
            val person = Person(name = "Alice", sourcePaths = listOf("/photo1.jpg", "/photo2.jpg"))

            val updated = person.withoutSourcePath("/photo1.jpg")
            assertThat(updated.sourcePaths).containsExactly("/photo2.jpg")
        }

        @Test
        @DisplayName("removing a non-existent path is a no-op")
        fun removeNonExistentPath() {
            val person = Person(name = "Alice", sourcePaths = listOf("/photo1.jpg", "/photo2.jpg"))

            val updated = person.withoutSourcePath("/photo3.jpg")
            assertThat(updated.sourcePaths).containsExactly("/photo1.jpg", "/photo2.jpg")
        }

        @Test
        @DisplayName("adding multiple paths sequentially")
        fun addMultiplePaths() {
            val person = Person(name = "Alice")
                .withSourcePath("/photo1.jpg")
                .withSourcePath("/photo2.jpg")
                .withSourcePath("/photo3.jpg")

            assertThat(person.sourcePaths).containsExactly("/photo1.jpg", "/photo2.jpg", "/photo3.jpg")
        }

        @Test
        @DisplayName("withSourcePath updates updatedAt timestamp")
        fun withSourcePathUpdatesTimestamp() {
            val person = Person(name = "Alice", updatedAt = 1000L)
            val updated = person.withSourcePath("/photo1.jpg")
            assertThat(updated.updatedAt).isGreaterThan(1000L)
        }

        @Test
        @DisplayName("withoutSourcePath updates updatedAt timestamp")
        fun withoutSourcePathUpdatesTimestamp() {
            val person = Person(name = "Alice", sourcePaths = listOf("/photo1.jpg"), updatedAt = 1000L)
            val updated = person.withoutSourcePath("/photo1.jpg")
            assertThat(updated.updatedAt).isGreaterThan(1000L)
        }

        @Test
        @DisplayName("removing all source paths results in empty list")
        fun removeAllPaths() {
            val person = Person(name = "Alice", sourcePaths = listOf("/photo1.jpg"))
                .withoutSourcePath("/photo1.jpg")

            assertThat(person.sourcePaths).isEmpty()
        }
    }

    // ── photoCount ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("photoCount")
    inner class PhotoCount {

        @Test
        @DisplayName("photoCount equals sourcePaths.size with no paths")
        fun noPaths() {
            val person = Person(name = "Alice", sourcePaths = emptyList())
            assertThat(person.photoCount).isEqualTo(0)
        }

        @Test
        @DisplayName("photoCount equals sourcePaths.size with multiple paths")
        fun multiplePaths() {
            val person = Person(
                name = "Alice",
                sourcePaths = listOf("/photo1.jpg", "/photo2.jpg", "/photo3.jpg"),
            )
            assertThat(person.photoCount).isEqualTo(3)
        }

        @Test
        @DisplayName("photoCount reflects changes after withSourcePath")
        fun countAfterAdding() {
            val person = Person(name = "Alice")
                .withSourcePath("/photo1.jpg")
                .withSourcePath("/photo2.jpg")

            assertThat(person.photoCount).isEqualTo(2)
        }

        @Test
        @DisplayName("photoCount reflects changes after withoutSourcePath")
        fun countAfterRemoving() {
            val person = Person(name = "Alice", sourcePaths = listOf("/a.jpg", "/b.jpg", "/c.jpg"))
                .withoutSourcePath("/b.jpg")

            assertThat(person.photoCount).isEqualTo(2)
        }
    }

    // ── Default values ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("Default values")
    inner class DefaultValues {

        @Test
        @DisplayName("id is auto-generated (non-blank)")
        fun autoGeneratedId() {
            val person = Person()
            assertThat(person.id).isNotBlank()
        }

        @Test
        @DisplayName("each new Person gets a unique id")
        fun uniqueIds() {
            val person1 = Person()
            val person2 = Person()
            assertThat(person1.id).isNotEqualTo(person2.id)
        }

        @Test
        @DisplayName("gallery defaults to empty list")
        fun emptyGalleryDefault() {
            val person = Person()
            assertThat(person.gallery).isEmpty()
        }

        @Test
        @DisplayName("sourcePaths defaults to empty list")
        fun emptySourcePathsDefault() {
            val person = Person()
            assertThat(person.sourcePaths).isEmpty()
        }

        @Test
        @DisplayName("name defaults to Unnamed (not empty)")
        fun nonEmptyNameDefault() {
            val person = Person()
            assertThat(person.name).isEqualTo("Unnamed")
        }

        @Test
        @DisplayName("thumbnailPath defaults to empty string")
        fun emptyThumbnailDefault() {
            val person = Person()
            assertThat(person.thumbnailPath).isEmpty()
        }

        @Test
        @DisplayName("timestamps are auto-set (positive epoch millis)")
        fun timestampsAutoSet() {
            val person = Person()
            assertThat(person.createdAt).isGreaterThan(0L)
            assertThat(person.updatedAt).isGreaterThan(0L)
        }

        @Test
        @DisplayName("createdAt and updatedAt are close together on creation")
        fun timestampsCloseOnCreation() {
            val person = Person()
            // They should be within a few seconds of each other
            assertThat(person.updatedAt - person.createdAt).isLessThan(5000L)
        }
    }

    // ── Data class behavior ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Data class behavior")
    inner class DataClassBehavior {

        @Test
        @DisplayName("copy preserves all fields except those specified")
        fun copyPreservesFields() {
            val person = Person(
                id = "id-1",
                name = "Alice",
                gallery = listOf(testEmbedding()),
                thumbnailPath = "/thumb.jpg",
                sourcePaths = listOf("/a.jpg"),
                createdAt = 1000L,
                updatedAt = 2000L,
            )
            val copied = person.copy(name = "Bob")

            assertThat(copied.id).isEqualTo("id-1")
            assertThat(copied.name).isEqualTo("Bob")
            assertThat(copied.gallery).hasSize(1)
            assertThat(copied.thumbnailPath).isEqualTo("/thumb.jpg")
            assertThat(copied.sourcePaths).containsExactly("/a.jpg")
            assertThat(copied.createdAt).isEqualTo(1000L)
            assertThat(copied.updatedAt).isEqualTo(2000L)
        }

        @Test
        @DisplayName("equality works correctly")
        fun equality() {
            val id = "same-id"
            val person1 = Person(id = id, name = "Alice")
            val person2 = Person(id = id, name = "Alice")
            assertThat(person1).isEqualTo(person2)
        }

        @Test
        @DisplayName("inequality works correctly")
        fun inequality() {
            val person1 = Person(id = "id-1", name = "Alice")
            val person2 = Person(id = "id-2", name = "Bob")
            assertThat(person1).isNotEqualTo(person2)
        }
    }
}