@file:Suppress("TooManyFunctions", "MagicNumber")
@file:OptIn(kotlinx.coroutines.FlowPreview::class)

package org.kryspetrie.fileimport.ui.screens.wizard.metadata

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage
import org.kryspetrie.fileimport.domain.port.DispatcherProvider

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
    val cache: TileCache
        get() = caches[mapStyle]!!

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
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
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
     * Compute which tiles are visible at the given camera position and zoom. Uses [baseZoom] (floor
     * of fractional zoom) to select tiles, then computes the wider viewport that results from the
     * fractional scale factor.
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
     * Compute which tiles at [targetZoom] cover the same viewport as the currently visible tiles at
     * [currentZoom]. Used for prefetching adjacent zoom levels.
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
                    setRequestProperty("User-Agent", "PhotoImporter/1.0")
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
        } catch (e: Exception) {
            // CancellationException must be re-thrown to preserve coroutine cancellation semantics.
            // Swallowing it would prevent proper coroutine cancellation and cause tile loading
            // to silently fail when the composable is inside a Dialog or other scoped context.
            if (e is CancellationException) throw e
            null
        }
    }

    /**
     * Prefetch tiles at adjacent zoom levels (one above and one below). These are loaded
     * asynchronously to make zoom transitions feel instant.
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
                adjacentZoomTiles(
                    centerLat,
                    centerLon,
                    zoom,
                    adjZoom,
                    viewWidth,
                    viewHeight,
                    tileSize,
                )
            for ((z, x, y) in tiles) {
                val key = "$z/$x/$y"
                if (cache.get(key) != null) continue
                scope.launch {
                    try {
                        loadTile(z, x, y)
                    } catch (_: CancellationException) {
                        // Don't propagate — would cancel the snapshotFlow collector
                    }
                }
            }
        }
    }

    /**
     * Prefetch tiles at zoom+1 that cover the viewport region around the mouse pointer, for faster
     * zoom-in transitions.
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
                    scope.launch {
                        try {
                            loadTile(targetZoom, tx, ty)
                        } catch (_: CancellationException) {
                            // Don't propagate — would cancel the snapshotFlow collector
                        }
                    }
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
         * Zoom sensitivity: how much fractional zoom change per unit of scroll delta. Typical mouse
         * wheel notch is ~120 units of scroll, giving ~0.36 zoom levels per notch at this
         * sensitivity.
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
