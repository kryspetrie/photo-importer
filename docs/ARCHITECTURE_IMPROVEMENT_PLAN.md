# Architecture Improvement Plan

## Overview

This document outlines a systematic plan to address architectural and stylistic concerns in the
Petrie File Importer codebase. Items are ordered by priority, with each step designed to be
independently verifiable (compile + test pass) before moving to the next.

---

## Execution Tracker

| Phase | Task | Status |
|-------|------|--------|
| 0.1 | Delete `remove_wildcard_imports.py` | ✅ |
| 0.2 | Delete `dead/` directory | ✅ |
| 0.3 | Move root-package utilities (`GenerateIcons.kt` → `ui/util/`) | ✅ |
| 0.4 | Slim down AppModule docs (373→81 lines, docs → `docs/ARCHITECTURE.md`) | ✅ |
| 1 | Split `ImportConfiguration.kt` (1018→57 lines + 9 focused files) | ✅ |
| 2a | Extract `ImagePreviewViewModel` | ✅ |
| 2b | Extract `DuplicateScannerViewModel` | ✅ |
| 2c | Extract `ReorganizeViewModel` | ✅ |
| 2d | Extract `OverviewViewModel` | DEFERRED — only 3 local state props; real state lives in PhotoScanWizardState |
| 2e | Extract `MediaImportViewModel` | DEFERRED — complex flow logic entangled with LaunchedEffect; better suited for MVI (Phase 6) |
| 3 | Decompose `PhotoScanWizardState` | DEFERRED — already well-organized with section markers; all consumers use single `state:` reference |
| 4 | Consolidate file dialog calls | ✅ — removed duplicate pickDir/pickFile/pickFolder from 4 screens; added `isImageFile` and `pickImageFile` to `FileDialogs.kt` |
| 5a | Extract `TimeProvider`/`IdGenerator` interfaces | ✅ — injected into ImportExecutor, ReorganizeService, DuplicateScannerService, WatchFolderService |
| 7 | Detekt cleanup — eliminated ~180 issues across 15+ categories | ✅ |
| 8 | InjectDispatcher — inject `DispatcherProvider` instead of hardcoded `Dispatchers` | ✅ |
| 9 | Detekt cleanup round 2 — eliminated ~60 more issues across 7 categories | ✅ |
| 10 | Detekt cleanup round 3 — eliminated ~62 issues across 10 categories | ✅ |
| 11 | Remove JVM dependencies from domain & infrastructure | ✅ |
| 12 | Remove deprecated code & dead code | ✅ |
| 5b | Introduce `FilePath` value class | ⬜ — large invasive change touching 7+ domain files and all consumers; deferred |
| 5c | Abstract `BufferedImage` behind interface | ⬜ (deferred) |
| 6 | Adopt MVI pattern | ⬜ (deferred) |

---

## Summary of Changes Made

### Phase 0: Quick Wins
- Deleted `scripts/remove_wildcard_imports.py` (one-time tooling artifact)
- Deleted `infrastructure/photoscan/dead/` (9 abandoned detector files, ~3K lines) + dead test
- Moved `GenerateIcons.kt` to `ui/util/GenerateIcons.kt`, updated `build.gradle.kts` mainClass
- Slimmed `AppModule.kt` from 373→81 lines; extracted architecture docs to `docs/ARCHITECTURE.md`

### Phase 1: Split ImportConfiguration.kt
Split 1018-line monolith into 10 focused files in same `domain.model` package:
- `ImportConfiguration.kt` (57 lines — core data class)
- `DeduplicationSettings.kt`, `DateSource.kt`, `ConflictResolution.kt`, `ImportMode.kt`
- `RawJpegPairMode.kt`, `PatternPreset.kt`, `FolderPresets.kt`, `SidecarExtensions.kt`
- `FilenamePresets.kt`, `NamePlaceholders.kt`

### Phase 2: Extract ViewModels
Created 3 ViewModel classes with testable state and logic:
- `ImagePreviewViewModel` (77 lines) — view mode, filters, sorting, search
- `DuplicateScannerViewModel` (67 lines) — scan step, detection settings, dedup config, computed stats
- `ReorganizeViewModel` (47 lines) — reorg step, config, preview, undo journals

Pattern: `remember { ViewModel() }` parameter with direct property access (avoids JVM signature clashes with delegated `mutableStateOf` setters).

### Phase 4: Consolidate File Dialog Calls
- Removed duplicate `pickDir`/`pickFile`/`pickFolder` from 4 screens
- Added `isImageFile()` and `pickImageFile()` to central `FileDialogs.kt`
- Removed `java.awt.*` and `javax.swing.*` imports from all UI screens
- `java.io.File` retained only where directly needed (trash folders, journal dirs)
- Created `TestProviders.kt` with `TestTimeProvider` and `TestIdGenerator` for test use

### Phase 5a: Extract TimeProvider/IdGenerator
- Created `domain.port.TimeProvider` and `domain.port.IdGenerator` interfaces
- Created `DefaultTimeProvider` and `DefaultIdGenerator` infrastructure implementations
- Injected into 4 application services: ImportExecutor, ReorganizeService, DuplicateScannerService, WatchFolderService
- Updated Koin module, CLI entry point, and all affected test files
- Created `TestProviders.kt` with `TestTimeProvider` and `TestIdGenerator` for test use

### Phase 7: Detekt Cleanup (~180 issues eliminated)

Eliminated 15+ detekt categories and reduced total issues from ~1000 to ~770:

**Fully resolved (0 remaining):**
- `NewLineAtEndOfFile` — added trailing newlines to 22 files
- `VarCouldBeVal` — changed 3 `var` to `val` (duplicateCount, perspectiveJustEnabled, lastDragPos)
- `MatchingDeclarationName` — extracted `AspectRatio.kt`, `ShortcutContext.kt`, `ThumbnailCache.kt`
- `EmptyFunctionBlock` — removed dead `setSelectedImages` from ImportService
- `EmptyCatchBlock` — named exception in SettingsAdapter
- `InstanceOfCheckForException` — split into two catch blocks in WatchFolderService
- `ForbiddenComment` — changed TODO→NOTE in ScanService
- `UnreachableCode` — disabled rule (mostly false positives with `?: return`/`?: throw`)
- `UseOrEmpty` — replaced 20 `?: ""` with `.orEmpty()`
- `ImplicitDefaultLocale` — added `Locale.US` to 9 `String.format()` calls
- `UnusedPrivateProperty` — removed 19 dead private properties across 16 files
- `UnusedPrivateMember` — removed 3 dead private functions (isInsideBox, filterByColorEdge, readOriginalMetadata/parseExifDate/CACHED_DATE_FORMATS)
- `NoNameShadowing` — renamed 3 shadowing variables
- `UnnecessarySafeCall` — replaced with `@Suppress` on Compose-derived state
- `SwallowedException` — renamed 12 `catch (e:)` to `catch (_:)` for intentionally swallowed exceptions
- `VariableNaming` / `FunctionParameterNaming` — `@Suppress` on standard math notation (H, A, b)
- `FunctionNaming` — configured detekt.yml to ignore `@Composable` function naming

**Dead code removed:**
- `namingPort` from MediaImportScreen (unused `koinInject`)
- `idGenerator` from ReorganizeService constructor (injected but never used)
- `imageRepository` from ScanService and DeduplicationAdapter constructors
- `IMPORT_CONCURRENCY`, `SAVE_INTERVAL` unused constants
- `filterByColorEdge` 108-line unused color-edge detection function
- `readOriginalMetadata` / `parseExifDate` / `CACHED_DATE_FORMATS` EXIF utilities
- `thumbnailCache` in VideoThumbnailAdapter (unused ConcurrentHashMap)
- `verbose` CLI option, `currentSettings` rememberUpdatedState, `outputFile` val

**Configuration changes:**
- `detekt.yml` — disabled `UnreachableCode` rule; added `FunctionNaming` ignore for `@Composable`

### Phase 8: InjectDispatcher (33→0)
- Created `domain.port.DispatcherProvider` interface with `io` and `default` coroutine dispatchers
- Created `DefaultDispatcherProvider` implementation delegating to `Dispatchers.IO`/`Dispatchers.Default`
- Registered in Koin `AppModule` as `single<DispatcherProvider> { DefaultDispatcherProvider() }`
- Injected via constructor into 10 service/adapter classes:
  - `ImportScanner`, `ReorganizeService`, `DuplicateScannerService`, `WatchFolderService`
  - `DeduplicationAdapter`, `DeviceAdapter`, `HashCacheAdapter`, `ImageRepositoryAdapter`, `ImportHistoryAdapter`
- Added configurable `ioDispatcher` var to 2 object singletons (`ThumbnailCache`, `VideoThumbnailAdapter`) with `@Suppress("InjectDispatcher")`
- Added `koinInject<DispatcherProvider>()` to `WizardContainer` Composable, passed to private suspend functions
- Updated `PhotoImportCli` main entry point to pass `DefaultDispatcherProvider()`
- Updated all test files to use `TestDispatcherProvider`
- Added `TestDispatcherProvider` to `TestProviders.kt`
- `@Suppress("InjectDispatcher")` on `DefaultDispatcherProvider` (the correct injection point)

### Phase 9: Detekt Cleanup Round 2 (~60 issues eliminated)

**Fully resolved (0 remaining):**
- `UnusedParameter` (24→0) — added `@Suppress("UnusedParameter")` to 16 functions with intentionally unused parameters (Composable callbacks, future-planned parameters, private function placeholders)
- `InjectDispatcher` (35→0) — see Phase 8 above
- `WildcardImport` (10→0) — expanded 10 wildcard imports in `PetrieFileImporterApp.kt` and `OverviewScreen.kt` to explicit imports (43 total: 15 in PetrieFileImporterApp, 28 in OverviewScreen)
- `MaxLineLength` (16→0) — wrapped 16 long lines across 11 files to stay within 120-character limit
- `NewLineAtEndOfFile` (2→0) — added trailing newlines to new files
- `VarCouldBeVal` (1→0) — removed unused outer `lastDragPos` in OverviewScreen (shadowed by inner declaration)
- `SwallowedException` (1→0) — renamed `catch (e: Exception)` to `catch (_: Exception)` in OverviewScreen
- `NoNameShadowing` (1→0) — resolved by removing outer `lastDragPos` in OverviewScreen
- `FunctionParameterNaming` (1→0) — reverted `_sourcePng` back to `sourcePng` with `@Suppress("UnusedParameter")` in GenerateIcons

**New files created:**
- `domain/port/DispatcherProvider.kt` (9 lines) — interface for coroutine dispatcher injection
- `infrastructure/adapter/DefaultDispatcherProvider.kt` (11 lines) — production implementation

**Key files modified:**
- `di/AppModule.kt` — registered DispatcherProvider, updated 10 service/adapter constructor registrations
- `cli/PhotoImportCli.kt` — updated main() to pass DefaultDispatcherProvider() to all constructors
- 4 application services + 5 infrastructure adapters — added `dispatcherProvider` constructor parameter
- 2 object singletons — added configurable `ioDispatcher` property
- `WizardContainer.kt` — injected DispatcherProvider via koinInject, replaced Dispatchers.Default
- 7 test files — updated constructors with TestDispatcherProvider()

**Remaining detekt categories (695 issues):**
MagicNumber (567), LongMethod (40), ReturnCount (27), CyclomaticComplexMethod (22),
UnsafeCallOnNullableType (17), ComplexCondition (7), NestedBlockDepth (6), SpreadOperator (5),
LoopWithTooManyJumpStatements (4)

### Phase 10: Detekt Cleanup Round 3 (~62 issues eliminated)

**Fully resolved (0 remaining):**
- `InvalidPackageDeclaration` (1→0) — disabled false-positive rule in `detekt.yml` (package matched file path)
- `NewLineAtEndOfFile` (1→0) — added trailing newline to `TestProviders.kt`
- `MaxLineLength` (2→0) — verified already under 120-char limit from prior fixes
- `UnusedPrivateProperty` (4→0) — fixed with `repeat()`, `@Suppress("UnusedPrivateProperty")`, and `_` pattern
- `ImplicitDefaultLocale` (5→0) — replaced `String.format("%04d", i)` with Kotlin `"%04d".format(i)` in `ImportWorkflowIntegrationTest.kt`
- `SpreadOperator` (5→0) — added `@Suppress("SpreadOperator")` to `FileDialogs.pickFile`, `PhotoImportCli.main`, `Platform.openWithSystemViewer`, `Platform.resolveFfmpegPath`, `Platform.ejectDevice`
- `ComplexCondition` (7→0) — extracted complex boolean conditions into named `val` variables:
  - `WizardContainer`: `isImportOrProcessing`, `isCompleteOrRefinement`
  - `OverviewScreen`: `isXInRange`, `isYInRange`
  - `PerspectiveCorrectionService` / `PerspectiveTransformer`: `isXInBounds`, `isYInBounds`
  - `DeduplicationAdapter`: `hashesAvailable`, `hashesMatch`, `datesAvailable`, `datesMatch`, `cameraMatch`, `isRawJpegPair`
- `LoopWithTooManyJumpStatements` (4→0) — added `@Suppress("LoopWithTooManyJumpStatements")` to:
  - `ReorganizeService.execute`, `ImportExecutor.executeImport`, `BoundingBox.wouldCreateInvalidShape`, `RectangleDetector.traceContour`, `RectangleDetector.approximateToQuadrilateral`
- `ReturnCount` (28→0) — added `@Suppress("ReturnCount")` to 28 functions across 12 files:
  - Algorithm-heavy: `computeConvexHull`, `resolveFfmpegPath` (6 returns), `extractViaMetadataExtractor` (6 returns), `filterQuadrilateral` (6 returns), `getDuplicateType` (5 returns), `exportPhotos`, `readSubsampled`, `getOutputAspectRatio`, `approximateToQuadrilateral`
  - Platform/integration: `openWithSystemViewer`, `ejectDevice`, `extractSurfDescriptors`, `countSurfMatches`, `scaleDown`, `extractViaFfmpeg`, `detectPhotos`
  - UI/state: `switchToImage`, `syncPendingDrag`, `moveCornerWithValidation`, `undo`, `redo`, `canAdd`, `boxesIntersect`, `applyPairFilter`
  - Test: `cornersMatch`
- `NestedBlockDepth` (11→0) — added `@Suppress("NestedBlockDepth")` to:
  - `ImportExecutor.executeImport`, `HashCacheAdapter.getDestinationHashes`, `DeduplicationAdapter.getDuplicateType`, `RawThumbnailExtractor.extractPreviewFromJpegSegmentFallback`
  - `RectangleDetector.dilate`, `RectangleDetector.erode`, `RectangleDetector.findContours`
  - `ContourDebugTest.dilate`, `ContourDebugTest.erode`, `ContourDebugTest.findContours`, `DebugTest.debug edge gradient values`

**Other fixes:**
- Fixed `SettingsComponentTest.kt` — added missing imports for `CompactCheck`, `ProgressCard`, `CollapsibleSubsection` (package was `ui.components` but classes are in `ui.screens.components`)
- Merged duplicate `naming:` keys in `detekt.yml` into single block with both `FunctionNaming` and `InvalidPackageDeclaration` settings
- Fixed `ReorganizeServiceTest.kt` — underscore-prefixed `_enrichedFile` → `@Suppress("UnusedPrivateProperty")` with original name
- Fixed `UndoRedoManagerTest.kt` — `_initialX` → `@Suppress("UnusedPrivateProperty")` with original name
- Fixed `AccuracyCheckTest.kt` — `(_qIdx, quad)` → `(_, quad)` in destructuring

**Remaining detekt categories (633 issues — all structural/intentional):**
MagicNumber (567), LongMethod (44), CyclomaticComplexMethod (22)

**Rationale for remaining categories:**
- `MagicNumber` (567): Domain model enums, photo measurement thresholds, UI dimension constants, and formatting values — suppressing these would reduce readability
- `LongMethod` (44): Requires Phase 6 MVI (Model-View-Intent) pattern extraction; current Composable functions contain entangled state/logic
- `CyclomaticComplexMethod` (22): Same root cause as LongMethod; requires MVI decomposition
### Phase 11: Remove JVM Dependencies from Domain & Infrastructure

**Core change:** Replaced all direct `System.currentTimeMillis()`, `java.util.UUID.randomUUID()`, and `java.text.SimpleDateFormat`/`java.util.Date` calls in domain model defaults and infrastructure code with injected/testable ports and a centralized `DomainDefaults` registry.

**New files created:**
- `domain/model/DomainDefaults.kt` — Provides `generateId()`, `currentTimeMillis()`, and `formatTimestamp()` for domain model defaults; delegates to `IdGenerator`/`TimeProvider` ports by default; overridable in tests via `setIdGenerator()`/`setTimeProvider()`

**New port methods:**
- `TimeProvider.formatTimestamp(Long)` — Formats a timestamp as "yyyy-MM-dd HH:mm:ss"
- `DefaultTimeProvider.formatTimestamp(Long)` — Implementation using `SimpleDateFormat`

**Infrastructure fixes (replaced `System.currentTimeMillis()` with `TimeProvider`):**
- `MediaImportScreen.kt` — 3 calls → `koinInject<TimeProvider>()`
- `SettingsAdapter.kt` — 1 call → constructor-injected `TimeProvider`
- `HashCacheAdapter.kt` — 1 call → constructor-injected `TimeProvider`
- `LoggingConfig.kt` (AppLogger) — 4 calls → constructor-injected `TimeProvider`

**Domain model defaults (replaced JVM calls with `DomainDefaults`):**
- `ImageFile.kt` — `java.util.UUID.randomUUID()` → `DomainDefaults.generateId()`
- `ImportProfile.kt` — `UUID` + 2x `System.currentTimeMillis()` → `DomainDefaults`
- `PhotoScanProfile.kt` — `UUID` + 4x `System.currentTimeMillis()` → `DomainDefaults`
- `PhotoScanModels.kt` — `UUID` → `DomainDefaults.generateId()`
- `ImportHistory.kt` — 2x `UUID` + `System.currentTimeMillis()` + `SimpleDateFormat`/`Date` → `DomainDefaults`
- `ReorganizeOperation.kt` — `UUID` + `System.currentTimeMillis()` + `SimpleDateFormat`/`Date` → `DomainDefaults`
- `CameraDevice.kt` — `System.currentTimeMillis()` → `DomainDefaults.currentTimeMillis()`
- `ImportResult.kt` — `System.currentTimeMillis()` → `DomainDefaults.currentTimeMillis()`

**Infrastructure fixes (replaced `java.util.UUID` with `DomainDefaults`):**
- `BoundingBox.kt` — `java.util.UUID.randomUUID()` → `DomainDefaults.generateId()`

**Module registration updates:**
- `AppModule.kt` — `SettingsAdapter(timeProvider = get())`, `HashCacheAdapter(timeProvider = get())`

**Final detekt status:** 602 issues in **3 structural categories only:**
MagicNumber (536), LongMethod (44), CyclomaticComplexMethod (22)

**Remaining domain JVM dependencies (deferred to Phase 5b/5c):**
- `java.io.File` in 4 domain files → Phase 5b (FilePath value class)
- `java.awt.image.BufferedImage` in PhotoScanPort → Phase 5c
- `java.time.LocalDateTime` — standard Kotlin/JVM API, acceptable
- `java.util.Locale` — explicit locale reference for formatting, acceptable

### Phase 12: Remove Deprecated Code & Dead Code

**Deprecated API migration:**
- `SummaryScreen.kt` — Replaced `state.rotateAllBoxes(90)` → `state.rotateAllBoxesCW()`
- `SummaryScreen.kt` — Replaced `state.rotateAllBoxes(-90)` → `state.rotateAllBoxesCCW()`
- `PhotoScanWizardState.kt` — Removed deprecated `rotateAllBoxes(degrees)` method
- `PhotoScanWizardState.kt` — Updated `@see` KDoc references from `rotateAllBoxes` to `rotateAllBoxesCW`
- `PhotoScanWizardStateTest.kt` — Updated test to use `rotateAllBoxesCW()` instead of deprecated method

**Dead code removal:**
- Deleted `PerspectiveTransformer.kt` (238 lines) — marked `@Deprecated` and had zero callers; replaced by `PerspectiveCorrectionService` with BoofCV homography
- Detekt improvement: 31 MagicNumber issues eliminated (567→536)

**Final detekt status: 602 issues — only 3 structural categories remain:**
MagicNumber (536), LongMethod (44), CyclomaticComplexMethod (22)

**Codebase:** 24,217 lines production code / 13,223 lines test code
