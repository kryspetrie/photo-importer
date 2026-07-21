# Petrie Image Importer — Production Readiness Assessment

> **Assessment Date**: July 2026  
> **Application Version**: Current main branch  
> **Assessor**: Architecture review of Kotlin + Compose Desktop codebase

---

## 1. Executive Summary

Petrie Image Importer is a **desktop photo import and management application** built with **Kotlin + Compose Desktop**, following a hexagonal architecture with clean separation across domain, application, infrastructure, and UI layers.

The application's key differentiating feature is **physical photo scanning with ML-backed corner detection**, combined with face tagging (MWG-RS regions), batch EXIF editing, and automatic orientation detection — a workflow no competitor currently offers end-to-end.

| Metric | Value |
|--------|-------|
| Total main source LOC | ~48,500 |
| Total test LOC | ~12,000 |
| Distributable JAR size | 368 MB |
| ONNX model contribution | ~330 MB (single model) |
| Number of ONNX models | 5 |
| Architecture pattern | Hexagonal (domain → application → infrastructure → UI) |
| DI framework | Koin 4.0.0 |
| UI framework | Compose Desktop (Material 3) |

**Overall assessment**: Petrie has an impressive feature set with genuine market differentiation, but several architectural and distribution issues must be addressed before a production-quality release. The most critical items are extracting ViewModels from god composables, lazy-downloading the 330 MB orientation model, and resolving UI→infrastructure layer violations.

---

## 2. Code Quality Assessment

### 2.1 God Composables — 🔴 Critical

Several composables violate single-responsibility by mixing UI rendering, state management, business logic, and I/O operations in single functions. This is the single highest-impact issue in the codebase.

| Composable | Lines | `koinInject` Calls | State Variables | Primary Concern |
|-----------|-------|---------------------|-----------------|-----------------|
| `MetadataEditorScreen` | 1,221 | 11 | 17+ | EXIF editing, face tagging, location, preview |
| `FaceSelectorOverlay` | 1,193 | — | 7 | Face rectangle drawing, tagging, editing |
| `BackImagePickerDialog` | 536 | — | 13 | Back-of-photo image selection and cropping |
| `MediaImportScreen` | 388 | — | — | Entire import workflow orchestration |
| `WizardContainer` | — | 8 | — | Export coordination across wizard steps |

**Impact**:
- Impossible to unit-test screen logic without Compose UI testing infrastructure
- State initialization order bugs are likely and hard to trace
- Any change to one aspect risks regressions in another
- Cannot use `@Preview` because composables depend on Koin injections

**Fix**: Extract ViewModels. Each screen should have a ViewModel holding all state and business logic. The composable becomes a thin shell observing `StateFlow` from the ViewModel. This single change unlocks testability, `@Preview`, process-death recovery, and compositional clarity.

```
Before:
@Composable
fun MetadataEditorScreen(...) {
    val serviceA = koinInject<ServiceA>()
    val serviceB = koinInject<ServiceB>()
    // ... 9 more injections ...
    var state1 by remember { mutableStateOf(...) }
    // ... 16 more state variables ...
    // 1,200 lines of mixed UI + logic
}

After:
class MetadataEditorViewModel(
    private val serviceA: ServiceA,
    // ... injected via constructor ...
) : ViewModel() {
    val uiState: StateFlow<MetadataEditorUiState> = ...
    fun onEvent(event: MetadataEditorEvent) { ... }
}

@Composable
fun MetadataEditorScreen(viewModel: MetadataEditorViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsState()
    // ~100-200 lines of pure UI
}
```

---

### 2.2 Business Logic in UI Layer — 🔴 Critical

Screens call infrastructure services directly via Koin DI instead of going through application services. This violates the hexagonal architecture the project was designed around and undermines the entire purpose of the layered design.

**13 direct UI → Infrastructure violations identified**:

| Infrastructure Type | Files Violating | Count |
|--------------------|-----------------|-------|
| `AppPaths` | 5 files | 5 |
| `AppLogger` | 3 files | 3 |
| `Platform` | 2 files | 2 |
| `correctPerspective` (infrastructure function) | 2 files | 2 |
| `ThumbnailExtractorAdapter` | 1 file | 1 |

**Why this matters**: The hexagonal architecture's value is that the domain and application layers have zero infrastructure dependencies. When UI composables reach through the application layer directly to infrastructure, you lose:
- Testability (can't swap infrastructure in UI tests)
- Architecture enforcement (the `HexagonalArchitectureKonsistTest` doesn't catch these because the violations go UI→Infrastructure, not Domain→Infrastructure)
- Clear ownership of business workflows

**Fix**: Create application services that wrap these infrastructure calls. UI should only call application service interfaces.

```kotlin
// Instead of UI calling:
val paths = koinInject<AppPaths>()
val outputPath = paths.outputDirectory

// UI should call:
class PathService(private val appPaths: AppPaths) {
    fun getOutputDirectory(): Path = appPaths.outputDirectory
}
// And inject PathService in UI
```

---

### 2.3 PhotoScanConfiguration God Object — 🟠 High

`PhotoScanConfiguration` is a 44-field data class mixing geometry, EXIF, location, face regions, back-of-photo imaging, and override tri-states into a single type. Every function taking this type implicitly depends on all 44 fields.

**Fix**: Decompose into focused value objects:

```kotlin
data class GeometrySettings(
    val perspectiveCorrection: Boolean,
    val rotationDegrees: Int,
    val aspectRatio: Float?,
    val correctionStrategy: CorrectionStrategy,
    val detectionMode: DetectionMode
)

data class ExifOverrides(
    val description: String?,
    val keywords: List<String>,
    val dates: DateOverrides?,
    val camera: String?,
    val lens: String?,
    val exposure: ExposureOverrides?,
    val overrideStates: Map<ExifField, OverrideState>
)

data class LocationMetadata(
    val locationName: String?,
    val address: String?,
    val city: String?,
    val state: String?,
    val country: String?,
    val gpsLat: Double?,
    val gpsLon: Double?
)

data class BackImageConfig(
    val backImageMode: BackImageMode,
    val backImageSourcePath: Path?,
    val backCropNormalized: RectF?,
    val backCropRotation: Float
)

data class PhotoScanConfiguration(
    val geometry: GeometrySettings,
    val exif: ExifOverrides,
    val location: LocationMetadata,
    val backImage: BackImageConfig,
    // Only top-level properties that don't belong to a subgroup
)
```

This makes it clear what each function depends on, enables targeted testing, and reduces the cognitive load of constructing configurations.

---

### 2.4 FileSystemPort ISP Violation — 🟡 Medium

`FileSystemPort` has 21 methods on a single interface. Any test needing file operations must mock all 21 methods, even if it only uses one.

**Fix**: Split into role-based interfaces following the Interface Segregation Principle:

```kotlin
interface FileReadPort {
    fun exists(path: Path): Boolean
    fun readFile(path: Path): ByteArray
    fun listFiles(path: Path): List<Path>
    // ... read-only methods
}

interface FileWritePort {
    fun writeFile(path: Path, content: ByteArray)
    fun createDirectory(path: Path)
    fun move(from: Path, to: Path)
    // ... write methods
}

interface FileMetadataPort {
    fun lastModified(path: Path): Instant
    fun fileSize(path: Path): Long
    fun mimeType(path: Path): String
    // ... metadata methods
}
```

---

### 2.5 Koin Version Mismatch — ✅ Fixed

Was mixing `koin-core:3.5.6` with `koin-compose:4.0.0`. Now unified to `4.0.0` across all modules.

---

### 2.6 Duplicate Dependencies — ✅ Fixed

`slf4j-simple` was declared twice in `build.gradle.kts`. Duplicate removed.

---

## 3. Maintainability Assessment

### 3.1 No ViewModel Pattern — 🔴 Critical

There are **zero ViewModels** across 48,500 lines of code. All state is managed via `mutableStateOf`/`remember` inside `@Composable` functions.

**Consequences**:

| Issue | Explanation |
|-------|-------------|
| No unit testing of screen logic | Business logic is entangled with Compose runtime; requires Compose UI testing to verify |
| No process-death recovery | `remember` state is lost on process death; no `SavedStateHandle` equivalent |
| No shared state between screens | Each screen manages its own state independently; no coordination mechanism |
| No `@Preview` support | Composables with Koin injections cannot be previewed in Android Studio / IntelliJ |
| State hoisting complexity | State must be manually threaded through composable parameters |

This is the **single most impactful architectural gap** in the project. Without ViewModels, the other code quality issues (god composables, untestable logic) cannot be properly resolved.

---

### 3.2 Hard-coded Strings — 🟠 High

**250+ UI strings** are embedded directly in composables with no resource bundle or internationalization infrastructure.

| File | Hard-coded Strings |
|------|-------------------|
| `ImagePreviewScreen` | 54 |
| `EditSections` | 38 |
| `MetadataEditorScreen` | 37 |
| Other screens | 120+ |

**Impact**: No localization path exists. Any text change requires code modification and recompilation. No translator can work independently.

**Fix**: Extract strings into a localization system. See [LOCALIZATION.md](./LOCALIZATION.md) for a full proposal.

---

### 3.3 No Design Token System — 🟡 Medium

86+ hard-coded `.dp` values in `EditSections.kt`, 74 in `MetadataEditorScreen.kt`. Colors reference raw hex values. No spacing scale or typography scale beyond Material defaults.

```kotlin
// Current: magic numbers everywhere
Spacer(modifier = Modifier.height(16.dp))
Text(color = Color(0xFF6B7280), fontSize = 12.sp)

// Target: design tokens
Spacing.md // 16.dp
ColorTheme.onSurfaceVariant
Typography.bodySmall
```

**Fix**: Create a `Theme.kt` with spacing, color, and typography tokens. Use CompositionLocal providers for theme switching.

---

### 3.4 Excessive Parameter Counts — 🟡 Medium

| Function | Parameter Count |
|----------|----------------|
| `WizardStepContent` | 13 |
| `DrawCorners` | 12 |
| `RefinementCanvasContent` | 11 |
| `MediaImportProgressView` | 10 |

**Fix**: Group related parameters into data classes or use ViewModels to hold state, reducing composable signatures to 3-5 parameters.

---

### 3.5 Test Coverage Gaps — 🟡 Medium

| Gap | Detail |
|-----|--------|
| **40+ production files** | No matching test file exists |
| **No UI integration tests** | Zero Compose UI tests |
| **No ViewModel tests** | Because no ViewModels exist |
| **Monolithic test file** | `PhotoScanWizardStateTest`: 1,280 LOC, 89 tests |
| **Only architectural guard** | `HexagonalArchitectureKonsistTest` — doesn't catch UI→Infrastructure |

**Priority test gaps** (by risk):
1. Import workflow end-to-end (file copy + EXIF write + metadata)
2. EXIF writing with commons-imaging (data corruption risk)
3. Orientation detection failure paths
4. Face region MWG-RS serialization
5. File move/copy operations

---

## 4. Complexity Assessment

### 4.1 Nesting Depth — 🔴 Critical

Multiple composables exceed the 6-8 level maximum for readable, testable code:

| Composable | Max Nesting Depth |
|-----------|-------------------|
| `MetadataEditorScreen` | 17 |
| `FaceSelectorOverlay` | 12 |
| `EditScreen` | 12 |
| `EditSections` | 11 |

**17 levels of nesting** in `MetadataEditorScreen` means developers must track 17 scopes simultaneously when reading or modifying the code. This is a significant bug source and review blocker.

**Fix**: Extract nested blocks into named composables or ViewModel methods. Each nesting level >4 should be a candidate for extraction.

---

### 4.2 Model Size — 🔴 Critical

**397 MB of ONNX models** in the distributable (only ~67 MB bundled, the rest lazy-downloaded):

| Model | Size | Recommendation |
|-------|------|----------------|
| `orientation_detection_model.onnx` | 346 MB | **Lazy-download + INT8 quantize** (→ ~80 MB) |
| `face_embedding_model.onnx` | 8 MB | **Lazy-download** (zip archive from Hailo Model Zoo) |
| `pose_model.onnx` | 38 MB | Bundle (acceptable) |
| `face_detection_model.onnx` | 10 MB | Bundle (acceptable) |
| `corner_regression_model.onnx` | 9.5 MB | Bundle (acceptable) |
| `detection_model.onnx` | 9.4 MB | Bundle (acceptable) |

The orientation and face embedding models are lazy-downloaded on first use via
[HuggingFaceModelDownloadAdapter](../src/main/kotlin/org/kryspetrie/fileimport/infrastructure/download/HuggingFaceModelDownloadAdapter.kt).
See [MODEL_MANAGEMENT.md](./MODEL_MANAGEMENT.md) for URL details and manual installation instructions.

The orientation model alone accounts for **89.6%** of the total model size. It is a ViT-based model that:
- Is only needed when auto-orient is enabled
- Can be INT8 quantized from ~346 MB → ~80 MB with **<1% accuracy loss**
- Is lazy-downloaded on first use with a progress indicator

The face/pose/corner/detection models total ~67 MB — reasonable to bundle.

**Impact**: 368 MB download for a photo import tool will face **download abandonment**. Users on slow connections or with limited storage will not tolerate this.

---

### 4.3 Dependency Bloat — 🟡 Medium

| Dependency | Size | Assessment |
|-----------|------|------------|
| BoofCV | ~30-50 MB transitive | Works well for corner detection. Keep. |
| ONNX Runtime | Significant | Justified for ML inference. Keep. |
| `commons-imaging:1.0-alpha3` | Small | **Only viable Java EXIF writer**, but alpha quality. Risk of data corruption. Must add comprehensive tests. |
| Koin | Moderate | Now unified at 4.0.0. Keep. |
| jline | Small | Used for CLI progress output. Acceptable. |

**`commons-imaging:1.0-alpha3` risk**: This is the only maintained Java library for EXIF writing, but its alpha status means potential data corruption on user photos. This is an **existential risk** for a photo management application. Comprehensive round-trip tests are mandatory.

---

## 5. Usability Assessment

### 5.1 Auto-Orient UX Gaps

| Issue | Description |
|-------|-------------|
| No progress indicator | Batch orientation detection shows no feedback during processing |
| Silent failure | When the orientation model is absent, the feature disables silently — no user explanation |
| Indicator placement | `AutoOrientIndicator` appears below settings, not near the Import action button where users look |

**Fixes**:
- Add indeterminate progress bar during orientation detection
- Show explanatory message when model is not yet downloaded
- Move orientation status near the primary action button

---

### 5.2 Wizard State

`PhotoScanWizardState` has **34 public methods** and **494 LOC**, mixing state management with I/O coordination. There is no state machine enforcing valid step transitions — nothing prevents skipping steps or entering invalid states.

**Fix**: Implement a finite state machine for wizard navigation:
```kotlin
sealed class WizardStep {
    object Scan : WizardStep()
    object Refine : WizardStep()
    object Metadata : WizardStep()
    object Review : WizardStep()
}

val validTransitions = mapOf(
    WizardStep.Scan to setOf(WizardStep.Refine),
    WizardStep.Refine to setOf(WizardStep.Metadata, WizardStep.Scan),
    // ...
)
```

---

### 5.3 Error Handling

Error handling is **inconsistent across screens**:

| Screen | Error Mechanism |
|--------|----------------|
| MediaImport | `ErrorCard` composable |
| SummaryScreen | `AlertDialog` |
| MetadataEditor | Toast-like notifications |
| Orientation detection | Silent failures (returns null) |

**Fix**: Adopt a unified error presentation strategy. Recommended: snackbar for transient errors, `ErrorCard` for persistent/blocking errors, `AlertDialog` for destructive confirmations.

---

### 5.4 No Undo for Import — 🔴 Critical

The import workflow has **no rollback capability**. Once files are moved to the output directory and EXIF metadata is written, there is no way to undo the operation.

This is the **#1 fear of photo organization app users**. A bad import that moves files to wrong folders with no undo will destroy user trust permanently.

**Fix** (prioritized):
1. **Immediate**: Write a transaction log before import begins. Allow "undo last import" from the log.
2. **Short-term**: Copy files instead of moving them during import. Delete originals only after explicit user confirmation.
3. **Long-term**: Full undo/redo stack for all operations.

---

## 6. Market Analysis

### 6.1 Competitive Landscape

| App | Strengths | Weaknesses vs Petrie |
|-----|-----------|---------------------|
| **Adobe Lightroom** | Industry standard, cloud sync, mobile companion, plugin ecosystem | No physical scan workflow, no back-of-photo, subscription model |
| **macOS Photos** | Free, iCloud sync, face detection | No batch import rules, no EXIF override, no physical scan |
| **digiKam** | Open-source, comprehensive metadata, face detection, map view | Java-based (slow), poor UX, no physical scan workflow |
| **PhotoScan / VueScan** | Purpose-built for scanning | No EXIF override, no face tagging, no batch processing rules |
| **Excire Foto** | AI tagging, duplicate finding | No import workflow, no physical scan |

### 6.2 Petrie's Differentiated Position

Petrie uniquely bridges **physical photo scanning → digital import → full metadata control**. No single competitor offers:

- **Physical photo scanning** with ML corner detection
- **Back-of-photo imaging** (capturing handwritten notes on photo backs)
- **MWG-RS face regions** in EXIF metadata
- **Batch EXIF override** with per-field tri-state (set/keep/clear)
- **Automatic orientation detection** via ViT model

This combination is **genuinely unique** in the market and serves an unmet need for archivists, genealogists, and photo preservationists.

### 6.3 Market Risks

| # | Risk | Severity | Mitigation |
|---|------|----------|------------|
| 1 | **368 MB download** for a photo import tool — will face download abandonment | High | Lazy model download; INT8 quantization |
| 2 | **Kotlin Desktop + Compose Desktop** is niche. Immature ecosystem, limited accessibility, no mobile path | Medium | Evaluate Kotlin Multiplatform expansion path |
| 3 | **No cloud sync, no mobile companion, no sharing** — table stakes in 2024 | High | Prioritize cloud sync API integration |
| 4 | **Alpha EXIF writer** (`commons-imaging:1.0-alpha3`) — data corruption risk | High | Comprehensive round-trip EXIF tests; monitor upstream |
| 5 | **No undo for import** — users' #1 fear | Critical | Transaction log + undo-last-import |
| 6 | **No plugin system** — every feature is hardcoded | Medium | See [PLUGIN_SYSTEM.md](./PLUGIN_SYSTEM.md) |

### 6.4 Technical Market Fit

| Dimension | Score | Notes |
|----------|-------|-------|
| Feature depth | 8/10 | Impressive — scan, import, EXIF, faces, back-of-photo |
| Architecture intent | 7/10 | Hexagonal correctly designed at the package level |
| Architecture execution | 4/10 | 13 violations, no ViewModels, god object |
| Testability | 3/10 | 0% UI logic test coverage |
| Distribution readiness | 3/10 | 368 MB, no lazy model download |
| Localization readiness | 1/10 | 250+ hardcoded strings |
| Performance | 5/10 | ONNX async, but 330 MB in JAR |
| Competitive differentiation | 7/10 | Physical scanning + faces + back-of-photo is unique |
| **Weighted average** | **4.7/10** | **Not production-ready. P0 fixes are essential.** |

---

## 7. Prioritized Fix Roadmap

### P0 — Must Fix (blocks quality releases)

| # | Fix | Effort | Rationale |
|---|-----|--------|-----------|
| 1 | **Extract ViewModels** for all screens with business logic | High | Enables testing, preview, state recovery. Foundational for all other fixes. |
| 2 | **Lazy-download orientation model** (330 MB), prompt on first use | Medium | Cuts download from 368 MB → ~38 MB. Prevents abandonment. |
| 3 | **Decompose `MetadataEditorScreen`** (1,221 lines → 8-10 focused composables + ViewModel) | Medium | Most complex composable; highest bug risk; blocks any metadata feature work. |
| 4 | ~~Fix Koin version mismatch~~ | Done | — |
| 5 | ~~Remove duplicate slf4j~~ | Done | — |

### P1 — Should Fix (improves maintainability)

| # | Fix | Effort | Rationale |
|---|-----|--------|-----------|
| 6 | **Decompose `PhotoScanConfiguration`** into value objects | Medium | Reduces 44-field coupling; makes each concern independently testable. |
| 7 | **Fix 13 UI→Infrastructure violations** | Medium | Restores architecture integrity; enables infrastructure swapping in tests. |
| 8 | **Split `FileSystemPort`** into read/write/metadata | Low | ISP compliance; reduces mock burden; low risk. |
| 9 | **Extract UI strings** into localization system | Medium | 250+ strings; prerequisite for any non-English users. |
| 10 | **Create design token system** (spacing, colors, typography) | Medium | 160+ magic dp values; consistency and theming foundation. |

### P2 — Nice to Have

| # | Fix | Effort | Rationale |
|---|-----|--------|-----------|
| 11 | Decompose remaining god composables | Medium | Improves readability and reduces nesting. |
| 12 | Add import/export workflow integration tests | Medium | Validates the core user workflow end-to-end. |
| 13 | INT8 quantize orientation model (330 → ~80 MB) | Medium | Reduces on-disk size and memory usage. |
| 14 | Add wizard state machine | Medium | Prevents invalid step transitions. |
| 15 | Plugin system | High | See [PLUGIN_SYSTEM.md](./PLUGIN_SYSTEM.md). |

---

## 8. Plugin System Proposal

> See [PLUGIN_SYSTEM.md](./PLUGIN_SYSTEM.md) for the full proposal including plugin lifecycle, API surface, security model, and reference implementation.

---

## 9. Localization Proposal

> See [LOCALIZATION.md](./LOCALIZATION.md) for the full proposal including string extraction strategy, resource bundle format, RTL considerations, and implementation timeline.

---

## Appendix: Methodology

This assessment was generated through:
- Static analysis of the complete codebase (48,500 LOC main source, 12,000 LOC tests)
- Dependency tree analysis and JAR size profiling
- Architectural layer violation scanning (Konsist + manual review)
- Composable complexity metrics (nesting depth, parameter count, injection count)
- Market comparison against 5 competing products
- Competitive positioning analysis

All findings are reproducible from the current `main` branch.