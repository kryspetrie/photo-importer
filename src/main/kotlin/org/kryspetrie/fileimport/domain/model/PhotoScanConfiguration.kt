package org.kryspetrie.fileimport.domain.model

import kotlinx.serialization.Serializable

/**
 * Configuration for photo scan operations.
 *
 * Specifies how detected photos within a scanned image should be processed:
 * - Metadata overrides for date, tags, notes
 * - Export settings
 * - Corner detection parameters
 *
 * @property originalDateOverride Optional override for the "original date" EXIF field
 * @property originalYearOverride Optional override for year extracted from date
 * @property originalMonthOverride Optional override for month extracted from date
 * @property tags Comma-separated tags to apply to the scanned photo
 * @property notes Additional notes/metadata for the scanned photo
 */
@Serializable
data class PhotoScanConfiguration(
    /** Override the original date (EXIF DateTimeOriginal) */
    val originalDateOverride: String? = null,

    /** Override the year portion of the date */
    val originalYearOverride: String? = null,

    /** Override the month portion of the date */
    val originalMonthOverride: String? = null,

    /** Comma-separated tags to apply to the scanned photo */
    val tags: String = "",

    /** Additional notes/metadata */
    val notes: String = "",
)
