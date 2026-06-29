package org.kryspetrie.fileimport.ui.screens.wizard.metadata


import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import org.kryspetrie.fileimport.ui.components.LoadingIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.kryspetrie.fileimport.domain.model.LocationResult
import org.kryspetrie.fileimport.domain.port.GeocodingPort
import org.kryspetrie.fileimport.domain.port.LocationSearchPort

/**
 * Full-screen location picker with an OpenStreetMap map and search sidebar.
 *
 * Layout: Search + results + preset views on the left, map fills the rest.
 * - Choose a preset view (Eastern US, Western US, etc.) to quickly jump the map
 * - Type a query to search for locations via Nominatim
 * - Click a result to center the map and set a pin
 * - Click on the map to drop a pin -> reverse geocode
 * - Confirm to populate city/state/country/GPS fields
 */
@Composable
fun LocationPickerDialog(
    locationSearchService: LocationSearchPort,
    geocodingPort: GeocodingPort,
    dispatcherProvider: org.kryspetrie.fileimport.domain.port.DispatcherProvider,
    initialLat: Double = 39.0,
    initialLon: Double = -78.0,
    initialZoom: Double = 5.0,
    onLocationSelected: (LocationResult) -> Unit,
    onDismiss: () -> Unit,
    onMapLocationChanged: ((lat: Double, lon: Double, zoom: Double) -> Unit)? = null,
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedLocation by remember { mutableStateOf<LocationResult?>(null) }
    var pinLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var mapCenterLat by remember { mutableStateOf(initialLat) }
    var mapCenterLon by remember { mutableStateOf(initialLon) }
    var mapZoom by remember { mutableDoubleStateOf(initialZoom) }
    var isReverseGeocoding by remember { mutableStateOf(false) }
    var presetExpanded by remember { mutableStateOf(false) }
    var mapStyle by remember { mutableStateOf(MapStyle.STREET) }

    val searchResults by locationSearchService.searchResults.collectAsState()
    val isSearching by locationSearchService.isSearching.collectAsState()
    val errorMessage by locationSearchService.errorMessage.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(Unit) { onDispose { locationSearchService.clearResults() } }

    Surface(modifier = Modifier.fillMaxSize(), tonalElevation = 8.dp) {
        Row(modifier = Modifier.fillMaxSize()) {
            // ── Left sidebar ──────────────────────────────────────────────
            Column(
                modifier = Modifier.widthIn(min = 280.dp).fillMaxHeight().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Text("Search Location", style = MaterialTheme.typography.titleMedium)
                }

                // Preset view selector
                Box {
                    OutlinedTextField(
                        value = "Jump to view\u2026",
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().clickable { presetExpanded = true },
                        label = { Text("Map view", style = MaterialTheme.typography.labelSmall) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Search,
                                "Preset views",
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                    DropdownMenu(
                        expanded = presetExpanded,
                        onDismissRequest = { presetExpanded = false },
                    ) {
                        mapViewPresets.forEach { preset ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            preset.name,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        Text(
                                            "%.1f, %.1f  z%.0f"
                                                .format(preset.lat, preset.lon, preset.zoom),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                onClick = {
                                    mapCenterLat = preset.lat
                                    mapCenterLon = preset.lon
                                    mapZoom = preset.zoom
                                    presetExpanded = false
                                },
                            )
                        }
                    }
                }

                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { newQuery ->
                        searchQuery = newQuery
                        locationSearchService.search(newQuery)
                    },
                    label = { Text("Type a place name\u2026") },
                    placeholder = { Text("e.g. Worcester, MA") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search",
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Loading
                if (isSearching) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LoadingIndicator(
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Searching\u2026",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Error
                if (errorMessage != null) {
                    Text(
                        errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                // Reverse geocode loading
                if (isReverseGeocoding) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LoadingIndicator(
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Looking up address\u2026",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Selected location details
                if (selectedLocation != null) {
                    Card(
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    null,
                                    Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    selectedLocation!!.name,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                selectedLocation!!.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "%.4f, %.4f"
                                    .format(
                                        selectedLocation!!.latitude,
                                        selectedLocation!!.longitude,
                                    ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            selectedLocation!!.city?.let {
                                Text("City: $it", style = MaterialTheme.typography.labelSmall)
                            }
                            selectedLocation!!.state?.let {
                                Text("State: $it", style = MaterialTheme.typography.labelSmall)
                            }
                            selectedLocation!!.country?.let {
                                Text("Country: $it", style = MaterialTheme.typography.labelSmall)
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        onMapLocationChanged?.invoke(mapCenterLat, mapCenterLon, mapZoom)
                                        onLocationSelected(selectedLocation!!)
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Use This")
                                }
                                OutlinedButton(
                                    onClick = {
                                        selectedLocation = null
                                        pinLocation = null
                                    }
                                ) {
                                    Text("Clear")
                                }
                            }
                        }
                    }
                }

                // Search results
                if (searchResults.isNotEmpty()) {
                    Text(
                        "Results",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(searchResults) { result ->
                            LocationResultItem(
                                result = result,
                                isSelected =
                                    selectedLocation?.let {
                                        it.latitude == result.latitude &&
                                            it.longitude == result.longitude
                                    } ?: false,
                                onClick = {
                                    selectedLocation = result
                                    pinLocation = Pair(result.latitude, result.longitude)
                                    mapCenterLat = result.latitude
                                    mapCenterLon = result.longitude
                                    mapZoom = 12.0
                                },
                            )
                        }
                    }
                } else if (!isSearching && searchQuery.length >= 2 && errorMessage == null) {
                    Text(
                        "No locations found. Try a different search term, or click on the map to drop a pin.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }

                Text(
                    "Tip: Click on the map to drop a pin and look up the address.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.weight(1f))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = {
                                        onMapLocationChanged?.invoke(mapCenterLat, mapCenterLon, mapZoom)
                                        onDismiss()
                                    }) { Text("Cancel") }
                }
            }

            // ── Right side: map ──────────────────────────────────────────
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                OsmMapView(
                    modifier = Modifier.fillMaxSize(),
                    initialLat = mapCenterLat,
                    initialLon = mapCenterLon,
                    initialZoom = mapZoom,
                    initialMapStyle = mapStyle,
                    onMapStyleChanged = { mapStyle = it },
                    onMapClick = { lat, lon ->
                        pinLocation = Pair(lat, lon)
                        isReverseGeocoding = true
                        coroutineScope.launch {
                            try {
                                val result = geocodingPort.reverseGeocode(lat, lon)
                                selectedLocation =
                                    result
                                        ?: LocationResult(
                                            displayName = "%.4f, %.4f".format(lat, lon),
                                            name = "%.4f, %.4f".format(lat, lon),
                                            latitude = lat,
                                            longitude = lon,
                                        )
                            } catch (_: Exception) {
                                selectedLocation =
                                    LocationResult(
                                        displayName = "%.4f, %.4f".format(lat, lon),
                                        name = "%.4f, %.4f".format(lat, lon),
                                        latitude = lat,
                                        longitude = lon,
                                    )
                            }
                            isReverseGeocoding = false
                        }
                    },
                    pinLocation = pinLocation,
                    searchResults = searchResults,
                    selectedResult = selectedLocation,
                    dispatcherProvider = dispatcherProvider,
                )
            }
        }
    }
}

@Composable
private fun LocationResultItem(result: LocationResult, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
            ),
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                tint =
                    if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(result.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Text(
                    result.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Text(
                "%.4f, %.4f".format(result.latitude, result.longitude),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
