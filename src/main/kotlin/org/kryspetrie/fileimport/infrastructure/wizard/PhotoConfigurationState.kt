package org.kryspetrie.fileimport.infrastructure.wizard

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages per-photo configuration state and metadata selection for the photo scan wizard.
 *
 * This is a composed sub-state of [PhotoScanWizardState], responsible for:
 * - Per-photo correction configurations (perspective, rotation, metadata)
 * - Metadata screen photo selection (multi-edit)
 * - Bulk operations on configurations (rotate all, perspective correction all, clear all)
 * - Applying metadata edits to selected photos
 *
 * @param _photoConfigurations Shared mutable reference to photo configurations map (keyed by box
 *   ID). Mutated in-place for all configuration changes.
 * @param _boundingBoxList Shared mutable reference to the bounding box list. Used to resolve
 *   photo-index → box-ID lookups and to iterate over all boxes for bulk operations.
 */
class PhotoConfigurationState(
    private val _photoConfigurations: MutableStateFlow<Map<String, PhotoConfiguration>>,
    private val _boundingBoxList: MutableStateFlow<BoundingBoxList>,
) {

    // ========== Photo Configurations ==========

    /** Read-only access to all photo configurations keyed by box ID. */
    val photoConfigurations: StateFlow<Map<String, PhotoConfiguration>>
        get() = _photoConfigurations.asStateFlow()

    /**
     * Sets the photo configuration for a specific box.
     *
     * @param boxId The unique identifier of the bounding box to configure
     * @param config The configuration to apply
     */
    fun setPhotoConfiguration(boxId: String, config: PhotoConfiguration) {
        _photoConfigurations.value = _photoConfigurations.value + (boxId to config)
    }

    /**
     * Updates the photo configuration for a specific box, preserving existing values.
     *
     * Unlike [setPhotoConfiguration] which replaces the entire config, this method applies a
     * transformation function to the existing config. Useful for updating a single field while
     * preserving others.
     *
     * @param boxId The ID of the box to update
     * @param update A function that takes existing config and returns new config
     */
    fun updatePhotoConfiguration(
        boxId: String,
        update: (PhotoConfiguration) -> PhotoConfiguration,
    ) {
        val existing = _photoConfigurations.value[boxId] ?: PhotoConfiguration()
        _photoConfigurations.value = _photoConfigurations.value + (boxId to update(existing))
    }

    /**
     * Clears the photo configuration for a specific box.
     *
     * @param boxId The ID of the box to clear configuration for
     */
    fun clearPhotoConfiguration(boxId: String) {
        _photoConfigurations.value = _photoConfigurations.value - boxId
    }

    /** Clears all photo configurations, resetting every box to default settings. */
    fun clearAllConfigurations() {
        _photoConfigurations.value = emptyMap()
    }

    // ========== Bulk Configuration Operations ==========

    /**
     * Rotates all bounding boxes 90° clockwise (cycles rotation: 0°→90°→180°→270°→0°).
     *
     * Each box's configuration is updated via [updatePhotoConfiguration].
     */
    fun rotateAllBoxesCW() {
        boxes.forEach { box -> updatePhotoConfiguration(box.id) { it.cycleRotationCW() } }
    }

    /**
     * Rotates all bounding boxes 90° counter-clockwise (cycles: 0°→270°→180°→90°→0°).
     */
    fun rotateAllBoxesCCW() {
        boxes.forEach { box -> updatePhotoConfiguration(box.id) { it.cycleRotationCCW() } }
    }

    /**
     * Enables or disables perspective correction for all bounding boxes.
     *
     * @param enabled True to enable perspective correction, false to disable
     */
    fun setPerspectiveCorrectionAll(enabled: Boolean) {
        boxes.forEach { box ->
            updatePhotoConfiguration(box.id) { it.copy(perspectiveCorrectionEnabled = enabled) }
        }
    }

    /** Returns all bounding boxes as a list. */
    val boxes: List<BoundingBox>
        get() = _boundingBoxList.value.boxes

    // ========== Metadata Screen Selection ==========

    /** Indices of photos selected on the metadata screen. Used for multi-edit. */
    private val _selectedMetadataIndices = MutableStateFlow<Set<Int>>(emptySet())
    val selectedMetadataIndices: StateFlow<Set<Int>> = _selectedMetadataIndices.asStateFlow()

    /** Toggles metadata selection for a photo index (for shift-click multi-select). */
    fun toggleMetadataSelection(index: Int) {
        val current = _selectedMetadataIndices.value
        _selectedMetadataIndices.value = if (index in current) current - index else current + index
    }

    /** Selects a single photo for metadata editing, replacing any previous selection. */
    fun selectSingleMetadata(index: Int) {
        _selectedMetadataIndices.value = setOf(index)
    }

    /** Selects all photos for metadata editing. */
    fun selectAllMetadata() {
        _selectedMetadataIndices.value = (0 until _boundingBoxList.value.size()).toSet()
    }

    /** Deselects all photos on the metadata screen. */
    fun deselectAllMetadata() {
        _selectedMetadataIndices.value = emptySet()
    }

    /**
     * Applies metadata fields to all selected photos on the metadata screen. Only non-empty fields
     * are applied — empty fields are left unchanged on each photo.
     */
    fun applyMetadataToSelected(
        description: String = "",
        keywords: String = "",
        originalDate: String = "",
        year: String = "",
        cameraModel: String = "",
        cameraMake: String = "",
        lensModel: String = "",
        focalLength: String = "",
        aperture: String = "",
        shutterSpeed: String = "",
        iso: String = "",
        locationName: String = "",
        city: String = "",
        state: String = "",
        country: String = "",
        gpsLatitude: String = "",
        gpsLongitude: String = "",
        subjects: String = "",
    ) {
        val indices = _selectedMetadataIndices.value
        val list = _boundingBoxList.value
        for (index in indices) {
            if (index >= 0 && index < list.size()) {
                val boxId = list.boxes[index].id
                updatePhotoConfiguration(boxId) { existing ->
                    existing.copy(
                        description =
                            if (description.isNotBlank()) description else existing.description,
                        keywords = if (keywords.isNotBlank()) keywords else existing.keywords,
                        originalDate =
                            if (originalDate.isNotBlank()) originalDate else existing.originalDate,
                        year = if (year.isNotBlank()) year else existing.year,
                        cameraModel =
                            if (cameraModel.isNotBlank()) cameraModel else existing.cameraModel,
                        cameraMake =
                            if (cameraMake.isNotBlank()) cameraMake else existing.cameraMake,
                        lensModel = if (lensModel.isNotBlank()) lensModel else existing.lensModel,
                        focalLength =
                            if (focalLength.isNotBlank()) focalLength else existing.focalLength,
                        aperture = if (aperture.isNotBlank()) aperture else existing.aperture,
                        shutterSpeed =
                            if (shutterSpeed.isNotBlank()) shutterSpeed else existing.shutterSpeed,
                        iso = if (iso.isNotBlank()) iso else existing.iso,
                        locationName =
                            if (locationName.isNotBlank()) locationName else existing.locationName,
                        city = if (city.isNotBlank()) city else existing.city,
                        state = if (state.isNotBlank()) state else existing.state,
                        country = if (country.isNotBlank()) country else existing.country,
                        gpsLatitude =
                            if (gpsLatitude.isNotBlank()) gpsLatitude else existing.gpsLatitude,
                        gpsLongitude =
                            if (gpsLongitude.isNotBlank()) gpsLongitude else existing.gpsLongitude,
                        subjects = if (subjects.isNotBlank()) subjects else existing.subjects,
                    )
                }
            }
        }
    }
}