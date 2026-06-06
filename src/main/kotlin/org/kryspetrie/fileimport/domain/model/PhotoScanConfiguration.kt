package org.kryspetrie.fileimport.domain.model

import kotlinx.serialization.Serializable

/**
 * Tri-state for per-field EXIF override behavior.
 * - [KEEP_SOURCE]: Preserve the original EXIF value (default)
 * - [OVERRIDE]: Replace with a user-specified value
 * - [NULL_OUT]: Explicitly remove the field from the output
 */
@Serializable
enum class OverrideState {
    KEEP_SOURCE,
    OVERRIDE,
    NULL_OUT,
}

/**
 * Configuration for photo scan operations.
 *
 * Specifies how detected photos within a scanned image should be processed:
 * - Metadata overrides for date, tags, notes, and camera EXIF fields
 * - Export settings
 * - Corner detection parameters
 *
 * ## EXIF Metadata Fields
 *
 * All metadata fields default to null/empty, meaning "no override" — the original source image's
 * EXIF data is preserved. When a field is set to a non-null/non-empty value, that value overrides
 * the corresponding EXIF tag in the exported JPEG.
 *
 * | Field        | EXIF Tag         | Type                      |
 * |--------------|------------------|---------------------------|
 * | description  | ImageDescription | ASCII                     |
 * | keywords     | XPKeywords       | BYTE (UTF-16LE)           |
 * | originalDate | DateTimeOriginal | ASCII                     |
 * | year         | DateTimeOriginal | ASCII (year portion only) |
 * | cameraMake   | Make             | ASCII                     |
 * | cameraModel  | Model            | ASCII                     |
 * | lensModel    | LensModel        | ASCII                     |
 * | focalLength  | FocalLength      | RATIONAL                  |
 * | aperture     | FNumber          | RATIONAL                  |
 * | shutterSpeed | ExposureTime     | RATIONAL                  |
 * | iso          | ISOSpeedRatings  | SHORT                     |
 *
 * @see PhotoConfiguration for the wizard-layer equivalent
 */
@Serializable
data class PhotoScanConfiguration(
    /**
     * Override the original date (EXIF DateTimeOriginal). Format: "YYYY-MM-DD" or "YYYY-MM-DD
     * HH:MM:SS"
     */
    val originalDateOverride: String? = null,

    /** Override the year portion of the date */
    val originalYearOverride: String? = null,

    /** Override the month portion of the date */
    val originalMonthOverride: String? = null,

    /** Comma-separated tags to apply to the scanned photo (written as XPKeywords) */
    val tags: String = "",

    /** Additional notes/metadata (written as ImageDescription if no description override) */
    val notes: String = "",

    // -- EXIF metadata overrides (null = preserve original, non-null = override) --

    /** Image description (EXIF tag 0x010E ImageDescription). Null means preserve original. */
    val description: String? = null,

    /** Keywords/tags (written as XPKeywords tag). Comma-separated. Null means preserve original. */
    val keywords: String? = null,

    /**
     * Date/time original (EXIF tag 0x9003 DateTimeOriginal). Format: "YYYY:MM:DD HH:MM:SS". Null
     * means preserve original.
     */
    val originalDate: String? = null,

    /** Year override — sets the year portion of DateTimeOriginal. Null means no override. */
    val year: String? = null,

    /** Camera make (EXIF tag 0x010F Make). Null means preserve original. */
    val cameraMake: String? = null,

    /** Camera model (EXIF tag 0x0110 Model). Null means preserve original. */
    val cameraModel: String? = null,

    /** Lens model (EXIF tag 0xA434 LensModel). Null means preserve original. */
    val lensModel: String? = null,

    /** Focal length in mm (EXIF tag 0x920A FocalLength). Null means preserve original. */
    val focalLength: String? = null,

    /** Aperture f-number (EXIF tag 0x829D FNumber). Null means preserve original. */
    val aperture: String? = null,

    /**
     * Shutter speed as "1/N" or decimal seconds (EXIF tag 0x829A ExposureTime). Null means preserve
     * original.
     */
    val shutterSpeed: String? = null,

    /** ISO speed rating (EXIF tag 0x8827 ISOSpeedRatings). Null means preserve original. */
    val iso: String? = null,

    /**
     * Whether to copy EXIF data from the original source file as a baseline before applying
     * overrides. When true, reads existing EXIF from the source scan and overlays user overrides on
     * top. When false, starts with an empty EXIF and only writes user-specified overrides. Defaults
     * to true for backward compatibility and to preserve scanner metadata.
     */
    val copyOriginalExif: Boolean = true,

    // -- Location metadata (IPTC Core + EXIF GPS) --

    /**
     * Sub-location / location name (IPTC 2:91). E.g. "Grandma's house". Null means preserve
     * original.
     */
    val locationName: String? = null,

    /** City (IPTC 2:90 / photoshop:City). E.g. "Worcester". Null means preserve original. */
    val city: String? = null,

    /** State/province (IPTC 2:92 / photoshop:State). E.g. "MA". Null means preserve original. */
    val state: String? = null,

    /**
     * Country name (IPTC 2:101 / photoshop:Country). E.g. "United States". Null means preserve
     * original.
     */
    val country: String? = null,

    /**
     * GPS latitude in decimal degrees (EXIF GPSLatitude + GPSLatitudeRef). E.g. "42.2626". Null
     * means preserve original.
     */
    val gpsLatitude: String? = null,

    /**
     * GPS longitude in decimal degrees (EXIF GPSLongitude + GPSLongitudeRef). E.g. "-71.8023". Null
     * means preserve original.
     */
    val gpsLongitude: String? = null,

    // -- Subject/face metadata (MWG-RS Regions + IPTC:Keywords) --

    /**
     * Comma-separated subject/person names. Written as IPTC:Keywords AND as MWG-RS face regions (if
     * coordinates provided). Null means preserve original.
     */
    val subjects: String? = null,

    /**
     * Structured face regions with coordinates (MWG-RS format). Empty means no face region data.
     */
    val faceRegions: List<FaceRegionConfig> = emptyList(),

    // EXIF override tri-states — null = use legacy nullable string fields (backward compat)
    // When non-null, the OverrideState determines: KEEP_SOURCE, OVERRIDE, or NULL_OUT
    val overrideDescription: OverrideState? = null,
    val overrideKeywords: OverrideState? = null,
    val overrideOriginalDate: OverrideState? = null,
    val overrideYear: OverrideState? = null,
    val overrideCameraMake: OverrideState? = null,
    val overrideCameraModel: OverrideState? = null,
    val overrideLensModel: OverrideState? = null,
    val overrideFocalLength: OverrideState? = null,
    val overrideAperture: OverrideState? = null,
    val overrideShutterSpeed: OverrideState? = null,
    val overrideIso: OverrideState? = null,
    val overrideGps: OverrideState? = null, // covers lat+lon together
) {
    /**
     * Returns true if any EXIF metadata override fields are non-null/non-empty, or if we need to
     * write EXIF even without overrides (e.g. copyOriginalExif=false forces a fresh write).
     */
    fun hasExifOverrides(): Boolean =
        !copyOriginalExif || // When not copying original, we always need to write EXIF (even if
            // empty baseline)
            !description.isNullOrBlank() ||
            !keywords.isNullOrBlank() ||
            !originalDate.isNullOrBlank() ||
            !year.isNullOrBlank() ||
            !cameraMake.isNullOrBlank() ||
            !cameraModel.isNullOrBlank() ||
            !lensModel.isNullOrBlank() ||
            !focalLength.isNullOrBlank() ||
            !aperture.isNullOrBlank() ||
            !shutterSpeed.isNullOrBlank() ||
            !iso.isNullOrBlank() ||
            !originalDateOverride.isNullOrBlank() ||
            !tags.isNullOrBlank() ||
            !notes.isNullOrBlank() ||
            !locationName.isNullOrBlank() ||
            !city.isNullOrBlank() ||
            !state.isNullOrBlank() ||
            !country.isNullOrBlank() ||
            !gpsLatitude.isNullOrBlank() ||
            !gpsLongitude.isNullOrBlank() ||
            !subjects.isNullOrBlank() ||
            faceRegions.isNotEmpty()
}

/**
 * Face region configuration for MWG-RS structured face data.
 *
 * Coordinates are normalized 0.0-1.0 relative to the output image dimensions. MWG-RS uses
 * center-based coordinates (x/y = center point, NOT top-left).
 *
 * @property name Person/subject name for this face region
 * @property type Region type: "Face", "Pet", "Body", or "Object" (MWG-RS types)
 * @property x Center X as fraction of image width (0.0-1.0)
 * @property y Center Y as fraction of image height (0.0-1.0)
 * @property w Width as fraction of image width (0.0-1.0)
 * @property h Height as fraction of image height (0.0-1.0)
 */
@Serializable
data class FaceRegionConfig(
    val name: String = "",
    val type: String = "Face",
    val x: Double = 0.0,
    val y: Double = 0.0,
    val w: Double = 0.0,
    val h: Double = 0.0,
)
