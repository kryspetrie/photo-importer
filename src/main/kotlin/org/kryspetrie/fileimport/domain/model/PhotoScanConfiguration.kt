package org.kryspetrie.fileimport.domain.model

import kotlinx.serialization.Serializable

/**
 * Configuration for photo scan operations — the single source of truth for both the wizard UI and
 * the domain export layer.
 *
 * Specifies how detected photos within a scanned image should be processed:
 * - Geometric correction settings (perspective, rotation, aspect ratio, strategy)
 * - EXIF metadata overrides (date, camera, location, subjects)
 * - Per-field override tri-states (keep source / override / null out)
 * - Face region data
 *
 * ## String Convention
 *
 * All metadata string fields use **empty string** (`""`) to mean "no override" (preserve the source
 * EXIF value). The export service treats blank strings as "not set" and skips them. The tri-state
 * [OverrideState] enum provides explicit KEEP_SOURCE / OVERRIDE / NULL_OUT control.
 *
 * ## EXIF Metadata Fields
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
 */
@Serializable
data class PhotoScanConfiguration(
    // -- Geometric correction (wizard UI fields) --

    /** Whether to apply perspective correction (warp-stretch) during export. */
    val perspectiveCorrectionEnabled: Boolean = false,

    /** Rotation in degrees clockwise: 0, 90, 180, or 270. */
    val rotationDegrees: Int = 0,

    /**
     * Output aspect ratio. 0 = preserve original, otherwise width/height ratio (e.g. 1.5 = 3:2).
     */
    val aspectRatio: Double = 0.0,

    /**
     * Correction strategy for the photo geometry. null = use the global default strategy (set via
     * [PhotoScanWizardState.defaultCorrectionStrategy]). Non-null = force a specific strategy for
     * this photo, overriding the global default.
     */
    val correctionStrategy: CorrectionStrategy? = null,

    /**
     * Detection mode: how this photo was detected (CV, YOLO, etc.). null = auto/inferred, non-null
     * = force specific mode.
     */
    val detectionMode: DetectionMode? = null,

    // -- EXIF metadata overrides (empty string = preserve original, non-blank = override) --

    /** Image description (EXIF tag 0x010E ImageDescription). Empty = preserve original. */
    val description: String = "",

    /** Keywords/tags (written as XPKeywords tag). Comma-separated. Empty = preserve original. */
    val keywords: String = "",

    /**
     * Date/time original (EXIF tag 0x9003 DateTimeOriginal). Format: "YYYY-MM-DD" or "YYYY-MM-DD
     * HH:MM:SS". Empty = preserve original.
     */
    val originalDate: String = "",

    /** Year override — sets the year portion of DateTimeOriginal. Empty = no override. */
    val year: String = "",

    /** Camera make (EXIF tag 0x010F Make). Empty = preserve original. */
    val cameraMake: String = "",

    /** Camera model (EXIF tag 0x0110 Model). Empty = preserve original. */
    val cameraModel: String = "",

    /** Lens model (EXIF tag 0xA434 LensModel). Empty = preserve original. */
    val lensModel: String = "",

    /** Focal length in mm (EXIF tag 0x920A FocalLength). Empty = preserve original. */
    val focalLength: String = "",

    /** Aperture f-number (EXIF tag 0x829D FNumber). Empty = preserve original. */
    val aperture: String = "",

    /**
     * Shutter speed as "1/N" or decimal seconds (EXIF tag 0x829A ExposureTime). Empty = preserve
     * original.
     */
    val shutterSpeed: String = "",

    /** ISO speed rating (EXIF tag 0x8827 ISOSpeedRatings). Empty = preserve original. */
    val iso: String = "",

    /**
     * Whether to copy EXIF data from the original source file as a baseline before applying
     * overrides. When true, reads existing EXIF from the source scan and overlays user overrides on
     * top. When false, starts with an empty EXIF and only writes user-specified overrides. Defaults
     * to true for backward compatibility and to preserve scanner metadata.
     */
    val copyOriginalExif: Boolean = true,

    // -- Location metadata (IPTC Core + EXIF GPS) --

    /**
     * Sub-location / location name (IPTC 2:91). E.g. "Grandma's house" or "Disney World".
     * A colloquial or recognizable name for the place. Empty = preserve original.
     */
    val locationName: String = "",

    /**
     * Full street address from geocoding (e.g. "Worcester, Massachusetts, United States").
     * Written to IPTC SubLocation when set, providing the detailed address. Falls back to
     * [locationName] in IPTC export if this is blank. Empty = preserve original.
     */
    val address: String = "",

    /** City (IPTC 2:90 / photoshop:City). E.g. "Worcester". Empty = preserve original. */
    val city: String = "",

    /** State/province (IPTC 2:92 / photoshop:State). E.g. "MA". Empty = preserve original. */
    val state: String = "",

    /**
     * Country name (IPTC 2:101 / photoshop:Country). E.g. "United States". Empty = preserve
     * original.
     */
    val country: String = "",

    /** GPS latitude in decimal degrees (EXIF GPSLatitude + GPSLatitudeRef). E.g. "42.2626". */
    val gpsLatitude: String = "",

    /** GPS longitude in decimal degrees (EXIF GPSLongitude + GPSLongitudeRef). E.g. "-71.8023". */
    val gpsLongitude: String = "",

    // -- Subject/face metadata (MWG-RS Regions + IPTC:Keywords) --

    /** Comma-separated subject/person names. Written as IPTC:Keywords AND MWG-RS face regions. */
    val subjects: String = "",

    /** Structured face regions with coordinates (MWG-RS format). Empty = no face region data. */
    val faceRegions: List<FaceRegion> = emptyList(),

    // -- Back-of-photo imaging --

    /**
     * Mode for handling a back-of-photo image associated with this photo.
     * - null: No back image assigned "combine": Vertically stitch the back crop below the front
     *   photo crop "append_back": Export the back crop as a separate file with "_back" suffix
     */
    val backImageMode: String? = null,

    /**
     * Source file path for the back-of-photo image. The user selects this from a file picker or
     * from the batch source files. If null, no back image is assigned.
     */
    val backImageSourcePath: String? = null,

    /**
     * Bounding box coordinates for cropping the back image, in the back image's coordinate space.
     * Stored as [topLeft, topRight, bottomRight, bottomLeft] in normalized coordinates (0.0-1.0).
     * If null, the entire back image is used as the crop.
     */
    val backCropNormalized: List<Float>? = null,

    /**
     * Rotation to apply to the back crop before stitching/appending, in degrees (0, 90, 180, 270).
     */
    val backCropRotation: Int = 0,

    // -- EXIF override tri-states --
    // null = legacy behavior (non-blank field value → override; blank → keep source)
    // KEEP_SOURCE = explicitly preserve original
    // OVERRIDE = replace with user-specified value
    // NULL_OUT = explicitly remove the field from output
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
    /** Cycles rotation 90° clockwise: 0→90→180→270→0, transforming face regions accordingly. */
    fun cycleRotationCW(): PhotoScanConfiguration {
        val newDegrees = (rotationDegrees + 90) % 360
        return copy(
            rotationDegrees = newDegrees,
            faceRegions = faceRegions.map { it.rotate90CW() },
        )
    }

    /** Cycles rotation 90° counter-clockwise: 0→270→180→90→0, transforming face regions accordingly. */
    fun cycleRotationCCW(): PhotoScanConfiguration {
        val newDegrees = (rotationDegrees - 90 + 360) % 360
        return copy(
            rotationDegrees = newDegrees,
            faceRegions = faceRegions.map { it.rotate90CCW() },
        )
    }

    /** Rotates 180°, transforming face regions accordingly. */
    fun rotate180(): PhotoScanConfiguration = copy(
        rotationDegrees = (rotationDegrees + 180) % 360,
        faceRegions = faceRegions.map { it.rotate180() },
    )

    /** Returns true if any metadata fields are non-empty. */
    fun hasMetadata(): Boolean =
        description.isNotBlank() ||
            keywords.isNotBlank() ||
            originalDate.isNotBlank() ||
            year.isNotBlank() ||
            cameraMake.isNotBlank() ||
            cameraModel.isNotBlank() ||
            lensModel.isNotBlank() ||
            focalLength.isNotBlank() ||
            aperture.isNotBlank() ||
            shutterSpeed.isNotBlank() ||
            iso.isNotBlank() ||
            locationName.isNotBlank() ||
            address.isNotBlank() ||
            city.isNotBlank() ||
            state.isNotBlank() ||
            country.isNotBlank() ||
            gpsLatitude.isNotBlank() ||
            gpsLongitude.isNotBlank() ||
            subjects.isNotBlank() ||
            faceRegions.isNotEmpty() ||
            backImageMode != null

    /** Parses the comma-separated keywords string into individual keyword strings. */
    fun keywordList(): List<String> =
        keywords.split(",").map { it.trim() }.filter { it.isNotBlank() }

    /** Sets keywords from a list of individual keyword strings. */
    fun withKeywordList(keywords: List<String>): PhotoScanConfiguration =
        copy(keywords = keywords.joinToString(", "))

    /** Returns subjects as a list of individual names. */
    fun subjectList(): List<String> =
        subjects.split(",").map { it.trim() }.filter { it.isNotBlank() }

    /** Returns a human-readable location string, e.g. "Grandma's house, Worcester, MA". */
    fun locationDisplay(): String =
        listOf(locationName, address, city, state, country).filter { it.isNotBlank() }.joinToString(", ")

    /** Returns true if there is GPS coordinate data. */
    fun hasGpsCoordinates(): Boolean = gpsLatitude.isNotBlank() && gpsLongitude.isNotBlank()

    /**
     * Returns true if any EXIF metadata override fields are non-blank, or if we need to write EXIF
     * even without overrides (e.g. copyOriginalExif=false forces a fresh write).
     */
    fun hasExifOverrides(): Boolean =
        !copyOriginalExif ||
            description.isNotBlank() ||
            keywords.isNotBlank() ||
            originalDate.isNotBlank() ||
            year.isNotBlank() ||
            cameraMake.isNotBlank() ||
            cameraModel.isNotBlank() ||
            lensModel.isNotBlank() ||
            focalLength.isNotBlank() ||
            aperture.isNotBlank() ||
            shutterSpeed.isNotBlank() ||
            iso.isNotBlank() ||
            locationName.isNotBlank() ||
            address.isNotBlank() ||
            city.isNotBlank() ||
            state.isNotBlank() ||
            country.isNotBlank() ||
            gpsLatitude.isNotBlank() ||
            gpsLongitude.isNotBlank() ||
            subjects.isNotBlank() ||
            faceRegions.isNotEmpty()

    /** Returns true if this photo has a back-of-photo image assigned. */
    fun hasBackImage(): Boolean = backImageMode != null && backImageSourcePath != null
}

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
