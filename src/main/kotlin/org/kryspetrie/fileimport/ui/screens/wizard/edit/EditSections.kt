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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection

import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.kryspetrie.fileimport.domain.model.FaceRegion
import org.kryspetrie.fileimport.domain.model.MetadataHistory
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.i18n.strings
import org.kryspetrie.fileimport.domain.model.RecentMetadataSet
import org.kryspetrie.fileimport.domain.model.RegionType
import org.kryspetrie.fileimport.ui.screens.wizard.metadata.MetadataField
import org.kryspetrie.fileimport.ui.screens.wizard.metadata.OverrideCheckbox
import org.kryspetrie.fileimport.ui.screens.wizard.metadata.RecentLocationDropdown
import org.kryspetrie.fileimport.ui.screens.wizard.regionTypeIcon
import org.kryspetrie.fileimport.ui.wizard.state.FaceSize
import org.kryspetrie.fileimport.ui.wizard.state.PhotoScanWizardState
import org.kryspetrie.fileimport.ui.wizard.state.SourceExifSummary

/** Rotation controls section. */
@Composable
internal fun RotationSection(
    rotationDegrees: Int,
    onRotateCW: () -> Unit,
    onRotateCCW: () -> Unit,
    onRotate180: () -> Unit,
) {
    val s = strings()
    Surface(
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(s.t(StringKey.FIELD_ROTATION), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRotateCCW, modifier = Modifier.size(24.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.RotateLeft,
                    s.t(StringKey.ACC_ROTATE_CCW),
                    Modifier.size(16.dp),
                )
            }
            IconButton(onClick = onRotate180, modifier = Modifier.size(24.dp)) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = s.t(StringKey.FIELD_ROTATE_180),
                    modifier = Modifier.size(16.dp),
                )
            }
            IconButton(onClick = onRotateCW, modifier = Modifier.size(24.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.RotateRight,
                    s.t(StringKey.ACC_ROTATE_CW),
                    Modifier.size(16.dp),
                )
            }
            Text(
                s.t(StringKey.META_ROTATION_DEGREES, "degrees" to "$rotationDegrees"),
                style = MaterialTheme.typography.labelSmall,
                color =
                    if (rotationDegrees != 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(36.dp),
                textAlign = TextAlign.End,
            )
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
    val s = strings()
    val focusManager = LocalFocusManager.current
    val keywordList =
        remember(keywords) { keywords.split(",").map { it.trim() }.filter { it.isNotBlank() } }
    var keywordInput by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }

    MetadataField(
        label = s.t(StringKey.FIELD_DESCRIPTION),
        placeholder = s.t(StringKey.FIELD_DESCRIPTION_PLACEHOLDER),
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
        Text(s.t(StringKey.FIELD_KEYWORDS), style = MaterialTheme.typography.labelMedium)
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
        if (availableSuggestions.isNotEmpty()) {
            Box {
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
                            Text(
                                s.t(StringKey.FIELD_ADD_KEYWORD_PLACEHOLDER),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 0.dp),
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
                                        s.t(StringKey.FIELD_ADD_KEYWORD),
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        },
                    )
                }
                if (filteredSuggestions.isNotEmpty()) {
                    DropdownMenu(
                        expanded = suggestionsExpanded && filteredSuggestions.isNotEmpty(),
                        onDismissRequest = { suggestionsExpanded = false },
                    ) {
                        filteredSuggestions.take(10).forEach { suggestion ->
                            DropdownMenuItem(
                                text = {
                                    Text(suggestion, style = MaterialTheme.typography.labelSmall)
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

    // Original Date + Year on the same line
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MetadataField(
                    label = s.t(StringKey.FIELD_ORIGINAL_DATE),
                    placeholder = s.t(StringKey.FIELD_DATE_PLACEHOLDER),
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
                    modifier = Modifier.size(32.dp).padding(top = 20.dp),
                ) {
                    Icon(
                        Icons.Default.DateRange,
                        s.t(StringKey.FIELD_PICK_DATE),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        MetadataField(
            label = s.t(StringKey.FIELD_YEAR),
            placeholder = s.t(StringKey.FIELD_YEAR_PLACEHOLDER),
            value = year,
            onValueChange = { onYearChange(it.filter { c -> c.isDigit() }.take(4)) },
            keyboardType = KeyboardType.Number,
            suggestions = metadataHistory.year,
            onCommit = { onMetadataHistoryUpdate("year", year) },
            modifier = Modifier.width(120.dp),
            fieldIncluded = overrideYear,
            onFieldIncludedChange = onOverrideYearChange,
            sourceHint = sourceExif?.dateOriginal?.take(4),
        )
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
}

/** Camera settings section — collapsed by default since it's rarely needed. */
@Composable
internal fun CameraSection(
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
    val s = strings()
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (expanded) ExpandLessIcon else ExpandMoreIcon,
                contentDescription = if (expanded) s.t(StringKey.ACC_HIDE) else s.t(StringKey.ACC_SHOW),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                s.t(StringKey.FIELD_CAMERA_SETTINGS),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            )
        }
        if (expanded) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MetadataField(
                    label = s.t(StringKey.FIELD_CAMERA_MAKE),
                    placeholder = s.t(StringKey.FIELD_CAMERA_MAKE_PLACEHOLDER),
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
                    label = s.t(StringKey.FIELD_CAMERA_MODEL),
                    placeholder = s.t(StringKey.FIELD_CAMERA_MODEL_PLACEHOLDER),
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
                label = s.t(StringKey.FIELD_LENS_MODEL),
                placeholder = s.t(StringKey.FIELD_LENS_PLACEHOLDER),
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
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MetadataField(
                    label = s.t(StringKey.FIELD_FOCAL_LENGTH),
                    placeholder = s.t(StringKey.FIELD_FOCAL_PLACEHOLDER),
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
                    label = s.t(StringKey.FIELD_APERTURE),
                    placeholder = s.t(StringKey.FIELD_APERTURE_PLACEHOLDER),
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
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MetadataField(
                    label = s.t(StringKey.FIELD_SHUTTER_SPEED),
                    placeholder = s.t(StringKey.FIELD_SHUTTER_PLACEHOLDER),
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
                    label = s.t(StringKey.FIELD_ISO),
                    placeholder = s.t(StringKey.FIELD_ISO_PLACEHOLDER),
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

private val ExpandMoreIcon = Icons.Default.ExpandMore
private val ExpandLessIcon = Icons.Default.ExpandLess

/**
 * Location section — condensed layout.
 * - **Location Name**: A colloquial/recognizable name (e.g. "Grandma's House", "Disney World")
 * - **Address**: Full geocoded address from the map picker (e.g. "Worcester, Massachusetts, United
 *   States")
 *
 * If `address` is set, it is written to IPTC SubLocation in EXIF; otherwise locationName is used.
 * City/State/Country/GPS are collapsed by default since the map picker typically fills them; they
 * remain editable for fine-grained control when expanded.
 */
@Composable
internal fun LocationSection(
    locationName: String,
    onLocationNameChange: (String) -> Unit,
    address: String,
    onAddressChange: (String) -> Unit,
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
    val s = strings()
    // Collapse details by default
    val hasDetails =
        city.isNotBlank() ||
            stateVal.isNotBlank() ||
            country.isNotBlank() ||
            gpsLatitude.isNotBlank() ||
            gpsLongitude.isNotBlank()
    var detailsExpanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
        // ── Section header ──
        Text(
            s.t(StringKey.FIELD_LOCATION),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        )

        // ── Recent Locations ──
        if (onApplyRecentLocation != null) {
            RecentLocationDropdown(
                metadataHistory = metadataHistory,
                onApplyLocation = onApplyRecentLocation,
            )
        }

        // ── Location Name — colloquial/recognizable name ──
        MetadataField(
            label = s.t(StringKey.FIELD_LOCATION_NAME),
            placeholder = s.t(StringKey.FIELD_LOCATION_NAME_PLACEHOLDER),
            value = locationName,
            onValueChange = onLocationNameChange,
            suggestions = metadataHistory.locationName,
            onCommit = { onMetadataHistoryUpdate("locationName", locationName) },
        )

        // ── Address — full geocoded address from map picker ──
        MetadataField(
            label = s.t(StringKey.FIELD_ADDRESS),
            placeholder = s.t(StringKey.FIELD_ADDRESS_PLACEHOLDER),
            value = address,
            onValueChange = onAddressChange,
            suggestions = metadataHistory.address,
            onCommit = { onMetadataHistoryUpdate("address", address) },
            trailingIcon =
                if (onPickLocation != null) {
                    {
                        IconButton(onClick = onPickLocation, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = s.t(StringKey.FIELD_PICK_ON_MAP),
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                } else null,
        )

        // ── Expand/collapse for City/State/Country/GPS details ──
        Row(
            modifier = Modifier.fillMaxWidth().clickable { detailsExpanded = !detailsExpanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (detailsExpanded) ExpandLessIcon else ExpandMoreIcon,
                contentDescription =
                    if (detailsExpanded) s.t(StringKey.FIELD_HIDE_DETAILS)
                    else s.t(StringKey.FIELD_SHOW_DETAILS),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                if (detailsExpanded) s.t(StringKey.FIELD_HIDE_DETAILS)
                else s.t(StringKey.FIELD_LOCATION_DETAILS),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            if (!detailsExpanded) {
                val summaryParts =
                    listOfNotNull(
                        city.takeIf { it.isNotBlank() },
                        stateVal.takeIf { it.isNotBlank() },
                        country.takeIf { it.isNotBlank() },
                        gpsLatitude
                            .takeIf { it.isNotBlank() }
                            ?.let { lat ->
                                gpsLongitude.takeIf { it.isNotBlank() }?.let { lon -> "$lat, $lon" }
                            },
                    )
                if (summaryParts.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "— ${summaryParts.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (detailsExpanded) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MetadataField(
                    label = s.t(StringKey.FIELD_CITY),
                    placeholder = s.t(StringKey.FIELD_CITY_PLACEHOLDER),
                    value = city,
                    onValueChange = onCityChange,
                    modifier = Modifier.weight(1f),
                    suggestions = metadataHistory.city,
                    onCommit = { onMetadataHistoryUpdate("city", city) },
                )
                MetadataField(
                    label = s.t(StringKey.FIELD_STATE),
                    placeholder = s.t(StringKey.FIELD_STATE_PLACEHOLDER),
                    value = stateVal,
                    onValueChange = onStateChange,
                    modifier = Modifier.weight(1f),
                    suggestions = metadataHistory.state,
                    onCommit = { onMetadataHistoryUpdate("state", stateVal) },
                )
                MetadataField(
                    label = s.t(StringKey.FIELD_COUNTRY),
                    placeholder = s.t(StringKey.FIELD_COUNTRY_PLACEHOLDER),
                    value = country,
                    onValueChange = onCountryChange,
                    modifier = Modifier.weight(1f),
                    suggestions = metadataHistory.country,
                    onCommit = { onMetadataHistoryUpdate("country", country) },
                )
            }

            // ── GPS Coordinates — inside details section ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(s.t(StringKey.FIELD_GPS), style = MaterialTheme.typography.labelMedium)
                if (overrideGps != null && onOverrideGpsChange != null) {
                    Spacer(Modifier.width(4.dp))
                    OverrideCheckbox(included = overrideGps, onIncludedChange = onOverrideGpsChange)
                }
                Spacer(Modifier.weight(1f))
                if (sourceGpsHint != null) {
                    Text(
                        sourceGpsHint,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MetadataField(
                    label = s.t(StringKey.FIELD_LAT),
                    placeholder = s.t(StringKey.FIELD_LAT_PLACEHOLDER),
                    value = gpsLatitude,
                    onValueChange = onGpsLatitudeChange,
                    suggestions = metadataHistory.gpsLatitude,
                    onCommit = { onMetadataHistoryUpdate("gpsLatitude", gpsLatitude) },
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
                MetadataField(
                    label = s.t(StringKey.FIELD_LON),
                    placeholder = s.t(StringKey.FIELD_LON_PLACEHOLDER),
                    value = gpsLongitude,
                    onValueChange = onGpsLongitudeChange,
                    suggestions = metadataHistory.gpsLongitude,
                    onCommit = { onMetadataHistoryUpdate("gpsLongitude", gpsLongitude) },
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Tag Photo section — collapsed by default (secondary workflow). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun SubjectsSection(
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
    val s = strings()
    val subjectList =
        remember(subjects) { subjects.split(",").map { it.trim() }.filter { it.isNotBlank() } }
    var subjectInput by remember { mutableStateOf("") }
    var subjectsExpanded by remember { mutableStateOf(false) }
    val subjectFocusManager = LocalFocusManager.current

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
        Row(
            modifier = Modifier.fillMaxWidth().clickable { subjectsExpanded = !subjectsExpanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (subjectsExpanded) ExpandLessIcon else ExpandMoreIcon,
                contentDescription = if (subjectsExpanded) s.t(StringKey.ACC_HIDE) else s.t(StringKey.ACC_SHOW),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                s.t(StringKey.FIELD_TAG_PHOTO),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            )
        }
        if (subjectsExpanded) {
            if (faceRegions.isNotEmpty()) {
                Surface(
                    tonalElevation = 1.dp,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                s.t(StringKey.FIELD_TAG_REGIONS),
                                style =
                                    MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                            )
                            if (onClearAllFaces != null) {
                                Text(
                                    s.t(StringKey.FIELD_CLEAR_ALL),
                                    style =
                                        MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.clickable { onClearAllFaces() },
                                )
                            }
                        }
                        faceRegions.forEachIndexed { idx, region ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Icon(
                                        regionTypeIcon(RegionType.fromMwgRs(region.type)),
                                        contentDescription = region.type,
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(region.name, style = MaterialTheme.typography.labelSmall)
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
                                        modifier = Modifier.size(18.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            s.t(StringKey.META_REMOVE),
                                            modifier = Modifier.size(12.dp),
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
            // ── Action buttons: Tag Photo + Add person input ──
            Surface(
                tonalElevation = 1.dp,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(8.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (onSelectFaces != null) {
                        OutlinedButton(
                            onClick = onSelectFaces,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Sell, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (faceRegions.isEmpty()) s.t(StringKey.FIELD_TAG_PHOTO)
                                else "${s.t(StringKey.WIZARD_TAG_EDITOR)} (${faceRegions.size})",
                                style = MaterialTheme.typography.labelSmall,
                            )
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
                    Box {
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
                                    Text(
                                        s.t(StringKey.FIELD_ADD_PERSON),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                },
                                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 0.dp),
                                singleLine = true,
                                keyboardActions =
                                    KeyboardActions(
                                        onDone = {
                                            if (subjectInput.isNotBlank()) {
                                                val updated =
                                                    if (subjects.isBlank()) subjectInput.trim()
                                                    else
                                                        "${subjects.trim()}, ${subjectInput.trim()}"
                                                onSubjectsChange(updated)
                                                onMetadataHistoryUpdate(
                                                    "subjects",
                                                    subjectInput.trim(),
                                                )
                                                subjectInput = ""
                                            }
                                            subjectFocusManager.moveFocus(FocusDirection.Down)
                                        }
                                    ),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                trailingIcon = {
                                    if (subjectInput.isNotBlank()) {
                                        IconButton(
                                            onClick = {
                                                val updated =
                                                    if (subjects.isBlank()) subjectInput.trim()
                                                    else
                                                        "${subjects.trim()}, ${subjectInput.trim()}"
                                                onSubjectsChange(updated)
                                                onMetadataHistoryUpdate(
                                                    "subjects",
                                                    subjectInput.trim(),
                                                )
                                                subjectInput = ""
                                            },
                                            modifier = Modifier.size(20.dp),
                                        ) {
                                            Icon(
                                                Icons.Default.Add,
                                                s.t(StringKey.FIELD_ADD_SUBJECT),
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                },
                            )
                        }
                        if (filteredSuggestions.isNotEmpty()) {
                            DropdownMenu(
                                expanded = suggestionsExpanded && filteredSuggestions.isNotEmpty(),
                                onDismissRequest = { suggestionsExpanded = false },
                            ) {
                                filteredSuggestions.take(10).forEach { suggestion ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                suggestion,
                                                style = MaterialTheme.typography.labelSmall,
                                            )
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
                } // closes Column
            } // closes Surface
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DatePickerDialog(onDismissRequest: () -> Unit, onDateSelected: (String) -> Unit) {
    val s = strings()
    val datePickerState = rememberDatePickerState()
    EditDialog(onDismissRequest = onDismissRequest) {
        Card(modifier = Modifier.padding(16.dp)) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                DatePicker(state = datePickerState)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismissRequest) { Text(s.cancel) }
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
                        Text(s.ok)
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
    val s = strings()
    Surface(
        modifier = Modifier.width(220.dp),
        tonalElevation = 8.dp,
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                s.t(
                    StringKey.FIELD_NAME_THIS,
                    "type" to selectedRegionType.displayName.lowercase(),
                ),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
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
                placeholder = { Text(s.t(StringKey.FIELD_NAME_PLACEHOLDER), style = MaterialTheme.typography.labelSmall) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.align(Alignment.End),
            ) {
                OutlinedButton(onClick = onCancel) {
                    Text(s.cancel, style = MaterialTheme.typography.labelSmall)
                }
                Button(onClick = onConfirm, enabled = faceNameInput.isNotBlank()) {
                    Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(s.save, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/** A removable chip/tag for keywords and subjects. Shows text with an X button to remove. */
@Composable
internal fun RemovableChip(text: String, onRemove: () -> Unit) {
    val s = strings()
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
                contentDescription = "${s.t(StringKey.META_REMOVE)} $text",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/** Read-only section showing all source metadata from the original file. */
@Composable
internal fun SourceMetadataSection(sourceExif: SourceExifSummary) {
    val s = strings()
    val lines = sourceExif.summaryLines(s)
    if (lines.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = s.t(StringKey.FIELD_SOURCE_METADATA),
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.tertiary,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                s.t(StringKey.FIELD_SOURCE_METADATA),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        Surface(
            tonalElevation = 1.dp,
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(6.dp)) {
                lines.forEach { (label, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            "$label:",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(100.dp),
                        )
                        Text(
                            value,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
