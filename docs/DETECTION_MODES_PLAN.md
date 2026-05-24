# Implementation Plan: Unified Corner Order & Multi-Mode Detection

> ⚠️ **SUPERSEDED** — This plan has been absorbed and extended by [ML_DETECTION_INTEGRATION_PLAN.md](./ML_DETECTION_INTEGRATION_PLAN.md) (v2.0, 2026-05-10). That document includes all features described here plus ONNX YOLO integration, CV refinement, fiducial models, crop modes, visibility tracking, tiered sweep, training data export, and full settings propagation. Refer to the new plan for the current architecture and implementation roadmap.
>
> This document is retained for historical reference only.

> **Status:** Superseded by [ML_DETECTION_INTEGRATION_PLAN.md](./ML_DETECTION_INTEGRATION_PLAN.md)
> **Created:** 2026-04-29
> **Superseded:** 2026-05-10
> **Scope:** petrie-file-importer (corner order unification + 3 new detection modes + perspective threshold)

## Overview

Three changes to petrie-file-importer:

1. **Unify corner order** to match YOLO convention (LL/UL/UR/LR), preparing for ONNX integration
2. **Add 3 new detection modes**: Computer Vision (current), Bounding Box (YOLO detection), Perspective Correction (YOLO pose), Hybrid (detection→dilated crop→pose)
3. **Add perspective/rotation threshold**: when a detected quadrilateral is nearly rectangular, skip full perspective correction and do a simple crop+rotate instead

---

## Phase 1: Unify Corner Order

### Problem

petrie uses `topLeft/topRight/bottomLeft/bottomRight` everywhere. YOLO uses `LL/UL/UR/LR` (lower-left/upper-left/upper-right/lower-right). In screen coordinates (origin top-left), these are:

| YOLO keypoint | YOLO name | Screen position | Current petrie name |
|---------------|-----------|-----------------|---------------------|
| kp0 | LL (lower-left) | bottom-left of photo | `bottomLeft` |
| kp1 | UL (upper-left) | top-left of photo | `topLeft` |
| kp2 | UR (upper-right) | top-right of photo | `topRight` |
| kp3 | LR (lower-right) | bottom-right of photo | `bottomRight` |

### Decision: Keep TL/TR/BL/BR naming, add explicit YOLO mapping

The TL/TR/BL/BR names are intuitive in screen coordinates and used across 30+ files. Rather than rename everything to LL/UL/UR/LR (which would be confusing in screen coordinates where LL = bottom-left), we:

1. **Keep the current field names** (`topLeft`, `topRight`, `bottomLeft`, `bottomRight`)
2. **Add a `fromYoloKeypoints()` factory** that maps YOLO output to the correct fields
3. **Add a `toYoloKeypoints()` method** for the reverse mapping
4. **Document the mapping clearly** in code comments

This avoids a mass rename while making YOLO integration trivial.

### Files to Change

#### 1. `domain/model/PhotoScanModels.kt` — Add YOLO mapping to `DetectedPhoto`

```kotlin
// Add companion object method:
companion object {
    /**
     * Create a DetectedPhoto from YOLO pose model keypoints.
     *
     * YOLO keypoint order: kp0=LL, kp1=UL, kp2=UR, kp3=LR
     * Screen coordinate mapping: LL→bottomLeft, UL→topLeft, UR→topRight, LR→bottomRight
     */
    fun fromYoloKeypoints(
        kp0: PhotoCorner,  // LL → bottomLeft
        kp1: PhotoCorner,  // UL → topLeft
        kp2: PhotoCorner,  // UR → topRight
        kp3: PhotoCorner,  // LR → bottomRight
        confidence: Float = 1.0f,
        id: String = java.util.UUID.randomUUID().toString()
    ): DetectedPhoto = DetectedPhoto(
        id = id,
        topLeft = kp1,
        topRight = kp2,
        bottomLeft = kp0,
        bottomRight = kp3
    )
}

// Add method:
fun toYoloKeypoints(): List<PhotoCorner> = listOf(
    bottomLeft,  // kp0: LL
    topLeft,     // kp1: UL
    topRight,    // kp2: UR
    bottomRight  // kp3: LR
)
```

#### 2. `infrastructure/photoscan/HybridCornerDetector.kt` — Add comment clarifying TL/TR/BR/BL ordering

The existing `buildDetectedPhoto()` already produces TL→TR→BR→BL from `RectangleDetector.sortCorners()`. Add a KDoc comment making the ordering explicit:

```kotlin
/**
 * Builds a DetectedPhoto from a quadrilateral.
 *
 * Corner order from RectangleDetector: [topLeft, topRight, bottomRight, bottomLeft]
 * Maps directly to DetectedPhoto fields.
 *
 * YOLO keypoint mapping (for future ONNX integration):
 *   kp0 LL → bottomLeft, kp1 UL → topLeft, kp2 UR → topRight, kp3 LR → bottomRight
 */
private fun buildDetectedPhoto(...): DetectedPhoto { ... }
```

#### 3. `domain/model/PhotoScanModels.kt` — Add `confidence` field to `DetectedPhoto`

Currently `DetectedPhoto` has no confidence field. YOLO outputs confidence scores. Add:

```kotlin
data class DetectedPhoto(
    val id: String = java.util.UUID.randomUUID().toString(),
    // ... existing fields ...
    val confidence: Float = 1.0f,  // Detection confidence (1.0 for classical CV, YOLO provides actual scores)
    val detectionMode: DetectionMode = DetectionMode.COMPUTER_VISION,  // NEW: how this was detected
)
```

#### 4. Tests — Add `DetectedPhotoTest.kt`

Test the YOLO mapping:
- `fromYoloKeypoints()` maps kp0→bottomLeft, kp1→topLeft, kp2→topRight, kp3→bottomRight
- `toYoloKeypoints()` reverses the mapping
- Round-trip: `DetectedPhoto.fromYoloKeypoints(...).toYoloKeypoints()` returns original corners

---

## Phase 2: Detection Mode Enum & Detection Port Refactoring

### New Enum: `DetectionMode`

```kotlin
// domain/model/DetectionMode.kt
enum class DetectionMode(
    val displayName: String,
    val description: String,
    val usesYolo: Boolean,
    val providesCorners: Boolean  // false = axis-aligned bbox only
) {
    COMPUTER_VISION(
        displayName = "Computer Vision",
        description = "Edge detection + contour tracing (current method)",
        usesYolo = false,
        providesCorners = true
    ),
    BOUNDING_BOX(
        displayName = "Bounding Box",
        description = "YOLO detection model finds rectangular regions",
        usesYolo = true,
        providesCorners = false  // Axis-aligned bbox only, no corners
    ),
    PERSPECTIVE_CORRECTION(
        displayName = "Perspective Correction",
        description = "YOLO pose model finds exact 4 corners for perspective warp",
        usesYolo = true,
        providesCorners = true
    ),
    HYBRID(
        displayName = "Hybrid",
        description = "YOLO detection finds regions, then pose model refines corners",
        usesYolo = true,
        providesCorners = true
    )
}
```

### Refactor: `PhotoScanDetectorPort` → Strategy Pattern

Current `PhotoScanDetectorService` is a single concrete class. Refactor to use the port/adapter pattern that already exists:

```kotlin
// domain/port/PhotoScanPort.kt — Extend existing interface
interface PhotoScanDetectorPort {
    fun detectPhotos(image: BufferedImage, mode: DetectionMode = DetectionMode.COMPUTER_VISION): List<DetectedPhoto>
}
```

### New Infrastructure: YOLO Detection

#### `infrastructure/photoscan/YoloDetectionService.kt` — New file

```kotlin
class YoloDetectionService(
    private val detectionModelPath: String,  // Path to detection_model.onnx
    private val poseModelPath: String,        // Path to pose_model.onnx
) : PhotoScanDetectorPort {

    override fun detectPhotos(image: BufferedImage, mode: DetectionMode): List<DetectedPhoto> {
        return when (mode) {
            DetectionMode.BOUNDING_BOX -> detectBoundingBoxes(image)
            DetectionMode.PERSPECTIVE_CORRECTION -> detectWithCorners(image)
            DetectionMode.HYBRID -> detectHybrid(image)
            DetectionMode.COMPUTER_VISION -> throw IllegalArgumentException("Use HybridCornerDetector for CV mode")
        }
    }

    private fun detectBoundingBoxes(image: BufferedImage): List<DetectedPhoto> {
        // 1. Preprocess: resize to 640x640, normalize, NCHW
        // 2. Run detection model
        // 3. Post-process: NMS, extract bounding boxes
        // 4. Create DetectedPhoto with axis-aligned corners (no perspective)
        // 5. Set confidence, detectionMode = BOUNDING_BOX
    }

    private fun detectWithCorners(image: BufferedImage): List<DetectedPhoto> {
        // 1. Preprocess: resize to 640x640, normalize, NCHW
        // 2. Run pose model on full image
        // 3. Post-process: NMS, extract keypoints
        // 4. Map YOLO keypoints to DetectedPhoto via fromYoloKeypoints()
        // 5. Set confidence, detectionMode = PERSPECTIVE_CORRECTION
    }

    private fun detectHybrid(image: BufferedImage): List<DetectedPhoto> {
        // 1. Run detection model → get bounding boxes
        // 2. For each bbox, dilate by 20%, crop sub-image
        // 3. Run pose model on each crop
        // 4. Map keypoints back to full image coordinates
        // 5. Create DetectedPhoto via fromYoloKeypoints()
        // 6. Set confidence, detectionMode = HYBRID
    }
}
```

#### `infrastructure/photoscan/PhotoScanDetectorService.kt` — Modify

Add `mode` parameter to delegate to the correct detector:

```kotlin
class PhotoScanDetectorService(
    private val rectangleDetector: RectangleDetector = RectangleDetector(),
    private val maxPhotos: Int = 4,
    private val yoloDetector: YoloDetectionService? = null  // Optional, injected if ONNX models available
) : PhotoScanDetectorPort {

    private val cvDetector = HybridCornerDetector(rectangleDetector)

    override fun detectPhotos(image: BufferedImage, mode: DetectionMode): List<DetectedPhoto> {
        return when (mode) {
            DetectionMode.COMPUTER_VISION -> {
                cvDetector.targetPhotoCount = maxPhotos
                cvDetector.detectPhotos(image).map { it.copy(detectionMode = DetectionMode.COMPUTER_VISION) }
            }
            DetectionMode.BOUNDING_BOX,
            DetectionMode.PERSPECTIVE_CORRECTION,
            DetectionMode.HYBRID -> {
                yoloDetector?.detectPhotos(image, mode)
                    ?: throw IllegalStateException("YOLO models not available. Set model paths in settings.")
            }
        }
    }
}
```

### DI Registration (`AppModule.kt`)

```kotlin
// Conditionally create YOLO service if models exist
single { PhotoScanDetectorService(rectangleDetector = get(), yoloDetector = null) }
// Future: inject YoloDetectionService when models are configured
```

---

## Phase 3: Perspective / Rotation Threshold

### Problem

When detected corners form a nearly-rectangular quadrilateral, applying a full perspective transform adds complexity (and introduces artifacts at edges) for minimal benefit. If a photo is only slightly rotated (e.g., <2°), a simple crop+rotate is better.

### New: `CorrectionStrategy` Enum & Threshold Logic

```kotlin
// domain/model/PhotoScanModels.kt (or a new file)
enum class CorrectionStrategy {
    /** Simple axis-aligned crop — for nearly-rectangular, unrotated photos */
    CROP,
    /** Crop + rotation correction — for rectangular but slightly rotated photos */
    CROP_AND_ROTATE,
    /** Full 4-point perspective transform — for skewed/trapezoidal photos */
    PERSPECTIVE
}

/**
 * Determines the best correction strategy based on corner geometry.
 *
 * @param corners The four corners of the detected photo
 * @param rotationThresholdDegrees Maximum rotation angle before using rotation (default 1.5°)
 * @param skewThresholdDegrees Maximum corner angle deviation from 90° before using perspective (default 3°)
 */
fun determineCorrectionStrategy(
    corners: BoundingBoxCorners,
    rotationThresholdDegrees: Double = 1.5,
    skewThresholdDegrees: Double = 3.0
): CorrectionStrategy {
    val angles = computeCornerAngles(corners)
    val maxAngleDeviation = angles.map { kotlin.math.abs(it - 90.0) }.maxOrNull() ?: 0.0
    val rotation = computeAverageRotation(corners)

    return when {
        maxAngleDeviation > skewThresholdDegrees -> CorrectionStrategy.PERSPECTIVE
        rotation > rotationThresholdDegrees -> CorrectionStrategy.CROP_AND_ROTATE
        else -> CorrectionStrategy.CROP
    }
}

private fun computeCornerAngles(corners: BoundingBoxCorners): List<Double> {
    // Compute angle at each corner using dot product
    val pts = corners.toList()
    return (0..3).map { i ->
        val prev = pts[(i - 1 + 4) % 4]
        val curr = pts[i]
        val next = pts[(i + 1) % 4]
        val v1 = prev - curr
        val v2 = next - curr
        val dot = v1.x * v2.x + v1.y * v2.y
        val cross = v1.x * v2.y - v1.y * v2.x
        kotlin.math.atan2(cross, dot) * 180.0 / kotlin.math.PI
    }
}

private fun computeAverageRotation(corners: BoundingBoxCorners): Double {
    // Average angle of top and bottom edges from horizontal
    val topEdgeAngle = kotlin.math.atan2(
        corners.topRight.y - corners.topLeft.y,
        corners.topRight.x - corners.topLeft.x
    ) * 180.0 / kotlin.math.PI
    val bottomEdgeAngle = kotlin.math.atan2(
        corners.bottomRight.y - corners.bottomLeft.y,
        corners.bottomRight.x - corners.bottomLeft.x
    ) * 180.0 / kotlin.math.PI
    return kotlin.math.abs((topEdgeAngle + bottomEdgeAngle) / 2.0)
}
```

### Modify: `PhotoScanExportService` to use `CorrectionStrategy`

Currently `DetectedPhoto.applyPerspectiveCorrection` is a boolean. Replace the boolean logic with the strategy:

```kotlin
// In export pipeline:
val strategy = if (photo.applyPerspectiveCorrection) {
    determineCorrectionStrategy(photo.toBoundingBoxCorners(), rotationThreshold, skewThreshold)
} else {
    CorrectionStrategy.CROP  // User explicitly disabled perspective
}

val correctedImage = when (strategy) {
    CorrectionStrategy.CROP -> cropAxisAligned(sourceImage, photo)
    CorrectionStrategy.CROP_AND_ROTATE -> {
        val cropped = cropAxisAligned(sourceImage, photo)
        rotateImage(cropped, photo.rotation)
    }
    CorrectionStrategy.PERSPECTIVE -> perspectiveService.correctPerspective(sourceImage, photo)
}
```

### Configurable Thresholds

Add to `PhotoScanConfiguration`:

```kotlin
data class PhotoScanConfiguration(
    // ... existing fields ...
    val perspectiveThreshold: Double = 3.0,   // Degrees of skew before full perspective
    val rotationThreshold: Double = 1.5,       // Degrees of rotation before crop+rotate
    val autoStrategy: Boolean = true,           // Automatically choose CROP/ROTATE/PERSPECTIVE
)
```

User can override auto-detection by explicitly choosing in the UI:
- "Auto" (default) — uses threshold logic
- "Always crop" — `CorrectionStrategy.CROP`
- "Always perspective" — `CorrectionStrategy.PERSPECTIVE`

---

## Phase 4: UI Changes

### Detection Mode Selector

Add to the photo scan wizard (import screen or overview screen):

```kotlin
@Composable
fun DetectionModeSelector(
    currentMode: DetectionMode,
    onModeChange: (DetectionMode) -> Unit,
    yoloAvailable: Boolean  // false if ONNX models not configured
) {
    // Dropdown or segmented button:
    // [Computer Vision] [Bounding Box] [Perspective] [Hybrid]
    // Bounding Box/Perspective/Hybrid disabled if !yoloAvailable
}
```

### Correction Strategy Selector

Add to the refinement/summary screens:

```kotlin
@Composable
fun CorrectionStrategySelector(
    currentStrategy: CorrectionStrategy,
    onStrategyChange: (CorrectionStrategy) -> Unit,
    autoEnabled: Boolean
) {
    // Options: [Auto] [Crop Only] [Always Perspective]
    // "Auto" uses thresholds, others force the strategy
}
```

### Visual Indicator for Detection Mode

Show which mode detected each photo:

```kotlin
// In overview/refinement screen, small badge on each photo:
Text(
    when (photo.detectionMode) {
        DetectionMode.COMPUTER_VISION -> "CV"
        DetectionMode.BOUNDING_BOX -> "BB"
        DetectionMode.PERSPECTIVE_CORRECTION -> "YOLO"
        DetectionMode.HYBRID -> "HYB"
    },
    color = when (photo.detectionMode) {
        DetectionMode.COMPUTER_VISION -> Color.Gray
        DetectionMode.BOUNDING_BOX -> Color(0xFF2196F3)   // Blue
        DetectionMode.PERSPECTIVE_CORRECTION -> Color(0xFF4CAF50) // Green
        DetectionMode.HYBRID -> Color(0xFFFF9800)        // Orange
    }
)
```

For `BOUNDING_BOX` mode: show axis-aligned rectangle (no corner handles, since there are no precise corners).
For `PERSPECTIVE_CORRECTION` and `HYBRID`: show 4 corner handles as currently.
For `COMPUTER_VISION`: show 4 corner handles as currently.

---

## Phase 5: ONNX Integration Plumbing

### Dependencies (`build.gradle.kts`)

```kotlin
// Add ONNX Runtime
implementation("org.onnxruntime:onnxruntime:1.17.0")

// Optional: Platform-specific acceleration
// implementation("org.onnxruntime:onnxruntime-android:1.17.0")  // For Android
```

### Configuration

Store ONNX model paths in app settings:

```kotlin
// In AppSettings / PhotoScanSettings
data class YoloConfig(
    val detectionModelPath: String = "",  // Empty = not configured, fall back to CV
    val poseModelPath: String = "",
    val confidenceThreshold: Float = 0.5f,
    val nmsThreshold: Float = 0.45f,
)
```

Users configure model paths in Settings dialog. If empty, only `COMPUTER_VISION` mode is available.

### `YoloDetectionService` — ONNX Preprocessing

```kotlin
private fun preprocess(image: BufferedImage, targetSize: Int = 640): Pair<FloatBuffer, Pair<Int, Int>> {
    // 1. Resize to 640x640 (letterboxed or stretched)
    // 2. Convert to RGB float array [1, 3, 640, 640] normalized to [0, 1]
    // 3. Return preprocessed buffer + original dimensions for coordinate mapping
}
```

### `YoloDetectionService` — Detection Post-processing

```kotlin
private fun postprocessDetection(output: FloatArray, originalWidth: Int, originalHeight: Int): List<DetectedPhoto> {
    // Output shape: (1, 5, 8400) — cx, cy, w, h, confidence
    // 1. Filter by confidence threshold
    // 2. NMS (IoU > 0.45)
    // 3. Scale boxes back to original image coordinates
    // 4. Create DetectedPhoto with axis-aligned corners, mode=BOUNDING_BOX
}
```

### `YoloDetectionService` — Pose Post-processing

```kotlin
private fun postprocessPose(output: FloatArray, originalWidth: Int, originalHeight: Int): List<DetectedPhoto> {
    // Output shape: (1, 300, 18) — cx, cy, w, h, confidence, kp0x, kp0y, kp0v, ...
    // 1. Filter by confidence threshold
    // 2. NMS (IoU > 0.45)
    // 3. Scale keypoints back to original image coordinates
    // 4. Map via DetectedPhoto.fromYoloKeypoints()
    // 5. Set mode=PERSPECTIVE_CORRECTION
}
```

### `YoloDetectionService` — Hybrid (Detect → Dilate → Pose)

```kotlin
private fun detectHybrid(image: BufferedImage): List<DetectedPhoto> {
    // 1. Run detection model on full image → bounding boxes
    val boxes = detectBoundingBoxes(image)

    // 2. For each box, dilate by 20% and crop sub-image
    val results = mutableListOf<DetectedPhoto>()
    for (box in boxes) {
        val dilatedBox = dilateBoundingBox(box, 0.20f)  // 20% padding
        val crop = subImage(image, dilatedBox)

        // 3. Run pose model on crop
        val poseResults = detectWithCorners(crop)

        // 4. Map keypoints back to full image coordinates
        for (photo in poseResults) {
            val mapped = mapCropToFullImage(photo, dilatedBox)
            results.add(mapped.copy(detectionMode = DetectionMode.HYBRID))
        }
    }
    return results
}

private fun dilateBoundingBox(box: DetectedPhoto, factor: Float): DetectedPhoto {
    // Expand each corner away from center by factor%
    val cx = (box.topLeft.x + box.topRight.x + box.bottomLeft.x + box.bottomRight.x) / 4
    val cy = (box.topLeft.y + box.topRight.y + box.bottomLeft.y + box.bottomRight.y) / 4
    // ...
}
```

---

## Implementation Order

| Step | What | Files | Dependencies | Estimated Size |
|------|------|-------|-------------|----------------|
| **1** | Add `DetectionMode` enum + `fromYoloKeypoints()` + `toYoloKeypoints()` + `confidence` field | `PhotoScanModels.kt` (modify), `DetectionMode.kt` (new) | None | Small |
| **2** | Add `CorrectionStrategy` enum + `determineCorrectionStrategy()` | `PhotoScanModels.kt` (modify) or new file | Step 1 | Small |
| **3** | Add `CorrectionStrategy` to export pipeline | `PhotoScanExportService.kt` (modify), `PerspectiveCorrectionService.kt` (modify) | Step 2 | Medium |
| **4** | Tests for YOLO mapping round-trip + CorrectionStrategy logic | New test files | Step 1, 2 | Medium |
| **5** | Refactor `PhotoScanDetectorPort` to accept `DetectionMode` | `PhotoScanPort.kt` (modify), `PhotoScanDetectorService.kt` (modify) | Step 1 | Small |
| **6** | Create `YoloDetectionService` skeleton (preprocess + placeholder) | New file | Step 1 | Medium |
| **7** | Implement ONNX preprocessing/postprocessing | `YoloDetectionService.kt` | Step 6, ONNX Runtime dep | Medium |
| **8** | Implement Hybrid mode (detect → dilate → pose) | `YoloDetectionService.kt` | Step 7 | Medium |
| **9** | DI registration + settings for model paths | `AppModule.kt`, `PhotoScanSettings.kt` | Step 5, 6 | Small |
| **10** | UI: Detection mode selector | New composable, modify wizard screens | Step 5 | Medium |
| **11** | UI: Correction strategy selector | New composable, modify refinement/summary screens | Step 3 | Medium |
| **12** | UI: Detection mode badge on photos | Modify overview/refinement screen | Step 5 | Small |
| **13** | Integration tests (CV mode still works, mode switching) | New test files | Steps 5-8 | Medium |

---

## Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| YOLO model paths not configured → crash | `YoloDetectionService` is optional in DI; UI disables YOLO modes when not configured |
| ONNX Runtime native library conflicts | Use `onnxruntime` (CPU-only) by default; offer GPU builds as separate config |
| Performance: pose model on full image is slow | Hybrid mode runs pose only on cropped regions; Bounding Box mode is fast |
| Corner order confusion during integration | `fromYoloKeypoints()` is the single point of mapping; all YOLO code goes through it |
| Existing CV detection breaks | `Computer Vision` mode remains unchanged; no code paths modified, only new parameter added |
| Tests break on `DetectedPhoto` changes | Add `confidence` and `detectionMode` with defaults so existing constructors still compile |

---

## What Does NOT Change

- `RectangleDetector` — untouched, still the backbone of CV mode
- `HybridCornerDetector` — untouched, still handles CV mode
- `PerspectiveCorrectionService` — core logic unchanged, only call sites add strategy selection
- `BoundingBox` / `BoundingBoxCorners` (wizard) — unchanged, these are UI-only types
- `Corner` enum (wizard) — unchanged, maps to `CornerType` for UI
- All existing tests — continue to pass with default `detectionMode = COMPUTER_VISION`