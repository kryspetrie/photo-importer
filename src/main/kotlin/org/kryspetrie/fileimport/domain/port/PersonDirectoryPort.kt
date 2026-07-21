package org.kryspetrie.fileimport.domain.port

import kotlinx.coroutines.flow.StateFlow
import org.kryspetrie.fileimport.domain.model.FaceEmbedding
import org.kryspetrie.fileimport.domain.model.Person
import org.kryspetrie.fileimport.domain.model.PersonDirectory

/**
 * Port for managing the person directory — the persistent store of known persons
 * and their face embedding galleries.
 *
 * The directory is observed reactively via [observeDirectory] for UI updates,
 * and persisted locally (JSON file). For larger directories, a SQLite adapter
 * would be more appropriate (see Architecture docs).
 *
 * ## Design rationale: single interface
 *
 * This interface has 12 methods, which is wider than typical port interfaces. This is
 * intentional: the person directory is an aggregate root with rich domain operations
 * (merge, gallery enrichment, import/export) that don't reduce cleanly to simple CRUD.
 * Splitting would create artificial seams (e.g., PersonDirectoryReader, PersonDirectoryWriter,
 * PersonDirectoryExporter) that complicate DI and testing for no real benefit at MVP scale.
 * If the interface grows significantly beyond this, consider the CQRS pattern.
 */
interface PersonDirectoryPort {
    /** Load the person directory from persistent storage. */
    suspend fun loadDirectory(): PersonDirectory

    /** Save the entire person directory to persistent storage. */
    suspend fun saveDirectory(directory: PersonDirectory)

    /** Observe the person directory as a reactive state flow. */
    fun observeDirectory(): StateFlow<PersonDirectory>

    /** Find the best-matching person for a given embedding, or null if no match above threshold. */
    suspend fun findMatch(embedding: FaceEmbedding): Person?

    /** Find all persons matching an embedding, sorted by similarity descending. */
    suspend fun findMatches(embedding: FaceEmbedding): List<Pair<Person, Float>>

    /** Add or update a person. Persists immediately. */
    suspend fun upsertPerson(person: Person)

    /** Delete a person by ID. Also removes all their embeddings. */
    suspend fun deletePerson(personId: String)

    /** Rename a person. Persists immediately. */
    suspend fun renamePerson(personId: String, newName: String)

    /** Add a face embedding to an existing person (or create a new person with that name). */
    suspend fun addEmbedding(personName: String, embedding: FaceEmbedding): Person

    /** Merge two persons. The source person's gallery and paths are absorbed into the target. */
    suspend fun mergePersons(targetId: String, sourceId: String): Person

    /**
     * Export the person directory as a byte array (zip bundle).
     *
     * The zip contains a single `persons.json` entry with the serialized directory.
     * Embedding vectors are stored as Base64-encoded byte arrays for compact serialization.
     */
    suspend fun exportDirectory(): ByteArray

    /**
     * Import a person directory from a byte array (zip bundle).
     *
     * Validates the imported data before replacing the current directory.
     * Returns the imported directory, or throws [PersonDirectoryImportException] if
     * validation fails (exceeds size limits, invalid format, incompatible version).
     */
    @Throws(PersonDirectoryImportException::class)
    suspend fun importDirectory(data: ByteArray): PersonDirectory
}

/**
 * Exception thrown when a person directory import fails validation.
 *
 * @property errors List of validation error messages describing why the import was rejected.
 */
class PersonDirectoryImportException(
    val errors: List<String>,
    message: String = "Person directory import failed validation: ${errors.joinToString("; ")}",
) : Exception(message)