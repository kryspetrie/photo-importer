package org.kryspetrie.fileimport.ui.screens.metadataeditor

import org.kryspetrie.fileimport.domain.model.OverrideState
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration

/**
 * Helpers for building override toggle states and field-change callbacks for the metadata editor
 * panel.
 *
 * These reduce the 12× repeated pattern of:
 * ```
 * overrideX = if (!isMultiSelect && singleEditConfig != null)
 *     singleEditConfig.overrideX != OverrideState.NULL_OUT else null,
 * onOverrideXChange = if (!isMultiSelect) {
 *     { included: Boolean -> state.updateSelectedConfig {
 *         it.copy(overrideX = if (included) OverrideState.KEEP_SOURCE else OverrideState.NULL_OUT)
 *     }}
 * } else null,
 * ```
 *
 * All helpers follow the same contract:
 * - In single-edit mode: they read from the config and update it.
 * - In multi-edit mode: they return `null`, disabling per-field override checkboxes.
 */

/**
 * Represents the state of an override toggle (checkbox).
 *
 * @property isChecked Whether the override is active (KEEP_SOURCE).
 * @property onToggle Callback to flip the override, or null if disabled (multi-edit).
 */
data class OverrideToggle(
    val isChecked: Boolean?,
    val onToggle: ((Boolean) -> Unit)?,
)

/**
 * Reads an override state from a config and returns a toggle for the UI.
 *
 * For description/keywords/date/gps fields: checked means KEEP_SOURCE (= preserve original),
 * unchecked means NULL_OUT (= remove from output).
 *
 * @param config The currently-selected file's config, or null in multi-edit mode.
 * @param getter Lambda that reads the override field from the config.
 * @param setter Lambda that returns a new config with the override field updated.
 * @param isMultiSelect Whether multiple files are selected (disables individual overrides).
 * @param updateConfig Callback to push the updated config back to the state.
 */
fun overrideToggle(
    config: PhotoScanConfiguration?,
    getter: (PhotoScanConfiguration) -> OverrideState?,
    setter: (PhotoScanConfiguration, OverrideState?) -> PhotoScanConfiguration,
    isMultiSelect: Boolean,
    updateConfig: ((PhotoScanConfiguration) -> PhotoScanConfiguration) -> Unit,
): OverrideToggle {
    if (isMultiSelect || config == null) return OverrideToggle(null, null)
    val current = getter(config)
    return OverrideToggle(
        isChecked = current != OverrideState.NULL_OUT,
        onToggle = { included: Boolean ->
            val target: OverrideState =
                if (included) OverrideState.KEEP_SOURCE else OverrideState.NULL_OUT
            updateConfig { setter(it, target) }
        },
    )
}

/**
 * Like [overrideToggle] but for camera fields that use KEEP_SOURCE == true semantics
 * (camera overrides are "on" when they KEEP_SOURCE, off when NULL_OUT).
 */
fun overrideCameraToggle(
    config: PhotoScanConfiguration?,
    getter: (PhotoScanConfiguration) -> OverrideState?,
    setter: (PhotoScanConfiguration, OverrideState?) -> PhotoScanConfiguration,
    isMultiSelect: Boolean,
    updateConfig: ((PhotoScanConfiguration) -> PhotoScanConfiguration) -> Unit,
): OverrideToggle {
    if (isMultiSelect || config == null) return OverrideToggle(null, null)
    val current = getter(config)
    return OverrideToggle(
        isChecked = current == OverrideState.KEEP_SOURCE,
        onToggle = { included: Boolean ->
            val target: OverrideState =
                if (included) OverrideState.KEEP_SOURCE else OverrideState.NULL_OUT
            updateConfig { setter(it, target) }
        },
    )
}