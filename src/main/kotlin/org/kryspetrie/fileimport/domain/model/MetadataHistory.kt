package org.kryspetrie.fileimport.domain.model

import kotlinx.serialization.Serializable

/**
 * Stores recently used metadata values for photo scan EXIF fields.
 *
 * Each field maintains an MRU list of up to [MAX_ENTRIES] unique values. Additionally,
 * [recentSets] stores complete snapshots of all metadata fields (including location) as a unit,
 * enabling users to apply a previously-entered set of values to the current photo.
 *
 * Persisted as part of [AppSettings] via [SettingsPort].
 */
@Serializable
data class MetadataHistory(
    val description: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
    val originalDate: List<String> = emptyList(),
    val year: List<String> = emptyList(),
    val cameraMake: List<String> = emptyList(),
    val cameraModel: List<String> = emptyList(),
    val lensModel: List<String> = emptyList(),
    val focalLength: List<String> = emptyList(),
    val aperture: List<String> = emptyList(),
    val shutterSpeed: List<String> = emptyList(),
    val iso: List<String> = emptyList(),
    // Location metadata history
    val locationName: List<String> = emptyList(),
    val city: List<String> = emptyList(),
    val state: List<String> = emptyList(),
    val country: List<String> = emptyList(),
    val gpsLatitude: List<String> = emptyList(),
    val gpsLongitude: List<String> = emptyList(),
    // Subject/face region history
    val subjects: List<String> = emptyList(),
    /**
     * Recent complete metadata sets — snapshots of all fields from a previously-entered
     * configuration. Stored MRU-first (most recently used is first).
     * Capped at [MAX_SETS] entries.
     */
    val recentSets: List<RecentMetadataSet> = emptyList(),
) {
    companion object {
        const val MAX_ENTRIES = 10
        const val MAX_SETS = 20

        /** Field name keys for looking up and updating suggestion lists. */
        val FIELD_KEYS: List<String> =
            listOf(
                "description",
                "keywords",
                "originalDate",
                "year",
                "cameraMake",
                "cameraModel",
                "lensModel",
                "focalLength",
                "aperture",
                "shutterSpeed",
                "iso",
                "locationName",
                "city",
                "state",
                "country",
                "gpsLatitude",
                "gpsLongitude",
                "subjects",
            )

        /**
         * Checks whether two recent sets match on all their non-blank fields.
         * Used for deduplication — if the new set has the same non-blank values as an existing
         * set, we consider them the same and only keep the newer one.
         */
        private fun fieldsMatch(a: RecentMetadataSet, b: RecentMetadataSet): Boolean {
            val fields =
                listOf(
                    a.description to b.description,
                    a.keywords to b.keywords,
                    a.originalDate to b.originalDate,
                    a.year to b.year,
                    a.cameraMake to b.cameraMake,
                    a.cameraModel to b.cameraModel,
                    a.lensModel to b.lensModel,
                    a.focalLength to b.focalLength,
                    a.aperture to b.aperture,
                    a.shutterSpeed to b.shutterSpeed,
                    a.iso to b.iso,
                    a.locationName to b.locationName,
                    a.city to b.city,
                    a.state to b.state,
                    a.country to b.country,
                    a.gpsLatitude to b.gpsLatitude,
                    a.gpsLongitude to b.gpsLongitude,
                    a.subjects to b.subjects,
                )
            // Two sets match if all fields where at least one is non-blank are equal
            return fields.all { (av, bv) ->
                if (av.isBlank() && bv.isBlank()) true
                else av == bv
            }
        }
    }

    /**
     * Adds a value to the front of the specified field's list, deduping and capping at
     * [MAX_ENTRIES].
     */
    fun addValue(fieldKey: String, value: String): MetadataHistory {
        if (value.isBlank()) return this
        val updated =
            (listOf(value) + getSuggestions(fieldKey).filter { it != value }).take(MAX_ENTRIES)
        return when (fieldKey) {
            "description" -> copy(description = updated)
            "keywords" -> copy(keywords = updated)
            "originalDate" -> copy(originalDate = updated)
            "year" -> copy(year = updated)
            "cameraMake" -> copy(cameraMake = updated)
            "cameraModel" -> copy(cameraModel = updated)
            "lensModel" -> copy(lensModel = updated)
            "focalLength" -> copy(focalLength = updated)
            "aperture" -> copy(aperture = updated)
            "shutterSpeed" -> copy(shutterSpeed = updated)
            "iso" -> copy(iso = updated)
            "locationName" -> copy(locationName = updated)
            "city" -> copy(city = updated)
            "state" -> copy(state = updated)
            "country" -> copy(country = updated)
            "gpsLatitude" -> copy(gpsLatitude = updated)
            "gpsLongitude" -> copy(gpsLongitude = updated)
            "subjects" -> copy(subjects = updated)
            else -> this
        }
    }

    /** Returns the suggestion list for the given field key. */
    fun getSuggestions(fieldKey: String): List<String> =
        when (fieldKey) {
            "description" -> description
            "keywords" -> keywords
            "originalDate" -> originalDate
            "year" -> year
            "cameraMake" -> cameraMake
            "cameraModel" -> cameraModel
            "lensModel" -> lensModel
            "focalLength" -> focalLength
            "aperture" -> aperture
            "shutterSpeed" -> shutterSpeed
            "iso" -> iso
            "locationName" -> locationName
            "city" -> city
            "state" -> state
            "country" -> country
            "gpsLatitude" -> gpsLatitude
            "gpsLongitude" -> gpsLongitude
            "subjects" -> subjects
            else -> emptyList()
        }

    /** Removes a specific value from a field's suggestion list. */
    fun removeValue(fieldKey: String, value: String): MetadataHistory {
        if (value.isBlank()) return this
        val updated = getSuggestions(fieldKey).filter { it != value }
        return when (fieldKey) {
            "description" -> copy(description = updated)
            "keywords" -> copy(keywords = updated)
            "originalDate" -> copy(originalDate = updated)
            "year" -> copy(year = updated)
            "cameraMake" -> copy(cameraMake = updated)
            "cameraModel" -> copy(cameraModel = updated)
            "lensModel" -> copy(lensModel = updated)
            "focalLength" -> copy(focalLength = updated)
            "aperture" -> copy(aperture = updated)
            "shutterSpeed" -> copy(shutterSpeed = updated)
            "iso" -> copy(iso = updated)
            "locationName" -> copy(locationName = updated)
            "city" -> copy(city = updated)
            "state" -> copy(state = updated)
            "country" -> copy(country = updated)
            "gpsLatitude" -> copy(gpsLatitude = updated)
            "gpsLongitude" -> copy(gpsLongitude = updated)
            "subjects" -> copy(subjects = updated)
            else -> this
        }
    }

    /**
     * Adds a complete metadata set to [recentSets]. Deduplicates against existing sets
     * (matched by all non-blank fields) and caps at [MAX_SETS]. The new set is placed at
     * the front (MRU order).
     */
    fun addSet(set: RecentMetadataSet): MetadataHistory {
        if (!set.hasAnyValue()) return this

        // Remove any existing set that matches all non-blank fields of the new set
        val deduped =
            recentSets.filter { existing ->
                !fieldsMatch(existing, set)
            }

        val updated = (listOf(set) + deduped).take(MAX_SETS)
        return copy(recentSets = updated)
    }

    /**
     * Removes a specific recent set by its timestamp (unique identifier).
     */
    fun removeSet(timestamp: Long): MetadataHistory =
        copy(recentSets = recentSets.filter { it.timestamp != timestamp })

    /**
     * Returns location-only suggestion sets — distinct combinations of location fields
     * from [recentSets]. Useful for the "Apply Location" quick-fill button which
     * specifically fills location fields (locationName, city, state, country, lat, lon).
     */
    fun getLocationSets(): List<RecentMetadataSet> =
        recentSets.filter {
            it.locationName.isNotBlank() ||
                it.city.isNotBlank() ||
                it.state.isNotBlank() ||
                it.country.isNotBlank() ||
                it.gpsLatitude.isNotBlank() ||
                it.gpsLongitude.isNotBlank()
        }
}