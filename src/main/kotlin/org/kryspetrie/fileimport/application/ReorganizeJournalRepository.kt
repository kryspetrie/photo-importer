package org.kryspetrie.fileimport.application

import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.kryspetrie.fileimport.domain.model.ReorganizeJournal
import org.kryspetrie.fileimport.domain.model.ReorganizeJournalSummary

/** Shared JSON instance for journal serialization. */
val json = Json { prettyPrint = true }

/**
 * Handles all journal file I/O for reorganization operations.
 *
 * Responsible for listing, reading, and writing journal files that enable
 * undo functionality after reorganize operations.
 */
class ReorganizeJournalRepository {

    /**
     * Lists all reorganization journals with summaries.
     *
     * @return List of journal summaries sorted by date (newest first)
     */
    fun listJournals(): List<ReorganizeJournalSummary> {
        val dir = File(System.getProperty("user.home"), ".petrie-importer/journals")
        val files =
            dir.listFiles()
                ?.filter { it.extension == "json" }
                ?.sortedByDescending { it.lastModified() } ?: emptyList()

        return files.mapNotNull { file ->
            try {
                val journal = json.decodeFromString<ReorganizeJournal>(file.readText())
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
        val file = File(journalPath)
        if (!file.exists()) return null
        return try {
            json.decodeFromString<ReorganizeJournal>(file.readText())
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
        val journalDir = File(System.getProperty("user.home"), ".petrie-importer/journals")
        journalDir.mkdirs()
        val journalFile = File(journalDir, "reorg_$timestamp.json")
        journalFile.writeText(json.encodeToString(journal))
        return journalFile.absolutePath
    }

    /**
     * Marks a journal as undone by rewriting it.
     *
     * @param journalPath Path to the journal file
     * @param updatedJournal Journal with undone flag set
     */
    fun markUndone(journalPath: String, updatedJournal: ReorganizeJournal) {
        val file = File(journalPath)
        file.writeText(json.encodeToString(updatedJournal))
    }
}