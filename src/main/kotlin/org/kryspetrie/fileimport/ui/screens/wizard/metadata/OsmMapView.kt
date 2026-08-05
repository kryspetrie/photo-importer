@file:Suppress("TooManyFunctions", "MagicNumber")
@file:OptIn(kotlinx.coroutines.FlowPreview::class)

package org.kryspetrie.fileimport.ui.screens.wizard.metadata

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.kryspetrie.fileimport.domain.model.LocationResult
import org.kryspetrie.fileimport.domain.port.DispatcherProvider

// OsmMapView
// ──────────────────────────────────────────────────────────────────────────────

/**
 * A smooth-zooming OpenStreetMap tiled-map composable for Compose Desktop.
 *
 * ## Smooth Zoom
 *
 * Uses **fractional zoom** (e.g., 12.35) so the map zooms continuously instead of snapping between
 * discrete levels. Tiles from the base zoom level (floor of fractional zoom) are scale-transformed
 * by `2^(zoom - floor(zoom))` to fill intermediate positions. This gives a Google Maps–like smooth
 * zoom experience.
 *
 * ## Zoom Behavior
 * - **Scroll wheel**: Each scroll event adds/subtracts a small fractional zoom change (controlled
 *   by [TileLoader.ZOOM_SENSITIVITY]). The map zooms smoothly toward the mouse pointer position —
 *   the geographic point under the cursor stays fixed.
 * - **Double-click**: Animated zoom-in (250ms ease-out) toward the click position.
 * - **+/- buttons**: Animated zoom centered on the map center.
 * - **Drag**: Pan with latitude-corrected pixel scaling.
 *
 * ## Performance
 * - Tiles are rendered at their native source size (256px). Adjacent zoom levels are prefetched
 *   asynchronously.
 * - Hover prefetching: when the mouse lingers in an area for 300ms, tiles at zoom+1 around that
 *   location are prefetched so zoom-in transitions are instant.
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
    LaunchedEffect(initialZoom) { camera.zoom = initialZoom }

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
        zoomAnimationJob =
            coroutineScope.launch {
                val durationNs = TileLoader.ZOOM_ANIMATION_MS.toLong() * 1_000_000L
                val startTimeNs = System.nanoTime()
                val diff = targetZoom - startZoom
                while (true) {
                    val elapsed = System.nanoTime() - startTimeNs
                    val progress = (elapsed.toDouble() / durationNs).coerceIn(0.0, 1.0)
                    // Ease-in-out (quadratic): slow start, fast middle, slow end
                    val eased =
                        if (progress < 0.5) {
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

    // Sync map style to tile loader — do NOT clear memory cache;
    // each style has its own TileCache so switching is instant.
    // Just invalidate to trigger a redraw with the new style's tiles.
    LaunchedEffect(mapStyle) {
        tileLoader.mapStyle = mapStyle
        sizeTracker.invalidate()
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
            }
            .collect { key ->
                if (key.width <= 0 || key.height <= 0) return@collect
                // Update viewport center for cache eviction priority
                tileLoader.updateViewportCenter(key.lat, key.lon, key.zoom)
                val baseZoom = floor(key.zoom).toInt()
                val tiles =
                    tileLoader.visibleTiles(
                        key.lat,
                        key.lon,
                        key.zoom,
                        key.width,
                        key.height,
                        sourceTileSize,
                    )
                // Sort tiles by distance from mouse pointer so closest tiles load first.
                // This dramatically reduces visible grey area when zooming rapidly.
                val ptx = lastPointerX
                val pty = lastPointerY
                val sortedTiles =
                    if (ptx > 0f && pty > 0f) {
                        val (centerPx, centerPy) =
                            MapTileRenderer.latLonToPixelOffset(
                                key.lat,
                                key.lon,
                                key.zoom,
                                sourceTileSize,
                            )
                        val viewW = key.width.toDouble()
                        val viewH = key.height.toDouble()
                        val pointerWorldX = centerPx + (ptx.toDouble() - viewW / 2.0)
                        val pointerWorldY = centerPy + (pty.toDouble() - viewH / 2.0)
                        val pointerTileX = pointerWorldX / sourceTileSize
                        val pointerTileY = pointerWorldY / sourceTileSize
                        tiles.sortedBy {
                            (it.second - pointerTileX) * (it.second - pointerTileX) +
                                (it.third - pointerTileY) * (it.third - pointerTileY)
                        }
                    } else {
                        tiles
                    }
                for ((tz, tx, ty) in sortedTiles) {
                    val tileKey = "$tz/$tx/$ty"
                    if (tileLoader.cache.get(tileKey) != null) continue
                    launch {
                        try {
                            if (tileLoader.loadTile(tz, tx, ty) != null) {
                                sizeTracker.invalidate()
                            }
                        } catch (_: CancellationException) {
                            // Tile load cancelled (e.g. composable leaving composition) —
                            // don't propagate, as it would cancel the snapshotFlow collector
                            // and permanently stop all tile loading.
                        }
                    }
                }
                // Also prefetch adjacent zoom levels
                tileLoader.prefetchAdjacentZoomTiles(
                    key.lat,
                    key.lon,
                    key.zoom,
                    key.width,
                    key.height,
                    sourceTileSize,
                    this,
                )
                // If hovering, prefetch zoom+1 around the hover point
                if (!hoverGeoLat.isNaN() && !hoverGeoLon.isNaN()) {
                    tileLoader.prefetchHoverRegion(
                        key.lat,
                        key.lon,
                        key.zoom,
                        hoverGeoLat,
                        hoverGeoLon,
                        key.width,
                        key.height,
                        sourceTileSize,
                        this,
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
            }
            .debounce(500)
            .collect { key ->
                if (key.width <= 0 || key.height <= 0) return@collect
                // Aggressively prefetch adjacent zoom levels and 2 levels out
                tileLoader.prefetchAdjacentZoomTiles(
                    key.lat,
                    key.lon,
                    key.zoom,
                    key.width,
                    key.height,
                    sourceTileSize,
                    this,
                )
                // Also prefetch 2 zoom levels out (zoom-2 and zoom+2) for smoother transitions
                val baseZoom = floor(key.zoom).toInt()
                for (farZoom in listOf(baseZoom - 2, baseZoom + 2).filter { it in 2..18 }) {
                    val tiles =
                        tileLoader.adjacentZoomTiles(
                            key.lat,
                            key.lon,
                            key.zoom,
                            farZoom,
                            key.width,
                            key.height,
                            sourceTileSize,
                        )
                    for ((z, x, y) in tiles) {
                        val tileKey = "$z/$x/$y"
                        if (tileLoader.cache.get(tileKey) != null) continue
                        launch {
                            try {
                                tileLoader.loadTile(z, x, y)
                            } catch (_: CancellationException) {
                                // Don't propagate — would cancel the debounced prefetch collector
                            }
                        }
                    }
                }
            }
    }

    /** Animated zoom centered on the map center (for +/- buttons). */
    fun requestZoomAnimation(targetZoom: Double) {
        animateZoomTo(targetZoom)
    }

    /** Animated zoom toward a pointer position (for double-click). */
    fun requestPointerZoomAnimation(
        pointerX: Float,
        pointerY: Float,
        oldZoom: Double,
        delta: Double,
        viewW: Float,
        viewH: Float,
    ) {
        val newZoom = (oldZoom + delta).coerceIn(TileLoader.MIN_ZOOM, TileLoader.MAX_ZOOM)
        adjustCenterForPointerZoom(
            camera,
            sourceTileSize,
            pointerX,
            pointerY,
            oldZoom,
            newZoom,
            viewW,
            viewH,
        )
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
                                        // Cancel any in-progress zoom animation so scroll takes
                                        // over
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
                                                camera,
                                                sourceTileSize,
                                                change.position.x,
                                                change.position.y,
                                                oldZoom,
                                                newZoom,
                                                size.width.toFloat(),
                                                size.height.toFloat(),
                                                onZoomChanged,
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
                            val latShift =
                                dragAmount.y * pixelSize * cos(Math.toRadians(camera.centerLat))
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
                                    offset.x,
                                    offset.y,
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
                                    hoverPrefetchJob =
                                        coroutineScope.launch {
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
                    camera.centerLat,
                    camera.centerLon,
                    camera.zoom,
                    sourceTileSize,
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
                drawPinMarker((pinPx - viewLeft).toFloat(), (pinPy - viewTop).toFloat())
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

        OsmMapControls(
            mapStyle = mapStyle,
            zoom = camera.zoom,
            onToggleStyle = {
                mapStyle =
                    when (mapStyle) {
                        MapStyle.STREET -> MapStyle.SATELLITE
                        MapStyle.SATELLITE -> MapStyle.STREET
                    }
            },
            onZoomRequested = ::requestZoomAnimation,
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 8.dp, top = 8.dp),
        )
    }
}
