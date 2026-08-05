@file:Suppress("TooManyFunctions", "MagicNumber")
@file:OptIn(kotlinx.coroutines.FlowPreview::class)

package org.kryspetrie.fileimport.ui.screens.wizard.metadata

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.setValue

// Map camera state — holds all mutable map state
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Holds the mutable state for the map viewport.
 *
 * Zoom is stored as [Double] to support **fractional zoom** (e.g., 12.35) for continuous smooth
 * zooming. Tiles are fetched at `floor(zoom)` and scaled by `2^(zoom - floor(zoom))` to fill the
 * fractional gap.
 */
@Stable
class MapCameraState(
    initialLat: Double = 39.0,
    initialLon: Double = -78.0,
    initialZoom: Double = 5.0,
) {
    var centerLat by mutableDoubleStateOf(initialLat)
    var centerLon by mutableDoubleStateOf(initialLon)
    /** Fractional zoom level (e.g., 12.35 means between zoom 12 and 13). */
    var zoom by mutableDoubleStateOf(initialZoom)
}

// ──────────────────────────────────────────────────────────────────────────────
