package org.kryspetrie.fileimport.application

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.ReorganizeJournal
import org.kryspetrie.fileimport.domain.model.ReorganizeJournalSummary
import org.kryspetrie.fileimport.domain.port.FileSystemPort

/** Shared JSON instance for journal serialization. */
val json = Json { prettyPrint = true }

/**
 * Handles all journal file I/O for reorganization operations.
 *
 * Responsible for listing, reading, and writing journal files that enable
 * undo functionality after reorganize operations.
 */
class ReorganizeJournalRepository(private val fileSystem: FileSystemPort) {

    /**
     * Lists all reorganization journals with summaries.
     *
     * @return List of journal summaries sorted by date (newest first)
     */
    fun listJournals(): List<ReorganizeJournalSummary> = runBlocking {
        val dir = FilePath(System.getProperty("user.home") + "/.petrie-importer/journals")
        if (!fileSystem.exists(dir) || !fileSystem.isDirectory(dir)) {
            return@runBlocking emptyList<ReorganizeJournalSummary>()
        }

        val files = fileSystem.listFiles(dir)
        val jsonFiles = files.filter { fileSystem.extension(it) == "json" }
        val sortedFiles = jsonFiles
            .map { it to fileSystem.lastModified(it) }
            .sortedByDescending { it.second }
            .map { it.first }

        return@runBlocking sortedFiles.mapNotNull { filePath ->
            try {
                val content = fileSystem.readText(filePath)
                val journal = json.decodeFromString<ReorganizeJournal>(content)
                ReorganizeJournalSummary(
                    id = journal.id,
                    timestamp = journal.timestamp,
                    timestampString = ReorganizeJournal.createTimestampString(journal.timestamp),
                    rootFolder = journal.rootFolder,
                    operationMode = journal.operationMode,
                    totalFiles = journal.totalFiles,
                    changedFiles = journal.changedFiles,
                    undone = journal.undone,
                )
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Gets full journal details by path.
     *
     * @param journalPath Path to journal file
     * @return Full journal object or null if not found/invalid
     */
    fun getJournal(journalPath: String): ReorganizeJournal? {
        val filePath = FilePath(journalPath)
        val exists = runBlocking { fileSystem.exists(filePath) }
        if (!exists) return null
        return try {
            val content = fileSystem.readText(filePath)
            json.decodeFromString<ReorganizeJournal>(content)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Saves a journal to disk and returns the file path.
     *
     * @param journal The journal to persist
     * @param timestamp Timestamp used for the filename
     * @return Absolute path to the saved journal file
     */
    fun saveJournal(journal: ReorganizeJournal, timestamp: Long): String {
        val journalDir = FilePath(System.getProperty("user.home") + "/.petrie-importer/journals")
        runBlocking { fileSystem.mkdirs(journalDir) }
        val journalFile = journalDir.resolve("reorg_$timestamp.json")
        fileSystem.writeText(journalFile, json.encodeToString(journal))
        return fileSystem.absolutePath(journalFile)
    }

    /**
     * Marks a journal as undone by rewriting it.
     *
     * @param journalPath Path to the journal file
     * @param updatedJournal Journal with undone flag set
     */
    fun markUndone(journalPath: String, updatedJournal: ReorganizeJournal) {
        val filePath = FilePath(journalPath)
        fileSystem.writeText(filePath, json.encodeToString(updatedJournal))
    }
}