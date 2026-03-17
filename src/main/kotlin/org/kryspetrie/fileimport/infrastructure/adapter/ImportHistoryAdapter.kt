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

  suspend fun addEntry(entry: ImportHistoryEntry) =
      withContext(Dispatchers.IO) {
        try {
          val history = loadHistory().toMutableList()
          history.add(0, entry)
          val trimmed = history.take(500)
          historyFile.writeText(json.encodeToString(trimmed))
        } catch (_: Exception) {}
      }

  suspend fun clearHistory() =
      withContext(Dispatchers.IO) {
        try {
          historyFile.delete()
        } catch (_: Exception) {}
      }

  suspend fun isSourceFullyImported(sourcePath: String, destinationPath: String): Boolean {
    return loadHistory().any { entry ->
      entry.sourcePath == sourcePath &&
          entry.destinationPath == destinationPath &&
          entry.errorCount == 0 &&
          entry.skippedCount == 0
    }
  }
}
