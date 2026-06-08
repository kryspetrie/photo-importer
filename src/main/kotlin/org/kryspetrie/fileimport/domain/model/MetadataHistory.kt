package org.kryspetrie.fileimport.domain.model

import kotlinx.serialization.Serializable

/**
 * Stores recently used metadata values for photo scan EXIF fields.
 *
 * Each field maintains an MRU list of up to [MAX_ENTRIES] unique values. Persisted as part of
 * [AppSettings] via [SettingsPort].
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
    // Subject/face region history
    val subjects: List<String> = emptyList(),
) {
    companion object {
        const val MAX_ENTRIES = 10

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
                "subjects",
            )
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
            "subjects" -> copy(subjects = updated)
            else -> this
        }
    }
}
