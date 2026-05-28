package org.kryspetrie.fileimport.domain.model

/**
 * Source of date for date-based folder/filename patterns.
 *
 * Determines which timestamp is used when resolving date placeholders like `{yyyy}`, `{MM}`,
 * `{dd}`, `{HH}`, etc.
 * - **EXIF_DATE**: Use EXIF "DateTimeOriginal" tag (when photo was taken). Most accurate for
 *   photos. Falls back to file date if no EXIF.
 * - **FILE_MODIFIED_DATE**: Use file's last modified timestamp. Works for all file types but may
 *   not reflect capture date.
 * - **FILE_CREATED_DATE**: Use file's creation timestamp. May not reflect capture date and is lost
 *   when copying to a new filesystem.
 *
 * @see ImportConfiguration.dateSource
 */
enum class DateSource {
    EXIF_DATE,
    FILE_MODIFIED_DATE,
    FILE_CREATED_DATE,
}
