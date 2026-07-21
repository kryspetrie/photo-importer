package org.kryspetrie.fileimport.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("PersonDirectory")
class PersonDirectoryTest {

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun testEmbedding(
        values: FloatArray = FloatArray(FaceEmbedding.DIM_MOBILEFACENET) { 0.01f * it },
        modelName: String = "mobilefacenet",
        sourcePath: String = "",
    ) = FaceEmbedding(embeddingVector = values, modelName = modelName, sourcePath = sourcePath)

    /** Unit vector with a single 1.0 at [idx] and 0s elsewhere — useful for deterministic cosine similarity. */
    private fun unitVec(dim: Int = FaceEmbedding.DIM_MOBILEFACENET, idx: Int) =
        FloatArray(dim) { if (it == idx) 1f else 0f }

    /** Create a test person with a given name and optional gallery. */
    private fun testPerson(
        id: String = "person-${System.nanoTime()}",
        name: String = "Alice",
        gallery: List<FaceEmbedding> = emptyList(),
        sourcePaths: List<String> = emptyList(),
    ) = Person(id = id, name = name, gallery = gallery, sourcePaths = sourcePaths)

    // ── personById ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("personById")
    inner class PersonById {

        @Test
        @DisplayName("finds a person by ID")
        fun findsPersonById() {
            val alice = testPerson(id = "a1", name = "Alice")
            val bob = testPerson(id = "b1", name = "Bob")
            val dir = PersonDirectory(persons = listOf(alice, bob))

            assertThat(dir.personById("a1")).isEqualTo(alice)
            assertThat(dir.personById("b1")).isEqualTo(bob)
        }

        @Test
        @DisplayName("returns null when ID not found")
        fun returnsNullWhenNotFound() {
            val dir = PersonDirectory(persons = listOf(testPerson(id = "a1", name = "Alice")))

            assertThat(dir.personById("nonexistent")).isNull()
        }

        @Test
        @DisplayName("returns null for empty directory")
        fun returnsNullForEmptyDirectory() {
            val dir = PersonDirectory()

            assertThat(dir.personById("anything")).isNull()
        }
    }

    // ── personByName ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("personByName")
    inner class PersonByName {

        @Test
        @DisplayName("finds a person by exact name")
        fun findsPersonByExactName() {
            val alice = testPerson(name = "Alice")
            val dir = PersonDirectory(persons = listOf(alice))

            assertThat(dir.personByName("Alice")).isEqualTo(alice)
        }

        @Test
        @DisplayName("case-insensitive match")
        fun caseInsensitiveMatch() {
            val alice = testPerson(name = "Alice")
            val dir = PersonDirectory(persons = listOf(alice))

            assertThat(dir.personByName("alice")).isEqualTo(alice)
            assertThat(dir.personByName("ALICE")).isEqualTo(alice)
            assertThat(dir.personByName("AlIce")).isEqualTo(alice)
        }

        @Test
        @DisplayName("trims whitespace before matching")
        fun trimsWhitespace() {
            val alice = testPerson(name = "Alice")
            val dir = PersonDirectory(persons = listOf(alice))

            assertThat(dir.personByName("  Alice  ")).isEqualTo(alice)
        }

        @Test
        @DisplayName("returns null when name not found")
        fun returnsNullWhenNotFound() {
            val dir = PersonDirectory(persons = listOf(testPerson(name = "Alice")))

            assertThat(dir.personByName("Bob")).isNull()
        }

        @Test
        @DisplayName("returns null for empty directory")
        fun returnsNullForEmptyDirectory() {
            val dir = PersonDirectory()

            assertThat(dir.personByName("Alice")).isNull()
        }
    }

    // ── isNameAvailable ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("isNameAvailable")
    inner class IsNameAvailable {

        @Test
        @DisplayName("returns true for a name not in the directory")
        fun availableName() {
            val dir = PersonDirectory(persons = listOf(testPerson(name = "Alice")))

            assertThat(dir.isNameAvailable("Bob")).isTrue()
        }

        @Test
        @DisplayName("returns false for a duplicate name")
        fun duplicateName() {
            val dir = PersonDirectory(persons = listOf(testPerson(name = "Alice")))

            assertThat(dir.isNameAvailable("Alice")).isFalse()
        }

        @Test
        @DisplayName("case-insensitive duplicate detection")
        fun caseInsensitiveDuplicate() {
            val dir = PersonDirectory(persons = listOf(testPerson(name = "Alice")))

            assertThat(dir.isNameAvailable("alice")).isFalse()
            assertThat(dir.isNameAvailable("ALICE")).isFalse()
        }

        @Test
        @DisplayName("returns true for same name when excludeId matches")
        fun sameNameWithExcludeId() {
            val alice = testPerson(id = "a1", name = "Alice")
            val dir = PersonDirectory(persons = listOf(alice))

            // Alice can keep her own name during a rename
            assertThat(dir.isNameAvailable("Alice", excludeId = "a1")).isTrue()
        }

        @Test
        @DisplayName("returns false when another person has the name even with excludeId")
        fun differentPersonHasName() {
            val alice = testPerson(id = "a1", name = "Alice")
            val bob = testPerson(id = "b1", name = "Bob")
            val dir = PersonDirectory(persons = listOf(alice, bob))

            // Alice cannot rename to "Bob" even with her own excludeId
            assertThat(dir.isNameAvailable("Bob", excludeId = "a1")).isFalse()
        }

        @Test
        @DisplayName("trims whitespace before checking")
        fun trimsWhitespace() {
            val dir = PersonDirectory(persons = listOf(testPerson(name = "Alice")))

            // "  Alice  " with trimming should match "Alice"
            assertThat(dir.isNameAvailable("  Alice  ")).isFalse()
        }

        @Test
        @DisplayName("null excludeId behaves as no exclusion")
        fun nullExcludeId() {
            val alice = testPerson(id = "a1", name = "Alice")
            val dir = PersonDirectory(persons = listOf(alice))

            assertThat(dir.isNameAvailable("Alice", excludeId = null)).isFalse()
        }
    }

    // ── validateForImport ────────────────────────────────────────────────────

    @Nested
    @DisplayName("validateForImport")
    inner class ValidateForImport {

        @Test
        @DisplayName("empty directory is valid")
        fun emptyDirectoryIsValid() {
            val dir = PersonDirectory()

            assertThat(dir.validateForImport()).isEmpty()
        }

        @Test
        @DisplayName("valid directory returns no errors")
        fun validDirectoryNoErrors() {
            val dir = PersonDirectory(persons = listOf(testPerson(name = "Alice")))

            assertThat(dir.validateForImport()).isEmpty()
        }

        @Test
        @DisplayName("exceeds MAX_DIRECTORY_SIZE")
        fun exceedsMaxDirectorySize() {
            val persons = (1..PersonDirectory.MAX_DIRECTORY_SIZE + 1).map {
                testPerson(id = "p$it", name = "Person$it")
            }
            val dir = PersonDirectory(persons = persons)

            val errors = dir.validateForImport()
            assertThat(errors).anyMatch { it.contains("persons") && it.contains("maximum") }
        }

        @Test
        @DisplayName("exactly at MAX_DIRECTORY_SIZE is valid")
        fun atMaxDirectorySizeIsValid() {
            val persons = (1..PersonDirectory.MAX_DIRECTORY_SIZE).map {
                testPerson(id = "p$it", name = "Person$it")
            }
            val dir = PersonDirectory(persons = persons)

            // May still have other errors but no size error
            assertThat(dir.validateForImport().none { it.contains("persons") && it.contains("maximum") }).isTrue()
        }

        @Test
        @DisplayName("exceeds MAX_TOTAL_EMBEDDINGS")
        fun exceedsMaxTotalEmbeddings() {
            val largeGallery = (1..101).map { testEmbedding() }
            val persons = listOf(
                testPerson(name = "P1", gallery = largeGallery)
            )
            // 101 embeddings > 0 but we need to exceed 10000 total; create many persons with galleries
            // Use 101 persons each with 100 embeddings = 10100 total
            val manyPersons = (1..101).map { idx ->
                testPerson(
                    id = "p$idx",
                    name = "Person$idx",
                    gallery = (1..100).map { testEmbedding() }
                )
            }
            val dir = PersonDirectory(persons = manyPersons)
            // 101 * 100 = 10100 > 10000

            val errors = dir.validateForImport()
            assertThat(errors).anyMatch { it.contains("embeddings") && it.contains("maximum") }
        }

        @Test
        @DisplayName("blank name produces error")
        fun blankNameError() {
            val dir = PersonDirectory(persons = listOf(Person(name = "   ")))

            val errors = dir.validateForImport()
            assertThat(errors).anyMatch { it.contains("blank") }
        }

        @Test
        @DisplayName("too long name produces error")
        fun tooLongNameError() {
            val dir = PersonDirectory(persons = listOf(Person(name = "x".repeat(101))))

            val errors = dir.validateForImport()
            assertThat(errors).anyMatch { it.contains("cannot exceed") }
        }

        @Test
        @DisplayName("future version produces error")
        fun futureVersionError() {
            val dir = PersonDirectory(version = PersonDirectory.CURRENT_VERSION + 1)

            val errors = dir.validateForImport()
            assertThat(errors).anyMatch { it.contains("version") && it.contains("newer") }
        }

        @Test
        @DisplayName("multiple errors reported together")
        fun multipleErrorsTogether() {
            val dir = PersonDirectory(
                persons = listOf(Person(name = "")),
                version = PersonDirectory.CURRENT_VERSION + 1,
            )

            val errors = dir.validateForImport()
            assertThat(errors).hasSizeGreaterThanOrEqualTo(2)
            assertThat(errors).anyMatch { it.contains("blank") }
            assertThat(errors).anyMatch { it.contains("version") }
        }

        @Test
        @DisplayName("invalid embedding dimension produces error")
        fun invalidEmbeddingDimension() {
            val weirdEmbedding = FaceEmbedding(
                embeddingVector = FloatArray(64) { 0.01f * it },
                modelName = "mobilefacenet",
            )
            val dir = PersonDirectory(persons = listOf(
                testPerson(name = "Alice", gallery = listOf(weirdEmbedding))
            ))

            val errors = dir.validateForImport()
            assertThat(errors).anyMatch { it.contains("dimension") || it.contains("invalid") }
        }

        @Test
        @DisplayName("duplicate names (case-insensitive) produce error")
        fun duplicateNamesError() {
            val dir = PersonDirectory(persons = listOf(
                testPerson(id = "p1", name = "Alice"),
                testPerson(id = "p2", name = "alice"), // case-insensitive duplicate
            ))

            val errors = dir.validateForImport()
            assertThat(errors).anyMatch { it.contains("Duplicate") || it.contains("duplicate") }
        }

        @Test
        @DisplayName("exact duplicate names produce error")
        fun exactDuplicateNamesError() {
            val dir = PersonDirectory(persons = listOf(
                testPerson(id = "p1", name = "Bob"),
                testPerson(id = "p2", name = "Bob"),
            ))

            val errors = dir.validateForImport()
            assertThat(errors).anyMatch { it.contains("Duplicate") || it.contains("duplicate") }
        }
    }

    // ── findBestMatch ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findBestMatch")
    inner class FindBestMatch {

        @Test
        @DisplayName("empty directory returns null")
        fun emptyDirectoryReturnsNull() {
            val dir = PersonDirectory()
            val embedding = testEmbedding()

            assertThat(dir.findBestMatch(embedding)).isNull()
        }

        @Test
        @DisplayName("person with empty gallery is skipped")
        fun emptyGallerySkipped() {
            val person = testPerson(name = "Alice", gallery = emptyList())
            val dir = PersonDirectory(persons = listOf(person))
            val embedding = testEmbedding()

            assertThat(dir.findBestMatch(embedding)).isNull()
        }

        @Test
        @DisplayName("single person match above threshold returns that person")
        fun singleMatch() {
            // Use unit vector at dimension 0
            val emb1 = testEmbedding(values = unitVec(idx = 0))
            val alice = testPerson(name = "Alice", gallery = listOf(emb1))
            val dir = PersonDirectory(persons = listOf(alice))

            // Same unit vector → cosine similarity = 1.0
            val probe = testEmbedding(values = unitVec(idx = 0))
            assertThat(dir.findBestMatch(probe)).isEqualTo(alice)
        }

        @Test
        @DisplayName("best match wins among multiple persons")
        fun bestMatchWins() {
            // Alice at dim 0, Bob at dim 1, Charlie at dim 2
            val alice = testPerson(name = "Alice", gallery = listOf(
                testEmbedding(values = unitVec(idx = 0))
            ))
            val bob = testPerson(name = "Bob", gallery = listOf(
                testEmbedding(values = unitVec(idx = 1))
            ))
            val charlie = testPerson(name = "Charlie", gallery = listOf(
                testEmbedding(values = unitVec(idx = 2))
            ))
            val dir = PersonDirectory(persons = listOf(alice, bob, charlie))

            // Probe aligned with Alice's embedding → cos ≈ 0.99
            // Bob and Charlie are nearly orthogonal → cos ≈ 0.07
            val probeValues = FloatArray(FaceEmbedding.DIM_MOBILEFACENET).also {
                it[0] = 0.99f
                it[1] = 0.1f
                it[2] = 0.1f
            }
            val probe = testEmbedding(values = probeValues)

            val result = dir.findBestMatch(probe)
            assertThat(result).isEqualTo(alice)
        }

        @Test
        @DisplayName("below threshold returns null")
        fun belowThresholdReturnsNull() {
            // Orthogonal vectors → cosine similarity = 0
            val alice = testPerson(name = "Alice", gallery = listOf(
                testEmbedding(values = unitVec(idx = 0))
            ))
            val dir = PersonDirectory(persons = listOf(alice))

            // Probe at dimension 1 is orthogonal (similarity = 0), below threshold
            val probe = testEmbedding(values = unitVec(idx = 1))
            assertThat(dir.findBestMatch(probe)).isNull()
        }

        @Test
        @DisplayName("custom threshold parameter")
        fun customThreshold() {
            // Two vectors with medium similarity (~0.707 between unit vec dim 0 and
            // a vector split between dim 0 and dim 1)
            val alice = testPerson(name = "Alice", gallery = listOf(
                testEmbedding(values = unitVec(idx = 0))
            ))
            val dir = PersonDirectory(persons = listOf(alice))

            // Vector at 45° between dimensions 0 and 1:
            // cos with unitVec(0) = (1/sqrt(2)) ≈ 0.707
            val probeValues = FloatArray(FaceEmbedding.DIM_MOBILEFACENET).also {
                it[0] = 1f
                it[1] = 1f
            }
            val probe = testEmbedding(values = probeValues)

            // Above MATCH_THRESHOLD (0.65) but below AUTO_TAG_THRESHOLD (0.75)
            assertThat(dir.findBestMatch(probe, threshold = FaceEmbedding.MATCH_THRESHOLD)).isEqualTo(alice)
            assertThat(dir.findBestMatch(probe, threshold = FaceEmbedding.AUTO_TAG_THRESHOLD)).isNull()
        }

        @Test
        @DisplayName("only considers persons with non-empty galleries")
        fun skipsEmptyGalleries() {
            val alice = testPerson(name = "Alice", gallery = emptyList())
            val bob = testPerson(name = "Bob", gallery = listOf(testEmbedding(values = unitVec(idx = 0))))
            val dir = PersonDirectory(persons = listOf(alice, bob))

            val probe = testEmbedding(values = unitVec(idx = 0))
            assertThat(dir.findBestMatch(probe)).isEqualTo(bob)
        }
    }

    // ── findAllMatches ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("findAllMatches")
    inner class FindAllMatches {

        @Test
        @DisplayName("empty directory returns empty list")
        fun emptyDirectoryReturnsEmpty() {
            val dir = PersonDirectory()
            assertThat(dir.findAllMatches(testEmbedding())).isEmpty()
        }

        @Test
        @DisplayName("returns all persons above threshold sorted by score descending")
        fun allMatchesSortedByScore() {
            val alice = testPerson(name = "Alice", gallery = listOf(
                testEmbedding(values = unitVec(idx = 0))
            ))
            val bob = testPerson(name = "Bob", gallery = listOf(
                testEmbedding(values = unitVec(idx = 1))
            ))
            val charlie = testPerson(name = "Charlie", gallery = emptyList())
            val dir = PersonDirectory(persons = listOf(alice, bob, charlie))

            // Probe has components in both dim 0 and dim 1:
            // cos(probe, Alice) = 0.9 / sqrt(0.81 + 0.64) ≈ 0.75  (> 0.65 threshold)
            // cos(probe, Bob)   = 0.8 / sqrt(0.81 + 0.64) ≈ 0.66  (> 0.65 threshold)
            val probeValues = FloatArray(FaceEmbedding.DIM_MOBILEFACENET).also {
                it[0] = 0.9f  // strong for Alice
                it[1] = 0.8f  // moderate for Bob
            }
            val probe = testEmbedding(values = probeValues)

            val matches = dir.findAllMatches(probe)
            // Both Alice and Bob exceed threshold; Charlie has empty gallery and is skipped
            assertThat(matches.map { it.first.name }).containsExactly("Alice", "Bob")
            // Scores are descending (Alice's score > Bob's score)
            assertThat(matches[0].second).isGreaterThan(matches[1].second)
        }

        @Test
        @DisplayName("no matches above threshold returns empty list")
        fun noMatchesAboveThreshold() {
            val alice = testPerson(name = "Alice", gallery = listOf(
                testEmbedding(values = unitVec(idx = 0))
            ))
            val dir = PersonDirectory(persons = listOf(alice))

            // Orthogonal → similarity = 0
            val probe = testEmbedding(values = unitVec(idx = 50))
            assertThat(dir.findAllMatches(probe)).isEmpty()
        }

        @Test
        @DisplayName("persons with empty galleries are excluded")
        fun emptyGalleriesExcluded() {
            val alice = testPerson(name = "Alice", gallery = emptyList())
            val dir = PersonDirectory(persons = listOf(alice))

            assertThat(dir.findAllMatches(testEmbedding())).isEmpty()
        }

        @Test
        @DisplayName("custom threshold filters more aggressively")
        fun customThresholdFilter() {
            val alice = testPerson(name = "Alice", gallery = listOf(
                testEmbedding(values = unitVec(idx = 0))
            ))
            val dir = PersonDirectory(persons = listOf(alice))

            // 45° vector → similarity ≈ 0.707
            val probeValues = FloatArray(FaceEmbedding.DIM_MOBILEFACENET).also {
                it[0] = 1f
                it[1] = 1f
            }
            val probe = testEmbedding(values = probeValues)

            // At threshold 0.65, it matches
            assertThat(dir.findAllMatches(probe, threshold = 0.65f)).isNotEmpty
            // At threshold 0.8, it does not
            assertThat(dir.findAllMatches(probe, threshold = 0.8f)).isEmpty()
        }
    }

    // ── withPerson ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("withPerson")
    inner class WithPerson {

        @Test
        @DisplayName("adds a new person")
        fun addsNewPerson() {
            val dir = PersonDirectory()
            val alice = testPerson(id = "a1", name = "Alice")

            val updated = dir.withPerson(alice)
            assertThat(updated.persons).hasSize(1)
            assertThat(updated.persons[0]).isEqualTo(alice)
        }

        @Test
        @DisplayName("updates an existing person with same ID")
        fun updatesExistingPerson() {
            val alice = testPerson(id = "a1", name = "Alice")
            val dir = PersonDirectory(persons = listOf(alice))

            val updatedAlice = alice.copy(name = "Alice Updated")
            val updated = dir.withPerson(updatedAlice)

            assertThat(updated.persons).hasSize(1)
            assertThat(updated.persons[0].name).isEqualTo("Alice Updated")
        }

        @Test
        @DisplayName("throws on invalid person name")
        fun throwsOnInvalidName() {
            val dir = PersonDirectory()

            assertThatThrownBy { dir.withPerson(Person(name = "")) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Invalid person name")
        }

        @Test
        @DisplayName("throws on blank name")
        fun throwsOnBlankName() {
            val dir = PersonDirectory()

            assertThatThrownBy { dir.withPerson(Person(name = "   ")) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    // ── withoutPerson ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("withoutPerson")
    inner class WithoutPerson {

        @Test
        @DisplayName("removes a person by ID")
        fun removesPersonById() {
            val alice = testPerson(id = "a1", name = "Alice")
            val bob = testPerson(id = "b1", name = "Bob")
            val dir = PersonDirectory(persons = listOf(alice, bob))

            val updated = dir.withoutPerson("a1")
            assertThat(updated.persons).hasSize(1)
            assertThat(updated.persons[0]).isEqualTo(bob)
        }

        @Test
        @DisplayName("non-existent ID returns unchanged directory")
        fun nonExistentIdNoChange() {
            val alice = testPerson(id = "a1", name = "Alice")
            val dir = PersonDirectory(persons = listOf(alice))

            val updated = dir.withoutPerson("nonexistent")
            assertThat(updated.persons).hasSize(1)
            assertThat(updated.persons[0]).isEqualTo(alice)
        }

        @Test
        @DisplayName("empty directory stays empty")
        fun emptyDirectoryStaysEmpty() {
            val dir = PersonDirectory()

            val updated = dir.withoutPerson("anything")
            assertThat(updated.persons).isEmpty()
        }
    }

    // ── mergePersons ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("mergePersons")
    inner class MergePersons {

        @Test
        @DisplayName("merges two persons, target keeps name")
        fun mergeKeepsTargetName() {
            val alice = testPerson(
                id = "a1",
                name = "Alice",
                gallery = listOf(testEmbedding(values = unitVec(idx = 0))),
                sourcePaths = listOf("/photo1.jpg"),
            )
            val bob = testPerson(
                id = "b1",
                name = "Bob",
                gallery = listOf(testEmbedding(values = unitVec(idx = 1))),
                sourcePaths = listOf("/photo2.jpg"),
            )
            val dir = PersonDirectory(persons = listOf(alice, bob))

            val merged = dir.mergePersons("a1", "b1")

            val mergedPerson = merged.personById("a1")
            assertThat(mergedPerson).isNotNull
            assertThat(mergedPerson!!.name).isEqualTo("Alice")
            // Gallery should contain both embeddings
            assertThat(mergedPerson.gallery).hasSize(2)
            // Source paths should be merged
            assertThat(mergedPerson.sourcePaths).containsExactly("/photo1.jpg", "/photo2.jpg")
        }

        @Test
        @DisplayName("source person is removed from directory after merge")
        fun sourceRemovedAfterMerge() {
            val alice = testPerson(id = "a1", name = "Alice")
            val bob = testPerson(id = "b1", name = "Bob")
            val dir = PersonDirectory(persons = listOf(alice, bob))

            val merged = dir.mergePersons("a1", "b1")

            assertThat(merged.personById("b1")).isNull()
            assertThat(merged.persons).hasSize(1)
        }

        @Test
        @DisplayName("non-existent target ID returns unchanged directory")
        fun nonExistentTargetNoChange() {
            val alice = testPerson(id = "a1", name = "Alice")
            val dir = PersonDirectory(persons = listOf(alice))

            val merged = dir.mergePersons("nonexistent", "a1")
            assertThat(merged).isEqualTo(dir)
        }

        @Test
        @DisplayName("non-existent source ID returns unchanged directory")
        fun nonExistentSourceNoChange() {
            val alice = testPerson(id = "a1", name = "Alice")
            val dir = PersonDirectory(persons = listOf(alice))

            val merged = dir.mergePersons("a1", "nonexistent")
            assertThat(merged).isEqualTo(dir)
        }

        @Test
        @DisplayName("merge preserves diversity in combined gallery")
        fun mergePreservesGalleryDiversity() {
            val alice = testPerson(
                id = "a1",
                name = "Alice",
                gallery = listOf(testEmbedding(values = unitVec(idx = 0))),
            )
            val bob = testPerson(
                id = "b1",
                name = "Bob",
                gallery = listOf(testEmbedding(values = unitVec(idx = 1))),
            )
            val dir = PersonDirectory(persons = listOf(alice, bob))

            val merged = dir.mergePersons("a1", "b1")
            val mergedPerson = merged.personById("a1")!!

            // Both embeddings are orthogonal (most diverse), so both should be kept
            assertThat(mergedPerson.gallery).hasSize(2)
        }
    }

    // ── allSourcePaths ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("allSourcePaths")
    inner class AllSourcePaths {

        @Test
        @DisplayName("returns all unique source paths across all persons")
        fun allUniqueSourcePaths() {
            val alice = testPerson(name = "Alice", sourcePaths = listOf("/photo1.jpg", "/photo2.jpg"))
            val bob = testPerson(name = "Bob", sourcePaths = listOf("/photo2.jpg", "/photo3.jpg"))
            val dir = PersonDirectory(persons = listOf(alice, bob))

            assertThat(dir.allSourcePaths).containsExactlyInAnyOrder("/photo1.jpg", "/photo2.jpg", "/photo3.jpg")
        }

        @Test
        @DisplayName("empty directory returns empty list")
        fun emptyDirectory() {
            val dir = PersonDirectory()

            assertThat(dir.allSourcePaths).isEmpty()
        }

        @Test
        @DisplayName("persons with no source paths contribute nothing")
        fun noSourcePaths() {
            val alice = testPerson(name = "Alice", sourcePaths = emptyList())
            val dir = PersonDirectory(persons = listOf(alice))

            assertThat(dir.allSourcePaths).isEmpty()
        }

        @Test
        @DisplayName("deduplicates paths")
        fun deduplicatesPaths() {
            val alice = testPerson(name = "Alice", sourcePaths = listOf("/photo1.jpg"))
            val bob = testPerson(name = "Bob", sourcePaths = listOf("/photo1.jpg"))
            val dir = PersonDirectory(persons = listOf(alice, bob))

            assertThat(dir.allSourcePaths).containsExactly("/photo1.jpg")
        }
    }

    // ── totalEmbeddings ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("totalEmbeddings")
    inner class TotalEmbeddings {

        @Test
        @DisplayName("sums gallery sizes across all persons")
        fun sumsGallerySizes() {
            val alice = testPerson(name = "Alice", gallery = listOf(testEmbedding(), testEmbedding()))
            val bob = testPerson(name = "Bob", gallery = listOf(testEmbedding()))
            val dir = PersonDirectory(persons = listOf(alice, bob))

            assertThat(dir.totalEmbeddings).isEqualTo(3)
        }

        @Test
        @DisplayName("empty directory has zero embeddings")
        fun emptyDirectoryZeroEmbeddings() {
            val dir = PersonDirectory()

            assertThat(dir.totalEmbeddings).isEqualTo(0)
        }

        @Test
        @DisplayName("persons with no gallery contribute zero")
        fun noGalleryContributesZero() {
            val alice = testPerson(name = "Alice", gallery = emptyList())
            val dir = PersonDirectory(persons = listOf(alice))

            assertThat(dir.totalEmbeddings).isEqualTo(0)
        }
    }

    // ── isEmpty ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("isEmpty")
    inner class IsEmpty {

        @Test
        @DisplayName("true when no persons")
        fun trueWhenEmpty() {
            val dir = PersonDirectory()

            assertThat(dir.isEmpty).isTrue()
        }

        @Test
        @DisplayName("false when persons exist")
        fun falseWhenPersonsExist() {
            val dir = PersonDirectory(persons = listOf(testPerson(name = "Alice")))

            assertThat(dir.isEmpty).isFalse()
        }
    }

    // ── Companion constants ─────────────────────────────────────────────────

    @Nested
    @DisplayName("companion constants")
    inner class CompanionConstants {

        @Test
        @DisplayName("CURRENT_VERSION is 1")
        fun currentVersion() {
            assertThat(PersonDirectory.CURRENT_VERSION).isEqualTo(1)
        }

        @Test
        @DisplayName("MAX_DIRECTORY_SIZE is 500")
        fun maxDirectorySize() {
            assertThat(PersonDirectory.MAX_DIRECTORY_SIZE).isEqualTo(500)
        }

        @Test
        @DisplayName("MAX_TOTAL_EMBEDDINGS is 10000")
        fun maxTotalEmbeddings() {
            assertThat(PersonDirectory.MAX_TOTAL_EMBEDDINGS).isEqualTo(10000)
        }
    }

    // ── Default values ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("default values")
    inner class DefaultValues {

        @Test
        @DisplayName("default persons is empty list")
        fun defaultPersonsEmpty() {
            val dir = PersonDirectory()

            assertThat(dir.persons).isEmpty()
        }

        @Test
        @DisplayName("default version is CURRENT_VERSION")
        fun defaultVersionIsCurrent() {
            val dir = PersonDirectory()

            assertThat(dir.version).isEqualTo(PersonDirectory.CURRENT_VERSION)
        }
    }
}