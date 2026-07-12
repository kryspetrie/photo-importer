package org.kryspetrie.fileimport.application

import kotlinx.coroutines.withContext
import org.kryspetrie.fileimport.domain.model.FileChangeType
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.JournalEntry
import org.kryspetrie.fileimport.domain.model.ReorganizeMapping
import org.kryspetrie.fileimport.domain.model.ReorganizeMode
import org.kryspetrie.fileimport.domain.port.DispatcherProvider
import org.kryspetrie.fileimport.domain.port.FileSystemPort

/**
 * Executes the actual file moves/copies during reorganization and creates journal entries for undo.
 */
class FileOperationExecutor(
    private val dispatcherProvider: DispatcherProvider,
    private val fileSystem: FileSystemPort,
) {

    /** Result of executing a single mapping operation. */
    data class OperationResult(
        val journalEntry: JournalEntry? = null,
        val movedCount: Int = 0,
        val renamedCount: Int = 0,
        val copiedCount: Int = 0,
        val skippedCount: Int = 0,
        val error: String? = null,
    )

    /**
     * Executes a single file move or copy operation.
     *
     * @param mapping The mapping describing source and destination
     * @return OperationResult with counts and optional journal entry
     */
    suspend fun executeOperation(mapping: ReorganizeMapping): OperationResult =
        withContext(dispatcherProvider.io) {
            try {
                val source = FilePath(mapping.currentPath)
                val dest = FilePath(mapping.newPath)

                if (!fileSystem.exists(source)) {
                    return@withContext OperationResult(
                        error = "Source not found: ${mapping.currentPath}"
                    )
                }

                val sourceAbsPath = fileSystem.absolutePath(source)
                val destAbsPath = fileSystem.absolutePath(dest)
                if (fileSystem.exists(dest) && destAbsPath != sourceAbsPath) {
                    return@withContext OperationResult(skippedCount = 1)
                }

                val destParent = FilePath(dest.parent ?: "")
                if (dest.parent != null) {
                    fileSystem.mkdirs(destParent)
                }

                val sourceParent = source.parent
                val destParentStr = dest.parent
                val sameDir = sourceParent == destParentStr
                val sourceName = fileSystem.name(source)
                val destName = fileSystem.name(dest)
                val changeType =
                    when {
                        sameDir && sourceName != destName -> FileChangeType.RENAME_ONLY
                        !sameDir && sourceName == destName -> FileChangeType.MOVE_ONLY
                        else -> FileChangeType.BOTH
                    }

                when (mapping.mode) {
                    ReorganizeMode.MOVE -> {
                        if (fileSystem.renameTo(source, dest)) {
                            OperationResult(
                                journalEntry =
                                    JournalEntry(
                                        originalPath = mapping.currentPath,
                                        newPath = mapping.newPath,
                                        originalFilename = sourceName,
                                        newFilename = destName,
                                        originalParent = sourceParent.orEmpty(),
                                        newParent = destParentStr.orEmpty(),
                                        operationType = ReorganizeMode.MOVE,
                                        wasSuccessful = true,
                                        fileSize = fileSystem.length(source),
                                        patternUsed = "",
                                        changeType = changeType,
                                    ),
                                movedCount = if (sameDir) 0 else 1,
                                renamedCount = if (sameDir) 1 else 0,
                            )
                        } else {
                            // renameTo can fail across filesystems — fall back to copy + delete
                            fileSystem.copy(source, dest)
                            fileSystem.delete(source)
                            OperationResult(
                                journalEntry =
                                    JournalEntry(
                                        originalPath = mapping.currentPath,
                                        newPath = mapping.newPath,
                                        originalFilename = sourceName,
                                        newFilename = destName,
                                        originalParent = sourceParent.orEmpty(),
                                        newParent = destParentStr.orEmpty(),
                                        operationType = ReorganizeMode.MOVE,
                                        wasSuccessful = true,
                                        fileSize = fileSystem.length(dest),
                                        patternUsed = "",
                                        changeType = changeType,
                                    ),
                                movedCount = 1,
                            )
                        }
                    }
                    ReorganizeMode.COPY -> {
                        fileSystem.copy(source, dest)
                        OperationResult(
                            journalEntry =
                                JournalEntry(
                                    originalPath = mapping.currentPath,
                                    newPath = mapping.newPath,
                                    originalFilename = sourceName,
                                    newFilename = destName,
                                    originalParent = sourceParent.orEmpty(),
                                    newParent = destParentStr.orEmpty(),
                                    operationType = ReorganizeMode.COPY,
                                    wasSuccessful = true,
                                    fileSize = fileSystem.length(dest),
                                    patternUsed = "",
                                    changeType = changeType,
                                ),
                            copiedCount = 1,
                        )
                    }
                }
            } catch (e: Exception) {
                OperationResult(error = "${mapping.file.fileName}: ${e.message}")
            }
        }

    /**
     * Executes an undo operation for a single journal entry.
     *
     * For MOVE: moves file back to original location. For COPY: deletes the copied file (originals
     * were preserved).
     *
     * @param entry The journal entry describing the operation to undo
     * @return Pair of (restoredCount, deletedCount, error message or null)
     */
    data class UndoResult(
        val restoredCount: Int = 0,
        val deletedCount: Int = 0,
        val error: String? = null,
    )

    suspend fun executeUndo(entry: JournalEntry): UndoResult =
        withContext(dispatcherProvider.io) {
            try {
                when (entry.operationType) {
                    ReorganizeMode.MOVE -> {
                        val current = FilePath(entry.newPath)
                        val original = FilePath(entry.originalPath)

                        if (!fileSystem.exists(current)) {
                            return@withContext UndoResult(
                                error = "File not found for undo: ${entry.newPath}"
                            )
                        }

                        val originalParent = original.parent
                        if (originalParent != null) {
                            fileSystem.mkdirs(FilePath(originalParent))
                        }
                        if (fileSystem.renameTo(current, original)) {
                            UndoResult(restoredCount = 1)
                        } else {
                            fileSystem.copy(current, original)
                            fileSystem.delete(current)
                            UndoResult(restoredCount = 1)
                        }
                    }
                    ReorganizeMode.COPY -> {
                        val copied = FilePath(entry.newPath)
                        if (fileSystem.exists(copied)) {
                            fileSystem.delete(copied)
                            UndoResult(deletedCount = 1)
                        } else {
                            UndoResult()
                        }
                    }
                }
            } catch (e: Exception) {
                UndoResult(error = "Undo failed for ${entry.newPath}: ${e.message}")
            }
        }

    /**
     * Cleans up empty directories left behind after file moves.
     *
     * Walks the directory tree bottom-up and removes empty directories.
     */
    suspend fun cleanEmptyDirs(root: FilePath) {
        if (!fileSystem.isDirectory(root)) return
        fileSystem.walkBottomUp(root).forEach { dirPath ->
            val isDir = fileSystem.isDirectory(dirPath)
            val isNotRoot = dirPath != root
            val isEmpty = fileSystem.listFiles(dirPath).isEmpty()
            if (isDir && isNotRoot && isEmpty) {
                fileSystem.delete(dirPath)
            }
        }
    }
}
