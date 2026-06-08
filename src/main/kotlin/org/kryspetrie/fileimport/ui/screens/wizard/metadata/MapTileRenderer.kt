@file:Suppress("TooManyFunctions", "MagicNumber")

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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
data class MapViewPreset(val name: String, val lat: Double, val lon: Double, val zoom: Int)

/** Built-in map view presets covering common regions. */
val mapViewPresets =
    listOf(
        MapViewPreset("Eastern US", 39.0, -78.0, 5),
        MapViewPreset("Western US", 39.0, -114.0, 5),
        MapViewPreset("Central US", 39.0, -96.0, 5),
        MapViewPreset("Europe", 50.0, 10.0, 5),
        MapViewPreset("UK & Ireland", 54.0, -3.0, 6),
        MapViewPreset("East Asia", 35.0, 120.0, 5),
        MapViewPreset("South America", -15.0, -58.0, 4),
        MapViewPreset("Africa", 5.0, 20.0, 4),
        MapViewPreset("Australia", -28.0, 135.0, 4),
        MapViewPreset("World", 25.0, 0.0, 2),
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

object MapTileRenderer {

    fun latLonToTile(lat: Double, lon: Double, zoom: Int): Pair<Int, Int> {
        val n = 2.0.pow(zoom)
        val x = floor((lon + 180.0) / 360.0 * n).toInt()
        val latRad = lat * PI / 180.0
        val y = floor((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n).toInt()
        return Pair(x.coerceIn(0, n.toInt() - 1), y.coerceIn(0, n.toInt() - 1))
    }

    /**
     * Convert lat/lon to absolute pixel coordinates at the given zoom. tileSize is the *source*
     * tile size.
     */
    fun latLonToPixelOffset(
        lat: Double,
        lon: Double,
        zoom: Int,
        tileSize: Int = 256,
    ): Pair<Double, Double> {
        val n = 2.0.pow(zoom)
        val px = (lon + 180.0) / 360.0 * n * tileSize
        val latRad = lat * PI / 180.0
        val py = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n * tileSize
        return Pair(px, py)
    }

    /** Convert absolute pixel coordinates back to lat/lon. tileSize is the *source* tile size. */
    fun pixelOffsetToLatLon(
        px: Double,
        py: Double,
        zoom: Int,
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
// LRU tile cache
// ──────────────────────────────────────────────────────────────────────────────

class TileCache(private val maxTiles: Int = 150) {
    private val cache =
        object : LinkedHashMap<String, ImageBitmap>(16, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, ImageBitmap>
            ): Boolean = size > maxTiles
        }

    @Synchronized fun get(key: String): ImageBitmap? = cache[key]

    @Synchronized
    fun put(key: String, bitmap: ImageBitmap) {
        cache[key] = bitmap
    }

    @Synchronized
    fun clear() {
        cache.clear()
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Disk tile cache
// ──────────────────────────────────────────────────────────────────────────────

class DiskTileCache(cacheDir: File = File(Platform.cacheDir, "map-tiles")) {
    private val cacheDir: File
    /** Maximum age in milliseconds before a cached tile is considered stale (7 days). */
    private val maxAgeMs: Long = 7L * 24 * 60 * 60 * 1000
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
                // Check if tile is expired
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
                    if (totalSize - freed <= maxCacheBytes * 0.8) break // Evict to 80% capacity
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

    fun visibleTiles(
        centerLat: Double,
        centerLon: Double,
        zoom: Int,
        viewWidth: Int,
        viewHeight: Int,
        tileSize: Int = 256,
    ): List<Triple<Int, Int, Int>> {
        val (cx, cy) = MapTileRenderer.latLonToTile(centerLat, centerLon, zoom)
        val displayTileSize = tileSize * TILE_SCALE
        val spanX = ceil(viewWidth.toDouble() / displayTileSize).toInt() / 2 + 1
        val spanY = ceil(viewHeight.toDouble() / displayTileSize).toInt() / 2 + 1
        val n = 2.0.pow(zoom).toInt()
        val tiles = mutableListOf<Triple<Int, Int, Int>>()
        for (tx in (cx - spanX)..(cx + spanX)) {
            for (ty in (cy - spanY)..(cy + spanY)) {
                if (tx in 0 until n && ty in 0 until n) tiles.add(Triple(zoom, tx, ty))
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
     * Prefetch tiles at adjacent zoom levels (zoom-1 and zoom+1) covering the same geographic
     * viewport. These tiles are loaded at lower priority — after the visible tiles are already
     * cached — so zoom transitions feel instant.
     *
     * @param centerLat Center latitude of current view
     * @param centerLon Center longitude of current view
     * @param zoom Current zoom level
     * @param viewWidth Viewport width in pixels
     * @param viewHeight Viewport height in pixels
     * @param tileSize Source tile size (default 256)
     * @param scope Coroutine scope to launch background fetches
     */
    fun prefetchAdjacentZoomTiles(
        centerLat: Double,
        centerLon: Double,
        zoom: Int,
        viewWidth: Int,
        viewHeight: Int,
        tileSize: Int = 256,
        scope: CoroutineScope,
    ) {
        val adjacentZooms = listOfNotNull(zoom - 1, zoom + 1).filter { it in 2..18 }
        for (adjZoom in adjacentZooms) {
            val tiles = visibleTiles(centerLat, centerLon, adjZoom, viewWidth, viewHeight, tileSize)
            for ((z, x, y) in tiles) {
                val key = "${mapStyle.name.lowercase()}/$z/$x/$y"
                if (cache.get(key) != null) continue
                scope.launch { loadTile(z, x, y) }
            }
        }
    }

    /** Clear memory cache when switching map styles. */
    fun clearMemoryCache() {
        cache.clear()
    }

    companion object {
        /**
         * Tiles are scaled up by this factor for display. Higher values = fewer tiles needed per
         * viewport (blurrier but less network data). 4 = each 256px tile renders at 1024px display
         * pixels.
         */
        const val TILE_SCALE = 4
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// OsmMapView
// ──────────────────────────────────────────────────────────────────────────────

/**
 * A lightweight OpenStreetMap tiled-map composable for Compose Desktop.
 *
 * Tiles are displayed at TILE_SCALE× their source size (default 4×) to reduce tile count and
 * improve load times. At zoom 5 with 4× scaling, the Eastern US needs only ~2 tiles instead of
 * ~20+, at the cost of slightly blurrier text.
 *
 * The map also supports predefined view presets (e.g. "Eastern US") for quick navigation, floating
 * zoom overlay buttons, and switching between street map and satellite imagery.
 *
 * @param modifier Layout modifier
 * @param initialLat Starting latitude
 * @param initialLon Starting longitude
 * @param initialZoom Starting zoom level (2-18)
 * @param onMapClick Callback with (lat, lon) when the user clicks the map
 * @param pinLocation Optional (lat, lon) for a red pin marker
 * @param searchResults Location results to show as blue markers on the map
 * @param selectedResult Highlighted search result (yellow ring)
 * @param initialMapStyle Starting map style (street or satellite)
 * @param onMapStyleChanged Callback when map style changes
 * @param onZoomChanged Callback when zoom level changes
 * @param dispatcherProvider Provides IO dispatcher for tile loading
 * @param coroutineScope Scope for launching tile-load coroutines
 */
@Composable
fun OsmMapView(
    modifier: Modifier = Modifier,
    initialLat: Double = 39.0,
    initialLon: Double = -78.0,
    initialZoom: Int = 5,
    onMapClick: (lat: Double, lon: Double) -> Unit = { _, _ -> },
    pinLocation: Pair<Double, Double>? = null,
    searchResults: List<LocationResult> = emptyList(),
    selectedResult: LocationResult? = null,
    initialMapStyle: MapStyle = MapStyle.STREET,
    onMapStyleChanged: (MapStyle) -> Unit = {},
    onZoomChanged: (Int) -> Unit = {},
    dispatcherProvider: DispatcherProvider,
    coroutineScope: CoroutineScope,
) {
    var centerLat by remember { mutableDoubleStateOf(initialLat) }
    var centerLon by remember { mutableDoubleStateOf(initialLon) }
    var zoom by remember { mutableIntStateOf(initialZoom) }
    var mapStyle by remember { mutableStateOf(initialMapStyle) }

    // Sync external center/zoom changes (e.g. selecting a search result or preset)
    LaunchedEffect(initialLat) { centerLat = initialLat }
    LaunchedEffect(initialLon) { centerLon = initialLon }
    LaunchedEffect(initialZoom) {
        zoom = initialZoom
        onZoomChanged(initialZoom)
    }

    // When map style changes, clear memory cache and update tile loader
    var renderVersion by remember { mutableIntStateOf(0) }
    var lastPointerOffset by remember { mutableStateOf(Offset.Zero) }

    val tileLoader = remember { TileLoader(dispatcherProvider) }
    val sourceTileSize = 256
    val displayTileSize = sourceTileSize * TileLoader.TILE_SCALE
    val textMeasurer = rememberTextMeasurer()

    var viewWidth by remember { mutableIntStateOf(0) }
    var viewHeight by remember { mutableIntStateOf(0) }

    // Sync map style to tile loader
    LaunchedEffect(mapStyle) {
        tileLoader.mapStyle = mapStyle
        tileLoader.clearMemoryCache()
        renderVersion++
        onMapStyleChanged(mapStyle)
    }

    // Load only viewport-visible tiles when center/zoom/size changes
    LaunchedEffect(centerLat, centerLon, zoom, viewWidth, viewHeight, mapStyle) {
        if (viewWidth <= 0 || viewHeight <= 0) return@LaunchedEffect
        val tiles =
            tileLoader.visibleTiles(
                centerLat,
                centerLon,
                zoom,
                viewWidth,
                viewHeight,
                sourceTileSize,
            )
        for ((z, x, y) in tiles) {
            val key = "${mapStyle.name.lowercase()}/$z/$x/$y"
            if (tileLoader.cache.get(key) != null) continue
            launch { if (tileLoader.loadTile(z, x, y) != null) renderVersion++ }
        }
        // Prefetch adjacent zoom levels so zoom transitions are instant
        tileLoader.prefetchAdjacentZoomTiles(
            centerLat,
            centerLon,
            zoom,
            viewWidth,
            viewHeight,
            sourceTileSize,
            this,
        )
    }

    val maxZoom = 18

    Box(modifier = modifier) {
        // ── Canvas map ──────────────────────────────────────────────────
        Canvas(
            modifier =
                Modifier.fillMaxSize()
                    .pointerInput(zoom) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type == PointerEventType.Scroll) {
                                    val change = event.changes.firstOrNull() ?: continue
                                    lastPointerOffset = change.position
                                    val scrollDelta = change.scrollDelta
                                    if (scrollDelta.y != 0f) {
                                        val oldZoom = zoom
                                        val newZoom =
                                            (oldZoom + if (scrollDelta.y > 0) -1 else 1).coerceIn(
                                                2,
                                                maxZoom,
                                            )
                                        if (newZoom != oldZoom) {
                                            // Zoom toward pointer position
                                            val (centerPx, centerPy) =
                                                MapTileRenderer.latLonToPixelOffset(
                                                    centerLat,
                                                    centerLon,
                                                    oldZoom,
                                                    sourceTileSize,
                                                )
                                            val vW = size.width.toDouble()
                                            val vH = size.height.toDouble()
                                            val pointerWorldPx =
                                                centerPx +
                                                    (lastPointerOffset.x.toDouble() - vW / 2.0) /
                                                        TileLoader.TILE_SCALE
                                            val pointerWorldPy =
                                                centerPy +
                                                    (lastPointerOffset.y.toDouble() - vH / 2.0) /
                                                        TileLoader.TILE_SCALE
                                            val (pointerLat, pointerLon) =
                                                MapTileRenderer.pixelOffsetToLatLon(
                                                    pointerWorldPx,
                                                    pointerWorldPy,
                                                    oldZoom,
                                                    sourceTileSize,
                                                )
                                            zoom = newZoom
                                            onZoomChanged(newZoom)
                                            // After zoom, adjust center so pointer stays at same
                                            // screen position
                                            val (newPointerPx, newPointerPy) =
                                                MapTileRenderer.latLonToPixelOffset(
                                                    pointerLat,
                                                    pointerLon,
                                                    newZoom,
                                                    sourceTileSize,
                                                )
                                            val newCenterPx =
                                                newPointerPx -
                                                    (lastPointerOffset.x.toDouble() - vW / 2.0) /
                                                        TileLoader.TILE_SCALE
                                            val newCenterPy =
                                                newPointerPy -
                                                    (lastPointerOffset.y.toDouble() - vH / 2.0) /
                                                        TileLoader.TILE_SCALE
                                            val (newLat, newLon) =
                                                MapTileRenderer.pixelOffsetToLatLon(
                                                    newCenterPx,
                                                    newCenterPy,
                                                    newZoom,
                                                    sourceTileSize,
                                                )
                                            centerLat = MapTileRenderer.clampLat(newLat)
                                            centerLon = MapTileRenderer.clampLon(newLon)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val n = 2.0.pow(zoom)
                            // Scale drag by 1/TILE_SCALE since displayed pixels map to more
                            // world-space
                            val pixelSize = 360.0 / (n * sourceTileSize * TileLoader.TILE_SCALE)
                            val lonShift = -dragAmount.x * pixelSize
                            val latShift = dragAmount.y * pixelSize * cos(Math.toRadians(centerLat))
                            centerLat = MapTileRenderer.clampLat(centerLat + latShift)
                            centerLon = MapTileRenderer.clampLon(centerLon + lonShift)
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val vW = size.width.toDouble()
                            val vH = size.height.toDouble()
                            val (centerPx, centerPy) =
                                MapTileRenderer.latLonToPixelOffset(
                                    centerLat,
                                    centerLon,
                                    zoom,
                                    sourceTileSize,
                                )
                            // Convert screen offset to world pixel offset (accounting for tile
                            // scale)
                            val clickWorldPx =
                                centerPx + (offset.x.toDouble() - vW / 2.0) / TileLoader.TILE_SCALE
                            val clickWorldPy =
                                centerPy + (offset.y.toDouble() - vH / 2.0) / TileLoader.TILE_SCALE
                            val (lat, lon) =
                                MapTileRenderer.pixelOffsetToLatLon(
                                    clickWorldPx,
                                    clickWorldPy,
                                    zoom,
                                    sourceTileSize,
                                )
                            onMapClick(lat, lon)
                        }
                    }
        ) {
            val currentWidth = size.width.toInt()
            val currentHeight = size.height.toInt()
            if (currentWidth != viewWidth || currentHeight != viewHeight) {
                viewWidth = currentWidth
                viewHeight = currentHeight
            }

            @Suppress("UNUSED_EXPRESSION") renderVersion

            if (size.width <= 0f || size.height <= 0f) return@Canvas

            drawRect(Color(0xFFE0E0E0))

            // Compute tile positions using source tile coordinates, then scale for display
            val (centerPx, centerPy) =
                MapTileRenderer.latLonToPixelOffset(centerLat, centerLon, zoom, sourceTileSize)
            // Top-left in source-pixel space (256px per tile)
            val vW = size.width.toDouble()
            val vH = size.height.toDouble()
            val topLeftPx = centerPx - vW / 2.0 / TileLoader.TILE_SCALE
            val topLeftPy = centerPy - vH / 2.0 / TileLoader.TILE_SCALE

            val n = 2.0.pow(zoom).toInt()
            val minTileX = max(0, floor(topLeftPx / sourceTileSize).toInt())
            val minTileY = max(0, floor(topLeftPy / sourceTileSize).toInt())
            val maxTileX =
                min(n - 1, floor((topLeftPx + vW / TileLoader.TILE_SCALE) / sourceTileSize).toInt())
            val maxTileY =
                min(n - 1, floor((topLeftPy + vH / TileLoader.TILE_SCALE) / sourceTileSize).toInt())

            // Draw tiles scaled up by TILE_SCALE
            for (tx in minTileX..maxTileX) {
                for (ty in minTileY..maxTileY) {
                    val key = "${mapStyle.name.lowercase()}/$zoom/$tx/$ty"
                    val bitmap = tileLoader.cache.get(key)
                    // Source tile offset in display pixels
                    val tileScreenX =
                        ((tx * sourceTileSize - topLeftPx) * TileLoader.TILE_SCALE).toFloat()
                    val tileScreenY =
                        ((ty * sourceTileSize - topLeftPy) * TileLoader.TILE_SCALE).toFloat()

                    if (bitmap != null) {
                        drawImage(
                            image = bitmap,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(bitmap.width, bitmap.height),
                            dstOffset = IntOffset(tileScreenX.toInt(), tileScreenY.toInt()),
                            dstSize = IntSize(displayTileSize, displayTileSize),
                        )
                    } else {
                        drawRect(
                            Color(0xFFDADADA),
                            topLeft = Offset(tileScreenX, tileScreenY),
                            size = Size(displayTileSize.toFloat(), displayTileSize.toFloat()),
                        )
                    }
                }
            }

            // Search result markers
            for (result in searchResults) {
                val (px, py) =
                    MapTileRenderer.latLonToPixelOffset(
                        result.latitude,
                        result.longitude,
                        zoom,
                        sourceTileSize,
                    )
                val screenX = ((px - topLeftPx) * TileLoader.TILE_SCALE).toFloat()
                val screenY = ((py - topLeftPy) * TileLoader.TILE_SCALE).toFloat()
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
                val textLayout =
                    textMeasurer.measure(label, TextStyle(color = Color.White, fontSize = 10.sp))
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

            // Pin
            if (pinLocation != null) {
                val (pinLat, pinLon) = pinLocation
                val (pinPx, pinPy) =
                    MapTileRenderer.latLonToPixelOffset(pinLat, pinLon, zoom, sourceTileSize)
                drawPinMarker(
                    ((pinPx - topLeftPx) * TileLoader.TILE_SCALE).toFloat(),
                    ((pinPy - topLeftPy) * TileLoader.TILE_SCALE).toFloat(),
                )
            }

            // Zoom indicator
            val zoomText = "z$zoom"
            val zoomLayout =
                textMeasurer.measure(zoomText, TextStyle(color = Color.White, fontSize = 10.sp))
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

            // Attribution — adjust based on map style
            val attrText =
                when (mapStyle) {
                    MapStyle.STREET -> "© OpenStreetMap"
                    MapStyle.SATELLITE -> "© Esri"
                }
            val attrLayout =
                textMeasurer.measure(
                    attrText,
                    TextStyle(color = Color(0xCCFFFFFF.toInt()), fontSize = 8.sp),
                )
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
                    if (zoom < maxZoom) {
                        zoom++
                        onZoomChanged(zoom)
                    }
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
                    if (zoom > 2) {
                        zoom--
                        onZoomChanged(zoom)
                    }
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
