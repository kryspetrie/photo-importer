# Petrie File Importer — Session Progress Notes

**Session ID**: `20260625_1`  
**Date**: 2026-06-26  
**Git HEAD**: `9981b49 refactor: add PhotoCorner.toPoint()/fromPoint() and Point.toPhotoCorner() conversions, simplify boundary code`  
**Working directory**: `/Users/krys.petrie/dev/petrie-file-importer`

---

## How to Resume

Use the Goose CLI command:
```
goose session resume 20260625_1
```

---

## Project Overview

Kotlin/Compose Desktop photo scanning application using hexagonal architecture (Ports & Adapters). Domain layer has no framework dependencies. UI depends on Application and Domain. Infrastructure implements Domain ports.

---

## Completed This Session

### 1. Phase 3: Remove PhotoConfiguration typealias (commit `8a04b16`)

Deleted `ui/wizard/state/PhotoConfiguration.kt` which was a `typealias PhotoConfiguration = PhotoScanConfiguration`. Replaced all 24 files (15 production, 9 test) that imported from the old path with direct imports from `domain.model.PhotoScanConfiguration`. All `PhotoConfiguration` type references now use `PhotoScanConfiguration` directly.

### 2. Phase 8a: Remove geometry typealiases (commit `28b2c5c`)

Deleted `ui/wizard/state/BoundingBox.kt` and `ui/wizard/state/BoundingBoxList.kt` which re-exported `Point`, `BoundingBox`, `BoundingBoxCorners`, `Corner`, and `BoundingBoxList` from `domain.model.geometry`. All 40+ files updated to import directly from the domain module.

### 3. Phase 4 partial: Use MetadataEditState in MetadataEditorPanel (commit `e4f61f9`)

Replaced 18 separate `var buffered*` state variables + 2 lambda helpers in `MetadataEditorPanel.kt` with a single `MetadataEditState` instance. Added convenience methods: `MetadataEditState.toRecentMetadataSet()`, `PhotoConfigurationState.applyMetadataToSelected(MetadataEditState)`. Also fixed pre-existing architecture violation in `MapTileRenderTestApp` (was importing `DefaultDispatcherProvider` from infrastructure directly).

### 4. Phase 10 step 1: Remove dead coordinate types (commit `3aa3f73`)

Deleted entire `PhotoModels.kt` (257 lines of dead code): `PercentPoint`, `PercentBoundingBox`, `PercentBounds`, `CornerType`, `PixelPoint` typealias, `PhotoMetadata`, `OriginalDatePrecision`. These types were defined but never referenced outside their own file. Also removed `PhotoCorner.toPercent()` (unused method) and `CornerTypeTest` (tested dead code).

Active coordinate types that remain:
- `geometry.Point` (Double) — wizard state bounding boxes
- `PhotoCorner` (Float) — YOLO pipeline corner coordinates
- `geometry.Corner` — active corner enumeration

### 5. Phase 10 step 2: Add coordinate conversion methods (commit `9981b49`)

Added `PhotoCorner.toPoint()` / `PhotoCorner.fromPoint()` and `Point.toPhotoCorner()` conversion methods for Float↔Double boundary crossing. Simplified boundary code in `WizardExportOrchestrator`, `WizardImportHandler`, and `SharedImageUtils` to use the new methods instead of manual `x.toFloat()` / `x.toDouble()` calls.

---

## Refactoring Plan Status

| Phase | Description | Status |
|-------|-------------|--------|
| 1 | FaceRegion, OverrideState, AspectRatio unification | ✅ Done |
| 2 | DomainDefaults, ScanService, ExifValueResolver, GeometryUtils, pickKeeper | ✅ Done |
| 3 | PhotoConfiguration ↔ PhotoScanConfiguration unification | ✅ Done |
| 4 | Extract shared MetadataEditorPane | ⚠️ Partial (MetadataEditState integrated, shared pane not extracted) |
| 5 | Decompose PhotoScanExportService (export/ subpackage) | ✅ Done |
| 6 | Introduce DomainImage | Not started |
| 7 | Decompose PhotoScanWizardState (God Object) | ✅ Done |
| 8a | Move BoundingBox, BoundingBoxList typealiases to domain | ✅ Done |
| 8b | Split ImportProfile.kt | ✅ Done |
| 9 | Create FileSystemPort | Not started |
| 10 | Coordinate unification | ✅ Done (dead code removed, conversion methods added, types kept separate) |

### Next Targets

1. **Phase 4** (medium effort, medium risk): Extract shared `MetadataEditorPane` composable to deduplicate ~400 lines of metadata field rendering between `MetadataScreen` and `QuickEditScreen`. `MetadataEditState.kt` (164 lines) already exists and is now used by `MetadataEditorPanel`.

2. **Phase 6** (large effort, high risk): Introduce `DomainImage` wrapper to remove `BufferedImage` from domain ports.

3. **Phase 9** (medium effort, medium risk): Create `FileSystemPort` to abstract `java.io.File` operations.

---

## Architecture Notes

### Key File Locations
- **Domain models**: `src/main/kotlin/org/kryspetrie/fileimport/domain/model/`
- **Domain geometry**: `src/main/kotlin/org/kryspetrie/fileimport/domain/model/geometry/`
- **Domain ports**: `src/main/kotlin/org/kryspetrie/fileimport/domain/port/`
- **Wizard state**: `src/main/kotlin/org/kryspetrie/fileimport/ui/wizard/state/`
- **UI screens**: `src/main/kotlin/org/kryspetrie/fileimport/ui/screens/wizard/`
- **Edit screen sub-files**: `src/main/kotlin/org/kryspetrie/fileimport/ui/screens/wizard/edit/`
- **Export service**: `src/main/kotlin/org/kryspetrie/fileimport/application/PhotoScanExportService.kt` + `application/export/`

### Coordinate Types (after Phase 10)
- **`geometry.Point`** (Double) — wizard bounding boxes, UI state. Has `toPhotoCorner()` conversion.
- **`PhotoCorner`** (Float) — YOLO pipeline corners, export coordinates. Has `toPoint()` and `fromPoint()` conversions.
- **`geometry.Corner`** — corner enumeration (TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT).
- **Dead code removed**: `PercentPoint`, `PercentBoundingBox`, `PercentBounds`, `CornerType`, `PixelPoint`, `PhotoMetadata`, `OriginalDatePrecision` (entire `PhotoModels.kt` file deleted).

### Testing
- Run tests: `cd /Users/krys.petrie/dev/petrie-file-importer && ./gradlew test`
- Build check: `cd /Users/krys.petrie/dev/petrie-file-importer && ./gradlew compileKotlin compileTestKotlin`
- Key test files are in `src/test/kotlin/org/kryspetrie/fileimport/`

---

## Guardrails
- Read files before editing them
- Run tests after changes
- Commit at each checkpoint
- Each extraction should be a separate commit