package org.kryspetrie.fileimport.ui.screens.wizard.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
 * In single-edit mode, changes are applied immediately via direct config updates.
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
    showCameraSection: Boolean,
    onToggleCameraSection: () -> Unit,
    showLocationSection: Boolean,
    onToggleLocationSection: () -> Unit,
    showSubjectsSection: Boolean,
    onToggleSubjectsSection: () -> Unit,
    sourceExif: SourceExifSummary?,
    onSelectFaces: (Int) -> Unit,
    onPickLocation: (Int) -> Unit,
    onAddBackImage: () -> Unit,
    onRemoveBackImage: () -> Unit,
    onRecordMetadataSet: (RecentMetadataSet) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isMultiSelect = selectedIndices.size > 1 || isMultiEditMode

    // Single MetadataEditState for multi-edit buffering (replaces 18 separate var buffered*)
    val editState = remember { MetadataEditState() }

    ChunkyScrollbar(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isMultiSelect) {
                // ── Multi-edit mode ──
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

                // ── Recent Values (multi-edit) ──
                if (metadataHistory.recentSets.isNotEmpty()) {
                    RecentValuesDropdown(
                        recentSets = metadataHistory.recentSets,
                        onApplySet = { set ->
                            editState.loadFromSet(set)
                            onRecordMetadataSet(set)
                        },
                    )
                }

                QuickEditMetadataFields(
                    description = editState.description,
                    onDescriptionChange = { editState.description = it },
                    keywords = editState.keywords,
                    onKeywordsChange = { editState.keywords = it },
                    originalDate = editState.originalDate,
                    onOriginalDateChange = { editState.originalDate = it },
                    year = editState.year,
                    onYearChange = { editState.year = it },
                    metadataHistory = metadataHistory,
                    onMetadataHistoryUpdate = onMetadataHistoryUpdate,
                    onMetadataHistoryRemove = onMetadataHistoryRemove,
                    sourceExif = sourceExif,
                )
                CameraSection(
                    showExpanded = showCameraSection,
                    onToggle = onToggleCameraSection,
                    cameraMake = editState.cameraMake,
                    onCameraMakeChange = { editState.cameraMake = it },
                    cameraModel = editState.cameraModel,
                    onCameraModelChange = { editState.cameraModel = it },
                    lensModel = editState.lensModel,
                    onLensModelChange = { editState.lensModel = it },
                    focalLength = editState.focalLength,
                    onFocalLengthChange = { editState.focalLength = it },
                    aperture = editState.aperture,
                    onApertureChange = { editState.aperture = it },
                    shutterSpeed = editState.shutterSpeed,
                    onShutterSpeedChange = { editState.shutterSpeed = it },
                    iso = editState.iso,
                    onIsoChange = { editState.iso = it },
                    metadataHistory = metadataHistory,
                    onMetadataHistoryUpdate = onMetadataHistoryUpdate,
                )
                LocationSection(
                    showExpanded = showLocationSection,
                    onToggle = onToggleLocationSection,
                    locationName = editState.locationName,
                    onLocationNameChange = { editState.locationName = it },
                    city = editState.city,
                    onCityChange = { editState.city = it },
                    stateVal = editState.state,
                    onStateChange = { editState.state = it },
                    country = editState.country,
                    onCountryChange = { editState.country = it },
                    gpsLatitude = editState.gpsLatitude,
                    onGpsLatitudeChange = { editState.gpsLatitude = it },
                    gpsLongitude = editState.gpsLongitude,
                    onGpsLongitudeChange = { editState.gpsLongitude = it },
                    metadataHistory = metadataHistory,
                    onMetadataHistoryUpdate = onMetadataHistoryUpdate,
                    onApplyRecentLocation = { set -> editState.loadFromSet(set) },
                    sourceGpsHint =
                        sourceExif?.let {
                            val parts = mutableListOf<String>()
                            it.gpsLatitude?.let { lat -> parts.add("Lat: $lat") }
                            it.gpsLongitude?.let { lon -> parts.add("Lon: $lon") }
                            if (parts.isNotEmpty()) "Source: ${parts.joinToString(", ")}" else null
                        },
                )
                SubjectsSection(
                    showExpanded = showSubjectsSection,
                    onToggle = onToggleSubjectsSection,
                    subjects = editState.subjects,
                    onSubjectsChange = { editState.subjects = it },
                    metadataHistory = metadataHistory,
                    onMetadataHistoryUpdate = onMetadataHistoryUpdate,
                    onMetadataHistoryRemove = onMetadataHistoryRemove,
                )
            } else {
                // ── Single-select: immediate-edit mode ──
                val selectedIndex = selectedIndices.first()
                val box = boundingBoxList.boxes[selectedIndex]
                val config = photoConfigurations[box.id] ?: PhotoScanConfiguration()

                Text(
                    "Photo ${selectedIndex + 1}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )

                // ── Recent Values (single-edit) ──
                if (metadataHistory.recentSets.isNotEmpty()) {
                    RecentValuesDropdown(
                        recentSets = metadataHistory.recentSets,
                        onApplySet = { set ->
                            state.configs.updatePhotoScanConfiguration(box.id) { set.mergeInto(it) }
                            onRecordMetadataSet(set)
                        },
                    )
                }

                // ── Metadata fields ──
                QuickEditMetadataFields(
                    description = config.description,
                    onDescriptionChange = { newValue ->
                        state.configs.updatePhotoScanConfiguration(box.id) { it.copy(description = newValue) }
                    },
                    keywords = config.keywords,
                    onKeywordsChange = { newValue ->
                        state.configs.updatePhotoScanConfiguration(box.id) { it.copy(keywords = newValue) }
                    },
                    originalDate = config.originalDate,
                    onOriginalDateChange = { newValue ->
                        state.configs.updatePhotoScanConfiguration(box.id) { it.copy(originalDate = newValue) }
                    },
                    year = config.year,
                    onYearChange = { newValue ->
                        state.configs.updatePhotoScanConfiguration(box.id) {
                            it.copy(year = newValue.filter { c -> c.isDigit() }.take(4))
                        }
                    },
                    metadataHistory = metadataHistory,
                    onMetadataHistoryUpdate = onMetadataHistoryUpdate,
                    onMetadataHistoryRemove = onMetadataHistoryRemove,
                    onCommitKeyword = { keyword -> onMetadataHistoryUpdate("keywords", keyword) },
                    boxId = box.id,
                    state = state,
                    overrideDescription = config.overrideDescription != OverrideState.NULL_OUT,
                    onOverrideDescriptionChange = { included ->
                        state.configs.updatePhotoScanConfiguration(box.id) {
                            it.copy(
                                overrideDescription =
                                    if (included) OverrideState.KEEP_SOURCE
                                    else OverrideState.NULL_OUT
                            )
                        }
                    },
                    overrideKeywords = config.overrideKeywords != OverrideState.NULL_OUT,
                    onOverrideKeywordsChange = { included ->
                        state.configs.updatePhotoScanConfiguration(box.id) {
                            it.copy(
                                overrideKeywords =
                                    if (included) OverrideState.KEEP_SOURCE
                                    else OverrideState.NULL_OUT
                            )
                        }
                    },
                    overrideOriginalDate = config.overrideOriginalDate != OverrideState.NULL_OUT,
                    onOverrideOriginalDateChange = { included ->
                        state.configs.updatePhotoScanConfiguration(box.id) {
                            it.copy(
                                overrideOriginalDate =
                                    if (included) OverrideState.KEEP_SOURCE
                                    else OverrideState.NULL_OUT
                            )
                        }
                    },
                    overrideYear = config.overrideYear != OverrideState.NULL_OUT,
                    onOverrideYearChange = { included ->
                        state.configs.updatePhotoScanConfiguration(box.id) {
                            it.copy(
                                overrideYear =
                                    if (included) OverrideState.KEEP_SOURCE
                                    else OverrideState.NULL_OUT
                            )
                        }
                    },
                    sourceExif = sourceExif,
                )

                // ── Camera Settings ──
                CameraSection(
                    showExpanded = showCameraSection,
                    onToggle = onToggleCameraSection,
                    cameraMake = config.cameraMake,
                    onCameraMakeChange = { newValue ->
                        state.configs.updatePhotoScanConfiguration(box.id) { it.copy(cameraMake = newValue) }
                    },
                    cameraModel = config.cameraModel,
                    onCameraModelChange = { newValue ->
                        state.configs.updatePhotoScanConfiguration(box.id) { it.copy(cameraModel = newValue) }
                    },
                    lensModel = config.lensModel,
                    onLensModelChange = { newValue ->
                        state.configs.updatePhotoScanConfiguration(box.id) { it.copy(lensModel = newValue) }
                    },
                    focalLength = config.focalLength,
                    onFocalLengthChange = { newValue ->
                        state.configs.updatePhotoScanConfiguration(box.id) { it.copy(focalLength = newValue) }
                    },
                    aperture = config.aperture,
                    onApertureChange = { newValue ->
                        state.configs.updatePhotoScanConfiguration(box.id) { it.copy(aperture = newValue) }
                    },
                    shutterSpeed = config.shutterSpeed,
                    onShutterSpeedChange = { newValue ->
                        state.configs.updatePhotoScanConfiguration(box.id) { it.copy(shutterSpeed = newValue) }
                    },
                    iso = config.iso,
                    onIsoChange = { newValue ->
                        state.configs.updatePhotoScanConfiguration(box.id) { it.copy(iso = newValue) }
                    },
                    metadataHistory = metadataHistory,
                    onMetadataHistoryUpdate = onMetadataHistoryUpdate,
                    overrideCameraMake = config.overrideCameraMake != OverrideState.NULL_OUT,
                    onOverrideCameraMakeChange = { included ->
                        state.configs.updatePhotoScanConfiguration(box.id) {
                            it.copy(
                                overrideCameraMake =
                                    if (included) OverrideState.KEEP_SOURCE
                                    else OverrideState.NULL_OUT
                            )
                        }
                    },
                    overrideCameraModel = config.overrideCameraModel != OverrideState.NULL_OUT,
                    onOverrideCameraModelChange = { included ->
                        state.configs.updatePhotoScanConfiguration(box.id) {
                            it.copy(
                                overrideCameraModel =
                                    if (included) OverrideState.KEEP_SOURCE
                                    else OverrideState.NULL_OUT
                            )
                        }
                    },
                    overrideLensModel = config.overrideLensModel != OverrideState.NULL_OUT,
                    onOverrideLensModelChange = { included ->
                        state.configs.updatePhotoScanConfiguration(box.id) {
                            it.copy(
                                overrideLensModel =
                                    if (included) OverrideState.KEEP_SOURCE
                                    else OverrideState.NULL_OUT
                            )
                        }
                    },
                    overrideFocalLength = config.overrideFocalLength != OverrideState.NULL_OUT,
                    onOverrideFocalLengthChange = { included ->
                        state.configs.updatePhotoScanConfiguration(box.id) {
                            it.copy(
                                overrideFocalLength =
                                    if (included) OverrideState.KEEP_SOURCE
                                    else OverrideState.NULL_OUT
                            )
                        }
                    },
                    overrideAperture = config.overrideAperture != OverrideState.NULL_OUT,
                    onOverrideApertureChange = { included ->
                        state.configs.updatePhotoScanConfiguration(box.id) {
                            it.copy(
                                overrideAperture =
                                    if (included) OverrideState.KEEP_SOURCE
                                    else OverrideState.NULL_OUT
                            )
                        }
                    },
                    overrideShutterSpeed = config.overrideShutterSpeed != OverrideState.NULL_OUT,
                    onOverrideShutterSpeedChange = { included ->
                        state.configs.updatePhotoScanConfiguration(box.id) {
                            it.copy(
                                overrideShutterSpeed =
                                    if (included) OverrideState.KEEP_SOURCE
                                    else OverrideState.NULL_OUT
                            )
                        }
                    },
                    overrideIso = config.overrideIso != OverrideState.NULL_OUT,
                    onOverrideIsoChange = { included ->
                        state.configs.updatePhotoScanConfiguration(box.id) {
                            it.copy(
                                overrideIso =
                                    if (included) OverrideState.KEEP_SOURCE
                                    else OverrideState.NULL_OUT
                            )
                        }
                    },
                    sourceExif = sourceExif,
                )

                // ── Location ──
                LocationSection(
                    showExpanded = showLocationSection,
                    onToggle = onToggleLocationSection,
                    locationName = config.locationName,
                    onLocationNameChange = { newValue ->
                        state.configs.updatePhotoScanConfiguration(box.id) { it.copy(locationName = newValue) }
                    },
                    city = config.city,
                    onCityChange = { newValue ->
                        state.configs.updatePhotoScanConfiguration(box.id) { it.copy(city = newValue) }
                    },
                    stateVal = config.state,
                    onStateChange = { newValue ->
                        state.configs.updatePhotoScanConfiguration(box.id) { it.copy(state = newValue) }
                    },
                    country = config.country,
                    onCountryChange = { newValue ->
                        state.configs.updatePhotoScanConfiguration(box.id) { it.copy(country = newValue) }
                    },
                    gpsLatitude = config.gpsLatitude,
                    onGpsLatitudeChange = { newValue ->
                        state.configs.updatePhotoScanConfiguration(box.id) { it.copy(gpsLatitude = newValue) }
                    },
                    gpsLongitude = config.gpsLongitude,
                    onGpsLongitudeChange = { newValue ->
                        state.configs.updatePhotoScanConfiguration(box.id) { it.copy(gpsLongitude = newValue) }
                    },
                    metadataHistory = metadataHistory,
                    onMetadataHistoryUpdate = onMetadataHistoryUpdate,
                    onApplyRecentLocation = { set ->
                        state.configs.updatePhotoScanConfiguration(box.id) { set.mergeLocationInto(it) }
                    },
                    onPickLocation = { onPickLocation(selectedIndex) },
                    overrideGps = config.overrideGps != OverrideState.NULL_OUT,
                    onOverrideGpsChange = { included ->
                        state.configs.updatePhotoScanConfiguration(box.id) {
                            it.copy(
                                overrideGps =
                                    if (included) OverrideState.KEEP_SOURCE
                                    else OverrideState.NULL_OUT
                            )
                        }
                    },
                    sourceGpsHint =
                        sourceExif?.let {
                            val parts = mutableListOf<String>()
                            it.gpsLatitude?.let { lat -> parts.add("Lat: $lat") }
                            it.gpsLongitude?.let { lon -> parts.add("Lon: $lon") }
                            if (parts.isNotEmpty()) "Source: ${parts.joinToString(", ")}" else null
                        },
                )

                // ── Subjects & Faces ──
                SubjectsSection(
                    showExpanded = showSubjectsSection,
                    onToggle = onToggleSubjectsSection,
                    subjects = config.subjects,
                    onSubjectsChange = { newValue ->
                        state.configs.updatePhotoScanConfiguration(box.id) { it.copy(subjects = newValue) }
                    },
                    metadataHistory = metadataHistory,
                    onMetadataHistoryUpdate = onMetadataHistoryUpdate,
                    onMetadataHistoryRemove = onMetadataHistoryRemove,
                    onSelectFaces = { onSelectFaces(selectedIndex) },
                    faceRegions = config.faceRegions,
                    onRemoveFace = { faceIdx -> state.faceRegions.removeFaceRegion(selectedIndex, faceIdx) },
                    onClearAllFaces = { state.faceRegions.clearAllFaceRegions(selectedIndex) },
                )

                // ── Back-of-photo Image ──
                if (config.hasBackImage()) {
                    Surface(
                        tonalElevation = 1.dp,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Image,
                                "Back image assigned",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                "Back: ${if (config.backImageMode == "combine") "Combined" else "Appended"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.weight(1f))
                            OutlinedButton(
                                onClick = onAddBackImage,
                                modifier = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                            ) {
                                Text("Change", style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(
                                onClick = onRemoveBackImage,
                                modifier = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                            ) {
                                Text(
                                    "Remove",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                } else {
                    OutlinedButton(onClick = onAddBackImage, modifier = Modifier.fillMaxWidth()) {
                        Icon(
                            Icons.Default.Image,
                            "Select back of photo",
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Add Back Image", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}