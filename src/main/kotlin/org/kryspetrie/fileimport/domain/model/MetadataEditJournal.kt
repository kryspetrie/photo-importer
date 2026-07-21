package org.kryspetrie.fileimport.domain.model

import kotlinx.serialization.Serializable

/**
 * A single file entry in a metadata edit journal.
 *
 * Records the before/after state for a single file's metadata edit, enabling undo (restore backup)
 * and redo (re-apply metadata) operations.
 *
 * @property filePath Absolute path of the original file that was edited.
 * @property backupPath Absolute path of the backup copy made before editing.
 * @property configSnapshot The [PhotoScanConfiguration] that was applied during the edit. Stored
 *   for redo support — re-applying the same config produces the same result.
 * @property wasSavedNew If true, this was a SAVE_NEW operation (output in a different directory).
 *   For SAVE_NEW entries, undo means deleting the output file; redo means re-running the write.
 * @property outputFilePath For SAVE_NEW mode, the path of the created output file.
 * @property backImageBackupPath For files that had a back-of-photo image, the backup path of the
 *   `_back.jpg` file (or null if no back image was written).
 * @property backImageOutputPath The output path of the back-of-photo image (for SAVE_NEW mode).
 * @property wasSuccessful Whether the original write operation completed successfully.
 */
@Serializable
data class MetadataEditEntry(
    val filePath: String,
    val backupPath: String,
    val configSnapshot: PhotoScanConfiguration = PhotoScanConfiguration(),
    val wasSavedNew: Boolean = false,
    val outputFilePath: String = "",
    val backImageBackupPath: String? = null,
    val backImageOutputPath: String? = null,
    val wasSuccessful: Boolean = true,
)

/**
 * A journal recording a batch of metadata edits, enabling full undo/redo.
 *
 * Each save operation (whether single-file or batch) creates one journal. The journal contains
 * entries for every file that was modified. Undo restores all backup files; redo re-applies all
 * configs.
 *
 * Journals are serialized to JSON files in `~/.petrie-importer/metadata-journals/` for persistence
 * across sessions.
 *
 * @property id Unique identifier for this journal.
 * @property timestamp Epoch millis when the journal was created.
 * @property sourceFolderPath The source folder (or file) that was being edited.
 * @property outputMode The output mode used for this batch (OVERWRITE or SAVE_NEW).
 * @property entries The list of file entries in this batch.
 * @property undone Whether this journal has been undone.
 */
@Serializable
data class MetadataEditJournal(
    val id: String = DomainDefaults.generateId(),
    val timestamp: Long = DomainDefaults.currentTimeMillis(),
    val sourceFolderPath: String = "",
    val outputMode: String = "OVERWRITE",
    val entries: List<MetadataEditEntry> = emptyList(),
    val undone: Boolean = false,
) {
    /** Human-readable timestamp string. */
    val timestampString: String
        get() = DomainDefaults.formatTimestamp(timestamp)

    /** Number of files that were successfully modified. */
    val successCount: Int
        get() = entries.count { it.wasSuccessful }

    /** Total number of entries (successful or not). */
    val totalCount: Int
        get() = entries.size
}

/** Summary of a metadata edit journal for display in a list. */
data class MetadataEditJournalSummary(
    val id: String,
    val timestamp: Long,
    val timestampString: String,
    val sourceFolderPath: String,
    val outputMode: String,
    val totalCount: Int,
    val successCount: Int,
    val undone: Boolean,
)
