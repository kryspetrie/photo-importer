package org.kryspetrie.fileimport.application.metadata

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.MetadataEditJournal
import org.kryspetrie.fileimport.domain.model.MetadataEditJournalSummary
import org.kryspetrie.fileimport.domain.port.FileSystemPort

/** Shared JSON instance for journal serialization. */
private val json = Json { prettyPrint = true }

/**
 * Handles all journal file I/O for metadata edit operations.
 *
 * Responsible for listing, reading, and writing journal files that enable undo functionality
 * after metadata edits in the bulk metadata editor.
 *
 * Journals are stored as JSON files in `~/.petrie-importer/metadata-journals/`.
 */
class MetadataEditJournalRepository(private val fileSystem: FileSystemPort) {

    private val journalDir =
        FilePath(System.getProperty("user.home") + "/.petrie-importer/metadata-journals")

    /** Lists all metadata edit journals with summaries, sorted by date (newest first). */
    fun listJournals(): List<MetadataEditJournalSummary> = runBlocking {
        if (!fileSystem.exists(journalDir) || !fileSystem.isDirectory(journalDir)) {
            return@runBlocking emptyList<MetadataEditJournalSummary>()
        }

        val files = fileSystem.listFiles(journalDir)
        val jsonFiles = files.filter { fileSystem.extension(it) == "json" }
        val sortedFiles =
            jsonFiles
                .map { it to fileSystem.lastModified(it) }
                .sortedByDescending { it.second }
                .map { it.first }

        return@runBlocking sortedFiles.mapNotNull { filePath ->
            try {
                val content = fileSystem.readText(filePath)
                val journal = json.decodeFromString<MetadataEditJournal>(content)
                MetadataEditJournalSummary(
                    id = journal.id,
                    timestamp = journal.timestamp,
                    timestampString = journal.timestampString,
                    sourceFolderPath = journal.sourceFolderPath,
                    outputMode = journal.outputMode,
                    totalCount = journal.totalCount,
                    successCount = journal.successCount,
                    undone = journal.undone,
                )
            } catch (_: Exception) {
                null
            }
        }
    }

    /** Gets full journal details by path. Returns null if not found or invalid. */
    fun getJournal(journalPath: String): MetadataEditJournal? {
        val filePath = FilePath(journalPath)
        val exists = runBlocking { fileSystem.exists(filePath) }
        if (!exists) return null
        return try {
            val content = fileSystem.readText(filePath)
            json.decodeFromString<MetadataEditJournal>(content)
        } catch (_: Exception) {
            null
        }
    }

    /** Saves a journal to disk and returns the file path. */
    fun saveJournal(journal: MetadataEditJournal): String {
        runBlocking { fileSystem.mkdirs(journalDir) }
        val journalFile =
            journalDir.resolve("metadata_edit_${journal.timestamp}.json")
        fileSystem.writeText(journalFile, json.encodeToString(journal))
        return fileSystem.absolutePath(journalFile)
    }

    /** Marks a journal as undone by rewriting it with the `undone` flag set. */
    fun markUndone(journalPath: String, updatedJournal: MetadataEditJournal) {
        val filePath = FilePath(journalPath)
        fileSystem.writeText(filePath, json.encodeToString(updatedJournal))
    }

    /** Deletes a journal file from disk. */
    fun deleteJournal(journalPath: String) {
        runBlocking { fileSystem.delete(FilePath(journalPath)) }
    }
}