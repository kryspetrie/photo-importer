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
        displayName = "Computer Vision",
        description = "Edge detection + contour tracing (classical method)",
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
        displayName = "Perspective Correction",
        description = "YOLO pose model finds exact 4 corners for perspective warp",
        usesYolo = true,
        providesCorners = true,
    ),
    @SerialName("hybrid")
    HYBRID(
        displayName = "Hybrid",
        description = "YOLO detection finds regions, then pose model refines corners",
        usesYolo = true,
        providesCorners = true,
    ),
}
