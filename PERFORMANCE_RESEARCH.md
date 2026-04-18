# Canvas Performance Research: Dragging a Bounding Box

## Current Problem Analysis

**What's happening:** When dragging a corner handle, the bounding box feels sluggish despite:
- A fast computer
- 12Hz throttle limiting Canvas redraws
- Position filtering to skip micro-movements

**Root cause hypothesis:** The issue is likely NOT the Canvas redraw rate. Compose's Canvas uses Skia via Skiko, which is already GPU-accelerated. The sluggishness is probably caused by:

1. **Compose recomposition overhead** - Even with throttling, each `displayBox` update triggers:
   - Recomposition of the Canvas composable
   - Re-execution of all `remember` blocks in scope
   - Rebuilding the DrawScope lambda

2. **State collection overhead** - `displayBox.collectAsState()` in Canvas scope

3. **Pointer event blocking** - `awaitPointerEvent()` is a **suspend function** that blocks the main thread waiting for events. Even with throttling, the composition/recomposition cycle competes for thread time with pointer handling.

4. **Unnecessary image redraws** - The entire image is being redrawn on every Canvas recomposition, not just the bounding box overlay.

---

## Options to Improve Performance

### Tier 1: Quick Wins (Low Effort, Likely Helpful)

#### 1. Separate Layers - Don't Redraw the Image Every Frame
**Problem:** Canvas redraws the entire image (potentially megapixels) every time `displayBox` changes.

**Solution:** Use Compose's `graphicsLayer` to cache the image as a GPU layer, separate from the bounding box overlay:

```kotlin
Box(modifier = modifier) {
    // Layer 1: Static image (cached, never redrawn)
    Image(
        bitmap = imageBitmap,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { cacheMode = CacheMode.Enabled }
    )
    
    // Layer 2: Only the bounding box overlay (redrawn at 12Hz)
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRefinementBox(displayBox, selected, zoom)
    }
}
```

**Effort:** ~30 minutes  
**Expected improvement:** Significant - eliminates image redraw entirely

---

#### 2. Use `rememberUpdatedState` for Throttle Variables
**Problem:** The `LaunchedEffect` captures values at composition time. If `boxIndex` changes, the effect restarts, but other values might be stale.

**Solution:** Use `rememberUpdatedState` for values accessed in the loop:

```kotlin
val boxIndexState by rememberUpdatedState(boxIndex)
val stateRef by rememberUpdatedState(state)

LaunchedEffect(isDragging) {
    while (isDragging) {
        stateRef.syncPendingDrag(boxIndexState)
        delay(83L)
    }
}
```

**Effort:** 10 minutes  
**Expected improvement:** Minor - reduces stale closure issues

---

#### 3. Skip Drawing When Box Unchanged
**Problem:** `syncPendingDrag` updates `_displayRefinementBox` even if the box didn't actually change (e.g., invalid shape rejected).

**Solution:** Add a check to only trigger recomposition when the box actually changed:

```kotlin
fun syncPendingDrag(boxIndex: Int): Boolean {
    // ... calculate moved ...
    if (moved == _displayRefinementBox.value) {
        return false // No change, don't trigger recomposition
    }
    _displayRefinementBox.value = moved
    return true
}
```

**Effort:** 15 minutes  
**Expected improvement:** Moderate - reduces wasted recompositions

---

### Tier 2: Architectural Changes (Medium Effort)

#### 4. Use AWT/Swing Canvas (Direct Skia Access)
**Problem:** Compose adds overhead for its state management and recomposition system.

**Solution:** Use Skiko's direct Skia API via `SkiaLayer` or `SwingPanel` from Skiko:

```kotlin
// Using Skiko's SkiaLayer directly - bypasses Compose
val skiaLayer = remember {
    SkiaLayer()
}

Canvas(
    modifier = modifier.onParentSizeChanged { size ->
        skiaLayer.setSize(size.width, size.height)
    }
) {
    // Low-level Skia drawing
    skiaLayer.skiaView?.let { view ->
        view.pictureCanvas { canvas ->
            // Direct Skia API
            canvas.drawRect(...)
        }
    }
}
```

**Effort:** 2-4 hours  
**Expected improvement:** Moderate - removes Compose overhead but complex to implement

---

#### 5. Use `Modifier.drawWithContent` for Layer-Based Drawing
**Problem:** Standard Canvas redraws everything every frame.

**Solution:** Use the `drawWithContent` modifier to create persistent drawing layers:

```kotlin
val cachedImage by remember {
    derivedStateOf { /* pre-render image to ImageBitmap */ }
}

Canvas(modifier = Modifier.fillMaxSize()) {
    drawImage(cachedImage)
    // Draw box on top
    drawRefinementBox(displayBox, selected, zoom)
}
```

Or use `graphicsLayer` with content caching:

```kotlin
Canvas(modifier = Modifier.graphicsLayer {
    compositingMode = CompositingMode.Offscreen
    renderEffect = null // Disable effects for performance
}) {
    // Only bounding box here
}
```

**Effort:** 1-2 hours  
**Expected improvement:** Moderate to significant

---

#### 6. Double Buffering with Offscreen Canvas
**Problem:** Screen tearing or flickering from direct drawing.

**Solution:** Render to an offscreen bitmap, then blit to screen:

```kotlin
val offscreenBitmap = remember {
    image?.let { createSampledImageForRefinement(it, 1.0) }
}

Canvas(modifier = Modifier.fillMaxSize()) {
    // Draw cached bitmap
    offscreenBitmap?.let { drawImage(it.toComposeImageBitmap()) }
    // Draw box
    drawRefinementBox(displayBox, selected, zoom)
}
```

**Effort:** 2-3 hours  
**Expected improvement:** Moderate

---

### Tier 3: Framework Alternatives (High Effort)

#### 7. Use Kotlin/JavaFX with Canvas (If Applicable)
**Problem:** Compose Desktop's Canvas has inherent overhead.

**Solution:** If feasible, use JavaFX's `Canvas` or `GraphicsContext` which has more direct rendering paths.

**Effort:** Major refactoring (weeks)  
**Expected improvement:** Significant but not worth the cost for this feature

---

#### 8. Custom Rendering with Skiko Direct API
**Problem:** Compose wraps Skia, adding overhead.

**Solution:** Use Skiko's raw Skia API directly:

```kotlin
import org.jetbrains.skia.*

val surface = Surface.makeRasterN32Premul(width, height)
val canvas = surface.canvas

Canvas(
    modifier = Modifier.drawBehind {
        // Access native Skia canvas
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.use { nc ->
                val skiaCanvas = org.jetbrains.skiko.GraphicsApi.SKIA.getCanvas(nc)
                // Direct Skia calls
            }
        }
    }
)
```

**Effort:** 4-8 hours  
**Expected improvement:** Moderate - removes Compose drawing overhead

---

#### 9. OpenGL/Vulkan via LWJGL (Overkill)
**Problem:** Need sub-millisecond rendering.

**Solution:** Use raw OpenGL/Vulkan for rendering.

**Effort:** Days to weeks  
**Expected improvement:** Extreme but massively overkill for this use case

---

#### 10. libGDX (Massive Overkill)
**Problem:** Full game engine for a UI component.

**Solution:** libGDX with Scene2D UI.

**Effort:** Days  
**Expected improvement:** Extreme but completely overkill

---

#### 11. KorGE (Kotlin Game Engine)
**Problem:** Need dedicated game-loop rendering.

**Solution:** KorGE with views.

**Effort:** Days  
**Expected improvement:** Extreme but overkill

---

## Recommended Approach

### Try First (in order):

1. **Option 1 (Separate Layers)** - Most likely to help with minimal effort. Separating the static image from the dynamic overlay is a standard technique.

2. **Option 3 (Skip Unchanged)** - Simple optimization to reduce wasted recompositions.

3. **Option 2 (rememberUpdatedState)** - Quick fix for potential stale closure issues.

### If Still Sluggish:

4. **Option 5 (`graphicsLayer` with caching)** - More advanced layer management.

5. **Option 4 (Skiko Direct API)** - Bypass Compose drawing entirely.

### Avoid:

- Options 7-11 are massive overkill for this use case. A bounding box overlay should never require a game engine.

---

## Diagnostic Steps Before Implementing

To confirm where time is being spent, add profiling:

```kotlin
val measureTime = measureTimedValue {
    state.syncPendingDrag(boxIndex)
}
println("syncPendingDrag: ${measureTime.duration.inMicroseconds()}μs")

// Or in Canvas
Canvas(modifier = Modifier.fillMaxSize()) {
    val start = System.nanoTime()
    // drawing code
    val elapsed = (System.nanoTime() - start) / 1000
    if (elapsed > 1000) { // > 1ms
        println("SLOW DRAW: ${elapsed}μs")
    }
}
```

Typical times on a modern machine:
- Drawing 4 lines + 4 circles: **< 1ms** ✓
- Drawing a 4K image: **2-5ms** ⚠️
- Compose recomposition: **1-10ms** ⚠️

If drawing is fast (<2ms) but it feels sluggish, the problem is likely **recomposition overhead**, not drawing performance.
