# Petrie File Importer — Architecture Refactoring Plan

> Generated: 2026-06-06  
> Status: Proposed  
> Based on: Full codebase architecture audit (see notes at bottom)

---

## Guiding Principles

1. **Every phase must leave the build green** — no half-finished migrations.
2. **Bottom-up order** — fix domain models before the services that depend on them, fix services before the UI that depends on them.
3. **One concept per phase** — each phase targets a single architectural concern to minimize merge-conflict surface.
4. **Preserve existing test coverage** — every phase updates tests alongside production code. Tests that reference renamed/moved types get updated in the same commit.
5. **No big-bang rewrites** — each phase is independently deployable. If we stop after phase 2, the codebase is still coherent.

---

## Phase 1: Consolidate Domain Duplicates

**Goal:** Eliminate the three duplicate model triples that force field-by-field bridge code.

**Estimated effort:** Small (2-3 hours)  
**Risk:** Low — mostly search-replace + type alias bridge

### 1a. Unify `FaceRegion` / `FaceRegionConfig` / `SourceFaceRegion` → `FaceRegion`

All three classes have identical fields: `name, type, x, y, w, h`.

| Current location | Action |
|---|---|
| `domain.model.FaceRegionConfig` | **Keep** — rename to `FaceRegion` |
| `infrastructure.wizard.FaceRegion` (in WizardState) | **Delete** — use domain type |
| `application.SourceFaceRegion` (in FaceRegionTransformer) | **Delete** — use domain type |

**Migration path:**
1. Rename `domain.model.FaceRegionConfig` → `domain.model.FaceRegion`
2. Add `@Deprecated` typealias `FaceRegionConfig = FaceRegion` in domain for temporary compat
3. Replace all `infrastructure.wizard.FaceRegion` imports with `domain.model.FaceRegion`
4. Replace `SourceFaceRegion` in `FaceRegionTransformer.kt` with `FaceRegion` (the fields are identical)
5. Remove the `FaceRegion(name=,type=,x=,y=,w=,h=) → FaceRegionConfig(name=,...)` mapping in `WizardContainer.kt` lines 788-796
6. Update test files: `PhotoConfigurationFaceRegionTest.kt`, `FaceSelectionTest.kt`, `FaceRegionTransformerTest.kt`, `XmpFaceRegionExportTest.kt`
7. Remove typealias

**Files modified:** ~12 production, ~4 test

### 1b. Unify `OverrideState` enums, remove dead `FieldOverride`

Two `OverrideState` enums with identical values (`KEEP_SOURCE`, `OVERRIDE`, `NULL_OUT`).

| Current location | Action |
|---|---|
| `domain.model.OverrideState` (in PhotoScanConfiguration.kt) | **Keep** — canonical |
| `infrastructure.wizard.OverrideState` (in WizardState) | **Delete** — use domain type |
| `infrastructure.wizard.FieldOverride` | **Delete** — `value` field is never used by export pipeline |

`FieldOverride.value` is dead code: `WizardContainer.kt` maps only `FieldOverride?.state → OverrideState?`, and `PhotoConfiguration` stores the actual override value as the raw string field (e.g., `description = "something"`). The `FieldOverride.value` has no downstream consumer.

**Migration path:**
1. Change `PhotoConfiguration` fields from `FieldOverride?` → `OverrideState?` (directly)
2. Replace `infrastructure.wizard.OverrideState` imports with `domain.model.OverrideState` in:
   - `MetadataScreen.kt` (line 119)
   - `QuickEditScreen.kt` (line 120)
   - `OverrideIndicator.kt` (line 22)
   - `WizardContainer.kt` (line 1094)
3. Delete the `toDomain()` extension function in `WizardContainer.kt` (no longer needed)
4. Simplify `WizardContainer.kt` override mapping: `config.overrideDescription?.state?.toDomain()` → `config.overrideDescription`
5. Remove `FieldOverride` and `infrastructure.wizard.OverrideState` from WizardState
6. Update `OverrideIndicator.kt` to work with `domain.model.OverrideState` directly (already simplified to two-state toggle, so this is trivial)
7. Update tests

**Files modified:** ~8 production, ~3 test

### 1c. Unify `AspectRatio` triple → one enum

Three `AspectRatio` enums exist:

| Location | Values |
|---|---|
| `domain.model.PhotoModels.kt` | `RATIO_2_3, RATIO_4_3, ...` (6 values) |
| `infrastructure.wizard.AspectRatioHandler.kt` | `CURRENT, SQUARE, RATIO_4_3, ...` (8 values) |
| `ui.screens.wizard.AspectRatio.kt` | `CURRENT, SQUARE, PORTRAIT_2_3, ...` (10 values) |

**Keep: `ui.screens.wizard.AspectRatio`** — it's the most complete, orientation-aware, and the one actually used by the UI dropdown.

**Migration path:**
1. Move `AspectRatio` from `ui.screens.wizard` → `domain.model` (it's a pure enum with no UI dependency)
2. Add orientation awareness from `AspectRatioHandler` (the `isPortrait()` logic)
3. Delete `AspectRatioHandler.AspectRatio` inner enum, make `AspectRatioHandler` use domain type
4. Delete `PhotoModels.kt.AspectRatio` enum — it has zero external consumers
5. Update `PhotoProcessingConfig` to use the canonical `AspectRatio`
6. Update `AspectRatioDropdown.kt` imports

**Files modified:** ~6 production, ~1 test

### 1d. Unify `ProcessingMode` / `PerspectiveMode`

`ProcessingMode` (`CROP_ONLY, ROTATE_ONLY, PERSPECTIVE_CORRECTION`) and `PerspectiveMode` (`AUTO, MANUAL, DISABLED`) model the same feature.

**Keep `PerspectiveMode`** — it's more semantically meaningful and used in `PhotoScanProfile`.

**Migration path:**
1. Move `PerspectiveMode` to its own file in `domain/model/` if not already
2. Map `ProcessingMode.PERSPECTIVE_CORRECTION` → `PerspectiveMode.AUTO`, `ProcessingMode.CROP_ONLY` → `PerspectiveMode.DISABLED`
3. Replace `PhotoProcessingConfig.mode: ProcessingMode` with `mode: PerspectiveMode`
4. Delete `ProcessingMode` enum from `PhotoModels.kt`

**Files modified:** ~4 production, ~1 test

---

## Phase 2: Clean Layer Violations (Low-Effort Fixes)

**Goal:** Fix the most obvious dependency-direction violations with minimal code change.

**Estimated effort:** Small (1-2 hours)  
**Risk:** Low

### 2a. Remove `DomainDefaults` infrastructure imports

`DomainDefaults.kt` (in `domain/model/`) imports `DefaultIdGenerator` and `DefaultTimeProvider` from `infrastructure/adapter/`. This directly violates hexagonal dependency direction.

**Fix:**
1. Remove the `DefaultIdGenerator`/`DefaultTimeProvider` imports
2. Remove `@Volatile` default fields — make them purely abstract:
   ```kotlin
   object DomainDefaults {
       private var idGenerator: IdGenerator? = null
       private var timeProvider: TimeProvider? = null
       
       fun generateId(): String = idGenerator?.generateId() ?: UUID.randomUUID().toString()
       fun currentTimeMillis(): Long = timeProvider?.currentTimeMillis() ?: System.currentTimeMillis()
       
       fun setIdGenerator(gen: IdGenerator): () -> Unit { ... }
       fun setTimeProvider(provider: TimeProvider): () -> Unit { ... }
   }
   ```
3. Better yet: delete `DomainDefaults` entirely. All services already receive `IdGenerator`/`TimeProvider` via DI. Audit for any model default params that call `DomainDefaults` and push those defaults into the DI module instead.

### 2b. Fix `ScanService` → inject `PhotoScanDetectorPort` instead of `HybridCornerDetector`

```kotlin
// Before
class ScanService(private val hybridCornerDetector: HybridCornerDetector)

// After
class ScanService(private val photoScanDetector: PhotoScanDetectorPort)
```

`PhotoScanDetectorPort` already exists in `domain/port/PhotoScanPort.kt` but isn't used by `ScanService`. One-line fix in constructor + DI module.

### 2c. Extract `ExifValueResolver` — pure domain logic from `PhotoScanExportService`

These 8 functions in `PhotoScanExportService` are pure value transformations with no I/O:
- `resolveKeywords()`, `resolveDateOriginal()`, `formatDateToExif()`
- `parseFocalLength()`, `parseAperture()`, `parseShutterSpeed()`
- `decimalToGpsRationals()`, `applyTriStateField()`

**Fix:**
1. Create `domain/model/ExifValueResolver.kt` (object or class with zero dependencies)
2. Move all 8 functions there
3. `PhotoScanExportService` calls `ExifValueResolver.resolveKeywords(config)` etc.
4. Write focused unit tests for `ExifValueResolver` without needing `BufferedImage` mocks

### 2d. Extract `applyMargin()` — shared geometry from both services

Both `PhotoScanExportService.applyMargin()` and `FaceRegionTransformer.applyMargin()` implement the same "push corners outward from centroid" geometry.

**Fix:**
1. Create `domain/model/GeometryUtils.kt` with `applyMargin(photo: DetectedPhoto, marginFraction: Double): DetectedPhoto`
2. Delete duplicate from both services
3. Also move `distance()` from `PhotoScanExportService` into same file

### 2e. Extract `pickKeeper()` from `DuplicateScannerService`

`pickKeeper()` is pure business logic (highest resolution, RAW over JPEG, newest, etc.) with zero I/O.

**Fix:**
1. Create `domain/model/DuplicateResolution.kt` with the `pickKeeper()` logic
2. `DuplicateScannerService` delegates to it

---

## Phase 3: Unify `PhotoConfiguration` ↔ `PhotoScanConfiguration`

**Goal:** Eliminate the 51-line field-by-field bridge mapping and stop maintaining two nearly-identical metadata models.

**Estimated effort:** Medium (4-6 hours)  
**Risk:** Medium — touches WizardState, WizardContainer, both editor screens, and all export tests

### The Problem

| `PhotoConfiguration` (wizard) | `PhotoScanConfiguration` (domain) | Difference |
|---|---|---|
| `description: String = ""` | `description: String? = null` | Blank vs null semantics |
| `keywords: String = ""` | `keywords: String? = null` | Same |
| `faceRegions: List<FaceRegion>` | `faceRegions: List<FaceRegionConfig>` | Same after Phase 1a |
| `overrideDescription: FieldOverride?` | `overrideDescription: OverrideState?` | Same after Phase 1b |
| `rotationDegrees: Int` | N/A (rotation is on `DetectedPhoto`) | Rotation stored differently |
| N/A | `originalDateOverride: String?` | Separate override-value field |
| N/A | `copyOriginalExif: Boolean` | Missing from wizard model |

After Phase 1, many of these differences collapse. The remaining differences are:
1. `String = ""` vs `String? = null` — blank-vs-null convention
2. `rotationDegrees: Int` lives in `PhotoConfiguration` but not `PhotoScanConfiguration`
3. `copyOriginalExif: Boolean` lives in `PhotoScanConfiguration` but not `PhotoConfiguration`
4. `originalDateOverride` / `originalYearOverride` / `originalMonthOverride` — domain has explicit override-value fields that wizard doesn't

### Strategy

1. **Make `PhotoScanConfiguration` the canonical type.** It lives in domain, uses proper nullable types, and is what the export pipeline consumes.
2. **Delete `PhotoConfiguration` from `WizardState`.** Replace with `PhotoScanConfiguration` throughout.
3. **Handle `String = ""` vs `String? = null`** with a thin UI wrapper or extension:
   ```kotlin
   // ui/util/MetadataFieldExt.kt
   fun String?.orBlank(): String = this ?: ""
   fun String.ifBlankToNull(): String? = ifBlank { null }
   ```
   Compose `TextField(value = config.description.orBlank())` and on change `config.copy(description = newValue.ifBlankToNull())`.
4. **Add `rotationDegrees` to `PhotoScanConfiguration`** — it's already in `DetectedPhoto` but having it on config simplifies the wizard state. Or: remove it from PhotoConfiguration and let the caller read it from the `DetectedPhoto` / BoundingBox.
5. **Delete the 51-line bridge in WizardContainer** — no more `PhotoScanConfiguration(...)` construction from `PhotoConfiguration` fields.

### Migration Path

1. Add `orBlank()`/`ifBlankToNull()` extensions in `ui/util/`
2. Change `PhotoScanWizardState._photoConfigurations` from `Map<String, PhotoConfiguration>` → `Map<String, PhotoScanConfiguration>`
3. Update all 30+ `updatePhotoConfiguration` calls and face region CRUD to work with `PhotoScanConfiguration`
4. Delete `PhotoConfiguration` data class from WizardState
5. Delete bridge code in WizardContainer
6. Update all 6 test files that reference `PhotoConfiguration`
7. Add `toPhotoScanConfig()` convenience if any external consumers still need conversion

**Files modified:** ~15 production, ~6 test

---

## Phase 4: Extract Shared Metadata Editing UI

**Goal:** Deduplicate the 200+ lines of identical buffered-field state and metadata form rendering between `MetadataScreen` and `QuickEditScreen`.

**Estimated effort:** Medium (3-4 hours)  
**Risk:** Medium — touches two of the largest UI files

### 4a. Create `MetadataEditState`

```kotlin
// ui/screens/wizard/metadata/MetadataEditState.kt
class MetadataEditState {
    var bufferedDescription by mutableStateOf("")
    var bufferedKeywords by mutableStateOf("")
    var bufferedOriginalDate by mutableStateOf("")
    var bufferedYear by mutableStateOf("")
    var bufferedCameraModel by mutableStateOf("")
    var bufferedCameraMake by mutableStateOf("")
    var bufferedLensModel by mutableStateOf("")
    var bufferedFocalLength by mutableStateOf("")
    var bufferedAperture by mutableStateOf("")
    var bufferedShutterSpeed by mutableStateOf("")
    var bufferedIso by mutableStateOf("")
    // Location fields (QuickEditScreen has these, MetadataScreen doesn't yet)
    var bufferedLocationName by mutableStateOf("")
    var bufferedCity by mutableStateOf("")
    var bufferedState by mutableStateOf("")
    var bufferedCountry by mutableStateOf("")
    var bufferedGpsLatitude by mutableStateOf("")
    var bufferedGpsLongitude by mutableStateOf("")
    var bufferedSubjects by mutableStateOf("")
    
    // Focus requesters
    val descriptionFocusRequester = FocusRequester()
    val keywordsFocusRequester = FocusRequester()
    val yearFocusRequester = FocusRequester()
    val originalDateFocusRequester = FocusRequester()
    val subjectInputFocusRequester = FocusRequester()
    val cameraModelFocusRequester = FocusRequester()
    val cameraMakeFocusRequester = FocusRequester()
    val lensModelFocusRequester = FocusRequester()
    val focalLengthFocusRequester = FocusRequester()
    val apertureFocusRequester = FocusRequester()
    val shutterSpeedFocusRequester = FocusRequester()
    val isoFocusRequester = FocusRequester()
    
    /** Sync from a PhotoScanConfiguration, handling null→blank */
    fun syncFrom(config: PhotoScanConfiguration) { ... }
    
    /** Build an updated PhotoScanConfiguration from buffered values */
    fun toConfig(current: PhotoScanConfiguration): PhotoScanConfiguration { ... }
}
```

### 4b. Create shared `MetadataEditorPane` composable

Extract the repeated metadata field layout into a single composable that both screens use:
```kotlin
@Composable
fun MetadataEditorPane(
    editState: MetadataEditState,
    config: PhotoScanConfiguration,
    onConfigChange: (PhotoScanConfiguration) -> Unit,
    showAdvanced: MutableState<Boolean>,
    showLocationSection: Boolean = true,
    showSubjectSection: Boolean = true,
    modifier: Modifier = Modifier,
) { ... }
```

This replaces the inline field rendering in both `MetadataScreen` (lines 830-1050) and `QuickEditScreen` (lines 852-1050 equivalent).

### 4c. Create `MetadataFormSection` composables

Break the flat layout into composable sections that can be rearranged:
- `DescriptionKeywordsSection`
- `CameraSection`
- `LocationSection`
- `SubjectsFacesSection`
- `AdvancedMetadataSection`

Both screens already have similar sections — just organized differently. Make them configurable via parameters.

---

## Phase 5: Decompose `PhotoScanExportService` (God Service)

**Goal:** Break the 1322-line God service into focused, testable units behind proper port interfaces.

**Estimated effort:** Large (6-8 hours)  
**Risk:** Medium-High — touches core export pipeline, all export tests

### Current responsibilities → target decomposition

| Current | Target location | Layer |
|---|---|---|
| `exportPhotos()`, `exportSinglePhoto()` | `PhotoExportOrchestrator` (application) | Application |
| `cropAxisAligned()`, `rotateImage()` | `ImageTransformPort` (domain port) → `AwtImageTransformAdapter` (infra) | Infrastructure behind port |
| `writeExifMetadata()`, `readExifOutputSet()`, `writeGpsData()` | `ExifMetadataPort` (domain port) → `CommonsImagingExifAdapter` (infra) | Infrastructure behind port |
| `writeIptcData()` | `IptcMetadataPort` (domain port) → `CommonsImagingIptcAdapter` (infra) | Infrastructure behind port |
| `writeXmpFaceRegions()`, `escapeXml()` | `XmpMetadataPort` (domain port) → `CommonsImagingXmpAdapter` (infra) | Infrastructure behind port |
| `resolveKeywords()`, `parseFocalLength()`, etc. | `ExifValueResolver` (domain) — **done in Phase 2c** | Domain |
| `applyMargin()` | `GeometryUtils` (domain) — **done in Phase 2d** | Domain |
| `generateUniqueFileName()` | `NamingPort` (existing) | Already exists |

### Step-by-step

1. **Create `ExifMetadataPort`, `IptcMetadataPort`, `XmpMetadataPort`, `ImageTransformPort`** in `domain/port/` — none should reference `BufferedImage` or `java.io.File`
2. **Create adapter implementations** in `infrastructure/adapter/` — these hold the JVM/AWT/commons-imaging code
3. **Refactor `PhotoScanExportService`** to depend on ports, not JVM types directly
4. **Slim `PhotoScanExportService`** down to thin orchestrator (~200 lines) that calls ports
5. **Port `PhotoScanExportPort` update** — once `BufferedImage` is hidden behind `DomainImage`, the port interface becomes JVM-free
6. **Update DI module** to wire adapters
7. **Move/rewrite tests** — `PhotoScanExportServiceTest.kt` (1066 lines) tests should cover the orchestrator's coordination logic; adapter tests cover JVM-specific I/O separately

---

## Phase 6: Introduce `DomainImage` — Multiplatform Foundation

**Goal:** Remove `java.awt.image.BufferedImage` from all domain and application code, enabling Android/iOS/WASM targets.

**Estimated effort:** Large (8-12 hours)  
**Risk:** High — touches every image-processing function  
**Depends on:** Phase 5 (export service decomposition makes this feasible)

### Current leak sites

| Layer | Files using `BufferedImage` |
|---|---|
| **Domain ports** | `PhotoScanDetectorPort`, `PhotoScanExportPort` |
| **Application** | `PerspectiveCorrectionService`, `PhotoScanExportService`, `ScanService`, `FaceRegionTransformer` |
| **UI** | `MetadataScreen`, `QuickEditScreen`, `WizardContainer`, `ImagePreviewScreen` |
| **Infrastructure** | `ImageRepositoryAdapter`, `Platform.kt`, `VideoThumbnailAdapter` |

### Strategy

1. **Create `DomainImage` as a Kotlin `expect`/`actual` type:**
   ```kotlin
   // domain/model/DomainImage.kt (common)
   expect class DomainImage {
       val width: Int
       val height: Int
   }
   
   // domain/model/DomainImage.kt (JVM actual)
   actual class DomainImage actual constructor(val wrapped: BufferedImage) {
       actual val width: Int get() = wrapped.width
       actual val height: Int get() = wrapped.height
   }
   ```
   (Or simpler: start with a JVM wrapper class without expect/actual until multiplatform is real.)

2. **Update port interfaces** to use `DomainImage` instead of `BufferedImage`
3. **Update application services** — `PerspectiveCorrectionService`, `ScanService`, `FaceRegionTransformer` all operate on `DomainImage`
4. **Update UI screens** — compose `Image(painter = ...)` already uses platform-agnostic painters; the `BufferedImage` parameter in screen composables becomes `DomainImage` or better yet, a `ImagePainter` factory
5. **Adapters** do the `DomainImage ↔ BufferedImage` conversion at the infrastructure boundary only

### Practical starting approach

Before going full `expect`/`actual`, start with a simple JVM wrapper:

```kotlin
// domain/model/DomainImage.kt
data class DomainImage(val awtImage: BufferedImage) {
    val width: Int get() = awtImage.width
    val height: Int get() = awtImage.height
}
```

This is an opaque wrapper — not multiplatform yet, but:
- Hides `BufferedImage` from domain ports
- Makes the refactoring tractable (no build system changes)
- Can be upgraded to `expect`/`actual` later

---

## Phase 7: Decompose `PhotoScanWizardState` (God Object)

**Goal:** Break the 1681-line, 30-StateFlow, 104-function God object into focused state holders.

**Estimated effort:** Large (8-12 hours)  
**Risk:** High — every wizard screen depends on it  
**Depends on:** Phase 3 (model unification), Phase 4 (shared metadata UI)

### Current structure → target decomposition

| Current responsibility | Target | Lines (est.) |
|---|---|---|
| Navigation (step transitions) | `WizardNavigationState` | ~50 |
| Box selection, drag, zoom | `BoundingBoxState` (already partially extracted via `BoundingBoxList`, `ZoomController`) | ~200 |
| Undo/redo | `UndoRedoManager` (already extracted!) | ~0 (done) |
| Image cache, batch processing | `ImageProcessingState` | ~150 |
| Photo config CRUD, metadata, face regions | `PhotoConfigurationState` | ~300 |
| Corner selection, four-point mode | `RefinementState` | ~200 |
| Metadata buffered fields, focus | `MetadataEditState` (**Phase 4**) | ~100 |

### Migration path

1. `WizardNavigationState` — extract step/step-history StateFlows + transition functions
2. `ImageProcessingState` — extract `currentImageIndex`, `batchFiles`, `sourceImage`, `preProcessedImages`
3. `PhotoConfigurationState` — extract all `photoConfigurations`, `updatePhotoConfiguration` overloads, face region CRUD, metadata apply functions
4. Slim `PhotoScanWizardState` to a **facade** that delegates to composed state holders:
   ```kotlin
   class PhotoScanWizardState(
       val navigation: WizardNavigationState,
       val boxes: BoundingBoxState,
       val images: ImageProcessingState,
       val configs: PhotoConfigurationState,
       val refinement: RefinementState,
   )
   ```
5. Screens update: instead of `state.selectAllMetadata()`, they call `state.configs.selectAllMetadata()`
6. The facade pattern means we can do this incrementally — extract one sub-state at a time

---

## Phase 8: Move Geometry to Domain + Split `ImportProfile.kt`

**Goal:** Put pure-domain types where they belong and split the 762-line `ImportProfile.kt`.

**Estimated effort:** Small (1-2 hours)  
**Risk:** Low

### 8a. Move `BoundingBox`, `BoundingBoxList`, `FourPointState` to `domain/model/`

These 770 lines contain pure geometry and state logic — no I/O, no platform deps (only `kotlin.math`). They're currently in `infrastructure/wizard/` but should be in `domain/`.

### 8b. Split `ImportProfile.kt` (762 lines) into dedicated files

Current file contains 8 types:
- `TabSettings` → `domain/model/TabSettings.kt`
- `ImportProfile` → `domain/model/ImportProfile.kt` (stays)
- `AppSettings` → `domain/model/AppSettings.kt` (300+ lines, deserves own file)
- `WindowState` → `domain/model/WindowState.kt`
- `AppTheme` → `domain/model/AppTheme.kt`
- `MetadataHistory` → `domain/model/MetadataHistory.kt`
- Plus any others in the file

---

## Phase 9: Create `FileSystemPort` — Abstract File Operations

**Goal:** Replace direct `java.io.File` usage in application services with a port abstraction.

**Estimated effort:** Medium (4-6 hours)  
**Risk:** Medium  
**Depends on:** Phase 5 (or can be done in parallel if we create the port first)

### Current `java.io.File` leak sites in application

| Service | Operations |
|---|---|
| `DuplicateScannerService` | `File()`, `.renameTo()`, `.delete()` |
| `ImportExecutor` | `File()`, `.parentFile?.mkdirs()` |
| `ReorganizeService` | `File()`, `.renameTo()`, `.copyTo()`, `.walkBottomUp()`, `System.getProperty("user.home")` |
| `PhotoScanExportService` | `File()`, `FileOutputStream`, `.mkdirs()` |
| `WatchFolderService` | `java.nio.file.*`, `FileSystems`, `WatchService` |

### Design

```kotlin
// domain/port/FileSystemPort.kt
interface FileSystemPort {
    fun exists(path: String): Boolean
    fun mkdirs(path: String): Boolean
    fun move(source: String, destination: String): Boolean
    fun copy(source: String, destination: String): Boolean
    fun delete(path: String): Boolean
    fun listFiles(directory: String, recursive: Boolean = false): List<String>
    fun watchDirectory(path: String, callback: (String) -> Unit): WatchHandle
    fun resolve(base: String, relative: String): String
    fun userHome(): String
}

// For image-specific operations (already partially in ImageRepositoryPort)
// Consider merging or keeping separate based on whether ImageRepositoryPort
// should remain image-focused
```

### Migration path

1. Create `FileSystemPort` interface
2. Create `JvmFileSystemAdapter` in `infrastructure/adapter/` (wraps `java.io.File`, `java.nio.file`)
3. Register in DI module
4. Replace `File()` constructor calls in application services with `fileSystem.resolve()`
5. Replace `File.renameTo()` with `fileSystem.move()`
6. Move journal persistence in `ReorganizeService` behind the port
7. Move `WatchFolderService` NIO code behind `fileSystem.watchDirectory()`

---

## Phase 10: Coordinate System Unification

**Goal:** Consolidate `PercentPoint` / `PhotoCorner` / `Point` and `PercentBounds` / `PhotoBounds` / `BoundingBox`.

**Estimated effort:** Medium (3-4 hours)  
**Risk:** Medium — geometry types are used in rendering hot paths  
**Depends on:** Phase 8a (BoundingBox moved to domain)

### Current state — 3 point types, 3 bounds types

| Type | Package | Coordinate space | Numeric type |
|---|---|---|---|
| `PercentPoint` | `domain.model.PhotoModels` | Normalized 0-1 | Double |
| `PhotoCorner` | `domain.model.PhotoScanModels` | Pixel? | Float |
| `Point` | `infrastructure.wizard.BoundingBox` | Normalized 0-1 | Double |

`PercentPoint` and `Point` are essentially the same (both normalized, both Double). `PhotoCorner` is the odd one out with Float and unclear coordinate space.

### Strategy

1. Create `domain/model/Point2D(val x: Double, val y: Double)` — the canonical point type
2. Create `domain/model/Bounds2D(val minX, maxX, minY, maxY: Double)` — the canonical bounds type
3. Add `enum class CoordinateSpace { NORMALIZED, PIXEL }` if needed to distinguish
4. Replace `PercentPoint` → `Point2D`, `Point` → `Point2D`
5. Replace `PhotoCorner` → `Point2D` (after auditing all usages)
6. Replace `PercentBounds` → `Bounds2D`
7. Keep `BoundingBox` as a higher-level domain type that composes `Bounds2D` + corners

---

## Dependency Graph Between Phases

```
Phase 1 (Domain Duplicates) ──┬──→ Phase 3 (Unify PhotoConfig) ──→ Phase 4 (Shared Metadata UI)
                               │                                       │
                               │                                       └──→ Phase 7 (Decompose WizardState)
                               │
Phase 2 (Layer Violations) ───┼──→ Phase 5 (Decompose ExportService) ──→ Phase 6 (DomainImage)
                               │
                               └──→ Phase 8 (Move Geometry) ──→ Phase 10 (Coordinate Unification)
                               
Phase 9 (FileSystemPort) — independent, can be done any time after Phase 2e
```

---

## Phase Ordering Summary

| Phase | Name | Effort | Risk | Unblock value |
|---|---|---|---|---|
| **1** | Consolidate Domain Duplicates | Small | Low | Eliminates 3 duplicate triples, removes bridge code |
| **2** | Clean Layer Violations | Small | Low | Fixes 5 specific dependency-direction bugs |
| **3** | Unify PhotoConfig ↔ PhotoScanConfig | Medium | Medium | Kills 51-line bridge, one canonical model |
| **4** | Extract Shared Metadata UI | Medium | Medium | Deduplicates ~400 lines across 2 screens |
| **5** | Decompose PhotoScanExportService | Large | Med-High | Testable EXIF/metadata writer, port interfaces |
| **6** | Introduce DomainImage | Large | High | Multiplatform enablement |
| **7** | Decompose PhotoScanWizardState | Large | High | Breaks God object, focused testing |
| **8** | Move Geometry + Split ImportProfile | Small | Low | Proper layering, code organization |
| **9** | Create FileSystemPort | Medium | Medium | Abstract file ops for testability |
| **10** | Coordinate System Unification | Medium | Medium | One Point2D to rule them all |

### Recommended execution order

1. **Start with Phase 1** — everything else depends on having one canonical `FaceRegion`, `OverrideState`, `AspectRatio`, `PerspectiveMode`
2. **Then Phase 2** — quick layer-violation fixes that are independent
3. **Then Phase 3** — requires Phase 1 done, unlocks Phase 4 and 7
4. **Phase 4 and 8 can run in parallel** — they're independent
5. **Phase 5 next** — requires Phase 2 done, but unlocks Phase 6
6. **Phase 6 and 7 are the biggest** — can be done in either order; 6 is more impactful for multiplatform, 7 is more impactful for daily development velocity
7. **Phase 9 and 10** — can be done any time after their prerequisites

### Estimated total effort

| | Phases 1-2 | Phases 3-4 | Phase 5 | Phases 6-7 | Phases 8-10 | Total |
|---|---|---|---|---|---|---|
| Hours | 3-5 | 7-10 | 6-8 | 16-24 | 8-12 | **40-59** |

---

## Audit Notes (Raw Findings)

These are the original findings from the architecture scan that drove each phase:

### Duplicate model triples
- `FaceRegion` (wizard) / `FaceRegionConfig` (domain) / `SourceFaceRegion` (application) — identical fields
- `OverrideState` (wizard) / `OverrideState` (domain) — identical values; `FieldOverride` wraps wizard version with dead `value` field
- `AspectRatio` in 3 locations, `ProcessingMode`/`PerspectiveMode` overlap

### God objects/services
- `PhotoScanExportService`: 1322 lines, 5+ responsibilities, 30+ JVM imports
- `PhotoScanWizardState`: 1681 lines, 30 StateFlows, 104 functions

### Domain port leaks (java.awt / java.io in port interfaces)
- `PhotoScanDetectorPort.detectPhotos(image: BufferedImage)`
- `PhotoScanExportPort.exportSinglePhoto(sourceImage: BufferedImage, sourceFile: java.io.File?)`
- `ImageRepositoryPort` leaks `java.io.File` in 4 methods

### Layer violations
- `DomainDefaults` (domain) imports `DefaultIdGenerator`/`DefaultTimeProvider` (infrastructure)
- `ScanService` (application) imports `HybridCornerDetector` (infrastructure)
- 6 application services use `java.io.File` directly without a port
- `BoundingBox`/`BoundingBoxList`/`FourPointState` in `infrastructure/wizard/` contain no I/O — should be in domain

### Business logic trapped in wrong layer
- `DuplicateScannerService.pickKeeper()` — domain decision logic
- `PhotoScanExportService` — 8 pure value-resolution functions
- `FaceRegionTransformer` — coordinate transformation + XMP parsing (domain math mixed with I/O)
- `PerspectiveCorrectionService` — geometry functions (calculateOutputDimensions, isValidQuadrilateral)
- `ImportService.detectRawJpegPairs()` — domain business rule