package org.kryspetrie.fileimport.ui.shared.face

import org.kryspetrie.fileimport.domain.model.FaceRegion
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration
import org.kryspetrie.fileimport.domain.model.RegionType
import org.kryspetrie.fileimport.ui.screens.metadataeditor.BulkEditState
import org.kryspetrie.fileimport.ui.screens.metadataeditor.MetadataEditorPanelController
import org.kryspetrie.fileimport.ui.wizard.state.FaceSize

/** [FaceRegionMutator] backed by [BulkEditState] for the standalone metadata editor. */
class BulkEditFaceRegionMutator(
    private val bulkEditState: BulkEditState,
    private val onConfigUpdated: ((Int, PhotoScanConfiguration) -> Unit)? = null,
) : FaceRegionMutator {

    private fun updatePhoto(
        photoIndex: Int,
        transform: (PhotoScanConfiguration) -> PhotoScanConfiguration,
    ) {
        if (photoIndex < 0 || photoIndex >= bulkEditState.files.size) return
        bulkEditState.updateConfig(photoIndex) { existing ->
            val updated = transform(existing)
            onConfigUpdated?.invoke(photoIndex, updated)
            updated
        }
    }

    override fun addFaceRegion(
        photoIndex: Int,
        name: String,
        x: Double,
        y: Double,
        type: RegionType,
        size: FaceSize,
    ) {
        val faceRegion =
            FaceRegion(
                name = name,
                type = type.mwgRsValue,
                x = x.coerceIn(0.0, 1.0),
                y = y.coerceIn(0.0, 1.0),
                w = size.diameter,
                h = size.diameter,
            )
        updatePhoto(photoIndex) { existing ->
            val newRegions = existing.faceRegions + faceRegion
            existing.copy(
                faceRegions = newRegions,
                subjects =
                    MetadataEditorPanelController.mergeFaceNamesIntoSubjects(
                        existing.subjects,
                        newRegions,
                    ),
            )
        }
    }

    override fun removeFaceRegion(photoIndex: Int, faceIndex: Int) {
        updatePhoto(photoIndex) { existing ->
            if (faceIndex < 0 || faceIndex >= existing.faceRegions.size) return@updatePhoto existing
            val removed = existing.faceRegions[faceIndex]
            val newRegions = existing.faceRegions.filterIndexed { i, _ -> i != faceIndex }
            val currentSubjects =
                existing.subjects.split(",").map { it.trim() }.filter { it.isNotBlank() }
            val updatedSubjects = currentSubjects.filter { it != removed.name }
            existing.copy(faceRegions = newRegions, subjects = updatedSubjects.joinToString(", "))
        }
    }

    override fun clearAllFaceRegions(photoIndex: Int) {
        updatePhoto(photoIndex) { existing -> existing.copy(faceRegions = emptyList()) }
    }

    override fun addDetectedFaceRegions(photoIndex: Int, regions: List<FaceRegion>) {
        updatePhoto(photoIndex) { existing ->
            val newRegions = existing.faceRegions + regions
            existing.copy(
                faceRegions = newRegions,
                subjects =
                    MetadataEditorPanelController.mergeFaceNamesIntoSubjects(
                        existing.subjects,
                        newRegions,
                    ),
            )
        }
    }

    override fun updateFaceRegionName(photoIndex: Int, faceIndex: Int, name: String) {
        updatePhoto(photoIndex) { existing ->
            if (faceIndex < 0 || faceIndex >= existing.faceRegions.size) return@updatePhoto existing
            val updatedRegion = existing.faceRegions[faceIndex].copy(name = name)
            val newRegions =
                existing.faceRegions.mapIndexed { i, region ->
                    if (i == faceIndex) updatedRegion else region
                }
            existing.copy(
                faceRegions = newRegions,
                subjects =
                    MetadataEditorPanelController.mergeFaceNamesIntoSubjects(
                        existing.subjects,
                        newRegions,
                    ),
            )
        }
    }

    override fun updateFaceRegion(photoIndex: Int, faceIndex: Int, x: Double?, y: Double?) {
        updatePhoto(photoIndex) { existing ->
            if (faceIndex < 0 || faceIndex >= existing.faceRegions.size) return@updatePhoto existing
            val old = existing.faceRegions[faceIndex]
            val updated =
                old.copy(x = x?.coerceIn(0.0, 1.0) ?: old.x, y = y?.coerceIn(0.0, 1.0) ?: old.y)
            existing.copy(
                faceRegions =
                    existing.faceRegions.mapIndexed { i, region ->
                        if (i == faceIndex) updated else region
                    }
            )
        }
    }

    override fun resizeFaceRegion(photoIndex: Int, faceIndex: Int, size: FaceSize) {
        updatePhoto(photoIndex) { existing ->
            if (faceIndex < 0 || faceIndex >= existing.faceRegions.size) return@updatePhoto existing
            val old = existing.faceRegions[faceIndex]
            val updated = old.copy(w = size.diameter, h = size.diameter)
            existing.copy(
                faceRegions =
                    existing.faceRegions.mapIndexed { i, region ->
                        if (i == faceIndex) updated else region
                    }
            )
        }
    }

    override fun moveFaceRegion(photoIndex: Int, faceIndex: Int, dx: Double, dy: Double) {
        updatePhoto(photoIndex) { existing ->
            if (faceIndex < 0 || faceIndex >= existing.faceRegions.size) return@updatePhoto existing
            val old = existing.faceRegions[faceIndex]
            val updated =
                old.copy(x = (old.x + dx).coerceIn(0.0, 1.0), y = (old.y + dy).coerceIn(0.0, 1.0))
            existing.copy(
                faceRegions =
                    existing.faceRegions.mapIndexed { i, region ->
                        if (i == faceIndex) updated else region
                    }
            )
        }
    }
}
