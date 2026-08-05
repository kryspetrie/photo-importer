package org.kryspetrie.fileimport.ui.screens.metadataeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.model.MetadataHistory
import org.kryspetrie.fileimport.domain.model.RecentMetadataSet
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.domain.port.DispatcherProvider
import org.kryspetrie.fileimport.domain.port.SettingsPort
import org.kryspetrie.fileimport.ui.components.ChunkyScrollbar
import org.kryspetrie.fileimport.ui.i18n.strings
import org.kryspetrie.fileimport.ui.screens.shared.MetadataEditorPanelHeader
import org.kryspetrie.fileimport.ui.screens.shared.metadata.MetadataEditState
import org.kryspetrie.fileimport.ui.screens.shared.metadata.RecentValuesDropdown
import org.kryspetrie.fileimport.ui.screens.wizard.edit.CameraSection
import org.kryspetrie.fileimport.ui.screens.wizard.edit.LocationSection
import org.kryspetrie.fileimport.ui.screens.wizard.edit.QuickEditMetadataFields
import org.kryspetrie.fileimport.ui.screens.wizard.edit.SourceMetadataSection
import org.kryspetrie.fileimport.ui.screens.wizard.edit.SubjectsSection
import org.kryspetrie.fileimport.ui.wizard.state.SourceExifSummary

/**
 * Right-hand metadata editor panel for the bulk metadata editor.
 *
 * Adapts between single-edit and multi-edit modes:
 * - Single-edit: Shows all fields bound to the selected file's config, with source EXIF hints.
 * - Multi-edit: Shows fields for batch editing, with an "Apply" button.
 *
 * ## Upsert Semantics
 *
 * The standalone metadata editor uses pure upsert behavior:
 * - Non-blank field values are written to the output (overwriting any source value).
 * - Blank field values preserve the source EXIF (no overwrite).
 * - There are no per-field override checkboxes — the intent is always "write what I provide, keep
 *   what I don't touch."
 * - This matches the legacy `OverrideState = null` behavior in the EXIF writer, which is the
 *   simplest and most predictable model for bulk metadata editing.
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
    onSelectFaces: (() -> Unit)? = null,
    onRemoveFace: ((Int) -> Unit)? = null,
    onClearAllFaces: (() -> Unit)? = null,
    wideSourceMetadata: Boolean = false,
    keywordsFocusTrigger: Int = 0,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    val isMultiSelect =
        MetadataEditorPanelController.isMultiPhotoSelection(isMultiEditMode, selectedIndices.size)
    val isBatchEdit = MetadataEditorPanelController.isBatchEditMode(isMultiEditMode)
    val singleEditBoxId: String? = state.selectedFile?.absolutePath

    ChunkyScrollbar(modifier = modifier) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header
            if (isBatchEdit) {
                MetadataEditorPanelHeader(
                    title =
                        if (isMultiSelect) {
                            s.t(
                                StringKey.META_PHOTOS_SELECTED,
                                "count" to selectedIndices.size.toString(),
                            )
                        } else {
                            state.selectedFile?.name ?: s.t(StringKey.META_NO_FILE_SELECTED)
                        },
                    onClear = onClear,
                    onApply = onApply,
                    hint = s.t(StringKey.META_MULTI_EDIT_HINT),
                )
            } else {
                MetadataEditorPanelHeader(
                    title = state.selectedFile?.name ?: s.t(StringKey.META_NO_FILE_SELECTED),
                    onClear = onClear,
                )
            }

            // Recent values (multi-edit only)
            if (isMultiSelect && metadataHistory.recentSets.isNotEmpty()) {
                RecentValuesDropdown(
                    recentSets =
                        MetadataEditorPanelController.recentSetsForBatch(
                            metadataHistory,
                            isMultiSelect,
                        ),
                    onApplySet = { set ->
                        editState.loadFromSet(set)
                        onSettingsChange(currentSettings.addMetadataSet(set))
                    },
                )
            }

            // ── Source metadata display (single-edit only) ──
            if (!isMultiSelect && sourceExif != null) {
                SourceMetadataSection(sourceExif = sourceExif, wideLayout = wideSourceMetadata)
            }

            // ── Metadata sections ──
            // All override parameters are null: the standalone editor uses pure upsert semantics
            // (non-blank = write, blank = preserve source). No per-field override checkboxes.
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
                overrideDescription = null,
                onOverrideDescriptionChange = null,
                overrideKeywords = null,
                onOverrideKeywordsChange = null,
                overrideOriginalDate = null,
                onOverrideOriginalDateChange = null,
                overrideYear = null,
                onOverrideYearChange = null,
                sourceExif = sourceExif,
                keywordsFocusTrigger = keywordsFocusTrigger,
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
                overrideGps = null,
                onOverrideGpsChange = null,
                sourceGpsHint =
                    run {
                        val exif = sourceExif ?: return@run null
                        val parts = mutableListOf<String>()
                        exif.gpsLatitude?.let { lat ->
                            parts.add(s.t(StringKey.FIELD_SOURCE_LAT, "value" to lat))
                        }
                        exif.gpsLongitude?.let { lon ->
                            parts.add(s.t(StringKey.FIELD_SOURCE_LON, "value" to lon))
                        }
                        if (parts.isNotEmpty()) {
                            s.t(StringKey.FIELD_SOURCE_GPS, "value" to parts.joinToString(", "))
                        } else null
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
                onSelectFaces = if (!isMultiSelect) onSelectFaces else null,
                faceRegions = if (!isMultiSelect) state.selectedConfig.faceRegions else emptyList(),
                onRemoveFace = if (!isMultiSelect) onRemoveFace else null,
                onClearAllFaces = if (!isMultiSelect) onClearAllFaces else null,
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
                overrideCameraMake = null,
                onOverrideCameraMakeChange = null,
                overrideCameraModel = null,
                onOverrideCameraModelChange = null,
                overrideLensModel = null,
                onOverrideLensModelChange = null,
                overrideFocalLength = null,
                onOverrideFocalLengthChange = null,
                overrideAperture = null,
                onOverrideApertureChange = null,
                overrideShutterSpeed = null,
                onOverrideShutterSpeedChange = null,
                overrideIso = null,
                onOverrideIsoChange = null,
                sourceExif = sourceExif,
            )
        }
    }
}
