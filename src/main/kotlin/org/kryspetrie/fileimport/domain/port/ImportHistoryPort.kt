package org.kryspetrie.fileimport.domain.port

import org.kryspetrie.fileimport.domain.model.ImportFileDetail
import org.kryspetrie.fileimport.domain.model.ImportHistoryEntry

/**
 * Port interface for import history persistence.
 *
 * Decouples the UI from the JSON-file-based [ImportHistoryAdapter], keeping the hexagonal
 * architecture boundary clean. The UI should inject this port, not the concrete adapter.
 */
interface ImportHistoryPort {

    /** Loads all import history entries, newest first. */
    suspend fun loadHistory(): List<ImportHistoryEntry>

    /** Adds a new import history entry, optionally enriched with per-file details. */
    suspend fun addEntry(entry: ImportHistoryEntry, fileDetails: List<ImportFileDetail>? = null)

    /** Clears all import history. */
    suspend fun clearHistory()

    /**
     * Returns true if the given source→destination has been fully imported without errors or skips.
     */
    suspend fun isSourceFullyImported(sourcePath: String, destinationPath: String): Boolean

    /** Returns the import entry with the given ID, or null. */
    suspend fun getImportDetails(importId: String): ImportHistoryEntry?

    /** Searches history by source or destination path. */
    suspend fun searchByPath(path: String): List<ImportHistoryEntry>
}
