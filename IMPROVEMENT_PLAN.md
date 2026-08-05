# Improvement Plan

_Audit date: 2026-07-21. Last metadata-integration update: 2026-07-24._

This document tracks known issues and proposed work, ordered by priority. Line references are accurate as of the audit date and may drift as the code changes. Items marked **✓ verified** were confirmed against the code during the audit.

## Recently completed (metadata integration)

- [x] **Replaced Commons Imaging writers with ExifTool** — `MetadataWritingService` now delegates to the `photo-metadata-editor` library (`MetadataEditorPort`) via `PhotoScanMetadataMapper`. Legacy `ExifMetadataWriter`, `IptcMetadataWriter`, and `XmpMetadataWriter` were removed.
- [x] **Metadata-only save avoids pixel re-encoding** — `MetadataEditService` uses `MetadataWritingService.writeMetadataOnly()` when rotation/back-image/perspective are unchanged.
- [x] **OVERWRITE aborts when undo backup fails** — `MetadataEditService.saveFile` returns `null` if `createBackup` fails in OVERWRITE mode.
- [x] **Write failures propagate** — `MetadataWritingService` throws `MetadataWriteException` on ExifTool failures (no silent stderr swallowing).
- [x] **Export/metadata tests** — `PhotoScanMetadataMapperTest`, `PhotoScanExportServiceTest` (tri-state + readback), `SampleImageMetadataIntegrationTest`, `RawMetadataWriteIntegrationTest`, `XmpFaceRegionExportTest`.

## Priority legend

- **P0** — Data loss or corruption. Fix first.
- **P1** — Incorrect behavior / hangs / silently-wrong output.
- **P2** — Primary goal: metadata-editor consistency & usability.
- **P3** — Broader UI/UX, test coverage, docs, structure.

---

## P0 — Data loss / corruption

- [x] **Metadata OVERWRITE proceeds when backup creation fails** — fixed in `MetadataEditService.saveFile` (aborts when backup is null).

- [x] **Metadata-only "save" re-encodes the whole JPEG at quality 0.95** — fixed: metadata-only path calls `writeMetadataOnly()`. Export/crop paths still re-encode pixels intentionally via `writeImageWithMetadata()`.

- [x] **Unreadable files are grouped as EXACT_HASH duplicates and can be deleted** — fixed: `findDuplicates` excludes empty hashes; `getDuplicateType` requires non-empty hashes (2026-07-28).

- [x] **Copy bounded by stale scan-time file size → truncated copy** — fixed: copy loops to EOF with re-stat; `verifyCopy` uses fresh source hash (2026-07-28).

## P1 — Incorrect behavior / hangs / silently-wrong output

- [x] **Infinite loop in `resolveConflict` when the pattern has no `{counter}`** — fixed in `NamingAdapter.resolveConflict`: appends `_N` before extension when pattern lacks `{counter}` (2026-07-28).

- [x] **MWG face-region XMP on files with pre-existing XMP** — verified: export with `copyOriginalExif` preserves prior XMP-dc tags while writing MWG RegionInfo (`XmpFaceRegionExportTest`, 2026-07-29).

- [x] **`ReorganizeService` `renameOnly` is a dead no-op** — fixed: `renameOnly` keeps each file in its current directory (2026-07-28).

- [x] **Reorganize empty-dir cleanup deletes unrelated empty folders** — fixed: only removes source parents of moved files (2026-07-28).

- [x] **RAW+JPEG pairing matches base filename only** — fixed: `detectRawJpegPairs` requires same parent directory (2026-07-28).

- [x] **Import `counter` only advances on full success** — fixed: counter advances for skipped/failed/hash-mismatch/success paths (2026-07-28).

- [x] **GPS seconds carry-over is dead code** — fixed: seconds≥60″ and minutes≥60′ cascade correctly (2026-07-28).

- [x] **`copyOriginalExif` not implemented in ExifTool export path** — fixed: `MetadataWritingService` merges transferable source tags via `SourceExifBaselineMerger` when `copyOriginalExif=true` (2026-07-28).

- [x] **Import duplicate count hard-coded to 0** — fixed: ViewModel tracks already-transferred + visual-dupe counts; `ImportExecutor` accepts `detectedDuplicateCount` (2026-07-28).

## P2 — Metadata-editor consistency & usability (primary goal)

_Two intentional pages: standalone Bulk Metadata Editor (`ui/screens/metadataeditor/`) and the Photo-Scan wizard editor (`ui/screens/wizard/edit/`, `wizard/metadata/`). These fixes address inconsistency **within** those pages and reduce clicks._

- [x] **Unify override-checkbox semantics (biggest usability issue)** — both description and camera fields use `OverrideUiSemantics` (`!= NULL_OUT` / KEEP_SOURCE↔NULL_OUT) in the wizard panel (2026-07-29).

- [x] **Standalone screen re-implements rotation controls** — standalone preview pane consumes shared `RotationSection` (2026-07-29).

- [x] **Panel headers inconsistent between the two pages** — both panels use shared `MetadataEditorPanelHeader` (title + Clear; Apply + hint in batch mode) (2026-07-29).

- [x] **Source-EXIF hints missing on camera fields in the bulk editor** — `CameraSection` receives `sourceExif` in the standalone panel (2026-07-29).

- [x] **Off-theme color for "Clear All" tags** — uses `colorScheme.error` (2026-07-29).

- [x] **Dead conditional in keyword field** — suggestion dropdown gated on non-empty filtered suggestions (2026-07-29).

## P3 — Broader UI/UX

- [x] **Design tokens adopted on high-traffic Dup/Reorg surfaces** — `DefaultSpacing` on Duplicate Scanner / Reorganize screens & action bars; `DefaultColors` theme-aware (Material scheme) + used for status/KEEP chip accents. Broader `.dp` lint still optional.
- [x] **Keyboard nav on Duplicate Scanner / Reorganize** — Esc cancel during busy / leave results or preview; Enter resolve/apply/confirm undo; setup Enter preserved (`SetupScreenKeyboard` helpers, 2026-07-29).
- [x] **Shortcut help discoverability** — global `F1` / `Ctrl+/` via `AppKeyboardShortcuts`; wizard hint string mentions F1. Menu entry still optional.
- [x] **Loading spinner themed** — `CircularSpinner` defaults to `MaterialTheme.colorScheme.primary` + `DefaultSpacing` (no hardcoded Google blue). Consolidating other busy widgets still optional.
- [x] **Scan-screen destructive actions** — legacy `ScanScreen` / `ScanPhotoList` removed; Photo Scan wizard owns that flow with confirmations where needed.
- [x] **Misc quick UI fixes** — `ReorganizeScreen` title uses `headlineSmall`; dead "Keep" chip removed; error icons have content descriptions + `DefaultColors.error` (not color alone).

## P3 — Test coverage

_Highest-value remaining gaps:_

- [x] `MetadataEditService.saveFile` — OVERWRITE backup abort/success, SAVE_NEW parent fallback + outDir (`MetadataEditServiceTest`, 2026-07-29).
- [x] `MetadataEditUndoService.redo` + undo-with-missing-backup (`MetadataEditUndoServiceTest`, 2026-07-29).
- [x] **`ReorganizeService.undo`** — MOVE round-trip, COPY undo (deletes copy), missing-journal error (`ReorganizeServiceTest.Undo`, 2026-07-29).
- [x] `FileOperationExecutor` cross-filesystem `renameTo`-fallback (`FileOperationExecutorTest`, 2026-07-29).
- [x] `ImportExecutor` hash-mismatch / sidecar / delete-after-import (`ImportExecutorTest`, 2026-07-29).
- [x] `MetadataEditorViewModel.applyBatchRotationCorrection` — detection→apply VM path; replaced duplicated `nearestCorrectionDeg` tests (2026-07-29).
- [x] **`copyOriginalExif` baseline behavior** — covered by `SourceExifBaselineMergerTest` + export path in `MetadataWritingService` (2026-07-28).
- [x] **Scratch harnesses excluded from default suite** — photoscan `*Debug*` / `AccuracyCheckTest` tagged `@Tag("scratch")`; `tasks.test` excludes `scratch` (2026-07-29).

_Integration tests (require bundled ExifTool + sibling `photo-metadata-editor` repo):_

```bash
./gradlew downloadExifTool integrationTest
```

Sample RAW/JPEG fixtures come from `metadata-test-fixtures` on the test classpath (not copied into this repo).

## P3 — Docs & structure

- [x] **`ADVERSARIAL_ANALYSIS.md` marked stale** — status header added (2026-07-29); treat as historical. Full prune/archive still optional.
- [x] **Docs accuracy** — README / DEVELOPER_GUIDE / QUICK_REFERENCE no longer claim hot reload or in-repo `@Preview`; ARCHITECTURE `PhotoScanWizardState` line count + `PathsPort` boundary updated; CONTRIBUTING AppPaths exception removed (2026-07-29).
- [x] **`javax.inject` dependency removed** — no longer in `build.gradle.kts`; aligns with konsist JSR-330 forbid.
- [x] **Dev-only harnesses** — `src/dev` Kotlin source set wired; `runMapTileTest` / `runLocationPickerTest` use `dev` classpath (2026-07-29).
- [x] **Structural cleanups** — shared metadata types moved to `ui/screens/shared/metadata/`; oversized UI files split into focused screen, dialog, rendering, cache, and action files (`EditScreen`, `FaceSelectorOverlay`, `MapTileRenderer`, `BackImagePickerDialog`, `SummaryScreen`, `ImagePreviewScreen`, `MetadataEditorFileBrowserPanel`, `MetadataEditorViewModel`, and `EditSections`). `MetadataOverrides` extraction remains an optional domain follow-up.
- [x] **Post-split cleanup** — dead standalone-editor back-image API removed (no UI entry point existed); hard-coded English strings in the wizard/summary/back-image/location-picker UI routed through `StringKey` (4 new keys synced to all locales); `SidebarAction` no longer picks its icon by comparing an English label; `UiTextLocalizationInspector` now also flags positionally-passed `Icon(…, "text")` descriptions so this gap class fails the build; `PRODUCTION_READINESS.md` / `LOCALIZATION.md` marked as snapshots and `DEVELOPER_GUIDE.md` Photo Scan section rewritten around `WizardStep` / `WizardContainer` (2026-07-29).

---

## Suggested first milestone

P0–P3 plan items are largely complete as of 2026-07-29. Optional follow-ups:

1. Broader `.dp` → `DefaultSpacing` adoption beyond Dup/Reorg.
2. Extract `MetadataOverrides` from `PhotoScanConfiguration`.
3. Full prune/archive of `ADVERSARIAL_ANALYSIS.md`.
