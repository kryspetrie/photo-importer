package org.kryspetrie.fileimport.domain.model

import kotlinx.serialization.Serializable

/** Persisted scan-mode, export defaults, and UI state for the Photo Scan import landing screen. */
@Serializable
data class PhotoScanImportSessionPreferences(
    val cvAutoDetectEnabled: Boolean = true,
    val singlePhotoMode: Boolean = false,
    val settingsExpanded: Boolean = false,
    val perspectiveCorrectionEnabled: Boolean = true,
    val exportMarginPercent: Double = 0.02,
    val defaultCorrectionStrategy: CorrectionStrategy = CorrectionStrategy.PERSPECTIVE,
    val skipCropAndRotate: Boolean = false,
    val autoSkipBackFiles: Boolean = true,
) {
    /** Returns [exportMarginPercent] clamped to the slider range (0–20%). */
    fun normalizedExportMarginPercent(): Double = exportMarginPercent.coerceIn(0.0, 0.2)
}
