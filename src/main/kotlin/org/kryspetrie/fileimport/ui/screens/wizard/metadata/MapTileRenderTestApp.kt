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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * Standalone component test for [OsmMapView] tile rendering.
 *
 * Launch this to verify that map tiles load and render properly. The window shows
 * a full OpenStreetMap view centered on the Eastern US at zoom level 5. You can
 * pan (drag), zoom (scroll wheel or +/- buttons), and switch between street and
 * satellite views (🛰 button).
 *
 * Run via: `./gradlew runMapTileTest`
 */
fun mainMapTileTest() = application {
    val koin = startKoin {
        modules(appModule)
    }

    val dispatcherProvider: DispatcherProvider = koin.koin.get()

    Window(
        onCloseRequest = ::exitApplication,
        title = "OsmMapView Tile Render Test",
        state = rememberWindowState(
            width = androidx.compose.ui.unit.Dp(1200f),
            height = androidx.compose.ui.unit.Dp(800f)
        ),
    ) {
        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                OsmMapView(
                    modifier = Modifier.fillMaxSize(),
                    initialLat = 39.0,
                    initialLon = -78.0,
                    initialZoom = 5,
                    onMapClick = { lat, lon ->
                        println("Map clicked: lat=$lat, lon=$lon")
                    },
                    dispatcherProvider = dispatcherProvider,
                    coroutineScope = CoroutineScope(Dispatchers.Main),
                )
            }
        }
    }
}

fun main() = mainMapTileTest()