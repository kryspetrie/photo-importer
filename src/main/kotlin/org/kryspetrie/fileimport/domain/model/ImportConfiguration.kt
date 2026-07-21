package org.kryspetrie.fileimport.domain.model

import kotlinx.serialization.Serializable

/**
 * Complete configuration for photo/video import operations.
 *
 * Encapsulates all settings that control how files are imported, organized, named, and processed.
 * Marked `@Serializable` for JSON persistence in import profiles.
 *
 * @property scanMode Whether to use standard import or photo scan mode
 * @property folderPattern Pattern for creating subfolders (e.g., `{yyyy-MM-dd}`)
 * @property fileNamePattern Pattern for generating filenames when [preserveOriginalName] is false
 * @property fileNameExtension File extension pattern (usually `{ext}`)
 * @property preserveOriginalName If true, keep original filename
 * @property createSubfolders If true, create folder hierarchy using [folderPattern]
 * @property dateSource Which date to use for date-based patterns
 * @property conflictResolution Strategy for handling filename conflicts
 * @property lastDestinationPath Persisted destination folder path
 * @property detectTransferredByHash Detect already-imported files by hash
 * @property detectTransferredByExif Detect duplicates by EXIF metadata
 * @property detectVisualDuplicates Use perceptual hashing for visual duplicates
 * @property perceptualHashThreshold Similarity threshold for pHash matching (0.0-1.0)
 * @property useSurfMatching Enable SURF feature matching for advanced duplicate detection
 * @property surfMatchThreshold Number of matching features required for SURF match
 * @property rawJpegPairMode How to handle RAW+JPEG pairs
 * @property keepPairsTogether Keep RAW+JPEG pairs in same folder
 * @property importSidecars Import sidecar files (.xmp, .thm, etc.) alongside media
 * @property verifyAfterCopy Verify file integrity after copy
 * @property deleteAfterImport Delete source files after successful import
 * @see FolderPresets Built-in folder pattern presets
 * @see FilenamePresets Built-in filename pattern presets
 * @see NamePlaceholders Available placeholders for patterns
 */
@Serializable
data class ImportConfiguration(
    val scanMode: ScanMode = ScanMode.STANDARD,
    val folderPattern: String = "{yyyy-MM-dd}",
    val fileNamePattern: String = "{original}",
    val fileNameExtension: String = "{ext}",
    val preserveOriginalName: Boolean = true,
    val createSubfolders: Boolean = true,
    val dateSource: DateSource = DateSource.EXIF_DATE,
    val conflictResolution: ConflictResolution = ConflictResolution.RENAME,
    val lastDestinationPath: String = "",
    val detectTransferredByHash: Boolean = true,
    val detectTransferredByExif: Boolean = false,
    val detectVisualDuplicates: Boolean = false,
    val perceptualHashThreshold: Float = 0.95f,
    val useSurfMatching: Boolean = false,
    val surfMatchThreshold: Int = 30,
    val rawJpegPairMode: RawJpegPairMode = RawJpegPairMode.IMPORT_BOTH,
    val keepPairsTogether: Boolean = true,
    val importSidecars: Boolean = true,
    val verifyAfterCopy: Boolean = true,
    val deleteAfterImport: Boolean = false,

    /** When true, automatically detect and correct photo orientation during import. */
    val autoOrientEnabled: Boolean = false,
)
