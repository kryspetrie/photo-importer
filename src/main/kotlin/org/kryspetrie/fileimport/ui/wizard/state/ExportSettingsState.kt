package org.kryspetrie.fileimport.ui.wizard.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.kryspetrie.fileimport.domain.model.CorrectionStrategy

/**
 * Sub-state holding export-related settings: perspective correction, margin percent, and default
 * correction strategy.
 */
class ExportSettingsState {

    /** Whether to apply perspective correction (warp-stretch) when exporting. Default: true. */
    private val _perspectiveCorrectionEnabled = MutableStateFlow(true)
    val perspectiveCorrectionEnabled: StateFlow<Boolean> =
        _perspectiveCorrectionEnabled.asStateFlow()

    fun setPerspectiveCorrectionEnabled(enabled: Boolean) {
        _perspectiveCorrectionEnabled.value = enabled
    }

    /**
     * Margin to add around each photo during export, expressed as a fraction of the photo's
     * diagonal length. Default: 0.02 (2%). For perspective correction: corners are pushed outward
     * from the quad center. For simple crop: the bounding box is expanded.
     */
    private val _exportMarginPercent = MutableStateFlow(0.02)
    val exportMarginPercent: StateFlow<Double> = _exportMarginPercent.asStateFlow()

    fun setExportMarginPercent(percent: Double) {
        _exportMarginPercent.value = percent.coerceIn(0.0, 0.2)
    }

    /** Default correction strategy for photos that don't have an explicit per-photo strategy. */
    private val _defaultCorrectionStrategy = MutableStateFlow(CorrectionStrategy.PERSPECTIVE)
    val defaultCorrectionStrategy: StateFlow<CorrectionStrategy> =
        _defaultCorrectionStrategy.asStateFlow()

    fun setDefaultCorrectionStrategy(strategy: CorrectionStrategy) {
        _defaultCorrectionStrategy.value = strategy
    }

    /** Resets all export settings to defaults. */
    fun reset() {
        _perspectiveCorrectionEnabled.value = true
        _exportMarginPercent.value = 0.02
        _defaultCorrectionStrategy.value = CorrectionStrategy.PERSPECTIVE
    }
}