package org.kryspetrie.fileimport.infrastructure.adapter

import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.kryspetrie.fileimport.domain.model.FaceEmbedding
import org.kryspetrie.fileimport.domain.model.Person
import org.kryspetrie.fileimport.domain.model.PersonDirectory
import org.kryspetrie.fileimport.domain.port.DispatcherProvider
import org.kryspetrie.fileimport.domain.port.FileSystemPort
import org.kryspetrie.fileimport.domain.port.PersonDirectoryPort
import org.kryspetrie.fileimport.domain.port.PersonDirectoryImportException
import org.kryspetrie.fileimport.domain.model.FilePath
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * JSON-file-backed implementation of [PersonDirectoryPort].
 *
 * Persists the person directory to `~/.petrie-importer/people/persons.json` using
 * kotlinx-serialization. Face embedding vectors are stored as Base64-encoded byte arrays
 * ([FaceEmbedding.vectorBase64]) for compact serialization (~2x smaller than float-list JSON).
 *
 * For larger directories (>500 persons), a SQLite adapter would be more appropriate.
 * The [PersonDirectory.MAX_DIRECTORY_SIZE] limit prevents unbounded growth.
 *
 * ## Thread Safety
 *
 * This adapter is the single source of truth for the person directory state.
 * All mutations go through [saveDirectory] which atomically updates both the file
 * and the in-memory StateFlow. There is no separate cached state in PersonService —
 * consumers observe [observeDirectory] for reactive updates.
 *
 * @see PersonDirectoryPort
 */
class JsonPersonDirectoryAdapter(
    private val fileSystem: FileSystemPort,
    private val dispatcherProvider: DispatcherProvider,
) : PersonDirectoryPort {

    companion object {
        private const val MAX_ZIP_ENTRIES = 100
        private const val MAX_ENTRY_SIZE = 10L * 1024 * 1024 // 10MB
    }

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val filePath = FilePath(PeoplePaths.peopleJsonPath)

    private val _directory = MutableStateFlow(PersonDirectory())
    override fun observeDirectory(): StateFlow<PersonDirectory> = _directory.asStateFlow()

    @Volatile
    private var initialized = false
    private val initMutex = Mutex()

    /**
     * Ensures the directory is loaded from disk before any operation.
     *
     * Uses a [Mutex] to prevent concurrent initialization — without this, multiple
     * coroutines could pass the `!initialized` check simultaneously and each trigger
     * a file read. The mutex ensures exactly one coroutine performs the initial load,
     * while others wait and then see the already-loaded state.
     */
    private suspend fun ensureInitialized() {
        if (initialized) return
        initMutex.withLock {
            if (initialized) return
            loadDirectory()
            initialized = true
        }
    }

    override suspend fun loadDirectory(): PersonDirectory {
        return try {
            val text = fileSystem.readText(filePath)
            val directory = json.decodeFromString<PersonDirectory>(text)
            _directory.value = directory
            directory
        } catch (_: Exception) {
            // File doesn't exist or is corrupt — start fresh
            val empty = PersonDirectory()
            _directory.value = empty
            empty
        }
    }

    override suspend fun saveDirectory(directory: PersonDirectory) {
        val text = json.encodeToString(directory)
        // C24: Atomic write — write to temp file first, then rename.
        // This prevents data loss if a crash occurs mid-write.
        val tempFilePath = FilePath(PeoplePaths.peopleJsonPath + ".tmp")
        // Clean up any stale temp file from a previous crash
        if (fileSystem.exists(tempFilePath)) {
            fileSystem.delete(tempFilePath)
        }
        fileSystem.writeText(tempFilePath, text)
        val renamed = fileSystem.renameTo(tempFilePath, filePath)
        if (!renamed) {
            // Fallback: if rename fails (e.g. cross-device), try direct write
            fileSystem.writeText(filePath, text)
        }
        // Only update StateFlow after file write succeeds
        _directory.value = directory
    }

    override suspend fun findMatch(embedding: FaceEmbedding): Person? {
        ensureInitialized()
        return _directory.value.findBestMatch(embedding)
    }

    override suspend fun findMatches(embedding: FaceEmbedding): List<Pair<Person, Float>> {
        ensureInitialized()
        return _directory.value.findAllMatches(embedding)
    }

    override suspend fun upsertPerson(person: Person) {
        ensureInitialized()
        val updated = _directory.value.withPerson(person)
        saveDirectory(updated)
    }

    override suspend fun deletePerson(personId: String) {
        ensureInitialized()
        val updated = _directory.value.withoutPerson(personId)
        saveDirectory(updated)
    }

    override suspend fun renamePerson(personId: String, newName: String) {
        ensureInitialized()
        val person = _directory.value.personById(personId) ?: return
        val renamed = person.copy(name = newName, updatedAt = System.currentTimeMillis())
        val updated = _directory.value.withPerson(renamed)
        saveDirectory(updated)
    }

    override suspend fun addEmbedding(personName: String, embedding: FaceEmbedding): Person {
        ensureInitialized()
        val existing = _directory.value.personByName(personName)
        val person = if (existing != null) {
            existing.withEmbedding(embedding)
        } else {
            Person(name = personName).withEmbedding(embedding)
        }
        val updated = _directory.value.withPerson(person)
        saveDirectory(updated)
        return person
    }

    override suspend fun mergePersons(targetId: String, sourceId: String): Person {
        ensureInitialized()
        val updated = _directory.value.mergePersons(targetId, sourceId)
        saveDirectory(updated)
        return updated.personById(targetId) ?: Person()
    }

    override suspend fun exportDirectory(): ByteArray {
        ensureInitialized()
        val jsonStr = json.encodeToString(_directory.value)
        val zipBytes = ByteArrayOutputStream()
        ZipOutputStream(zipBytes).use { zip ->
            zip.putNextEntry(ZipEntry("persons.json"))
            zip.write(jsonStr.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return zipBytes.toByteArray()
    }

    override suspend fun importDirectory(data: ByteArray): PersonDirectory {
        val jsonStr = ZipInputStream(data.inputStream()).use { zip ->
            var result = ""
            var entryCount = 0
            var entry = zip.nextEntry
            while (entry != null) {
                entryCount++
                // C7: Limit number of ZIP entries to prevent DoS
                if (entryCount > MAX_ZIP_ENTRIES) {
                    throw PersonDirectoryImportException(listOf("ZIP contains too many entries (max $MAX_ZIP_ENTRIES)"))
                }
                // C7: Reject path traversal entries
                val entryName = entry.name
                if (entryName.contains("..") || entryName.startsWith("/")) {
                    zip.closeEntry()
                    entry = zip.nextEntry
                    continue
                }
                // C7: Only process persons.json entries
                if (entryName == "persons.json") {
                    // C7: Size limit check (entry.size may be -1 if unknown)
                    if (entry.size > MAX_ENTRY_SIZE) {
                        throw PersonDirectoryImportException(listOf("ZIP entry '$entryName' exceeds maximum size of $MAX_ENTRY_SIZE bytes"))
                    }
                    val bytes = zip.readBytes()
                    if (bytes.size > MAX_ENTRY_SIZE) {
                        throw PersonDirectoryImportException(listOf("ZIP entry '$entryName' exceeds maximum size of $MAX_ENTRY_SIZE bytes"))
                    }
                    result = bytes.toString(Charsets.UTF_8)
                }
                entry = zip.nextEntry
            }
            result
        }
        val directory = if (jsonStr.isNotEmpty()) {
            json.decodeFromString<PersonDirectory>(jsonStr)
        } else {
            PersonDirectory()
        }

        // Validate before importing (issue #19: no validation on zip import)
        val errors = directory.validateForImport()
        if (errors.isNotEmpty()) {
            throw PersonDirectoryImportException(errors)
        }

        saveDirectory(directory)
        return directory
    }
}

/**
 * File paths for the person directory persistence.
 *
 * Uses [Platform.appDataDir] for cross-platform path resolution and
 * [File] constructor for safe path concatenation (issue #21).
 */
object PeoplePaths {
    /** Directory for person data storage. */
    val peopleDir: File by lazy {
        File(Platform.appDataDir, "people")
    }

    /** Directory for face thumbnail images. */
    val thumbnailsDir: File by lazy {
        File(peopleDir, "thumbnails").also { it.mkdirs() }
    }

    /** Path to the person directory JSON file. */
    val peopleJsonPath: String by lazy {
        File(peopleDir, "persons.json").path
    }
}