package org.kryspetrie.fileimport.ui.screens.metadataeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.model.MetadataHistory
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration
import org.kryspetrie.fileimport.domain.model.RecentMetadataSet
import org.kryspetrie.fileimport.domain.port.DispatcherProvider
import org.kryspetrie.fileimport.domain.port.SettingsPort
import org.kryspetrie.fileimport.ui.components.ChunkyScrollbar
import org.kryspetrie.fileimport.ui.screens.wizard.edit.CameraSection
import org.kryspetrie.fileimport.ui.screens.wizard.edit.LocationSection
import org.kryspetrie.fileimport.ui.screens.wizard.edit.QuickEditMetadataFields
import org.kryspetrie.fileimport.ui.screens.wizard.edit.SubjectsSection
import org.kryspetrie.fileimport.ui.screens.wizard.metadata.MetadataEditState
import org.kryspetrie.fileimport.ui.screens.wizard.metadata.RecentValuesDropdown
import org.kryspetrie.fileimport.ui.wizard.state.SourceExifSummary
import kotlinx.coroutines.CoroutineScope

/**
 * Right-hand metadata editor panel for the bulk metadata editor.
 *
 * Adapts between single-edit and multi-edit modes:
 * - Single-edit: Shows all fields bound to the selected file's config, with override toggles.
 * - Multi-edit: Shows fields for batch editing, with an "Apply" button and no override toggles.
 *
 * @param state The bulk edit state.
 * @param editState The metadata edit state (field values).
 * @param isMultiEditMode Whether multi-edit mode is active.
 * @param selectedIndices Set of currently selected indices (multi-edit).
 * @param sourceExif Source EXIF summary for the current file.
 * @param metadataHistory Recent metadata values history.
 * @param onSettingsChange Callback when settings (history) are updated.
 * @param currentSettings Current app settings.
 * @param settingsPort Settings port for persistence.
 * @param coroutineScope Coroutine scope for async operations.
 * @param dispatcherProvider Dispatcher provider for thread switching.
 * @param onPickLocation Callback when location picker is requested.
 * @param onApply Callback when "Apply" is clicked in multi-edit mode (after clearing fields).
 * @param onClear Callback when "Clear" is clicked to reset edit fields.
 * @param modifier Modifier for the panel.
 */
@Composable
fun MetadataEditorPanel(
    state: BulkEditState,
    editState: MetadataEditState,
    isMultiEditMode: Boolean,
    selectedIndices: Set<Int>,
    sourceExif: SourceExifSummary?,
    metadataHistory: MetadataHistory,
    onSettingsChange: (AppSettings) -> Unit,
    currentSettings: AppSettings,
    settingsPort: SettingsPort,
    coroutineScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    onPickLocation: (List<Int>) -> Unit,
    onApply: () -> Unit = {},
    onClear: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isMultiSelect = isMultiEditMode && selectedIndices.size > 1
    val selectedIndex = if (!isMultiSelect && state.selectedIndex >= 0) state.selectedIndex else -1
    val singleEditConfig: PhotoScanConfiguration? =
        if (!isMultiSelect) state.selectedConfig else null
    val singleEditBoxId: String? = state.selectedFile?.absolutePath

    // Compute override toggles using helper — eliminates 12x repeated pattern
    val toggles = computeOverrideToggles(singleEditConfig, isMultiSelect, state)

    ChunkyScrollbar(modifier = modifier) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Header
            if (isMultiSelect) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${selectedIndices.size} photos selected",
                        style =
                            MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    OutlinedButton(
                        onClick = onClear,
                        modifier = Modifier.height(28.dp),
                    ) {
                        Text("Clear", style = MaterialTheme.typography.labelSmall)
                    }
                    Button(
                        onClick = onApply,
                        modifier = Modifier.height(28.dp),
                    ) {
                        Text("Apply", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Text(
                    "Only filled fields will be applied. Leave blank to keep existing values.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        state.selectedFile?.name ?: "No file selected",
                        style =
                            MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    OutlinedButton(
                        onClick = onClear,
                        modifier = Modifier.height(28.dp),
                    ) {
                        Text("Clear", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Recent values (multi-edit only)
            if (isMultiSelect && metadataHistory.recentSets.isNotEmpty()) {
                RecentValuesDropdown(
                    recentSets = metadataHistory.recentSets,
                    onApplySet = { set ->
                        editState.loadFromSet(set)
                        onSettingsChange(currentSettings.addMetadataSet(set))
                    },
                )
            }

            // Metadata sections
            QuickEditMetadataFields(
                description = editState.description,
                onDescriptionChange = { v ->
                    editState.description = v
                    singleEditBoxId?.let { state.updateSelectedConfig { it.copy(description = v) } }
                },
                keywords = editState.keywords,
                onKeywordsChange = { v ->
                    editState.keywords = v
                    singleEditBoxId?.let { state.updateSelectedConfig { it.copy(keywords = v) } }
                },
                originalDate = editState.originalDate,
                onOriginalDateChange = { v ->
                    editState.originalDate = v
                    singleEditBoxId?.let {
                        state.updateSelectedConfig { it.copy(originalDate = v) }
                    }
                },
                year = editState.year,
                onYearChange = { v ->
                    val f = v.filter { c -> c.isDigit() }.take(4)
                    editState.year = f
                    singleEditBoxId?.let { state.updateSelectedConfig { it.copy(year = f) } }
                },
                metadataHistory = metadataHistory,
                onMetadataHistoryUpdate = { field, value ->
                    onSettingsChange(currentSettings.addMetadataHistory(field, value))
                },
                onMetadataHistoryRemove = { field, value ->
                    onSettingsChange(currentSettings.removeMetadataHistory(field, value))
                },
                onCommitKeyword =
                    if (!isMultiSelect) {
                        { keyword ->
                            onSettingsChange(
                                currentSettings.addMetadataHistory("keywords", keyword)
                            )
                        }
                    } else null,
                boxId = singleEditBoxId,
                state = null, // Bulk edit doesn't use PhotoScanWizardState
                overrideDescription = toggles.description.isChecked,
                onOverrideDescriptionChange = toggles.description.onToggle,
                overrideKeywords = toggles.keywords.isChecked,
                onOverrideKeywordsChange = toggles.keywords.onToggle,
                overrideOriginalDate = toggles.originalDate.isChecked,
                onOverrideOriginalDateChange = toggles.originalDate.onToggle,
                overrideYear = toggles.year.isChecked,
                onOverrideYearChange = toggles.year.onToggle,
                sourceExif = sourceExif,
            )

            LocationSection(
                locationName = editState.locationName,
                onLocationNameChange = { v ->
                    editState.locationName = v
                    singleEditBoxId?.let {
                        state.updateSelectedConfig { it.copy(locationName = v) }
                    }
                },
                address = editState.address,
                onAddressChange = { v ->
                    editState.address = v
                    singleEditBoxId?.let { state.updateSelectedConfig { it.copy(address = v) } }
                },
                city = editState.city,
                onCityChange = { v ->
                    editState.city = v
                    singleEditBoxId?.let { state.updateSelectedConfig { it.copy(city = v) } }
                },
                stateVal = editState.state,
                onStateChange = { v ->
                    editState.state = v
                    singleEditBoxId?.let { state.updateSelectedConfig { it.copy(state = v) } }
                },
                country = editState.country,
                onCountryChange = { v ->
                    editState.country = v
                    singleEditBoxId?.let { state.updateSelectedConfig { it.copy(country = v) } }
                },
                gpsLatitude = editState.gpsLatitude,
                onGpsLatitudeChange = { v ->
                    editState.gpsLatitude = v
                    singleEditBoxId?.let { state.updateSelectedConfig { it.copy(gpsLatitude = v) } }
                },
                gpsLongitude = editState.gpsLongitude,
                onGpsLongitudeChange = { v ->
                    editState.gpsLongitude = v
                    singleEditBoxId?.let {
                        state.updateSelectedConfig { it.copy(gpsLongitude = v) }
                    }
                },
                metadataHistory = metadataHistory,
                onMetadataHistoryUpdate = { field, value ->
                    onSettingsChange(currentSettings.addMetadataHistory(field, value))
                },
                onApplyRecentLocation =
                    if (!isMultiSelect) {
                        { set: RecentMetadataSet ->
                            singleEditBoxId?.let {
                                state.updateSelectedConfig { set.mergeLocationInto(it) }
                            }
                            editState.loadFromSet(set)
                            Unit
                        }
                    } else {
                        { set: RecentMetadataSet ->
                            editState.loadFromSet(set)
                            Unit
                        }
                    },
                onPickLocation = {
                    onPickLocation(
                        if (isMultiSelect) selectedIndices.toList() else listOf(state.selectedIndex)
                    )
                },
                overrideGps = toggles.gps.isChecked,
                onOverrideGpsChange = toggles.gps.onToggle,
                sourceGpsHint =
                    run {
                        val exif = sourceExif ?: return@run null
                        val parts = mutableListOf<String>()
                        exif.gpsLatitude?.let { lat -> parts.add("Lat: $lat") }
                        exif.gpsLongitude?.let { lon -> parts.add("Lon: $lon") }
                        if (parts.isNotEmpty()) "Source: ${parts.joinToString(", ")}" else null
                    },
            )

            SubjectsSection(
                subjects = editState.subjects,
                onSubjectsChange = { v ->
                    editState.subjects = v
                    singleEditBoxId?.let { state.updateSelectedConfig { it.copy(subjects = v) } }
                },
                metadataHistory = metadataHistory,
                onMetadataHistoryUpdate = { field, value ->
                    onSettingsChange(currentSettings.addMetadataHistory(field, value))
                },
                onMetadataHistoryRemove = { field, value ->
                    onSettingsChange(currentSettings.removeMetadataHistory(field, value))
                },
                onSelectFaces = null,
                faceRegions = if (!isMultiSelect) state.selectedConfig.faceRegions else emptyList(),
                onRemoveFace = null,
                onClearAllFaces = null,
            )

            CameraSection(
                cameraMake = editState.cameraMake,
                onCameraMakeChange = { v ->
                    editState.cameraMake = v
                    singleEditBoxId?.let { state.updateSelectedConfig { it.copy(cameraMake = v) } }
                },
                cameraModel = editState.cameraModel,
                onCameraModelChange = { v ->
                    editState.cameraModel = v
                    singleEditBoxId?.let { state.updateSelectedConfig { it.copy(cameraModel = v) } }
                },
                lensModel = editState.lensModel,
                onLensModelChange = { v ->
                    editState.lensModel = v
                    singleEditBoxId?.let { state.updateSelectedConfig { it.copy(lensModel = v) } }
                },
                focalLength = editState.focalLength,
                onFocalLengthChange = { v ->
                    editState.focalLength = v
                    singleEditBoxId?.let { state.updateSelectedConfig { it.copy(focalLength = v) } }
                },
                aperture = editState.aperture,
                onApertureChange = { v ->
                    editState.aperture = v
                    singleEditBoxId?.let { state.updateSelectedConfig { it.copy(aperture = v) } }
                },
                shutterSpeed = editState.shutterSpeed,
                onShutterSpeedChange = { v ->
                    editState.shutterSpeed = v
                    singleEditBoxId?.let {
                        state.updateSelectedConfig { it.copy(shutterSpeed = v) }
                    }
                },
                iso = editState.iso,
                onIsoChange = { v ->
                    editState.iso = v
                    singleEditBoxId?.let { state.updateSelectedConfig { it.copy(iso = v) } }
                },
                metadataHistory = metadataHistory,
                onMetadataHistoryUpdate = { field, value ->
                    onSettingsChange(currentSettings.addMetadataHistory(field, value))
                },
                overrideCameraMake = toggles.cameraMake.isChecked,
                onOverrideCameraMakeChange = toggles.cameraMake.onToggle,
                overrideCameraModel = toggles.cameraModel.isChecked,
                onOverrideCameraModelChange = toggles.cameraModel.onToggle,
                overrideLensModel = toggles.lensModel.isChecked,
                onOverrideLensModelChange = toggles.lensModel.onToggle,
                overrideFocalLength = toggles.focalLength.isChecked,
                onOverrideFocalLengthChange = toggles.focalLength.onToggle,
                overrideAperture = toggles.aperture.isChecked,
                onOverrideApertureChange = toggles.aperture.onToggle,
                overrideShutterSpeed = toggles.shutterSpeed.isChecked,
                onOverrideShutterSpeedChange = toggles.shutterSpeed.onToggle,
                overrideIso = toggles.iso.isChecked,
                onOverrideIsoChange = toggles.iso.onToggle,
                sourceExif = null,
            )
        }
    }
}

// ── Override toggle computation ──

/**
 * Holds all override toggles computed for the metadata editor panel.
 *
 * Using this data class eliminates the 12x repeated pattern of checking isMultiSelect
 * and constructing override toggle callbacks inline.
 */
data class OverrideToggles(
    val description: OverrideToggle,
    val keywords: OverrideToggle,
    val originalDate: OverrideToggle,
    val year: OverrideToggle,
    val gps: OverrideToggle,
    val cameraMake: OverrideToggle,
    val cameraModel: OverrideToggle,
    val lensModel: OverrideToggle,
    val focalLength: OverrideToggle,
    val aperture: OverrideToggle,
    val shutterSpeed: OverrideToggle,
    val iso: OverrideToggle,
)

/**
 * Computes all override toggles for the metadata editor panel.
 *
 * In single-edit mode, each toggle reads from the config and updates it.
 * In multi-edit mode, all toggles are null (disabled).
 */
private fun computeOverrideToggles(
    config: PhotoScanConfiguration?,
    isMultiSelect: Boolean,
    state: BulkEditState,
): OverrideToggles {
    val update = state::updateSelectedConfig
    return OverrideToggles(
        description = overrideToggle(
            config, { c -> c.overrideDescription }, { c, v -> c.copy(overrideDescription = v) }, isMultiSelect, update
        ),
        keywords = overrideToggle(
            config, { c -> c.overrideKeywords }, { c, v -> c.copy(overrideKeywords = v) }, isMultiSelect, update
        ),
        originalDate = overrideToggle(
            config, { c -> c.overrideOriginalDate }, { c, v -> c.copy(overrideOriginalDate = v) }, isMultiSelect, update
        ),
        year = overrideToggle(
            config, { c -> c.overrideYear }, { c, v -> c.copy(overrideYear = v) }, isMultiSelect, update
        ),
        gps = overrideToggle(
            config, { c -> c.overrideGps }, { c, v -> c.copy(overrideGps = v) }, isMultiSelect, update
        ),
        cameraMake = overrideCameraToggle(
            config, { c -> c.overrideCameraMake }, { c, v -> c.copy(overrideCameraMake = v) }, isMultiSelect, update
        ),
        cameraModel = overrideCameraToggle(
            config, { c -> c.overrideCameraModel }, { c, v -> c.copy(overrideCameraModel = v) }, isMultiSelect, update
        ),
        lensModel = overrideCameraToggle(
            config, { c -> c.overrideLensModel }, { c, v -> c.copy(overrideLensModel = v) }, isMultiSelect, update
        ),
        focalLength = overrideCameraToggle(
            config, { c -> c.overrideFocalLength }, { c, v -> c.copy(overrideFocalLength = v) }, isMultiSelect, update
        ),
        aperture = overrideCameraToggle(
            config, { c -> c.overrideAperture }, { c, v -> c.copy(overrideAperture = v) }, isMultiSelect, update
        ),
        shutterSpeed = overrideCameraToggle(
            config, { c -> c.overrideShutterSpeed }, { c, v -> c.copy(overrideShutterSpeed = v) }, isMultiSelect, update
        ),
        iso = overrideCameraToggle(
            config, { c -> c.overrideIso }, { c, v -> c.copy(overrideIso = v) }, isMultiSelect, update
        ),
    )
}