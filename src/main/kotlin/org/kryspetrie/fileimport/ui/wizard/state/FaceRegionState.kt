package org.kryspetrie.fileimport.ui.wizard.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.kryspetrie.fileimport.domain.model.FaceRegion
import org.kryspetrie.fileimport.domain.model.RegionType

/**
 * Manages face region state for the photo scan wizard: face selection mode, face region CRUD
 * operations (add, remove, update, move, resize), and auto-population of the subjects string from
 * face region names.
 *
 * This is a composed sub-state of [PhotoScanWizardState], responsible for all face-region-related
 * concerns. It holds its own [faceSelectMode] and [faceSelectPhotoIndex] StateFlows, and mutates
 * photo configurations (face regions, subjects) via the shared [_photoConfigurations] StateFlow and
 * looks up bounding boxes via the shared [_boundingBoxList] StateFlow.
 *
 * @param _photoConfigurations Shared mutable reference to photo configurations map (keyed by box
 *   ID). Mutated in-place to add/update/remove face regions and subjects.
 * @param _boundingBoxList Shared mutable reference to the bounding box list. Used to resolve
 *   photo-index → box-ID lookups.
 */
class FaceRegionState(
    private val _photoConfigurations: MutableStateFlow<Map<String, PhotoConfiguration>>,
    private val _boundingBoxList: MutableStateFlow<BoundingBoxList>,
) {

    // ========== Face Selection Mode ==========

    /** Whether face selection mode is active (fullscreen overlay for clicking faces). */
    private val _faceSelectMode = MutableStateFlow(false)
    val faceSelectMode: StateFlow<Boolean> = _faceSelectMode.asStateFlow()

    /** Index of the photo currently in face-select mode, or null if not active. */
    private val _faceSelectPhotoIndex = MutableStateFlow<Int?>(null)
    val faceSelectPhotoIndex: StateFlow<Int?> = _faceSelectPhotoIndex.asStateFlow()

    /** Enters face selection mode for a given photo index. */
    fun enterFaceSelectMode(photoIndex: Int) {
        _faceSelectMode.value = true
        _faceSelectPhotoIndex.value = photoIndex
    }

    /** Exits face selection mode. */
    fun exitFaceSelectMode() {
        _faceSelectMode.value = false
        _faceSelectPhotoIndex.value = null
    }

    // ========== Face Region CRUD ==========

    /**
     * Adds a face region to the specified photo's configuration. Creates a default-sized bounding
     * box centered at the given normalized coordinates.
     *
     * @param photoIndex Index of the photo in the bounding box list
     * @param name Person's name for the face region
     * @param x Center X as fraction of image width (0.0-1.0)
     * @param y Center Y as fraction of image height (0.0-1.0)
     * @param type Region type (default: FACE)
     * @param size Preset size (default: MEDIUM)
     */
    fun addFaceRegion(
        photoIndex: Int,
        name: String,
        x: Double,
        y: Double,
        type: RegionType = RegionType.FACE,
        size: FaceSize = FaceSize.DEFAULT,
    ) {
        val list = _boundingBoxList.value
        if (photoIndex < 0 || photoIndex >= list.size()) return
        val boxId = list.boxes[photoIndex].id

        val faceRegion =
            FaceRegion(
                name = name,
                type = type.mwgRsValue,
                x = x.coerceIn(0.0, 1.0),
                y = y.coerceIn(0.0, 1.0),
                w = size.diameter,
                h = size.diameter,
            )

        updatePhotoConfiguration(boxId) { existing ->
            val newRegions = existing.faceRegions + faceRegion
            // Auto-populate subjects string with face region names
            val names = newRegions.map { it.name }.filter { it.isNotBlank() }
            val newSubjects = names.joinToString(", ")
            existing.copy(faceRegions = newRegions, subjects = newSubjects)
        }
    }

    /**
     * Removes a face region by index from the specified photo's configuration.
     *
     * @param photoIndex Index of the photo in the bounding box list
     * @param faceIndex Index of the face region within the photo's faceRegions list
     */
    fun removeFaceRegion(photoIndex: Int, faceIndex: Int) {
        val list = _boundingBoxList.value
        if (photoIndex < 0 || photoIndex >= list.size()) return
        val boxId = list.boxes[photoIndex].id

        updatePhotoConfiguration(boxId) { existing ->
            if (faceIndex < 0 || faceIndex >= existing.faceRegions.size)
                return@updatePhotoConfiguration existing
            val removed = existing.faceRegions[faceIndex]
            val newRegions = existing.faceRegions.filterIndexed { i, _ -> i != faceIndex }
            // Remove the name from subjects string
            val currentSubjects =
                existing.subjects.split(",").map { it.trim() }.filter { it.isNotBlank() }
            val updatedSubjects = currentSubjects.filter { it != removed.name }
            existing.copy(faceRegions = newRegions, subjects = updatedSubjects.joinToString(", "))
        }
    }

    /**
     * Removes all face regions from the specified photo's configuration and clears the derived
     * subjects string.
     *
     * @param photoIndex Index of the photo in the bounding box list
     */
    fun clearAllFaceRegions(photoIndex: Int) {
        val list = _boundingBoxList.value
        if (photoIndex < 0 || photoIndex >= list.size()) return
        val boxId = list.boxes[photoIndex].id

        updatePhotoConfiguration(boxId) { existing ->
            existing.copy(faceRegions = emptyList(), subjects = "")
        }
    }

    /**
     * Adds multiple detected face regions at once (from auto-detection).
     *
     * Creates unnamed face regions at the detected positions. Names can be assigned later
     * via the naming cycle UI. Also auto-populates the subjects string with face names
     * (though initially empty for auto-detected regions).
     *
     * @param photoIndex Index of the photo in the bounding box list
     * @param regions List of face regions to add (typically from face detection)
     */
    fun addDetectedFaceRegions(photoIndex: Int, regions: List<FaceRegion>) {
        val list = _boundingBoxList.value
        if (photoIndex < 0 || photoIndex >= list.size()) return
        val boxId = list.boxes[photoIndex].id

        updatePhotoConfiguration(boxId) { existing ->
            val newRegions = existing.faceRegions + regions
            val names = newRegions.map { it.name }.filter { it.isNotBlank() }
            val newSubjects = names.joinToString(", ")
            existing.copy(faceRegions = newRegions, subjects = newSubjects)
        }
    }

    /**
     * Updates a face region's name at the given index.
     *
     * @param photoIndex Index of the photo in the bounding box list
     * @param faceIndex Index of the face region within the photo's faceRegions list
     * @param name New name for the face region
     */
    fun updateFaceRegionName(photoIndex: Int, faceIndex: Int, name: String) {
        val list = _boundingBoxList.value
        if (photoIndex < 0 || photoIndex >= list.size()) return
        val boxId = list.boxes[photoIndex].id

        updatePhotoConfiguration(boxId) { existing ->
            if (faceIndex < 0 || faceIndex >= existing.faceRegions.size)
                return@updatePhotoConfiguration existing
            val old = existing.faceRegions[faceIndex]
            val updated = old.copy(name = name)
            val newRegions =
                existing.faceRegions.mapIndexed { i, r -> if (i == faceIndex) updated else r }
            val names = newRegions.map { it.name }.filter { it.isNotBlank() }
            val newSubjects = names.joinToString(", ")
            existing.copy(faceRegions = newRegions, subjects = newSubjects)
        }
    }

    /**
     * Updates a face region's position at the given index (used for drag-to-move).
     *
     * @param photoIndex Index of the photo in the bounding box list
     * @param faceIndex Index of the face region within the photo's faceRegions list
     * @param x New center X (0.0-1.0), or null to keep current
     * @param y New center Y (0.0-1.0), or null to keep current
     */
    fun updateFaceRegion(photoIndex: Int, faceIndex: Int, x: Double? = null, y: Double? = null) {
        val list = _boundingBoxList.value
        if (photoIndex < 0 || photoIndex >= list.size()) return
        val boxId = list.boxes[photoIndex].id

        updatePhotoConfiguration(boxId) { existing ->
            if (faceIndex < 0 || faceIndex >= existing.faceRegions.size)
                return@updatePhotoConfiguration existing
            val old = existing.faceRegions[faceIndex]
            val updated =
                old.copy(x = x?.coerceIn(0.0, 1.0) ?: old.x, y = y?.coerceIn(0.0, 1.0) ?: old.y)
            existing.copy(
                faceRegions =
                    existing.faceRegions.mapIndexed { i, r -> if (i == faceIndex) updated else r }
            )
        }
    }

    /**
     * Changes a face region's size to one of the preset sizes.
     *
     * @param photoIndex Index of the photo in the bounding box list
     * @param faceIndex Index of the face region within the photo's faceRegions list
     * @param size The new preset size
     */
    fun resizeFaceRegion(photoIndex: Int, faceIndex: Int, size: FaceSize) {
        val list = _boundingBoxList.value
        if (photoIndex < 0 || photoIndex >= list.size()) return
        val boxId = list.boxes[photoIndex].id

        updatePhotoConfiguration(boxId) { existing ->
            if (faceIndex < 0 || faceIndex >= existing.faceRegions.size)
                return@updatePhotoConfiguration existing
            val old = existing.faceRegions[faceIndex]
            val updated = old.copy(w = size.diameter, h = size.diameter)
            existing.copy(
                faceRegions =
                    existing.faceRegions.mapIndexed { i, r -> if (i == faceIndex) updated else r }
            )
        }
    }

    /**
     * Moves a face region by offsetting its center position.
     *
     * @param photoIndex Index of the photo in the bounding box list
     * @param faceIndex Index of the face region within the photo's faceRegions list
     * @param dx X offset to add (in normalized coordinates)
     * @param dy Y offset to add (in normalized coordinates)
     */
    fun moveFaceRegion(photoIndex: Int, faceIndex: Int, dx: Double, dy: Double) {
        val list = _boundingBoxList.value
        if (photoIndex < 0 || photoIndex >= list.size()) return
        val boxId = list.boxes[photoIndex].id

        updatePhotoConfiguration(boxId) { existing ->
            if (faceIndex < 0 || faceIndex >= existing.faceRegions.size)
                return@updatePhotoConfiguration existing
            val old = existing.faceRegions[faceIndex]
            val updated =
                old.copy(x = (old.x + dx).coerceIn(0.0, 1.0), y = (old.y + dy).coerceIn(0.0, 1.0))
            existing.copy(
                faceRegions =
                    existing.faceRegions.mapIndexed { i, r -> if (i == faceIndex) updated else r }
            )
        }
    }

    // ========== Internal Helpers ==========

    /**
     * Updates the photo configuration for a specific box, preserving existing values. Internal
     * helper that mirrors [PhotoScanWizardState.updatePhotoConfiguration].
     */
    private fun updatePhotoConfiguration(
        boxId: String,
        update: (PhotoConfiguration) -> PhotoConfiguration,
    ) {
        val existing = _photoConfigurations.value[boxId] ?: PhotoConfiguration()
        _photoConfigurations.value = _photoConfigurations.value + (boxId to update(existing))
    }
}