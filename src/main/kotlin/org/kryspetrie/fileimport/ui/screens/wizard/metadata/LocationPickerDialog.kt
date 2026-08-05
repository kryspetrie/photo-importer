package org.kryspetrie.fileimport.ui.screens.wizard.metadata

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.kryspetrie.fileimport.domain.model.LocationResult
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.domain.port.GeocodingPort
import org.kryspetrie.fileimport.domain.port.LocationSearchPort
import org.kryspetrie.fileimport.ui.components.LoadingIndicator
import org.kryspetrie.fileimport.ui.i18n.strings

/**
 * Full-screen location picker overlay with an OpenStreetMap map and unified search panel.
 *
 * Uses a [Dialog] with `usePlatformDefaultWidth = false` so it renders as a full-screen overlay
 * within the existing window (matching the face selector overlay pattern), rather than opening a
 * separate window.
 *
 * Layout: Map fills the entire overlay; a floating search panel overlays the left side.
 * - Type a query to search for locations via Nominatim
 * - Click a result to center the map and set a pin
 * - Click on the map to drop a pin → reverse geocode
 * - Confirm to populate location fields
 * - Defaults to the last-viewed map position
 */
@Composable
fun LocationPickerOverlay(
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
    val s = strings()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        LocationPickerContent(
            locationSearchService = locationSearchService,
            geocodingPort = geocodingPort,
            dispatcherProvider = dispatcherProvider,
            initialLat = initialLat,
            initialLon = initialLon,
            initialZoom = initialZoom,
            onLocationSelected = { result -> onLocationSelected(result) },
            onDismiss = onDismiss,
            onMapLocationChanged = onMapLocationChanged,
        )
    }
}

/**
 * The actual content of the location picker, extracted so it can be tested independently (e.g. in a
 * standalone Window for component testing).
 *
 * Layout: Map fills the entire area. The search panel floats over the map on the left side as a
 * translucent overlay.
 */
@Composable
fun LocationPickerContent(
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
    val s = strings()
    var searchQuery by remember { mutableStateOf("") }
    var selectedLocation by remember { mutableStateOf<LocationResult?>(null) }
    var pinLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var mapCenterLat by remember { mutableStateOf(initialLat) }
    var mapCenterLon by remember { mutableStateOf(initialLon) }
    var mapZoom by remember { mutableDoubleStateOf(initialZoom) }
    var isReverseGeocoding by remember { mutableStateOf(false) }
    var mapStyle by remember { mutableStateOf(MapStyle.STREET) }

    val searchResults by locationSearchService.searchResults.collectAsState()
    val isSearching by locationSearchService.isSearching.collectAsState()
    val errorMessage by locationSearchService.errorMessage.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(Unit) { onDispose { locationSearchService.clearResults() } }

    // ── Map fills the entire area; search panel overlays on the left ──
    Box(modifier = Modifier.fillMaxSize()) {
        // ── Map (fills everything) ────────────────────────────────────
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
                    } catch (e: CancellationException) {
                        throw e
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

        // ── Floating search panel overlaid on the left side of the map ──
        Surface(
            modifier =
                Modifier.align(Alignment.CenterStart)
                    .widthIn(min = 260.dp, max = 320.dp)
                    .fillMaxSize()
                    .padding(8.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            tonalElevation = 4.dp,
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Header with close button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        s.t(StringKey.META_LOCATION_PICKER_TITLE),
                        style =
                            MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    )
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.height(24.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) {
                        Text(s.cancel, style = MaterialTheme.typography.labelSmall)
                    }
                }

                // ── Unified search field ──
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { newQuery ->
                        searchQuery = newQuery
                        locationSearchService.search(newQuery)
                    },
                    label = { Text(s.t(StringKey.WIZARD_LOCATION_SEARCH)) },
                    placeholder = { Text(s.t(StringKey.WIZARD_LOCATION_EXAMPLE)) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = s.t(StringKey.ACC_SEARCH),
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Loading indicator
                if (isSearching) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LoadingIndicator(modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            s.t(StringKey.WIZARD_SEARCHING),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Error message
                if (errorMessage != null) {
                    Text(
                        errorMessage.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
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
                        LoadingIndicator(modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            s.t(StringKey.WIZARD_LOOKUP_ADDRESS),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // ── Selected location details + confirm ──
                if (selectedLocation != null) {
                    Card(
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(6.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    null,
                                    Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    selectedLocation!!.name,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                selectedLocation!!.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                            )
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
                                Text(
                                    "${s.t(StringKey.FIELD_CITY)}: $it",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            selectedLocation!!.state?.let {
                                Text(
                                    "${s.t(StringKey.FIELD_STATE)}: $it",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            selectedLocation!!.country?.let {
                                Text(
                                    "${s.t(StringKey.FIELD_COUNTRY)}: $it",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        onMapLocationChanged?.invoke(
                                            mapCenterLat,
                                            mapCenterLon,
                                            mapZoom,
                                        )
                                        onLocationSelected(selectedLocation!!)
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(s.t(StringKey.WIZARD_USE_THIS))
                                }
                                OutlinedButton(
                                    onClick = {
                                        selectedLocation = null
                                        pinLocation = null
                                    }
                                ) {
                                    Text(s.t(StringKey.META_CLEAR))
                                }
                            }
                        }
                    }
                }

                // ── Search results ──
                if (searchResults.isNotEmpty()) {
                    Text(
                        s.t(StringKey.WIZARD_RESULTS),
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
                        s.t(StringKey.WIZARD_NO_LOCATIONS),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    s.t(StringKey.WIZARD_MAP_TIP),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                tint =
                    if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(result.name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                Text(
                    result.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Text(
                "%.4f, %.4f".format(result.latitude, result.longitude),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
            )
        }
    }
}
