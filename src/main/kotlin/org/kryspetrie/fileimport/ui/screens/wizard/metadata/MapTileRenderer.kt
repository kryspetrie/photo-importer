@file:Suppress("TooManyFunctions", "MagicNumber")
@file:OptIn(kotlinx.coroutines.FlowPreview::class)

package org.kryspetrie.fileimport.ui.screens.wizard.metadata

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.tan

// Math utilities — Web Mercator projection
// ──────────────────────────────────────────────────────────────────────────────

/** Core math for Web Mercator tile-based map rendering. Supports fractional zoom. */
object MapTileRenderer {

    /**
     * Convert lat/lon to tile coordinates at the given zoom. Works with both integer and fractional
     * zoom values for pixel-offset calculations.
     */
    fun latLonToTile(lat: Double, lon: Double, zoom: Double): Pair<Int, Int> {
        val intZoom = floor(zoom).toInt()
        val n = 2.0.pow(intZoom)
        val x = floor((lon + 180.0) / 360.0 * n).toInt()
        val latRad = lat * PI / 180.0
        val y = floor((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n).toInt()
        return Pair(x.coerceIn(0, n.toInt() - 1), y.coerceIn(0, n.toInt() - 1))
    }

    /**
     * Convert lat/lon to absolute pixel coordinates at the given zoom. Supports fractional zoom
     * (e.g., 12.35) for smooth zoom interpolation. [tileSize] is the source tile pixel size
     * (typically 256).
     */
    fun latLonToPixelOffset(
        lat: Double,
        lon: Double,
        zoom: Double,
        tileSize: Int = 256,
    ): Pair<Double, Double> {
        val n = 2.0.pow(zoom)
        val px = (lon + 180.0) / 360.0 * n * tileSize
        val latRad = lat * PI / 180.0
        val py = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n * tileSize
        return Pair(px, py)
    }

    /**
     * Convert absolute pixel coordinates back to lat/lon at the given zoom. Supports fractional
     * zoom.
     */
    fun pixelOffsetToLatLon(
        px: Double,
        py: Double,
        zoom: Double,
        tileSize: Int = 256,
    ): Pair<Double, Double> {
        val n = 2.0.pow(zoom)
        val lon = px / (n * tileSize) * 360.0 - 180.0
        val latRad = atan(sinh(PI * (1.0 - 2.0 * py / (n * tileSize))))
        val lat = latRad * 180.0 / PI
        return Pair(lat, lon)
    }

    fun clampLat(lat: Double): Double = lat.coerceIn(-85.05, 85.05)

    fun clampLon(lon: Double): Double = ((lon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
}

// ──────────────────────────────────────────────────────────────────────────────
