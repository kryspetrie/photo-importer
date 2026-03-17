package org.kryspetrie.fileimport.domain.model

import java.io.File
import kotlinx.serialization.Serializable

data class ReorganizeMapping(
    val file: ImageFile,
    val currentPath: String,
    val newPath: String,
    val newFileName: String,
    val wouldConflict: Boolean = false,
    val isChanged: Boolean = true
) {
  val currentRelativePath: String
    get() = file.file.name

  val newRelativePath: String
    get() = File(newPath).name
}

data class ReorganizePreview(
    val mappings: List<ReorganizeMapping>,
    val totalFiles: Int,
    val changedFiles: Int,
    val conflictCount: Int,
    val newFolderCount: Int
)

data class ReorganizeProgress(
    val current: Int = 0,
    val total: Int = 0,
    val currentFile: String = "",
    val phase: ReorganizePhase = ReorganizePhase.SCANNING
)

enum class ReorganizePhase {
  SCANNING,
  PREVIEWING,
  EXECUTING,
  COMPLETE,
  ROLLING_BACK
}

data class ReorganizeResult(
    val movedCount: Int,
    val renamedCount: Int,
    val skippedCount: Int,
    val errorCount: Int,
    val errors: List<String> = emptyList(),
    val journalPath: String? = null
)

@Serializable
data class ReorganizeJournal(
    val timestamp: Long = System.currentTimeMillis(),
    val rootFolder: String,
    val moves: List<JournalEntry>
)

@Serializable data class JournalEntry(val originalPath: String, val newPath: String)
