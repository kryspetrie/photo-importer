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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.awt.image.BufferedImage
import org.kryspetrie.fileimport.application.PerspectiveCorrectionService
import org.kryspetrie.fileimport.domain.model.MetadataHistory
import org.kryspetrie.fileimport.domain.model.OverrideState
import org.kryspetrie.fileimport.domain.model.RecentMetadataSet
import org.kryspetrie.fileimport.infrastructure.wizard.BoundingBoxList
import org.kryspetrie.fileimport.infrastructure.wizard.PhotoConfiguration
import org.kryspetrie.fileimport.infrastructure.wizard.PhotoScanWizardState
import org.kryspetrie.fileimport.infrastructure.wizard.SourceExifSummary
import org.kryspetrie.fileimport.ui.components.ChunkyScrollbar
import org.kryspetrie.fileimport.ui.screens.wizard.metadata.RecentValuesDropdown

/**
 * Metadata editor panel — shown in Metadata mode. Contains all the metadata editing functionality.
 */
@Composable
internal fun MetadataEditorPanel(
    state: PhotoScanWizardState,
    image: BufferedImage,
    perspectiveService: PerspectiveCorrectionService,
    boundingBoxList: BoundingBoxList,
    photoConfigurations: Map<String, PhotoConfiguration>,
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

    // Buffered values for multi-edit
    var bufferedDescription by remember { mutableStateOf("") }
    var bufferedKeywords by remember { mutableStateOf("") }
    var bufferedOriginalDate by remember { mutableStateOf("") }
    var bufferedYear by remember { mutableStateOf("") }
    var bufferedCameraModel by remember { mutableStateOf("") }
    var bufferedCameraMake by remember { mutableStateOf("") }
    var bufferedLensModel by remember { mutableStateOf("") }
    var bufferedFocalLength by remember { mutableStateOf("") }
    var bufferedAperture by remember { mutableStateOf("") }
    var bufferedShutterSpeed by remember { mutableStateOf("") }
    var bufferedIso by remember { mutableStateOf("") }
    var bufferedLocationName by remember { mutableStateOf("") }
    var bufferedCity by remember { mutableStateOf("") }
    var bufferedState by remember { mutableStateOf("") }
    var bufferedCountry by remember { mutableStateOf("") }
    var bufferedGpsLatitude by remember { mutableStateOf("") }
    var bufferedGpsLongitude by remember { mutableStateOf("") }
    var bufferedSubjects by remember { mutableStateOf("") }

    /** Apply a recent metadata set to multi-edit buffered fields (only fills non-blank values). */
    val applyRecentSetToBuffered: (RecentMetadataSet) -> Unit = remember { { set ->
        if (set.description.isNotBlank()) bufferedDescription = set.description
        if (set.keywords.isNotBlank()) bufferedKeywords = set.keywords
        if (set.originalDate.isNotBlank()) bufferedOriginalDate = set.originalDate
        if (set.year.isNotBlank()) bufferedYear = set.year
        if (set.cameraMake.isNotBlank()) bufferedCameraMake = set.cameraMake
        if (set.cameraModel.isNotBlank()) bufferedCameraModel = set.cameraModel
        if (set.lensModel.isNotBlank()) bufferedLensModel = set.lensModel
        if (set.focalLength.isNotBlank()) bufferedFocalLength = set.focalLength
        if (set.aperture.isNotBlank()) bufferedAperture = set.aperture
        if (set.shutterSpeed.isNotBlank()) bufferedShutterSpeed = set.shutterSpeed
        if (set.iso.isNotBlank()) bufferedIso = set.iso
        if (set.locationName.isNotBlank()) bufferedLocationName = set.locationName
        if (set.city.isNotBlank()) bufferedCity = set.city
        if (set.state.isNotBlank()) bufferedState = set.state
        if (set.country.isNotBlank()) bufferedCountry = set.country
        if (set.gpsLatitude.isNotBlank()) bufferedGpsLatitude = set.gpsLatitude
        if (set.gpsLongitude.isNotBlank()) bufferedGpsLongitude = set.gpsLongitude
        if (set.subjects.isNotBlank()) bufferedSubjects = set.subjects
    } }

    /** Apply a recent location set to multi-edit buffered fields. */
    val applyRecentLocationToBuffered: (RecentMetadataSet) -> Unit = remember { { set ->
        if (set.locationName.isNotBlank()) bufferedLocationName = set.locationName
        if (set.city.isNotBlank()) bufferedCity = set.city
        if (set.state.isNotBlank()) bufferedState = set.state
        if (set.country.isNotBlank()) bufferedCountry = set.country
        if (set.gpsLatitude.isNotBlank()) bufferedGpsLatitude = set.gpsLatitude
        if (set.gpsLongitude.isNotBlank()) bufferedGpsLongitude = set.gpsLongitude
    } }

    ChunkyScrollbar(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isMultiSelect) {
                // ── Multi-edit mode ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${selectedIndices.size} ${if (selectedIndices.size == 1) "photo" else "photos"} selected",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Button(
                        onClick = {
                            state.applyMetadataToSelected(
                                description = bufferedDescription,
                                keywords = bufferedKeywords,
                                originalDate = bufferedOriginalDate,
                                year = bufferedYear,
                                cameraModel = bufferedCameraModel,
                                cameraMake = bufferedCameraMake,
                                lensModel = bufferedLensModel,
                                focalLength = bufferedFocalLength,
                                aperture = bufferedAperture,
                                shutterSpeed = bufferedShutterSpeed,
                                iso = bufferedIso,
                                locationName = bufferedLocationName,
                                city = bufferedCity,
                                state = bufferedState,
                                country = bufferedCountry,
                                gpsLatitude = bufferedGpsLatitude,
                                gpsLongitude = bufferedGpsLongitude,
                                subjects = bufferedSubjects,
                            )
                            onRecordMetadataSet(
                                RecentMetadataSet(
                                    description = bufferedDescription,
                                    keywords = bufferedKeywords,
                                    originalDate = bufferedOriginalDate,
                                    year = bufferedYear,
                                    cameraMake = bufferedCameraMake,
                                    cameraModel = bufferedCameraModel,
                                    lensModel = bufferedLensModel,
                                    focalLength = bufferedFocalLength,
                                    aperture = bufferedAperture,
                                    shutterSpeed = bufferedShutterSpeed,
                                    iso = bufferedIso,
                                    locationName = bufferedLocationName,
                                    city = bufferedCity,
                                    state = bufferedState,
                                    country = bufferedCountry,
                                    gpsLatitude = bufferedGpsLatitude,
                                    gpsLongitude = bufferedGpsLongitude,
                                    subjects = bufferedSubjects,
                                )
                            )
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
                            applyRecentSetToBuffered(set)
                            onRecordMetadataSet(set)
                        },
                    )
                }

                QuickEditMetadataFields(
                    description = bufferedDescription,
                    onDescriptionChange = { bufferedDescription = it },
                    keywords = bufferedKeywords,
                    onKeywordsChange = { bufferedKeywords = it },
                    originalDate = bufferedOriginalDate,
                    onOriginalDateChange = { bufferedOriginalDate = it },
                    year = bufferedYear,
                    onYearChange = { bufferedYear = it },
                    metadataHistory = metadataHistory,
                    onMetadataHistoryUpdate = onMetadataHistoryUpdate,
                    onMetadataHistoryRemove = onMetadataHistoryRemove,
                    sourceExif = sourceExif,
                )
                CameraSection(
                    showExpanded = showCameraSection,
                    onToggle = onToggleCameraSection,
                    cameraMake = bufferedCameraMake,
                    onCameraMakeChange = { bufferedCameraMake = it },
                    cameraModel = bufferedCameraModel,
                    onCameraModelChange = { bufferedCameraModel = it },
                    lensModel = bufferedLensModel,
                    onLensModelChange = { bufferedLensModel = it },
                    focalLength = bufferedFocalLength,
                    onFocalLengthChange = { bufferedFocalLength = it },
                    aperture = bufferedAperture,
                    onApertureChange = { bufferedAperture = it },
                    shutterSpeed = bufferedShutterSpeed,
                    onShutterSpeedChange = { bufferedShutterSpeed = it },
                    iso = bufferedIso,
                    onIsoChange = { bufferedIso = it },
                    metadataHistory = metadataHistory,
                    onMetadataHistoryUpdate = onMetadataHistoryUpdate,
                )
                LocationSection(
                    showExpanded = showLocationSection,
                    onToggle = onToggleLocationSection,
                    locationName = bufferedLocationName,
                    onLocationNameChange = { bufferedLocationName = it },
                    city = bufferedCity,
                    onCityChange = { bufferedCity = it },
                    stateVal = bufferedState,
                    onStateChange = { bufferedState = it },
                    country = bufferedCountry,
                    onCountryChange = { bufferedCountry = it },
                    gpsLatitude = bufferedGpsLatitude,
                    onGpsLatitudeChange = { bufferedGpsLatitude = it },
                    gpsLongitude = bufferedGpsLongitude,
                    onGpsLongitudeChange = { bufferedGpsLongitude = it },
                    metadataHistory = metadataHistory,
                    onMetadataHistoryUpdate = onMetadataHistoryUpdate,
                    onApplyRecentLocation = applyRecentLocationToBuffered,
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
                    subjects = bufferedSubjects,
                    onSubjectsChange = { bufferedSubjects = it },
                    metadataHistory = metadataHistory,
                    onMetadataHistoryUpdate = onMetadataHistoryUpdate,
                    onMetadataHistoryRemove = onMetadataHistoryRemove,
                )
            } else {
                // ── Single-select: immediate-edit mode ──
                val selectedIndex = selectedIndices.first()
                val box = boundingBoxList.boxes[selectedIndex]
                val config = photoConfigurations[box.id] ?: PhotoConfiguration()

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
                            state.updatePhotoConfiguration(box.id) { set.mergeInto(it) }
                            onRecordMetadataSet(set)
                        },
                    )
                }

                // ── Metadata fields ──
                QuickEditMetadataFields(
                    description = config.description,
                    onDescriptionChange = { newValue ->
                        state.updatePhotoConfiguration(box.id) { it.copy(description = newValue) }
                    },
                    keywords = config.keywords,
                    onKeywordsChange = { newValue ->
                        state.updatePhotoConfiguration(box.id) { it.copy(keywords = newValue) }
                    },
                    originalDate = config.originalDate,
                    onOriginalDateChange = { newValue ->
                        state.updatePhotoConfiguration(box.id) { it.copy(originalDate = newValue) }
                    },
                    year = config.year,
                    onYearChange = { newValue ->
                        state.updatePhotoConfiguration(box.id) {
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
                        state.updatePhotoConfiguration(box.id) {
                            it.copy(
                                overrideDescription =
                                    if (included) OverrideState.KEEP_SOURCE
                                    else OverrideState.NULL_OUT
                            )
                        }
                    },
                    overrideKeywords = config.overrideKeywords != OverrideState.NULL_OUT,
                    onOverrideKeywordsChange = { included ->
                        state.updatePhotoConfiguration(box.id) {
                            it.copy(
                                overrideKeywords =
                                    if (included) OverrideState.KEEP_SOURCE
                                    else OverrideState.NULL_OUT
                            )
                        }
                    },
                    overrideOriginalDate = config.overrideOriginalDate != OverrideState.NULL_OUT,
                    onOverrideOriginalDateChange = { included ->
                        state.updatePhotoConfiguration(box.id) {
                            it.copy(
                                overrideOriginalDate =
                                    if (included) OverrideState.KEEP_SOURCE
                                    else OverrideState.NULL_OUT
                            )
                        }
                    },
                    overrideYear = config.overrideYear != OverrideState.NULL_OUT,
                    onOverrideYearChange = { included ->
                        state.updatePhotoConfiguration(box.id) {
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
                        state.updatePhotoConfiguration(box.id) { it.copy(cameraMake = newValue) }
                    },
                    cameraModel = config.cameraModel,
                    onCameraModelChange = { newValue ->
                        state.updatePhotoConfiguration(box.id) { it.copy(cameraModel = newValue) }
                    },
                    lensModel = config.lensModel,
                    onLensModelChange = { newValue ->
                        state.updatePhotoConfiguration(box.id) { it.copy(lensModel = newValue) }
                    },
                    focalLength = config.focalLength,
                    onFocalLengthChange = { newValue ->
                        state.updatePhotoConfiguration(box.id) { it.copy(focalLength = newValue) }
                    },
                    aperture = config.aperture,
                    onApertureChange = { newValue ->
                        state.updatePhotoConfiguration(box.id) { it.copy(aperture = newValue) }
                    },
                    shutterSpeed = config.shutterSpeed,
                    onShutterSpeedChange = { newValue ->
                        state.updatePhotoConfiguration(box.id) { it.copy(shutterSpeed = newValue) }
                    },
                    iso = config.iso,
                    onIsoChange = { newValue ->
                        state.updatePhotoConfiguration(box.id) { it.copy(iso = newValue) }
                    },
                    metadataHistory = metadataHistory,
                    onMetadataHistoryUpdate = onMetadataHistoryUpdate,
                    overrideCameraMake = config.overrideCameraMake != OverrideState.NULL_OUT,
                    onOverrideCameraMakeChange = { included ->
                        state.updatePhotoConfiguration(box.id) {
                            it.copy(
                                overrideCameraMake =
                                    if (included) OverrideState.KEEP_SOURCE
                                    else OverrideState.NULL_OUT
                            )
                        }
                    },
                    overrideCameraModel = config.overrideCameraModel != OverrideState.NULL_OUT,
                    onOverrideCameraModelChange = { included ->
                        state.updatePhotoConfiguration(box.id) {
                            it.copy(
                                overrideCameraModel =
                                    if (included) OverrideState.KEEP_SOURCE
                                    else OverrideState.NULL_OUT
                            )
                        }
                    },
                    overrideLensModel = config.overrideLensModel != OverrideState.NULL_OUT,
                    onOverrideLensModelChange = { included ->
                        state.updatePhotoConfiguration(box.id) {
                            it.copy(
                                overrideLensModel =
                                    if (included) OverrideState.KEEP_SOURCE
                                    else OverrideState.NULL_OUT
                            )
                        }
                    },
                    overrideFocalLength = config.overrideFocalLength != OverrideState.NULL_OUT,
                    onOverrideFocalLengthChange = { included ->
                        state.updatePhotoConfiguration(box.id) {
                            it.copy(
                                overrideFocalLength =
                                    if (included) OverrideState.KEEP_SOURCE
                                    else OverrideState.NULL_OUT
                            )
                        }
                    },
                    overrideAperture = config.overrideAperture != OverrideState.NULL_OUT,
                    onOverrideApertureChange = { included ->
                        state.updatePhotoConfiguration(box.id) {
                            it.copy(
                                overrideAperture =
                                    if (included) OverrideState.KEEP_SOURCE
                                    else OverrideState.NULL_OUT
                            )
                        }
                    },
                    overrideShutterSpeed = config.overrideShutterSpeed != OverrideState.NULL_OUT,
                    onOverrideShutterSpeedChange = { included ->
                        state.updatePhotoConfiguration(box.id) {
                            it.copy(
                                overrideShutterSpeed =
                                    if (included) OverrideState.KEEP_SOURCE
                                    else OverrideState.NULL_OUT
                            )
                        }
                    },
                    overrideIso = config.overrideIso != OverrideState.NULL_OUT,
                    onOverrideIsoChange = { included ->
                        state.updatePhotoConfiguration(box.id) {
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
                        state.updatePhotoConfiguration(box.id) { it.copy(locationName = newValue) }
                    },
                    city = config.city,
                    onCityChange = { newValue ->
                        state.updatePhotoConfiguration(box.id) { it.copy(city = newValue) }
                    },
                    stateVal = config.state,
                    onStateChange = { newValue ->
                        state.updatePhotoConfiguration(box.id) { it.copy(state = newValue) }
                    },
                    country = config.country,
                    onCountryChange = { newValue ->
                        state.updatePhotoConfiguration(box.id) { it.copy(country = newValue) }
                    },
                    gpsLatitude = config.gpsLatitude,
                    onGpsLatitudeChange = { newValue ->
                        state.updatePhotoConfiguration(box.id) { it.copy(gpsLatitude = newValue) }
                    },
                    gpsLongitude = config.gpsLongitude,
                    onGpsLongitudeChange = { newValue ->
                        state.updatePhotoConfiguration(box.id) { it.copy(gpsLongitude = newValue) }
                    },
                    metadataHistory = metadataHistory,
                    onMetadataHistoryUpdate = onMetadataHistoryUpdate,
                    onApplyRecentLocation = { set ->
                        state.updatePhotoConfiguration(box.id) { set.mergeLocationInto(it) }
                    },
                    onPickLocation = { onPickLocation(selectedIndex) },
                    overrideGps = config.overrideGps != OverrideState.NULL_OUT,
                    onOverrideGpsChange = { included ->
                        state.updatePhotoConfiguration(box.id) {
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
                        state.updatePhotoConfiguration(box.id) { it.copy(subjects = newValue) }
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
