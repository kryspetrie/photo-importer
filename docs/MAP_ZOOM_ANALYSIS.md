# Map View Zoom Analysis & Fix Plan

**Date**: 2025-06-26  
**Author**: goose (with deep analysis)  
**Scope**: `MapTileRenderer.kt` — `OsmMapView` composable  

---

## Executive Summary

The map zoom implementation has **three fundamental problems** that make it feel objectively terrible:

1. **Integer-only zoom** — `MapCameraState.zoom` is `Int`, so the map snaps between discrete levels with no interpolation. Every professional map library (Google Maps, Leaflet, MapLibre GL, Apple Maps) uses **fractional zoom** (e.g., 12.35) for continuous smooth zooming.

2. **Accumulated-threshold scroll handling** — The current `ZOOM_SCROLL_THRESHOLD = 80.0f` means you must scroll 80 units before ANY zoom change occurs, then it jumps a full level. This creates the "nothing is happening... BANG wrong zoom" feel.

3. **No tile scaling during transitions** — When zoom changes, we immediately switch to a completely different set of tiles. There's no visual bridge — no scaling of current tiles while new ones load. The viewport "jumps" to a fundamentally different image.

**Additionally**, the zoom-toward-pointer calculation has a bug: the `zoomAt` function works in theory but the integer-only zoom combined with the threshold accumulator makes the pointer anchoring unreliable in practice.

---

## Current Implementation (Problems)

### Architecture

```
MapCameraState — zoom: Int, centerLat/Lon: Double
      ↓ scroll event
accumulatedScroll += scrollDelta
      ↓ threshold crossing
camera.zoom += 1  (snaps to next integer)
      ↓ triggers
snapshotFlow → load new tiles → redraw
```

### Problem 1: `zoom` is `Int`

```kotlin
// MapCameraState.kt
var zoom by mutableIntStateOf(initialZoom)
```

Every zoom change jumps an **entire level**. A single zoom step at the median zoom of 12 changes coverage by **2×** (each zoom level halves coverage area). This is why it feels like lurching between completely different maps.

**How Google/MapLibre/Leaflet do it**: zoom is `Double` (e.g., 12.35). The tiles from `floor(12.35) = 12` are rendered at scale `2^0.35 ≈ 1.27×`. This makes zooming perfectly smooth — you see a continuous zoom, not jumps.

### Problem 2: Scroll threshold accumulator

```kotlin
var accumulatedScroll by remember { mutableFloatStateOf(0f) }
// ...
accumulatedScroll += scrollDelta
val newZoom = when {
    accumulatedScroll < -ZOOM_SCROLL_THRESHOLD -> { accumulatedScroll += ZOOM_SCROLL_THRESHOLD; zoom + 1 }
    accumulatedScroll > ZOOM_SCROLL_THRESHOLD -> { accumulatedScroll -= ZOOM_SCROLL_THRESHOLD; zoom - 1 }
    else -> zoom  // NO FEEDBACK until threshold is crossed!
}
```

This means:
- First scroll notch: **nothing happens visually** (accumulating)
- More scrolling: **still nothing** (still below threshold)
- Threshold crossed: **instant level jump** (no animation)

**How Google Maps does it**: Each scroll notch directly maps to a small fractional zoom change (≈0.1 zoom per scroll delta). You see immediate continuous feedback. The map smoothly zooms under the cursor.

### Problem 3: No tile scaling during zoom transitions

Current drawing code:
```kotlin
val key = "${mapStyle}/${camera.zoom}/$tx/$ty"  // Uses INTEGER zoom
val bitmap = tileLoader.cache.get(key)            // Only has tiles at integer levels
if (bitmap != null) {
    drawImage(bitmap, ...)  // Draws at fixed displayTileSize = 1024px
}
```

When you zoom from 5→6:
- Zoom 5 tiles: **disappear** (no longer in viewport)
- Zoom 6 tiles: **not loaded yet** (gray rectangles)
- Eventually: zoom 6 tiles load one by one

**How MapLibre GL does it**:
1. Zoom 5 tiles immediately scale up by 2× (interpolated to fill zoom 6)
2. Zoom 6 tiles begin loading in background
3. As zoom 6 tiles load, they cross-fade in (300ms opacity transition)
4. After cross-fade, zoom 5 tiles are removed

### Problem 4: `TILE_SCALE = 4` magnifies every defect

Each 256px tile is rendered at **1024 display pixels**. This means:
- At any integer zoom, tiles are already 4× upsampled → **always blurry**
- When zoom changes, the 4× multiplication makes the visual area change even more dramatic
- The blurry baseline makes the "snap to next level" feel even worse

Professional map clients use `TILE_SCALE = 1` (native size) and just load more tiles. The 4× scale was a premature network optimization that destroys visual quality.

### Problem 5: Zoom-to-pointer is buggy in practice

The `zoomAt` function implements the correct math (world-pixel → lat/lon → rescale → adjust center), but:
- Integer zoom means the pointer anchoring snaps a full level, which shifts the viewport dramatically
- The scroll threshold means there's no visual feedback until the snap, so the user can't see where they're zooming
- The `size.width`/`size.height` references in `drawScope` may be stale when invoked from pointer handlers

---

## How Professional Maps Work

### Fractional Zoom Rendering

The core algorithm for rendering at fractional zoom `z` (e.g., 12.35):

```
baseZoom = floor(z)          // = 12 — tiles to fetch
fractionalPart = z - baseZoom // = 0.35
scaleFactor = 2 ^ fractionalPart // ≈ 1.27 — how much to magnify tiles

For each visible tile at baseZoom:
  drawImage(
    tile, 
    dstOffset = computed from center offset and scale,
    dstSize = (tileSize * TILE_SCALE * scaleFactor)  // ← KEY: scale the tile
  )
```

This means tiles from zoom 12 are drawn 1.27× their base size, making the viewport show a smooth interpolation between zoom 12 and 13.

### Scroll-to-Zoom Mapping

Google Maps and MapLibre both use:
```
newZoom = currentZoom + scrollDelta * ZOOM_SENSITIVITY
// ZOOM_SENSITIVITY ≈ 0.003 per scroll notch
// This gives ~0.3 zoom levels per scroll "click" (typical mouse wheel notch ≈ 120 delta)
```

No threshold. No accumulation. Every scroll event produces immediate visual feedback.

### Tile Loading at Adjacent Zoom Levels

```
currentZoom = 12.35

Load tiles from:
  - zoom 12 (floor) — displayed scaled UP by 2^0.35 ≈ 1.27×
  - zoom 13 (ceil) — displayed scaled DOWN by 2^(-0.65) ≈ 0.64× (faded in as loaded)
  - zoom 11 (one below) — for fallback when zoom 12 tiles haven't loaded yet
```

The higher-zoom tiles are loaded asynchronously. When they arrive, they cross-fade in over ~200ms.

### Zoom-Toward-Cursor (Correct Algorithm)

```
// Given: pointerX, pointerY on screen, current zoom, current center lat/lon, new zoom
// All in Double (fractional supported)

// 1. What geographic point is under the cursor?
pointerGeo = screenToGeo(pointerX, pointerY, center, currentZoom)

// 2. What screen position would that point be at the new zoom, with same center?
newScreenPos = geoToScreen(pointerGeo, center, newZoom)

// 3. Adjust center so the point stays under the cursor
//    Shift center by the pixel difference, converted back to geo coords at newZoom
centerShiftPixels = Offset(pointerX - newScreenPos.x, pointerY - newScreenPos.y)
newCenter = shiftCenterByPixels(center, centerShiftPixels, newZoom)
```

This works perfectly with **fractional zoom** because each scroll delta produces a tiny zoom change (0.003), so the pointer stays fixed with precision.

---

## Step-by-Step Fix Plan

### Phase 1: Fractional Zoom (Critical — fixes the "jumping")

**File**: `MapTileRenderer.kt`

1. **Change `MapCameraState.zoom` from `Int` to `Double`**
   ```kotlin
   var zoom by mutableDoubleStateOf(initialZoom.toDouble())
   ```

2. **Update `visibleTiles()` to use `floor(zoom)` for tile selection**
   ```kotlin
   val baseZoom = zoom.toInt()  // floor
   // Use baseZoom for tile fetching
   // Use (zoom - baseZoom) for scaling
   ```

3. **Update tile drawing to apply fractional scale**
   ```kotlin
   val fractionalPart = zoom - baseZoom
   val scaleFactor = 2.0.pow(fractionalPart)  // e.g., 1.27 at zoom 12.35
   
   // When drawing each tile:
   val scaledDisplaySize = (sourceTileSize * TILE_SCALE * scaleFactor).toFloat()
   // Offset positions also scale by scaleFactor
   ```

4. **Update `zoomAt()` to accept Double zoom values**
   - The math already works with Doubles; just need to change the signature
   - Remove integer coercion — let zoom be `Double.coerceIn(2.0, 18.0)`

### Phase 2: Continuous Scroll Zoom (Critical — fixes "slow then jumpy")

**File**: `MapTileRenderer.kt`

1. **Replace scroll threshold accumulator with direct fractional zoom**
   ```kotlin
   // Remove: ZOOM_SCROLL_THRESHOLD, accumulatedScroll
   // Replace with:
   val ZOOM_SENSITIVITY = 0.003  // zoom levels per scroll delta unit
   
   // In scroll handler:
   val zoomDelta = -scrollDelta * ZOOM_SENSITIVITY  // negate: scroll up = zoom in
   val newZoom = (camera.zoom + zoomDelta).coerceIn(2.0, 18.0)
   zoomAt(pointerX, pointerY, camera.zoom, newZoom, size.width, size.height)
   ```

2. **This gives immediate visual feedback on every scroll event**

### Phase 3: Zoom Animation for Button Clicks

**File**: `MapTileRenderer.kt`

1. **Add `Animatable` for zoom button clicks and double-click**
   ```kotlin
   val zoomAnimatable = remember { Animatable(camera.zoom.toFloat()) }
   
   // On +/- button click or double-click:
   coroutineScope.launch {
       zoomAnimatable.animateTo(
           targetValue = newZoom.toFloat(),
           animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)
       )
   }
   // Observe zoomAnimatable.value and update camera.zoom each frame
   ```

2. **For scroll zoom, no animation needed** — it's already continuous

### Phase 4: Reduce TILE_SCALE (Important — fixes blurriness)

**File**: `MapTileRenderer.kt`

1. **Reduce `TILE_SCALE` from 4 to 2**
   - This halves the visual jump per zoom level
   - Loads 4× more tiles (still manageable — 4 tiles → 16 at typical viewport)
   - Much sharper rendering at every zoom level

2. **Future: consider TILE_SCALE = 1 for maximum quality**
   - Would need aggressive tile caching/prefetch
   - Network-per-viewport goes from ~4 to ~20 tiles

### Phase 5: Dual-Zoom Tile Rendering (Polish — eliminates gray flash)

1. **When fractional zoom > baseZoom + 0.5, also load ceiling zoom tiles**
   ```kotlin
   val baseZoom = floor(camera.zoom).toInt()
   val fracPart = camera.zoom - baseZoom
   
   // Always render baseZoom tiles (scaled by 2^fracPart)
   // When fracPart > 0.3, also start loading/fading in (baseZoom + 1) tiles
   ```

2. **Cross-fade between zoom levels**
   ```kotlin
   // Draw baseZoom tiles at alpha = 1.0 - smoothStep(fracPart, 0.3, 0.7)
   // Draw (baseZoom+1) tiles at alpha = smoothStep(fracPart, 0.3, 0.7)
   ```

3. **Fallback rendering**: When a tile isn't loaded yet at the current zoom, scale the parent-zoom tile as a placeholder

### Phase 6: Fix Zoom-to-Pointer (Verifies correctness)

1. **Simplify the `zoomAt` function** using the "geographic anchor" algorithm
2. **Test**: At zoom 5.0, place cursor on Chicago, scroll zoom in. Chicago should stay exactly under the cursor throughout the entire zoom range to 18.0.
3. **Test**: At zoom 15.0, place cursor on a street intersection, scroll zoom out. The intersection should stay fixed.

---

## Implementation Order

| Step | What | Status |
|------|------|--------|
| 1 | Change zoom to Double + scale-based tile rendering | ✅ Done |
| 2 | Replace scroll threshold with direct fractional zoom | ✅ Done |
| 3 | Fix zoomAtPointer() for fractional zoom | ✅ Done |
| 4 | Keep TILE_SCALE=4 (user preference), reduce later if perf allows | ✅ Done |
| 5 | Zoom animation for buttons/double-click (Animatable, 250ms ease-out) | ✅ Done |
| 6 | Adjacent-zoom tile prefetching | ✅ Done |
| 7 | Hover-based zoom+1 prefetching (300ms delay) | ✅ Done |
| 8 | Disk cache TTL changed to 24 hours | ✅ Done |
| 9 | Disk cache 50MB max with 80% eviction | ✅ Already existed |
| 10 | Zoom indicator shows fractional zoom (e.g., "z5.3") | ✅ Done |

All steps are interdependent and were done together.

---

## Detailed Tile Rendering Algorithm

### Current (Broken)

```
For each viewport tile (tx, ty) at zoom = camera.zoom (integer):
  bitmap = cache.get("style/zoom/tx/ty")  
  if bitmap: drawImage(bitmap, dstOffset=(...), dstSize=(1024, 1024))
  else: drawRect(gray, ...)
```

### Proposed (Fractional Zoom)

```
baseZoom = floor(camera.zoom)                // e.g., 5
fracPart = camera.zoom - baseZoom            // e.g., 0.35
scaleFactor = 2.0.pow(fracPart)              // e.g., 1.27

// Compute viewport in world-pixels at fractional zoom
worldSize = 2^camera.zoom * sourceTileSize   // NOT 2^baseZoom
centerWorldPx = latLonToPixelOffset(center, camera.zoom, sourceTileSize)
viewportTopLeft = centerWorldPx - (viewW/2 / scaleFactor / TILE_SCALE,
                                    viewH/2 / scaleFactor / TILE_SCALE)

For each tile at baseZoom in viewport:
  // Tile position in screen space:
  //   The tile covers world-pixel range [tx*256, (tx+1)*256) at baseZoom
  //   At fractional zoom, this range is scaled by scaleFactor
  tileWorldLeft = tx * sourceTileSize
  tileWorldTop = ty * sourceTileSize
  
  // Convert to fractional-zoom world coordinates
  tileFracLeft = tileWorldLeft * scaleFactor  
  tileFracTop = tileWorldTop * scaleFactor
  
  // Screen offset from viewport center
  screenLeft = (tileFracLeft - viewportLeft) * TILE_SCALE
  screenTop = (tileFracTop - viewportTop) * TILE_SCALE
  tileSize = sourceTileSize * TILE_SCALE * scaleFactor
  
  bitmap = cache.get("style/baseZoom/tx/ty")
  if bitmap:
    drawImage(bitmap, dstOffset=(screenLeft, screenTop), dstSize=(tileSize, tileSize))
  else:
    drawRect(gray, ...)
```

Wait — that's incorrect. Let me reconsider. The correct approach:

At **fractional zoom `z`**, a geographic point has world-pixel position:
```
worldPx = (lon + 180) / 360 * 2^z * 256
worldPy = (1 - ln(tan(latRad) + 1/cos(latRad)) / π) / 2 * 2^z * 256
```

This is the standard Web Mercator formula. `2^z` gives the total number of tiles. At zoom 5.35, the world is `2^5.35 ≈ 40.7` tiles wide.

But **tiles exist only at integer zoom levels**. At zoom 5.35, we need tiles from zoom 5. To display zoom-5 tiles at zoom 5.35, we scale them by `2^0.35 ≈ 1.27`.

```
// Center pixel at fractional zoom
centerPx = latLonToPixelOffset(centerLat, centerLon, camera.zoom, sourceTileSize)
// Note: latLonToPixelOffset uses 2^camera.zoom, which works with doubles

// Viewport bounds in world-pixel space at fractional zoom
viewLeft = centerPx - viewWidth / (2 * TILE_SCALE)
viewTop = centerPy - viewHeight / (2 * TILE_SCALE)
viewRight = centerPx + viewWidth / (2 * TILE_SCALE)
viewBottom = centerPy + viewHeight / (2 * TILE_SCALE)

// Scale factor for displaying baseZoom tiles at fractional zoom
scaleFactor = 2^(camera.zoom - baseZoom)  // e.g., 2^0.35 ≈ 1.27

// Which baseZoom tiles cover the viewport?
// Convert viewport bounds from fractional-zoom pixels to baseZoom pixels
baseViewLeft = viewLeft / scaleFactor
baseViewTop = viewTop / scaleFactor
baseViewRight = viewRight / scaleFactor
baseViewBottom = viewBottom / scaleFactor

// Tile range at baseZoom
minTileX = max(0, floor(baseViewLeft / sourceTileSize))
minTileY = max(0, floor(baseViewTop / sourceTileSize))
maxTileX = min(2^baseZoom - 1, floor(baseViewRight / sourceTileSize))
maxTileY = min(2^baseZoom - 1, floor(baseViewBottom / sourceTileSize))

// Draw each tile
for (tx in minTileX..maxTileX) {
  for (ty in minTileY..maxTileY) {
    // Tile position in fractional-zoom world space
    tileFracLeft = tx * sourceTileSize * scaleFactor
    tileFracTop = ty * sourceTileSize * scaleFactor
    tileSize = sourceTileSize * scaleFactor
    
    // Convert to screen coordinates
    screenLeft = (tileFracLeft - viewLeft) * TILE_SCALE
    screenTop = (tileFracTop - viewTop) * TILE_SCALE
    screenTileSize = tileSize * TILE_SCALE
    
    bitmap = cache.get("style/baseZoom/tx/ty")
    if (bitmap) {
      drawImage(bitmap, dstOffset=(screenLeft, screenTop), dstSize=(screenTileSize, screenTileSize))
    }
  }
}
```

This is the correct algorithm. The key insight:
- **Fetch tiles at `floor(zoom)`** (they exist)
- **Scale them by `2^(zoom - floor(zoom))`** to fill the fractional-zoom viewport
- **This gives continuous smooth zoom** between integer levels

---

## Zoom-Toward-Pointer (Revised Algorithm)

```kotlin
fun zoomAtPointer(pointerX: Float, pointerY: Float, oldZoom: Double, newZoom: Double, viewW: Float, viewH: Float) {
    // Step 1: World-pixel position under the pointer at OLD zoom
    val (centerPx, centerPy) = latLonToPixelOffset(camera.centerLat, camera.centerLon, oldZoom, sourceTileSize)
    
    // Pointer offset from center in display pixels, converted to world pixels at old zoom
    val pointerWorldPx = centerPx + (pointerX - viewW / 2f) / TILE_SCALE
    val pointerWorldPy = centerPy + (pointerY - viewH / 2f) / TILE_SCALE
    
    // Convert to geographic coordinates (zoom-independent)
    val (pointerLat, pointerLon) = pixelOffsetToLatLon(pointerWorldPx, pointerWorldPy, oldZoom, sourceTileSize)
    
    // Step 2: What pixel position would that geographic point have at the NEW zoom?
    val (newPointerPx, newPointerPy) = latLonToPixelOffset(pointerLat, pointerLon, newZoom, sourceTileSize)
    
    // Step 3: Compute the new center that keeps the pointer fixed
    // At newZoom, pointer should be at screen position (pointerX, pointerY)
    // So: newCenterPx = newPointerPx - (pointerX - viewW/2) / TILE_SCALE
    //     newCenterPy = newPointerPy - (pointerY - viewH/2) / TILE_SCALE
    val newCenterPx = newPointerPx - (pointerX - viewW / 2f) / TILE_SCALE
    val newCenterPy = newPointerPy - (pointerY - viewH / 2f) / TILE_SCALE
    
    val (newLat, newLon) = pixelOffsetToLatLon(newCenterPx, newCenterPy, newZoom, sourceTileSize)
    
    camera.centerLat = clampLat(newLat)
    camera.centerLon = clampLon(newLon)
    camera.zoom = newZoom
}
```

This is essentially the same algorithm as the current code, but it works with **Double** zoom values and **no intermediate world-pixel conversion via TILE_SCALE** is needed in the center calculation.

**IMPORTANT**: The current code has `pointerOffX = (pointerX - viewW / 2f) / TILE_SCALE` which divides by TILE_SCALE. But `centerPx` is in world pixels at `sourceTileSize` resolution. The offset needs to be in the same units as `centerPx`. Since `latLonToPixelOffset` returns values in `sourceTileSize` units, and the screen offset is in display pixels, the division by TILE_SCALE converts display pixels to source-pixel units. This is correct.

**But wait** — there's a subtle coordinate system confusion. Let me trace through an example:

At zoom 12.35, center at (40.0, -74.0):
- `latLonToPixelOffset(40.0, -74.0, 12.35, 256)` returns world pixels at fractional zoom
- But tiles are only available at zoom 12. The world pixel space at zoom 12.35 doesn't directly correspond to integer tile boundaries.

**This is why the tile rendering must use the `scaleFactor` approach** — we draw the tiles as if they're at the fractional zoom, scaling by `2^fracPart`. The `latLonToPixelOffset` function already handles fractional zoom correctly because `2^12.35` is a well-defined floating-point value.

So the `zoomAtPointer` function above is correct because:
1. It converts to geographic coordinates (lat/lon) at the old zoom — this is zoom-independent
2. It converts back to pixel coordinates at the new zoom — also correct
3. It adjusts the center so the geographic point stays under the pointer

---

## Testing Strategy

### Manual Tests
1. **Smooth zoom test**: Place mouse on a city, scroll slowly. The city should remain precisely under the cursor throughout the entire zoom range (2→18).
2. **Continuous zoom test**: Scroll one notch at a time. Each scroll should produce a small, immediately visible zoom change.
3. **Blur test**: At any integer zoom level, map text should be readable (not 4× upsampled).
4. **Button zoom test**: Click +/− buttons. Should animate smoothly over 250ms.
5. **Satellite/street toggle test**: Switching styles should work correctly at any zoom level.

### Edge Cases
- Zoom near poles (`lat > 85°`) — Web Mercator distortion
- Zoom near international date line (`lon ≈ ±180°`) — wraparound
- Very fast scrolling — zoom should not overshoot bounds
- Multiple rapid +/- clicks — animation should cancel and start new one

---

## Summary of All Changes Needed

| File | Change | Reason |
|------|--------|--------|
| `MapTileRenderer.kt` | `MapCameraState.zoom`: `Int` → `Double` | Fractional zoom |
| `MapTileRenderer.kt` | Replace scroll threshold with direct fractional zoom mapping | Continuous zoom response |
| `MapTileRenderer.kt` | Update tile drawing to apply `2^(zoom - floor(zoom))` scale | Smooth visual interpolation |
| `MapTileRenderer.kt` | Reduce `TILE_SCALE` from 4 to 2 | Reduce baseline blur |
| `MapTileRenderer.kt` | Fix `zoomAt` to use Double zoom | Correct pointer anchoring |
| `MapTileRenderer.kt` | Add `Animatable` for button/double-click zoom | Smooth animated zoom |
| `MapTileRenderer.kt` | Load adjacent-zoom tiles and cross-fade | Eliminate gray flash |
| `LocationPickerDialog.kt` | Adapt to `Double` zoom API | Interface consistency |
| `MapTileRenderTestApp.kt` | Adapt to `Double` zoom API | Interface consistency |