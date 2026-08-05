package org.kryspetrie.fileimport.ui.screens.metadataeditor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// ── Dialog toggles ──────────────────────────────────────────

internal fun MetadataEditorViewModel.toggleBulkSelectionDialog() {
    showBulkSelectionDialog = !showBulkSelectionDialog
}

internal fun MetadataEditorViewModel.dismissBulkSelectionDialog() {
    showBulkSelectionDialog = false
}

// ── Location picker ──────────────────────────────────────────

internal fun MetadataEditorViewModel.requestLocationPicker(indices: List<Int>) {
    locationPickerTargetIndices = indices
    showLocationPicker = true
}

internal fun MetadataEditorViewModel.onLocationSelected(
    result: org.kryspetrie.fileimport.domain.model.LocationResult
) {
    if (isMultiEditMode) {
        editState.locationName = result.name
        editState.address = result.displayName
        editState.city = result.city.orEmpty()
        editState.state = result.state.orEmpty()
        editState.country = result.country.orEmpty()
        editState.gpsLatitude = result.latitude.toString()
        editState.gpsLongitude = result.longitude.toString()
    } else {
        for (idx in locationPickerTargetIndices) {
            state.updateConfig(idx) { config ->
                config.copy(
                    locationName = result.name,
                    address = result.displayName,
                    city = result.city ?: config.city,
                    state = result.state ?: config.state,
                    country = result.country ?: config.country,
                    gpsLatitude = result.latitude.toString(),
                    gpsLongitude = result.longitude.toString(),
                )
            }
        }
    }
    showLocationPicker = false
    locationPickerTargetIndices = emptyList()
}

internal fun MetadataEditorViewModel.dismissLocationPicker() {
    showLocationPicker = false
    locationPickerTargetIndices = emptyList()
}

internal fun MetadataEditorViewModel.updateMapLocation(
    lat: Double,
    lon: Double,
    zoom: Double,
    scope: CoroutineScope,
) {
    scope.launch {
        val current = settingsPort.observeSettings().first()
        settingsPort.saveSettings(
            current.copy(lastMapLat = lat, lastMapLon = lon, lastMapZoom = zoom)
        )
    }
}
