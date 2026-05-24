package org.kryspetrie.fileimport.domain.model

import kotlinx.serialization.Serializable

/**
 * Detailed information about a single imported file.
 *
 * Tracks the complete journey of each file through the import process, including source,
 * destination, naming patterns applied, and verification status.
 */
@Serializable
data class ImportFileDetail(
    /** Unique identifier for this file record */
    val id: String = DomainDefaults.generateId(),

    /** Original source file path */
    val sourcePath: String,

    /** Final destination file path */
    val destinationPath: String,

    /** Destination folder (parent of destinationPath) */
    val destinationFolder: String,

    /** Final filename after applying patterns */
    val finalFilename: String,

    /** Original filename from source */
    val originalFilename: String,

    /** Folder pattern used (e.g., "{yyyy}/{MM}/{dd}") */
    val folderPattern: String = "",

    /** Filename pattern used (e.g., "{yyyy}{MM}{dd}_{original}") */
    val filenamePattern: String = "",

    /** Resolved folder path after pattern expansion */
    val resolvedFolder: String = "",

    /** File size in bytes */
    val fileSize: Long = 0,

    /** File hash (SHA-256) if calculated */
    val fileHash: String? = null,

    /** Whether hash verification was performed */
    val hashVerified: Boolean = false,

    /** Whether hash verification passed (if performed) */
    val hashMatches: Boolean = true,

    /** Whether the file was successfully imported */
    val success: Boolean = true,

    /** Error message if import failed */
    val errorMessage: String? = null,

    /** Whether this file was detected as a duplicate */
    val wasDuplicate: Boolean = false,

    /** Whether the file was skipped (conflict, etc.) */
    val wasSkipped: Boolean = false,

    /** Conflict resolution strategy applied (SKIP, RENAME, REPLACE) */
    val conflictResolution: String? = null,

    /** Whether sidecar files were imported with this file */
    val sidecarsImported: Boolean = false,

    /** List of sidecar files imported */
    val sidecarFiles: List<String> = emptyList(),

    /** Whether source file was deleted after import */
    val sourceDeleted: Boolean = false,

    /** EXIF date if available */
    val exifDate: String? = null,

    /** Camera model if available */
    val cameraModel: String? = null,

    /** Import sequence number */
    val sequenceNumber: Int = 0,
)

/**
 * Complete history entry for an import operation.
 *
 * Contains both summary statistics and detailed per-file information for comprehensive import
 * tracking and auditing.
 */
@Serializable
data class ImportHistoryEntry(
    /** Unique identifier for this import session */
    val id: String = DomainDefaults.generateId(),

    /** Timestamp when import started */
    val timestamp: Long = DomainDefaults.currentTimeMillis(),

    /** Human-readable timestamp string */
    val timestampString: String = "",

    /** Source directory that was imported from */
    val sourcePath: String,

    /** Destination root directory */
    val destinationPath: String,

    /** Import profile name used (if any) */
    val profileName: String = "",

    /** Profile folder pattern used */
    val folderPattern: String = "",

    /** Profile filename pattern used */
    val filenamePattern: String = "",

    /** Total number of files discovered in source */
    val totalFiles: Int,

    /** Number of files successfully imported */
    val successCount: Int,

    /** Number of files that encountered errors */
    val errorCount: Int,

    /** Number of files skipped (conflicts, duplicates, etc.) */
    val skippedCount: Int,

    /** Number of duplicate files detected */
    val duplicateCount: Int,

    /** Number of source files deleted after import */
    val deletedSourceCount: Int = 0,

    /** Total bytes of all files */
    val totalBytes: Long = 0,

    /** Total bytes successfully copied */
    val copiedBytes: Long = 0,

    /** Duration of import in milliseconds */
    val durationMs: Long = 0,

    /** Detailed per-file import records */
    val fileDetails: List<ImportFileDetail> = emptyList(),

    /** Import mode (e.g., "Import New", "Import All", "Import Selected") */
    val importMode: String = "Import All",

    /** Whether hash verification was enabled */
    val verifyHashes: Boolean = false,

    /** Conflict resolution strategy used */
    val conflictResolution: String = "SKIP",

    /** Whether sidecar files were imported */
    val importSidecars: Boolean = false,

    /** Notes or comments about this import */
    val notes: String = "",
) {
    companion object {
        fun createTimestampString(timestamp: Long): String = DomainDefaults.formatTimestamp(timestamp)
    }
}
