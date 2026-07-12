package org.kryspetrie.fileimport.ui.wizard.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.kryspetrie.fileimport.domain.model.ImportConfiguration

/**
 * Sub-state holding import-related settings: CV auto-detect, single photo mode, and import
 * configuration.
 */
class ImportSettingsState {

    /** Whether CV auto-detection of bounding boxes is enabled. Default: true. */
    private val _cvAutoDetectEnabled = MutableStateFlow(true)
    val cvAutoDetectEnabled: StateFlow<Boolean> = _cvAutoDetectEnabled.asStateFlow()

    fun setCvAutoDetectEnabled(enabled: Boolean) {
        _cvAutoDetectEnabled.value = enabled
    }

    /**
     * Whether single photo mode is active (skip multi-box detection, import one photo directly).
     */
    private val _singlePhotoMode = MutableStateFlow(false)
    val singlePhotoMode: StateFlow<Boolean> = _singlePhotoMode.asStateFlow()

    fun setSinglePhotoMode(enabled: Boolean) {
        _singlePhotoMode.value = enabled
    }

    /** The current import configuration. */
    private val _configuration = MutableStateFlow(ImportConfiguration())
    val configuration: StateFlow<ImportConfiguration> = _configuration.asStateFlow()

    fun setConfiguration(config: ImportConfiguration) {
        _configuration.value = config
    }

    /** Resets all import settings to defaults. */
    fun reset() {
        _cvAutoDetectEnabled.value = true
        _singlePhotoMode.value = false
        _configuration.value = ImportConfiguration()
    }
}
