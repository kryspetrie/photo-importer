# Face Selection & Override Simplification Plan

## Overview
This plan covers two main changes:
1. **Simplify the override indicator** — remove tri-state cycling; entering a value = override, clicking clear = null out
2. **Implement face region selection** — fullscreen mode for clicking faces on photos, with name entry popup and pill display

---

## Phase 1: Simplify OverrideIndicator (2-action model)

### 1.1 Refactor OverrideIndicator
- [x] Replace tri-state cycle (KEEP_SOURCE → OVERRIDE → NULL_OUT) with two clear actions:
  - **Entering a value** → automatically sets state to OVERRIDE
  - **Clear button (X)** → sets state to NULL_OUT (removes the EXIF field on export)
  - Remove KEEP_SOURCE from the visible UI — it's only the default when no override is present
- [x] The indicator becomes a simple "clear" button that appears when a field has content (either user-entered or inherited)
- [x] NULL_OUT fields show strikethrough text and are disabled (as current)

### 1.2 Update MetadataField and MetadataScreen
- [x] Remove `overrideState`/`onOverrideChange` parameters — replace with `onClear` callback
- [x] When user types in a field, the field is inherently in OVERRIDE mode
- [x] When user clicks the clear/X button, set the field to NULL_OUT mode
- [x] Click clear again on a NULL_OUT field → restore to default (KEEP_SOURCE)
- [x] Add `FocusRequester` chain for Tab navigation between all fields

### 1.3 Update AdvancedMetadataSection
- [x] Same simplification: replace OverrideState tri-state params with onClear callbacks
- [x] Add tab navigation order

### 1.4 Update WizardContainer Mapping
- [x] Update `toDomain()` to handle the simplified mapping:
  - `null` FieldOverride → KEEP_SOURCE (no user action taken)
  - Non-null FieldOverride with OVERRIDE state → OverrideState.OVERRIDE
  - Non-null FieldOverride with NULL_OUT state → OverrideState.NULL_OUT

### 1.5 Update PhotoScanExportService
- [x] No changes needed — the domain OverrideState enum already has all 3 values; the UI just no longer exposes KEEP_SOURCE as a clickable state

---

## Phase 2: Default to First Photo

- [x] When entering MetadataScreen, auto-select the first photo if none is selected
- [x] Use `LaunchedEffect` at screen entry to call `state.selectSingleMetadata(0)`

---

## Phase 3: Face Region Selection — Data Model & State

### 3.1 FaceRegion in PhotoConfiguration (already exists)
- [x] `FaceRegion` data class exists in `PhotoScanWizardState.kt` with `name`, `type`, `x`, `y`, `w`, `h`
- [x] `faceRegions: List<FaceRegion>` field exists in `PhotoConfiguration`

### 3.2 Face Selection State in PhotoScanWizardState
- [x] Add `_faceSelectMode = MutableStateFlow(false)` for fullscreen face selection
- [x] Add `_pendingFaceRegion` state for the face being placed (before naming)
- [x] Add methods: `enterFaceSelectMode()`, `exitFaceSelectMode()`
- [x] Add `addFaceRegion(photoIndex, name, x, y, type)` — creates default-sized FaceRegion centered at (x,y)
- [x] Add `removeFaceRegion(photoIndex, faceIndex)` — removes a face region by index
- [x] Add `updateFaceRegion(photoIndex, faceIndex, x?, y?, w?, h?)` — updates region position/dimensions
- [x] Add `moveFaceRegion(photoIndex, faceIndex, dx, dy)` — shifts region center position

---

## Phase 4: Face Selection UI — Metadata Screen

### 4.1 "Select Faces" Button
- [x] Add "Select Faces" button next to the subjects/faces field in MetadataScreen
- [x] Button opens fullscreen face select mode on the currently selected photo

### 4.2 Face Pills in Metadata
- [x] Display face regions as pills (chips) below the subjects text field
- [x] Each pill shows the region type icon + person's name with an X close button
- [x] Hover on X shows tooltip "Remove" or "Delete"
- [x] Clicking X removes the face region and updates the subjects string
- [x] Coordinates are NOT shown in the UI

### 4.3 Face Name Auto-Population
- [x] When a face is saved, add the name to the `subjects` comma-separated string
- [x] When a face pill is removed, remove the name from `subjects`
- [x] Re-render pills whenever `faceRegions` changes

---

## Phase 5: Fullscreen Face Selection Mode

### 5.1 Fullscreen Overlay
- [x] When "Select Faces" is clicked, open a Popup/Dialog that shows the cropped photo fullscreen
- [x] Close button (X) in top-right corner
- [x] "Select Faces" label/toolbar at top

### 5.2 Click-to-Place Bounding Box
- [x] Single click on the photo places a default-sized bounding box (centered on click point)
- [x] Default box size: approximately 15% of image width × 20% of image height (face-proportioned)
- [x] Box appears immediately with a name-entry popup

### 5.3 Name Entry Popup
- [x] After clicking, a small popup appears near the click point with:
  - Region type selector (Face, Pet, Body, Object chips)
  - Text input field for the person's name
  - Checkmark button (✓) to save
  - Cancel button to discard
- [x] Focus automatically goes to the text input
- [x] On save: create FaceRegion with name, type, and computed normalized coordinates

### 5.4 Showing Existing Face Regions
- [x] Draw rectangles (bounding boxes) on the photo for all existing face regions
- [x] Each box shows the person's name + region type icon as a label
- [x] Each box has an X button at the top-right corner
- [x] Clicking X removes that face region
- [x] Corner drag handles for resizing face bounding boxes
- [x] Interior drag for moving/repositioning face bounding boxes
- [x] Minimum size constraint (3% of image dimension)

### 5.5 Coordinate Mapping
- [x] Store coordinates as normalized fractions (0.0-1.0) relative to the cropped/photo image
- [x] When clicking, convert pixel position to normalized coordinates
- [x] When drawing boxes, convert from normalized coordinates back to pixel positions
- [x] Account for image scaling (ContentScale.Fit) in coordinate mapping

### 5.6 Inherited Face Regions
- [x] Read face regions from source image XMP metadata (via FaceRegionTransformer)
- [x] Transform coordinates through perspective/crop/rotation pipeline
- [x] Display inherited regions with cyan border and "Inherited" label
- [x] Adopt button (+) to convert inherited regions to user-specified regions
- [x] Filter out regions whose names already exist as user-specified
- [x] Toolbar shows count of inherited regions

---

## Phase 6: Wire Face Selection Through Wizard State

### 6.1 WizardContainer Integration
- [x] Pass face selection callbacks from MetadataScreen through to WizardContainer
- [x] Ensure face regions are included in the PhotoConfiguration when exporting
- [x] Face regions map from `FaceRegion` (wizard) → `FaceRegionConfig` (domain) in `toDomain()`

### 6.2 Export Integration
- [x] Verify `PhotoScanExportService` writes face regions as MWG-RS metadata
- [x] Face regions with coordinates write both the region AND add names to subjects/keywords
- [x] Pre/post-rotation dimension bug fixed (correctedImage dimensions used for homography)

---

## Extension: RegionType Selection
- [x] `RegionType` enum (Face, Pet, Body, Object) with MWG-RS mapping
- [x] `RegionType.fromMwgRs()` parser (case-insensitive, defaults to FACE)
- [x] Region type chip selector in name entry popup
- [x] Type-specific icons on face pills (Face→Face, Pet→Pets, Body→Accessibility, Object→Category)
- [x] Type preserved through inherited region adoption

---

## Implementation Order

1. ✅ Phase 1 — Simplify OverrideIndicator
2. ✅ Phase 2 — Default to first photo
3. ✅ Phase 3 — Face region state management
4. ✅ Phase 4 — Face pills in MetadataScreen
5. ✅ Phase 5 — Fullscreen face selection (including resize/move/inherited regions)
6. ✅ Phase 6 — Wiring and export
7. ✅ Extension — RegionType selection