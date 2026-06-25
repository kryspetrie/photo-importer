package org.kryspetrie.fileimport.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Detection mode used to find photo regions in a scanned image.
 *
 * Each mode produces `DetectedPhoto` objects with different levels of geometric precision:
 * - CV modes: Classical edge detection pipelines (always available)
 * - YOLO modes: Neural network inference (requires ONNX models)
 *
 * @property displayName Human-readable label for UI
 * @property description Short description of what this mode does
 * @property usesYolo Whether this mode requires YOLO ONNX models
 * @property providesCorners Whether this mode provides precise 4-corner coordinates (false =
 *   axis-aligned bounding box only)
 */
@Serializable
enum class DetectionMode(
    val displayName: String,
    val description: String,
    val usesYolo: Boolean,
    val providesCorners: Boolean,
) {
    @SerialName("computer_vision")
    COMPUTER_VISION(
        displayName = "Simple Crop",
        description = "Edge detection + contour tracing — best for flat scans",
        usesYolo = false,
        providesCorners = true,
    ),
    @SerialName("bounding_box")
    BOUNDING_BOX(
        displayName = "Bounding Box",
        description = "YOLO detection model finds rectangular regions",
        usesYolo = true,
        providesCorners = false,
    ),
    @SerialName("perspective_correction")
    PERSPECTIVE_CORRECTION(
        displayName = "Perspective Crop",
        description = "Finds exact 4 corners for perspective correction — best for angled photos",
        usesYolo = true,
        providesCorners = true,
    ),
}
