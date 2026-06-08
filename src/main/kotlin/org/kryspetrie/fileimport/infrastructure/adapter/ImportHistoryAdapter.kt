package org.kryspetrie.fileimport.infrastructure.adapter

import java.io.File
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.kryspetrie.fileimport.domain.model.ImportFileDetail
import org.kryspetrie.fileimport.domain.model.ImportHistoryEntry
import org.kryspetrie.fileimport.domain.port.DispatcherProvider
import org.kryspetrie.fileimport.domain.port.ImportHistoryPort

class ImportHistoryAdapter(
    private val historyDir: File = File(System.getProperty("user.home"), ".petrie-importer"),
    private val dispatcherProvider: DispatcherProvider,
) : ImportHistoryPort {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val historyFile = File(historyDir, "import_history.json")

    init {
        historyDir.mkdirs()
    }

    override suspend fun loadHistory(): List<ImportHistoryEntry> =
        withContext(dispatcherProvider.io) {
            try {
                if (historyFile.exists()) {
                    json.decodeFromString<List<ImportHistoryEntry>>(historyFile.readText())
                } else emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }

    override suspend fun addEntry(
        entry: ImportHistoryEntry,
        fileDetails: List<ImportFileDetail>?,
    ): Unit =
        withContext(dispatcherProvider.io) {
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

    override suspend fun clearHistory(): Unit =
        withContext(dispatcherProvider.io) {
            try {
                historyFile.delete()
            } catch (_: Exception) {}
        }

    override suspend fun isSourceFullyImported(
        sourcePath: String,
        destinationPath: String,
    ): Boolean {
        return loadHistory().any { entry ->
            entry.sourcePath == sourcePath &&
                entry.destinationPath == destinationPath &&
                entry.errorCount == 0 &&
                entry.skippedCount == 0
        }
    }

    override suspend fun getImportDetails(importId: String): ImportHistoryEntry? {
        return loadHistory().find { it.id == importId }
    }

    override suspend fun searchByPath(path: String): List<ImportHistoryEntry> {
        return loadHistory().filter {
            it.sourcePath.contains(path, ignoreCase = true) ||
                it.destinationPath.contains(path, ignoreCase = true)
        }
    }
}
