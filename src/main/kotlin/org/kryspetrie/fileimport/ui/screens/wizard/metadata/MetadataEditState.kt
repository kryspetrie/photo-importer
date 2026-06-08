package org.kryspetrie.fileimport.ui.screens.wizard.metadata

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration

/**
 * Holds buffered metadata field values for editing.
 *
 * In multi-edit mode, fields are buffered here and only applied to the selected photos when the
 * user clicks "Apply to Selected". In single-edit mode, changes can be applied immediately via
 * [applyToConfig].
 *
 * Also provides [applyNonBlankTo] for multi-edit, which only overwrites fields that the user has
 * explicitly filled in (leaving blank fields unchanged on the target config).
 */
class MetadataEditState {
    var description by mutableStateOf("")
    var keywords by mutableStateOf("")
    var originalDate by mutableStateOf("")
    var year by mutableStateOf("")
    var cameraMake by mutableStateOf("")
    var cameraModel by mutableStateOf("")
    var lensModel by mutableStateOf("")
    var focalLength by mutableStateOf("")
    var aperture by mutableStateOf("")
    var shutterSpeed by mutableStateOf("")
    var iso by mutableStateOf("")

    var locationName by mutableStateOf("")
    var city by mutableStateOf("")
    var state by mutableStateOf("")
    var country by mutableStateOf("")
    var gpsLatitude by mutableStateOf("")
    var gpsLongitude by mutableStateOf("")

    var subjects by mutableStateOf("")

    /** Resets all fields to empty strings (for multi-edit mode). */
    fun clear() {
        description = ""
        keywords = ""
        originalDate = ""
        year = ""
        cameraMake = ""
        cameraModel = ""
        lensModel = ""
        focalLength = ""
        aperture = ""
        shutterSpeed = ""
        iso = ""
        locationName = ""
        city = ""
        state = ""
        country = ""
        gpsLatitude = ""
        gpsLongitude = ""
        subjects = ""
    }

    /** Loads field values from an existing [PhotoScanConfiguration]. */
    fun loadFrom(config: PhotoScanConfiguration) {
        description = config.description
        keywords = config.keywords
        originalDate = config.originalDate
        year = config.year
        cameraMake = config.cameraMake
        cameraModel = config.cameraModel
        lensModel = config.lensModel
        focalLength = config.focalLength
        aperture = config.aperture
        shutterSpeed = config.shutterSpeed
        iso = config.iso
        locationName = config.locationName
        city = config.city
        state = config.state
        country = config.country
        gpsLatitude = config.gpsLatitude
        gpsLongitude = config.gpsLongitude
        subjects = config.subjects
    }

    /**
     * Returns a [PhotoScanConfiguration] copy of [base] with all fields from this state applied
     * (regardless of whether they are blank). Use this for single-edit mode where changes are
     * immediate.
     */
    fun applyToConfig(base: PhotoScanConfiguration): PhotoScanConfiguration =
        base.copy(
            description = description,
            keywords = keywords,
            originalDate = originalDate,
            year = year,
            cameraMake = cameraMake,
            cameraModel = cameraModel,
            lensModel = lensModel,
            focalLength = focalLength,
            aperture = aperture,
            shutterSpeed = shutterSpeed,
            iso = iso,
            locationName = locationName,
            city = city,
            state = state,
            country = country,
            gpsLatitude = gpsLatitude,
            gpsLongitude = gpsLongitude,
            subjects = subjects,
        )

    /**
     * Returns a [PhotoScanConfiguration] copy of [base] with only non-blank fields from this state
     * applied. Blank fields in this state are left unchanged on [base]. Use this for multi-edit
     * mode where only explicitly filled fields should be applied.
     */
    fun applyNonBlankTo(base: PhotoScanConfiguration): PhotoScanConfiguration =
        base.copy(
            description = if (description.isNotBlank()) description else base.description,
            keywords = if (keywords.isNotBlank()) keywords else base.keywords,
            originalDate = if (originalDate.isNotBlank()) originalDate else base.originalDate,
            year = if (year.isNotBlank()) year else base.year,
            cameraMake = if (cameraMake.isNotBlank()) cameraMake else base.cameraMake,
            cameraModel = if (cameraModel.isNotBlank()) cameraModel else base.cameraModel,
            lensModel = if (lensModel.isNotBlank()) lensModel else base.lensModel,
            focalLength = if (focalLength.isNotBlank()) focalLength else base.focalLength,
            aperture = if (aperture.isNotBlank()) aperture else base.aperture,
            shutterSpeed = if (shutterSpeed.isNotBlank()) shutterSpeed else base.shutterSpeed,
            iso = if (iso.isNotBlank()) iso else base.iso,
            locationName = if (locationName.isNotBlank()) locationName else base.locationName,
            city = if (city.isNotBlank()) city else base.city,
            state = if (state.isNotBlank()) state else base.state,
            country = if (country.isNotBlank()) country else base.country,
            gpsLatitude = if (gpsLatitude.isNotBlank()) gpsLatitude else base.gpsLatitude,
            gpsLongitude = if (gpsLongitude.isNotBlank()) gpsLongitude else base.gpsLongitude,
            subjects = if (subjects.isNotBlank()) subjects else base.subjects,
        )
}
