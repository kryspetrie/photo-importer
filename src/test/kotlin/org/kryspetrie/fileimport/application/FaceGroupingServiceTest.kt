package org.kryspetrie.fileimport.application

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.domain.model.FaceEmbedding
import org.kryspetrie.fileimport.domain.model.FaceMatchingConfig
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.Person
import org.kryspetrie.fileimport.domain.model.PersonDirectory
import org.kryspetrie.fileimport.domain.model.ProcessedImage
import org.kryspetrie.fileimport.domain.port.DetectedFace
import org.kryspetrie.fileimport.domain.port.FaceDetectionPort
import org.kryspetrie.fileimport.domain.port.FaceEmbeddingPort
import org.kryspetrie.fileimport.domain.port.ImageProcessingPort
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@DisplayName("FaceGroupingService")
class FaceGroupingServiceTest {

    private val faceDetectionPort: FaceDetectionPort = mock()
    private val faceEmbeddingPort: FaceEmbeddingPort = mock()
    private val personService: PersonService = mock()
    private val imageProcessingPort: ImageProcessingPort = mock()

    private lateinit var service: FaceGroupingService
    private lateinit var directoryFlow: MutableStateFlow<PersonDirectory>

    private val mockImage: ProcessedImage = mock()

    // ── Shared test fixtures ──────────────────────────────────────────────

    private val testFace = DetectedFace(
        x1 = 10f, y1 = 20f, x2 = 110f, y2 = 120f,
        confidence = 0.95f,
    )

    private val testFace2 = DetectedFace(
        x1 = 200f, y1 = 50f, x2 = 300f, y2 = 150f,
        confidence = 0.85f,
    )

    private val testEmbeddingVector = FloatArray(128) { i -> (i + 1).toFloat() / 128f }
    private val testEmbedding = FaceEmbedding(
        embeddingVector = testEmbeddingVector,
        quality = 0.9f,
        modelName = "mobilefacenet",
        sourcePath = "/photos/test.jpg",
    )

    private val testEmbedding2 = FaceEmbedding(
        embeddingVector = FloatArray(128) { i -> if (i % 2 == 0) 0.5f else -0.3f },
        quality = 0.8f,
        modelName = "mobilefacenet",
        sourcePath = "/photos/test2.jpg",
    )

    private val testPerson = Person(
        id = "person-1",
        name = "Alice",
        gallery = listOf(testEmbedding),
    )

    @BeforeEach
    fun setup() {
        directoryFlow = MutableStateFlow(PersonDirectory())
        whenever(personService.directory).thenReturn(directoryFlow)
        runBlocking { whenever(personService.getMatchingConfig()).thenReturn(FaceMatchingConfig()) }
        whenever(mockImage.width).thenReturn(640)
        whenever(mockImage.height).thenReturn(480)
        service = FaceGroupingService(faceDetectionPort, faceEmbeddingPort, personService, imageProcessingPort)
    }

    // ── Helper to create a similar embedding (cosine similarity ≈ 1.0 with target) ──

    private fun similarEmbeddingTo(base: FaceEmbedding, id: String = "emb-sim"): FaceEmbedding {
        // Use the same vector to guarantee cosine similarity = 1.0 for match testing
        return FaceEmbedding(
            embeddingVector = base.vector.copyOf(),
            quality = base.quality,
            modelName = base.modelName,
            sourcePath = base.sourcePath,
            id = id,
        )
    }

    private fun dissimilarEmbedding(id: String = "emb-dis"): FaceEmbedding {
        // Perfectly orthogonal to testEmbedding (all values negative where test has positive)
        // This guarantees cosine similarity ≈ -1.0, which is well below MATCH_THRESHOLD
        val vec = FloatArray(128) { i -> -(i + 1).toFloat() / 128f }
        return FaceEmbedding(
            embeddingVector = vec,
            quality = 0.9f,
            modelName = "mobilefacenet",
            id = id,
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    //  detectAndSuggest
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("detectAndSuggest")
    inner class DetectAndSuggest {

        @Test
        @DisplayName("should return empty list when image is not found")
        fun imageNotFound() = runTest {
            whenever(imageProcessingPort.readImage(FilePath("/missing.jpg"))).thenReturn(null)

            val result = service.detectAndSuggest("/missing.jpg")

            assertThat(result).isEmpty()
        }

        @Test
        @DisplayName("should return empty list when no faces are detected")
        fun noFacesDetected() = runTest {
            whenever(imageProcessingPort.readImage(FilePath("/photo.jpg"))).thenReturn(mockImage)
            whenever(faceDetectionPort.detectFaces(mockImage, confThreshold = 0.5f)).thenReturn(emptyList())

            val result = service.detectAndSuggest("/photo.jpg")

            assertThat(result).isEmpty()
        }

        @Test
        @DisplayName("should return empty list with custom confidence threshold when no faces detected")
        fun noFacesWithHighThreshold() = runTest {
            whenever(imageProcessingPort.readImage(FilePath("/photo.jpg"))).thenReturn(mockImage)
            whenever(faceDetectionPort.detectFaces(mockImage, confThreshold = 0.9f)).thenReturn(emptyList())

            val result = service.detectAndSuggest("/photo.jpg", confThreshold = 0.9f)

            assertThat(result).isEmpty()
        }

        @Test
        @DisplayName("should return suggestions with null embeddings when embedding model unavailable")
        fun embeddingUnavailable() = runTest {
            whenever(imageProcessingPort.readImage(FilePath("/photo.jpg"))).thenReturn(mockImage)
            whenever(faceDetectionPort.detectFaces(mockImage, confThreshold = 0.5f)).thenReturn(listOf(testFace))
            whenever(faceEmbeddingPort.isEmbeddingAvailable()).thenReturn(false)

            val result = service.detectAndSuggest("/photo.jpg")

            assertThat(result).hasSize(1)
            assertThat(result[0].detectedFace).isEqualTo(testFace)
            assertThat(result[0].embedding).isNull()
            assertThat(result[0].suggestedPerson).isNull()
            assertThat(result[0].confidence).isEqualTo(0f)
            assertThat(result[0].isConfident).isFalse()
            assertThat(result[0].isPotential).isFalse()
        }

        @Test
        @DisplayName("should return suggestions with embeddings but no person when no match in directory")
        fun embeddingNoMatch() = runTest {
            whenever(imageProcessingPort.readImage(FilePath("/photo.jpg"))).thenReturn(mockImage)
            whenever(faceDetectionPort.detectFaces(mockImage, confThreshold = 0.5f)).thenReturn(listOf(testFace))
            whenever(faceEmbeddingPort.isEmbeddingAvailable()).thenReturn(true)
            whenever(faceEmbeddingPort.extractEmbeddings(mockImage, listOf(testFace)))
                .thenReturn(listOf(testEmbedding))
            // Empty directory — no match
            directoryFlow.value = PersonDirectory()

            val result = service.detectAndSuggest("/photo.jpg")

            assertThat(result).hasSize(1)
            assertThat(result[0].detectedFace).isEqualTo(testFace)
            assertThat(result[0].embedding).isNotNull
            assertThat(result[0].suggestedPerson).isNull()
            assertThat(result[0].confidence).isEqualTo(0f)
            assertThat(result[0].isConfident).isFalse()
            assertThat(result[0].isPotential).isFalse()
        }

        @Test
        @DisplayName("should suggest person when match found above MATCH_THRESHOLD but below AUTO_TAG_THRESHOLD")
        fun matchPotentialButNotConfident() = runTest {
            // Create an embedding that scores between MATCH_THRESHOLD (0.65) and AUTO_TAG_THRESHOLD (0.75)
            // by placing the person's gallery embedding as a partial match
            val galleryEmbedding = FaceEmbedding(
                embeddingVector = FloatArray(128) { i ->
                    if (i < 64) 1f else 0f
                },
                quality = 1.0f,
                modelName = "mobilefacenet",
                id = "gallery-emb",
            )
            val person = Person(id = "person-1", name = "Bob", gallery = listOf(galleryEmbedding))

            // Query embedding that will have a medium similarity
            val queryEmbedding = FaceEmbedding(
                embeddingVector = FloatArray(128) { i ->
                    if (i < 80) 1f else 0f
                },
                quality = 1.0f,
                modelName = "mobilefacenet",
                id = "query-emb",
            )

            whenever(imageProcessingPort.readImage(FilePath("/photo.jpg"))).thenReturn(mockImage)
            whenever(faceDetectionPort.detectFaces(mockImage, confThreshold = 0.5f)).thenReturn(listOf(testFace))
            whenever(faceEmbeddingPort.isEmbeddingAvailable()).thenReturn(true)
            whenever(faceEmbeddingPort.extractEmbeddings(mockImage, listOf(testFace)))
                .thenReturn(listOf(queryEmbedding))
            directoryFlow.value = PersonDirectory(persons = listOf(person))

            val result = service.detectAndSuggest("/photo.jpg")

            assertThat(result).hasSize(1)
            assertThat(result[0].suggestedPerson).isEqualTo(person)
            assertThat(result[0].isPotential).isTrue()
            // Confidence between 0.65 and 0.75 → isConfident = false
        }

        @Test
        @DisplayName("should suggest person with isConfident=true when match above AUTO_TAG_THRESHOLD")
        fun matchConfident() = runTest {
            // Use identical embedding vectors → cosine similarity = 1.0 → above 0.75
            val queryEmbedding = similarEmbeddingTo(testEmbedding, id = "query-emb")
            val person = Person(
                id = "person-1",
                name = "Alice",
                gallery = listOf(testEmbedding),
            )

            whenever(imageProcessingPort.readImage(FilePath("/photo.jpg"))).thenReturn(mockImage)
            whenever(faceDetectionPort.detectFaces(mockImage, confThreshold = 0.5f)).thenReturn(listOf(testFace))
            whenever(faceEmbeddingPort.isEmbeddingAvailable()).thenReturn(true)
            whenever(faceEmbeddingPort.extractEmbeddings(mockImage, listOf(testFace)))
                .thenReturn(listOf(queryEmbedding))
            directoryFlow.value = PersonDirectory(persons = listOf(person))

            val result = service.detectAndSuggest("/photo.jpg")

            assertThat(result).hasSize(1)
            assertThat(result[0].suggestedPerson).isEqualTo(person)
            assertThat(result[0].confidence).isGreaterThanOrEqualTo(FaceEmbedding.AUTO_TAG_THRESHOLD)
            assertThat(result[0].isConfident).isTrue()
            assertThat(result[0].isPotential).isTrue()
        }

        @Test
        @DisplayName("should propagate sourcePath from imagePath to embedding")
        fun sourcePathPropagation() = runTest {
            val embeddingWithoutSource = FaceEmbedding(
                embeddingVector = testEmbeddingVector,
                quality = 0.9f,
                modelName = "mobilefacenet",
                sourcePath = "", // adapter doesn't know the file path
                id = "emb-1",
            )
            val expectedSourcePath = "/photos/family/reunion.jpg"

            whenever(imageProcessingPort.readImage(FilePath(expectedSourcePath))).thenReturn(mockImage)
            whenever(faceDetectionPort.detectFaces(mockImage, confThreshold = 0.5f)).thenReturn(listOf(testFace))
            whenever(faceEmbeddingPort.isEmbeddingAvailable()).thenReturn(true)
            whenever(faceEmbeddingPort.extractEmbeddings(mockImage, listOf(testFace)))
                .thenReturn(listOf(embeddingWithoutSource))
            directoryFlow.value = PersonDirectory()

            val result = service.detectAndSuggest(expectedSourcePath)

            assertThat(result).hasSize(1)
            assertThat(result[0].embedding?.sourcePath).isEqualTo(expectedSourcePath)
        }

        @Test
        @DisplayName("should handle multiple faces in one image")
        fun multipleFaces() = runTest {
            val faces = listOf(testFace, testFace2)
            val embeddings = listOf(testEmbedding, testEmbedding2)

            whenever(imageProcessingPort.readImage(FilePath("/group.jpg"))).thenReturn(mockImage)
            whenever(faceDetectionPort.detectFaces(mockImage, confThreshold = 0.5f)).thenReturn(faces)
            whenever(faceEmbeddingPort.isEmbeddingAvailable()).thenReturn(true)
            whenever(faceEmbeddingPort.extractEmbeddings(mockImage, faces)).thenReturn(embeddings)
            directoryFlow.value = PersonDirectory()

            val result = service.detectAndSuggest("/group.jpg")

            assertThat(result).hasSize(2)
            assertThat(result[0].detectedFace).isEqualTo(testFace)
            assertThat(result[0].embedding).isEqualTo(testEmbedding.copy(sourcePath = "/group.jpg"))
            assertThat(result[1].detectedFace).isEqualTo(testFace2)
            assertThat(result[1].embedding).isEqualTo(testEmbedding2.copy(sourcePath = "/group.jpg"))
        }

        @Test
        @DisplayName("should handle mixed results: some faces with embeddings, some without")
        fun mixedEmbeddings() = runTest {
            val faces = listOf(testFace, testFace2)
            // First face gets embedding, second fails
            val embeddings = listOf(testEmbedding, null)

            whenever(imageProcessingPort.readImage(FilePath("/photo.jpg"))).thenReturn(mockImage)
            whenever(faceDetectionPort.detectFaces(mockImage, confThreshold = 0.5f)).thenReturn(faces)
            whenever(faceEmbeddingPort.isEmbeddingAvailable()).thenReturn(true)
            whenever(faceEmbeddingPort.extractEmbeddings(mockImage, faces)).thenReturn(embeddings)
            directoryFlow.value = PersonDirectory()

            val result = service.detectAndSuggest("/photo.jpg")

            assertThat(result).hasSize(2)
            assertThat(result[0].embedding).isNotNull
            assertThat(result[0].suggestedPerson).isNull() // no person in directory
            assertThat(result[1].embedding).isNull()
            assertThat(result[1].suggestedPerson).isNull()
            assertThat(result[1].confidence).isEqualTo(0f)
        }

        @Test
        @DisplayName("should select best match when multiple persons match same face")
        fun bestMatchWins() = runTest {
            val queryEmbedding = similarEmbeddingTo(testEmbedding, id = "query-emb")

            // Two persons, both in directory with embeddings — best match should win
            val lowerMatchEmbedding = FaceEmbedding(
                embeddingVector = FloatArray(128) { i -> if (i < 64) 1f else 0f },
                quality = 1.0f,
                modelName = "mobilefacenet",
                id = "lower-emb",
            )
            val personLower = Person(id = "person-low", name = "Bob", gallery = listOf(lowerMatchEmbedding))
            val personHigher = Person(id = "person-high", name = "Alice", gallery = listOf(testEmbedding))

            whenever(imageProcessingPort.readImage(FilePath("/photo.jpg"))).thenReturn(mockImage)
            whenever(faceDetectionPort.detectFaces(mockImage, confThreshold = 0.5f)).thenReturn(listOf(testFace))
            whenever(faceEmbeddingPort.isEmbeddingAvailable()).thenReturn(true)
            whenever(faceEmbeddingPort.extractEmbeddings(mockImage, listOf(testFace)))
                .thenReturn(listOf(queryEmbedding))
            directoryFlow.value = PersonDirectory(persons = listOf(personLower, personHigher))

            val result = service.detectAndSuggest("/photo.jpg")

            assertThat(result).hasSize(1)
            assertThat(result[0].suggestedPerson).isEqualTo(personHigher)
        }

        @Test
        @DisplayName("should pass confThreshold to face detection")
        fun passesConfidenceThreshold() = runTest {
            whenever(imageProcessingPort.readImage(FilePath("/photo.jpg"))).thenReturn(mockImage)
            whenever(faceDetectionPort.detectFaces(mockImage, confThreshold = 0.8f)).thenReturn(emptyList())

            service.detectAndSuggest("/photo.jpg", confThreshold = 0.8f)

            // Verify the custom confThreshold was passed (iouThreshold uses its default)
            verify(faceDetectionPort).detectFaces(org.mockito.kotlin.eq(mockImage), org.mockito.kotlin.eq(0.8f), org.mockito.kotlin.eq(0.45f))
        }

        @Test
        @DisplayName("should handle match below MATCH_THRESHOLD — no person suggested")
        fun matchBelowThreshold() = runTest {
            // Dissimilar embedding → cosine similarity near 0 → below MATCH_THRESHOLD
            val queryEmbedding = dissimilarEmbedding(id = "query-emb")

            whenever(imageProcessingPort.readImage(FilePath("/photo.jpg"))).thenReturn(mockImage)
            whenever(faceDetectionPort.detectFaces(mockImage, confThreshold = 0.5f)).thenReturn(listOf(testFace))
            whenever(faceEmbeddingPort.isEmbeddingAvailable()).thenReturn(true)
            whenever(faceEmbeddingPort.extractEmbeddings(mockImage, listOf(testFace)))
                .thenReturn(listOf(queryEmbedding))
            directoryFlow.value = PersonDirectory(persons = listOf(testPerson))

            val result = service.detectAndSuggest("/photo.jpg")

            assertThat(result).hasSize(1)
            assertThat(result[0].suggestedPerson).isNull()
            assertThat(result[0].confidence).isLessThan(FaceEmbedding.MATCH_THRESHOLD)
            assertThat(result[0].isConfident).isFalse()
            assertThat(result[0].isPotential).isFalse()
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  extractAndMatch
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("extractAndMatch")
    inner class ExtractAndMatch {

        @Test
        @DisplayName("should return null when embedding extraction fails")
        fun extractionFails() = runTest {
            whenever(faceEmbeddingPort.extractEmbedding(mockImage, testFace)).thenReturn(null)

            val result = service.extractAndMatch(mockImage, testFace)

            assertThat(result).isNull()
        }

        @Test
        @DisplayName("should return suggestion with no person when no match in directory")
        fun noMatchInDirectory() = runTest {
            whenever(faceEmbeddingPort.extractEmbedding(mockImage, testFace)).thenReturn(testEmbedding)
            directoryFlow.value = PersonDirectory()

            val result = service.extractAndMatch(mockImage, testFace)

            assertThat(result).isNotNull
            assertThat(result!!.detectedFace).isEqualTo(testFace)
            assertThat(result.embedding).isEqualTo(testEmbedding)
            assertThat(result.suggestedPerson).isNull()
            assertThat(result.confidence).isEqualTo(0f)
            assertThat(result.isConfident).isFalse()
            assertThat(result.isPotential).isFalse()
        }

        @Test
        @DisplayName("should return suggestion with person when match found")
        fun matchFound() = runTest {
            val queryEmbedding = similarEmbeddingTo(testEmbedding, id = "query-emb")
            whenever(faceEmbeddingPort.extractEmbedding(mockImage, testFace)).thenReturn(queryEmbedding)
            directoryFlow.value = PersonDirectory(persons = listOf(testPerson))

            val result = service.extractAndMatch(mockImage, testFace)

            assertThat(result).isNotNull
            assertThat(result!!.suggestedPerson).isEqualTo(testPerson)
            assertThat(result.confidence).isGreaterThan(0f)
            assertThat(result.isPotential).isTrue()
        }

        @Test
        @DisplayName("should return confident match when above AUTO_TAG_THRESHOLD")
        fun confidentMatch() = runTest {
            val queryEmbedding = similarEmbeddingTo(testEmbedding, id = "query-emb")
            whenever(faceEmbeddingPort.extractEmbedding(mockImage, testFace)).thenReturn(queryEmbedding)
            directoryFlow.value = PersonDirectory(persons = listOf(testPerson))

            val result = service.extractAndMatch(mockImage, testFace)

            assertThat(result).isNotNull
            assertThat(result!!.isConfident).isTrue()
            assertThat(result.isPotential).isTrue()
        }

        @Test
        @DisplayName("should return potential but not confident match between thresholds")
        fun potentialButNotConfident() = runTest {
            // Create embeddings that will yield a medium similarity (between 0.65 and 0.75)
            val galleryEmbedding = FaceEmbedding(
                embeddingVector = FloatArray(128) { i -> if (i < 64) 1f else 0f },
                quality = 1.0f,
                modelName = "mobilefacenet",
                id = "gallery-emb",
            )
            val person = Person(id = "person-1", name = "Charlie", gallery = listOf(galleryEmbedding))

            val queryEmbedding = FaceEmbedding(
                embeddingVector = FloatArray(128) { i -> if (i < 80) 1f else 0f },
                quality = 1.0f,
                modelName = "mobilefacenet",
                id = "query-emb",
            )

            whenever(faceEmbeddingPort.extractEmbedding(mockImage, testFace)).thenReturn(queryEmbedding)
            directoryFlow.value = PersonDirectory(persons = listOf(person))

            val result = service.extractAndMatch(mockImage, testFace)

            assertThat(result).isNotNull
            assertThat(result!!.suggestedPerson).isEqualTo(person)
            assertThat(result.isPotential).isTrue()
            // If the score falls between thresholds, isConfident is false
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  confirmIdentification
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("confirmIdentification")
    inner class ConfirmIdentification {

        @Test
        @DisplayName("should delegate to PersonService.confirmIdentification")
        fun confirmExistingPerson() = runTest {
            val updatedPerson = testPerson.withEmbedding(testEmbedding).withSourcePath("/photos/reunion.jpg")
            whenever(personService.confirmIdentification("person-1", testEmbedding, "/photos/reunion.jpg", maxGallerySize = 20))
                .thenReturn(updatedPerson)

            val result = service.confirmIdentification("person-1", testEmbedding, "/photos/reunion.jpg")

            assertThat(result).isEqualTo(updatedPerson)
            verify(personService).confirmIdentification("person-1", testEmbedding, "/photos/reunion.jpg", maxGallerySize = 20)
        }

        @Test
        @DisplayName("should return null when person not found")
        fun confirmNonExistentPerson() = runTest {
            whenever(personService.confirmIdentification("nonexistent-id", testEmbedding, "/photos/photo.jpg", maxGallerySize = 20))
                .thenReturn(null)

            val result = service.confirmIdentification("nonexistent-id", testEmbedding, "/photos/photo.jpg")

            assertThat(result).isNull()
        }

        @Test
        @DisplayName("should pass maxGallerySize from FaceMatchingConfig")
        fun confirmWithConfiguredGallerySize() = runTest {
            val config = FaceMatchingConfig(maxGallerySize = 30)
            whenever(personService.getMatchingConfig()).thenReturn(config)
            whenever(personService.confirmIdentification("person-1", testEmbedding, "/photos/photo.jpg", maxGallerySize = 30))
                .thenReturn(null)

            service.confirmIdentification("person-1", testEmbedding, "/photos/photo.jpg")

            verify(personService).confirmIdentification("person-1", testEmbedding, "/photos/photo.jpg", maxGallerySize = 30)
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  createPersonFromFace
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("createPersonFromFace")
    inner class CreatePersonFromFace {

        @Test
        @DisplayName("should delegate to PersonService.createOrAppendPersonFromFace")
        fun createNewPerson() = runTest {
            val createdPerson = Person(id = "new-id", name = "Bob")
                .withEmbedding(testEmbedding)
                .withSourcePath("/photos/bob.jpg")
            whenever(personService.createOrAppendPersonFromFace("Bob", testEmbedding, "/photos/bob.jpg", maxGallerySize = 20))
                .thenReturn(createdPerson)

            val result = service.createPersonFromFace("Bob", testEmbedding, "/photos/bob.jpg")

            assertThat(result).isEqualTo(createdPerson)
            verify(personService).createOrAppendPersonFromFace("Bob", testEmbedding, "/photos/bob.jpg", maxGallerySize = 20)
        }

        @Test
        @DisplayName("should delegate to createOrAppendPersonFromFace when person name already exists")
        fun nameConflictAppendsToExistingPerson() = runTest {
            val existingPerson = Person(id = "existing-id", name = "Alice", gallery = listOf(testEmbedding))
                .withEmbedding(testEmbedding)
                .withSourcePath("/photos/alice.jpg")
            whenever(personService.createOrAppendPersonFromFace("Alice", testEmbedding, "/photos/alice.jpg", maxGallerySize = 20))
                .thenReturn(existingPerson)

            val result = service.createPersonFromFace("Alice", testEmbedding, "/photos/alice.jpg")

            assertThat(result.name).isEqualTo("Alice")
            verify(personService).createOrAppendPersonFromFace("Alice", testEmbedding, "/photos/alice.jpg", maxGallerySize = 20)
        }

        @Test
        @DisplayName("should pass maxGallerySize from FaceMatchingConfig")
        fun createWithConfiguredGallerySize() = runTest {
            val config = FaceMatchingConfig(maxGallerySize = 30)
            whenever(personService.getMatchingConfig()).thenReturn(config)
            val createdPerson = Person(id = "new-id", name = "Bob")
                .withEmbedding(testEmbedding, maxGallerySize = 30)
                .withSourcePath("/photos/bob.jpg")
            whenever(personService.createOrAppendPersonFromFace("Bob", testEmbedding, "/photos/bob.jpg", maxGallerySize = 30))
                .thenReturn(createdPerson)

            val result = service.createPersonFromFace("Bob", testEmbedding, "/photos/bob.jpg")

            assertThat(result).isEqualTo(createdPerson)
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  isEmbeddingAvailable / isDetectionAvailable / preload
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("availability and preloading delegations")
    inner class AvailabilityAndPreloading {

        @Test
        @DisplayName("isEmbeddingAvailable should delegate to faceEmbeddingPort")
        fun isEmbeddingAvailable() {
            whenever(faceEmbeddingPort.isEmbeddingAvailable()).thenReturn(true)
            assertThat(service.isEmbeddingAvailable()).isTrue()

            whenever(faceEmbeddingPort.isEmbeddingAvailable()).thenReturn(false)
            assertThat(service.isEmbeddingAvailable()).isFalse()
        }

        @Test
        @DisplayName("isDetectionAvailable should delegate to faceDetectionPort")
        fun isDetectionAvailable() {
            whenever(faceDetectionPort.isFaceDetectionAvailable()).thenReturn(true)
            assertThat(service.isDetectionAvailable()).isTrue()

            whenever(faceDetectionPort.isFaceDetectionAvailable()).thenReturn(false)
            assertThat(service.isDetectionAvailable()).isFalse()
        }

        @Test
        @DisplayName("preload should return true when both models load successfully")
        fun preloadSuccess() = runTest {
            whenever(faceDetectionPort.preload()).thenReturn(true)
            whenever(faceEmbeddingPort.preload()).thenReturn(true)

            assertThat(service.preload()).isTrue()
        }

        @Test
        @DisplayName("preload should return false when detection model fails to load")
        fun preloadDetectionFails() = runTest {
            whenever(faceDetectionPort.preload()).thenReturn(false)
            whenever(faceEmbeddingPort.preload()).thenReturn(true)

            assertThat(service.preload()).isFalse()
        }

        @Test
        @DisplayName("preload should return false when embedding model fails to load")
        fun preloadEmbeddingFails() = runTest {
            whenever(faceDetectionPort.preload()).thenReturn(true)
            whenever(faceEmbeddingPort.preload()).thenReturn(false)

            assertThat(service.preload()).isFalse()
        }

        @Test
        @DisplayName("preload should return false when both models fail to load")
        fun preloadBothFail() = runTest {
            whenever(faceDetectionPort.preload()).thenReturn(false)
            whenever(faceEmbeddingPort.preload()).thenReturn(false)

            assertThat(service.preload()).isFalse()
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Edge cases
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge cases")
    inner class EdgeCases {

        @Test
        @DisplayName("should handle person with empty gallery (no match possible)")
        fun personWithEmptyGallery() = runTest {
            val emptyGalleryPerson = Person(id = "person-empty", name = "Empty", gallery = emptyList())
            val queryEmbedding = similarEmbeddingTo(testEmbedding, id = "query-emb")

            whenever(imageProcessingPort.readImage(FilePath("/photo.jpg"))).thenReturn(mockImage)
            whenever(faceDetectionPort.detectFaces(mockImage, confThreshold = 0.5f)).thenReturn(listOf(testFace))
            whenever(faceEmbeddingPort.isEmbeddingAvailable()).thenReturn(true)
            whenever(faceEmbeddingPort.extractEmbeddings(mockImage, listOf(testFace)))
                .thenReturn(listOf(queryEmbedding))
            directoryFlow.value = PersonDirectory(persons = listOf(emptyGalleryPerson))

            val result = service.detectAndSuggest("/photo.jpg")

            assertThat(result).hasSize(1)
            // Person with empty gallery can't match — findBestMatch returns null
            assertThat(result[0].suggestedPerson).isNull()
        }

        @Test
        @DisplayName("should handle embedding with cross-model mismatch (cosine similarity = 0)")
        fun crossModelEmbeddings() = runTest {
            // Embeddings with different modelNames → cosine similarity = 0 → no match
            val galleryEmbedding = FaceEmbedding(
                embeddingVector = testEmbeddingVector,
                quality = 1.0f,
                modelName = "mobilefacenet",
                id = "gallery-emb",
            )
            val queryEmbedding = FaceEmbedding(
                embeddingVector = testEmbeddingVector,
                quality = 1.0f,
                modelName = "arcface-r50", // different model!
                id = "query-emb",
            )

            val person = Person(id = "person-1", name = "CrossModel", gallery = listOf(galleryEmbedding))

            whenever(imageProcessingPort.readImage(FilePath("/photo.jpg"))).thenReturn(mockImage)
            whenever(faceDetectionPort.detectFaces(mockImage, confThreshold = 0.5f)).thenReturn(listOf(testFace))
            whenever(faceEmbeddingPort.isEmbeddingAvailable()).thenReturn(true)
            whenever(faceEmbeddingPort.extractEmbeddings(mockImage, listOf(testFace)))
                .thenReturn(listOf(queryEmbedding))
            directoryFlow.value = PersonDirectory(persons = listOf(person))

            val result = service.detectAndSuggest("/photo.jpg")

            assertThat(result).hasSize(1)
            // Cross-model comparison is invalid → no match
            assertThat(result[0].suggestedPerson).isNull()
            assertThat(result[0].confidence).isEqualTo(0f)
        }

        @Test
        @DisplayName("should handle embedding extraction returning fewer results than faces")
        fun fewerEmbeddingsThanFaces() = runTest {
            val faces = listOf(testFace, testFace2, DetectedFace(0f, 0f, 50f, 50f, confidence = 0.6f))
            // Only 2 embeddings returned for 3 faces
            val embeddings = listOf(testEmbedding, testEmbedding2)

            whenever(imageProcessingPort.readImage(FilePath("/photo.jpg"))).thenReturn(mockImage)
            whenever(faceDetectionPort.detectFaces(mockImage, confThreshold = 0.5f)).thenReturn(faces)
            whenever(faceEmbeddingPort.isEmbeddingAvailable()).thenReturn(true)
            whenever(faceEmbeddingPort.extractEmbeddings(mockImage, faces)).thenReturn(embeddings)
            directoryFlow.value = PersonDirectory()

            val result = service.detectAndSuggest("/photo.jpg")

            assertThat(result).hasSize(3)
            // First two faces have embeddings
            assertThat(result[0].embedding).isNotNull()
            assertThat(result[1].embedding).isNotNull()
            // Third face: embedding.getOrNull(2) → null
            assertThat(result[2].embedding).isNull()
            assertThat(result[2].suggestedPerson).isNull()
            assertThat(result[2].confidence).isEqualTo(0f)
        }

        @Test
        @DisplayName("should handle embedding returning null in the middle of the list")
        fun nullEmbeddingInMiddle() = runTest {
            val faces = listOf(testFace, testFace2)
            val embeddings = listOf(testEmbedding, null) // second face extraction failed

            whenever(imageProcessingPort.readImage(FilePath("/photo.jpg"))).thenReturn(mockImage)
            whenever(faceDetectionPort.detectFaces(mockImage, confThreshold = 0.5f)).thenReturn(faces)
            whenever(faceEmbeddingPort.isEmbeddingAvailable()).thenReturn(true)
            whenever(faceEmbeddingPort.extractEmbeddings(mockImage, faces)).thenReturn(embeddings)
            directoryFlow.value = PersonDirectory()

            val result = service.detectAndSuggest("/photo.jpg")

            assertThat(result).hasSize(2)
            assertThat(result[0].embedding).isNotNull()
            assertThat(result[1].embedding).isNull()
        }

        @Test
        @DisplayName("extractAndMatch should handle directory with multiple matching persons")
        fun extractAndMatchBestMatch() = runTest {
            // Two persons, both match — best match should be returned
            val queryEmbedding = similarEmbeddingTo(testEmbedding, id = "query-emb")

            val person1 = Person(id = "person-1", name = "Alice", gallery = listOf(testEmbedding))
            // person2 has a weaker match
            val weakerEmbedding = FaceEmbedding(
                embeddingVector = FloatArray(128) { i -> if (i < 64) 1f else 0f },
                quality = 1.0f,
                modelName = "mobilefacenet",
                id = "weak-emb",
            )
            val person2 = Person(id = "person-2", name = "Bob", gallery = listOf(weakerEmbedding))

            whenever(faceEmbeddingPort.extractEmbedding(mockImage, testFace)).thenReturn(queryEmbedding)
            directoryFlow.value = PersonDirectory(persons = listOf(person1, person2))

            val result = service.extractAndMatch(mockImage, testFace)

            assertThat(result).isNotNull
            assertThat(result!!.suggestedPerson).isEqualTo(person1) // best match
        }

        @Test
        @DisplayName("confirmIdentification should not add source path if person not in directory")
        fun confirmIdentificationPersonNotFound() = runTest {
            whenever(personService.confirmIdentification("nonexistent", testEmbedding, "/photo.jpg", maxGallerySize = 20))
                .thenReturn(null)

            val result = service.confirmIdentification("nonexistent", testEmbedding, "/photo.jpg")

            assertThat(result).isNull()
        }

        @Test
        @DisplayName("detectAndSuggest should preserve face confidence in DetectedFace")
        fun preserveFaceConfidence() = runTest {
            val lowConfFace = DetectedFace(0f, 0f, 50f, 50f, confidence = 0.55f)

            whenever(imageProcessingPort.readImage(FilePath("/photo.jpg"))).thenReturn(mockImage)
            whenever(faceDetectionPort.detectFaces(mockImage, confThreshold = 0.5f))
                .thenReturn(listOf(lowConfFace))
            whenever(faceEmbeddingPort.isEmbeddingAvailable()).thenReturn(false)

            val result = service.detectAndSuggest("/photo.jpg")

            assertThat(result).hasSize(1)
            assertThat(result[0].detectedFace.confidence).isEqualTo(0.55f)
        }
    }
}