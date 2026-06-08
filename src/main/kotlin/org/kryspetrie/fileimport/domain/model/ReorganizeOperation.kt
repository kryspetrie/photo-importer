package org.kryspetrie.fileimport.domain.model

import kotlinx.serialization.Serializable

/**
 * Operation mode for reorganizing library files.
 *
 * Determines whether files are moved (originals removed) or copied (originals preserved).
 */
enum class ReorganizeMode {
    /** Move files to new locations, removing originals */
    MOVE,

    /** Copy files to new locations, keeping originals intact */
    COPY,
}

/**
 * Complete mapping information for a single file reorganization.
 *
 * Tracks both current and planned state for preview and undo operations.
 */
data class ReorganizeMapping(
    val file: ImageFile,
    val currentPath: String,
    val newPath: String,
    val newFileName: String,
    val wouldConflict: Boolean = false,
    val isChanged: Boolean = true,
    /** Operation mode: MOVE or COPY */
    val mode: ReorganizeMode = ReorganizeMode.MOVE,
) {
    val currentRelativePath: String
        get() = file.path.name

    val newRelativePath: String
        get() = FilePath(newPath).name

    /** Current parent directory */
    val currentParent: String?
        get() = FilePath(currentPath).parent

    /** New parent directory */
    val newParent: String?
        get() = FilePath(newPath).parent
}

/** Preview of reorganization operation before execution. */
data class ReorganizePreview(
    val mappings: List<ReorganizeMapping>,
    val totalFiles: Int,
    val changedFiles: Int,
    val conflictCount: Int,
    val newFolderCount: Int,
    val operationMode: ReorganizeMode = ReorganizeMode.MOVE,
)

/** Progress tracking during reorganization. */
data class ReorganizeProgress(
    val current: Int = 0,
    val total: Int = 0,
    val currentFile: String = "",
    val phase: ReorganizePhase = ReorganizePhase.SCANNING,
    val operationMode: ReorganizeMode = ReorganizeMode.MOVE,
)

/** Phases of reorganization operation. */
enum class ReorganizePhase {
    SCANNING,
    PREVIEWING,
    EXECUTING,
    COMPLETE,
    ROLLING_BACK,
    UNDOING,
}

/** Results of a reorganization operation. */
data class ReorganizeResult(
    val movedCount: Int,
    val renamedCount: Int,
    val copiedCount: Int = 0,
    val skippedCount: Int,
    val errorCount: Int,
    val errors: List<String> = emptyList(),
    val journalPath: String? = null,
    val operationMode: ReorganizeMode = ReorganizeMode.MOVE,
)

/**
 * Complete journal entry for undo/rollback operations.
 *
 * Stores full original state to enable complete restoration.
 */
@Serializable
data class JournalEntry(
    /** Original file path before operation */
    val originalPath: String,

    /** New file path after operation */
    val newPath: String,

    /** Original filename */
    val originalFilename: String,

    /** New filename */
    val newFilename: String,

    /** Original parent directory */
    val originalParent: String,

    /** New parent directory */
    val newParent: String,

    /** Whether this was a move or copy operation */
    val operationType: ReorganizeMode = ReorganizeMode.MOVE,

    /** Whether the operation was successful */
    val wasSuccessful: Boolean = true,

    /** Original file size for verification */
    val fileSize: Long = 0,

    /** Pattern used for renaming (if any) */
    val patternUsed: String = "",

    /** Whether file was renamed, moved, or both */
    val changeType: FileChangeType = FileChangeType.BOTH,
)

/** Type of change applied to a file. */
enum class FileChangeType {
    /** Only filename changed */
    RENAME_ONLY,

    /** Only location changed */
    MOVE_ONLY,

    /** Both filename and location changed */
    BOTH,

    /** No changes applied */
    NONE,
}

/**
 * Complete journal for reorganization operations.
 *
 * Enables undo functionality by storing complete before/after state.
 */
@Serializable
data class ReorganizeJournal(
    /** Unique identifier for this journal */
    val id: String = DomainDefaults.generateId(),

    /** Timestamp when operation started */
    val timestamp: Long = DomainDefaults.currentTimeMillis(),

    /** Human-readable timestamp */
    val timestampString: String = "",

    /** Root folder that was reorganized */
    val rootFolder: String,

    /** Operation mode used (MOVE or COPY) */
    val operationMode: ReorganizeMode = ReorganizeMode.MOVE,

    /** Folder pattern used for organization */
    val folderPattern: String = "",

    /** Filename pattern used for renaming */
    val filenamePattern: String = "",

    /** Total files processed */
    val totalFiles: Int = 0,

    /** Files that were changed */
    val changedFiles: Int = 0,

    /** Detailed entries for each file operation */
    val entries: List<JournalEntry> = emptyList(),

    /** Whether undo was performed on this journal */
    val undone: Boolean = false,

    /** Notes about the operation */
    val notes: String = "",
) {
    companion object {
        fun createTimestampString(timestamp: Long): String =
            DomainDefaults.formatTimestamp(timestamp)
    }
}

/** Summary of a reorganize journal for display purposes. */
data class ReorganizeJournalSummary(
    val id: String,
    val timestamp: Long,
    val timestampString: String,
    val rootFolder: String,
    val operationMode: ReorganizeMode,
    val totalFiles: Int,
    val changedFiles: Int,
    val undone: Boolean,
)
