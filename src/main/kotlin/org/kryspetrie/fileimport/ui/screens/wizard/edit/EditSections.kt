package org.kryspetrie.fileimport.ui.screens.wizard.edit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.DatePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.kryspetrie.fileimport.domain.model.FaceRegion
import org.kryspetrie.fileimport.domain.model.MetadataHistory
import org.kryspetrie.fileimport.domain.model.RecentMetadataSet
import org.kryspetrie.fileimport.domain.model.RegionType
import org.kryspetrie.fileimport.ui.wizard.state.FaceSize
import org.kryspetrie.fileimport.ui.wizard.state.PhotoScanWizardState
import org.kryspetrie.fileimport.ui.wizard.state.SourceExifSummary
import org.kryspetrie.fileimport.ui.screens.wizard.regionTypeIcon
import org.kryspetrie.fileimport.ui.screens.wizard.metadata.MetadataField
import org.kryspetrie.fileimport.ui.screens.wizard.metadata.OverrideCheckbox
import org.kryspetrie.fileimport.ui.screens.wizard.metadata.RecentLocationDropdown

/** Rotation controls section. */
@Composable
internal fun RotationSection(
    rotationDegrees: Int,
    onRotateCW: () -> Unit,
    onRotateCCW: () -> Unit,
    onRotate180: () -> Unit,
) {
    Surface(
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Rotation", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRotateCCW, modifier = Modifier.size(28.dp)) {
                Icon(Icons.AutoMirrored.Filled.RotateLeft, "CCW", Modifier.size(18.dp))
            }
            IconButton(onClick = onRotate180, modifier = Modifier.size(28.dp)) {
                Text("180°", style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = onRotateCW, modifier = Modifier.size(28.dp)) {
                Icon(Icons.AutoMirrored.Filled.RotateRight, "CW", Modifier.size(18.dp))
            }
            if (rotationDegrees != 0) {
                Text(
                    "${rotationDegrees}°",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * Core metadata fields: description, keywords (with chip UI + X removal), original date (with date
 * picker), year.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun QuickEditMetadataFields(
    description: String,
    onDescriptionChange: (String) -> Unit,
    keywords: String,
    onKeywordsChange: (String) -> Unit,
    originalDate: String,
    onOriginalDateChange: (String) -> Unit,
    year: String,
    onYearChange: (String) -> Unit,
    metadataHistory: MetadataHistory,
    onMetadataHistoryUpdate: (String, String) -> Unit,
    onMetadataHistoryRemove: (String, String) -> Unit,
    onCommitKeyword: ((String) -> Unit)? = null,
    boxId: String? = null,
    state: PhotoScanWizardState? = null,
    overrideDescription: Boolean? = null,
    onOverrideDescriptionChange: ((Boolean) -> Unit)? = null,
    overrideKeywords: Boolean? = null,
    onOverrideKeywordsChange: ((Boolean) -> Unit)? = null,
    overrideOriginalDate: Boolean? = null,
    onOverrideOriginalDateChange: ((Boolean) -> Unit)? = null,
    overrideYear: Boolean? = null,
    onOverrideYearChange: ((Boolean) -> Unit)? = null,
    sourceExif: SourceExifSummary? = null,
) {
    val focusManager = LocalFocusManager.current
    val keywordList =
        remember(keywords) { keywords.split(",").map { it.trim() }.filter { it.isNotBlank() } }
    var keywordInput by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }

    MetadataField(
        label = "Description",
        placeholder = "Photo description...",
        value = description,
        onValueChange = onDescriptionChange,
        suggestions = metadataHistory.description,
        onCommit = { onMetadataHistoryUpdate("description", description) },
        fieldIncluded = overrideDescription,
        onFieldIncludedChange = onOverrideDescriptionChange,
        sourceHint = sourceExif?.description,
    )

    // Keywords — chip/tag UI with X removal + suggestion dropdown
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Keywords", style = MaterialTheme.typography.labelMedium)
        if (keywordList.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                keywordList.forEach { keyword ->
                    RemovableChip(
                        text = keyword,
                        onRemove = {
                            val updated = keywordList.filter { it != keyword }
                            onKeywordsChange(updated.joinToString(", "))
                            onMetadataHistoryRemove("keywords", keyword)
                        },
                    )
                }
            }
        }
        var suggestionsExpanded by remember { mutableStateOf(false) }
        val availableSuggestions =
            remember(metadataHistory.keywords, keywordList) {
                metadataHistory.keywords.filter { it !in keywordList }
            }
        val filteredSuggestions =
            remember(availableSuggestions, keywordInput) {
                if (keywordInput.isBlank()) availableSuggestions
                else availableSuggestions.filter { it.contains(keywordInput, ignoreCase = true) }
            }
        if (availableSuggestions.isNotEmpty() || true) {
            ExposedDropdownMenuBox(
                expanded = suggestionsExpanded && filteredSuggestions.isNotEmpty(),
                onExpandedChange = { suggestionsExpanded = it },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = keywordInput,
                        onValueChange = {
                            keywordInput = it
                            suggestionsExpanded = true
                        },
                        placeholder = {
                            Text("Add keyword...", style = MaterialTheme.typography.labelSmall)
                        },
                        modifier =
                            Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).weight(1f),
                        singleLine = true,
                        keyboardActions =
                            KeyboardActions(
                                onDone = {
                                    if (keywordInput.isNotBlank()) {
                                        val updated =
                                            if (keywords.isBlank()) keywordInput
                                            else "$keywords, $keywordInput"
                                        onKeywordsChange(updated.trim())
                                        onMetadataHistoryUpdate("keywords", keywordInput.trim())
                                        onCommitKeyword?.invoke(keywordInput.trim())
                                        keywordInput = ""
                                    }
                                    focusManager.moveFocus(FocusDirection.Down)
                                }
                            ),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        trailingIcon = {
                            if (keywordInput.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        val updated =
                                            if (keywords.isBlank()) keywordInput
                                            else "$keywords, $keywordInput"
                                        onKeywordsChange(updated.trim())
                                        onMetadataHistoryUpdate("keywords", keywordInput.trim())
                                        onCommitKeyword?.invoke(keywordInput.trim())
                                        keywordInput = ""
                                    },
                                    modifier = Modifier.size(20.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        "Add keyword",
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        },
                    )
                }
                if (filteredSuggestions.isNotEmpty()) {
                    ExposedDropdownMenu(
                        expanded = suggestionsExpanded && filteredSuggestions.isNotEmpty(),
                        onDismissRequest = { suggestionsExpanded = false },
                    ) {
                        filteredSuggestions.take(10).forEach { suggestion ->
                            DropdownMenuItem(
                                text = {
                                    Text(suggestion, style = MaterialTheme.typography.bodySmall)
                                },
                                onClick = {
                                    val updated =
                                        if (keywords.isBlank()) suggestion
                                        else "$keywords, $suggestion"
                                    onKeywordsChange(updated)
                                    onMetadataHistoryUpdate("keywords", suggestion)
                                    keywordInput = ""
                                    suggestionsExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    // Original Date with date picker
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MetadataField(
                label = "Original Date",
                placeholder = "YYYY-MM-DD",
                value = originalDate,
                onValueChange = onOriginalDateChange,
                suggestions = metadataHistory.originalDate,
                onCommit = { onMetadataHistoryUpdate("originalDate", originalDate) },
                modifier = Modifier.weight(1f),
                fieldIncluded = overrideOriginalDate,
                onFieldIncludedChange = onOverrideOriginalDateChange,
                sourceHint = sourceExif?.dateOriginal,
            )
            IconButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.size(40.dp).padding(top = 24.dp),
            ) {
                Icon(Icons.Default.DateRange, "Pick date", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            onDateSelected = { selectedDate ->
                onOriginalDateChange(selectedDate)
                onMetadataHistoryUpdate("originalDate", selectedDate)
                showDatePicker = false
            },
        )
    }

    MetadataField(
        label = "Year",
        placeholder = "1995",
        value = year,
        onValueChange = { onYearChange(it.filter { c -> c.isDigit() }.take(4)) },
        keyboardType = KeyboardType.Number,
        suggestions = metadataHistory.year,
        onCommit = { onMetadataHistoryUpdate("year", year) },
        fieldIncluded = overrideYear,
        onFieldIncludedChange = onOverrideYearChange,
        sourceHint = sourceExif?.dateOriginal?.take(4),
    )
}

/** Collapsible camera settings section. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CameraSection(
    showExpanded: Boolean,
    onToggle: () -> Unit,
    cameraMake: String,
    onCameraMakeChange: (String) -> Unit,
    cameraModel: String,
    onCameraModelChange: (String) -> Unit,
    lensModel: String,
    onLensModelChange: (String) -> Unit,
    focalLength: String,
    onFocalLengthChange: (String) -> Unit,
    aperture: String,
    onApertureChange: (String) -> Unit,
    shutterSpeed: String,
    onShutterSpeedChange: (String) -> Unit,
    iso: String,
    onIsoChange: (String) -> Unit,
    metadataHistory: MetadataHistory,
    onMetadataHistoryUpdate: (String, String) -> Unit,
    overrideCameraMake: Boolean? = null,
    onOverrideCameraMakeChange: ((Boolean) -> Unit)? = null,
    overrideCameraModel: Boolean? = null,
    onOverrideCameraModelChange: ((Boolean) -> Unit)? = null,
    overrideLensModel: Boolean? = null,
    onOverrideLensModelChange: ((Boolean) -> Unit)? = null,
    overrideFocalLength: Boolean? = null,
    onOverrideFocalLengthChange: ((Boolean) -> Unit)? = null,
    overrideAperture: Boolean? = null,
    onOverrideApertureChange: ((Boolean) -> Unit)? = null,
    overrideShutterSpeed: Boolean? = null,
    onOverrideShutterSpeedChange: ((Boolean) -> Unit)? = null,
    overrideIso: Boolean? = null,
    onOverrideIsoChange: ((Boolean) -> Unit)? = null,
    sourceExif: SourceExifSummary? = null,
) {
    Column {
        OutlinedButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
            Icon(
                if (showExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (showExpanded) "Hide" else "Show",
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text("Camera Settings", style = MaterialTheme.typography.labelSmall)
        }
        if (showExpanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MetadataField(
                        label = "Camera Make",
                        placeholder = "Canon",
                        value = cameraMake,
                        onValueChange = onCameraMakeChange,
                        modifier = Modifier.weight(1f),
                        suggestions = metadataHistory.cameraMake,
                        onCommit = { onMetadataHistoryUpdate("cameraMake", cameraMake) },
                        fieldIncluded = overrideCameraMake,
                        onFieldIncludedChange = onOverrideCameraMakeChange,
                        sourceHint = sourceExif?.cameraMake,
                    )
                    MetadataField(
                        label = "Camera Model",
                        placeholder = "EOS 5D",
                        value = cameraModel,
                        onValueChange = onCameraModelChange,
                        modifier = Modifier.weight(1f),
                        suggestions = metadataHistory.cameraModel,
                        onCommit = { onMetadataHistoryUpdate("cameraModel", cameraModel) },
                        fieldIncluded = overrideCameraModel,
                        onFieldIncludedChange = onOverrideCameraModelChange,
                        sourceHint = sourceExif?.cameraModel,
                    )
                }
                MetadataField(
                    label = "Lens Model",
                    placeholder = "24-70mm f/2.8L",
                    value = lensModel,
                    onValueChange = onLensModelChange,
                    suggestions = metadataHistory.lensModel,
                    onCommit = { onMetadataHistoryUpdate("lensModel", lensModel) },
                    fieldIncluded = overrideLensModel,
                    onFieldIncludedChange = onOverrideLensModelChange,
                    sourceHint = sourceExif?.lensModel,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MetadataField(
                        label = "Focal Length",
                        placeholder = "50mm",
                        value = focalLength,
                        onValueChange = onFocalLengthChange,
                        modifier = Modifier.weight(1f),
                        suggestions = metadataHistory.focalLength,
                        onCommit = { onMetadataHistoryUpdate("focalLength", focalLength) },
                        fieldIncluded = overrideFocalLength,
                        onFieldIncludedChange = onOverrideFocalLengthChange,
                        sourceHint = sourceExif?.focalLength,
                    )
                    MetadataField(
                        label = "Aperture",
                        placeholder = "f/2.8",
                        value = aperture,
                        onValueChange = onApertureChange,
                        modifier = Modifier.weight(1f),
                        suggestions = metadataHistory.aperture,
                        onCommit = { onMetadataHistoryUpdate("aperture", aperture) },
                        fieldIncluded = overrideAperture,
                        onFieldIncludedChange = onOverrideApertureChange,
                        sourceHint = sourceExif?.aperture,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MetadataField(
                        label = "Shutter Speed",
                        placeholder = "1/125",
                        value = shutterSpeed,
                        onValueChange = onShutterSpeedChange,
                        modifier = Modifier.weight(1f),
                        suggestions = metadataHistory.shutterSpeed,
                        onCommit = { onMetadataHistoryUpdate("shutterSpeed", shutterSpeed) },
                        fieldIncluded = overrideShutterSpeed,
                        onFieldIncludedChange = onOverrideShutterSpeedChange,
                        sourceHint = sourceExif?.shutterSpeed,
                    )
                    MetadataField(
                        label = "ISO",
                        placeholder = "400",
                        value = iso,
                        onValueChange = onIsoChange,
                        modifier = Modifier.weight(1f),
                        keyboardType = KeyboardType.Number,
                        suggestions = metadataHistory.iso,
                        onCommit = { onMetadataHistoryUpdate("iso", iso) },
                        fieldIncluded = overrideIso,
                        onFieldIncludedChange = onOverrideIsoChange,
                        sourceHint = sourceExif?.iso,
                    )
                }
            }
        }
    }
}

/** Collapsible location section with IPTC structured fields and GPS coordinates. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LocationSection(
    showExpanded: Boolean,
    onToggle: () -> Unit,
    locationName: String,
    onLocationNameChange: (String) -> Unit,
    city: String,
    onCityChange: (String) -> Unit,
    stateVal: String,
    onStateChange: (String) -> Unit,
    country: String,
    onCountryChange: (String) -> Unit,
    gpsLatitude: String,
    onGpsLatitudeChange: (String) -> Unit,
    gpsLongitude: String,
    onGpsLongitudeChange: (String) -> Unit,
    metadataHistory: MetadataHistory,
    onMetadataHistoryUpdate: (String, String) -> Unit,
    onPickLocation: (() -> Unit)? = null,
    onApplyRecentLocation: ((RecentMetadataSet) -> Unit)? = null,
    overrideGps: Boolean? = null,
    onOverrideGpsChange: ((Boolean) -> Unit)? = null,
    sourceGpsHint: String? = null,
) {
    Column {
        OutlinedButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
            Icon(
                if (showExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (showExpanded) "Hide" else "Show",
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.LocationOn, null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Location", style = MaterialTheme.typography.labelSmall)
        }
        if (showExpanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // ── Recent Locations ──
                if (onApplyRecentLocation != null) {
                    RecentLocationDropdown(
                        metadataHistory = metadataHistory,
                        onApplyLocation = onApplyRecentLocation,
                    )
                }
                MetadataField(
                    label = "Location Name",
                    placeholder = "Grandma's house",
                    value = locationName,
                    onValueChange = onLocationNameChange,
                    suggestions = metadataHistory.locationName,
                    onCommit = { onMetadataHistoryUpdate("locationName", locationName) },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MetadataField(
                        label = "City",
                        placeholder = "Worcester",
                        value = city,
                        onValueChange = onCityChange,
                        modifier = Modifier.weight(1f),
                        suggestions = metadataHistory.city,
                        onCommit = { onMetadataHistoryUpdate("city", city) },
                    )
                    MetadataField(
                        label = "State",
                        placeholder = "MA",
                        value = stateVal,
                        onValueChange = onStateChange,
                        modifier = Modifier.weight(1f),
                        suggestions = metadataHistory.state,
                        onCommit = { onMetadataHistoryUpdate("state", stateVal) },
                    )
                }
                MetadataField(
                    label = "Country",
                    placeholder = "United States",
                    value = country,
                    onValueChange = onCountryChange,
                    suggestions = metadataHistory.country,
                    onCommit = { onMetadataHistoryUpdate("country", country) },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("GPS Coordinates", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.weight(1f))
                    if (overrideGps != null && onOverrideGpsChange != null) {
                        OverrideCheckbox(
                            included = overrideGps,
                            onIncludedChange = onOverrideGpsChange,
                        )
                    }
                }
                Text(
                    "Enter decimal degrees (e.g. 42.2626, -71.8023). Negative = South/West.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (sourceGpsHint != null) {
                    Text(
                        sourceGpsHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MetadataField(
                        label = "Latitude",
                        placeholder = "42.2626",
                        value = gpsLatitude,
                        onValueChange = onGpsLatitudeChange,
                        suggestions = metadataHistory.gpsLatitude,
                        onCommit = { onMetadataHistoryUpdate("gpsLatitude", gpsLatitude) },
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                    )
                    MetadataField(
                        label = "Longitude",
                        placeholder = "-71.8023",
                        value = gpsLongitude,
                        onValueChange = onGpsLongitudeChange,
                        suggestions = metadataHistory.gpsLongitude,
                        onCommit = { onMetadataHistoryUpdate("gpsLongitude", gpsLongitude) },
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (onPickLocation != null) {
                    OutlinedButton(onClick = onPickLocation, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.LocationOn, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Pick on Map", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

/** Collapsible subjects/faces section. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun SubjectsSection(
    showExpanded: Boolean,
    onToggle: () -> Unit,
    subjects: String,
    onSubjectsChange: (String) -> Unit,
    metadataHistory: MetadataHistory,
    onMetadataHistoryUpdate: (String, String) -> Unit,
    onMetadataHistoryRemove: (String, String) -> Unit,
    onSelectFaces: (() -> Unit)? = null,
    faceRegions: List<FaceRegion> = emptyList(),
    onRemoveFace: ((Int) -> Unit)? = null,
    onClearAllFaces: (() -> Unit)? = null,
) {
    val subjectList =
        remember(subjects) { subjects.split(",").map { it.trim() }.filter { it.isNotBlank() } }
    var subjectInput by remember { mutableStateOf("") }

    Column {
        OutlinedButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
            Icon(
                if (showExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (showExpanded) "Hide" else "Show",
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.Person, null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Subjects & Faces", style = MaterialTheme.typography.labelSmall)
            if (faceRegions.isNotEmpty()) {
                Spacer(Modifier.width(4.dp))
                Text(
                    "(${faceRegions.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (showExpanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Subject names are written to EXIF/IPTC metadata and as MWG-RS face regions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (onSelectFaces != null) {
                    OutlinedButton(onClick = onSelectFaces, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Face, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (faceRegions.isEmpty()) "Select Faces on Photo"
                            else "Edit Face Regions (${faceRegions.size})",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                if (faceRegions.isNotEmpty()) {
                    Surface(
                        tonalElevation = 1.dp,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Face Regions", style = MaterialTheme.typography.labelMedium)
                                if (onClearAllFaces != null) {
                                    Text(
                                        "Clear All",
                                        style =
                                            MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold
                                            ),
                                        color = Color(0xFFFF6666),
                                        modifier = Modifier.clickable { onClearAllFaces() },
                                    )
                                }
                            }
                            faceRegions.forEachIndexed { idx, region ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Icon(
                                            regionTypeIcon(RegionType.fromMwgRs(region.type)),
                                            contentDescription = region.type,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                        Text(
                                            region.name,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        val xPct = kotlin.math.round(region.x * 100).toInt()
                                        val yPct = kotlin.math.round(region.y * 100).toInt()
                                        Text(
                                            "($xPct%, $yPct%)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (onRemoveFace != null) {
                                        IconButton(
                                            onClick = { onRemoveFace(idx) },
                                            modifier = Modifier.size(20.dp),
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                "Remove",
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.error,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (subjectList.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        subjectList.forEach { subject ->
                            RemovableChip(
                                text = subject,
                                onRemove = {
                                    val updated = subjectList.filter { it != subject }
                                    onSubjectsChange(updated.joinToString(", "))
                                    onMetadataHistoryRemove("subjects", subject)
                                },
                            )
                        }
                    }
                }
                var suggestionsExpanded by remember { mutableStateOf(false) }
                val availableSuggestions =
                    remember(metadataHistory.subjects, subjectList) {
                        metadataHistory.subjects.filter { it !in subjectList }
                    }
                val filteredSuggestions =
                    remember(availableSuggestions, subjectInput) {
                        if (subjectInput.isBlank()) availableSuggestions
                        else
                            availableSuggestions.filter {
                                it.contains(subjectInput, ignoreCase = true)
                            }
                    }
                ExposedDropdownMenuBox(
                    expanded = suggestionsExpanded && filteredSuggestions.isNotEmpty(),
                    onExpandedChange = { suggestionsExpanded = it },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = subjectInput,
                            onValueChange = {
                                subjectInput = it
                                suggestionsExpanded = true
                            },
                            placeholder = {
                                Text("Add person...", style = MaterialTheme.typography.labelSmall)
                            },
                            modifier =
                                Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).weight(1f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            trailingIcon = {
                                if (subjectInput.isNotBlank()) {
                                    IconButton(
                                        onClick = {
                                            val updated =
                                                if (subjects.isBlank()) subjectInput.trim()
                                                else "${subjects.trim()}, ${subjectInput.trim()}"
                                            onSubjectsChange(updated)
                                            onMetadataHistoryUpdate("subjects", subjectInput.trim())
                                            subjectInput = ""
                                        },
                                        modifier = Modifier.size(20.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.Add,
                                            "Add subject",
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            },
                        )
                    }
                    if (filteredSuggestions.isNotEmpty()) {
                        ExposedDropdownMenu(
                            expanded = suggestionsExpanded && filteredSuggestions.isNotEmpty(),
                            onDismissRequest = { suggestionsExpanded = false },
                        ) {
                            filteredSuggestions.take(10).forEach { suggestion ->
                                DropdownMenuItem(
                                    text = {
                                        Text(suggestion, style = MaterialTheme.typography.bodySmall)
                                    },
                                    onClick = {
                                        val updated =
                                            if (subjects.isBlank()) suggestion
                                            else "${subjects.trim()}, $suggestion"
                                        onSubjectsChange(updated)
                                        onMetadataHistoryUpdate("subjects", suggestion)
                                        subjectInput = ""
                                        suggestionsExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DatePickerDialog(onDismissRequest: () -> Unit, onDateSelected: (String) -> Unit) {
    val datePickerState = rememberDatePickerState()
    EditDialog(onDismissRequest = onDismissRequest) {
        Card(modifier = Modifier.padding(16.dp)) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                DatePicker(state = datePickerState)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismissRequest) { Text("Cancel") }
                    TextButton(
                        onClick = {
                            val selectedDate =
                                datePickerState.selectedDateMillis?.let { millis ->
                                    val instant = Instant.ofEpochMilli(millis)
                                    val localDate =
                                        instant.atZone(ZoneId.systemDefault()).toLocalDate()
                                    localDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                                } ?: ""
                            if (selectedDate.isNotBlank()) onDateSelected(selectedDate)
                            onDismissRequest()
                        }
                    ) {
                        Text("OK")
                    }
                }
            }
        }
    }
}

/** Wrapper for Dialog that works in Compose Desktop. */
@Composable
internal fun EditDialog(onDismissRequest: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 6.dp,
        ) {
            Box(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) { content() }
        }
    }
}

/** Face name entry panel (extracted from inline Dialog content). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FaceNameEntryPanel(
    faceNameInput: String,
    onFaceNameInputChange: (String) -> Unit,
    selectedRegionType: RegionType,
    selectedFaceSize: FaceSize,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(
        modifier = Modifier.width(260.dp),
        tonalElevation = 8.dp,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Name this ${selectedRegionType.displayName.lowercase()}",
                style = MaterialTheme.typography.titleSmall,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    regionTypeIcon(selectedRegionType),
                    contentDescription = selectedRegionType.displayName,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "${selectedRegionType.displayName} • ${selectedFaceSize.displayName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextField(
                value = faceNameInput,
                onValueChange = onFaceNameInputChange,
                placeholder = { Text("Name…", style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.align(Alignment.End),
            ) {
                OutlinedButton(onClick = onCancel) {
                    Text("Cancel", style = MaterialTheme.typography.labelSmall)
                }
                Button(onClick = onConfirm, enabled = faceNameInput.isNotBlank()) {
                    Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Save", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/** A removable chip/tag for keywords and subjects. Shows text with an X button to remove. */
@Composable
internal fun RemovableChip(text: String, onRemove: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onRemove),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove $text",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
