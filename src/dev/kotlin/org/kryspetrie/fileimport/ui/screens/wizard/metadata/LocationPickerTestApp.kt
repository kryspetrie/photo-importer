package org.kryspetrie.fileimport.ui.screens.wizard.metadata

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.koin.core.context.startKoin
import org.kryspetrie.fileimport.di.appModule
import org.kryspetrie.fileimport.domain.port.DispatcherProvider
import org.kryspetrie.fileimport.domain.port.GeocodingPort
import org.kryspetrie.fileimport.domain.port.LocationSearchPort

/**
 * Standalone component test for the full [LocationPickerContent] (unified search + map overlay).
 *
 * Launches a separate window containing the complete location picker UI that is used in the Edit
 * screen's "Pick on map" feature. This allows testing:
 * - Map tile rendering (reuses [OsmMapView] which is independently tested via `runMapTileTest`)
 * - Unified search field for location queries
 * - Reverse geocoding on map click
 * - Result selection and confirmation flow
 * - Cancel/dismiss button
 * - Defaults to last-viewed map position
 *
 * Run via: `./gradlew runLocationPickerTest`
 */
fun mainLocationPickerTest() = application {
    val koin = startKoin { modules(appModule) }

    val dispatcherProvider: DispatcherProvider = koin.koin.get()
    val geocodingPort: GeocodingPort = koin.koin.get()
    val locationSearchService: LocationSearchPort = koin.koin.get()

    Window(
        onCloseRequest = ::exitApplication,
        title = "Location Picker Test",
        state =
            rememberWindowState(
                width = androidx.compose.ui.unit.Dp(1200f),
                height = androidx.compose.ui.unit.Dp(800f),
            ),
    ) {
        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                LocationPickerContent(
                    locationSearchService = locationSearchService,
                    geocodingPort = geocodingPort,
                    dispatcherProvider = dispatcherProvider,
                    initialLat = 39.0,
                    initialLon = -78.0,
                    initialZoom = 5.0,
                    onLocationSelected = { result ->
                        println(
                            "Location selected: ${result.name} (${result.latitude}, ${result.longitude})"
                        )
                        println(
                            "  City: ${result.city}, State: ${result.state}, Country: ${result.country}"
                        )
                    },
                    onDismiss = {
                        println("Location picker dismissed")
                        exitApplication()
                    },
                    onMapLocationChanged = { lat, lon, zoom ->
                        println("Map moved to: lat=$lat, lon=$lon, zoom=$zoom")
                    },
                )
            }
        }
    }
}

fun main() = mainLocationPickerTest()
