# Petrie Photo Importer - Refined Implementation Plan

**Created:** 2026-04-10  
**Last Updated:** 2026-04-10

---

## Current State Analysis

### What Exists

| Component | Current State | Action Required |
|-----------|---------------|-----------------|
| `PhotoScanScreen.kt` | Main workflow orchestrator | Major rewrite for new screen flow |
| `PhotoScanPreviewScreen.kt` | Detection/correction UI | **DELETE and replace entirely** |
| `PhotoScanMetadataScreen.kt` | Metadata entry | Enhance with thumbnail click preview |
| `Loader` composable | Custom corner animation | **DELETE**, replace with CubeGrid |
| `PhotoScanState.kt` | State management | Enhance with new models |
| `DetectedPhoto` / `PhotoCorner` | Coordinate models | **Rewrite** to percentage-based |

### What Doesn't Exist

| Component | Priority |
|-----------|----------|
| Screen 2: Boundary Refinement wizard | High |
| Screen 3: Photo Processing with preview | High |
| Screen 4: Full-screen thumbnail preview | Medium |
| Screen 5: Enhanced review cards | Medium |
| Screen 6: Exporting with sequential progress | Medium |
| Preview Cache | High |
| Naming Pattern DSL | Medium |
| Processing Mode selector | High |
| Aspect Ratio selector | High |

---

## Implementation Roadmap

### Phase 0: Foundation (Safety Net)

**Goal:** Establish baseline, ensure no regressions.

1. **Commit current code** to git (if not already committed)
2. **Run full test suite** - verify current tests pass
3. **Create backup branch:** `git checkout -b backup-before-refactor`

**Verification:** `git status`, test suite passes

---

### Phase 1: Loading Animation (Quick Win)

**Goal:** Replace broken loader with CubeGrid.

#### 1.1 Add Compose-SpinKit Dependency

```kotlin
// build.gradle.kts
implementation("com.github.OCNYang.Compose-SpinKit:library:1.0.5") {
    exclude("com.github.jitpack")
}

// settings.gradle.kts (if jitpack not already configured)
dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://jitpack.io") }
    }
}
```

#### 1.2 Create Loading Overlay Component

```kotlin
package org.kryspetrie.fileimport.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.dp
import com.github.OCNYang.composekit.composecomponent.components.spinner.*

@Composable
fun LoadingOverlay(
    isLoading: Boolean,
    message: String = "Processing...",
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        content()
        
        if (isLoading) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CubeGridLoadingIndicator(
                        modifier = Modifier.size(60.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(message, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
fun CubeGridLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    // Wrapper to match the library's actual API
    // Note: Verify exact API from library documentation
    Box(modifier = modifier) {
        // Implementation based on library
    }
}
```

#### 1.3 Delete Old Loader

```bash
# Remove from PhotoScanScreen.kt
# - Delete the entire `Loader` composable function
# - Replace any calls with LoadingOverlay or CubeGridLoadingIndicator
```

**Verification:** Loading state shows CubeGrid spinner, no errors

---

### Phase 2: Coordinate System Migration (Critical)

**Goal:** Change from pixel coordinates to percentage (0.0-100.0).

#### 2.1 Define New Models

```kotlin
// domain/model/PhotoModels.kt

/**
 * Represents a point as percentage of image dimensions (0.0-100.0).
 * Resolution-independent coordinate system.
 */
data class PercentPoint(
    val x: Double,  // 0.0 to 100.0
    val y: Double   // 0.0 to 100.0
) {
    fun toPixel(imageWidth: Int, imageHeight: Int): PixelPoint = PixelPoint(
        x = (x / 100.0 * imageWidth).toFloat(),
        y = (y / 100.0 * imageHeight).toFloat()
    )
    
    companion object {
        fun fromPixel(x: Float, y: Float, imageWidth: Int, imageHeight: Int): PercentPoint = PercentPoint(
            x = (x / imageWidth * 100.0),
            y = (y / imageHeight * 100.0)
        )
    }
}

data class PixelPoint(val x: Float, val y: Float)

/**
 * Bounding box corners as percentages.
 */
data class PercentBoundingBox(
    val topLeft: PercentPoint,
    val topRight: PercentPoint,
    val bottomLeft: PercentPoint,
    val bottomRight: PercentPoint
) {
    // Conversion utilities
    fun toPixel(imageWidth: Int, imageHeight: Int): PixelBoundingBox
    fun translate(deltaX: Double, deltaY: Double): PercentBoundingBox
    fun moveCorner(corner: CornerType, newPosition: PercentPoint): PercentBoundingBox
    
    // Hit testing with tolerance
    fun findNearestCorner(point: PercentPoint, tolerance: Double = 3.0): CornerType?
    fun isPointInside(point: PercentPoint): Boolean
}
```

#### 2.2 Create Conversion Utilities

```kotlin
// infrastructure/wizard/CoordinateConverter.kt

object CoordinateConverter {
    fun toPercent(x: Float, y: Float, imageWidth: Int, imageHeight: Int): PercentPoint
    fun toPixel(point: PercentPoint, imageWidth: Int, imageHeight: Int): PixelPoint
    
    // For batch operations
    fun boxToPercent(box: PixelBoundingBox, imageWidth: Int, imageHeight: Int): PercentBoundingBox
    fun boxToPixel(box: PercentBoundingBox, imageWidth: Int, imageHeight: Int): PixelBoundingBox
}
```

#### 2.3 Update DetectedPhoto Model

```kotlin
data class DetectedPhoto(
    val id: String = UUID.randomUUID().toString(),
    val boundingBox: PercentBoundingBox,
    val processingConfig: PhotoProcessingConfig = PhotoProcessingConfig(),
    val metadata: PhotoMetadata = PhotoMetadata()
) {
    // Get display coordinates for current image dimensions
    fun getDisplayCorners(imageWidth: Int, imageHeight: Int): PixelBoundingBox
    fun moveBy(deltaX: Double, deltaY: Double): DetectedPhoto
    fun moveCorner(corner: CornerType, position: PercentPoint): DetectedPhoto
}
```

#### 2.4 Update PhotoScanState

```kotlin
class PhotoScanState {
    // ... existing code ...
    
    // New methods for percentage-based coordinates
    fun updatePhotoCorner(photoId: String, corner: CornerType, xPercent: Double, yPercent: Double)
    fun movePhoto(photoId: String, deltaXPercent: Double, deltaYPercent: Double)
}
```

#### 2.5 Write Conversion Tests

```kotlin
class CoordinateConverterTest {
    @Test fun `should convert pixel to percentage losslessly`()
    @Test fun `should convert percentage to pixel losslessly`()
    @Test fun `should handle edge cases at 0 and 100 percent`()
    @Test fun `should translate box correctly`()
    @Test fun `should find nearest corner with tolerance`()
}
```

**Verification:** All conversion tests pass, UI still functional

---

### Phase 3: Screen 1 Rewrite - Detection (High Priority)

**Goal:** Replace broken PhotoScanPreviewScreen with performant detection UI.

#### 3.1 Delete and Create New File

```bash
# Delete old file
rm src/main/kotlin/org/kryspetrie/fileimport/ui/screens/PhotoScanPreviewScreen.kt

# Create new screen
touch src/main/kotlin/org/kryspetrie/fileimport/ui/screens/wizard/DetectionScreen.kt
```

#### 3.2 Implement Performant Canvas

```kotlin
// ui/screens/wizard/DetectionScreen.kt

@Composable
fun DetectionScreen(
    image: BufferedImage,
    detectedPhotos: List<DetectedPhoto>,
    selectedPhotoId: String?,
    onPhotoSelect: (String?) -> Unit,
    onPhotoAdd: (DetectedPhoto) -> Unit,
    onPhotoRemove: (String) -> Unit,
    onCornerMove: (photoId: String, corner: CornerType, position: PercentPoint) -> Unit,
    onPhotoMove: (photoId: String, delta: PercentPoint) -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit
) {
    var imageSize by remember { mutableStateOf(IntSize.Zero) }
    
    // Use derivedStateOf to prevent recomposition during drag
    val displayParams = remember(imageSize, image.width, image.height) {
        derivedStateOf { calculateDisplayParams(imageSize, image.width, image.height) }
    }
    
    // Performance-critical: Separate drag state from compose state
    val dragState = remember { mutableStateOf(DragState.IDLE) }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        TopAppBar { /* Navigation and title */ }
        
        // Main canvas - NO zoom controls
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.DarkGray)
                .onSizeChanged { imageSize = it }
        ) {
            DetectionCanvas(
                image = image,
                photos = detectedPhotos,
                selectedPhotoId = selectedPhotoId,
                displayParams = displayParams.value,
                dragState = dragState,
                onPhotoSelect = onPhotoSelect,
                onCornerMove = onCornerMove,
                onPhotoMove = onPhotoMove
            )
        }
        
        // Photo list (bottom)
        PhotoListPanel(
            photos = detectedPhotos,
            selectedPhotoId = selectedPhotoId,
            onSelect = onPhotoSelect,
            onRemove = onPhotoRemove
        )
        
        // Bottom controls (Next/Skip/Back only)
        BottomControls(
            onBack = onBack,
            onSkip = onSkip,
            onNext = onNext
        )
    }
}
```

#### 3.3 Implement Performant DetectionCanvas

```kotlin
@Composable
private fun DetectionCanvas(
    image: BufferedImage,
    photos: List<DetectedPhoto>,
    selectedPhotoId: String?,
    displayParams: DisplayParams,
    dragState: MutableState<DragState>,
    onPhotoSelect: (String?) -> Unit,
    onCornerMove: (String, CornerType, PercentPoint) -> Unit,
    onPhotoMove: (String, PercentPoint) -> Unit
) {
    // Pre-sample image for display (only recomposes when scale changes)
    val displayImage = remember(image, displayParams.scale) {
        createSampledDisplayImage(image, displayParams.scale)
    }
    
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(photos) {
                // Single, optimized gesture detector
                detectInteractionGestures(
                    onTap = { position ->
                        handleTap(position, photos, displayParams, onPhotoSelect)
                    },
                    onDragStart = { position, phase ->
                        handleDragStart(position, photos, displayParams, dragState)
                    },
                    onDrag = { delta, phase ->
                        handleDrag(delta, phase, photos, displayParams, dragState, 
                                   onCornerMove, onPhotoMove)
                    },
                    onDragEnd = {
                        handleDragEnd(dragState)
                    }
                )
            }
    ) {
        // Draw image (using pre-sampled version)
        drawDisplayImage(displayImage, displayParams)
        
        // Draw overlays (using graphicsLayer for performance)
        photos.forEach { photo ->
            drawPhotoOverlay(
                photo = photo,
                isSelected = photo.id == selectedPhotoId,
                displayParams = displayParams,
                dragState = dragState.value
            )
        }
    }
}
```

#### 3.4 Implement Optimized Drag Handling

```kotlin
private enum class DragState {
    IDLE,
    DRAGGING_CORNER,
    DRAGGING_BOX
}

private data class DragContext(
    val photoId: String,
    val corner: CornerType?,
    val startPosition: PercentPoint
)

// Performance optimizations:
// 1. Use pointerInput with specific gesture types
// 2. Batch state updates - only apply on DragEnd
// 3. Calculate hit detection once at drag start
// 4. Use percentage-based coordinates for resolution independence
private fun PointerScope.detectInteractionGestures(
    onTap: (Offset) -> Unit,
    onDragStart: (Offset, MutableState<DragState>) -> DragContext?,
    onDrag: (Offset, DragState, DragContext?, ...) -> Unit,
    onDragEnd: (MutableState<DragState>) -> Unit
) {
    // Implementation with accumulateForDrag
    // Batch updates during drag, apply on release
}
```

#### 3.5 Remove Broken Features

- ❌ Remove zoom controls (lines 150-170 in current code)
- ❌ Remove "+/- photos" control (lines 180-210)
- ❌ Remove crop button
- ❌ Remove rescan button
- ❌ Remove full-screen editor dialog

**Verification:**
- [ ] Can click on bounding box to select
- [ ] Can drag corners smoothly (60fps)
- [ ] Can drag entire box smoothly
- [ ] Photo list updates on selection
- [ ] Next/Skip/Back work correctly

---

### Phase 4: Screen 2 - Boundary Refinement (New Screen)

**Goal:** Wizard for cycling through photos with corner refinement.

#### 4.1 Create RefinementScreen

```kotlin
// ui/screens/wizard/RefinementScreen.kt

@Composable
fun RefinementScreen(
    photos: List<DetectedPhoto>,
    currentIndex: Int,
    image: BufferedImage,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCornerMove: (corner: CornerType, position: PercentPoint) -> Unit,
    onPhotoMove: (delta: PercentPoint) -> Unit,
    onBack: () -> Unit,
    onProceed: () -> Unit
) {
    val currentPhoto = photos.getOrNull(currentIndex) ?: return
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Navigation bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton("← Previous", enabled = currentIndex > 0, onClick = onPrevious)
            Text("${currentIndex + 1} / ${photos.size}", style = MaterialTheme.typography.titleMedium)
            OutlinedButton("Next →", enabled = currentIndex < photos.size - 1, onClick = onNext)
        }
        
        // Image preview (cropped + 20% padding)
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().background(Color.Black)
        ) {
            RefinementCanvas(
                image = image,
                photo = currentPhoto,
                onCornerMove = onCornerMove,
                onPhotoMove = onPhotoMove
            )
        }
        
        // Bottom controls
        BottomControls(
            onBack = onBack,
            onNext = onProceed
        )
    }
}
```

#### 4.2 Implement RefinementCanvas with Corner Tolerance

```kotlin
@Composable
private fun RefinementCanvas(
    image: BufferedImage,
    photo: DetectedPhoto,
    onCornerMove: (CornerType, PercentPoint) -> Unit,
    onPhotoMove: (PercentPoint) -> Unit
) {
    // Hit tolerance radius (percentage points)
    val CORNER_HIT_TOLERANCE = 5.0  // 5% tolerance for easier grabbing
    
    Canvas(modifier = Modifier.fillMaxSize().pointerInput(photo) {
        // Detect corner hits with tolerance
        // Allow dragging entire box by clicking inside
    }) {
        // Draw cropped and padded image region
        val cropRegion = calculateCropRegion(photo, paddingPercent = 20.0)
        drawCroppedImage(image, cropRegion)
        
        // Draw overlay with large, visible corners
        drawRefinementOverlay(photo, cropRegion)
    }
}
```

**Verification:**
- [ ] Can cycle through photos with prev/next
- [ ] Corners are easy to grab (tolerance works)
- [ ] Box can be moved by dragging center
- [ ] Image shows cropped region with padding

---

### Phase 5: Screen 3 - Photo Processing (New Screen)

**Goal:** Preview processing effects with configuration options.

#### 5.1 Create ProcessingScreen

```kotlin
// ui/screens/wizard/ProcessingScreen.kt

@Composable
fun ProcessingScreen(
    photos: List<DetectedPhoto>,
    currentIndex: Int,
    previewCache: PreviewCache,
    onProcessingConfigChange: (PhotoProcessingConfig) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onProceed: () -> Unit
) {
    var isProcessing by remember { mutableStateOf(false) }
    val currentPhoto = photos.getOrNull(currentIndex)
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Navigation bar
        PhotoNavigator(
            currentIndex = currentIndex,
            totalPhotos = photos.size,
            onPrevious = onPrevious,
            onNext = onNext
        )
        
        // Preview area
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (isProcessing) {
                CubeGridLoadingIndicator()
                Text("Generating preview...")
            } else {
                Image(previewCache[currentPhoto?.id], contentDescription = null)
            }
        }
        
        // Configuration controls
        ProcessingControls(
            config = currentPhoto?.processingConfig,
            onConfigChange = onProcessingConfigChange,
            enabled = !isProcessing
        )
        
        // Bottom controls
        BottomControls(onBack = onBack, onNext = onProceed)
    }
}
```

#### 5.2 Implement Processing Modes

```kotlin
enum class ProcessingMode {
    CROP_ONLY,
    ROTATE_ONLY,
    PERSPECTIVE_CORRECTION
}

enum class AspectRatio(val width: Int, val height: Int) {
    RATIO_2_3(2, 3),
    RATIO_4_3(4, 3),
    RATIO_16_9(16, 9),
    RATIO_16_10(16, 10),
    RATIO_1_1(1, 1),
    DEFAULT(null, null)
}

fun calculateAutoRotation(corners: PercentBoundingBox): Double {
    // Calculate average angle of longest edges
    // Return degrees for rotation
}
```

#### 5.3 Implement Preview Cache

```kotlin
class PreviewCache {
    private val cache = ConcurrentHashMap<String, ImageBitmap>()
    
    suspend fun getPreview(
        photoId: String,
        image: BufferedImage,
        config: PhotoProcessingConfig,
        downsampledWidth: Int = 800
    ): ImageBitmap {
        val key = "$photoId-${config.hashCode()}"
        return cache.getOrPut(key) {
            generatePreview(image, config, downsampledWidth)
        }
    }
    
    fun clear() = cache.clear()
}
```

**Verification:**
- [ ] Processing mode radio buttons work
- [ ] Aspect ratio dropdown enables/disables correctly
- [ ] Preview updates when config changes
- [ ] Loading spinner shows during processing
- [ ] Preview is cached

---

### Phase 6: Screen 4 - Metadata (Enhancement)

**Goal:** Add full-screen preview on thumbnail click.

#### 6.1 Update PhotoScanMetadataScreen

```kotlin
@Composable
fun PhotoScanMetadataScreen(
    photos: List<DetectedPhoto>,
    // ... existing params ...
    onThumbnailClick: (DetectedPhoto) -> Unit  // NEW
) {
    var previewPhoto by remember { mutableStateOf<DetectedPhoto?>(null) }
    
    // Existing UI...
    
    // Add thumbnail click handler
    MetadataCard(
        photo = photo,
        thumbnail = cachedThumbnails[photo.id],
        onThumbnailClick = { onThumbnailClick(photo) }
    )
    
    // Full-screen preview overlay
    previewPhoto?.let { photo ->
        FullScreenPreviewDialog(
            image = cachedPreviewImages[photo.id],
            onDismiss = { previewPhoto = null }
        )
    }
}
```

#### 6.2 Implement FullScreenPreviewDialog

```kotlin
@Composable
private fun FullScreenPreviewDialog(
    image: ImageBitmap,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
                .clickable(onClick = onDismiss)
        ) {
            Image(
                image,
                contentDescription = "Full preview",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(Icons.Default.Close, "Close", tint = Color.White)
            }
        }
    }
}
```

**Verification:**
- [ ] Clicking thumbnail opens full-screen preview
- [ ] X button closes preview
- [ ] Clicking outside closes preview

---

### Phase 7: Screen 5 & 6 - Review and Export (Enhancement)

**Goal:** Enhance existing screens per plan requirements.

#### 7.1 Review Screen Enhancements

```kotlin
@Composable
fun PhotoScanSummaryScreen(
    photos: List<DetectedPhoto>,
    metadata: Map<String, PhotoMetadata>,
    exportConfig: ExportConfig,
    onEditPhoto: (String) -> Unit,
    onExport: () -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Scrollable list of review cards
        LazyColumn {
            photos.forEach { photo ->
                item {
                    ReviewCard(
                        photo = photo,
                        thumbnail = cachedThumbnails[photo.id],
                        metadata = metadata[photo.id],
                        processingMode = photo.processingConfig.mode,
                        aspectRatio = photo.processingConfig.aspectRatio,
                        filename = generateFilename(exportConfig.namingPattern, photo),
                        onEdit = { onEditPhoto(photo.id) }
                    )
                }
            }
        }
        
        // Export button
        Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Save, null)
            Text("Export Images")
        }
    }
}
```

#### 7.2 Exporting Screen Enhancements

```kotlin
@Composable
fun PhotoScanExportingScreen(
    progress: ExportProgress,
    totalPhotos: Int,
    currentPhoto: DetectedPhoto?,
    currentThumbnail: ImageBitmap?,
    destination: Path,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Current photo preview
        currentThumbnail?.let {
            Image(it, modifier = Modifier.size(300.dp))
        }
        
        // Progress
        Text("${progress.processedPhotos} of $totalPhotos")
        
        // Loading indicator
        CubeGridLoadingIndicator()
        
        // Destination
        Text("Exporting to: $destination")
        
        // Cancel button
        OutlinedButton("Cancel Export", onClick = onCancel)
    }
}
```

#### 7.3 Export Completion

```kotlin
@Composable
fun ExportCompleteScreen(
    exportedCount: Int,
    onCancelScan: () -> Unit,
    onContinueBatch: () -> Unit
) {
    Column {
        Icon(Icons.Default.CheckCircle, contentDescription = null)
        Text("Export completed!")
        Text("$exportedCount photos exported")
        
        Row {
            OutlinedButton("Cancel Scan", onClick = onCancelScan)
            Button("Continue to Next Batch", onClick = onContinueBatch)
        }
    }
}
```

**Verification:**
- [ ] All photos shown in review
- [ ] Export shows progress
- [ ] Completion screen shows buttons
- [ ] Continue to Next Batch works

---

## Test Strategy

### Unit Tests
```kotlin
// CoordinateConverterTest
- `toPercent converts correctly`
- `toPixel converts correctly`
- `translation preserves coordinates`

// BoundingBoxTest
- `findNearestCorner with tolerance`
- `isPointInside for valid points`
- `moveCorner updates correct corner`

// NamingPatternTest
- `parses valid pattern`
- `generates filename correctly`
- `handles duplicate names`
```

### Integration Tests
```kotlin
// PhotoScanWorkflowTest
- `full workflow completes`
- `navigation between screens`
- `state persists across screens`

// DragPerformanceTest
- `corner drag maintains 60fps`
- `box drag maintains 60fps`
```

### Manual Testing Checklist
- [ ] Loading animation displays correctly
- [ ] Can select photo by clicking inside bounding box
- [ ] Corner dragging is smooth (not jerky)
- [ ] Box dragging is smooth
- [ ] Corner selection tolerance works (don't need exact click)
- [ ] Photo cycling in refinement screen works
- [ ] Processing preview generates correctly
- [ ] Metadata entry persists
- [ ] Review cards show all info
- [ ] Export completes successfully

---

## Open Questions (Pending Clarification)

1. ~~Should "Continue to Next Batch" reset all state or preserve configuration?~~ ✅ **FIXED: Reset state except metadata defaults**
2. ~~For aspect ratio "default", how should portrait/landscape be determined?~~ ✅ **FIXED: By average edge lengths**
3. ~~Should metadata fields be pre-filled from original EXIF?~~ ✅ **FIXED: "Set Default" button, applies to future batches, only fills blank values first time**

### Configuration File (config.yaml)

```yaml
# Default configuration values
photo_scan:
  refinement_padding_percent: 20.0  # 20% padding around cropped boundary
  
metadata_defaults:
  # Persisted default values for metadata fields
  # Only applied to blank fields on first use, then preserved for future batches
  notes: ""
  photographer: ""
  tags: ""
  keywords: ""
  subjects: ""

naming_pattern:
  default: "{original_name}_{date_time_original}_{number}"
```

### Metadata "Set Default" Behavior

| Action | Result |
|--------|--------|
| Click "Set Default" on blank field | Clears the default for that field |
| Click "Set Default" on filled field | Saves value as default for future batches |
| New batch, field is blank | Pre-fill from default (if set) |
| New batch, field has value | Keep existing value (don't override) |

### Configuration Loading

```kotlin
data class AppConfig(
    val photoScan: PhotoScanConfig = PhotoScanConfig(),
    val metadataDefaults: MetadataDefaults = MetadataDefaults(),
    val namingPattern: NamingPatternConfig = NamingPatternConfig()
)

data class PhotoScanConfig(
    val refinementPaddingPercent: Double = 20.0
)

data class MetadataDefaults(
    val notes: String? = null,
    val photographer: String? = null,
    val tags: String? = null,
    val keywords: String? = null,
    val subjects: String? = null
)

// Load from config.yaml on app start
object ConfigLoader {
    suspend fun loadConfig(): AppConfig {
        val yaml = Yaml().load<Map<String, Any>>(configFile)
        return parseConfig(yaml)
    }
    
    suspend fun saveDefaults(metadataDefaults: MetadataDefaults) {
        // Persist to config.yaml when user clicks "Set Default"
    }
}
```

---

## File Deletions

| File | Reason |
|------|--------|
| `PhotoScanPreviewScreen.kt` | Replaced with `DetectionScreen.kt` and `RefinementScreen.kt` |
| `Loader` composable (in PhotoScanScreen.kt) | Replaced with CubeGrid |

## File Creations

| File | Purpose |
|------|---------|
| `ui/components/LoadingOverlay.kt` | Reusable loading overlay component |
| `ui/screens/wizard/DetectionScreen.kt` | Screen 1: Detection view |
| `ui/screens/wizard/RefinementScreen.kt` | Screen 2: Corner refinement |
| `ui/screens/wizard/ProcessingScreen.kt` | Screen 3: Photo processing |
| `ui/components/FullScreenPreview.kt` | Full-screen image preview |
| `domain/model/PhotoModels.kt` | New percentage-based models |
| `infrastructure/wizard/CoordinateConverter.kt` | Coordinate conversion utilities |
| `infrastructure/wizard/PreviewCache.kt` | Preview image cache |

---

## Success Criteria

1. ✅ CubeGrid loading animation works (replaces broken Loader)
2. ✅ Coordinates stored as percentages (resolution independent)
3. ✅ Detection screen is performant (60fps drag without jank)
4. ✅ Corner tolerance allows easy grabbing (not exact hover required)
5. ✅ All 6 screens implement plan requirements
6. ✅ Metadata "Set Default" persists across batches
7. ✅ Configuration read from config.yaml
8. ✅ End-to-end workflow completes successfully
9. ✅ All existing tests pass
10. ✅ New tests cover critical paths
