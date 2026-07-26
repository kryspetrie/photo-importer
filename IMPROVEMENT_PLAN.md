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

- [ ] **Unreadable files are grouped as EXACT_HASH duplicates and can be deleted** — `infrastructure/adapter/ImageRepositoryAdapter.kt:277`, `infrastructure/adapter/DeduplicationAdapter.kt:23-38`
  `calculateFileHash` returns `""` (not null) on any exception. `findDuplicates` filters `hash != null` (empty string passes) then `groupBy { hash!! }`, collapsing all hash-failures into one duplicate group with `hashMatch = true`. "Resolve all" then deletes/moves all but one. **Fix:** treat `""`/failed hashes as non-hashable and exclude them from grouping.

- [ ] **Copy bounded by stale scan-time file size → truncated copy** — `infrastructure/adapter/ImageRepositoryAdapter.kt:292-310`
  `totalBytes = source.fileSize` (captured at scan). If the file grew (e.g. a watch-folder file still being written), the loop stops early and returns `true`. With `verifyAfterCopy` off and `deleteAfterImport` on, the full source is then deleted. With verify on, `verifyCopy` compares the stale `source.hash` and deletes the good copy. **Fix:** copy to EOF (don't bound by cached size); re-stat before copy; verify against a freshly computed source hash.

## P1 — Incorrect behavior / hangs / silently-wrong output

- [ ] **Infinite loop in `resolveConflict` when the pattern has no `{counter}`** — `infrastructure/adapter/NamingAdapter.kt:137-140`
  `do { path = generateFilePath(..., counter); counter++ } while (File(path).exists())`. `generateFileName` only varies with `counter` if the pattern contains `{counter}`; otherwise the path is identical each iteration and the loop never terminates. **Fix:** if the pattern lacks `{counter}`, append a disambiguator (or force-append counter) before looping.

- [ ] **MWG face-region XMP on files with pre-existing XMP** — verify merge behavior when ExifTool rewrites XMP on photos that already had non-MWG XMP. `PhotoScanMetadataMapper` emits `XMP-mwg-rs:RegionInfo` structs; add regression tests for merge/passthrough on sample files with existing XMP.

- [ ] **`ReorganizeService` `renameOnly` is a dead no-op** — `application/ReorganizeService.kt:111` ✓ verified
  `val destRoot = if (renameOnly) folderPath else folderPath` — both branches identical; `generateFolderPath` runs unconditionally, so "rename only" still relocates files into subfolders. **Fix:** when `renameOnly`, keep each file in its current directory and only change the filename.

- [ ] **Reorganize empty-dir cleanup deletes unrelated empty folders** — `application/ReorganizeService.kt:230-233`, `application/FileOperationExecutor.kt:202-213`
  Cleanup root is derived as the grandparent of the first file, then every empty dir under it is deleted regardless of involvement in the move. Can delete a user's intentionally-empty folders. **Fix:** only remove directories that were actually emptied by this operation.

- [ ] **RAW+JPEG pairing matches base filename only** — `application/ImportService.kt:164-204`
  Pairs on `nameWithoutExtension` (case-insensitive) with no timestamp/folder check, contradicting the KDoc. Same-named files in different subfolders mispair; under `JPEG_ONLY`, a RAW with no real JPEG counterpart is silently dropped. **Fix:** require same directory (and/or timestamp) for a pair.

- [ ] **Import `counter` only advances on full success** — `application/ImportExecutor.kt:305` (increment), `continue` at 116/148/179/210
  Skipped/failed/hash-mismatch files `continue` without incrementing, so `{counter}` collides and sequence numbers gap. **Fix:** advance the counter for every attempted file (or decouple sequence number from the filename counter).

- [ ] **GPS seconds carry-over is dead code** — `domain/model/ExifValueResolver.kt:131` ✓ verified
  `if (secondsRounded >= 6000000)` has an extra zero (max value is 600000), so the branch never fires; the `minutes >= 60` branch (`:139`) is likewise unreachable. A coordinate rounding to 60.0000″ is emitted as `60″` (invalid DMS). **Fix:** compare against `600000`; add a rounding-boundary test.

- [ ] **`copyOriginalExif` not implemented in ExifTool export path** — `PhotoScanMetadataMapper` / `MetadataWritingService` do not yet copy source EXIF baseline when `PhotoScanConfiguration.copyOriginalExif` is true. Wizard/export behavior may differ from the old Commons Imaging path.

- [ ] **Import duplicate count hard-coded to 0** — `application/ImportExecutor.kt:61` (acknowledged TODO)
  `ImportResult`/history always report 0 duplicates. Reporting inaccuracy, not data loss.

## P2 — Metadata-editor consistency & usability (primary goal)

_Two intentional pages: standalone Bulk Metadata Editor (`ui/screens/metadataeditor/`) and the Photo-Scan wizard editor (`ui/screens/wizard/edit/`, `wizard/metadata/`). These fixes address inconsistency **within** those pages and reduce clicks._

- [ ] **Unify override-checkbox semantics (biggest usability issue)** — `ui/screens/metadataeditor/MetadataEditorPanel.kt`; `ui/screens/wizard/edit/MetadataEditorPanel.kt` (`!= NULL_OUT` vs `== KEEP_SOURCE` on camera fields)
  `overrideToggle` (description/keywords/date/year/GPS) is checked when `state != NULL_OUT`, so the default (null) renders checked/editable. Camera-field toggles use different semantics, so those fields open disabled and require an extra click before you can type. **Fix:** one consistent rule across all fields.

- [ ] **Standalone screen re-implements rotation controls** — `ui/screens/metadataeditor/MetadataEditorScreen.kt:654-724` vs shared `ui/screens/wizard/edit/EditSections.kt:80` (`RotationSection`)
  Different label ("Rotate:" vs "Rotation"), padding (12/4 vs 16/8), and degree display (`RotationBadge` vs plain `Text`). **Fix:** have the standalone screen consume the shared `RotationSection`.

- [ ] **Panel headers inconsistent between the two pages**
  - Single-edit: standalone shows filename **+ Clear button** (`metadataeditor/MetadataEditorPanel.kt:118-136`); wizard shows `"Photo N"` **without Clear** (`wizard/edit/MetadataEditorPanel.kt:140-146`).
  - Multi-edit: standalone header has **Clear + Apply**; wizard has **Apply only** (`:106-111` vs `:125-133`).
  **Fix:** standardize the header (title/"N selected" + consistent Clear/Apply placement) across both panels.

- [ ] **Source-EXIF hints missing on camera fields in the bulk editor** — `ui/screens/metadataeditor/MetadataEditorPanel.kt:354`
  `CameraSection` is passed `sourceExif = null`, so the user can't see the original camera values being overridden (intentional for scans, wrong for real photos in the bulk editor). **Fix:** thread `sourceExif` into the standalone `CameraSection`.

- [ ] **Off-theme color for "Clear All" tags** — `ui/screens/wizard/edit/EditSections.kt:790`
  Uses `Color(0xFFFF6666)` while every other destructive affordance uses `MaterialTheme.colorScheme.error`. Won't adapt to dark mode. **Fix:** use `colorScheme.error`.

- [ ] **Dead conditional in keyword field** — `ui/screens/wizard/edit/EditSections.kt:205`
  `if (availableSuggestions.isNotEmpty() || true)` is always true. **Fix:** remove the condition.

## P3 — Broader UI/UX

- [ ] **Design tokens exist but are never used** — `ui/theme/DefaultSpacing.kt`, `ui/theme/DefaultColors.kt`
  Zero external references; every screen hardcodes dp/colors. Root cause of most visual drift. **Fix:** adopt the tokens (incrementally) and lint against raw `.dp` literals for spacing.
- [ ] **Keyboard nav only in the wizard + metadata editor** — `MediaImportScreen`, `DuplicateScannerScreen`, `ReorganizeScreen`, `ScanScreen`, `ImagePreviewScreen` have no key handling. No Enter-to-submit on primary CTAs, no Esc-to-cancel, no arrow-key triage in the file grid, no `Ctrl+1..5` tab switching.
- [ ] **Shortcut help dialog is undiscoverable** — `wizard/KeyboardShortcuts.kt:346` is only reachable from two wizard screens. **Fix:** global `?`/F1 binding + a menu entry.
- [ ] **Off-brand loading spinner** — `ui/components/CircularSpinner.kt:31` defaults to `Color(0xFF1A73E8)` (Google blue), used everywhere, doesn't adapt to dark mode. Plus three different "busy" widgets across the app. **Fix:** theme the spinner; consolidate on one.
- [ ] **Destructive actions inconsistently guarded** — scan-screen "Re-detect" (`ScanScreen.kt:165`) just empties the list (doesn't re-detect) and discards corner edits with no confirm/undo; photo delete (`scan/ScanPhotoList.kt:48`) is immediate — while other screens do confirm.
- [ ] **Misc:** `ReorganizeScreen` title uses `labelLarge` where peers use `headlineSmall`; action-bar alignment and mid-job Cancel presence differ across screens; dead "Keep" `AssistChip` with `onClick={}` (`duplicatescanner/DuplicateGroupCard.kt:80`); state-encoding icons pass `contentDescription=null`; errors signaled by color alone.

## P3 — Test coverage

_Highest-value remaining gaps:_

- [ ] `MetadataEditService.saveFile` — OVERWRITE backup vs SAVE_NEW, output-dir fallback, journal-entry correctness.
- [ ] `MetadataEditUndoService.redo` (backup-as-EXIF-source) and undo-with-missing-backup.
- [ ] **`ReorganizeService.undo` — currently zero tests** (MOVE round-trip, COPY undo, partial-failure `undone` flag).
- [ ] `FileOperationExecutor` cross-filesystem `renameTo`-fallback (only fails across volumes → green in CI, broken in prod).
- [ ] `ImportExecutor` hash-mismatch / sidecar / delete-after-import paths.
- [ ] `MetadataEditorViewModel.applyBatchRotationCorrection` (current test re-implements the logic and never calls the VM) + multi-edit/location state transitions.
- [ ] **`copyOriginalExif` baseline behavior** once implemented in the ExifTool export path.
- [ ] **Remove scratch harnesses from the suite:** `*Debug*`, `ContourVisualizer`, `*ComparisonTest`, `AccuracyCheckTest` under `src/test/.../infrastructure/photoscan/**`.

_Integration tests (require bundled ExifTool + sibling `photo-metadata-editor` repo):_

```bash
./gradlew downloadExifTool integrationTest
```

Sample RAW/JPEG fixtures come from `metadata-test-fixtures` on the test classpath (not copied into this repo).

## P3 — Docs & structure

- [ ] **`ADVERSARIAL_ANALYSIS.md` is stale** — ~12 findings cite files that no longer exist (`PeopleScreen`, `PersonService`, `FaceGroupingService`, `FaceEmbedding`, `Person`, `JsonPersonDirectoryAdapter`); that people/face-recognition subsystem was split out. Add a status/date header and prune, or archive it. (Only the duplicate-count TODO, M1, still maps to real code.)
- [ ] **Docs oversell features** — README/DEVELOPER_GUIDE promote a `@Preview` "component preview system" but there are **0 `@Preview`s** in the codebase; `./gradlew run` is described as "hot reload" (it isn't). `docs/ARCHITECTURE.md` misstates the `AppPaths` boundary exception (removed for `PathsPort`) and claims `PhotoScanWizardState` is "~1500 lines" (actually 494).
- [ ] **Remove `javax.inject` dependency** — `build.gradle.kts:89`; contradicts the konsist test that forbids JSR-330 and is unused.
- [ ] **Dev-only harnesses** — `src/dev/kotlin/.../LocationPickerTestApp.kt` and `MapTileRenderTestApp.kt` each declare a `fun main()`. Keep them out of `src/main` (already moved to `src/dev`; wire a dev source set if needed).
- [ ] **Structural cleanups** — standalone metadata editor reaches sideways into `wizard/edit/` and `wizard/metadata/` and models per-file state on `PhotoScanConfiguration` (scan-only fields ride along); consider a neutral shared module + a dedicated `MetadataOverrides` model. Two files both named `MetadataEditorPanel.kt` (different packages). Nine files exceed 800 lines (`MapTileRenderer` 1532, `BackImagePickerDialog` 1453, `FaceSelectorOverlay` 1418, `EditSections` 1134, …) — split candidates. Domain-layer boundaries are otherwise clean.

---

## Suggested first milestone

Bundle the remaining metadata-editor UX work and import safety fixes:

1. P2: unify override-checkbox semantics, adopt shared `RotationSection`, standardize headers, thread `sourceExif`, theme "Clear All", drop the dead conditional.
2. P0/P1: duplicate-hash grouping, copy-to-EOF, `renameOnly`, GPS rounding boundary.
3. Tests: `MetadataEditService.saveFile`, `MetadataEditUndoService.redo`, `ReorganizeService.undo`.

Quick low-risk batch (independent one-liners): `renameOnly` no-op (P1), GPS `6000000` (P1), dead keyword conditional (P2).
