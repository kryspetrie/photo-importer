package org.kryspetrie.fileimport.application

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.kryspetrie.fileimport.domain.model.FaceEmbedding
import org.kryspetrie.fileimport.domain.model.FaceMatchingConfig
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.Person
import org.kryspetrie.fileimport.domain.model.PersonDirectory
import org.kryspetrie.fileimport.domain.port.DispatcherProvider
import org.kryspetrie.fileimport.domain.port.FileSystemPort
import org.kryspetrie.fileimport.domain.port.PersonDirectoryPort
import org.kryspetrie.fileimport.domain.port.PersonDirectoryImportException
import org.kryspetrie.fileimport.domain.port.PlatformPort
import org.kryspetrie.fileimport.domain.port.SettingsPort

/**
 * Application service for managing the person directory.
 *
 * Provides CRUD operations on persons, face search, embedding management,
 * and database export/import as a zip bundle.
 *
 * ## Single Source of Truth
 *
 * This service is a thin coordinator — all state lives in [PersonDirectoryPort.observeDirectory].
 * There is no separate cached StateFlow here; consumers observe the port's StateFlow directly
 * via [directory]. This eliminates dual-state race conditions (issue #14).
 *
 * ## Thread Safety
 *
 * Read-then-write mutations ([addEmbeddingToPerson], [addSourcePath], [confirmIdentification],
 * [createPersonWithEmbedding]) are protected by [mutationMutex] to prevent lost updates when
 * multiple coroutines modify the same person concurrently.
 *
 * @see PersonDirectoryPort
 * @see PersonDirectory
 */
class PersonService(
    private val personDirectoryPort: PersonDirectoryPort,
    private val settingsPort: SettingsPort,
    private val fileSystem: FileSystemPort,
    private val dispatcherProvider: DispatcherProvider,
    private val platformPort: PlatformPort,
) {
    /**
     * Mutex protecting read-then-write mutations on the person directory.
     *
     * Without this, two concurrent calls to [addEmbeddingToPerson] on the same person
     * could read the same directory state, both apply their changes to that state,
     * and the second write would overwrite the first — losing the first embedding.
     *
     * This mutex ensures that read-then-write operations serialize: the second call
     * reads the directory state that already includes the first call's changes.
     */
    private val mutationMutex = Mutex()
    /**
     * Observable person directory state. Delegates to the adapter's StateFlow,
     * which is the single source of truth (no duplicate cache).
     */
    val directory: StateFlow<PersonDirectory> get() = personDirectoryPort.observeDirectory()

    // ── Person CRUD ──────────────────────────────────────────────────────

    /**
     * Create a new person with the given name.
     *
     * Validates the name and checks for duplicates before creating.
     * @return The created person.
     * @throws IllegalArgumentException if the name is invalid.
     * @throws IllegalStateException if a person with this name already exists.
     */
    suspend fun createPerson(name: String): Person = mutationMutex.withLock {
        val trimmedName = name.trim()
        val nameError = Person.validateName(trimmedName)
        require(nameError == null) { "Invalid person name: $nameError" }

        val currentDir = personDirectoryPort.loadDirectory()
        check(currentDir.isNameAvailable(trimmedName)) {
            "A person named \"$trimmedName\" already exists"
        }

        val person = Person(name = trimmedName)
        personDirectoryPort.upsertPerson(person)
        person
    }

    /**
     * Rename a person.
     *
     * Validates the new name and checks for duplicates (excluding the person being renamed).
     * Protected by [mutationMutex] to prevent name conflicts from concurrent operations.
     *
     * @throws IllegalArgumentException if the new name is invalid.
     * @throws IllegalStateException if another person already has the new name.
     */
    suspend fun renamePerson(personId: String, newName: String) = mutationMutex.withLock {
        val trimmedName = newName.trim()
        val nameError = Person.validateName(trimmedName)
        require(nameError == null) { "Invalid person name: $nameError" }

        val currentDir = personDirectoryPort.loadDirectory()
        check(currentDir.isNameAvailable(trimmedName, excludeId = personId)) {
            "A person named \"$trimmedName\" already exists"
        }

        personDirectoryPort.renamePerson(personId, trimmedName)
    }

    /**
     * Delete a person and all their embeddings.
     *
     * Protected by [mutationMutex] to prevent a concurrent read-then-write operation
     * (e.g., [addEmbeddingToPerson]) from resurrecting the deleted person by upserting
     * stale state that still includes them.
     */
    suspend fun deletePerson(personId: String) = mutationMutex.withLock {
        personDirectoryPort.deletePerson(personId)
    }

    /**
     * Update a person's data (gallery, source paths, etc.).
     *
     * Protected by [mutationMutex] to prevent interleaving with other read-then-write
     * mutations that could overwrite this update with stale state.
     */
    suspend fun updatePerson(person: Person) = mutationMutex.withLock {
        personDirectoryPort.upsertPerson(person)
    }

    /**
     * Merge two persons. The source person's data is absorbed into the target.
     *
     * Protected by [mutationMutex] because the adapter's mergePersons performs
     * read-then-write on the directory (loads, merges, saves).
     */
    suspend fun mergePersons(targetId: String, sourceId: String): Person = mutationMutex.withLock {
        personDirectoryPort.mergePersons(targetId, sourceId)
    }

    // ── Atomic Mutations (Mutex-protected) ──────────────────────────────

    /**
     * Atomically add an embedding to a person's gallery and persist.
     *
     * Protected by [mutationMutex] to prevent lost updates when multiple coroutines
     * modify the same person concurrently.
     *
     * @param personId The person to enrich.
     * @param embedding The face embedding to add.
     * @param maxGallerySize Maximum gallery capacity (from FaceMatchingConfig).
     * @return The updated person, or null if the person was not found.
     */
    suspend fun addEmbeddingToPerson(
        personId: String,
        embedding: FaceEmbedding,
        maxGallerySize: Int = Person.DEFAULT_MAX_GALLERY_SIZE,
    ): Person? = mutationMutex.withLock {
        val person = personDirectoryPort.loadDirectory().personById(personId) ?: return@withLock null
        val updated = person.withEmbedding(embedding, maxGallerySize = maxGallerySize)
        personDirectoryPort.upsertPerson(updated)
        updated
    }

    /**
     * Atomically confirm a face identification: add embedding to an existing person's gallery
     * and associate the source photo path. Single write operation.
     *
     * Protected by [mutationMutex] to prevent lost updates.
     *
     * @return The updated person, or null if the person was not found.
     */
    suspend fun confirmIdentification(
        personId: String,
        embedding: FaceEmbedding,
        sourcePath: String,
        maxGallerySize: Int = Person.DEFAULT_MAX_GALLERY_SIZE,
    ): Person? = mutationMutex.withLock {
        val person = personDirectoryPort.loadDirectory().personById(personId) ?: return@withLock null
        val updated = person
            .withEmbedding(embedding, maxGallerySize = maxGallerySize)
            .withSourcePath(sourcePath)
        personDirectoryPort.upsertPerson(updated)
        updated
    }

    /**
     * Atomically create a new person with an initial embedding and source path.
     *
     * Protected by [mutationMutex] to prevent the race between createPerson and updatePerson.
     *
     * @return The newly created person with embedding and source path.
     * @throws IllegalArgumentException if the name is invalid.
     * @throws IllegalStateException if a person with this name already exists.
     */
    suspend fun createPersonWithEmbedding(
        name: String,
        embedding: FaceEmbedding,
        sourcePath: String,
        maxGallerySize: Int = Person.DEFAULT_MAX_GALLERY_SIZE,
    ): Person = mutationMutex.withLock {
        val trimmedName = name.trim()
        val nameError = Person.validateName(trimmedName)
        require(nameError == null) { "Invalid person name: $nameError" }

        val currentDir = personDirectoryPort.loadDirectory()
        check(currentDir.isNameAvailable(trimmedName)) {
            "A person named \"$trimmedName\" already exists"
        }

        val person = Person(name = trimmedName)
            .withEmbedding(embedding, maxGallerySize = maxGallerySize)
            .withSourcePath(sourcePath)
        personDirectoryPort.upsertPerson(person)
        person
    }

    /**
     * Atomically create a new person from a face identification, or add the embedding
     * to an existing person with the same name if one already exists.
     *
     * This is the race-safe version of the create-or-append pattern. Without mutex protection,
     * two concurrent calls with the same name could both fail on [createPersonWithEmbedding],
     * then both try to find and update the existing person — causing a lost-update race on the
     * gallery. By holding [mutationMutex] for the entire create-or-append sequence, we guarantee
     * that the second caller sees the directory state that includes the first caller's changes.
     *
     * @return The newly created or updated person.
     * @throws IllegalArgumentException if the name is invalid.
     */
    suspend fun createOrAppendPersonFromFace(
        name: String,
        embedding: FaceEmbedding,
        sourcePath: String,
        maxGallerySize: Int = Person.DEFAULT_MAX_GALLERY_SIZE,
    ): Person = mutationMutex.withLock {
        val trimmedName = name.trim()
        val nameError = Person.validateName(trimmedName)
        require(nameError == null) { "Invalid person name: $nameError" }

        val currentDir = personDirectoryPort.loadDirectory()
        val existing = currentDir.personByName(trimmedName)
        if (existing != null) {
            // Person with this name already exists — append embedding to their gallery
            val updated = existing
                .withEmbedding(embedding, maxGallerySize = maxGallerySize)
                .withSourcePath(sourcePath)
            personDirectoryPort.upsertPerson(updated)
            updated
        } else {
            // No existing person — create new
            val person = Person(name = trimmedName)
                .withEmbedding(embedding, maxGallerySize = maxGallerySize)
                .withSourcePath(sourcePath)
            personDirectoryPort.upsertPerson(person)
            person
        }
    }

    // ── Face Search ──────────────────────────────────────────────────────

    /** Find all persons whose gallery matches the given embedding above threshold. */
    suspend fun findMatches(embedding: FaceEmbedding): List<Pair<Person, Float>> {
        return personDirectoryPort.findMatches(embedding)
    }

    /** Find the single best-matching person for an embedding. */
    suspend fun findBestMatch(embedding: FaceEmbedding): Person? {
        return personDirectoryPort.findMatch(embedding)
    }

    // ── Source Path Management ───────────────────────────────────────────

    /** Add a source photo path to a person. Protected by [mutationMutex]. */
    suspend fun addSourcePath(personId: String, path: String): Person? = mutationMutex.withLock {
        val person = personDirectoryPort.loadDirectory().personById(personId) ?: return@withLock null
        val updated = person.withSourcePath(path)
        personDirectoryPort.upsertPerson(updated)
        updated
    }

    /** Remove a source photo path from a person. Protected by [mutationMutex]. */
    suspend fun removeSourcePath(personId: String, path: String): Person? = mutationMutex.withLock {
        val person = personDirectoryPort.loadDirectory().personById(personId) ?: return@withLock null
        val updated = person.withoutSourcePath(path)
        personDirectoryPort.upsertPerson(updated)
        updated
    }

    // ── Find Images by Face ───────────────────────────────────────────────

    /**
     * Find all photo paths associated with a person (by name or ID).
     *
     * Returns paths of photos where this person has been tagged.
     */
    fun findImagesForPerson(personId: String, dir: PersonDirectory): List<String> {
        val person = dir.personById(personId) ?: return emptyList()
        return person.sourcePaths
    }

    /**
     * Validate source paths across all persons in the directory, removing paths that no longer exist.
     *
     * Call this periodically (e.g., on app start or when the People screen opens) to clean up
     * stale references to moved, renamed, or deleted photos.
     *
     * @return The number of stale paths removed across all persons.
     */
    suspend fun validateAndCleanSourcePaths(): Int = mutationMutex.withLock {
        val currentDir = personDirectoryPort.loadDirectory()
        var totalRemoved = 0
        val updatedPersons = currentDir.persons.map { person ->
            val validPaths = person.sourcePaths.filter { path ->
                fileSystem.exists(FilePath(path))
            }
            val removed = person.sourcePaths.size - validPaths.size
            totalRemoved += removed
            if (removed > 0) {
                person.copy(sourcePaths = validPaths, updatedAt = System.currentTimeMillis())
            } else {
                person
            }
        }
        if (totalRemoved > 0) {
            personDirectoryPort.saveDirectory(currentDir.copy(persons = updatedPersons))
        }
        totalRemoved
    }

    /**
     * Find all photo paths across all persons whose name matches [query] (case-insensitive).
     */
    fun searchPersonsByName(query: String, dir: PersonDirectory): List<Person> {
        if (query.isBlank()) return dir.persons
        return dir.persons.filter {
            it.name.contains(query, ignoreCase = true)
        }
    }

    /**
     * Search persons by name, source path, or keyword.
     *
     * This supports text-tag search: if [query] matches a person's name, they are returned.
     * If [query] matches any source path filename, the persons associated with those paths are returned.
     * This enables finding people by photo filename, folder name, or any text tag.
     */
    fun searchPersonsByKeyword(query: String, dir: PersonDirectory): List<Person> {
        if (query.isBlank()) return dir.persons
        val lowerQuery = query.lowercase()
        return dir.persons.filter { person ->
            // Match by person name
            person.name.contains(query, ignoreCase = true) ||
                // Match by source path (filename or directory name)
                person.sourcePaths.any { path ->
                    path.lowercase().contains(lowerQuery)
                }
        }
    }

    /**
     * Find all persons whose gallery contains an embedding from a specific source file.
     *
     * Useful for finding which persons appear in a given photo.
     */
    fun findPersonsBySourcePath(sourcePath: String, dir: PersonDirectory): List<Person> {
        if (sourcePath.isBlank()) return emptyList()
        return dir.persons.filter { person ->
            person.gallery.any { it.sourcePath == sourcePath } ||
                person.sourcePaths.contains(sourcePath)
        }
    }

    // ── Gallery Face Crops ────────────────────────────────────────────────

    /**
     * Get thumbnail face crop paths for a person.
     *
     * Returns the source paths of images used for this person's gallery embeddings.
     * The UI can use these to display face crops.
     *
     * TODO(#16): Integrate with ThumbnailGenerator to produce actual face crop thumbnails.
     * Currently returns source image paths; the UI should extract face crops using
     * FaceCrop and the NormalizedRect region from each embedding.
     */
    fun getGallerySources(personId: String, dir: PersonDirectory): List<String> {
        val person = dir.personById(personId) ?: return emptyList()
        return person.gallery.map { it.sourcePath }.distinct()
    }

    // ── Export / Import ───────────────────────────────────────────────────

    /**
     * Export the person directory as a zip bundle.
     *
     * Writes a single zip file containing `persons.json` to the specified output path.
     * Does NOT write a separate raw JSON file (issue #4: removed redundant write).
     *
     * @return The full path to the written zip file.
     */
    suspend fun exportDatabase(outputPath: String): String = withContext(dispatcherProvider.default) {
        val zipBytes = personDirectoryPort.exportDirectory()

        // Write zip bundle only (no redundant raw JSON file)
        val zipPath = "${outputPath.removeSuffix("/")}/people-database.zip"
        fileSystem.writeBytes(org.kryspetrie.fileimport.domain.model.FilePath(zipPath), zipBytes)

        zipPath
    }

    /**
     * Import a person directory from a zip bundle.
     *
     * Reads the zip, validates the directory (size limits, version check, name validation),
     * and replaces the current directory.
     *
     * Protected by [mutationMutex] to prevent a concurrent mutation from overwriting
     * the imported directory, or the import from overwriting a concurrent mutation.
     *
     * @throws PersonDirectoryImportException if the imported data fails validation.
     */
    suspend fun importDatabase(zipPath: String): PersonDirectory = withContext(dispatcherProvider.default) {
        mutationMutex.withLock {
            val zipBytes = fileSystem.readBytes(org.kryspetrie.fileimport.domain.model.FilePath(zipPath))
            val imported = personDirectoryPort.importDirectory(zipBytes)
            imported
        }
    }

    // ── Settings Toggles ──────────────────────────────────────────────────

    /** Whether auto-detect is enabled for a given import method. */
    suspend fun isAutoDetectEnabled(): Boolean {
        val settings = settingsPort.observeSettings().value
        return settings.autoDetectFacesOnImport
    }

    /** Set auto-detect enabled/disabled for import methods. */
    suspend fun setAutoDetectEnabled(enabled: Boolean) {
        val settings = settingsPort.observeSettings().value
        settingsPort.saveSettings(settings.copy(autoDetectFacesOnImport = enabled))
    }

    /** Whether auto-identify is enabled (face name suggestions from person directory). */
    suspend fun isAutoIdentifyEnabled(): Boolean {
        val settings = settingsPort.observeSettings().value
        return settings.autoIdentifyFaces
    }

    /** Set auto-identify enabled/disabled. */
    suspend fun setAutoIdentifyEnabled(enabled: Boolean) {
        val settings = settingsPort.observeSettings().value
        settingsPort.saveSettings(settings.copy(autoIdentifyFaces = enabled))
    }

    // ── Privacy Controls (issue #24) ──────────────────────────────────────

    /**
     * Remove all face embeddings for a specific person, keeping the person entry and source paths.
     *
     * GDPR "right to be forgotten" for biometric data — allows removing face embedding data
     * while preserving the person record and photo associations.
     * Protected by [mutationMutex] to prevent conflicts with concurrent gallery enrichment.
     */
    suspend fun clearEmbeddings(personId: String) = mutationMutex.withLock {
        val currentDir = personDirectoryPort.loadDirectory()
        val person = currentDir.personById(personId) ?: return@withLock
        val updated = person.copy(gallery = emptyList(), updatedAt = System.currentTimeMillis())
        personDirectoryPort.upsertPerson(updated)
    }

    /**
     * Remove a single embedding from a person's gallery by embedding ID.
     *
     * Allows undo of individual face identifications without clearing the entire gallery.
     * Protected by [mutationMutex] to prevent conflicts with concurrent gallery enrichment.
     *
     * @return The updated person, or null if the person or embedding was not found.
     */
    suspend fun removeEmbedding(personId: String, embeddingId: String): Person? = mutationMutex.withLock {
        val person = personDirectoryPort.loadDirectory().personById(personId) ?: return@withLock null
        val updated = person.withoutEmbedding(embeddingId)
        personDirectoryPort.upsertPerson(updated)
        updated
    }

    /**
     * Remove all face embeddings for all persons in the directory.
     *
     * Bulk GDPR operation — clears all biometric data while preserving person names
     * and photo associations.
     * Protected by [mutationMutex] to prevent conflicts with concurrent gallery enrichment.
     */
    suspend fun clearAllEmbeddings() = mutationMutex.withLock {
        val currentDir = personDirectoryPort.loadDirectory()
        val cleared = currentDir.copy(
            persons = currentDir.persons.map {
                it.copy(gallery = emptyList(), updatedAt = System.currentTimeMillis())
            }
        )
        personDirectoryPort.saveDirectory(cleared)
    }

    /**
     * Trim all person galleries to the current [FaceMatchingConfig.maxGallerySize].
     *
     * Call this when the user decreases the max gallery size in settings — existing galleries
     * may exceed the new limit. Uses diversity-aware eviction to keep the most representative
     * embeddings.
     *
     * Protected by [mutationMutex] to prevent conflicts with concurrent mutations.
     *
     * @return The number of embeddings evicted.
     */
    suspend fun trimAllGalleriesToConfigSize(): Int = mutationMutex.withLock {
        val config = getMatchingConfig()
        val currentDir = personDirectoryPort.loadDirectory()
        val totalBefore = currentDir.totalEmbeddings
        val trimmed = currentDir.trimAllGalleriesToSize(config.maxGallerySize)
        val totalAfter = trimmed.totalEmbeddings
        if (trimmed != currentDir) {
            personDirectoryPort.saveDirectory(trimmed)
        }
        totalBefore - totalAfter
    }

    // ── Source Path Validation (issue #17) ────────────────────────────────

    /**
     * Validate a source path and open the containing folder if it exists.
     *
     * @return Result.success with the parent File if opened, Result.failure if the path
     *   doesn't exist or has no parent directory.
     */
    fun validateAndOpenFolder(path: String): Result<java.io.File> {
        val file = java.io.File(path)
        val parent = if (file.isFile) file.parentFile else file
        return if (parent != null && parent.exists()) {
            platformPort.openWithSystemViewer(parent)
            Result.success(parent)
        } else {
            Result.failure(java.io.FileNotFoundException("Path does not exist: $path"))
        }
    }

    // ── Matching Config ───────────────────────────────────────────────────

    /**
     * Get the current face matching configuration from app settings.
     *
     * Falls back to defaults if not explicitly configured.
     */
    suspend fun getMatchingConfig(): FaceMatchingConfig {
        val settings = settingsPort.observeSettings().value
        return settings.faceMatchingConfig
    }

    /**
     * Update the face matching configuration.
     *
     * Allows users to tune matching thresholds for their photo quality (see issue #6).
     */
    suspend fun setMatchingConfig(config: FaceMatchingConfig) {
        val settings = settingsPort.observeSettings().value
        settingsPort.saveSettings(settings.copy(faceMatchingConfig = config))
    }
}