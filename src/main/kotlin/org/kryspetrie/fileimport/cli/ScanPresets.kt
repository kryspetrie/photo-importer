package org.kryspetrie.fileimport.cli

import org.kryspetrie.fileimport.domain.model.CorrectionStrategy
import org.kryspetrie.fileimport.domain.model.DetectionMode
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration

/**
 * Predefined scan presets that map to PhotoScanConfiguration values used by the GUI.
 *
 * Each preset adjusts detection mode and correction strategy for common workflows:
 * - **fast**: Simple axis-aligned crop, no ML-based refinement
 * - **pose_refine**: YOLO bounding box detection + perspective correction
 * - **corner_refine**: Full pipeline (YOLO pose + corner refinement), matches GUI default
 */
enum class ScanPreset(
    val displayName: String,
    val description: String,
    val configuration: PhotoScanConfiguration,
) {
    FAST(
        displayName = "Fast",
        description =
            "Simple axis-aligned crop — best for flat scans with clearly separated photos",
        configuration =
            PhotoScanConfiguration(
                correctionStrategy = CorrectionStrategy.CROP,
                detectionMode = DetectionMode.COMPUTER_VISION,
            ),
    ),
    POSE_REFINE(
        displayName = "Pose Refine",
        description = "YOLO detection with perspective correction — best for angled photos",
        configuration =
            PhotoScanConfiguration(
                correctionStrategy = CorrectionStrategy.PERSPECTIVE,
                detectionMode = DetectionMode.PERSPECTIVE_CORRECTION,
            ),
    ),
    CORNER_REFINE(
        displayName = "Corner Refine",
        description = "Full pipeline with corner refinement — matches GUI default, best quality",
        configuration =
            PhotoScanConfiguration(
                correctionStrategy = CorrectionStrategy.PERSPECTIVE,
                detectionMode = DetectionMode.PERSPECTIVE_CORRECTION,
            ),
    ),
}
