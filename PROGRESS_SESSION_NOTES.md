# Petrie File Importer — Session Progress Notes

**Session ID**: `20260625_1`  
**Date**: 2026-06-25  
**Git HEAD**: `28b2c5c refactor: remove geometry typealiases from ui/wizard/state, use domain.model.geometry directly`  
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

Deleted `ui/wizard/state/BoundingBox.kt` and `ui/wizard/state/BoundingBoxList.kt` which re-exported `Point`, `BoundingBox`, `BoundingBoxCorners`, `Corner`, and `BoundingBoxList` from `domain.model.geometry`. All 40+ files updated to import directly from the domain module. Same-package files in `ui/wizard/state/` and their tests now have explicit `domain.model.geometry` imports instead of relying on implicit same-package access to typealiases.

---

## Refactoring Plan Status

| Phase | Description | Status |
|-------|-------------|--------|
| 1 | FaceRegion, OverrideState, AspectRatio unification | ✅ Done (prior sessions) |
| 2 | DomainDefaults, ScanService, ExifValueResolver, GeometryUtils, pickKeeper | ✅ Done (prior sessions) |
| 3 | PhotoConfiguration ↔ PhotoScanConfiguration unification | ✅ Done (this session — typealias removed) |
| 4 | Extract shared MetadataEditorPane from EditScreen/MetadataEditorPanel | ⚠️ Partial (MetadataEditState exists, shared pane not extracted yet) |
| 5 | Decompose PhotoScanExportService (export/ subpackage) | ✅ Done (prior sessions — 1322→281 lines) |
| 6 | Introduce DomainImage | Not started |
| 7 | Decompose PhotoScanWizardState (God Object) | ✅ Done (prior sessions — 1681→470 lines) |
| 8a | Move BoundingBox, BoundingBoxList typealiases to domain | ✅ Done (this session — typealiases removed) |
| 8b | Split ImportProfile.kt | ✅ Done (prior sessions — now 78 lines) |
| 9 | Create FileSystemPort | Not started |
| 10 | Coordinate system unification (PercentPoint/PhotoCorner/Point) | Not started |

### Next Targets

1. **Phase 4** (medium effort, medium risk): Extract shared `MetadataEditorPane` composable to deduplicate ~400 lines of metadata field rendering between `MetadataScreen` and `QuickEditScreen`. `MetadataEditState.kt` (164 lines) already exists.

2. **Phase 6** (large effort, high risk): Introduce `DomainImage` wrapper to remove `BufferedImage` from domain ports.

3. **Phase 9** (medium effort, medium risk): Create `FileSystemPort` to abstract `java.io.File` operations.

4. **Phase 10** (medium effort, medium risk): Consolidate `PercentPoint`/`PhotoCorner`/`Point` into a single canonical `Point2D`.

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

### Key Patterns
- `PhotoScanWizardState` (470 lines) is the central state holder, composed of sub-states: `navigation`, `batch`, `importSettings`, `exportSettings`, `boxes`, `faceRegions`, `configs`, `zoom`
- `WizardContainer` is the orchestrator that wires state to screens
- Top-level functions preferred over class methods for extracted orchestration code
- `PhotoScanConfiguration` is now the single canonical type (no more `PhotoConfiguration` alias)
- Geometry types (`Point`, `BoundingBox`, etc.) now live only in `domain.model.geometry`
- `ExportResult` sealed class (Success/Failure) is the domain type for per-photo export results

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