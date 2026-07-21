package org.kryspetrie.fileimport.application

import org.kryspetrie.fileimport.domain.model.FaceEmbedding
import org.kryspetrie.fileimport.domain.model.FaceSuggestion
import org.kryspetrie.fileimport.domain.model.FaceMatchingConfig
import org.kryspetrie.fileimport.domain.model.MatchResult
import org.kryspetrie.fileimport.domain.model.Person
import org.kryspetrie.fileimport.domain.model.PersonDirectory
import org.kryspetrie.fileimport.domain.model.ProcessedImage
import org.kryspetrie.fileimport.domain.port.DetectedFace
import org.kryspetrie.fileimport.domain.port.FaceDetectionPort
import org.kryspetrie.fileimport.domain.port.FaceEmbeddingPort
import org.kryspetrie.fileimport.domain.port.ImageProcessingPort
import org.kryspetrie.fileimport.domain.model.FilePath

/**
 * Application service for detecting faces, extracting embeddings, and matching
 * against the person directory.
 *
 * Orchestrates the full face identification pipeline:
 * 1. **Detect**: [FaceDetectionPort.detectFaces] → `List<DetectedFace>` (bounding boxes)
 * 2. **Embed**: [FaceEmbeddingPort.extractEmbeddings] → `List<FaceEmbedding?>` (128-dim vectors)
 * 3. **Match**: [PersonDirectory.findBestMatch] → `Person?` (known person or null)
 * 4. **Suggest**: Group all matches with confidence scores for UI auto-complete
 *
 * ## Progressive Gallery Enrichment
 *
 * When a user confirms a face identification, the embedding is added to that person's gallery
 * in the [PersonService]. Over time, the gallery accumulates embeddings from different angles,
 * expressions, and lighting conditions, improving recognition accuracy.
 *
 * ## Thread Safety
 *
 * All person directory mutations ([confirmIdentification], [createPersonFromFace]) delegate to
 * [PersonService]'s mutex-protected atomic methods, preventing lost-update race conditions when
 * multiple coroutines modify the same person concurrently.
 *
 * ## Configurable Thresholds
 *
 * Matching thresholds (match threshold, auto-tag threshold, max gallery size) are configurable
 * via [FaceMatchingConfig] in app settings. All matching operations read the current config,
 * allowing per-user tuning without code changes.
 *
 * @see FaceDetectionPort
 * @see FaceEmbeddingPort
 * @see PersonService
 */
class FaceGroupingService(
    private val faceDetectionPort: FaceDetectionPort,
    private val faceEmbeddingPort: FaceEmbeddingPort,
    private val personService: PersonService,
    private val imageProcessingPort: ImageProcessingPort,
) {

    /**
     * Maximum number of alternative person suggestions to include in [FaceSuggestion.alternativeMatches].
     * Keeps the suggestion list manageable — more than 3 alternatives is overwhelming for users.
     */
    companion object {
        const val MAX_ALTERNATIVE_MATCHES = 3
    }

    /**
     * Detect faces in an image and extract embeddings for each detected face.
     *
     * This is the primary entry point for face identification. It runs the full
     * detect → embed pipeline and returns auto-suggestions from the person directory.
     *
     * @param imagePath Path to the source image file.
     * @param confThreshold Minimum detection confidence (default 0.5).
     * @return A list of face suggestions, one per detected face.
     */
    suspend fun detectAndSuggest(
        imagePath: String,
        confThreshold: Float = 0.5f,
    ): List<FaceSuggestion> {
        val image = imageProcessingPort.readImage(FilePath(imagePath)) ?: return emptyList()
        return detectAndSuggest(image, imagePath, confThreshold)
    }

    /**
     * Detect faces in a pre-loaded image and extract embeddings for each detected face.
     *
     * Use this overload when the image is already loaded (e.g., in the UI) to avoid
     * redundant disk I/O. The [imagePath] is used as metadata on the embedding (sourcePath),
     * not for loading the image.
     */
    suspend fun detectAndSuggest(
        image: ProcessedImage,
        imagePath: String,
        confThreshold: Float = 0.5f,
    ): List<FaceSuggestion> {
        val detectedFaces = faceDetectionPort.detectFaces(image, confThreshold = confThreshold)
        if (detectedFaces.isEmpty()) return emptyList()

        val embeddings = if (faceEmbeddingPort.isEmbeddingAvailable()) {
            faceEmbeddingPort.extractEmbeddings(image, detectedFaces)
        } else {
            detectedFaces.map { null }
        }

        // Read config and directory atomically to avoid TOCTOU inconsistency.
        // Both reads happen sequentially before any matching, so the snapshot is consistent.
        val config = personService.getMatchingConfig()
        val directory = personService.directory.value

        return detectedFaces.mapIndexed { index, face ->
            val embedding = embeddings.getOrNull(index)?.copy(sourcePath = imagePath)
            val matchResult: MatchResult? = if (embedding != null) {
                directory.findBestMatchWithScore(embedding, threshold = config.matchThreshold)
            } else {
                null
            }

            // Collect alternative matches for "Did you mean…?" UX.
            // Show top matches with score >= matchThreshold, excluding the best match.
            val alternatives: List<Pair<Person, Float>> = if (embedding != null && matchResult != null) {
                directory.findAllMatches(embedding, threshold = config.matchThreshold)
                    .filter { it.first.id != matchResult.person.id }
                    .take(MAX_ALTERNATIVE_MATCHES)
            } else if (embedding != null && matchResult == null) {
                // No match above threshold — still show potential matches below threshold as hints
                directory.findAllMatches(embedding, threshold = config.matchThreshold * 0.9f)
                    .take(MAX_ALTERNATIVE_MATCHES)
            } else {
                emptyList()
            }

            FaceSuggestion.createWithAlternatives(
                detectedFace = face,
                embedding = embedding,
                suggestedPerson = matchResult?.person,
                confidence = matchResult?.score ?: 0f,
                matchThreshold = config.matchThreshold,
                autoTagThreshold = config.autoTagThreshold,
                alternativeMatches = alternatives,
            )
        }
    }

    /**
     * Extract an embedding for a single detected face and find matching persons.
     *
     * Used when a user manually selects a face region and wants auto-suggestions.
     *
     * @param image The source image containing the face.
     * @param face The detected face region to extract an embedding from.
     * @param sourcePath The file path of the source image. Stored on the embedding for gallery
     *   deduplication and source tracking. Must be provided — an empty sourcePath would break
     *   gallery dedup logic in [Person.withEmbedding].
     */
    suspend fun extractAndMatch(
        image: ProcessedImage,
        face: DetectedFace,
        sourcePath: String = "",
    ): FaceSuggestion? {
        val rawEmbedding = faceEmbeddingPort.extractEmbedding(image, face) ?: return null
        val embedding = if (sourcePath.isNotEmpty()) rawEmbedding.copy(sourcePath = sourcePath) else rawEmbedding

        // Read config and directory atomically to avoid TOCTOU inconsistency
        val config = personService.getMatchingConfig()
        val directory = personService.directory.value
        val matchResult = directory.findBestMatchWithScore(embedding, threshold = config.matchThreshold)

        return FaceSuggestion.create(
            detectedFace = face,
            embedding = embedding,
            suggestedPerson = matchResult?.person,
            confidence = matchResult?.score ?: 0f,
            matchThreshold = config.matchThreshold,
            autoTagThreshold = config.autoTagThreshold,
        )
    }

    /**
     * Confirm a face identification and add the embedding to the person's gallery.
     *
     * Delegates to [PersonService.confirmIdentification] for atomic, mutex-protected
     * read-modify-write, preventing lost-update race conditions.
     *
     * @return The updated person, or null if the person was not found.
     */
    suspend fun confirmIdentification(
        personId: String,
        embedding: FaceEmbedding,
        sourcePath: String,
    ): Person? {
        val config = personService.getMatchingConfig()
        return personService.confirmIdentification(
            personId, embedding, sourcePath, maxGallerySize = config.maxGallerySize,
        )
    }

    /**
     * Create a new person from a confirmed face identification, or append to an existing person.
     *
     * Delegates to [PersonService.createOrAppendPersonFromFace] which performs the entire
     * create-or-append sequence atomically under [PersonService]'s mutation mutex. This eliminates
     * the race condition where two concurrent calls could both fail on create, then both try to
     * find-and-update the same person, causing a lost-update on the gallery.
     *
     * @return The newly created or updated person.
     */
    suspend fun createPersonFromFace(
        name: String,
        embedding: FaceEmbedding,
        sourcePath: String,
    ): Person {
        val config = personService.getMatchingConfig()
        return personService.createOrAppendPersonFromFace(
            name, embedding, sourcePath, maxGallerySize = config.maxGallerySize,
        )
    }

    /**
     * Auto-detect faces in a list of imported images and add them to the person directory.
     *
     * Called after image import when [AppSettings.autoDetectFacesOnImport] is enabled.
     * For each image, detects faces and extracts embeddings. For confident matches (above
     * auto-tag threshold), automatically adds the embedding to the matched person's gallery.
     * For potential matches (above match threshold but below auto-tag), creates a person entry
     * but does not auto-tag — the user can review and confirm later.
     *
     * Errors for individual images are logged but do not fail the entire batch.
     *
     * @param imagePaths Paths to imported images.
     * @return The number of images that had faces successfully detected and processed.
     */
    suspend fun autoDetectFacesForImports(imagePaths: List<String>): Int {
        if (!faceDetectionPort.isFaceDetectionAvailable()) return 0
        val config = personService.getMatchingConfig()
        var processedCount = 0

        for (imagePath in imagePaths) {
            try {
                val image = imageProcessingPort.readImage(FilePath(imagePath)) ?: continue
                val suggestions = detectAndSuggest(image, imagePath, confThreshold = 0.5f)
                for (suggestion in suggestions) {
                    val embedding = suggestion.embedding ?: continue
                    if (suggestion.isConfident && suggestion.suggestedPerson != null) {
                        // Confident match: auto-tag and add to gallery
                        confirmIdentification(
                            suggestion.suggestedPerson.id,
                            embedding,
                            imagePath,
                        )
                    } else if (suggestion.isPotential && suggestion.suggestedPerson != null) {
                        // Potential match: add source path but don't auto-tag
                        personService.addSourcePath(suggestion.suggestedPerson.id, imagePath)
                    } else if (!suggestion.isPotential) {
                        // No match: create auto-named person entry for later review
                        val autoName = "Person ${suggestion.detectedFace.confidence}"
                        createPersonFromFace(autoName, embedding, imagePath)
                    }
                }
                processedCount++
            } catch (e: Exception) {
                // Individual image failure should not block remaining imports
                // Individual image failure should not block remaining imports
                // Logging is handled by the caller (MediaImportViewModel, WatchFolderService)
            }
        }
        return processedCount
    }

    /** Whether face embedding is available (model loaded and ready). */
    fun isEmbeddingAvailable(): Boolean = faceEmbeddingPort.isEmbeddingAvailable()

    /** Whether face detection is available (model loaded and ready). */
    fun isDetectionAvailable(): Boolean = faceDetectionPort.isFaceDetectionAvailable()

    /**
     * Pre-load both face detection and embedding models eagerly.
     * @return true if both models loaded successfully (or were already loaded).
     */
    suspend fun preload(): Boolean {
        val detectionLoaded = faceDetectionPort.preload()
        val embeddingLoaded = faceEmbeddingPort.preload()
        return detectionLoaded && embeddingLoaded
    }

    /**
     * Save a face thumbnail for a person and update their thumbnailPath.
     *
     * Crops the face from the source image using the detected face coordinates, resizes
     * to a fixed thumbnail size (96x96), and saves as JPEG. Updates the person's
     * [Person.thumbnailPath] to point to the saved file.
     *
     * Should be called after a face identification is confirmed — the caller has access
     * to the source image and detected face region.
     *
     * @return The path to the saved thumbnail, or null if saving fails.
     */
    suspend fun savePersonThumbnail(
        sourceImage: ProcessedImage,
        detectedFace: DetectedFace,
        personId: String,
        embeddingId: String,
    ): String? {
        val thumbnailPath = faceEmbeddingPort.saveFaceThumbnail(
            sourceImage, detectedFace, personId, embeddingId,
        ) ?: return null

        // Update the person's thumbnailPath
        val directory = personService.directory.value
        val person = directory.personById(personId) ?: return thumbnailPath
        personService.updatePerson(person.copy(thumbnailPath = thumbnailPath))

        return thumbnailPath
    }
}