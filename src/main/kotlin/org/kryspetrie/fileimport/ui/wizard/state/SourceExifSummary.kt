package org.kryspetrie.fileimport.ui.wizard.state

import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.i18n.Strings

/**
 * Read-only summary of all metadata read from the source file.
 *
 * Displayed in the MetadataScreen as "Source: ..." hints next to each field, so the user can see
 * what the original file contains before deciding whether to override.
 *
 * All fields are nullable: null means the source file has no value for that tag.
 *
 * Expanded to cover every editable metadata field — not just EXIF camera fields, but also IPTC
 * location/keywords and file-level metadata like dimensions, orientation, and copyright.
 */
data class SourceExifSummary(
    // Camera fields
    val cameraMake: String? = null,
    val cameraModel: String? = null,
    val lensModel: String? = null,
    val focalLength: String? = null,
    val aperture: String? = null,
    val shutterSpeed: String? = null,
    val iso: String? = null,

    // Description / date fields
    val description: String? = null,
    val dateOriginal: String? = null,

    // Keywords / subjects
    val keywords: String? = null,

    // Location fields (IPTC + GPS)
    val locationName: String? = null,
    val address: String? = null,
    val city: String? = null,
    val state: String? = null,
    val country: String? = null,
    val gpsLatitude: String? = null,
    val gpsLongitude: String? = null,

    // File-level metadata
    val imageWidth: Int? = null,
    val imageHeight: Int? = null,
    val orientation: Int? = null,
    val software: String? = null,
    val copyright: String? = null,
    val artist: String? = null,
    val colorSpace: String? = null,
    val flash: String? = null,
    val whiteBalance: String? = null,
    val meteringMode: String? = null,
    val exposureProgram: String? = null,
    val exposureCompensation: String? = null,
    val focalLength35mm: Int? = null,
) {
    /** Returns localized label/value pairs for [SourceMetadataSection]. */
    fun summaryLines(s: Strings): List<Pair<String, String>> = buildList {
        cameraMake?.let { add(s.t(StringKey.FIELD_CAMERA_MAKE) to it) }
        cameraModel?.let { add(s.t(StringKey.FIELD_CAMERA_MODEL) to it) }
        lensModel?.let { add(s.t(StringKey.FIELD_LENS_MODEL) to it) }
        focalLength?.let { add(s.t(StringKey.FIELD_FOCAL_LENGTH) to it) }
        aperture?.let { add(s.t(StringKey.FIELD_APERTURE) to it) }
        shutterSpeed?.let { add(s.t(StringKey.FIELD_SHUTTER_SPEED) to it) }
        iso?.let { add(s.t(StringKey.FIELD_ISO) to it) }
        description?.let { add(s.t(StringKey.FIELD_DESCRIPTION) to it) }
        dateOriginal?.let { add(s.t(StringKey.FIELD_ORIGINAL_DATE) to it) }
        keywords?.let { add(s.t(StringKey.FIELD_KEYWORDS) to it) }
        locationName?.let { add(s.t(StringKey.FIELD_LOCATION) to it) }
        address?.let { add(s.t(StringKey.FIELD_ADDRESS) to it) }
        city?.let { add(s.t(StringKey.FIELD_CITY) to it) }
        state?.let { add(s.t(StringKey.FIELD_STATE) to it) }
        country?.let { add(s.t(StringKey.FIELD_COUNTRY) to it) }
        gpsLatitude?.let { lat ->
            gpsLongitude?.let { lon -> add(s.t(StringKey.FIELD_GPS) to "$lat, $lon") }
        }
        imageWidth?.let { w ->
            imageHeight?.let { h -> add(s.t(StringKey.IMPORT_DIMENSIONS) to "${w}×$h") }
        }
        orientation?.let { add(s.t(StringKey.FIELD_ORIENTATION) to orientationLabel(it, s)) }
        software?.let { add(s.t(StringKey.FIELD_SOFTWARE) to it) }
        copyright?.let { add(s.t(StringKey.FIELD_COPYRIGHT) to it) }
        artist?.let { add(s.t(StringKey.FIELD_ARTIST) to it) }
        colorSpace?.let { add(s.t(StringKey.FIELD_COLOR_SPACE) to it) }
        flash?.let { add(s.t(StringKey.FIELD_FLASH) to it) }
        whiteBalance?.let { add(s.t(StringKey.FIELD_WHITE_BALANCE) to it) }
        meteringMode?.let { add(s.t(StringKey.FIELD_METERING) to it) }
        exposureProgram?.let { add(s.t(StringKey.FIELD_EXPOSURE_PROGRAM) to it) }
        exposureCompensation?.let { add(s.t(StringKey.FIELD_EXPOSURE_COMP) to it) }
        focalLength35mm?.let { add(s.t(StringKey.FIELD_FOCAL_LENGTH) to "${it}mm") }
    }

    companion object {
        /** Maps EXIF orientation values to human-readable labels. */
        fun orientationLabel(value: Int, s: Strings): String =
            when (value) {
                1 -> s.t(StringKey.ORIENT_NORMAL)
                2 -> s.t(StringKey.ORIENT_MIRRORED)
                3 -> s.t(StringKey.ORIENT_ROTATED_180)
                4 -> s.t(StringKey.ORIENT_MIRRORED_180)
                5 -> s.t(StringKey.ORIENT_MIRRORED_90_CCW)
                6 -> s.t(StringKey.ORIENT_ROTATED_90_CW)
                7 -> s.t(StringKey.ORIENT_MIRRORED_90_CW)
                8 -> s.t(StringKey.ORIENT_ROTATED_90_CCW)
                else -> s.t(StringKey.ORIENT_UNKNOWN, "value" to value.toString())
            }
    }
}
