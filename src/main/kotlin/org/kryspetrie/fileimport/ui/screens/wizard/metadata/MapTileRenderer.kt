@file:Suppress("TooManyFunctions", "MagicNumber")

package org.kryspetrie.fileimport.ui.screens.wizard.metadata

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.tan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage
import org.kryspetrie.fileimport.domain.model.LocationResult
import org.kryspetrie.fileimport.domain.port.DispatcherProvider
import org.kryspetrie.fileimport.infrastructure.adapter.Platform

// ──────────────────────────────────────────────────────────────────────────────
// Map preset views — predefined locations for quick navigation
// ──────────────────────────────────────────────────────────────────────────────

/** A named map view preset with center coordinates and zoom level. */
data class MapViewPreset(val name: String, val lat: Double, val lon: Double, val zoom: Double)

/** Built-in map view presets covering common regions. */
val mapViewPresets =
    listOf(
        MapViewPreset("Eastern US", 39.0, -78.0, 5.0),
        MapViewPreset("Western US", 39.0, -114.0, 5.0),
        MapViewPreset("Central US", 39.0, -96.0, 5.0),
        MapViewPreset("Europe", 50.0, 10.0, 5.0),
        MapViewPreset("UK & Ireland", 54.0, -3.0, 6.0),
        MapViewPreset("East Asia", 35.0, 120.0, 5.0),
        MapViewPreset("South America", -15.0, -58.0, 4.0),
        MapViewPreset("Africa", 5.0, 20.0, 4.0),
        MapViewPreset("Australia", -28.0, 135.0, 4.0),
        MapViewPreset("World", 25.0, 0.0, 2.0),
    )

// ──────────────────────────────────────────────────────────────────────────────
// Map style — street vs satellite
// ──────────────────────────────────────────────────────────────────────────────

/** Available map tile styles. */
enum class MapStyle(val label: String) {
    STREET("Street Map"),
    SATELLITE("Satellite"),
}

// ──────────────────────────────────────────────────────────────────────────────
// Math utilities — Web Mercator projection
// ──────────────────────────────────────────────────────────────────────────────

/** Core math for Web Mercator tile-based map rendering. Supports fractional zoom. */
object MapTileRenderer {

    /**
     * Convert lat/lon to tile coordinates at the given zoom.
     * Works with both integer and fractional zoom values for pixel-offset calculations.
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
     * Convert lat/lon to absolute pixel coordinates at the given zoom.
     * Supports fractional zoom (e.g., 12.35) for smooth zoom interpolation.
     * [tileSize] is the source tile pixel size (typically 256).
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
     * Convert absolute pixel coordinates back to lat/lon at the given zoom.
     * Supports fractional zoom.
     */
    fun pixelOffsetToLatLon(
        px: Double,
        py: Double,
        zoom: Double,
        tileSize: Int = 256,
    ): Pair<Double, Double> {
        val n = 2.0.pow(zoom)
        val lon = px / (n * tileSize) * 360.0 - 180.0
        val latRad = atan(PI * (1.0 - 2.0 * py / (n * tileSize)))
        val lat = latRad * 180.0 / PI
        return Pair(lat, lon)
    }

    fun clampLat(lat: Double): Double = lat.coerceIn(-85.05, 85.05)

    fun clampLon(lon: Double): Double = ((lon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
}

// ──────────────────────────────────────────────────────────────────────────────
// LRU tile cache (in-memory)
// ──────────────────────────────────────────────────────────────────────────────

class TileCache(private val maxTiles: Int = 150) {
    private val cache =
        object : LinkedHashMap<String, ImageBitmap>(16, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, ImageBitmap>,
            ): Boolean = size > maxTiles
        }

    @Synchronized fun get(key: String): ImageBitmap? = cache[key]

    @Synchronized fun put(key: String, bitmap: ImageBitmap) {
        cache[key] = bitmap
    }

    @Synchronized fun clear() {
        cache.clear()
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Disk tile cache
// ──────────────────────────────────────────────────────────────────────────────

class DiskTileCache(cacheDir: File = File(Platform.cacheDir, "map-tiles")) {
    private val cacheDir: File
    /** Maximum age in milliseconds before a cached tile is considered stale (24 hours). */
    private val maxAgeMs: Long = 24L * 60 * 60 * 1000
    /** Maximum disk cache size in bytes (50 MB). */
    private val maxCacheBytes: Long = 50L * 1024 * 1024

    init {
        this.cacheDir = cacheDir
        if (!this.cacheDir.exists()) this.cacheDir.mkdirs()
    }

    fun get(z: Int, x: Int, y: Int, style: MapStyle = MapStyle.STREET): ByteArray? {
        val prefix = style.name.lowercase()
        val file = File(cacheDir, "${prefix}_${z}_${x}_${y}.png")
        return if (file.exists()) {
            try {
                if (System.currentTimeMillis() - file.lastModified() > maxAgeMs) {
                    file.delete()
                    null
                } else {
                    file.readBytes()
                }
            } catch (_: Exception) {
                null
            }
        } else null
    }

    fun put(z: Int, x: Int, y: Int, bytes: ByteArray, style: MapStyle = MapStyle.STREET) {
        try {
            val prefix = style.name.lowercase()
            File(cacheDir, "${prefix}_${z}_${x}_${y}.png").writeBytes(bytes)
        } catch (_: Exception) {}
    }

    /** Evicts oldest files if total cache size exceeds [maxCacheBytes]. */
    fun evictIfNeeded() {
        try {
            val files = cacheDir.listFiles()?.sortedBy { it.lastModified() } ?: return
            val totalSize = files.sumOf { it.length() }
            if (totalSize > maxCacheBytes) {
                var freed = 0L
                for (file in files) {
                    if (totalSize - freed <= maxCacheBytes * 0.8) break
                    val size = file.length()
                    if (file.delete()) freed += size
                }
            }
        } catch (_: Exception) {}
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Tile loader
// ──────────────────────────────────────────────────────────────────────────────

class TileLoader(
    private val dispatcherProvider: DispatcherProvider,
    private val maxConcurrent: Int = 4,
) {
    val cache = TileCache()
    private val diskCache = DiskTileCache()
    private val semaphore = Semaphore(maxConcurrent)

    /** Current map style — determines which tile URL to fetch from. */
    var mapStyle: MapStyle = MapStyle.STREET

    suspend fun loadTile(z: Int, x: Int, y: Int): ImageBitmap? {
        val key = "${mapStyle.name.lowercase()}/$z/$x/$y"
        cache.get(key)?.let {
            return it
        }
        semaphore.acquire()
        return try {
            val bitmap =
                withContext(dispatcherProvider.io) {
                    val diskBytes = diskCache.get(z, x, y, mapStyle)
                    if (diskBytes != null) {
                        try {
                            SkiaImage.makeFromEncoded(diskBytes).toComposeImageBitmap()
                        } catch (_: Exception) {
                            null
                        }
                    } else {
                        fetchTile(z, x, y)
                    }
                }
            if (bitmap != null) cache.put(key, bitmap)
            bitmap
        } finally {
            semaphore.release()
        }
    }

    /**
     * Compute which tiles are visible at the given camera position and zoom.
     * Uses [baseZoom] (floor of fractional zoom) to select tiles, then computes
     * the wider viewport that results from the fractional scale factor.
     */
    fun visibleTiles(
        centerLat: Double,
        centerLon: Double,
        zoom: Double,
        viewWidth: Int,
        viewHeight: Int,
        tileSize: Int = 256,
    ): List<Triple<Int, Int, Int>> {
        val baseZoom = floor(zoom).toInt()
        val (cx, cy) = MapTileRenderer.latLonToTile(centerLat, centerLon, zoom)
        val fractionalScale = 2.0.pow(zoom - baseZoom)
        // The display tile size at fractional zoom
        val displayTileSize = tileSize * TILE_SCALE * fractionalScale
        // But we need tiles at baseZoom that cover the viewport
        // At baseZoom, each tile covers displayTileSize fractional pixels
        // The viewport in display pixels is viewWidth x viewHeight
        // In baseZoom tiles, we need ceil(viewWidth / displayTileSize) + 2 tiles across
        val spanX = ceil(viewWidth.toDouble() / displayTileSize).toInt() / 2 + 1
        val spanY = ceil(viewHeight.toDouble() / displayTileSize).toInt() / 2 + 1
        val n = 2.0.pow(baseZoom).toInt()
        val tiles = mutableListOf<Triple<Int, Int, Int>>()
        for (tx in (cx - spanX)..(cx + spanX)) {
            for (ty in (cy - spanY)..(cy + spanY)) {
                if (tx in 0 until n && ty in 0 until n) tiles.add(Triple(baseZoom, tx, ty))
            }
        }
        return tiles
    }

    /**
     * Compute which tiles at [targetZoom] cover the same viewport as the currently
     * visible tiles at [currentZoom]. Used for prefetching adjacent zoom levels.
     */
    fun adjacentZoomTiles(
        centerLat: Double,
        centerLon: Double,
        currentZoom: Double,
        targetZoom: Int,
        viewWidth: Int,
        viewHeight: Int,
        tileSize: Int = 256,
    ): List<Triple<Int, Int, Int>> {
        if (targetZoom < 2 || targetZoom > 18) return emptyList()
        val (cx, cy) = MapTileRenderer.latLonToTile(centerLat, centerLon, targetZoom.toDouble())
        // At targetZoom, use native tile size for display
        val displayTileSize = tileSize * TILE_SCALE
        val spanX = ceil(viewWidth.toDouble() / displayTileSize).toInt() / 2 + 1
        val spanY = ceil(viewHeight.toDouble() / displayTileSize).toInt() / 2 + 1
        val n = 2.0.pow(targetZoom).toInt()
        val tiles = mutableListOf<Triple<Int, Int, Int>>()
        for (tx in (cx - spanX)..(cx + spanX)) {
            for (ty in (cy - spanY)..(cy + spanY)) {
                if (tx in 0 until n && ty in 0 until n) tiles.add(Triple(targetZoom, tx, ty))
            }
        }
        return tiles
    }

    private fun fetchTile(z: Int, x: Int, y: Int): ImageBitmap? {
        val urlStr =
            when (mapStyle) {
                MapStyle.STREET -> "https://tile.openstreetmap.org/$z/$x/$y.png"
                MapStyle.SATELLITE ->
                    "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$z/$y/$x"
            }
        return try {
            val url = URL(urlStr)
            val conn =
                (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "PetrieImageImporter/1.0")
                    connectTimeout = 8_000
                    readTimeout = 8_000
                }
            conn.inputStream.use { stream ->
                val bytes = stream.readBytes()
                conn.disconnect()
                diskCache.put(z, x, y, bytes, mapStyle)
                diskCache.evictIfNeeded()
                SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Prefetch tiles at adjacent zoom levels (one above and one below).
     * These are loaded asynchronously to make zoom transitions feel instant.
     */
    fun prefetchAdjacentZoomTiles(
        centerLat: Double,
        centerLon: Double,
        zoom: Double,
        viewWidth: Int,
        viewHeight: Int,
        tileSize: Int = 256,
        scope: CoroutineScope,
    ) {
        val baseZoom = floor(zoom).toInt()
        val adjacentZooms = listOfNotNull(baseZoom - 1, baseZoom + 1).filter { it in 2..18 }
        for (adjZoom in adjacentZooms) {
            val tiles =
                adjacentZoomTiles(centerLat, centerLon, zoom, adjZoom, viewWidth, viewHeight, tileSize)
            for ((z, x, y) in tiles) {
                val key = "${mapStyle.name.lowercase()}/$z/$x/$y"
                if (cache.get(key) != null) continue
                scope.launch { loadTile(z, x, y) }
            }
        }
    }

    /**
     * Prefetch tiles at zoom+1 that cover the viewport region around the
     * mouse pointer, for faster zoom-in transitions.
     */
    fun prefetchHoverRegion(
        centerLat: Double,
        centerLon: Double,
        zoom: Double,
        hoverGeoLat: Double,
        hoverGeoLon: Double,
        viewWidth: Int,
        viewHeight: Int,
        tileSize: Int = 256,
        scope: CoroutineScope,
    ) {
        val targetZoom = (floor(zoom).toInt() + 1).coerceIn(2, 18)
        // Fetch tiles around the hover point at the target zoom
        val (hx, hy) = MapTileRenderer.latLonToTile(hoverGeoLat, hoverGeoLon, targetZoom.toDouble())
        val n = 2.0.pow(targetZoom).toInt()
        val radius = 2 // Prefetch a 5x5 grid around the hover point
        for (tx in (hx - radius)..(hx + radius)) {
            for (ty in (hy - radius)..(hy + radius)) {
                if (tx in 0 until n && ty in 0 until n) {
                    val key = "${mapStyle.name.lowercase()}/$targetZoom/$tx/$ty"
                    if (cache.get(key) != null) continue
                    scope.launch { loadTile(targetZoom, tx, ty) }
                }
            }
        }
    }

    /** Clear memory cache when switching map styles. */
    fun clearMemoryCache() {
        cache.clear()
    }

    companion object {
        /**
         * Tiles are scaled up by this factor for display. Higher values mean fewer tiles
         * needed per viewport (faster loading) but blurrier display. 4 = each 256px tile
         * renders at 1024px display pixels.
         */
        const val TILE_SCALE = 4

        /**
         * Zoom sensitivity: how much fractional zoom change per unit of scroll delta.
         * Typical mouse wheel notch is ~120 units of scroll, giving ~0.36 zoom levels
         * per notch at this sensitivity.
         */
        const val ZOOM_SENSITIVITY = 0.003

        /** Minimum zoom level. */
        const val MIN_ZOOM = 2.0

        /** Maximum zoom level. */
        const val MAX_ZOOM = 18.0

        /** Duration for animated zoom transitions (ms). */
        const val ZOOM_ANIMATION_MS = 250

        /** Hover delay before prefetching zoom-in tiles (ms). */
        const val HOVER_PREFETCH_DELAY_MS = 300L
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Map camera state — holds all mutable map state
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Holds the mutable state for the map viewport.
 *
 * Zoom is stored as [Double] to support **fractional zoom** (e.g., 12.35) for
 * continuous smooth zooming. Tiles are fetched at `floor(zoom)` and scaled by
 * `2^(zoom - floor(zoom))` to fill the fractional gap.
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
// OsmMapView
// ──────────────────────────────────────────────────────────────────────────────

/** Data class used as snapshotFlow key to detect when tile loading should occur. */
private data class TileLoadKey(
    val lat: Double,
    val lon: Double,
    val zoom: Double,
    val width: Int,
    val height: Int,
    val style: MapStyle,
)

/**
 * A smooth-zooming OpenStreetMap tiled-map composable for Compose Desktop.
 *
 * ## Smooth Zoom
 *
 * Uses **fractional zoom** (e.g., 12.35) so the map zooms continuously instead of
 * snapping between discrete levels. Tiles from the base zoom level (floor of fractional
 * zoom) are scale-transformed by `2^(zoom - floor(zoom))` to fill intermediate positions.
 * This gives a Google Maps–like smooth zoom experience.
 *
 * ## Zoom Behavior
 *
 * - **Scroll wheel**: Each scroll event adds/subtracts a small fractional zoom change
 *   (controlled by [TileLoader.ZOOM_SENSITIVITY]). The map zooms smoothly toward the
 *   mouse pointer position — the geographic point under the cursor stays fixed.
 * - **Double-click**: Animated zoom-in (250ms ease-out) toward the click position.
 * - **+/- buttons**: Animated zoom centered on the map center.
 * - **Drag**: Pan with latitude-corrected pixel scaling.
 *
 * ## Performance
 *
 * - Tiles are rendered at [TileLoader.TILE_SCALE]× their source size to reduce
 *   network requests. Adjacent zoom levels are prefetched asynchronously.
 * - Hover prefetching: when the mouse lingers in an area for 300ms, tiles at zoom+1
 *   around that location are prefetched so zoom-in transitions are instant.
 * - Dual-layer cache: in-memory LRU (150 tiles) + disk (24h TTL, 50MB max).
 *
 * @param modifier Layout modifier
 * @param initialLat Starting latitude
 * @param initialLon Starting longitude
 * @param initialZoom Starting zoom level (2.0–18.0, fractional supported)
 * @param onMapClick Callback with (lat, lon) when the user clicks the map
 * @param pinLocation Optional (lat, lon) for a red pin marker
 * @param searchResults Location results to show as blue markers on the map
 * @param selectedResult Highlighted search result (yellow ring)
 * @param initialMapStyle Starting map style (street or satellite)
 * @param onMapStyleChanged Callback when map style changes
 * @param onZoomChanged Callback when zoom level changes (receives fractional value)
 * @param dispatcherProvider Provides IO dispatcher for tile loading
 * @param coroutineScope Scope for launching tile-load and animation coroutines
 */
@Composable
fun OsmMapView(
    modifier: Modifier = Modifier,
    initialLat: Double = 39.0,
    initialLon: Double = -78.0,
    initialZoom: Double = 5.0,
    onMapClick: (lat: Double, lon: Double) -> Unit = { _, _ -> },
    pinLocation: Pair<Double, Double>? = null,
    searchResults: List<LocationResult> = emptyList(),
    selectedResult: LocationResult? = null,
    initialMapStyle: MapStyle = MapStyle.STREET,
    onMapStyleChanged: (MapStyle) -> Unit = {},
    onZoomChanged: (Double) -> Unit = {},
    dispatcherProvider: DispatcherProvider,
    coroutineScope: CoroutineScope,
) {
    val camera = remember { MapCameraState(initialLat, initialLon, initialZoom) }
    var mapStyle by remember { mutableStateOf(initialMapStyle) }

    // Sync external center/zoom changes (e.g. selecting a search result or preset)
    LaunchedEffect(initialLat) { camera.centerLat = initialLat }
    LaunchedEffect(initialLon) { camera.centerLon = initialLon }
    LaunchedEffect(initialZoom) {
        camera.zoom = initialZoom
    }

    val tileLoader = remember { TileLoader(dispatcherProvider) }
    val sourceTileSize = 256
    val textMeasurer = rememberTextMeasurer()

    // Cache text measurements so they don't re-execute every draw frame
    val zoomTextStyle = remember { TextStyle(color = Color.White, fontSize = 10.sp) }
    val attrTextStyle = remember { TextStyle(color = Color(0xCCFFFFFF.toInt()), fontSize = 8.sp) }

    // Viewport dimensions — written from draw scope via SizeTracker, read as state
    val sizeTracker = remember { SizeTracker() }

    // Hover-based prefetching state
    var hoverGeoLat by remember { mutableDoubleStateOf(Double.NaN) }
    var hoverGeoLon by remember { mutableDoubleStateOf(Double.NaN) }
    var hoverPrefetchJob by remember { mutableStateOf<Job?>(null) }

    // Zoom animation state for button/double-click zooms
    val zoomAnimatable = remember { Animatable(initialZoom.toFloat()) }

    // Observe zoom animation and apply to camera
    LaunchedEffect(zoomAnimatable.value) {
        val newZoom = zoomAnimatable.value.toDouble()
        if (newZoom != camera.zoom) {
            camera.zoom = newZoom
            onZoomChanged(newZoom)
        }
    }

    // Sync map style to tile loader
    LaunchedEffect(mapStyle) {
        tileLoader.mapStyle = mapStyle
        tileLoader.clearMemoryCache()
        onMapStyleChanged(mapStyle)
    }

    // ── Tile loading ──────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        snapshotFlow {
            TileLoadKey(
                camera.centerLat,
                camera.centerLon,
                camera.zoom,
                sizeTracker.width,
                sizeTracker.height,
                mapStyle,
            )
        }.collect { key ->
            if (key.width <= 0 || key.height <= 0) return@collect
            val baseZoom = floor(key.zoom).toInt()
            val tiles = tileLoader.visibleTiles(
                key.lat, key.lon, key.zoom, key.width, key.height, sourceTileSize
            )
            for ((tz, tx, ty) in tiles) {
                val tileKey = "${key.style.name.lowercase()}/$tz/$tx/$ty"
                if (tileLoader.cache.get(tileKey) != null) continue
                launch {
                    if (tileLoader.loadTile(tz, tx, ty) != null) {
                        sizeTracker.invalidate()
                    }
                }
            }
            // Also prefetch adjacent zoom levels
            tileLoader.prefetchAdjacentZoomTiles(
                key.lat, key.lon, key.zoom, key.width, key.height, sourceTileSize, this
            )
            // If hovering, prefetch zoom+1 around the hover point
            if (!hoverGeoLat.isNaN() && !hoverGeoLon.isNaN()) {
                tileLoader.prefetchHoverRegion(
                    key.lat, key.lon, key.zoom,
                    hoverGeoLat, hoverGeoLon,
                    key.width, key.height, sourceTileSize, this
                )
            }
        }
    }

    // ── Helper: zoom toward a screen point ──────────────────────────
    // Defined inside composable so it can read camera state directly.

    fun zoomAtPointer(pointerX: Float, pointerY: Float, oldZoom: Double, newZoom: Double, viewW: Float, viewH: Float) {
        // Convert pointer position to geographic coordinates at old zoom
        val (centerPx, centerPy) =
            MapTileRenderer.latLonToPixelOffset(camera.centerLat, camera.centerLon, oldZoom, sourceTileSize)
        // Pointer offset from center in display pixels → world pixels at old zoom
        val pointerOffX = (pointerX - viewW / 2f) / TileLoader.TILE_SCALE
        val pointerOffY = (pointerY - viewH / 2f) / TileLoader.TILE_SCALE
        val pointerWorldPx = centerPx + pointerOffX
        val pointerWorldPy = centerPy + pointerOffY
        val (pointerLat, pointerLon) =
            MapTileRenderer.pixelOffsetToLatLon(pointerWorldPx, pointerWorldPy, oldZoom, sourceTileSize)

        // Where would that geographic point be at the new zoom?
        val (newPointerPx, newPointerPy) =
            MapTileRenderer.latLonToPixelOffset(pointerLat, pointerLon, newZoom, sourceTileSize)

        // Adjust center so the geographic point stays under the pointer
        val newOffX = (pointerX - viewW / 2f) / TileLoader.TILE_SCALE
        val newOffY = (pointerY - viewH / 2f) / TileLoader.TILE_SCALE
        val newCenterPx = newPointerPx - newOffX
        val newCenterPy = newPointerPy - newOffY
        val (newLat, newLon) =
            MapTileRenderer.pixelOffsetToLatLon(newCenterPx, newCenterPy, newZoom, sourceTileSize)
        camera.centerLat = MapTileRenderer.clampLat(newLat)
        camera.centerLon = MapTileRenderer.clampLon(newLon)
        camera.zoom = newZoom
        onZoomChanged(newZoom)
    }

    /** Animated zoom centered on the map center (for +/- buttons). */
    fun animatedZoomCenter(targetZoom: Double) {
        coroutineScope.launch {
            zoomAnimatable.animateTo(
                targetZoom.toFloat(),
                animationSpec = tween(
                    durationMillis = TileLoader.ZOOM_ANIMATION_MS,
                    easing = FastOutSlowInEasing,
                ),
            )
        }
    }

    /** Animated zoom toward a pointer position (for double-click). */
    fun animatedZoomAtPointer(pointerX: Float, pointerY: Float, oldZoom: Double, delta: Double, viewW: Float, viewH: Float) {
        // First apply the center adjustment for the target zoom
        val newZoom = (oldZoom + delta).coerceIn(TileLoader.MIN_ZOOM, TileLoader.MAX_ZOOM)
        zoomAtPointer(pointerX, pointerY, oldZoom, newZoom, viewW, viewH)
        // Then animate from old zoom to new zoom
        coroutineScope.launch {
            zoomAnimatable.snapTo(oldZoom.toFloat())
            // The camera was already set to newZoom center, so just animate zoom
            zoomAnimatable.animateTo(
                newZoom.toFloat(),
                animationSpec = tween(
                    durationMillis = TileLoader.ZOOM_ANIMATION_MS,
                    easing = FastOutSlowInEasing,
                ),
            )
        }
    }

    Box(modifier = modifier) {
        // ── Canvas map ──────────────────────────────────────────────────
        Canvas(
            modifier =
                Modifier.fillMaxSize()
                    // ── Scroll zoom: direct fractional zoom toward pointer ────────
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type == PointerEventType.Scroll) {
                                    val change = event.changes.firstOrNull() ?: continue
                                    val scrollDelta = change.scrollDelta.y
                                    if (scrollDelta != 0f) {
                                        val zoomDelta = -scrollDelta * TileLoader.ZOOM_SENSITIVITY
                                        val oldZoom = camera.zoom
                                        val newZoom =
                                            (oldZoom + zoomDelta).coerceIn(
                                                TileLoader.MIN_ZOOM,
                                                TileLoader.MAX_ZOOM,
                                            )
                                        if (newZoom != oldZoom) {
                                            zoomAtPointer(
                                                change.position.x,
                                                change.position.y,
                                                oldZoom,
                                                newZoom,
                                                size.width.toFloat(),
                                                size.height.toFloat(),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // ── Drag to pan ────────────────────────────────────────────
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            // Use the current fractional zoom for pixel-size calculation
                            val n = 2.0.pow(camera.zoom)
                            val pixelSize = 360.0 / (n * sourceTileSize * TileLoader.TILE_SCALE)
                            val lonShift = -dragAmount.x * pixelSize
                            val latShift = dragAmount.y * pixelSize * cos(Math.toRadians(camera.centerLat))
                            camera.centerLat = MapTileRenderer.clampLat(camera.centerLat + latShift)
                            camera.centerLon = MapTileRenderer.clampLon(camera.centerLon + lonShift)
                        }
                    }
                    // ── Double-click to zoom in, single-click to place pin ─────
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { offset ->
                                val newZoom =
                                    (camera.zoom + 1.0).coerceIn(
                                        TileLoader.MIN_ZOOM,
                                        TileLoader.MAX_ZOOM,
                                    )
                                animatedZoomAtPointer(
                                    offset.x, offset.y,
                                    camera.zoom,
                                    1.0,
                                    size.width.toFloat(),
                                    size.height.toFloat(),
                                )
                            },
                            onTap = { offset ->
                                val vW = size.width.toDouble()
                                val vH = size.height.toDouble()
                                val (centerPx, centerPy) =
                                    MapTileRenderer.latLonToPixelOffset(
                                        camera.centerLat,
                                        camera.centerLon,
                                        camera.zoom,
                                        sourceTileSize,
                                    )
                                val clickWorldPx = centerPx + (offset.x - vW / 2.0) / TileLoader.TILE_SCALE
                                val clickWorldPy = centerPy + (offset.y - vH / 2.0) / TileLoader.TILE_SCALE
                                val (lat, lon) =
                                    MapTileRenderer.pixelOffsetToLatLon(
                                        clickWorldPx,
                                        clickWorldPy,
                                        camera.zoom,
                                        sourceTileSize,
                                    )
                                onMapClick(lat, lon)
                            },
                        )
                    }
                    // ── Mouse move: track hover position for prefetch ───────
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type == PointerEventType.Move) {
                                    val change = event.changes.firstOrNull() ?: continue
                                    val vW = size.width.toDouble()
                                    val vH = size.height.toDouble()
                                    if (vW <= 0 || vH <= 0) continue
                                    val (centerPx, centerPy) =
                                        MapTileRenderer.latLonToPixelOffset(
                                            camera.centerLat,
                                            camera.centerLon,
                                            camera.zoom,
                                            sourceTileSize,
                                        )
                                    val hoverWorldPx = centerPx + (change.position.x - vW / 2.0) / TileLoader.TILE_SCALE
                                    val hoverWorldPy = centerPy + (change.position.y - vH / 2.0) / TileLoader.TILE_SCALE
                                    val (lat, lon) =
                                        MapTileRenderer.pixelOffsetToLatLon(
                                            hoverWorldPx,
                                            hoverWorldPy,
                                            camera.zoom,
                                            sourceTileSize,
                                        )
                                    hoverGeoLat = lat
                                    hoverGeoLon = lon
                                    // Schedule prefetch after delay
                                    hoverPrefetchJob?.cancel()
                                    hoverPrefetchJob = coroutineScope.launch {
                                        delay(TileLoader.HOVER_PREFETCH_DELAY_MS)
                                        tileLoader.prefetchHoverRegion(
                                            camera.centerLat,
                                            camera.centerLon,
                                            camera.zoom,
                                            lat,
                                            lon,
                                            vW.toInt(),
                                            vH.toInt(),
                                            sourceTileSize,
                                            this,
                                        )
                                    }
                                }
                            }
                        }
                    }
        ) {
            // Update viewport tracking
            sizeTracker.update(size.width.toInt(), size.height.toInt())

            if (size.width <= 0f || size.height <= 0f) return@Canvas

            drawRect(Color(0xFFE0E0E0))

            // ── Draw tiles with fractional zoom scaling ──────────────────
            val baseZoom = floor(camera.zoom).toInt()
            val fractionalPart = camera.zoom - baseZoom
            val scaleFactor = 2.0.pow(fractionalPart)

            val (centerPx, centerPy) =
                MapTileRenderer.latLonToPixelOffset(
                    camera.centerLat, camera.centerLon, camera.zoom, sourceTileSize,
                )
            val vW = size.width.toDouble()
            val vH = size.height.toDouble()

            // Viewport bounds in world-pixel space at fractional zoom
            val viewLeft = centerPx - vW / 2.0 / TileLoader.TILE_SCALE
            val viewTop = centerPy - vH / 2.0 / TileLoader.TILE_SCALE

            // Which baseZoom tiles cover the viewport?
            // Convert fractional-zoom viewport to baseZoom tile space
            val baseViewLeft = viewLeft / scaleFactor
            val baseViewTop = viewTop / scaleFactor
            val baseViewRight =
                (centerPx + vW / 2.0 / TileLoader.TILE_SCALE) / scaleFactor
            val baseViewBottom =
                (centerPy + vH / 2.0 / TileLoader.TILE_SCALE) / scaleFactor

            val n = 2.0.pow(baseZoom).toInt()
            val minTileX = max(0, floor(baseViewLeft / sourceTileSize).toInt())
            val minTileY = max(0, floor(baseViewTop / sourceTileSize).toInt())
            val maxTileX = min(n - 1, floor(baseViewRight / sourceTileSize).toInt())
            val maxTileY = min(n - 1, floor(baseViewBottom / sourceTileSize).toInt())

            val scaledDisplaySize = (sourceTileSize * TileLoader.TILE_SCALE * scaleFactor).toFloat()

            for (tx in minTileX..maxTileX) {
                for (ty in minTileY..maxTileY) {
                    val key = "${mapStyle.name.lowercase()}/$baseZoom/$tx/$ty"
                    val bitmap = tileLoader.cache.get(key)

                    // Tile position in fractional-zoom screen space
                    val tileFracLeft = tx * sourceTileSize * scaleFactor
                    val tileFracTop = ty * sourceTileSize * scaleFactor

                    // Offset from viewport top-left (in fractional-zoom world pixels) → screen
                    val screenLeft =
                        ((tileFracLeft - viewLeft) * TileLoader.TILE_SCALE).toFloat()
                    val screenTop =
                        ((tileFracTop - viewTop) * TileLoader.TILE_SCALE).toFloat()

                    if (bitmap != null) {
                        drawImage(
                            image = bitmap,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(bitmap.width, bitmap.height),
                            dstOffset = IntOffset(screenLeft.toInt(), screenTop.toInt()),
                            dstSize = IntSize(scaledDisplaySize.toInt(), scaledDisplaySize.toInt()),
                        )
                    } else {
                        drawRect(
                            Color(0xFFDADADA),
                            topLeft = Offset(screenLeft, screenTop),
                            size = Size(scaledDisplaySize, scaledDisplaySize),
                        )
                    }
                }
            }

            // ── Search result markers ───────────────────────────────────────
            for (result in searchResults) {
                val (px, py) =
                    MapTileRenderer.latLonToPixelOffset(
                        result.latitude,
                        result.longitude,
                        camera.zoom,
                        sourceTileSize,
                    )
                val screenX = ((px - viewLeft) * TileLoader.TILE_SCALE).toFloat()
                val screenY = ((py - viewTop) * TileLoader.TILE_SCALE).toFloat()
                if (
                    screenX < -50 ||
                        screenX > size.width + 50 ||
                        screenY < -50 ||
                        screenY > size.height + 50
                )
                    continue
                val isSelected =
                    selectedResult != null &&
                        result.latitude == selectedResult.latitude &&
                        result.longitude == selectedResult.longitude
                if (isSelected)
                    drawCircle(
                        color = Color.Yellow,
                        radius = 14f,
                        center = Offset(screenX, screenY),
                    )
                drawCircle(
                    color = Color(0xFF2196F3),
                    radius = 8f,
                    center = Offset(screenX, screenY),
                )
                drawCircle(color = Color.White, radius = 4f, center = Offset(screenX, screenY))
                val label = result.name.take(20)
                val textLayout = textMeasurer.measure(label, zoomTextStyle)
                drawRoundRect(
                    color = Color(0xDD000000.toInt()),
                    topLeft =
                        Offset(screenX + 10f, screenY - textLayout.size.height.toFloat() / 2f - 1f),
                    size =
                        Size(
                            textLayout.size.width.toFloat() + 6f,
                            textLayout.size.height.toFloat() + 2f,
                        ),
                )
                drawText(
                    textLayoutResult = textLayout,
                    topLeft = Offset(screenX + 13f, screenY - textLayout.size.height.toFloat() / 2f),
                )
            }

            // ── Pin marker ───────────────────────────────────────────────────
            if (pinLocation != null) {
                val (pinLat, pinLon) = pinLocation
                val (pinPx, pinPy) =
                    MapTileRenderer.latLonToPixelOffset(pinLat, pinLon, camera.zoom, sourceTileSize)
                drawPinMarker(
                    ((pinPx - viewLeft) * TileLoader.TILE_SCALE).toFloat(),
                    ((pinPy - viewTop) * TileLoader.TILE_SCALE).toFloat(),
                )
            }

            // ── Zoom indicator (show fractional zoom) ───────────────────────
            val zoomText = "z${"%.1f".format(camera.zoom)}"
            val zoomLayout = textMeasurer.measure(zoomText, zoomTextStyle)
            drawRoundRect(
                Color(0xB3000000.toInt()),
                topLeft = Offset(size.width - zoomLayout.size.width.toFloat() - 46f, 6f),
                size =
                    Size(
                        zoomLayout.size.width.toFloat() + 12f,
                        zoomLayout.size.height.toFloat() + 4f,
                    ),
            )
            drawText(
                textLayoutResult = zoomLayout,
                topLeft = Offset(size.width - zoomLayout.size.width.toFloat() - 40f, 8f),
            )

            // ── Attribution ──────────────────────────────────────────────────
            val attrText =
                when (mapStyle) {
                    MapStyle.STREET -> "© OpenStreetMap"
                    MapStyle.SATELLITE -> "© Esri"
                }
            val attrLayout = textMeasurer.measure(attrText, attrTextStyle)
            drawText(
                textLayoutResult = attrLayout,
                topLeft = Offset(4f, size.height - attrLayout.size.height.toFloat() - 4f),
            )
        }

        // ── Floating zoom + style overlay ────────────────────────────────
        Column(
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 8.dp, top = 8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            // Map style toggle
            FloatingActionButton(
                onClick = {
                    mapStyle =
                        when (mapStyle) {
                            MapStyle.STREET -> MapStyle.SATELLITE
                            MapStyle.SATELLITE -> MapStyle.STREET
                        }
                },
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.surface,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp),
            ) {
                Text(
                    when (mapStyle) {
                        MapStyle.STREET -> "🛰"
                        MapStyle.SATELLITE -> "🗺"
                    },
                    fontSize = 14.sp,
                )
            }

            Spacer(Modifier.size(6.dp))

            // Zoom in
            FloatingActionButton(
                onClick = {
                    val newZoom =
                        (camera.zoom + 1.0).coerceIn(TileLoader.MIN_ZOOM, TileLoader.MAX_ZOOM)
                    animatedZoomCenter(newZoom)
                },
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.surface,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp),
            ) {
                Text("+", fontSize = 18.sp)
            }

            Spacer(Modifier.size(4.dp))

            // Zoom out
            FloatingActionButton(
                onClick = {
                    val newZoom =
                        (camera.zoom - 1.0).coerceIn(TileLoader.MIN_ZOOM, TileLoader.MAX_ZOOM)
                    animatedZoomCenter(newZoom)
                },
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.surface,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp),
            ) {
                Text("−", fontSize = 18.sp)
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// SizeTracker — bridges Canvas draw-scope size to Compose state
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Tracks viewport width/height from inside [DrawScope] and exposes them as observable state so
 * that [snapshotFlow] can react to size changes.
 *
 * Also provides [invalidate] which increments an internal counter, forcing a recomposition
 * when tiles finish loading.
 */
@Stable
private class SizeTracker {
    var width by mutableStateOf(0)
    var height by mutableStateOf(0)

    fun update(w: Int, h: Int) {
        if (w != width || h != height) {
            width = w
            height = h
        }
    }

    /** Increment to force a recomposition (e.g. when a new tile finishes loading). */
    var revision by mutableStateOf(0)
        private set

    fun invalidate() {
        revision++
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Helper
// ──────────────────────────────────────────────────────────────────────────────

private fun DrawScope.drawPinMarker(x: Float, y: Float) {
    drawCircle(color = Color(0xFFF44336), radius = 10f, center = Offset(x, y - 12f))
    drawCircle(color = Color.White, radius = 5f, center = Offset(x, y - 12f))
    val path =
        Path().apply {
            moveTo(x, y)
            lineTo(x - 7f, y - 8f)
            lineTo(x + 7f, y - 8f)
            close()
        }
    drawPath(path = path, color = Color(0xFFF44336))
}