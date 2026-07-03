package org.kryspetrie.fileimport.ui.screens.wizard.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.awt.image.BufferedImage
import org.kryspetrie.fileimport.domain.port.PerspectiveCorrectionPort
import org.kryspetrie.fileimport.domain.model.MetadataHistory
import org.kryspetrie.fileimport.domain.model.OverrideState
import org.kryspetrie.fileimport.domain.model.RecentMetadataSet
import org.kryspetrie.fileimport.domain.model.geometry.BoundingBoxList
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration
import org.kryspetrie.fileimport.ui.screens.wizard.metadata.MetadataEditState
import org.kryspetrie.fileimport.ui.wizard.state.PhotoScanWizardState
import org.kryspetrie.fileimport.ui.wizard.state.SourceExifSummary
import org.kryspetrie.fileimport.ui.components.ChunkyScrollbar
import org.kryspetrie.fileimport.ui.screens.wizard.metadata.RecentValuesDropdown

/**
 * Metadata editor panel — shown in Metadata mode. Contains all the metadata editing functionality.
 *
 * In multi-edit mode, uses [MetadataEditState] to buffer values until the user clicks "Apply".
 * In single-edit mode, [MetadataEditState] is synced from the selected photo's config and changes
 * are applied immediately via direct config updates.
 */
@Composable
internal fun MetadataEditorPanel(
    state: PhotoScanWizardState,
    image: BufferedImage,
    perspectiveService: PerspectiveCorrectionPort,
    boundingBoxList: BoundingBoxList,
    photoConfigurations: Map<String, PhotoScanConfiguration>,
    selectedIndices: Set<Int>,
    isMultiEditMode: Boolean,
    metadataHistory: MetadataHistory,
    onMetadataHistoryUpdate: (String, String) -> Unit,
    onMetadataHistoryRemove: (String, String) -> Unit,
    sourceExif: SourceExifSummary?,
    onSelectFaces: (Int) -> Unit,
    onPickLocation: (Int) -> Unit,
    onRecordMetadataSet: (RecentMetadataSet) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isMultiSelect = selectedIndices.size > 1 || isMultiEditMode

    // Single MetadataEditState for both multi-edit buffering and single-edit display
    val editState = remember { MetadataEditState() }

    // ── Single-edit mode: resolve selected photo ──
    val selectedIndex = if (!isMultiSelect && selectedIndices.isNotEmpty()) {
        selectedIndices.first()
    } else -1

    val singleEditBoxId: String? =
        if (!isMultiSelect && selectedIndex >= 0 && selectedIndex < boundingBoxList.size()) {
            boundingBoxList.boxes[selectedIndex].id
        } else null

    val singleEditConfig: PhotoScanConfiguration? =
        if (singleEditBoxId != null) {
            photoConfigurations[singleEditBoxId] ?: PhotoScanConfiguration()
        } else null

    // ── Sync editState from config in single-edit mode ──
    // Re-sync when selection changes OR when config fields are updated externally
    // (e.g. location picker updates city/state/country/GPS directly in the config).
    LaunchedEffect(
        singleEditBoxId,
        singleEditConfig?.locationName,
        singleEditConfig?.address,
        singleEditConfig?.city,
        singleEditConfig?.state,
        singleEditConfig?.country,
        singleEditConfig?.gpsLatitude,
        singleEditConfig?.gpsLongitude,
    ) {
        if (!isMultiSelect && singleEditConfig != null) {
            editState.loadFrom(singleEditConfig)
        }
    }

    // ── Clear editState when switching to multi-edit mode ──
    LaunchedEffect(isMultiSelect) {
        if (isMultiSelect) {
            editState.clear()
        }
    }

    ChunkyScrollbar(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ── Header ──
            if (isMultiSelect) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${selectedIndices.size} ${if (selectedIndices.size == 1) "photo" else "photos"} selected",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Button(
                        onClick = {
                            state.configs.applyMetadataToSelected(editState)
                            onRecordMetadataSet(editState.toRecentMetadataSet())
                        },
                        modifier = Modifier.height(32.dp),
                    ) {
                        Text("Apply", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Text(
                    "Only filled fields will be applied. Leave blank to keep existing values.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "Photo ${selectedIndex + 1}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // ── Recent Values ──
            if (metadataHistory.recentSets.isNotEmpty()) {
                if (isMultiSelect) {
                    RecentValuesDropdown(
                        recentSets = metadataHistory.recentSets,
                        onApplySet = { set ->
                            editState.loadFromSet(set)
                            onRecordMetadataSet(set)
                        },
                    )
                } else {
                    RecentValuesDropdown(
                        recentSets = metadataHistory.recentSets,
                        onApplySet = { set ->
                            singleEditBoxId?.let { id ->
                                state.configs.updatePhotoScanConfiguration(id) { set.mergeInto(it) }
                            }
                            editState.loadFromSet(set)
                            onRecordMetadataSet(set)
                        },
                    )
                }
            }

            // ── Metadata sections (shared between modes) ──
            // All field values come from editState; in single-edit mode, field changes are also
            // immediately pushed to the photo config via state.configs.updatePhotoScanConfiguration().

            QuickEditMetadataFields(
                description = editState.description,
                onDescriptionChange = { newValue ->
                    editState.description = newValue
                    singleEditBoxId?.let { id ->
                        state.configs.updatePhotoScanConfiguration(id) { it.copy(description = newValue) }
                    }
                },
                keywords = editState.keywords,
                onKeywordsChange = { newValue ->
                    editState.keywords = newValue
                    singleEditBoxId?.let { id ->
                        state.configs.updatePhotoScanConfiguration(id) { it.copy(keywords = newValue) }
                    }
                },
                originalDate = editState.originalDate,
                onOriginalDateChange = { newValue ->
                    editState.originalDate = newValue
                    singleEditBoxId?.let { id ->
                        state.configs.updatePhotoScanConfiguration(id) { it.copy(originalDate = newValue) }
                    }
                },
                year = editState.year,
                onYearChange = { newValue ->
                    val filtered = newValue.filter { c -> c.isDigit() }.take(4)
                    editState.year = filtered
                    singleEditBoxId?.let { id ->
                        state.configs.updatePhotoScanConfiguration(id) { it.copy(year = filtered) }
                    }
                },
                metadataHistory = metadataHistory,
                onMetadataHistoryUpdate = onMetadataHistoryUpdate,
                onMetadataHistoryRemove = onMetadataHistoryRemove,
                onCommitKeyword = if (!isMultiSelect) {
                    { keyword -> onMetadataHistoryUpdate("keywords", keyword) }
                } else null,
                boxId = singleEditBoxId,
                state = if (!isMultiSelect) state else null,
                // Override checkboxes (single-edit only)
                overrideDescription = if (!isMultiSelect && singleEditConfig != null) {
                    singleEditConfig.overrideDescription != OverrideState.NULL_OUT
                } else null,
                onOverrideDescriptionChange = if (!isMultiSelect) { { included: Boolean ->
                    singleEditBoxId?.let { id ->
                        state.configs.updatePhotoScanConfiguration(id) {
                            it.copy(overrideDescription = if (included) OverrideState.KEEP_SOURCE else OverrideState.NULL_OUT)
                        }
                    }
                } } else null,
                overrideKeywords = if (!isMultiSelect && singleEditConfig != null) {
                    singleEditConfig.overrideKeywords != OverrideState.NULL_OUT
                } else null,
                onOverrideKeywordsChange = if (!isMultiSelect) { { included: Boolean ->
                    singleEditBoxId?.let { id ->
                        state.configs.updatePhotoScanConfiguration(id) {
                            it.copy(overrideKeywords = if (included) OverrideState.KEEP_SOURCE else OverrideState.NULL_OUT)
                        }
                    }
                } } else null,
                overrideOriginalDate = if (!isMultiSelect && singleEditConfig != null) {
                    singleEditConfig.overrideOriginalDate != OverrideState.NULL_OUT
                } else null,
                onOverrideOriginalDateChange = if (!isMultiSelect) { { included: Boolean ->
                    singleEditBoxId?.let { id ->
                        state.configs.updatePhotoScanConfiguration(id) {
                            it.copy(overrideOriginalDate = if (included) OverrideState.KEEP_SOURCE else OverrideState.NULL_OUT)
                        }
                    }
                } } else null,
                overrideYear = if (!isMultiSelect && singleEditConfig != null) {
                    singleEditConfig.overrideYear != OverrideState.NULL_OUT
                } else null,
                onOverrideYearChange = if (!isMultiSelect) { { included: Boolean ->
                    singleEditBoxId?.let { id ->
                        state.configs.updatePhotoScanConfiguration(id) {
                            it.copy(overrideYear = if (included) OverrideState.KEEP_SOURCE else OverrideState.NULL_OUT)
                        }
                    }
                } } else null,
                sourceExif = sourceExif,
            )

            CameraSection(
                cameraMake = editState.cameraMake,
                onCameraMakeChange = { newValue ->
                    editState.cameraMake = newValue
                    singleEditBoxId?.let { id ->
                        state.configs.updatePhotoScanConfiguration(id) { it.copy(cameraMake = newValue) }
                    }
                },
                cameraModel = editState.cameraModel,
                onCameraModelChange = { newValue ->
                    editState.cameraModel = newValue
                    singleEditBoxId?.let { id ->
                        state.configs.updatePhotoScanConfiguration(id) { it.copy(cameraModel = newValue) }
                    }
                },
                lensModel = editState.lensModel,
                onLensModelChange = { newValue ->
                    editState.lensModel = newValue
                    singleEditBoxId?.let { id ->
                        state.configs.updatePhotoScanConfiguration(id) { it.copy(lensModel = newValue) }
                    }
                },
                focalLength = editState.focalLength,
                onFocalLengthChange = { newValue ->
                    editState.focalLength = newValue
                    singleEditBoxId?.let { id ->
                        state.configs.updatePhotoScanConfiguration(id) { it.copy(focalLength = newValue) }
                    }
                },
                aperture = editState.aperture,
                onApertureChange = { newValue ->
                    editState.aperture = newValue
                    singleEditBoxId?.let { id ->
                        state.configs.updatePhotoScanConfiguration(id) { it.copy(aperture = newValue) }
                    }
                },
                shutterSpeed = editState.shutterSpeed,
                onShutterSpeedChange = { newValue ->
                    editState.shutterSpeed = newValue
                    singleEditBoxId?.let { id ->
                        state.configs.updatePhotoScanConfiguration(id) { it.copy(shutterSpeed = newValue) }
                    }
                },
                iso = editState.iso,
                onIsoChange = { newValue ->
                    editState.iso = newValue
                    singleEditBoxId?.let { id ->
                        state.configs.updatePhotoScanConfiguration(id) { it.copy(iso = newValue) }
                    }
                },
                metadataHistory = metadataHistory,
                onMetadataHistoryUpdate = onMetadataHistoryUpdate,
                // Override checkboxes (single-edit only)
                overrideCameraMake = if (!isMultiSelect && singleEditConfig != null) {
                    singleEditConfig.overrideCameraMake != OverrideState.NULL_OUT
                } else null,
                onOverrideCameraMakeChange = if (!isMultiSelect) { { included: Boolean ->
                    singleEditBoxId?.let { id ->
                        state.configs.updatePhotoScanConfiguration(id) {
                            it.copy(overrideCameraMake = if (included) OverrideState.KEEP_SOURCE else OverrideState.NULL_OUT)
                        }
                    }
                } } else null,
                overrideCameraModel = if (!isMultiSelect && singleEditConfig != null) {
                    singleEditConfig.overrideCameraModel != OverrideState.NULL_OUT
                } else null,
                onOverrideCameraModelChange = if (!isMultiSelect) { { included: Boolean ->
                    singleEditBoxId?.let { id ->
                        state.configs.updatePhotoScanConfiguration(id) {
                            it.copy(overrideCameraModel = if (included) OverrideState.KEEP_SOURCE else OverrideState.NULL_OUT)
                        }
                    }
                } } else null,
                overrideLensModel = if (!isMultiSelect && singleEditConfig != null) {
                    singleEditConfig.overrideLensModel != OverrideState.NULL_OUT
                } else null,
                onOverrideLensModelChange = if (!isMultiSelect) { { included: Boolean ->
                    singleEditBoxId?.let { id ->
                        state.configs.updatePhotoScanConfiguration(id) {
                            it.copy(overrideLensModel = if (included) OverrideState.KEEP_SOURCE else OverrideState.NULL_OUT)
                        }
                    }
                } } else null,
                overrideFocalLength = if (!isMultiSelect && singleEditConfig != null) {
                    singleEditConfig.overrideFocalLength != OverrideState.NULL_OUT
                } else null,
                onOverrideFocalLengthChange = if (!isMultiSelect) { { included: Boolean ->
                    singleEditBoxId?.let { id ->
                        state.configs.updatePhotoScanConfiguration(id) {
                            it.copy(overrideFocalLength = if (included) OverrideState.KEEP_SOURCE else OverrideState.NULL_OUT)
                        }
                    }
                } } else null,
                overrideAperture = if (!isMultiSelect && singleEditConfig != null) {
                    singleEditConfig.overrideAperture != OverrideState.NULL_OUT
                } else null,
                onOverrideApertureChange = if (!isMultiSelect) { { included: Boolean ->
                    singleEditBoxId?.let { id ->
                        state.configs.updatePhotoScanConfiguration(id) {
                            it.copy(overrideAperture = if (included) OverrideState.KEEP_SOURCE else OverrideState.NULL_OUT)
                        }
                    }
                } } else null,
                overrideShutterSpeed = if (!isMultiSelect && singleEditConfig != null) {
                    singleEditConfig.overrideShutterSpeed != OverrideState.NULL_OUT
                } else null,
                onOverrideShutterSpeedChange = if (!isMultiSelect) { { included: Boolean ->
                    singleEditBoxId?.let { id ->
                        state.configs.updatePhotoScanConfiguration(id) {
                            it.copy(overrideShutterSpeed = if (included) OverrideState.KEEP_SOURCE else OverrideState.NULL_OUT)
                        }
                    }
                } } else null,
                overrideIso = if (!isMultiSelect && singleEditConfig != null) {
                    singleEditConfig.overrideIso != OverrideState.NULL_OUT
                } else null,
                onOverrideIsoChange = if (!isMultiSelect) { { included: Boolean ->
                    singleEditBoxId?.let { id ->
                        state.configs.updatePhotoScanConfiguration(id) {
                            it.copy(overrideIso = if (included) OverrideState.KEEP_SOURCE else OverrideState.NULL_OUT)
                        }
                    }
                } } else null,
                sourceExif = sourceExif,
            )

            LocationSection(
                locationName = editState.locationName,
                onLocationNameChange = { newValue ->
                    editState.locationName = newValue
                    singleEditBoxId?.let { id ->
                        state.configs.updatePhotoScanConfiguration(id) { it.copy(locationName = newValue) }
                    }
                },
                address = editState.address,
                onAddressChange = { newValue ->
                    editState.address = newValue
                    singleEditBoxId?.let { id ->
                        state.configs.updatePhotoScanConfiguration(id) { it.copy(address = newValue) }
                    }
                },
                city = editState.city,
                onCityChange = { newValue ->
                    editState.city = newValue
                    singleEditBoxId?.let { id ->
                        state.configs.updatePhotoScanConfiguration(id) { it.copy(city = newValue) }
                    }
                },
                stateVal = editState.state,
                onStateChange = { newValue ->
                    editState.state = newValue
                    singleEditBoxId?.let { id ->
                        state.configs.updatePhotoScanConfiguration(id) { it.copy(state = newValue) }
                    }
                },
                country = editState.country,
                onCountryChange = { newValue ->
                    editState.country = newValue
                    singleEditBoxId?.let { id ->
                        state.configs.updatePhotoScanConfiguration(id) { it.copy(country = newValue) }
                    }
                },
                gpsLatitude = editState.gpsLatitude,
                onGpsLatitudeChange = { newValue ->
                    editState.gpsLatitude = newValue
                    singleEditBoxId?.let { id ->
                        state.configs.updatePhotoScanConfiguration(id) { it.copy(gpsLatitude = newValue) }
                    }
                },
                gpsLongitude = editState.gpsLongitude,
                onGpsLongitudeChange = { newValue ->
                    editState.gpsLongitude = newValue
                    singleEditBoxId?.let { id ->
                        state.configs.updatePhotoScanConfiguration(id) { it.copy(gpsLongitude = newValue) }
                    }
                },
                metadataHistory = metadataHistory,
                onMetadataHistoryUpdate = onMetadataHistoryUpdate,
                // Location recent values: multi-edit uses editState, single-edit pushes to config
                onApplyRecentLocation = if (!isMultiSelect) { { set ->
                    singleEditBoxId?.let { id ->
                        state.configs.updatePhotoScanConfiguration(id) { set.mergeLocationInto(it) }
                    }
                    editState.loadFromSet(set)
                } } else { { set -> editState.loadFromSet(set) } },
                // Location picker (single-edit only)
                onPickLocation = if (!isMultiSelect && selectedIndex >= 0) {
                    { onPickLocation(selectedIndex) }
                } else null,
                // GPS override (single-edit only)
                overrideGps = if (!isMultiSelect && singleEditConfig != null) {
                    singleEditConfig.overrideGps != OverrideState.NULL_OUT
                } else null,
                onOverrideGpsChange = if (!isMultiSelect) { { included: Boolean ->
                    singleEditBoxId?.let { id ->
                        state.configs.updatePhotoScanConfiguration(id) {
                            it.copy(overrideGps = if (included) OverrideState.KEEP_SOURCE else OverrideState.NULL_OUT)
                        }
                    }
                } } else null,
                sourceGpsHint = sourceExif?.let {
                    val parts = mutableListOf<String>()
                    it.gpsLatitude?.let { lat -> parts.add("Lat: $lat") }
                    it.gpsLongitude?.let { lon -> parts.add("Lon: $lon") }
                    if (parts.isNotEmpty()) "Source: ${parts.joinToString(", ")}" else null
                },
            )

            SubjectsSection(
                subjects = editState.subjects,
                onSubjectsChange = { newValue ->
                    editState.subjects = newValue
                    singleEditBoxId?.let { id ->
                        state.configs.updatePhotoScanConfiguration(id) { it.copy(subjects = newValue) }
                    }
                },
                metadataHistory = metadataHistory,
                onMetadataHistoryUpdate = onMetadataHistoryUpdate,
                onMetadataHistoryRemove = onMetadataHistoryRemove,
                // Face selection (single-edit only)
                onSelectFaces = if (!isMultiSelect && selectedIndex >= 0) {
                    { onSelectFaces(selectedIndex) }
                } else null,
                faceRegions = if (!isMultiSelect && singleEditConfig != null) {
                    singleEditConfig.faceRegions
                } else emptyList(),
                onRemoveFace = if (!isMultiSelect && selectedIndex >= 0) {
                    { faceIdx -> state.faceRegions.removeFaceRegion(selectedIndex, faceIdx) }
                } else null,
                onClearAllFaces = if (!isMultiSelect && selectedIndex >= 0) {
                    { state.faceRegions.clearAllFaceRegions(selectedIndex) }
                } else null,
            )


        }
    }
}