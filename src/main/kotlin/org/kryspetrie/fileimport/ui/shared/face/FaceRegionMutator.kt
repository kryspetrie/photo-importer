package org.kryspetrie.fileimport.ui.shared.face

import org.kryspetrie.fileimport.domain.model.FaceRegion
import org.kryspetrie.fileimport.domain.model.RegionType
import org.kryspetrie.fileimport.ui.wizard.state.FaceSize

/**
 * Shared mutation API for face regions used by
 * [org.kryspetrie.fileimport.ui.screens.wizard.FaceSelectorOverlay] and other editors. Decouples
 * the overlay from wizard-specific state holders.
 */
interface FaceRegionMutator {
    fun addFaceRegion(
        photoIndex: Int,
        name: String,
        x: Double,
        y: Double,
        type: RegionType = RegionType.FACE,
        size: FaceSize = FaceSize.DEFAULT,
    )

    fun removeFaceRegion(photoIndex: Int, faceIndex: Int)

    fun clearAllFaceRegions(photoIndex: Int)

    fun addDetectedFaceRegions(photoIndex: Int, regions: List<FaceRegion>)

    fun updateFaceRegionName(photoIndex: Int, faceIndex: Int, name: String)

    fun updateFaceRegion(photoIndex: Int, faceIndex: Int, x: Double? = null, y: Double? = null)

    fun resizeFaceRegion(photoIndex: Int, faceIndex: Int, size: FaceSize)

    fun moveFaceRegion(photoIndex: Int, faceIndex: Int, dx: Double, dy: Double)
}
