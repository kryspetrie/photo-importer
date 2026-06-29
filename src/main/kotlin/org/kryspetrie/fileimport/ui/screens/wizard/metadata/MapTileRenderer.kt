@file:Suppress("TooManyFunctions", "MagicNumber")
@file:OptIn(kotlinx.coroutines.FlowPreview::class)

package org.kryspetrie.fileimport.ui.screens.wizard.metadata

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
import androidx.compose.material3.CircularProgressIndicator
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
import kotlin.math.sinh
import kotlin.math.tan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
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
        val latRad = atan(sinh(PI * (1.0 - 2.0 * py / (n * tileSize))))
        val lat = latRad * 180.0 / PI
        return Pair(lat, lon)
    }

    fun clampLat(lat: Double): Double = lat.coerceIn(-85.05, 85.05)

    fun clampLon(lon: Double): Double = ((lon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
}

// ──────────────────────────────────────────────────────────────────────────────
// LRU tile cache (in-memory) with viewport-aware eviction
// ──────────────────────────────────────────────────────────────────────────────

/**
 * In-memory tile cache that prioritizes keeping tiles near the current viewport.
 *
 * Each [MapStyle] has its own independent TileCache instance with its own 2048-tile
 * budget, so street and satellite tiles never evict each other. When eviction is
 * needed, tiles farthest from the viewport center (in tile-space distance) are
 * removed first. This ensures that when we reach capacity, tiles relevant to the
 * current map view are retained while tiles from distant regions are evicted first.
 */
class TileCache(private val maxTiles: Int = 2048) {
    private val cache =
        object : LinkedHashMap<String, ImageBitmap>(64, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, ImageBitmap>,
            ): Boolean {
                // Don't auto-evict; we use viewport-aware eviction instead
                return false
            }
        }

    /** Current viewport center in tile coordinates, used for eviction priority. */
    @Volatile
    private var viewportCenterZ: Int = 0
    @Volatile
    private var viewportCenterX: Double = 0.0
    @Volatile
    private var viewportCenterY: Double = 0.0

    /** Update the viewport center for eviction priority. Called when the camera moves. */
    fun updateViewportCenter(z: Int, x: Double, y: Double) {
        viewportCenterZ = z
        viewportCenterX = x
        viewportCenterY = y
    }

    @Synchronized fun get(key: String): ImageBitmap? = cache[key]

    @Synchronized fun put(key: String, bitmap: ImageBitmap) {
        cache[key] = bitmap
        if (cache.size > maxTiles) {
            evictFarTiles()
        }
    }

    @Synchronized fun clear() {
        cache.clear()
    }

    fun size(): Int = cache.size

    /**
     * Evict tiles that are farthest from the viewport center when the cache exceeds capacity.
     * This keeps tiles relevant to the current map view while discarding tiles from
     * areas the user has scrolled away from.
     * Key format: "z/x/y" (style is not included since each cache is style-specific).
     */
    private fun evictFarTiles() {
        if (cache.size <= maxTiles * 9 / 10) return // Only evict down to 90% capacity
        val cz = viewportCenterZ
        val cx = viewportCenterX
        val cy = viewportCenterY
        val toRemove = mutableListOf<String>()
        val sorted = cache.entries.sortedByDescending { entry ->
            val parts = entry.key.split("/")
            if (parts.size != 3) return@sortedByDescending Long.MAX_VALUE
            val tz = parts[0].toIntOrNull() ?: return@sortedByDescending Long.MAX_VALUE
            val tx = parts[1].toDoubleOrNull() ?: return@sortedByDescending Long.MAX_VALUE
            val ty = parts[2].toDoubleOrNull() ?: return@sortedByDescending Long.MAX_VALUE
            // Same zoom level is highest priority; adjacent zooms are lower
            val zoomPenalty = if (tz == cz) 0L else (kotlin.math.abs(tz - cz) * 100L)
            // Distance in tile coordinates at the same zoom
            val dist = ((tx - cx) * (tx - cx) + (ty - cy) * (ty - cy)).toLong()
            zoomPenalty + dist
        }
        val targetRemove = cache.size - maxTiles * 9 / 10
        for (i in 0 until targetRemove.coerceAtLeast(1)) {
            sorted.getOrNull(i)?.key?.let { toRemove.add(it) }
            if (cache.size - toRemove.size <= maxTiles * 9 / 10) break
        }
        for (key in toRemove) {
            cache.remove(key)
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Disk tile cache with viewport-aware eviction
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Persistent disk cache for map tiles with viewport-aware eviction.
 *
 * Each map style (street, satellite) gets its own subdirectory with an independent
 * size limit (1 GB each). When a style's cache exceeds its budget, tiles far from
 * the current viewport are evicted first (measured by tile-coordinate distance at
 * the same zoom, with a penalty for different zoom levels). This ensures cache
 * space is used for tiles the user is likely to need next rather than tiles from
 * distant regions, and that street and satellite tiles never evict each other.
 */
class DiskTileCache(cacheDir: File = File(Platform.cacheDir, "map-tiles")) {
    private val cacheDir: File
    /** Maximum age in milliseconds before a cached tile is considered stale (7 days). */
    private val maxAgeMs: Long = 7L * 24 * 60 * 60 * 1000
    /** Maximum disk cache size in bytes per style (1 GB each for street and satellite). */
    private val maxCacheBytesPerStyle: Long = 1024L * 1024 * 1024

    /** Current viewport center for eviction priority. */
    @Volatile private var vpZ: Int = 0
    @Volatile private var vpX: Double = 0.0
    @Volatile private var vpY: Double = 0.0

    private val evictionLock = Any()
    private var evictionScheduled = false

    init {
        this.cacheDir = cacheDir
        if (!this.cacheDir.exists()) this.cacheDir.mkdirs()
        // Ensure per-style subdirectories exist
        for (style in MapStyle.entries) {
            val styleDir = File(cacheDir, style.name.lowercase())
            if (!styleDir.exists()) styleDir.mkdirs()
        }
    }

    /** Update viewport center for eviction priority. */
    fun updateViewportCenter(z: Int, x: Double, y: Double) {
        vpZ = z
        vpX = x
        vpY = y
    }

    /** Get the subdirectory for a given map style. */
    private fun styleDir(style: MapStyle): File {
        return File(cacheDir, style.name.lowercase())
    }

    fun get(z: Int, x: Int, y: Int, style: MapStyle = MapStyle.STREET): ByteArray? {
        val file = File(styleDir(style), "${z}_${x}_${y}.png")
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
            val dir = styleDir(style)
            if (!dir.exists()) dir.mkdirs()
            File(dir, "${z}_${x}_${y}.png").writeBytes(bytes)
        } catch (_: Exception) {}
    }

    /**
     * Schedule viewport-aware eviction if any style\'s cache exceeds capacity.
     * Each style has its own independent budget. Eviction runs asynchronously
     * to avoid blocking the UI. When over capacity, tiles farthest from the
     * current viewport center are evicted first.
     */
    fun evictIfNeeded() {
        synchronized(evictionLock) {
            if (evictionScheduled) return
            evictionScheduled = true
        }
        Thread {
            try {
                for (style in MapStyle.entries) {
                    doEvictionForStyle(style)
                }
            } finally {
                synchronized(evictionLock) {
                    evictionScheduled = false
                }
            }
        }.start()
    }

    private fun doEvictionForStyle(style: MapStyle) {
        try {
            val dir = styleDir(style)
            if (!dir.exists()) return
            val files = dir.listFiles() ?: return
            val totalSize = files.sumOf { it.length() }
            if (totalSize <= maxCacheBytesPerStyle) return

            val cz = vpZ
            val cx = vpX
            val cy = vpY
            // Parse tile coordinates from filenames and sort by distance from viewport
            val parsed = files.mapNotNull { file ->
                // Format: z_x_y.png
                val name = file.nameWithoutExtension
                val parts = name.split("_")
                if (parts.size != 3) return@mapNotNull null
                val tz = parts[0].toIntOrNull() ?: return@mapNotNull null
                val tx = parts[1].toIntOrNull() ?: return@mapNotNull null
                val ty = parts[2].toIntOrNull() ?: return@mapNotNull null
                val zPenalty = if (tz == cz) 0L else (kotlin.math.abs(tz - cz) * 100L)
                val dist = ((tx - cx) * (tx - cx).toLong() + (ty - cy) * (ty - cy).toLong())
                Triple(file, zPenalty + dist, file.length())
            }.sortedByDescending { it.second }

            var currentSize = totalSize
            val targetSize = (maxCacheBytesPerStyle * 0.8).toLong()
            for ((file, _, fileSize) in parsed) {
                if (currentSize <= targetSize) break
                if (file.delete()) currentSize -= fileSize
            }
        } catch (_: Exception) {}
    }
}
// Tile loader
// ──────────────────────────────────────────────────────────────────────────────

class TileLoader(
    private val dispatcherProvider: DispatcherProvider,
    private val maxConcurrent: Int = 8,
) {
    /** Per-style in-memory caches. Each style gets its own 2048-tile budget. */
    private val caches = MapStyle.entries.associateWith { TileCache() }
    private val diskCache = DiskTileCache()
    private val semaphore = Semaphore(maxConcurrent)

    /** Current map style — determines which tile URL to fetch from and which cache to use. */
    var mapStyle: MapStyle = MapStyle.STREET

    /** Get the in-memory cache for the current map style. */
    val cache: TileCache get() = caches[mapStyle]!!

    /** Update viewport center for cache eviction priority. Called when the camera moves. */
    fun updateViewportCenter(lat: Double, lon: Double, zoom: Double) {
        val baseZoom = floor(zoom).toInt()
        val (tx, ty) = MapTileRenderer.latLonToTile(lat, lon, zoom)
        val cx = tx.toDouble()
        val cy = ty.toDouble()
        // Update all caches so the active one has the best eviction
        for (c in caches.values) {
            c.updateViewportCenter(baseZoom, cx, cy)
        }
        diskCache.updateViewportCenter(baseZoom, cx, cy)
    }

    suspend fun loadTile(z: Int, x: Int, y: Int): ImageBitmap? {
        val key = "$z/$x/$y"
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
            if (bitmap != null) cache.put("$z/$x/$y", bitmap)
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
        val displayTileSize = tileSize * fractionalScale
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
        val displayTileSize = tileSize
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
                    connectTimeout = 5_000
                    readTimeout = 5_000
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
                val key = "$z/$x/$y"
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
                    val key = "$targetZoom/$tx/$ty"
                    if (cache.get(key) != null) continue
                    scope.launch { loadTile(targetZoom, tx, ty) }
                }
            }
        }
    }

    /** Clear the current style's memory cache. Other style caches are preserved. */
    fun clearMemoryCache() {
        cache.clear()
    }

    companion object {
        /**
         * Display scale for tiles. 1 = native pixel size (sharpest, most tiles per viewport).
         * Increase for fewer but blurrier tiles (e.g. 4 = each 256px tile renders at 1024px).
         */
        const val TILE_SCALE = 1

        /**
         * Zoom sensitivity: how much fractional zoom change per unit of scroll delta.
         * Typical mouse wheel notch is ~120 units of scroll, giving ~0.36 zoom levels
         * per notch at this sensitivity.
         */
        const val ZOOM_SENSITIVITY = 0.006

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
 * - Tiles are rendered at their native source size (256px). Adjacent zoom levels are prefetched asynchronously.
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
) {
    val coroutineScope = rememberCoroutineScope()
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

    // Last known screen position of the mouse pointer (for priority loading)
    var lastPointerX by remember { mutableStateOf(0f) }
    var lastPointerY by remember { mutableStateOf(0f) }

    // ── Zoom animation ──────────────────────────────────────────────────────
    // Uses time-based interpolation with delay() instead of Compose animation APIs.
    // Compose's MonotonicFrameClock is not reliably available in Compose Desktop AWT
    // contexts, so we avoid Animatable/animate() and use a simple frame loop instead.

    /** Active zoom animation job, so scroll can cancel it. */
    var zoomAnimationJob by remember { mutableStateOf<Job?>(null) }

    /** Animate zoom from current to targetZoom over ZOOM_ANIMATION_MS, using ease-in-out. */
    fun animateZoomTo(targetZoom: Double) {
        val startZoom = camera.zoom
        if (startZoom == targetZoom) return
        zoomAnimationJob?.cancel()
        zoomAnimationJob = coroutineScope.launch {
            val durationNs = TileLoader.ZOOM_ANIMATION_MS.toLong() * 1_000_000L
            val startTimeNs = System.nanoTime()
            val diff = targetZoom - startZoom
            while (true) {
                val elapsed = System.nanoTime() - startTimeNs
                val progress = (elapsed.toDouble() / durationNs).coerceIn(0.0, 1.0)
                // Ease-in-out (quadratic): slow start, fast middle, slow end
                val eased = if (progress < 0.5) {
                    2.0 * progress * progress
                } else {
                    1.0 - (-2.0 * progress + 2.0) * (-2.0 * progress + 2.0) / 2.0
                }
                val newZoom = startZoom + diff * eased
                camera.zoom = newZoom
                onZoomChanged(newZoom)
                if (progress >= 1.0) break
                delay(16) // ~60fps
            }
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
            // Update viewport center for cache eviction priority
            tileLoader.updateViewportCenter(key.lat, key.lon, key.zoom)
            val baseZoom = floor(key.zoom).toInt()
            val tiles = tileLoader.visibleTiles(
                key.lat, key.lon, key.zoom, key.width, key.height, sourceTileSize
            )
            // Sort tiles by distance from mouse pointer so closest tiles load first.
            // This dramatically reduces visible grey area when zooming rapidly.
            val ptx = lastPointerX
            val pty = lastPointerY
            val sortedTiles = if (ptx > 0f && pty > 0f) {
                val (centerPx, centerPy) = MapTileRenderer.latLonToPixelOffset(
                    key.lat, key.lon, key.zoom, sourceTileSize,
                )
                val viewW = key.width.toDouble()
                val viewH = key.height.toDouble()
                val pointerWorldX = centerPx + (ptx.toDouble() - viewW / 2.0)
                val pointerWorldY = centerPy + (pty.toDouble() - viewH / 2.0)
                val pointerTileX = pointerWorldX / sourceTileSize
                val pointerTileY = pointerWorldY / sourceTileSize
                tiles.sortedBy { (it.second - pointerTileX) * (it.second - pointerTileX) + (it.third - pointerTileY) * (it.third - pointerTileY) }
            } else {
                tiles
            }
            for ((tz, tx, ty) in sortedTiles) {
                val tileKey = "$tz/$tx/$ty"
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

    // ── At-rest prefetch: after camera stops moving, fill gaps in adjacent zoom levels ──
    // Uses debounce so it only fires 500ms after the last camera change.
    // This ensures zoom-out tiles and any missed zoom-in tiles are fully loaded
    // after rapid zooming/panning settles.
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
        }.debounce(500)
            .collect { key ->
                if (key.width <= 0 || key.height <= 0) return@collect
                // Aggressively prefetch adjacent zoom levels and 2 levels out
                tileLoader.prefetchAdjacentZoomTiles(
                    key.lat, key.lon, key.zoom, key.width, key.height, sourceTileSize, this
                )
                // Also prefetch 2 zoom levels out (zoom-2 and zoom+2) for smoother transitions
                val baseZoom = floor(key.zoom).toInt()
                for (farZoom in listOf(baseZoom - 2, baseZoom + 2).filter { it in 2..18 }) {
                    val tiles = tileLoader.adjacentZoomTiles(
                        key.lat, key.lon, key.zoom, farZoom,
                        key.width, key.height, sourceTileSize,
                    )
                    for ((z, x, y) in tiles) {
                        val tileKey = "$z/$x/$y"
                        if (tileLoader.cache.get(tileKey) != null) continue
                        launch { tileLoader.loadTile(z, x, y) }
                    }
                }
            }
    }

    // ── Helper: zoom toward a screen point ──────────────────────────
    // Defined inside composable so it can read camera state directly.

    /** Adjust center for zoom toward a pointer position (sets center + zoom immediately). */
    fun zoomAtPointer(pointerX: Float, pointerY: Float, oldZoom: Double, newZoom: Double, viewW: Float, viewH: Float) {
        // Convert pointer position to geographic coordinates at old zoom
        val (centerPx, centerPy) =
            MapTileRenderer.latLonToPixelOffset(camera.centerLat, camera.centerLon, oldZoom, sourceTileSize)
        // Pointer offset from center in screen pixels
        val pointerOffX = pointerX - viewW / 2f
        val pointerOffY = pointerY - viewH / 2f
        val pointerWorldPx = centerPx + pointerOffX
        val pointerWorldPy = centerPy + pointerOffY
        val (pointerLat, pointerLon) =
            MapTileRenderer.pixelOffsetToLatLon(pointerWorldPx, pointerWorldPy, oldZoom, sourceTileSize)

        // Where would that geographic point be at the new zoom?
        val (newPointerPx, newPointerPy) =
            MapTileRenderer.latLonToPixelOffset(pointerLat, pointerLon, newZoom, sourceTileSize)

        // Adjust center so the geographic point stays under the pointer
        val newOffX = pointerX - viewW / 2f
        val newOffY = pointerY - viewH / 2f
        val newCenterPx = newPointerPx - newOffX
        val newCenterPy = newPointerPy - newOffY
        val (newLat, newLon) =
            MapTileRenderer.pixelOffsetToLatLon(newCenterPx, newCenterPy, newZoom, sourceTileSize)
        camera.centerLat = MapTileRenderer.clampLat(newLat)
        camera.centerLon = MapTileRenderer.clampLon(newLon)
        camera.zoom = newZoom
        onZoomChanged(newZoom)
    }

    /** Adjust center for zoom toward a pointer position without setting zoom (for animated pointer zoom). */
    fun adjustCenterForPointerZoom(pointerX: Float, pointerY: Float, oldZoom: Double, newZoom: Double, viewW: Float, viewH: Float) {
        val (centerPx, centerPy) =
            MapTileRenderer.latLonToPixelOffset(camera.centerLat, camera.centerLon, oldZoom, sourceTileSize)
        val pointerOffX = pointerX - viewW / 2f
        val pointerOffY = pointerY - viewH / 2f
        val pointerWorldPx = centerPx + pointerOffX
        val pointerWorldPy = centerPy + pointerOffY
        val (pointerLat, pointerLon) =
            MapTileRenderer.pixelOffsetToLatLon(pointerWorldPx, pointerWorldPy, oldZoom, sourceTileSize)
        val (newPointerPx, newPointerPy) =
            MapTileRenderer.latLonToPixelOffset(pointerLat, pointerLon, newZoom, sourceTileSize)
        val newOffX = pointerX - viewW / 2f
        val newOffY = pointerY - viewH / 2f
        val newCenterPx = newPointerPx - newOffX
        val newCenterPy = newPointerPy - newOffY
        val (newLat, newLon) =
            MapTileRenderer.pixelOffsetToLatLon(newCenterPx, newCenterPy, newZoom, sourceTileSize)
        camera.centerLat = MapTileRenderer.clampLat(newLat)
        camera.centerLon = MapTileRenderer.clampLon(newLon)
        // Note: does NOT set camera.zoom — animation will handle that
    }

    /** Animated zoom centered on the map center (for +/- buttons). */
    fun requestZoomAnimation(targetZoom: Double) {
        animateZoomTo(targetZoom)
    }

    /** Animated zoom toward a pointer position (for double-click). */
    fun requestPointerZoomAnimation(pointerX: Float, pointerY: Float, oldZoom: Double, delta: Double, viewW: Float, viewH: Float) {
        val newZoom = (oldZoom + delta).coerceIn(TileLoader.MIN_ZOOM, TileLoader.MAX_ZOOM)
        // Apply the center adjustment immediately (so the map doesn't jerk)
        adjustCenterForPointerZoom(pointerX, pointerY, oldZoom, newZoom, viewW, viewH)
        // Snap to old zoom, then animate to new zoom
        camera.zoom = oldZoom
        animateZoomTo(newZoom)
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
                                        // Cancel any in-progress zoom animation so scroll takes over
                                        zoomAnimationJob?.cancel()
                                        zoomAnimationJob = null
                                        val zoomDelta = -scrollDelta * TileLoader.ZOOM_SENSITIVITY
                                        val oldZoom = camera.zoom
                                        val newZoom =
                                            (oldZoom + zoomDelta).coerceIn(
                                                TileLoader.MIN_ZOOM,
                                                TileLoader.MAX_ZOOM,
                                            )
                                        if (newZoom != oldZoom) {
                                            lastPointerX = change.position.x
                                            lastPointerY = change.position.y
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
                            val pixelSize = 360.0 / (n * sourceTileSize)
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
                                lastPointerX = offset.x
                                lastPointerY = offset.y
                                requestPointerZoomAnimation(
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
                                val clickWorldPx = centerPx + (offset.x - vW / 2.0)
                                val clickWorldPy = centerPy + (offset.y - vH / 2.0)
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
                                    val hoverWorldPx = centerPx + (change.position.x - vW / 2.0)
                                    val hoverWorldPy = centerPy + (change.position.y - vH / 2.0)
                                    val (lat, lon) =
                                        MapTileRenderer.pixelOffsetToLatLon(
                                            hoverWorldPx,
                                            hoverWorldPy,
                                            camera.zoom,
                                            sourceTileSize,
                                        )
                                    hoverGeoLat = lat
                                    hoverGeoLon = lon
                                    lastPointerX = change.position.x
                                    lastPointerY = change.position.y
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

            // ── Draw tiles with fractional zoom scaling ──────────────────────
            // Strategy: walk down zoom levels to find cached fallback tiles,
            // then overlay the current baseZoom tiles on top.
            // This ensures the viewport is never fully grey even if tiles haven't loaded yet.
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
            val viewLeft = centerPx - vW / 2.0
            val viewTop = centerPy - vH / 2.0

            // Helper: draw tiles at a given zoom level, scaled to fill the viewport at camera.zoom.
            // Returns the number of tiles successfully drawn (used to detect if we need a spinner).
            fun drawTileLayer(
                layerZoom: Int,
                layerScale: Double, // 2^(camera.zoom - layerZoom)
                alpha: Float = 1f,
            ): Int {
                val n = 2.0.pow(layerZoom).toInt()
                val layerViewLeft = centerPx / layerScale - vW / 2.0 / layerScale
                val layerViewTop = centerPy / layerScale - vH / 2.0 / layerScale
                val layerViewRight = centerPx / layerScale + vW / 2.0 / layerScale
                val layerViewBottom = centerPy / layerScale + vH / 2.0 / layerScale
                val minTX = max(0, floor(layerViewLeft / sourceTileSize).toInt())
                val minTY = max(0, floor(layerViewTop / sourceTileSize).toInt())
                val maxTX = min(n - 1, floor(layerViewRight / sourceTileSize).toInt())
                val maxTY = min(n - 1, floor(layerViewBottom / sourceTileSize).toInt())
                val layerDisplaySize = (sourceTileSize * layerScale).toFloat()
                if (minTX > maxTX || minTY > maxTY) return 0
                val totalTiles = (maxTX - minTX + 1) * (maxTY - minTY + 1)
                var drawn = 0
                for (tx in minTX..maxTX) {
                    for (ty in minTY..maxTY) {
                        val key = "$layerZoom/$tx/$ty"
                        val bitmap = tileLoader.cache.get(key) ?: continue
                        drawn++
                        val tileFracLeft = tx * sourceTileSize * layerScale
                        val tileFracTop = ty * sourceTileSize * layerScale
                        val screenLeft = (tileFracLeft - viewLeft).toFloat()
                        val screenTop = (tileFracTop - viewTop).toFloat()
                        drawImage(
                            image = bitmap,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(bitmap.width, bitmap.height),
                            dstOffset = IntOffset(screenLeft.toInt(), screenTop.toInt()),
                            dstSize = IntSize(layerDisplaySize.toInt(), layerDisplaySize.toInt()),
                            alpha = alpha,
                        )
                    }
                }
                return drawn
            }

            // Draw fallback layers: walk down zoom levels until we get coverage.
            // Lower-zoom tiles cover larger areas (each tile at z covers 4^(baseZoom-z) pixels),
            // so even one cached tile at z-4 covers a huge portion of the viewport.
            var hadFallbackCoverage = false
            for (delta in 1..min(baseZoom - 2, 4)) {
                val fallbackZoom = baseZoom - delta
                if (fallbackZoom < 2) break
                val fallbackScale = 2.0.pow(camera.zoom - fallbackZoom)
                val drawn = drawTileLayer(fallbackZoom, fallbackScale, alpha = 0.7f)
                if (drawn > 0) {
                    hadFallbackCoverage = true
                    break // Stop as soon as we have a fallback layer
                }
            }

            // Draw the current baseZoom tiles (sharpest layer, drawn on top)
            val currentDrawn = drawTileLayer(baseZoom, scaleFactor, alpha = 1f)

            // Compute total tiles needed at baseZoom for loading indicator logic
            val nBase = 2.0.pow(baseZoom).toInt()
            val totalBaseTiles = run {
                val minX = max(0, floor(viewLeft / sourceTileSize).toInt())
                val minY = max(0, floor(viewTop / sourceTileSize).toInt())
                val maxX = min(nBase - 1, floor((centerPx + vW / 2.0) / sourceTileSize).toInt())
                val maxY = min(nBase - 1, floor((centerPy + vH / 2.0) / sourceTileSize).toInt())
                (maxX - minX + 1) * (maxY - minY + 1)
            }

            // Track loading state: show indicator when current-zoom tiles are incomplete
            // and no fallback layer provides coverage
            sizeTracker.showLoading = currentDrawn < totalBaseTiles && !hadFallbackCoverage

            // ── Search result markers ───────────────────────────────────────
            for (result in searchResults) {
                val (px, py) =
                    MapTileRenderer.latLonToPixelOffset(
                        result.latitude,
                        result.longitude,
                        camera.zoom,
                        sourceTileSize,
                    )
                val screenX = (px - viewLeft).toFloat()
                val screenY = (py - viewTop).toFloat()
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
                    (pinPx - viewLeft).toFloat(),
                    (pinPy - viewTop).toFloat(),
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

        // ── Loading indicator ─────────────────────────────────────────────
        if (sizeTracker.showLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color(0xFF2196F3),
                strokeWidth = 3.dp,
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
                    requestZoomAnimation(newZoom)
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
                    requestZoomAnimation(newZoom)
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
 * when tiles finish loading, and [showLoading] which signals whether a loading indicator
 * should be displayed (current-zoom tiles incomplete, no fallback).
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

    /** True when current-zoom tiles are missing and there's no fallback coverage. */
    var showLoading by mutableStateOf(false)

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