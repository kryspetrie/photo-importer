package org.kryspetrie.fileimport.infrastructure.wizard

/**
 * Read-only summary of EXIF metadata read from the source file.
 *
 * Displayed in the MetadataScreen as "Source: ..." hints next to each field, so the user can see
 * what the original file contains before deciding whether to Keep Source / Override / Null Out.
 *
 * All fields are nullable: null means the source file has no value for that tag.
 */
data class SourceExifSummary(
    val cameraMake: String? = null,
    val cameraModel: String? = null,
    val lensModel: String? = null,
    val focalLength: String? = null,
    val aperture: String? = null,
    val shutterSpeed: String? = null,
    val iso: String? = null,
    val description: String? = null,
    val dateOriginal: String? = null,
    val gpsLatitude: String? = null,
    val gpsLongitude: String? = null,
)
