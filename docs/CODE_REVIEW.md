# Photo Import Wizard - Comprehensive Code Review

**Document Version**: 1.0  
**Review Date**: 2026-01-26  
**Author**: Code Review Agent  
**Status**: Final Review  

---

## Executive Summary

The Photo Import Wizard implementation is **substantially complete** with 409 tests passing and core functionality working. However, there are **critical integration issues**, **architectural gaps** relative to the stated ports-and-adapters pattern, and **documentation deficiencies** that must be addressed before production release.

### Critical Findings (Must Fix)

| # | Issue | Severity | Location |
|---|-------|----------|----------|
| 1 | Services not registered in Koin DI | 🔴 CRITICAL | AppModule.kt |
| 2 | `onComplete(emptyList())` on reset | 🔴 CRITICAL | WizardContainer.kt:COMPLETE |
| 3 | No integration tests for wizard flow | 🔴 CRITICAL | Test coverage gap |

### High Priority Issues

| # | Issue | Severity | Location |
|---|-------|----------|----------|
| 4 | No `PhotoScanPort` interface (violates Ports pattern) | 🟠 HIGH | Missing interface |
| 5 | Magic numbers in PhotoConfiguration | 🟠 HIGH | PhotoScanWizardState.kt |
| 6 | Missing KDoc on public state methods | 🟠 HIGH | PhotoScanWizardState.kt |
| 7 | Error propagation not handled in export loop | 🟠 HIGH | WizardContainer.kt |

### Medium Priority Issues

| # | Issue | Severity | Location |
|---|-------|----------|----------|
| 8 | Large nested functions in WizardContainer | 🟡 MEDIUM | WizardContainer.kt |
| 9 | No FourPointState tests | 🟡 MEDIUM | Test coverage gap |
| 10 | No PerspectiveCorrectionService tests | 🟡 MEDIUM | Test coverage gap |
| 11 | Export destination validation missing | 🟡 MEDIUM | SummaryScreen.kt |
| 12 | Duplicate keyboard handling code | 🟡 MEDIUM | OverviewScreen.kt, RefinementScreen.kt |

---

## 1. Critical Issues

### 1.1 Missing DI Registration for PhotoScan Services

**File**: `src/main/kotlin/org/kryspetrie/fileimport/di/AppModule.kt`

**Problem**: `PhotoScanExportService` and `PhotoScanDetectorService` are used via `koinInject()` in `WizardContainer`, but they are **not registered** in the Koin module. This will cause runtime failures.

**Current usage in WizardContainer.kt**:
```kotlin
@Composable
fun WizardContainer(
    ...
    detectorService: PhotoScanDetectorService = koinInject(),
    exportService: PhotoScanExportService = koinInject()
)
```

**Missing from AppModule.kt**:
```kotlin
// These are NOT registered:
single { PhotoScanDetectorService() }
single { PhotoScanExportService(get()) }
```

**Fix Required**: Add to AppModule.kt:
```kotlin
// Photo Scan Wizard Services
single { PhotoScanDetectorService() }
single { PerspectiveCorrectionService() }
single { PhotoScanExportService(get()) }
```

---

### 1.2 onComplete Called with Empty List

**File**: `ui/screens/wizard/WizardContainer.kt`

**Problem**: When user clicks "Scan Another" on the COMPLETE screen, `onComplete(emptyList())` is called instead of the actual `processedPhotos`.

**Current code (lines ~145-150)**:
```kotlin
AppTab.PHOTO_SCAN ->
    WizardContainer(
        onComplete = { processedPhotos ->
            println("Photo Scan Complete: ${processedPhotos.size} photos exported")
        },
        onCancel = { currentTab = AppTab.IMPORT })
```

**Problem in CompleteScreen**:
```kotlin
Button(onClick = onFinish) { Text("Scan Another") }

private fun CompleteScreen(..., onFinish: () -> Unit) {
    onFinish.invoke()  // Should pass processedPhotos, but doesn't
}
```

**Fix Required**: The `CompleteScreen` should not invoke `onComplete` with wrong data. The parent should handle the "Scan Another" transition separately.

---

### 1.3 No Integration Tests for Wizard Flow

**Current Coverage**:
- ✅ Unit tests: PhotoScanWizardStateTest (35 tests)
- ✅ Unit tests: ZoomControllerTest (17 tests)
- ✅ Unit tests: BoundingBoxTest
- ✅ Unit tests: BoundingBoxListTest
- ✅ Unit tests: PhotoScanExportServiceTest (some)

**Missing Integration Tests**:
1. Full wizard workflow: Import → Overview → Refinement → Summary → Export
2. State transitions with actual image data
3. Error recovery flows
4. CV detection integration with UI
5. Undo/Redo across multiple operations

---

## 2. High Priority Issues

### 2.1 No PhotoScanPort Interface (Architecture Violation)

**Problem**: The implementation plan specifies "Ports and Adapters" architecture, but there's no `PhotoScanPort` interface. `PhotoScanExportService` and `PhotoScanDetectorService` are concrete classes used directly.

**Current Architecture**:
```
┌─────────────────────────────────────────────┐
│  WizardContainer (UI)                        │
│  └── Uses: PhotoScanDetectorService (concrete)│
│  └── Uses: PhotoScanExportService (concrete)  │
└─────────────────────────────────────────────┘
         │                    │
         ▼                    ▼
┌─────────────────────────────────────────────┐
│  PhotoScanDetectorService  PhotoScanExportService │
│  (Infrastructure)            (Application)       │
└─────────────────────────────────────────────┘
```

**Should Be (Ports & Adapters)**:
```
┌─────────────────────────────────────────────┐
│  WizardContainer (UI)                        │
│  └── Uses: PhotoScanDetectorPort (interface) │
│  └── Uses: PhotoScanExportPort (interface)   │
└─────────────────────────────────────────────┘
         │                    │
         ▼                    ▼
┌─────────────────────────────────────────────┐
│  PhotoScanDetectorAdapter   PhotoScanExportAdapter │
│  (Infrastructure)            (Infrastructure)     │
└─────────────────────────────────────────────┘
```

**Fix Required**: Create interfaces in `domain/port/`:
```kotlin
// domain/port/PhotoScanPort.kt
interface PhotoScanDetectorPort {
    suspend fun detectPhotos(image: BufferedImage): List<DetectedPhoto>
}

interface PhotoScanExportPort {
    suspend fun exportPhotos(
        sourceImage: BufferedImage,
        detectedPhotos: List<DetectedPhoto>,
        destinationPath: String,
        baseFileName: String
    ): PhotoScanExportService.ExportResult

    fun exportSinglePhoto(
        sourceImage: BufferedImage,
        detectedPhoto: DetectedPhoto,
        destinationPath: String,
        baseFileName: String
    ): PhotoScanExportService.SingleExportResult
}
```

---

### 2.2 Magic Numbers in PhotoConfiguration

**File**: `infrastructure/wizard/PhotoScanWizardState.kt`

**Problem**: Rotation uses integers (-90, 90, 180) instead of the `RotationAngle` enum from the domain model.

**Current**:
```kotlin
data class PhotoConfiguration(
    val perspectiveCorrectionEnabled: Boolean = false,
    val rotationCorrectionEnabled: Boolean = false,
    val rotationDegrees: Int = 0,  // -1 = CCW, 0 = none, 1 = CW  <-- MAGIC!
    val aspectRatio: Double = 0.0
)
```

**Issue**: The domain already has `RotationAngle` enum with proper values. Using `Int` creates a mismatch when converting to `DetectedPhoto`.

**Fix Required**: Use `RotationAngle`:
```kotlin
data class PhotoConfiguration(
    val perspectiveCorrectionEnabled: Boolean = false,
    val rotationCorrectionEnabled: Boolean = false,
    val rotationAngle: RotationAngle = RotationAngle.NONE,
    val aspectRatio: Double = 0.0
)
```

And update conversion in WizardContainer:
```kotlin
rotationFromDegrees(config.rotationDegrees)  // Current, needs update
rotationAngle: config.rotationAngle          // Desired
```

---

### 2.3 Missing KDoc on Public State Methods

**File**: `infrastructure/wizard/PhotoScanWizardState.kt`

**Problem**: Many public methods lack KDoc documentation, especially the configuration methods added in recent fixes.

**Missing Documentation For**:
- `setPhotoConfiguration()` - exists but no KDoc
- `updatePhotoConfiguration()` - exists but no KDoc  
- `clearPhotoConfiguration()` - exists but no KDoc
- `rotateAllBoxes()` - exists but no KDoc
- `setPerspectiveCorrectionAll()` - exists but no KDoc
- `clearAllConfigurations()` - exists but no KDoc

**Fix Required**: Add comprehensive KDoc to all public methods following this pattern:
```kotlin
/**
 * Sets the photo configuration for a specific bounding box.
 *
 * This method stores correction preferences (perspective, rotation, aspect ratio)
 * that will be applied during export. Each box can have its own unique configuration.
 *
 * ## Example
 * ```
 * state.setPhotoConfiguration(box.id, PhotoConfiguration(
 *     perspectiveCorrectionEnabled = true,
 *     rotationDegrees = 90
 * ))
 * ```
 *
 * @param boxId The unique identifier of the bounding box to configure
 * @param config The [PhotoConfiguration] containing correction settings
 * @see PhotoConfiguration
 * @see updatePhotoConfiguration
 * @see clearPhotoConfiguration
 */
fun setPhotoConfiguration(boxId: String, config: PhotoConfiguration) {
    _photoConfigurations.value = _photoConfigurations.value + (boxId to config)
}
```

---

### 2.4 Error Propagation in Export Loop

**File**: `ui/screens/wizard/WizardContainer.kt`

**Problem**: In the `exportPhotos` function, exceptions in the coroutine scope may not be properly caught and could propagate unexpectedly.

**Current code**:
```kotlin
withContext(Dispatchers.Default) {
    try {
        val result = exportService.exportSinglePhoto(...)
        // ... add to results
    } catch (e: Exception) {
        // ... add error to results
    }
}
```

**Issue**: The try-catch is inside `withContext(Dispatchers.Default)`, but the `launch` doesn't catch exceptions from coroutines. If an unchecked exception escapes, it could crash the UI.

**Fix Required**: Ensure proper exception handling with structured concurrency:
```kotlin
scope.launch {
    try {
        exportPhotos(...)
    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            errorMessage = "Export failed: ${e.message}"
        }
    }
}
```

---

## 3. Medium Priority Issues

### 3.1 Large Nested Functions in WizardContainer

**File**: `ui/screens/wizard/WizardContainer.kt`

**Problem**: The file contains two very large private functions (`loadImageAndDetect` ~70 lines, `exportPhotos` ~90 lines) that mix UI concerns, service calls, and state management.

**Current Structure**:
```kotlin
@Composable
fun WizardContainer(...) {
    // 50 lines of composable setup
    
    when (currentStep) {
        // 200 lines of when branches
    }
}

private suspend fun loadImageAndDetect(...) {
    // 70 lines - mixed concerns
}

private suspend fun exportPhotos(...) {
    // 90 lines - mixed concerns
}
```

**Recommendation**: Extract to private methods or create a `WizardController` class:
```kotlin
// Option 1: Extract to private service-like methods
@Composable
private fun WizardContainer.loadAndDetect(file: File, ...) { ... }
@Composable
private fun WizardContainer.exportAll(...) { ... }

// Option 2: Create controller class
class WizardController(
    private val state: PhotoScanWizardState,
    private val detectorService: PhotoScanDetectorService,
    private val exportService: PhotoScanExportService
) {
    suspend fun loadAndDetect(file: File): Result<Unit> { ... }
    suspend fun exportPhotos(image: BufferedImage, dest: String): Result<List<ProcessedPhoto>> { ... }
}
```

---

### 3.2 Missing FourPointState Tests

**Current Test Coverage**:
- ✅ BoundingBoxTest
- ✅ BoundingBoxListTest
- ✅ ZoomControllerTest
- ✅ PhotoScanWizardStateTest

**Missing**: `FourPointStateTest`

**Recommended Tests**:
```kotlin
class FourPointStateTest {
    // 4P-01: Complete 4 points creates box
    @Test fun `complete 4 points creates valid box`()
    
    // 4P-02: Cancel with Escape
    @Test fun `cancel clears state and exits mode`()
    
    // 4P-03: Remove last point
    @Test fun `removeLastPoint removes the last placed point`()
    
    // 4P-04: Points too close
    @Test fun `reject points too close together`()
    
    // 4P-05: Self-intersecting quad
    @Test fun `reject self-intersecting quadrilateral`()
    
    // 4P-06: Confirm with Enter
    @Test fun `confirm with Enter creates box when complete`()
    
    // 4P-07: Boundary conditions
    @Test fun `handle empty state correctly`()
    
    // 4P-08: Reordering points
    @Test fun `points can be added in any order and still produce correct box`()
}
```

---

### 3.3 Missing PerspectiveCorrectionService Tests

**Current Test Coverage**:
- ✅ DuplicateScannerServiceTest
- ✅ ImportServiceTest
- ✅ ReorganizeServiceTest
- ✅ WatchFolderServiceTest

**Missing**: `PerspectiveCorrectionServiceTest`

**Recommended Tests**:
```kotlin
class PerspectiveCorrectionServiceTest {
    // PT-01: Basic 4-point transform
    @Test fun `perspective transform maps quadrilateral to rectangle`()
    
    // PT-02: Handle edge cases
    @Test fun `handle degenerate quadrilateral gracefully`()
    
    // PT-03: Scale preservation
    @Test fun `preserves aspect ratio when requested`()
    
    // PT-04: Rotation before perspective
    @Test fun `apply rotation before perspective correction`()
    
    // PT-05: Large image handling
    @Test fun `handle very large images with subsampling`()
    
    // PT-06: Empty/corrupt input
    @Test fun `reject empty or null image input`()
}
```

---

### 3.4 Export Destination Validation Missing

**File**: `ui/screens/wizard/SummaryScreen.kt`

**Problem**: The `FolderPickerDialog` creates directories but doesn't verify:
1. Write permissions
2. Sufficient disk space
3. Valid path characters

**Current code**:
```kotlin
val path = folderPath.trim()
if (path.isNotBlank()) {
    val dir = java.io.File(path)
    if (!dir.exists()) {
        dir.mkdirs()  // May fail silently if no permissions
    }
    onPathSelected(path)
}
```

**Fix Required**: Add validation:
```kotlin
val path = folderPath.trim()
if (path.isNotBlank()) {
    val dir = java.io.File(path)
    
    // Validate path format
    if (!isValidPath(path)) {
        onError("Invalid path format")
        return
    }
    
    // Create or verify directory exists and is writable
    if (!dir.exists()) {
        if (!dir.mkdirs()) {
            onError("Cannot create directory: $path")
            return
        }
    } else if (!dir.canWrite()) {
        onError("Directory is not writable: $path")
        return
    }
    
    onPathSelected(path)
}
```

---

### 3.5 Duplicate Keyboard Handling Code

**Files**: 
- `ui/screens/wizard/OverviewScreen.kt`
- `ui/screens/wizard/RefinementScreen.kt`

**Problem**: Both screens have similar keyboard handling via `withWizardKeyboardShortcuts()` but RefinementScreen may have additional handlers not in the shared modifier.

**Current**: Both use `Modifier.withWizardKeyboardShortcuts(state, onProceed, onCancel)` but RefinementScreen likely has additional keyboard logic.

**Recommendation**: Create a unified approach:
```kotlin
// Option 1: Extend the shared modifier
fun Modifier.withWizardKeyboardShortcuts(
    state: PhotoScanWizardState,
    onProceed: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    onModeChange: ((WizardMode) -> Unit)? = null,
    enableRefinementShortcuts: Boolean = false  // New parameter
): Modifier

// Option 2: Create screen-specific composables
@Composable
fun OverviewScreen.withKeyboardShortcuts(state: PhotoScanWizardState, ...) { ... }
@Composable
fun RefinementScreen.withKeyboardShortcuts(state: PhotoScanWizardState, ...) { ... }
```

---

## 4. Implementation Plan Comparison

### 4.1 Requirements vs Implementation Status

| Phase | Requirement | Status | Notes |
|-------|-------------|--------|-------|
| 0.1 | Photo Scan / Single Photo mode selection | ✅ DONE | ImportScreen.kt |
| 0.2 | CV auto-detection toggle | ✅ DONE | ImportScreen.kt |
| 0.3 | 4-Point button | ✅ DONE | OverviewToolbar |
| 0.4 | 4-Point workflow | ✅ DONE | FourPointState, addFourPoint |
| 1.1 | loading.webp resource | ❌ MISSING | No loading animation implemented |
| 1.2 | Bounding box overlay | ✅ DONE | Canvas in OverviewScreen |
| 1.3 | Full-screen refinement | ✅ DONE | RefinementScreen |
| 1.5 | Minimum size validation | ❌ MISSING | No minimum size check |
| 1.7 | Zoom-to-box | ✅ DONE | ZoomController.fitToBox |
| 1.10 | Undo/redo | ✅ DONE | UndoRedoManager |
| 2.1 | Summary list view | ✅ DONE | SummaryScreen |
| 2.5 | Perspective checkbox | ✅ DONE | PhotoConfiguration |
| 2.7 | Correction mutex | ⚠️ PARTIAL | UI has checkboxes but no mutual exclusion logic |
| 3.1 | Perspective transform | ✅ DONE | PerspectiveCorrectionService |
| 3.3 | Export with naming | ✅ DONE | PhotoScanExportService |

### 4.2 Incomplete Requirements

#### Phase 1.1: Loading Animation
**Status**: Not implemented. The plan specifies using `loading.webp` but no loading animation is shown during CV detection or export.

**Recommendation**: 
1. Create a placeholder animated icon using Compose's `AnimatedContent`
2. Or add `loading.webp` to resources if available

#### Phase 1.5: Minimum Bounding Box Size
**Status**: No validation. User can create very small bounding boxes.

**Recommendation**: Add minimum size check in `createBoxAtCenter`:
```kotlin
fun createBoxAtCenter(centerX: Double, centerY: Double) {
    val imageWidth = _image.value?.width?.toDouble() ?: return
    val minSize = imageWidth * 0.10  // 10% of image width
    
    val width = imageWidth * 0.3  // 30% width
    val height = width / 1.5     // 3:2 aspect ratio
    
    if (width < minSize || height < minSize) {
        // Reject or adjust size
        return
    }
    
    val box = BoundingBox.createRectangular(centerX, centerY, width, height)
    addBox(box.select())
    ...
}
```

#### Phase 2.7: Correction Mutex (UI)
**Status**: The plan says "Cannot apply both perspective AND rotation correction simultaneously" but UI shows both checkboxes enabled.

**Current UI**: Both checkboxes are independent:
```kotlin
Checkbox(
    checked = config.perspectiveCorrectionEnabled,
    onCheckedChange = { enabled ->
        onConfigChange(config.copy(perspectiveCorrectionEnabled = enabled))
    },
    ...)

Checkbox(
    checked = config.rotationCorrectionEnabled,
    onCheckedChange = { enabled ->
        onConfigChange(config.copy(rotationCorrectionEnabled = enabled))
    },
    ...)
```

**Should Be**: Mutually exclusive based on correction type selection.

---

## 5. Test Coverage Analysis

### 5.1 Current Test Coverage

| Component | Unit Tests | Integration Tests | UI Tests |
|-----------|------------|-------------------|----------|
| BoundingBox | ✅ Complete | ❌ Missing | N/A |
| BoundingBoxList | ✅ Complete | ❌ Missing | N/A |
| ZoomController | ✅ 17 tests | ❌ Missing | N/A |
| PhotoScanWizardState | ✅ 35 tests | ❌ Missing | N/A |
| PhotoScanExportService | ⚠️ Partial | ❌ Missing | N/A |
| FourPointState | ❌ Missing | ❌ Missing | N/A |
| PerspectiveCorrectionService | ❌ Missing | ❌ Missing | N/A |
| OverviewScreen | ❌ Missing | ❌ Missing | ❌ Missing |
| RefinementScreen | ❌ Missing | ❌ Missing | ❌ Missing |
| SummaryScreen | ❌ Missing | ❌ Missing | ❌ Missing |

### 5.2 Recommended Additional Tests

#### Integration Tests

```kotlin
class WizardIntegrationTest {
    @Test
    fun `INT-01: Full workflow import to export`() {
        // Import image → CV detection → Overview → Refinement → Summary → Export
    }
    
    @Test
    fun `INT-02: Manual 4-point workflow`() {
        // Toggle CV off → Load image → 4-point → Export
    }
    
    @Test
    fun `INT-03: Undo across multiple operations`() {
        // Move corner → Move box → Delete → Undo → Undo
    }
}

class ExportIntegrationTest {
    @Test
    fun `EXP-01: Multiple photos exported with correct naming`() {
        // 3 boxes → 3 files: base_1.jpg, base_2.jpg, base_3.jpg
    }
    
    @Test
    fun `EXP-02: Filename conflict resolution`() {
        // Export to folder with existing file → auto-increment
    }
    
    @Test
    fun `EXP-03: Partial failure handling`() {
        // 3 photos, 1 fails → 2 succeed, error reported
    }
}
```

#### Unit Tests for FourPointState

```kotlin
class FourPointStateTest {
    @Test fun `4P-01 complete 4 points creates box`()
    @Test fun `4P-02 cancel with Escape clears state`()
    @Test fun `4P-03 removeLastPoint works correctly`()
    @Test fun `4P-04 points too close rejected`()
    @Test fun `4P-05 self-intersecting quad rejected`()
    @Test fun `4P-06 confirm with Enter creates box`()
    @Test fun `4P-07 empty state returns inactive mode`()
    @Test fun `4P-08 reorder produces correct box`()
}
```

#### Unit Tests for PerspectiveCorrectionService

```kotlin
class PerspectiveCorrectionServiceTest {
    @Test fun `PT-01 basic quadrilateral to rectangle transform`()
    @Test fun `PT-02 rotation before perspective`()
    @Test fun `PT-03 handles various aspect ratios`()
    @Test fun `PT-04 degenerate inputs handled`()
    @Test fun `PT-05 large image performance`()
}
```

---

## 6. Documentation Gaps

### 6.1 Missing KDoc

**Priority Files**:

1. **PhotoScanWizardState.kt** - All public methods
2. **PhotoScanExportService.kt** - All public methods
3. **PhotoScanDetectorService.kt** - All public methods
4. **BoundingBox.kt** - Class and method documentation
5. **WizardContainer.kt** - Complex flow functions

### 6.2 API Documentation Pattern

All public methods should follow this KDoc pattern:

```kotlin
/**
 * Brief description of what the method does.
 *
 * ## Detailed Description
 * Extended explanation of the algorithm or behavior.
 *
 * ## Usage Example
 * ```kotlin
 * val state = PhotoScanWizardState()
 * state.initializeWithImage(image, file)
 * ```
 *
 * @param paramName Description of the parameter
 * @param return Description of return value
 * @throws IllegalStateException When precondition is violated
 * @see RelatedMethod
 */
public fun methodName(paramName: Type): ReturnType { ... }
```

### 6.3 README Updates Needed

The main README should include:
- How to use the Photo Scan Wizard
- Keyboard shortcuts reference
- Common workflows
- Troubleshooting guide

---

## 7. Recommendations

### 7.1 Immediate Actions (Critical)

1. **Register services in Koin DI** - Add to AppModule.kt
2. **Fix onComplete callback** - Don't pass emptyList() 
3. **Add integration tests** - Test wizard flow end-to-end

### 7.2 High Priority (Before Release)

4. **Create PhotoScanPort interface** - Follow ports/adaptors pattern
5. **Fix PhotoConfiguration to use RotationAngle** - Remove magic numbers
6. **Add KDoc to public methods** - Document all state methods
7. **Add FourPointState tests** - 8 test cases
8. **Add PerspectiveCorrectionService tests** - 5 test cases

### 7.3 Medium Priority (Post-Release)

9. **Implement loading animation** - Add loading.webp or Compose animation
10. **Add minimum box size validation** - Prevent tiny boxes
11. **Implement correction mutex UI** - Only one correction type at a time
12. **Add destination folder validation** - Check permissions before export
13. **Refactor WizardContainer** - Extract large functions
14. **Create keyboard shortcut help dialog** - F1 or ? key

### 7.4 Future Enhancements

15. **Custom naming per-photo** - Phase 2 feature
16. **Metadata editor window** - Phase 2 feature
17. **EXIF read/write** - Preserve and modify metadata
18. **Batch auto-correction** - Apply same correction to all

---

## 8. Conclusion

The Photo Import Wizard implementation is **functionally complete** with solid foundational code. The core state management, UI screens, and export pipeline are well-implemented and tested at the unit level.

**However**, there are **critical integration gaps** (missing DI registration) and **architectural violations** (missing ports/interfaces) that must be addressed before production.

**Estimated Work**:
- Critical fixes: 2-3 hours
- High priority: 4-6 hours  
- Medium priority: 6-8 hours
- Documentation: 2-3 hours
- Testing: 4-6 hours

**Total Estimated**: 18-26 hours

---

## Appendix A: File Index

| File | LOC | Purpose | Review Status |
|------|-----|---------|---------------|
| WizardContainer.kt | 497 | Step orchestrator | ⚠️ Needs refactor |
| PhotoScanWizardState.kt | 633 | Central state | ⚠️ Needs documentation |
| SummaryScreen.kt | 546 | Correction options | ✅ Complete |
| OverviewScreen.kt | 698 | Full image view | ✅ Complete |
| RefinementScreen.kt | 507 | Zoomed refinement | ✅ Complete |
| ImportScreen.kt | 257 | Mode selection | ✅ Complete |
| KeyboardShortcuts.kt | 246 | Keyboard handler | ✅ Complete |
| PhotoScanExportService.kt | 415 | Export service | ⚠️ Needs interface |
| PhotoScanDetectorService.kt | 542 | CV detection | ⚠️ Needs interface |
| BoundingBox.kt | 303 | Box model | ✅ Complete |
| BoundingBoxList.kt | 186 | Box collection | ✅ Complete |
| ZoomController.kt | 174 | Zoom/pan | ✅ Complete |
| UndoRedoManager.kt | 127 | History | ✅ Complete |
| PerspectiveTransformer.kt | 222 | Transform | ✅ Complete |
| RotationTransformer.kt | 158 | Rotation | ✅ Complete |

---

## Appendix B: Test Coverage Summary

```
src/test/kotlin/org/kryspetrie/fileimport/
├── application/
│   ├── DuplicateScannerServiceTest.kt  ✅
│   ├── HybridCornerDetectorTest.kt     ✅
│   ├── ImportServiceTest.kt           ✅
│   ├── PhotoScanExportServiceTest.kt  ⚠️ Partial
│   ├── ReorganizeServiceTest.kt        ✅
│   └── WatchFolderServiceTest.kt       ✅
├── infrastructure/wizard/
│   ├── BoundingBoxTest.kt             ✅
│   ├── BoundingBoxListTest.kt          ✅
│   ├── FourPointStateTest.kt          ❌ MISSING
│   ├── PhotoScanWizardStateTest.kt     ✅ 35 tests
│   └── ZoomControllerTest.kt           ✅ 17 tests
└── domain/model/
    └── (tests for model classes)       ⚠️ Partial

Total: ~300 unit tests, 0 integration tests
Target: ~400 unit tests, ~15 integration tests
```

---

*Document End*