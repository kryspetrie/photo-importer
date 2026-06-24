package org.kryspetrie.fileimport.application

import java.io.File
import kotlinx.coroutines.withContext
import org.kryspetrie.fileimport.domain.model.FileChangeType
import org.kryspetrie.fileimport.domain.model.JournalEntry
import org.kryspetrie.fileimport.domain.model.ReorganizeMapping
import org.kryspetrie.fileimport.domain.model.ReorganizeMode
import org.kryspetrie.fileimport.domain.port.DispatcherProvider

/**
 * Executes the actual file moves/copies during reorganization and creates
 * journal entries for undo.
 */
class FileOperationExecutor(
    private val dispatcherProvider: DispatcherProvider,
) {

    /**
     * Result of executing a single mapping operation.
     */
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
                val source = File(mapping.currentPath)
                val dest = File(mapping.newPath)

                if (!source.exists()) {
                    return@withContext OperationResult(error = "Source not found: ${mapping.currentPath}")
                }

                if (dest.exists() && dest.absolutePath != source.absolutePath) {
                    return@withContext OperationResult(skippedCount = 1)
                }

                dest.parentFile?.mkdirs()

                val sameDir = source.parent == dest.parent
                val changeType =
                    when {
                        sameDir && source.name != dest.name -> FileChangeType.RENAME_ONLY
                        !sameDir && source.name == dest.name -> FileChangeType.MOVE_ONLY
                        else -> FileChangeType.BOTH
                    }

                when (mapping.mode) {
                    ReorganizeMode.MOVE -> {
                        if (source.renameTo(dest)) {
                            OperationResult(
                                journalEntry = JournalEntry(
                                    originalPath = mapping.currentPath,
                                    newPath = mapping.newPath,
                                    originalFilename = source.name,
                                    newFilename = dest.name,
                                    originalParent = source.parent.orEmpty(),
                                    newParent = dest.parent.orEmpty(),
                                    operationType = ReorganizeMode.MOVE,
                                    wasSuccessful = true,
                                    fileSize = source.length(),
                                    patternUsed = "",
                                    changeType = changeType,
                                ),
                                movedCount = if (sameDir) 0 else 1,
                                renamedCount = if (sameDir) 1 else 0,
                            )
                        } else {
                            // renameTo can fail across filesystems — fall back to copy + delete
                            source.copyTo(dest, overwrite = false)
                            source.delete()
                            OperationResult(
                                journalEntry = JournalEntry(
                                    originalPath = mapping.currentPath,
                                    newPath = mapping.newPath,
                                    originalFilename = source.name,
                                    newFilename = dest.name,
                                    originalParent = source.parent.orEmpty(),
                                    newParent = dest.parent.orEmpty(),
                                    operationType = ReorganizeMode.MOVE,
                                    wasSuccessful = true,
                                    fileSize = dest.length(),
                                    patternUsed = "",
                                    changeType = changeType,
                                ),
                                movedCount = 1,
                            )
                        }
                    }
                    ReorganizeMode.COPY -> {
                        source.copyTo(dest, overwrite = false)
                        OperationResult(
                            journalEntry = JournalEntry(
                                originalPath = mapping.currentPath,
                                newPath = mapping.newPath,
                                originalFilename = source.name,
                                newFilename = dest.name,
                                originalParent = source.parent.orEmpty(),
                                newParent = dest.parent.orEmpty(),
                                operationType = ReorganizeMode.COPY,
                                wasSuccessful = true,
                                fileSize = dest.length(),
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
     * For MOVE: moves file back to original location.
     * For COPY: deletes the copied file (originals were preserved).
     *
     * @param entry The journal entry describing the operation to undo
     * @return Pair of (restoredCount, deletedCount, error message or null)
     */
    data class UndoResult(val restoredCount: Int = 0, val deletedCount: Int = 0, val error: String? = null)

    suspend fun executeUndo(entry: JournalEntry): UndoResult =
        withContext(dispatcherProvider.io) {
            try {
                when (entry.operationType) {
                    ReorganizeMode.MOVE -> {
                        val current = File(entry.newPath)
                        val original = File(entry.originalPath)

                        if (!current.exists()) {
                            return@withContext UndoResult(error = "File not found for undo: ${entry.newPath}")
                        }

                        original.parentFile?.mkdirs()
                        if (current.renameTo(original)) {
                            UndoResult(restoredCount = 1)
                        } else {
                            current.copyTo(original, overwrite = false)
                            current.delete()
                            UndoResult(restoredCount = 1)
                        }
                    }
                    ReorganizeMode.COPY -> {
                        val copied = File(entry.newPath)
                        if (copied.exists()) {
                            copied.delete()
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
    fun cleanEmptyDirs(root: File) {
        if (!root.isDirectory) return
        root.walkBottomUp().forEach { dir ->
            if (dir.isDirectory && dir != root && (dir.listFiles()?.isEmpty() == true)) {
                dir.delete()
            }
        }
    }
}