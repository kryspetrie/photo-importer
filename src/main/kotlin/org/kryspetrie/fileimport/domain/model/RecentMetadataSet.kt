package org.kryspetrie.fileimport.domain.model

import kotlinx.serialization.Serializable

/**
 * A snapshot of all metadata fields from a previously-entered photo scan configuration.
 *
 * Stored in [MetadataHistory.recentSets] to let users quickly apply a complete set of metadata
 * values to the current photo — useful when importing a series of photos taken at the same
 * time and location.
 *
 * Each set is timestamped so the most-recently-used entries appear first. Fields that were not
 * set (blank in the original config) remain blank here, so applying a set only overwrites
 * fields the user originally filled in (matching the multi-edit "apply non-blank" convention).
 *
 * Note: Face selection (face regions) is excluded from recent sets, since those are
 * photo-specific coordinates rather than reusable metadata text values.
 */
@Serializable
data class RecentMetadataSet(
    val description: String = "",
    val keywords: String = "",
    val originalDate: String = "",
    val year: String = "",
    val cameraMake: String = "",
    val cameraModel: String = "",
    val lensModel: String = "",
    val focalLength: String = "",
    val aperture: String = "",
    val shutterSpeed: String = "",
    val iso: String = "",
    val locationName: String = "",
    val address: String = "",
    val city: String = "",
    val state: String = "",
    val country: String = "",
    val gpsLatitude: String = "",
    val gpsLongitude: String = "",
    val subjects: String = "",
    val timestamp: Long = System.currentTimeMillis(),
) {
    /**
     * A short human-readable label for this set, derived from the most identifying fields.
     * Prefers location-based labels (e.g. "Grandma's house, Worcester, MA"), falls back to
     * description, date, or "unlabeled set".
     */
    val label: String
        get() {
            val locationLabel =
                listOf(locationName, city, state, country).filter { it.isNotBlank() }.joinToString(
                    ", "
                )
            return when {
                locationLabel.isNotBlank() -> locationLabel
                description.isNotBlank() -> description
                originalDate.isNotBlank() -> originalDate
                else -> "unlabeled set"
            }
        }

    /**
     * Returns true if any field in this set is non-blank.
     * A completely empty set is not useful to offer in the UI.
     */
    fun hasAnyValue(): Boolean =
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
            subjects.isNotBlank()

    /**
     * Returns a summary line showing 2–3 key fields for compact display.
     * E.g. "Worcester, MA · 2024-06-15 · Canon"
     */
    val summary: String
        get() {
            val parts = mutableListOf<String>()
            val loc = listOfNotNull(
                if (locationName.isNotBlank()) locationName else null,
                if (city.isNotBlank()) city else null,
                if (state.isNotBlank()) state else null,
            ).joinToString(", ")
            if (loc.isNotBlank()) parts.add(loc)
            if (originalDate.isNotBlank()) parts.add(originalDate) else if (year.isNotBlank()) parts.add(year)
            if (cameraMake.isNotBlank() || cameraModel.isNotBlank()) {
                val cameraStr = listOfNotNull(
                    if (cameraMake.isNotBlank()) cameraMake else null,
                    if (cameraModel.isNotBlank()) cameraModel else null,
                ).joinToString(" ")
                if (cameraStr.isNotBlank()) parts.add(cameraStr)
            }
            return parts.joinToString(" · ")
        }

    /**
     * Applies this set's non-blank fields over an existing [PhotoScanConfiguration],
     * returning a new config with the merged values.
     *
     * Blank fields in this set are skipped, preserving the original config's values.
     * This matches the multi-edit "apply non-blank" convention.
     */
    fun mergeInto(config: PhotoScanConfiguration): PhotoScanConfiguration = config.copy(
        description = if (description.isNotBlank()) description else config.description,
        keywords = if (keywords.isNotBlank()) keywords else config.keywords,
        originalDate = if (originalDate.isNotBlank()) originalDate else config.originalDate,
        year = if (year.isNotBlank()) year else config.year,
        cameraMake = if (cameraMake.isNotBlank()) cameraMake else config.cameraMake,
        cameraModel = if (cameraModel.isNotBlank()) cameraModel else config.cameraModel,
        lensModel = if (lensModel.isNotBlank()) lensModel else config.lensModel,
        focalLength = if (focalLength.isNotBlank()) focalLength else config.focalLength,
        aperture = if (aperture.isNotBlank()) aperture else config.aperture,
        shutterSpeed = if (shutterSpeed.isNotBlank()) shutterSpeed else config.shutterSpeed,
        iso = if (iso.isNotBlank()) iso else config.iso,
        locationName = if (locationName.isNotBlank()) locationName else config.locationName,
        address = if (address.isNotBlank()) address else config.address,
        city = if (city.isNotBlank()) city else config.city,
        state = if (state.isNotBlank()) state else config.state,
        country = if (country.isNotBlank()) country else config.country,
        gpsLatitude = if (gpsLatitude.isNotBlank()) gpsLatitude else config.gpsLatitude,
        gpsLongitude = if (gpsLongitude.isNotBlank()) gpsLongitude else config.gpsLongitude,
        subjects = if (subjects.isNotBlank()) subjects else config.subjects,
    )

    /**
     * Applies only the location-related fields from this set over an existing
     * [PhotoScanConfiguration], returning a new config with merged location values.
     */
    fun mergeLocationInto(config: PhotoScanConfiguration): PhotoScanConfiguration = config.copy(
        locationName = if (locationName.isNotBlank()) locationName else config.locationName,
        address = if (address.isNotBlank()) address else config.address,
        city = if (city.isNotBlank()) city else config.city,
        state = if (state.isNotBlank()) state else config.state,
        country = if (country.isNotBlank()) country else config.country,
        gpsLatitude = if (gpsLatitude.isNotBlank()) gpsLatitude else config.gpsLatitude,
        gpsLongitude = if (gpsLongitude.isNotBlank()) gpsLongitude else config.gpsLongitude,
    )

    companion object {
        /**
         * Creates a [RecentMetadataSet] from a [PhotoScanConfiguration], capturing all metadata
         * fields. Note: face regions are excluded since they are photo-specific coordinate data,
         * not reusable metadata text.
         */
        fun fromConfig(config: PhotoScanConfiguration): RecentMetadataSet =
            RecentMetadataSet(
                description = config.description,
                keywords = config.keywords,
                originalDate = config.originalDate,
                year = config.year,
                cameraMake = config.cameraMake,
                cameraModel = config.cameraModel,
                lensModel = config.lensModel,
                focalLength = config.focalLength,
                aperture = config.aperture,
                shutterSpeed = config.shutterSpeed,
                iso = config.iso,
                locationName = config.locationName,
                address = config.address,
                city = config.city,
                state = config.state,
                country = config.country,
                gpsLatitude = config.gpsLatitude,
                gpsLongitude = config.gpsLongitude,
                subjects = config.subjects,
            )
    }
}