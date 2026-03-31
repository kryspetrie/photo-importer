package org.kryspetrie.fileimport.infrastructure.adapter

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.kryspetrie.fileimport.domain.model.ImportHistoryEntry

class ImportHistoryAdapter(
    private val historyDir: File = File(System.getProperty("user.home"), ".petrie-importer")
) {
  private val json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
  }
  private val historyFile = File(historyDir, "import_history.json")

  init {
    historyDir.mkdirs()
  }

  /**
   * Loads import history from disk.
   *
   * @return List of import history entries, newest first
   */
  suspend fun loadHistory(): List<ImportHistoryEntry> =
      withContext(Dispatchers.IO) {
        try {
          if (historyFile.exists()) {
            json.decodeFromString<List<ImportHistoryEntry>>(historyFile.readText())
          } else emptyList()
        } catch (_: Exception) {
          emptyList()
        }
      }

  /**
   * Adds a new import history entry with full file details.
   *
   * @param entry Import history entry to save
   * @param fileDetails Optional list of per-file details (merged into entry if provided)
   */
  suspend fun addEntry(
      entry: ImportHistoryEntry,
      fileDetails: List<org.kryspetrie.fileimport.domain.model.ImportFileDetail>? = null
  ) =
      withContext(Dispatchers.IO) {
        try {
          val history = loadHistory().toMutableList()
          val enrichedEntry =
              if (fileDetails != null && fileDetails.isNotEmpty()) {
                entry.copy(fileDetails = fileDetails)
              } else entry
          history.add(0, enrichedEntry)
          val trimmed = history.take(500)
          historyFile.writeText(json.encodeToString(trimmed))
        } catch (_: Exception) {}
      }

  /** Clears all import history. */
  suspend fun clearHistory() =
      withContext(Dispatchers.IO) {
        try {
          historyFile.delete()
        } catch (_: Exception) {}
      }

  /**
   * Checks if a source has been fully imported to a destination.
   *
   * @param sourcePath Source directory path
   * @param destinationPath Destination directory path
   * @return True if source was fully imported without errors or skips
   */
  suspend fun isSourceFullyImported(sourcePath: String, destinationPath: String): Boolean {
    return loadHistory().any { entry ->
      entry.sourcePath == sourcePath &&
          entry.destinationPath == destinationPath &&
          entry.errorCount == 0 &&
          entry.skippedCount == 0
    }
  }

  /**
   * Gets detailed history for a specific import session.
   *
   * @param importId Import session ID
   * @return Import entry with full file details or null
   */
  suspend fun getImportDetails(importId: String): ImportHistoryEntry? {
    return loadHistory().find { it.id == importId }
  }

  /**
   * Searches import history by source or destination path.
   *
   * @param path Path to search for (matches source or destination)
   * @return List of matching import entries
   */
  suspend fun searchByPath(path: String): List<ImportHistoryEntry> {
    return loadHistory().filter {
      it.sourcePath.contains(path, ignoreCase = true) ||
          it.destinationPath.contains(path, ignoreCase = true)
    }
  }
}
