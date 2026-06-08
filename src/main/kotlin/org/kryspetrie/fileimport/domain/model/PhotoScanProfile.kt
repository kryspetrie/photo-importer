package org.kryspetrie.fileimport.domain.model

import kotlinx.serialization.Serializable

/**
 * Output format for exported photos.
 *
 * Defines how photos are encoded when exported from the Photo Scan workflow.
 *
 * @property extension File extension used for output (without dot)
 * @property quality Quality percentage for lossy formats (JPEG)
 * @property description Human-readable description
 */
@Serializable
enum class PhotoOutputFormat(val extension: String, val quality: Int, val description: String) {
    JPEG_QUALITY_90("jpg", 90, "JPEG High Quality (90%)"),
    JPEG_QUALITY_85("jpg", 85, "JPEG Medium Quality (85%)"),
    JPEG_QUALITY_75("jpg", 75, "JPEG Low Quality (75%)"),
    JPEG_QUALITY_95("jpg", 95, "JPEG Best Quality (95%)"),
    PNG("png", 100, "PNG (Lossless)"),
    TIFF("tiff", 100, "TIFF (Uncompressed)"),
}

/** Backward-compatible alias for [AspectRatio]. See [AspectRatio] for the unified enum. */
typealias AspectRatioPreset = AspectRatio

/**
 * Default correction settings for photo export.
 *
 * @property enablePerspectiveCorrection Automatically correct trapezoidal distortion
 * @property enableRotationCorrection Automatically fix scan rotation
 * @property correctionStrategy Override for the correction strategy. null = auto-detect from corner
 *   geometry (see [determineCorrectionStrategy]), non-null = force a specific strategy regardless
 *   of geometry. This maps to the former [PerspectiveMode]:
 *     - AUTO → null
 *     - MANUAL → [CorrectionStrategy.PERSPECTIVE]
 *     - DISABLED → [CorrectionStrategy.CROP]
 *
 * @see CorrectionStrategy
 */
@Serializable
data class CorrectionSettings(
    val enablePerspectiveCorrection: Boolean = true,
    val enableRotationCorrection: Boolean = false,
    val correctionStrategy: CorrectionStrategy? = null,
)

/**
 * Represents a saved Photo Scan configuration profile.
 *
 * Photo Scan profiles allow users to save and reuse complete configurations for the photo scan
 * workflow, including destination folders, export settings, and correction preferences.
 *
 * ## Use Cases
 * 1. **Workflow Profiles**: Different settings for different types of scans
 *     - "Document Scan": Perspective correction, PDF output
 *     - "Photo Album": High quality JPEG, 4x6 aspect ratio
 *     - "Quick Scan": Fast processing, medium quality
 * 2. **Project Profiles**: Separate configurations per project or client
 *     - "Wedding Album": High quality, 4x6 prints
 *     - "Family Photos": Medium quality, original aspect ratio
 *
 * ## Auto-Selection
 *
 * Profiles can be associated with a camera or source folder. When files from that source are
 * detected, the profile can be automatically suggested.
 *
 * ## Example
 *
 * ```kotlin
 * val albumProfile = PhotoScanProfile(
 *     name = "Photo Album",
 *     description = "Standard photo album digitization",
 *     defaultDestination = "~/Pictures/Scanned Photos",
 *     outputFormat = PhotoOutputFormat.JPEG_QUALITY_90,
 *     aspectRatioPreset = AspectRatioPreset.LANDSCAPE_3_2,
 *     correctionSettings = CorrectionSettings(
 *         enablePerspectiveCorrection = true,
 *         enableRotationCorrection = true
 *     )
 * )
 * ```
 *
 * @property id Unique identifier for this profile. Auto-generated UUID ensures uniqueness.
 * @property name Human-readable name shown in the profile selector. Should be descriptive.
 * @property description Optional detailed description of the profile's purpose.
 * @property defaultDestination Default destination folder for exports. Relative paths are relative
 *   to user home directory.
 * @property outputFormat Output file format and quality setting.
 * @property aspectRatioPreset Aspect ratio for export (or ORIGINAL for no cropping).
 * @property correctionSettings Default correction settings (perspective, rotation).
 * @property namingPattern Filename pattern for exports. Uses placeholders like {original}, {date},
 *   {counter}. Default preserves original filename with date prefix.
 * @property autoDetectEnabled Whether auto-detection of photos is enabled by default.
 * @property createdAt Unix timestamp (milliseconds) when profile was created.
 * @property updatedAt Unix timestamp (milliseconds) when profile was last modified.
 * @property lastUsedAt Unix timestamp (milliseconds) when profile was last used for a scan.
 * @property useCount Number of times this profile has been used.
 * @see PhotoOutputFormat Available output formats
 * @see AspectRatioPreset Available aspect ratio presets
 * @see CorrectionSettings Default correction settings
 */
@Serializable
data class PhotoScanProfile(
    /**
     * Unique profile identifier.
     *
     * Auto-generated UUID ensures:
     * - No collisions between profiles
     * - Safe to rename profiles
     * - Can be referenced by ID in settings
     */
    val id: String = DomainDefaults.generateId(),

    /**
     * Profile display name.
     *
     * Shown in UI dropdowns and selectors. Examples:
     * - "Photo Album Scan"
     * - "Document Archive"
     * - "High Quality Export"
     */
    val name: String,

    /**
     * Optional profile description.
     *
     * Can include:
     * - Purpose of this profile
     * - Recommended use cases
     * - Special handling notes
     */
    val description: String = "",

    /**
     * Default destination folder for exports.
     *
     * This is the default output location when using this profile. Users can still change it during
     * export. The path can be:
     * - Absolute: `/Users/name/Pictures/Scans`
     * - Relative to home: `~/Pictures/Scans`
     * - Just `Pictures` or `Desktop` (resolved relative to home)
     *
     * Default: `Pictures/PhotoScan` (resolved to ~/Pictures/PhotoScan)
     */
    val defaultDestination: String = "Pictures/PhotoScan",

    /**
     * Output format for exported photos.
     *
     * Determines:
     * - File format (JPEG, PNG, TIFF)
     * - Quality for lossy formats
     * - File extension
     *
     * Default: JPEG_QUALITY_90 (good balance of quality and size)
     */
    val outputFormat: PhotoOutputFormat = PhotoOutputFormat.JPEG_QUALITY_90,

    /**
     * Aspect ratio preset for export cropping.
     *
     * When set to a specific preset, photos will be cropped to that aspect ratio. When set to
     * ORIGINAL, photos use their natural aspect ratio.
     *
     * Default: ORIGINAL (no cropping)
     */
    val aspectRatioPreset: AspectRatioPreset = AspectRatioPreset.ORIGINAL,

    /**
     * Default correction settings for this profile.
     *
     * These settings are applied by default when using this profile. Users can still modify
     * individual photos in the summary screen.
     *
     * Default: Perspective correction enabled, rotation disabled
     */
    val correctionSettings: CorrectionSettings = CorrectionSettings(),

    /**
     * Filename pattern for exports.
     *
     * Placeholders:
     * - `{original}`: Original filename (without extension)
     * - `{date}`: Current date (YYYY-MM-DD)
     * - `{datetime}`: Current date and time (YYYY-MM-DD_HH-mm-ss)
     * - `{counter}`: Sequential number (001, 002, etc.)
     * - `{counter4}`: Sequential number with padding (0001, 0002, etc.)
     *
     * Examples:
     * - `{original}` → `IMG_1234.jpg`
     * - `{date}_{original}` → `2024-01-15_IMG_1234.jpg`
     * - `Scan_{datetime}_{counter4}` → `Scan_2024-01-15_14-30-00_0001.jpg`
     *
     * Default: `{original}` (preserves original filename)
     */
    val namingPattern: String = "{original}",

    /**
     * Whether auto-detection is enabled by default.
     *
     * When true, the system automatically detects photo boundaries using computer vision. When
     * false, users must add bounding boxes manually.
     *
     * Default: true
     */
    val autoDetectEnabled: Boolean = true,

    /**
     * Profile creation timestamp.
     *
     * Unix timestamp in milliseconds since epoch. Used for:
     * - Sorting profiles by creation date
     * - Audit trail
     * - Sync conflict resolution
     */
    val createdAt: Long = DomainDefaults.currentTimeMillis(),

    /**
     * Profile last modification timestamp.
     *
     * Updated every time profile is saved. Used for:
     * - Showing "recently modified" indicator
     * - Sort by last used
     * - Sync and backup decisions
     */
    val updatedAt: Long = DomainDefaults.currentTimeMillis(),

    /**
     * Profile last usage timestamp.
     *
     * Updated when profile is used for a scan. Used for:
     * - Sorting by "recently used"
     * - Suggesting most-used profiles
     * - Analytics
     */
    val lastUsedAt: Long = 0L,

    /**
     * Number of times this profile has been used.
     *
     * Used for:
     * - Sorting by "most used"
     * - Determining which profiles to suggest
     * - User analytics
     */
    val useCount: Int = 0,

    // -- EXIF override defaults for scanned photos --

    /**
     * Whether to default the camera make override to NULL_OUT for new photos.
     *
     * Scanners typically set Make to "EPSON", "Canon" (scanner, not camera), etc. This is almost
     * always wrong metadata for a photographed/digitized print. Default: true — null out scanner
     * make by default.
     */
    val nullOutScannerMake: Boolean = true,

    /**
     * Whether to default the camera model override to NULL_OUT for new photos.
     *
     * Scanner model (e.g. "Perfection V600") is not a camera model. Default: true — null out
     * scanner model by default.
     */
    val nullOutScannerModel: Boolean = true,

    /**
     * Whether to default the date override to OVERRIDE (with user value) for new photos.
     *
     * Scanner dates are the scan date, not the photo date. Users typically want to override.
     * Default: false — keep scanner date unless user explicitly overrides.
     */
    val autoOverrideDate: Boolean = false,

    /**
     * Whether to default GPS override to NULL_OUT when no coordinates are provided.
     *
     * Scanners don't have GPS, so the source EXIF won't have GPS either, but some workflows may
     * inject GPS metadata that should be cleared. Default: true — null out GPS if no coordinates
     * provided.
     */
    val nullOutGpsIfMissing: Boolean = true,
) {
    /**
     * Resolves the default destination to an absolute path.
     *
     * Handles:
     * - Absolute paths (returned as-is)
     * - Home-relative paths (~/Pictures → /Users/name/Pictures)
     * - Relative paths (Pictures → /Users/name/Pictures/Pictures)
     *
     * @return Absolute path to the destination folder
     */
    fun resolveDestination(): String {
        val home = System.getProperty("user.home")
        return when {
            defaultDestination.startsWith("~/") -> defaultDestination.replace("~", home)
            defaultDestination.startsWith("/") -> defaultDestination
            else -> "$home/$defaultDestination"
        }
    }

    /**
     * Creates a copy of this profile with updated last-used timestamp and incremented use count.
     *
     * @return New profile with updated usage stats
     */
    fun markAsUsed(): PhotoScanProfile {
        return copy(
            lastUsedAt = DomainDefaults.currentTimeMillis(),
            useCount = useCount + 1,
            updatedAt = DomainDefaults.currentTimeMillis(),
        )
    }

    /**
     * Validates this profile for correctness.
     *
     * @return List of validation errors (empty if valid)
     */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()

        if (name.isBlank()) {
            errors.add("Profile name cannot be empty")
        }

        if (name.length > 100) {
            errors.add("Profile name cannot exceed 100 characters")
        }

        if (description.length > 500) {
            errors.add("Profile description cannot exceed 500 characters")
        }

        if (defaultDestination.isBlank()) {
            errors.add("Default destination cannot be empty")
        }

        if (namingPattern.isBlank()) {
            errors.add("Naming pattern cannot be empty")
        }

        if (
            !namingPattern.contains("{original}") &&
                !namingPattern.contains("{counter") &&
                !namingPattern.contains("{date")
        ) {
            errors.add(
                "Naming pattern should contain at least one of: {original}, {counter}, {date}"
            )
        }

        return errors
    }

    companion object {
        /**
         * Creates a default profile with sensible defaults.
         *
         * @return A default Photo Scan profile
         */
        fun createDefault(): PhotoScanProfile {
            return PhotoScanProfile(
                name = "Default",
                description = "Standard photo scan settings with high-quality JPEG output",
                defaultDestination = "Pictures/PhotoScan",
                outputFormat = PhotoOutputFormat.JPEG_QUALITY_90,
                aspectRatioPreset = AspectRatioPreset.ORIGINAL,
                correctionSettings =
                    CorrectionSettings(
                        enablePerspectiveCorrection = true,
                        enableRotationCorrection = false,
                    ),
                namingPattern = "{original}",
                autoDetectEnabled = true,
            )
        }

        /**
         * Creates a profile optimized for documents.
         *
         * @return A document-optimized Photo Scan profile
         */
        fun createDocumentProfile(): PhotoScanProfile {
            return PhotoScanProfile(
                name = "Document Scan",
                description = "Optimized for scanning documents with perspective correction",
                defaultDestination = "Documents/Scans",
                outputFormat = PhotoOutputFormat.JPEG_QUALITY_85,
                aspectRatioPreset = AspectRatioPreset.PORTRAIT_4_3,
                correctionSettings =
                    CorrectionSettings(
                        enablePerspectiveCorrection = true,
                        enableRotationCorrection = true,
                        correctionStrategy = CorrectionStrategy.PERSPECTIVE,
                    ),
                namingPattern = "{date}_{original}",
                autoDetectEnabled = true,
            )
        }

        /**
         * Creates a profile optimized for photo albums.
         *
         * @return A photo album-optimized Photo Scan profile
         */
        fun createPhotoAlbumProfile(): PhotoScanProfile {
            return PhotoScanProfile(
                name = "Photo Album",
                description = "High quality export for photo albums with 4x6 aspect ratio",
                defaultDestination = "Pictures/PhotoScan/Albums",
                outputFormat = PhotoOutputFormat.JPEG_QUALITY_95,
                aspectRatioPreset = AspectRatioPreset.LANDSCAPE_3_2,
                correctionSettings =
                    CorrectionSettings(
                        enablePerspectiveCorrection = true,
                        enableRotationCorrection = true,
                    ),
                namingPattern = "{original}_{counter4}",
                autoDetectEnabled = true,
            )
        }
    }
}
