# Petrie Photo Importer - Photo Scan Wizard Plan

**Created:** 2026-04-10  
**Last Updated:** 2026-04-10

---

## Overview

This plan outlines the implementation of a 6-screen photo scan wizard for extracting individual photos from scanned documents or group photos. The wizard handles automatic CV detection, manual refinement, processing preview, metadata entry, review, and export.

**Critical Constraint:** Do NOT modify the CV photo detection algorithm.

---

## Technology Stack

- **UI Framework:** Jetpack Compose (Desktop)
- **Loading Animation:** [Compose-SpinKit CubeGrid](https://github.com/OCNYang/Compose-SpinKit) (`com.github.OCNYang.Compose-SpinKit:library:1.0.5`)
- **DI:** Koin
- **Architecture:** Hexagonal (Ports & Adapters)

---

## Core Data Model Changes

### Coordinate System
All bounding box coordinates stored as **percentage of image dimensions (0.0-100.0)** for resolution independence.

```kotlin
data class BoundingBoxCorners(
    val topLeft: Point,      // x, y as 0.0-100.0 percentages
    val topRight: Point,
    val bottomRight: Point,
    val bottomLeft: Point
)

data class Point(
    val x: Double,  // 0.0 to 100.0
    val y: Double   // 0.0 to 100.0
)
```

### PhotoProcessingConfig (per photo)
```kotlin
data class PhotoProcessingConfig(
    val processingMode: ProcessingMode,  // CROP_ONLY, ROTATE_ONLY, PERSPECTIVE
    val aspectRatio: AspectRatio?,        // 2:3, 4:3, 16:9, 16:10, 1:1, null (default)
    val rotationDegrees: Double,           // Auto-calculated or manual override
    val perspectiveCorrection: Boolean
)

enum class ProcessingMode { CROP_ONLY, ROTATE_ONLY, PERSPECTIVE_CORRECTION }
enum class AspectRatio(val width: Int, val height: Int) {
    RATIO_2_3(2, 3),
    RATIO_4_3(4, 3),
    RATIO_16_9(16, 9),
    RATIO_16_10(16, 10),
    RATIO_1_1(1, 1),
    DEFAULT(null, null)
}
```

### PhotoMetadata (per photo)
```kotlin
data class PhotoMetadata(
    val notes: String? = null,
    val dateTaken: LocalDateTime? = null,
    val originalDate: OriginalDatePrecision? = null,  // YEAR, YEAR_MONTH, YEAR_MONTH_DAY
    val tags: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
    val photographer: String? = null,
    val subjects: List<String> = emptyList()
)

enum class OriginalDatePrecision { YEAR, YEAR_MONTH, YEAR_MONTH_DAY }
```

### ExportConfig
```kotlin
data class ExportConfig(
    val namingPattern: String,  // e.g., "{original_name}_{date_time_original}_{number}"
    val destination: Path,
    val metadata: PhotoMetadata
)
```

---

## Implementation Phases

### Phase 1: Foundation & Loading Animation

**Goal:** Establish core infrastructure and fix loading animation.

#### 1.1 Add Compose-SpinKit Dependency
```kotlin
// build.gradle.kts
implementation("com.github.OCNYang.Compose-SpinKit:library:1.0.5") {
    exclude("com.github.jitpack")
}
```

#### 1.2 Create Loading Overlay Component
```kotlin
@Composable
fun LoadingOverlay(
    isLoading: Boolean,
    message: String = "Processing...",
    content: @Composable () -> Unit
)
```

**Test:** Verify CubeGrid spinner appears during async operations.

#### 1.3 Refactor Coordinate System
- Migrate all `Point` usage to percentage-based coordinates
- Create conversion utilities: `toAbsolute(imageWidth, imageHeight)` and `toPercentage(imageWidth, imageHeight)`
- Update all BoundingBox operations to work with percentages

**Test:** 
- Create test that verifies percentage conversion is lossless for common resolutions
- Verify coordinates scale correctly for preview vs. full-resolution

#### 1.4 Define Naming Pattern DSL
```kotlin
data class NamingPattern(
    val pattern: String,  // e.g., "{original_name}_{date}"
    val availableFields: Set<String>  // derived from pattern
)

// Valid tokens: {original_name}, {date}, {date_time}, {date_time_original}, {number}, {tags}
// Default: "{original_name}_{date_time_original}_{number}"
```

**Test:**
- Parse pattern and extract tokens
- Generate filename from pattern with sample data
- Handle duplicate filenames with incrementing number

---

### Phase 2: Screen 1 - Initial Photo Detection (High Priority)

**Goal:** Fix broken functionality and create a performant bounding box editing experience.

#### 2.1 Remove Broken Features
- Remove floating control for "up / down / re-detect" photos
- Remove non-functional zoom/pan features
- Remove "crop" button

#### 2.2 Implement Performant Drag-and-Drop

**Root Cause Analysis:**
Current issues:
1. Recomposing entire UI on each frame
2. Not using `graphicsLayer` for transforms
3. Calculating bounding boxes on every drag event
4. Too many state updates per frame

**Performance Optimizations:**

1. **Use `graphicsLayer` for transforms:**
```kotlin
Box(
    modifier = Modifier.graphicsLayer {
        translationX = dragOffset.x
        translationY = dragOffset.y
    }
) {
        // Only what's visible inside
    }
```

2. **Separate drag state from compose state:**
```kotlin
// Use remember for drag state to avoid recomposition
val dragState = remember { mutableStateOf(DragState.IDLE) }

// Use snapshotFlow for drag updates
scope.launch {
    snapshotFlow { dragState.value }
        .filter { it != DragState.DRAGGING }
        .collect { /* update box position once drag ends */ }
}
```

3. **Lazy evaluation of bounding box geometry:**
```kotlin
val boxGeometry = remember(box) {
    derivedStateOf { calculateBoxGeometry(box) }  // Only recalculates when box changes
}
```

4. **Use `pointerInput` with direct manipulation:**
```kotlin
Modifier.pointerInput(Unit) {
    detectDragGestures(
        onDragStart = { offset -> /* calculate hit target */ },
        onDrag = { change, dragAmount -> /* apply delta, not recalculate */ },
        onDragEnd = { /* commit change to state */ }
    )
}
```

5. **Corner proximity detection with tolerance:**
```kotlin
val CORNER_HIT_TOLERANCE = 3.0  // percentage points

fun findNearestCorner(point: Point, corners: List<Point>): CornerHit? {
    return corners.minByOrNull { distance(it, point) }
        ?.takeIf { distance(it, point) <= CORNER_HIT_TOLERANCE }
}
```

6. **Batch state updates:**
```kotlin
val pendingChanges = mutableStateOf<List<BoxChange>>(emptyList())

// Accumulate changes during drag, apply on DragEnd
fun onDrag(corner: Corner, delta: Point) {
    pendingChanges.add(BoxChange(corner, delta))
}
```

**Test:**
- Drag a box 100 pixels - verify smooth 60fps movement
- Drag a corner - verify corner moves, not box
- Click near corner - verify proximity detection works
- Rapid multiple drags - verify no memory leaks

#### 2.3 Implement Click-to-Select Bounding Box
- Click anywhere within bounding box to select it
- Selected box should show visual indicator (highlighted border, handles)
- Show list of detected photos with selection sync

**Test:**
- Click inside bounding box - verify it's selected
- Click on bounding box in list - verify it highlights in canvas
- Multi-select (future consideration)

---

### Phase 3: Screen 2 - Photo Boundary Refinement

**Goal:** Create wizard cycling through photos with performant corner refinement.

#### 3.1 Navigation Controls
```kotlin
Row {
    Button("← Previous Photo", enabled = currentIndex > 0)
    Text("${currentIndex + 1} / ${totalPhotos}")
    Button("Next Photo →", enabled = currentIndex < totalPhotos - 1)
}
```

#### 3.2 Image Preview with Bounding Overlay
- Show cropped photo + 20% padding around detected boundary
- Render bounding box overlay with draggable corners
- Overlay should use same performance techniques as Screen 1

#### 3.3 Corner Refinement with Tolerance
```kotlin
@Composable
fun DraggableCorner(
    position: Point,
    onDrag: (delta: Point) -> Unit,
    onDragEnd: () -> Unit
) {
    // Visual representation of corner (larger hit area than visual)
    Box(
        modifier = Modifier
            .size(12.dp)  // Visual size
            .pointerInput(Unit) {
                // Hit area is 24.dp for easier grabbing
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        // Convert screen delta to image percentage delta
                        val percentageDelta = screenToPercentageDelta(dragAmount)
                        onDrag(percentageDelta)
                    }
                )
            }
    )
}
```

#### 3.4 Box Move Gesture
```kotlin
// When clicking center of box (not corners)
Modifier.pointerInput(box) {
    detectDragGestures(
        onDragStart = { /* enter move mode */ },
        onDrag = { change, dragAmount ->
            // Move all corners by same delta
            moveAllCorners(box, delta)
        },
        onDragEnd = { /* commit change */ }
    )
}
```

**Test:**
- Cycle through all photos using prev/next
- Drag corner - verify smooth, performant movement
- Drag center - verify entire box moves
- Edge case: Photo at boundaries of scrollable area

---

### Phase 4: Screen 3 - Photo Processing

**Goal:** Preview processing effects with configuration options.

#### 4.1 Processing Mode Radio Buttons
```kotlin
@Composable
fun ProcessingModeSelector(
    selected: ProcessingMode,
    onSelect: (ProcessingMode) -> Unit
) {
    Column {
        RadioButton("Crop Only", selected == CROP_ONLY)
        RadioButton("Rotate Only", selected == ROTATE_ONLY)
        RadioButton("Perspective Correction", selected == PERSPECTIVE_CORRECTION)
    }
}
```

#### 4.2 Aspect Ratio Dropdown
```kotlin
@Composable
fun AspectRatioSelector(
    enabled: Boolean,  // disabled when perspective correction is off
    selected: AspectRatio?,
    onSelect: (AspectRatio) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    ExposedDropdownMenuBox(expanded = expanded) {
        OutlinedTextField(
            value = selected?.toDisplayString() ?: "Default",
            enabled = enabled,
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }
        )
        ExposedDropdownMenu(expanded = expanded) {
            AspectRatio.entries.forEach { ratio ->
                DropdownMenuItem(
                    text = { Text(ratio.toDisplayString()) },
                    onClick = { onSelect(ratio); expanded = false }
                )
            }
        }
    }
}
```

#### 4.3 Preview Generation Pipeline

**Processing modes:**

1. **CROP_ONLY:**
   - Calculate axis-aligned bounding box (AABB) from corners
   - Crop image to AABB

2. **ROTATE_ONLY:**
   - Calculate average angle of longest edges
   - Rotate image by that angle
   - Calculate AABB after rotation
   - Crop to AABB

3. **PERSPECTIVE_CORRECTION:**
   - Apply 4-point perspective transform
   - If aspect ratio specified, scale output to match
   - If "default", use average edge ratios

#### 4.4 Preview Cache
```kotlin
class PreviewCache {
    private val cache = mutableStateMapOf<String, ImageBitmap>()
    
    fun get(key: PhotoCacheKey): ImageBitmap? = cache[key]
    
    suspend fun compute(
        key: PhotoCacheKey,
        processor: suspend () -> ImageBitmap
    ): ImageBitmap = cache.getOrPut(key) { processor() }
    
    fun clear() = cache.clear()
}
```

#### 4.5 Loading State During Preview
```kotlin
@Composable
fun ProcessingPreview(
    config: PhotoProcessingConfig,
    onConfigChange: (PhotoProcessingConfig) -> Unit
) {
    var isProcessing by remember { mutableStateOf(false) }
    
    Box {
        if (isProcessing) {
            LoadingOverlay(
                isLoading = true,
                message = "Generating preview..."
            )
        }
        
        Image(/* preview content */)
    }
}
```

**Test:**
- Change processing mode - verify preview updates
- Change aspect ratio - verify preview updates
- Disable perspective - verify aspect ratio dropdown is disabled
- Verify loading spinner appears during processing

---

### Phase 5: Screen 4 - Metadata

**Goal:** Allow metadata entry per photo with EXIF preservation.

#### 5.1 Metadata Card Component
```kotlin
@Composable
fun MetadataCard(
    photo: PhotoAsset,
    thumbnail: ImageBitmap,
    metadata: PhotoMetadata,
    onMetadataChange: (PhotoMetadata) -> Unit,
    onThumbnailClick: () -> Unit  // Show full-screen preview
)
```

#### 5.2 Metadata Fields
```kotlin
@Composable
fun MetadataForm(
    metadata: PhotoMetadata,
    onMetadataChange: (PhotoMetadata) -> Unit
) {
    var notes by remember { mutableStateOf(metadata.notes) }
    var dateTaken by remember { mutableStateOf(metadata.dateTaken) }
    var originalDate by remember { mutableStateOf(metadata.originalDate) }
    var tags by remember { mutableStateOf(metadata.tags.joinToString(", ")) }
    var keywords by remember { mutableStateOf(metadata.keywords.joinToString(", ")) }
    var photographer by remember { mutableStateOf(metadata.photographer) }
    var subjects by remember { mutableStateOf(metadata.subjects.joinToString(", ")) }
    
    // Form fields...
}
```

#### 5.3 Full-Screen Image Preview
```kotlin
@Composable
fun FullScreenImagePreview(
    image: ImageBitmap,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Image(
                image,
                contentDescription = "Full preview",
                modifier = Modifier.fillMaxSize()
            )
            
            IconButton(
                "Close",
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }
    }
}
```

#### 5.4 Original EXIF Preservation
```kotlin
interface ExifService {
    suspend fun readExif(imagePath: Path): ExifData
    suspend fun writeExif(imagePath: Path, exifData: ExifData)
    suspend fun copyExif(source: Path, destination: Path)
}

data class ExifData(
    val dateTimeOriginal: LocalDateTime?,
    val userComment: String?,
    val imageDescription: String?,
    val customTags: Map<String, String>,
    val rawData: Map<String, Any>
)
```

**Test:**
- Enter metadata in fields
- Verify metadata persists when navigating away and back
- Verify thumbnail click opens full-screen preview
- Verify EXIF copy preserves original metadata
- Verify new metadata overlays original

---

### Phase 6: Screen 5 - Review and Save

**Goal:** Show summary of all photos and begin export.

#### 6.1 Review Card Component
```kotlin
@Composable
fun ReviewCard(
    photo: PhotoAsset,
    thumbnail: ImageBitmap,
    exportConfig: ExportConfig,
    processingConfig: PhotoProcessingConfig,
    metadata: PhotoMetadata,
    onEditClick: () -> Unit  // Navigate back to relevant screen
)
```

#### 6.2 Export Button
```kotlin
@Composable
fun ExportButton(
    onClick: () -> Unit,
    isEnabled: Boolean
) {
    Button(
        onClick = onClick,
        enabled = isEnabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF4CAF50)
        )
    ) {
        Icon(Icons.Default.Save, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Export Images")
    }
}
```

**Test:**
- Verify all photos listed with correct thumbnails
- Verify processing mode shown
- Verify aspect ratio shown
- Verify final filename shown
- Verify destination shown

---

### Phase 7: Screen 6 - Exporting

**Goal:** Sequential export with progress indication.

#### 7.1 Export Progress UI
```kotlin
@Composable
fun ExportProgressScreen(
    totalPhotos: Int,
    currentIndex: Int,
    currentPhoto: PhotoAsset,
    currentThumbnail: ImageBitmap,
    destination: Path,
    onCancel: () -> Unit
) {
    Column {
        // Current photo preview
        Image(currentThumbnail)
        
        // Progress text
        Text("${currentIndex + 1} of ${totalPhotos}")
        
        // Destination
        Text("Exporting to: $destination")
        
        // Loading indicator
        CubeGridLoadingIndicator()
        
        // Cancel button
        OutlinedButton("Cancel Export", onClick = onCancel)
    }
}
```

#### 7.2 Sequential Export Loop
```kotlin
suspend fun exportPhotos(
    photos: List<PhotoAsset>,
    configs: Map<PhotoAsset.Id, PhotoProcessingConfig>,
    exportConfig: ExportConfig,
    onProgress: (Int, Int) -> Unit  // current, total
): List<Path> {
    return photos.mapIndexed { index, photo ->
        onProgress(index, photos.size)
        exportSinglePhoto(photo, configs[photo.id]!!, exportConfig)
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

**Test:**
- Verify progress updates correctly
- Verify each photo thumbnail shown during export
- Verify destination shown
- Verify completion message
- Verify buttons navigate correctly

---

## Screen Flow Summary

```
[Import Screen]
        │
        ▼
[Screen 1: Initial Detection]
        │ Next
        ▼
[Screen 2: Boundary Refinement]
        │ Next
        ▼
[Screen 3: Photo Processing]
        │ Next
        ▼
[Screen 4: Metadata]
        │ Next
        ▼
[Screen 5: Review & Save]
        │ Export
        ▼
[Screen 6: Exporting]
        │ Complete
        ▼
[Back to Screen 1 with next image] OR [Done]
```

---

## Iteration & Progress Tracking

### Milestones

| Milestone | Criteria | Test Verification |
|-----------|----------|-------------------|
| M1: Loading Animation | CubeGrid spinner visible during async ops | UI test or manual verification |
| M2: Coordinate System | Coordinates stored as percentages, scale losslessly | Unit tests for conversion |
| M3: Performant Drag | 60fps drag without jank | Manual test + profiling |
| M4: Screen 1 Complete | All detected photos viewable and editable | Integration test |
| M5: Screen 2 Complete | Cycle through photos, refine corners | Integration test |
| M6: Screen 3 Complete | Preview generation for all modes | Visual verification + unit tests |
| M7: Screen 4 Complete | Metadata entry and persistence | Unit tests |
| M8: Screen 5 Complete | Review cards display correctly | Visual verification |
| M9: Screen 6 Complete | Sequential export works | Integration test |
| M10: End-to-End | Full wizard flow functional | Integration test |

### Performance Benchmarks

| Operation | Target | Measurement |
|-----------|--------|-------------|
| Corner drag | <16ms per frame | Frame timing |
| Box move | <16ms per frame | Frame timing |
| Preview generation | <2s for 4K image | Stopwatch |
| Screen navigation | <100ms | Stopwatch |
| Export per photo | <5s for 4K image | Stopwatch |

---

## Naming Pattern Reference

| Token | Description | Example |
|-------|-------------|---------|
| `{original_name}` | Original filename without extension | `IMG_1234` |
| `{date}` | Current date (YYYY-MM-DD) | `2026-04-10` |
| `{date_time}` | Current datetime | `2026-04-10_14-30-00` |
| `{date_time_original}` | EXIF date taken | `2024-03-15_10-30-00` |
| `{number}` | Incrementing number for duplicates | `1`, `2`, `3` |
| `{tags}` | Comma-separated tags | `vacation,beach` |

### Default Pattern
**`{original_name}_{date_time_original}_{number}`**

Example outputs:
- `IMG_1234_2024-03-15_10-30-00.jpg`
- `IMG_1234_2024-03-15_10-30-00_1.jpg` (if duplicate)

---

## EXIF Metadata Fields

| Field | EXIF Tag | Notes |
|-------|----------|-------|
| Date Taken | `DateTimeOriginal` | Standard EXIF |
| Notes | `UserComment` / `ImageDescription` | Standard or custom |
| Original Date | Custom XMP | Can be YEAR, YEAR_MONTH, or YEAR_MONTH_DAY precision |
| Tags | `Keywords` / Custom XMP | Standard or custom |
| Keywords | Custom XMP | Custom namespace |
| Photographer | `Artist` / `Copyright` | Standard EXIF |
| Subjects | Custom XMP | Custom namespace |

---

## File Structure Recommendations

```
src/main/kotlin/org/kryspetrie/fileimport/
├── ui/
│   ├── screens/
│   │   ├── wizard/
│   │   │   ├── WizardContainer.kt          # Main wizard orchestrator
│   │   │   ├── Screen1Detection.kt         # Initial photo detection
│   │   │   ├── Screen2Refinement.kt         # Corner refinement
│   │   │   ├── Screen3Processing.kt         # Photo processing preview
│   │   │   ├── Screen4Metadata.kt            # Metadata entry
│   │   │   ├── Screen5Review.kt              # Review and save
│   │   │   ├── Screen6Exporting.kt           # Export progress
│   │   │   └── components/                   # Shared wizard components
│   │   └── components/
│   │       ├── LoadingOverlay.kt
│   │       ├── DraggableCorner.kt
│   │       ├── MetadataCard.kt
│   │       └── FullScreenPreview.kt
│   └── theme/
├── domain/
│   └── model/
│       ├── PhotoAsset.kt
│       ├── BoundingBox.kt
│       ├── PhotoProcessingConfig.kt
│       ├── PhotoMetadata.kt
│       └── NamingPattern.kt
├── infrastructure/
│   ├── wizard/
│   │   ├── PhotoScanWizardState.kt
│   │   └── WizardNavigation.kt
│   ├── export/
│   │   ├── ExportService.kt
│   │   └── ExifService.kt
│   └── processing/
│       ├── ImageProcessor.kt
│       └── PreviewCache.kt
└── application/
    └── services/
        └── ExportOrchestrator.kt
```

---

## Appendix: Performance Tips

### Drag Optimization Checklist
- [ ] Use `graphicsLayer` for transform operations
- [ ] Separate drag state from compose state
- [ ] Use `derivedStateOf` for expensive calculations
- [ ] Use `pointerInput` with `detectDragGestures`
- [ ] Batch state updates on drag end
- [ ] Avoid recomposition during drag
- [ ] Use corner hit tolerance for easier selection
- [ ] Consider using Canvas directly for complex overlays

### Image Processing Tips
- [ ] Downsample for all previews
- [ ] Cache processed previews in memory
- [ ] Use coroutines for background processing
- [ ] Show loading indicator during async operations
- [ ] Consider using OpenCV native bindings if Java libraries are slow

---

## Open Questions (Pending User Input)

1. Should the 20% padding around refined boundaries be configurable?
2. For "rotate only" mode, should the auto-rotation angle be adjustable by the user?
3. Should metadata fields be shared across all photos in a batch?
4. Should the naming pattern be configurable per-batch or per-photo?
5. What maximum number of photos should the wizard handle before performance degradation?
