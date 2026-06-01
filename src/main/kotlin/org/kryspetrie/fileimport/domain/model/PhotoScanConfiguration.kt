package org.kryspetrie.fileimport.domain.model

import kotlinx.serialization.Serializable

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
 * | Field              | EXIF Tag              | Type        |
 * |--------------------|-----------------------|-------------|
 * | description        | ImageDescription      | ASCII       |
 * | keywords           | XPKeywords            | BYTE (UTF-16LE) |
 * | originalDate       | DateTimeOriginal      | ASCII       |
 * | year               | DateTimeOriginal      | ASCII (year portion only) |
 * | cameraMake          | Make                  | ASCII       |
 * | cameraModel        | Model                 | ASCII       |
 * | lensModel           | LensModel             | ASCII       |
 * | focalLength        | FocalLength           | RATIONAL    |
 * | aperture           | FNumber               | RATIONAL    |
 * | shutterSpeed       | ExposureTime          | RATIONAL    |
 * | iso                | ISOSpeedRatings       | SHORT       |
 *
 * @see PhotoConfiguration for the wizard-layer equivalent
 */
@Serializable
data class PhotoScanConfiguration(
    /** Override the original date (EXIF DateTimeOriginal). Format: "YYYY-MM-DD" or "YYYY-MM-DD HH:MM:SS" */
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

    /** Date/time original (EXIF tag 0x9003 DateTimeOriginal). Format: "YYYY:MM:DD HH:MM:SS". Null means preserve original. */
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

    /** Shutter speed as "1/N" or decimal seconds (EXIF tag 0x829A ExposureTime). Null means preserve original. */
    val shutterSpeed: String? = null,

    /** ISO speed rating (EXIF tag 0x8827 ISOSpeedRatings). Null means preserve original. */
    val iso: String? = null,

    /**
     * Whether to copy EXIF data from the original source file as a baseline before applying overrides.
     * When true, reads existing EXIF from the source scan and overlays user overrides on top.
     * When false, starts with an empty EXIF and only writes user-specified overrides.
     * Defaults to true for backward compatibility and to preserve scanner metadata.
     */
    val copyOriginalExif: Boolean = true,
) {
    /**
     * Returns true if any EXIF metadata override fields are non-null/non-empty,
     * or if we need to write EXIF even without overrides (e.g. copyOriginalExif=false forces a fresh write).
     */
    fun hasExifOverrides(): Boolean =
        !copyOriginalExif || // When not copying original, we always need to write EXIF (even if empty baseline)
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
            !notes.isNullOrBlank()
}
