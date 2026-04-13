package org.kryspetrie.fileimport.domain.model

import kotlinx.serialization.Serializable

/**
 * Complete configuration for photo/video import operations.
 *
 * This data class encapsulates all settings that control how files are imported, organized, named,
 * and processed. It's the core configuration object that drives the entire import workflow.
 *
 * ## Configuration Categories
 *
 * ### 1. Organization (Folder Structure)
 * - [folderPattern]: How to organize files into subfolders
 * - [createSubfolders]: Whether to create folder hierarchy or flat structure
 * - [dateSource]: Which date to use for date-based patterns
 *
 * ### 2. Naming (File Names)
 * - [fileNamePattern]: Pattern for generating filenames
 * - [preserveOriginalName]: Keep original filename vs. generate new name
 * - [fileNameExtension]: File extension handling
 *
 * ### 3. Conflict Handling
 * - [conflictResolution]: What to do when destination file already exists
 *
 * ### 4. Duplicate Detection
 * - [detectTransferredByHash]: Detect already-imported files by hash
 * - [detectTransferredByExif]: Detect by EXIF metadata
 * - [detectVisualDuplicates]: Use perceptual hashing for visual duplicates
 *
 * ### 5. RAW+JPEG Handling
 * - [rawJpegPairMode]: How to handle RAW+JPEG pairs
 * - [keepPairsTogether]: Keep pairs in same folder
 *
 * ### 6. Sidecar Files
 * - [importSidecars]: Import accompanying sidecar files (.xmp, .thm, etc.)
 *
 * ### 7. Post-Import
 * - [verifyAfterCopy]: Verify file integrity after copy
 * - [deleteAfterImport]: Delete source files after successful import
 *
 * ### 8. Scan Mode
 * - [scanMode]: Whether to use standard import or photo scan mode
 *
 * ## Usage Example
 *
 * ```kotlin
 * val config = ImportConfiguration(
 *     // Organize by date
 *     folderPattern = "{yyyy}/{MM}/{dd}",
 *     createSubfolders = true,
 *
 *     // Keep original filenames
 *     preserveOriginalName = true,
 *
 *     // Safety features
 *     verifyAfterCopy = true,
 *     detectVisualDuplicates = true,
 *
 *     // Don't delete source
 *     deleteAfterImport = false
 * )
 * ```
 *
 * ## Serialization
 *
 * Marked with `@Serializable` for JSON persistence in import profiles. All settings are preserved
 * when saving/loading profiles.
 *
 * @property folderPattern Pattern for creating subfolders. Uses placeholders like `{yyyy-MM-dd}`,
 *   `{camera}`, etc. See [FolderPresets] for built-in patterns and examples.
 * @property fileNamePattern Pattern for generating filenames. Used when [preserveOriginalName] is
 *   false. See [FilenamePresets] for built-in patterns.
 * @property fileNameExtension File extension pattern. Usually `{ext}` to preserve original
 *   extension.
 * @property preserveOriginalName If true, keep original filename. If false, generate new name using
 *   [fileNamePattern].
 * @property createSubfolders If true, create folder hierarchy using [folderPattern]. If false, put
 *   all files in destination root (flat).
 * @property dateSource Which date to use for date-based patterns. Options: EXIF date (when taken),
 *   file modified, or file created.
 * @property conflictResolution Strategy for handling filename conflicts: Rename (add number), Skip,
 *   Replace, or Ask User.
 * @property detectTransferredByHash Detect already-imported files by comparing file hashes.
 *   Prevents importing same file twice.
 * @property detectTransferredByExif Detect duplicates by EXIF metadata (date, camera, dimensions).
 *   Catches edited versions of same photo.
 * @property detectVisualDuplicates Use perceptual hashing to find visually similar images. Catches
 *   resizes, crops, slight edits.
 * @property perceptualHashThreshold Similarity threshold for perceptual hash matching. 1.0 = exact
 *   match, 0.95 = very similar, 0.8 = similar.
 * @property useSurfMatching Enable SURF feature matching for advanced duplicate detection. Slower
 *   but more accurate than perceptual hash.
 * @property surfMatchThreshold Number of matching features required for SURF match. Higher = more
 *   strict (fewer false positives).
 * @property rawJpegPairMode How to handle RAW+JPEG pairs: Import both, RAW only, or JPEG only.
 * @property keepPairsTogether If true, keep RAW+JPEG pairs in same folder even if pattern would
 *   separate them.
 * @property importSidecars If true, import sidecar files (.xmp, .thm, .lrv, etc.) alongside media
 *   files.
 * @property verifyAfterCopy If true, calculate hash after copy and compare to source. Ensures file
 *   integrity. Recommended for safety.
 * @property deleteAfterImport If true, delete source files after successful import and
 *   verification. Use with caution!
 * @see ImportProfile Contains ImportConfiguration as part of saved profile
 * @see FolderPresets Built-in folder pattern presets
 * @see FilenamePresets Built-in filename pattern presets
 * @see NamePlaceholders Available placeholders for patterns
 */
@Serializable
data class ImportConfiguration(
    // ==================== SCAN MODE ====================

    /**
     * Scan mode for import operations.
     *
     * Determines how files are scanned and processed:
     * - [ScanMode.STANDARD]: Traditional import with duplicate detection
     * - [ScanMode.PHOTO_SCAN]: Specialized for scanning photos on background, detects corners of
     *   multiple photos and exports them individually
     *
     * @see ScanMode
     */
    val scanMode: ScanMode = ScanMode.STANDARD,

    // ==================== ORGANIZATION ====================

    /**
     * Folder organization pattern.
     *
     * Defines how files are organized into subfolders using placeholders:
     * - `{yyyy-MM-dd}` → `2024-03-15/`
     * - `{yyyy}/{MM}` → `2024/03/`
     * - `{camera}/{yyyy-MM-dd}` → `Canon_EOS_R5/2024-03-15/`
     *
     * Combined with [createSubfolders] to determine final structure.
     *
     * Default: `{yyyy-MM-dd}` (flat date-based folders)
     *
     * @see FolderPresets Built-in pattern examples
     * @see NamePlaceholders Available placeholders
     */
    val folderPattern: String = "{yyyy-MM-dd}",

    /**
     * Filename pattern (used when not preserving original names).
     *
     * Examples:
     * - `{yyyy}{MM}{dd}_{original}` → `20240315_IMG_1234.jpg`
     * - `{camera}_{yyyy}{MM}{dd}_{counter}` → `CanonR5_20240315_0001.jpg`
     *
     * Only used when [preserveOriginalName] is false.
     *
     * Default: `{original}` (same as preserving original)
     *
     * @see FilenamePresets Built-in pattern examples
     * @see NamePlaceholders Available placeholders
     */
    val fileNamePattern: String = "{original}",

    /**
     * File extension pattern.
     *
     * Usually `{ext}` to preserve original extension (.jpg, .cr3, .mp4, etc.). Can be customized
     * for specific needs (e.g., force .jpeg instead of .jpg).
     *
     * Default: `{ext}`
     */
    val fileNameExtension: String = "{ext}",

    /**
     * Whether to preserve original filenames.
     * - **true**: Keep original filename (IMG_1234.CR3)
     * - **false**: Generate new name using [fileNamePattern]
     *
     * Most users prefer preserving original names for familiarity.
     *
     * Default: `true`
     */
    val preserveOriginalName: Boolean = true,

    /**
     * Whether to create subfolder hierarchy.
     * - **true**: Create folders using [folderPattern]
     * - **false**: Put all files in destination root (flat structure)
     *
     * Flat structure is simpler but can lead to thousands of files in one folder.
     *
     * Default: `true`
     */
    val createSubfolders: Boolean = true,

    /**
     * Source of date for date-based patterns.
     *
     * Determines which date is used for `{yyyy}`, `{MM}`, `{dd}` placeholders:
     * - **EXIF_DATE**: When photo was taken (most accurate for photos)
     * - **FILE_MODIFIED_DATE**: When file was last modified
     * - **FILE_CREATED_DATE**: When file was created on filesystem
     *
     * For videos without EXIF, falls back to file dates.
     *
     * Default: `EXIF_DATE`
     */
    val dateSource: DateSource = DateSource.EXIF_DATE,

    // ==================== CONFLICT HANDLING ====================

    /**
     * Strategy for resolving filename conflicts.
     *
     * When a file with the same name exists at destination:
     * - **RENAME**: Add number suffix (IMG_001.jpg → IMG_002.jpg)
     * - **SKIP**: Don't import this file (keep existing)
     * - **REPLACE**: Overwrite existing file (dangerous!)
     * - **ASK_USER**: Prompt user for each conflict (slow)
     *
     * Default: `RENAME` (safest automatic option)
     */
    val conflictResolution: ConflictResolution = ConflictResolution.RENAME,

    // ==================== DESTINATION (PERSISTED) ====================

    /**
     * Last used destination folder path.
     *
     * Persisted to remember user's preferred output location across sessions. Reduces friction for
     * repeated imports to the same destination (e.g., "Photos/Imports").
     *
     * Not part of the saved configuration/profile - stored separately in AppSettings to survive
     * profile changes.
     *
     * Default: Empty string (user must select destination each session)
     */
    val lastDestinationPath: String = "",

    // ==================== DUPLICATE DETECTION ====================

    /**
     * Detect already-transferred files by hash.
     *
     * Compares file hash against import history database. Catches exact duplicates (same file
     * imported before).
     *
     * Fast and reliable for preventing accidental re-imports.
     *
     * Default: `true`
     */
    val detectTransferredByHash: Boolean = true,

    /**
     * Detect duplicates by EXIF metadata.
     *
     * Compares EXIF data (date, camera, dimensions, etc.) to find:
     * - Same photo with different filename
     * - Edited versions of same photo
     * - Photos from same shot (burst mode)
     *
     * Slower than hash but catches more duplicates.
     *
     * Default: `false`
     */
    val detectTransferredByExif: Boolean = false,

    /**
     * Enable visual duplicate detection.
     *
     * Uses perceptual hashing to find visually similar images:
     * - Resized versions
     * - Cropped versions
     * - Slightly edited (color, exposure)
     * - Different format of same image
     *
     * More computationally intensive but finds duplicates other methods miss.
     *
     * Default: `false`
     */
    val detectVisualDuplicates: Boolean = false,

    /**
     * Similarity threshold for perceptual hash matching.
     *
     * Range: 0.0 to 1.0
     * - 1.0: Exact match only
     * - 0.95: Very similar (recommended)
     * - 0.9: Similar
     * - 0.8: Loosely similar (may have false positives)
     *
     * Lower threshold = more duplicates found (but more false positives)
     *
     * Default: `0.95`
     */
    val perceptualHashThreshold: Float = 0.95f,

    /**
     * Enable SURF feature matching for duplicate detection.
     *
     * SURF (Speeded Up Robust Features) detects and matches visual features between images. More
     * accurate than perceptual hash but significantly slower.
     *
     * Best for:
     * - Finding duplicates with significant edits
     * - Matching cropped/rotated images
     * - High-value photo libraries where accuracy is critical
     *
     * Default: `false` (too slow for general use)
     */
    val useSurfMatching: Boolean = false,

    /**
     * Number of matching features for SURF match.
     *
     * Higher threshold = more strict matching (fewer false positives) Lower threshold = more
     * lenient (catches more duplicates)
     *
     * Typical range: 20-50
     *
     * Default: `30`
     */
    val surfMatchThreshold: Int = 30,

    // ==================== RAW+JPEG HANDLING ====================

    /**
     * How to handle RAW+JPEG pairs.
     *
     * Many cameras save both RAW and JPEG for each shot. This setting determines which files to
     * import:
     * - **IMPORT_BOTH**: Import both RAW and JPEG
     * - **RAW_ONLY**: Import only RAW files (skip JPEGs)
     * - **JPEG_ONLY**: Import only JPEG files (skip RAWs)
     *
     * Professional photographers often import both for flexibility. Casual users may prefer JPEG
     * only to save space.
     *
     * Default: `IMPORT_BOTH`
     */
    val rawJpegPairMode: RawJpegPairMode = RawJpegPairMode.IMPORT_BOTH,

    /**
     * Keep RAW+JPEG pairs together in same folder.
     *
     * If true, ensures RAW and JPEG from same shot stay in same folder even if folder pattern would
     * normally separate them.
     *
     * Important for workflows that process RAW+JPEG together.
     *
     * Default: `true`
     */
    val keepPairsTogether: Boolean = true,

    // ==================== SIDECAR HANDLING ====================

    /**
     * Import sidecar files alongside media files.
     *
     * Sidecar files contain additional data:
     * - **.xmp**: Lightroom/Camera Raw edits
     * - **.thm**: Thumbnail files (GoPro, etc.)
     * - **.lrv**: Low-resolution video (GoPro)
     * - **.aae**: iOS photo edits
     * - **.pp3**: Capture One edits
     *
     * Importing sidecars preserves edits and metadata.
     *
     * Default: `true`
     */
    val importSidecars: Boolean = true,

    // ==================== POST-IMPORT ====================

    /**
     * Verify file integrity after copy.
     *
     * After copying each file:
     * 1. Calculate hash of copied file
     * 2. Compare to hash of source file
     * 3. If mismatch, retry copy or report error
     *
     * Ensures files are not corrupted during transfer. Highly recommended for important photos.
     *
     * Adds time to import but provides safety.
     *
     * Default: `true`
     */
    val verifyAfterCopy: Boolean = true,

    /**
     * Delete source files after successful import.
     *
     * After file is copied and verified:
     * 1. Delete original from source (camera, SD card, folder)
     * 2. Update import history
     *
     * ⚠️ **DANGER**: Use with extreme caution!
     * - Only enable after confirming files imported correctly
     * - Ensure you have backups
     * - Consider keeping source until next backup
     *
     * Default: `false` (safer)
     */
    val deleteAfterImport: Boolean = false
)

/**
 * Dedicated settings for duplicate detection and resolution.
 *
 * Separate from [ImportConfiguration] to allow fine-tuning duplicate detection independently from
 * import settings. Used primarily in the Duplicate Scanner screen.
 *
 * @property enableHashDeduplication Detect exact duplicates by file hash
 * @property enablePerceptualHash Detect visual duplicates by perceptual hash
 * @property enableExifDeduplication Detect duplicates by EXIF metadata
 * @property enableFilenameDeduplication Detect duplicates by filename only
 * @property ignoreDifferentFileTypes Don't match across different file types
 * @property perceptualHashThreshold Similarity threshold for pHash matching
 * @property autoResolveDuplicates Automatically select which duplicate to keep
 * @property enableSurfMatching Enable SURF feature matching
 * @property surfMatchThreshold SURF match threshold
 * @see ImportConfiguration Main import configuration
 */
@Serializable
data class DeduplicationSettings(
    val enableHashDeduplication: Boolean = true,
    val enablePerceptualHash: Boolean = true,
    val enableExifDeduplication: Boolean = true,
    val enableFilenameDeduplication: Boolean = false,
    val ignoreDifferentFileTypes: Boolean = true,
    val perceptualHashThreshold: Float = 0.95f,
    val autoResolveDuplicates: Boolean = false,
    val enableSurfMatching: Boolean = false,
    val surfMatchThreshold: Int = 30
)

/**
 * Source of date for date-based folder/filename patterns.
 *
 * Determines which timestamp is used when resolving date placeholders like `{yyyy}`, `{MM}`,
 * `{dd}`, `{HH}`, etc.
 *
 * ## Options
 * - **EXIF_DATE**: Use EXIF "DateTimeOriginal" tag (when photo was taken)
 *     - Most accurate for photos
 *     - Reflects actual moment of capture
 *     - Falls back to file date if no EXIF
 * - **FILE_MODIFIED_DATE**: Use file's last modified timestamp
 *     - Works for all file types
 *     - May not reflect capture date
 *     - Changes if file is edited
 * - **FILE_CREATED_DATE**: Use file's creation timestamp
 *     - When file was created on current filesystem
 *     - May not reflect capture date
 *     - Lost when copying to new filesystem
 *
 * ## Recommendation
 *
 * Use **EXIF_DATE** for photos (most accurate). For videos without EXIF, system falls back to file
 * dates automatically.
 *
 * @see ImportConfiguration.dateSource Configuration property using this enum
 */
enum class DateSource {
  EXIF_DATE,
  FILE_MODIFIED_DATE,
  FILE_CREATED_DATE
}

/**
 * Strategy for resolving filename conflicts at destination.
 *
 * Determines what happens when a file with the same name already exists at the import destination.
 *
 * ## Options
 * - **RENAME**: Add numeric suffix to avoid conflict
 *     - IMG_001.jpg → IMG_002.jpg
 *     - Safe, automatic, preserves both files
 *     - Recommended for most users
 * - **SKIP**: Don't import conflicting file
 *     - Keep existing file at destination
 *     - Source file is not imported
 *     - Logged in import results
 * - **REPLACE**: Overwrite existing file
 *     - ⚠️ Destructive - existing file is lost
 *     - Only use with backups
 *     - Fastest option (no renaming)
 * - **ASK_USER**: Prompt user for each conflict
 *     - Most control
 *     - Slowest option
 *     - Not practical for large imports
 *
 * ## Recommendation
 *
 * Use **RENAME** for safety and automation. Use **SKIP** if you want to manually review conflicts
 * later. Avoid **REPLACE** unless you have backups.
 *
 * @see ImportConfiguration.conflictResolution Configuration property using this enum
 */
enum class ConflictResolution {
  RENAME,
  SKIP,
  REPLACE,
  ASK_USER
}

/**
 * Import mode selection.
 *
 * Determines which files from the source are included in the import.
 *
 * ## Options
 * - **ALL**: Import all files found in source
 *     - No filtering
 *     - Fastest option
 *     - May include already-imported files (unless duplicate detection enabled)
 * - **NEW**: Import only files not previously imported
 *     - Checks import history and hash cache
 *     - Skips already-imported files
 *     - Good for incremental imports from same source
 * - **SELECT**: User manually selects which files to import
 *     - Shows file browser with thumbnails
 *     - User checks/unchecks files
 *     - Most control, slowest workflow
 *
 * ## Usage
 *
 * Selected in Import screen via radio buttons or dropdown. Affects which files are shown in preview
 * and imported.
 *
 * @see ImportScreen UI where mode is selected
 */
enum class ImportMode {
  ALL,
  NEW,
  SELECT
}

/**
 * RAW+JPEG pair handling mode.
 *
 * Many cameras (especially DSLRs and mirrorless) can save both RAW and JPEG versions of each photo.
 * This enum determines which files to import.
 *
 * ## Options
 * - **IMPORT_BOTH**: Import both RAW and JPEG files
 *     - Maximum flexibility for editing
 *     - Uses more storage space
 *     - Professional workflow
 *     - Example: Import IMG_001.CR3 and IMG_001.JPG
 * - **RAW_ONLY**: Import only RAW files
 *     - Skip accompanying JPEGs
 *     - Save storage space
 *     - For photographers who always edit RAW
 *     - Example: Import IMG_001.CR3, skip IMG_001.JPG
 * - **JPEG_ONLY**: Import only JPEG files
 *     - Skip RAW files
 *     - Minimum storage space
 *     - For casual photographers who don't edit
 *     - Example: Import IMG_001.JPG, skip IMG_001.CR3
 *
 * ## Pair Detection
 *
 * Pairs are detected by:
 * - Same base filename (IMG_001.CR3 + IMG_001.JPG)
 * - Same timestamp (within 1 second)
 * - Same dimensions
 *
 * @property IMPORT_BOTH Import both RAW and JPEG files
 * @property RAW_ONLY Import only RAW files, skip JPEGs
 * @property JPEG_ONLY Import only JPEG files, skip RAWs
 * @see ImportConfiguration.rawJpegPairMode Configuration property using this enum
 */
@Serializable
enum class RawJpegPairMode {
  IMPORT_BOTH,
  RAW_ONLY,
  JPEG_ONLY
}

/**
 * A named preset for folder or filename patterns.
 *
 * Presets allow users to save and reuse custom patterns. Built-in presets are provided by
 * [FolderPresets] and [FilenamePresets], and users can create their own custom presets.
 *
 * ## Usage
 *
 * ```kotlin
 * // Built-in preset
 * val preset = FolderPresets.builtIn.first { it.name == "Year-Month" }
 *
 * // Custom user preset
 * val customPreset = PatternPreset(
 *     name = "Client / Date",
 *     pattern = "{client}/{yyyy-MM-dd}",
 *     isBuiltIn = false
 * )
 * ```
 *
 * @property name Display name shown in preset selector dropdown
 * @property pattern The actual pattern string with placeholders
 * @property isBuiltIn Whether this is a built-in preset (true) or user-created (false)
 * @see FolderPresets Built-in folder pattern presets
 * @see FilenamePresets Built-in filename pattern presets
 * @see AppSettings.savedFolderPresets User-saved folder presets
 * @see AppSettings.savedFilenamePresets User-saved filename presets
 */
@Serializable
data class PatternPreset(val name: String, val pattern: String, val isBuiltIn: Boolean = false)

/**
 * Built-in folder pattern presets.
 *
 * Provides commonly-used folder organization patterns as ready-to-use presets. Users can select
 * these from a dropdown instead of typing patterns manually.
 *
 * ## Included Patterns
 * - **Flat date**: `{yyyy-MM-dd}` → `2024-01-15/`
 * - **Year-Month**: `{yyyy-MM}` → `2024-01/`
 * - **Year / Date**: `{yyyy}/{yyyy-MM-dd}` → `2024/2024-01-15/`
 * - **Year / Month**: `{yyyy}/{MM}` → `2024/01/`
 * - **Year / Month / Day**: `{yyyy}/{MM}/{dd}` → `2024/01/15/`
 * - **Year / Month-Day**: `{yyyy}/{MM}-{dd}` → `2024/01-15/`
 * - **Camera / Date**: `{camera}/{yyyy-MM-dd}` → `Canon EOS R5/2024-01-15/`
 * - **Year / Camera**: `{yyyy}/{camera}` → `2024/Canon EOS R5/`
 * - **Year / Type**: `{yyyy}/{type}` → `2024/Photos/` or `2024/Videos/`
 * - **Year / Type / Date**: `{yyyy}/{type}/{yyyy-MM-dd}` → `2024/Photos/2024-01-15/`
 *
 * ## Usage
 *
 * Display in UI dropdown:
 * ```kotlin
 * @Composable
 * fun FolderPatternSelector(selectedPattern: String, onPatternChange: (String) -> Unit) {
 *     ExposedDropdownMenuBox(...) {
 *         FolderPresets.builtIn.forEach { preset ->
 *             DropdownMenuItem(
 *                 text = { Text(preset.name) },
 *                 onClick = { onPatternChange(preset.pattern) }
 *             )
 *         }
 *     }
 * }
 * ```
 *
 * @see PatternPreset Preset data structure
 * @see NamePlaceholders Available placeholders for patterns
 * @see FilenamePresets Built-in filename pattern presets
 */
object FolderPresets {
  val builtIn =
      listOf(
          PatternPreset("Flat date", "{yyyy-MM-dd}", isBuiltIn = true),
          PatternPreset("Year-Month", "{yyyy-MM}", isBuiltIn = true),
          PatternPreset("Year / Date", "{yyyy}/{yyyy-MM-dd}", isBuiltIn = true),
          PatternPreset("Year / Month", "{yyyy}/{MM}", isBuiltIn = true),
          PatternPreset("Year / Month / Day", "{yyyy}/{MM}/{dd}", isBuiltIn = true),
          PatternPreset("Year / Month-Day", "{yyyy}/{MM}-{dd}", isBuiltIn = true),
          PatternPreset("Camera / Date", "{camera}/{yyyy-MM-dd}", isBuiltIn = true),
          PatternPreset("Year / Camera", "{yyyy}/{camera}", isBuiltIn = true),
          PatternPreset("Year / Type", "{yyyy}/{type}", isBuiltIn = true),
          PatternPreset("Year / Type / Date", "{yyyy}/{type}/{yyyy-MM-dd}", isBuiltIn = true),
      )

  /**
   * Example outputs for each pattern.
   *
   * Map of pattern → example output path. Useful for showing users what the pattern produces.
   *
   * Example:
   * ```
   * "{yyyy-MM-dd}" → "dest/2024-01-15/IMG_0001.jpg"
   * "{yyyy}/{MM}" → "dest/2024/01/IMG_0001.jpg"
   * ```
   */
  val examples =
      mapOf(
          "{yyyy-MM-dd}" to "dest/2024-01-15/IMG_0001.jpg",
          "{yyyy-MM}" to "dest/2024-01/IMG_0001.jpg",
          "{yyyy}/{yyyy-MM-dd}" to "dest/2024/2024-01-15/IMG_0001.jpg",
          "{yyyy}/{MM}" to "dest/2024/01/IMG_0001.jpg",
          "{yyyy}/{MM}/{dd}" to "dest/2024/01/15/IMG_0001.jpg",
          "{yyyy}/{MM}-{dd}" to "dest/2024/01-15/IMG_0001.jpg",
          "{camera}/{yyyy-MM-dd}" to "dest/Canon EOS R5/2024-01-15/IMG_0001.jpg",
          "{yyyy}/{camera}" to "dest/2024/Canon EOS R5/IMG_0001.jpg",
          "{yyyy}/{type}" to "dest/2024/Photos/ or dest/2024/Videos/",
      )
}

/**
 * Sidecar file extensions that should be imported with media files.
 *
 * Sidecar files contain additional data associated with media files:
 * - Edit instructions (Lightroom, Capture One)
 * - Thumbnails and previews
 * - Metadata and annotations
 * - Proxy files
 *
 * ## Supported Extensions
 * - **.xmp**: Adobe XMP metadata (Lightroom, Camera Raw, Photoshop)
 * - **.thm**: Thumbnail files (Canon, GoPro, drones)
 * - **.lrv**: Low-resolution video proxy (GoPro)
 * - **.aae**: iOS photo edit instructions
 * - **.pp3**: Capture One processing instructions
 * - **.dop**: DxO PhotoLab settings
 * - **.cos**: CosmiColor settings
 * - **.nks**: Nikon settings
 *
 * ## Usage
 *
 * Check if a file is a sidecar:
 * ```kotlin
 * if (SidecarExtensions.isSidecar("xmp")) {
 *     // Import with associated media file
 * }
 * ```
 *
 * @see ImportConfiguration.importSidecars Configuration to enable sidecar import
 */
object SidecarExtensions {
  val extensions = setOf("xmp", "thm", "lrv", "aae", "pp3", "dop", "cos", "nks")

  /**
   * Check if a file extension is a known sidecar type.
   *
   * @param extension File extension without dot (e.g., "xmp", "jpg")
   * @return True if extension is a known sidecar type
   */
  fun isSidecar(extension: String): Boolean = extensions.contains(extension.lowercase())
}

/**
 * Built-in filename pattern presets.
 *
 * Provides commonly-used filename patterns as ready-to-use presets. Users select these instead of
 * typing patterns manually.
 *
 * ## Included Patterns
 * - **Original**: `{original}` → `IMG_1234.CR3`
 * - **Date + Original**: `{yyyy}{MM}{dd}_{original}` → `20240115_IMG_1234.CR3`
 * - **Date-Time**: `{yyyy}{MM}{dd}_{HH}{mm}{ss}` → `20240115_143022.CR3`
 * - **Date + Counter**: `{yyyy}{MM}{dd}_{counter}` → `20240115_0001.CR3`
 * - **Camera + Date**: `{camera}_{yyyy}{MM}{dd}_{counter}` → `CanonR5_20240115_0001.CR3`
 * - **Full EXIF**: `{yyyy}{MM}{dd}_{HH}{mm}{ss}_{camera}_ISO{iso}` →
 *   `20240115_143022_CanonR5_ISO400.CR3`
 *
 * ## Usage
 *
 * Selected in Import screen when [ImportConfiguration.preserveOriginalName] is false.
 *
 * @see PatternPreset Preset data structure
 * @see NamePlaceholders Available placeholders for patterns
 * @see FolderPresets Built-in folder pattern presets
 */
object FilenamePresets {
  val builtIn =
      listOf(
          PatternPreset("Original", "{original}", isBuiltIn = true),
          PatternPreset("Date + Original", "{yyyy}{MM}{dd}_{original}", isBuiltIn = true),
          PatternPreset("Date-Time", "{yyyy}{MM}{dd}_{HH}{mm}{ss}", isBuiltIn = true),
          PatternPreset("Date + Counter", "{yyyy}{MM}{dd}_{counter}", isBuiltIn = true),
          PatternPreset("Camera + Date", "{camera}_{yyyy}{MM}{dd}_{counter}", isBuiltIn = true),
          PatternPreset(
              "Full EXIF", "{yyyy}{MM}{dd}_{HH}{mm}{ss}_{camera}_ISO{iso}", isBuiltIn = true),
      )
}

/**
 * Available placeholders for folder and filename patterns.
 *
 * Placeholders are replaced with actual values during import. Different placeholders are available
 * for folder patterns vs. filename patterns.
 *
 * ## Folder Placeholders
 *
 * Used in [ImportConfiguration.folderPattern]:
 * - `{yyyy-MM-dd}`: Year-Month-Day (2024-01-15)
 * - `{yyyy}`: Year (2024)
 * - `{MM}`: Month (01-12)
 * - `{dd}`: Day (01-31)
 * - `{camera}`: Camera model (Canon_EOS_R5)
 * - `{type}`: Media type (Photos/Videos)
 *
 * ## Filename Placeholders
 *
 * Used in [ImportConfiguration.fileNamePattern]:
 * - `{original}`: Original filename (IMG_1234)
 * - `{ext}`: File extension (jpg, cr3, mp4)
 * - `{yyyy}{MM}{dd}`: Date components
 * - `{HH}{mm}{ss}`: Time components
 * - `{camera}`: Camera model
 * - `{iso}`: ISO value (400)
 * - `{counter}`: Sequential number (0001)
 * - And many more...
 *
 * ## Usage in UI
 *
 * Show available placeholders as help text:
 * ```kotlin
 * @Composable
 * fun PatternHelp() {
 *     NamePlaceholders.filePlaceholders.forEach { (placeholder, description) ->
 *         Text("$placeholder - $description")
 *     }
 * }
 * ```
 *
 * @see FolderPresets Examples using folder placeholders
 * @see FilenamePresets Examples using filename placeholders
 */
object NamePlaceholders {
  /**
   * Placeholders available in folder patterns.
   *
   * Map of placeholder → description. Used for help text and validation.
   */
  val folderPlaceholders =
      mapOf(
          "{yyyy-MM-dd}" to "Year-Month-Day",
          "{yyyy-MM}" to "Year-Month",
          "{yyyy}" to "Year (4 digits)",
          "{yy}" to "Year (2 digits)",
          "{MM}" to "Month (01-12)",
          "{dd}" to "Day (01-31)",
          "{HH}" to "Hour (00-23)",
          "{mm}" to "Minute (00-59)",
          "{camera}" to "Camera model",
          "{make}" to "Camera make",
          "{lens}" to "Lens model",
          "{type}" to "Media type (Photos / Videos)",
      )

  /**
   * Placeholders available in filename patterns.
   *
   * Map of placeholder → description. More extensive than folder placeholders, includes EXIF data.
   */
  val filePlaceholders =
      mapOf(
          "{original}" to "Original filename",
          "{ext}" to "File extension",
          "{yyyy}" to "Year",
          "{MM}" to "Month",
          "{dd}" to "Day",
          "{HH}" to "Hour",
          "{mm}" to "Minute",
          "{ss}" to "Second",
          "{camera}" to "Camera model",
          "{make}" to "Camera make",
          "{lens}" to "Lens model",
          "{iso}" to "ISO value",
          "{aperture}" to "Aperture (f/x)",
          "{shutter}" to "Shutter speed",
          "{focal}" to "Focal length (mm)",
          "{focal35}" to "35mm equiv. focal length",
          "{width}" to "Image/video width (px)",
          "{height}" to "Image/video height (px)",
          "{counter}" to "Sequential counter",
          "{type}" to "Media type (photo / video)",
          "{duration}" to "Video duration (e.g. 1m30s)",
          "{fps}" to "Video frame rate",
          "{codec}" to "Video codec",
          // ── EXIF Metadata Placeholders ──
          "{date}" to "Date taken (YYYYMMDD)",
          "{time}" to "Time taken (HHMMSS)",
          "{datetime}" to "Date & time (YYYYMMDD_HHMMSS)",
          "{date_orig}" to "Date original (YYYYMMDD)",
          "{make_raw}" to "Camera make (raw tag)",
          "{model_raw}" to "Camera model (raw tag)",
          "{lens_model}" to "Lens model (raw tag)",
          "{iso_speed}" to "ISO speed (raw tag)",
          "{f_number}" to "F-number (aperture raw)",
          "{exposure}" to "Exposure time (raw)",
          "{focal_length}" to "Focal length (raw mm)",
          "{gps_lat}" to "GPS latitude",
          "{gps_lon}" to "GPS longitude",
          "{gps_alt}" to "GPS altitude",
          "{orientation}" to "Orientation (1-8)",
          "{flash}" to "Flash (fired/not fired)",
          "{white_balance}" to "White balance (AWB/custom)",
          "{exposure_program}" to "Exposure program",
          "{metering_mode}" to "Metering mode",
          "{color_space}" to "Color space (sRGB/Adobe)",
          "{software}" to "Software",
          "{artist}" to "Artist/author",
          "{copyright}" to "Copyright",
          "{rating}" to "Rating (1-5 stars)",
          "{label}" to "Label/tag",
          "{comment}" to "Comment/description",
      )

  /**
   * Standard EXIF metadata fields (Extensible IFD Tag definitions).
   *
   * These represent common EXIF tags that can be embedded in image files. Values are extracted from
   * the EXIF data block and can be used in filename/folder patterns.
   *
   * @see ExifConstants Standard tag IDs (IFD0, ExifIFD, GPS IFD, InteropIFD)
   */
  object ExifFields {
    /** DateTimeOriginal - Date/Time of original image data was generated. */
    const val DATE_ORIGINAL = "DateTimeOriginal"

    /** DateTimeDigitized - Date/Time image data was stored. */
    const val DATE_DIGITIZED = "DateTimeDigitized"

    /** DateTime - Date/Time of last modification. */
    const val DATE_MODIFIED = "DateTime"

    /** Make - Manufacturer of camera. */
    const val MAKE = "Make"

    /** Model - Model name/number of camera. */
    const val MODEL = "Model"

    /** LensModel - Model of lens. */
    const val LENS_MODEL = "LensModel"

    /** LensMake - Manufacturer of lens. */
    const val LENS_MAKE = "LensMake"

    /** Software - Name and version of software. */
    const val SOFTWARE = "Software"

    /** Artist - Name of camera owner. */
    const val ARTIST = "Artist"

    /** Copyright - Copyright notice. */
    const val COPYRIGHT = "Copyright"

    /** ImageDescription - Description of image. */
    const val IMAGE_DESCRIPTION = "ImageDescription"

    /** UserComment - User comments. */
    const val USER_COMMENT = "UserComment"

    /** Orientation - Image orientation. */
    const val ORIENTATION = "Orientation"

    /** XResolution - Horizontal resolution. */
    const val X_RESOLUTION = "XResolution"

    /** YResolution - Vertical resolution. */
    const val Y_RESOLUTION = "YResolution"

    /** ResolutionUnit - Resolution unit (1=none, 2=inch, 3=cm). */
    const val RESOLUTION_UNIT = "ResolutionUnit"

    /** ExposureTime - Exposure time in seconds. */
    const val EXPOSURE_TIME = "ExposureTime"

    /** FNumber - F-number (aperture). */
    const val F_NUMBER = "FNumber"

    /** ISOSpeedRatings - ISO speed rating. */
    const val ISO_SPEED_RATINGS = "ISOSpeedRatings"

    /** FocalLength - Focal length of lens in mm. */
    const val FOCAL_LENGTH = "FocalLength"

    /** FocalLengthIn35mmFilm - 35mm equivalent focal length. */
    const val FOCAL_LENGTH_35MM = "FocalLengthIn35mmFilm"

    /** ExposureProgram - Program used for exposure. */
    const val EXPOSURE_PROGRAM = "ExposureProgram"

    /** MeteringMode - Metering mode. */
    const val METING_MODE = "MeteringMode"

    /** Flash - Flash status. */
    const val FLASH = "Flash"

    /** WhiteBalance - White balance mode. */
    const val WHITE_BALANCE = "WhiteBalance"

    /** ColorSpace - Color space. */
    const val COLOR_SPACE = "ColorSpace"

    /** GPSLatitudeRef - GPS latitude ref (N/S). */
    const val GPS_LAT_REF = "GPSLatitudeRef"

    /** GPSLatitude - GPS latitude. */
    const val GPS_LATITUDE = "GPSLatitude"

    /** GPSLongitudeRef - GPS longitude ref (E/W). */
    const val GPS_LON_REF = "GPSLongitudeRef"

    /** GPSLongitude - GPS longitude. */
    const val GPS_LONGITUDE = "GPSLongitude"

    /** GPSAltitudeRef - GPS altitude ref (above/below sea). */
    const val GPS_ALT_REF = "GPSAltitudeRef"

    /** GPSAltitude - GPS altitude. */
    const val GPS_ALTITUDE = "GPSAltitude"

    /** Rating - Rating (from 1 to 5, or 0 if not set). */
    const val RATING = "Rating"

    /** Label - User-defined label/tag. */
    const val LABEL = "XPKeywords"

    /** ImageWidth - Pixel image width. */
    const val IMAGE_WIDTH = "PixelXDimension"

    /** ImageHeight - Pixel image height. */
    const val IMAGE_HEIGHT = "PixelYDimension"

    /** FlashpixVersion - Flashpix version. */
    const val FLASHPIX_VERSION = "FlashpixVersion"

    /** ExifVersion - EXIF version. */
    const val EXIF_VERSION = "ExifVersion"
  }
}
