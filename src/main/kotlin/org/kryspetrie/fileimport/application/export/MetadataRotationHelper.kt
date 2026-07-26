package org.kryspetrie.fileimport.application.export

import org.kryspetrie.fileimport.domain.model.ImageFileType

/** Decides how rotation is applied per file format. */
object MetadataRotationHelper {
    fun usesPixelRotation(fileType: ImageFileType, rotationDegrees: Int): Boolean {
        if (normalizedDegrees(rotationDegrees) == 0) return false
        return FileFormatSupport.canRotatePixels(fileType)
    }

    fun usesMetadataOrientation(fileType: ImageFileType, rotationDegrees: Int): Boolean {
        if (normalizedDegrees(rotationDegrees) == 0) return false
        return !FileFormatSupport.canRotatePixels(fileType) &&
            FileFormatSupport.canSetOrientationLossless(fileType)
    }

    /** ExifTool-friendly orientation label for metadata-only rotation. */
    fun exifOrientationTag(rotationDegrees: Int): String =
        when (normalizedDegrees(rotationDegrees)) {
            90 -> "Rotate 90 CW"
            180 -> "Rotate 180"
            270 -> "Rotate 270 CW"
            else -> "Horizontal (normal)"
        }

    private fun normalizedDegrees(rotationDegrees: Int): Int = (rotationDegrees % 360 + 360) % 360
}
