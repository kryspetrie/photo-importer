package org.kryspetrie.fileimport.ui.screens.wizard.edit

import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.model.geometry.BoundingBoxList
import org.kryspetrie.fileimport.domain.port.DispatcherProvider
import org.kryspetrie.fileimport.domain.port.GeocodingPort
import org.kryspetrie.fileimport.domain.port.LocationSearchPort
import org.kryspetrie.fileimport.domain.port.SettingsPort
import org.kryspetrie.fileimport.ui.screens.wizard.metadata.LocationPickerOverlay
import org.kryspetrie.fileimport.ui.wizard.state.PhotoScanWizardState

@Composable
internal fun EditLocationPickerHost(
    showLocationPicker: Boolean,
    targetIndices: List<Int>,
    state: PhotoScanWizardState,
    boundingBoxList: BoundingBoxList,
    settings: AppSettings,
    locationSearchService: LocationSearchPort,
    geocodingPort: GeocodingPort,
    dispatcherProvider: DispatcherProvider,
    settingsPort: SettingsPort,
    coroutineScope: CoroutineScope,
    onDismiss: () -> Unit,
) {
    if (!showLocationPicker || targetIndices.isEmpty()) return

    LocationPickerOverlay(
        locationSearchService = locationSearchService,
        geocodingPort = geocodingPort,
        dispatcherProvider = dispatcherProvider,
        initialLat = settings.lastMapLat,
        initialLon = settings.lastMapLon,
        initialZoom = settings.lastMapZoom,
        onLocationSelected = { result ->
            for (idx in targetIndices) {
                if (idx < boundingBoxList.size()) {
                    val boxId = boundingBoxList.boxes[idx].id
                    state.configs.updatePhotoScanConfiguration(boxId) {
                        it.copy(
                            locationName = result.name,
                            address = result.displayName,
                            city = result.city ?: it.city,
                            state = result.state ?: it.state,
                            country = result.country ?: it.country,
                            gpsLatitude = result.latitude.toString(),
                            gpsLongitude = result.longitude.toString(),
                        )
                    }
                }
            }
            onDismiss()
        },
        onDismiss = onDismiss,
        onMapLocationChanged = { lat, lon, zoom ->
            coroutineScope.launch {
                val current = settingsPort.observeSettings().first()
                settingsPort.saveSettings(
                    current.copy(lastMapLat = lat, lastMapLon = lon, lastMapZoom = zoom)
                )
            }
        },
    )
}
