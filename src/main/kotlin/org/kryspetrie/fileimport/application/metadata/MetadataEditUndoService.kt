package org.kryspetrie.fileimport.application.metadata

import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.MetadataEditEntry
import org.kryspetrie.fileimport.domain.model.MetadataEditJournal
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration
import org.kryspetrie.fileimport.domain.port.FileSystemPort
import org.kryspetrie.fileimport.domain.port.ImageProcessingPort

/**
 * Service for performing undo/redo operations on metadata edits.
 *
 * ## Undo (Overwrite mode)
 * Restores original file bytes from backup copies. Each backup was created before the metadata
 * was written. After restoring, the journal is marked as undone.
 *
 * ## Undo (Save New mode)
 * Deletes the output files that were created. The originals were never modified.
 *
 * ## Redo (Overwrite mode)
 * Re-reads the config snapshot from the journal and re-applies the metadata to the original files.
 * Creates new backup files and marks the journal as not-undone.
 *
 * ## Redo (Save New mode)
 * Re-runs the write operation to create new output files.
 *
 * Backup files are stored in `~/.petrie-importer/metadata-backups/` with a naming convention
 * of `{timestamp}_{originalFilename}` to avoid collisions.
 */
class MetadataEditUndoService(
    private val journalRepository: MetadataEditJournalRepository,
    private val fileSystem: FileSystemPort,
    private val imageProcessing: ImageProcessingPort,
) {

    private val backupDir =
        File(System.getProperty("user.home") + "/.petrie-importer/metadata-backups")

    /**
     * Creates a backup of a file before metadata is written.
     *
     * @return The path to the backup file, or null if the backup failed.
     */
    suspend fun createBackup(originalPath: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val original = File(originalPath)
                if (!original.exists()) return@withContext null

                backupDir.mkdirs()
                val timestamp = System.currentTimeMillis()
                val backupFile = File(backupDir, "${timestamp}_${original.name}")

                // Copy file bytes
                original.copyTo(backupFile, overwrite = true)
                backupFile.absolutePath
            } catch (_: IOException) {
                null
            }
        }
    }

    /**
     * Saves a metadata edit journal after a batch save completes.
     *
     * @param sourceFolderPath The source folder/file path being edited.
     * @param outputMode The output mode ("OVERWRITE" or "SAVE_NEW").
     * @param entries The list of file entries with backup paths.
     * @return The saved journal, or null if saving failed.
     */
    fun saveJournalPath(
        sourceFolderPath: String,
        outputMode: String,
        entries: List<MetadataEditEntry>,
    ): String? {
        return try {
            val journal =
                MetadataEditJournal(
                    sourceFolderPath = sourceFolderPath,
                    outputMode = outputMode,
                    entries = entries,
                )
            journalRepository.saveJournal(journal)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Undoes a metadata edit journal.
     *
     * For OVERWRITE mode: restores original files from backups.
     * For SAVE_NEW mode: deletes the output files.
     *
     * @return The number of files successfully restored/deleted, or -1 on error.
     */
    suspend fun undo(journalPath: String): Int {
        val journal = journalRepository.getJournal(journalPath) ?: return -1
        if (journal.undone) return -1

        var restoredCount = 0

        for (entry in journal.entries) {
            if (!entry.wasSuccessful) continue

            when {
                entry.wasSavedNew -> {
                    // SAVE_NEW: delete the output file
                    val outputPath = FilePath(entry.outputFilePath)
                    if (fileSystem.exists(outputPath)) {
                        fileSystem.delete(outputPath)
                        // Also delete back image output if it exists
                        entry.backImageOutputPath?.let { backPath ->
                            val backFilePath = FilePath(backPath)
                            if (fileSystem.exists(backFilePath)) {
                                fileSystem.delete(backFilePath)
                            }
                        }
                        restoredCount++
                    }
                }
                else -> {
                    // OVERWRITE: restore from backup
                    val backupFile = File(entry.backupPath)
                    val originalFile = File(entry.filePath)
                    if (backupFile.exists() && originalFile.exists()) {
                        withContext(Dispatchers.IO) {
                            backupFile.copyTo(originalFile, overwrite = true)
                        }
                        // Also restore back image backup if it exists
                        entry.backImageBackupPath?.let { backBackupPath ->
                            val backBackup = File(backBackupPath)
                            val backOriginalPath =
                                originalFile.parent +
                                    File.separator +
                                    originalFile.nameWithoutExtension +
                                    "_back.jpg"
                            val backOriginal = File(backOriginalPath)
                            if (backBackup.exists()) {
                                withContext(Dispatchers.IO) {
                                    backBackup.copyTo(backOriginal, overwrite = true)
                                }
                            }
                        }
                        restoredCount++
                    }
                }
            }
        }

        // Mark journal as undone
        journalRepository.markUndone(journalPath, journal.copy(undone = true))
        return restoredCount
    }

    /**
     * Redoes a metadata edit journal by re-applying configs from the journal snapshots.
     *
     * @param metadataWriter Function that writes metadata to a file given its path, config, and
     *   optional source path.
     * @return The number of files successfully re-processed, or -1 on error.
     */
    suspend fun redo(
        journalPath: String,
        metadataWriter: suspend (FilePath, PhotoScanConfiguration, FilePath?) -> Unit,
    ): Int {
        val journal = journalRepository.getJournal(journalPath) ?: return -1
        if (!journal.undone) return -1

        var redoneCount = 0

        for (entry in journal.entries) {
            if (!entry.wasSuccessful) continue

            try {
                // Use the original backup as the EXIF source for KEEP_SOURCE fields.
                // The backup contains the pre-edit EXIF data, which is needed so that
                // KEEP_SOURCE fields read the original values rather than the current
                // (possibly already-written) values.
                // Falls back to the current file if the backup no longer exists.
                val exifSourcePath = if (entry.backupPath != null && File(entry.backupPath).exists()) {
                    FilePath(entry.backupPath)
                } else {
                    FilePath(entry.filePath)
                }

                // Create fresh backup before re-writing (for OVERWRITE mode)
                val newBackupPath = createBackup(entry.filePath)

                val originalPath = FilePath(entry.filePath)
                val config = entry.configSnapshot

                metadataWriter(originalPath, config, exifSourcePath)
                redoneCount++
            } catch (_: Exception) {
                // Skip failed entries
            }
        }

        // Mark journal as not undone
        journalRepository.markUndone(journalPath, journal.copy(undone = false))
        return redoneCount
    }

    /**
     * Cleans up old backup files that are older than [maxAgeMs].
     *
     * @param maxAgeMs Maximum age in milliseconds. Defaults to 7 days.
     */
    suspend fun cleanupOldBackups(maxAgeMs: Long = 7 * 24 * 60 * 60 * 1000L) {
        withContext(Dispatchers.IO) {
            val cutoff = System.currentTimeMillis() - maxAgeMs

            // Clean up backup files
            if (backupDir.exists()) {
                backupDir.listFiles()?.forEach { file ->
                    if (file.lastModified() < cutoff) {
                        file.delete()
                    }
                }
            }
        }
    }

    /**
     * Cleans up all backup files for a specific journal.
     *
     * Called after a journal is no longer needed.
     */
    suspend fun cleanupJournalBackups(journal: MetadataEditJournal) {
        withContext(Dispatchers.IO) {
            for (entry in journal.entries) {
                File(entry.backupPath).let { if (it.exists()) it.delete() }
                entry.backImageBackupPath?.let { backPath ->
                    File(backPath).let { f -> if (f.exists()) f.delete() }
                }
            }
        }
    }
}