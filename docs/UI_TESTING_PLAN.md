# UI Testing Plan — Petrie File Importer

## Overview

This document maps every UI surface in the application against existing test coverage and identifies gaps. Tests are categorized into three types:

| Type | Framework | Tag | Run with |
|------|-----------|-----|----------|
| **Compose UI** | `createComposeRule()` + `onNodeWithText`/`performClick` | `UiComponentTest` | `./gradlew uiTest` |
| **Unit** | JUnit 5 + AssertJ | _(none)_ | `./gradlew test` |
| **Integration** | Compose UI + real services | `integration` | `./gradlew integrationTest` |

---

## 1. App Shell — `PetrieFileImporterApp`

**What it does:** Tab navigation (side rail / bottom bar), keyboard shortcuts (Ctrl+1–5), model download prompt, theme/density/locale providers.

| Test | Type | Status | Priority |
|------|------|--------|----------|
| All 5 tabs render when clicked | Compose UI | ❌ Missing | **P0** |
| Side rail renders when wide / bottom bar when narrow | Compose UI | ❌ Missing | P1 |
| Ctrl+1–5 switches tabs | Compose UI | ❌ Missing | P1 |
| Model download dialog appears when model not downloaded | Compose UI | ❌ Missing | P2 |
| Model download dialog dismisses on cancel | Compose UI | ❌ Missing | P2 |
| `AppNavigationStyle.AUTO` breakpoint logic | Unit | ❌ Missing | P2 |
| `appTabFromSettingsName` fallback to PHOTO_SCAN | Unit | ❌ Missing | P2 |

---

## 2. Media Import Tab — `MediaImportScreen`

**What it does:** Source/destination fields, import configuration, watch folders, import flow (select → setup → dupe review → preview → import), history.

| Test | Type | Status | Priority |
|------|------|--------|----------|
| Title and subtitle render | Compose UI | ❌ Missing | **P0** |
| Source/destination fields render | Compose UI | ❌ Missing | **P0** |
| Settings section expands/collapses | Compose UI | ❌ Missing | P1 |
| Action bar shows "Start Import" when paths valid | Compose UI | ❌ Missing | P1 |
| Error card renders when error message set | Compose UI | ❌ Missing | P1 |
| Watch folder status card renders | Compose UI | ❌ Missing | P2 |
| Import history section expands/collapses | Compose UI | ❌ Missing | P2 |
| Content width constrained (max 900dp) | Compose UI | ❌ Missing | P1 |
| Single header (no double header) | Compose UI | ❌ Missing | P1 |
| `MediaImportViewModel` state transitions | Unit | ✅ Exists | — |
| `MediaImportLandingHero` renders steps | Compose UI | ✅ Exists | — |

---

## 3. Reorganize Tab — `ReorganizeScreen`

**What it does:** Folder selection, operation mode (move/copy), rename-only toggle, scan → preview → execute flow, undo journals.

| Test | Type | Status | Priority |
|------|------|--------|----------|
| Title and description render | Compose UI | ❌ Missing | **P0** |
| Folder selection field renders | Compose UI | ❌ Missing | **P0** |
| Move/Copy radio buttons render and switch | Compose UI | ❌ Missing | P1 |
| Rename-only toggle renders | Compose UI | ❌ Missing | P1 |
| "Start Preview" button enabled only when path set | Compose UI | ❌ Missing | P1 |
| Preview section renders after scan | Compose UI | ❌ Missing | P1 |
| Undo section lists journals | Compose UI | ❌ Missing | P2 |
| Undo confirm dialog appears and confirms | Compose UI | ❌ Missing | P2 |
| Error card renders on failure | Compose UI | ❌ Missing | P1 |
| Content width constrained (max 900dp) | Compose UI | ❌ Missing | P1 |
| Single header (no double header) | Compose UI | ❌ Missing | P1 |
| `ReorganizeViewModel` state transitions | Unit | ❌ Missing | P1 |

---

## 4. Duplicate Scanner Tab — `DuplicateScannerScreen`

**What it does:** Folder selection, detection methods (hash/exif/surf), scan → results → resolve flow.

| Test | Type | Status | Priority |
|------|------|--------|----------|
| Title and description render | Compose UI | ❌ Missing | **P0** |
| Folder selection field renders | Compose UI | ❌ Missing | **P0** |
| Detection method toggles render | Compose UI | ❌ Missing | P1 |
| "Scan" button enabled only when path set | Compose UI | ❌ Missing | P1 |
| Scanning progress renders | Compose UI | ❌ Missing | P1 |
| Results view renders with duplicate groups | Compose UI | ❌ Missing | P1 |
| Resolve confirm dialog appears | Compose UI | ❌ Missing | P2 |
| Resolve action radio buttons switch | Compose UI | ❌ Missing | P2 |
| Content width constrained (max 900dp) | Compose UI | ❌ Missing | P1 |
| Single header (no double header) | Compose UI | ❌ Missing | P1 |
| `DuplicateGroupCard` renders | Compose UI | ✅ Exists | — |
| `DuplicateScannerViewModel` state transitions | Unit | ❌ Missing | P1 |

---

## 5. Metadata Editor Tab — `MetadataEditorScreen`

**What it does:** Landing page (source path, open folder, select images), editing layout (browser panel, preview pane, metadata fields), face tagging, location picker, rotation preview, bulk operations.

| Test | Type | Status | Priority |
|------|------|--------|----------|
| Landing title and description render | Compose UI | ❌ Missing | **P0** |
| Source path field renders on landing | Compose UI | ❌ Missing | **P0** |
| "Open" button renders when path entered | Compose UI | ❌ Missing | P1 |
| "Select Images" button renders | Compose UI | ❌ Missing | P1 |
| Include subfolders checkbox renders | Compose UI | ❌ Missing | P1 |
| Recent paths list renders | Compose UI | ❌ Missing | P2 |
| Loading spinner renders while loading | Compose UI | ❌ Missing | P1 |
| Error message renders on load failure | Compose UI | ❌ Missing | P1 |
| Content width constrained (max 800dp wide, 560dp narrow) | Compose UI | ❌ Missing | P1 |
| Single header (no double header) | Compose UI | ❌ Missing | P1 |
| Buttons not full-width (FlowRow) | Compose UI | ❌ Missing | P1 |
| `MetadataEditorViewModel` | Unit | ✅ Exists | — |
| `MetadataEditorCommandBar` | Compose UI | ✅ Exists | — |
| `MetadataEditorEditingLayout` | Compose UI | ✅ Exists | — |
| `MetadataEditorFileTree` | Compose UI | ✅ Exists | — |
| `MetadataEditorBrowserNavigation` | Compose UI | ✅ Exists | — |
| `MetadataEditorColumnNavigation` | Compose UI | ✅ Exists | — |
| `MetadataEditorHierarchyList` | Compose UI | ✅ Exists | — |
| `MetadataEditorFileViewMode` | Compose UI | ✅ Exists | — |
| `MetadataEditorLayoutBreakpoint` | Compose UI | ✅ Exists | — |
| `MetadataEditorLayoutPreferencesIntegration` | Integration | ✅ Exists | — |
| `MetadataEditorPanelController` | Compose UI | ✅ Exists | — |
| `MetadataEditorShortcuts` | Compose UI | ✅ Exists | — |
| `PreviewImageGeometry` | Compose UI | ✅ Exists | — |

---

## 6. Photo Scan Wizard — `WizardContainer`

### 6a. Import Step — `PhotoScanImportScreen`

| Test | Type | Status | Priority |
|------|------|--------|----------|
| Title renders | Compose UI | ✅ Exists | — |
| Landing hero with workflow steps renders | Compose UI | ✅ Exists | — |
| Scan mode cards render | Compose UI | ✅ Exists | — |
| Auto-detect toggle renders | Compose UI | ✅ Exists | — |
| "Import Scans" button renders | Compose UI | ✅ Exists | — |
| Cancel button calls onCancel | Compose UI | ✅ Exists | — |
| Content width constrained | Compose UI | ❌ Missing | P1 |
| Single header (no double header) | Compose UI | ❌ Missing | P1 |

### 6b. Overview Step — `OverviewScreen`

| Test | Type | Status | Priority |
|------|------|--------|----------|
| Title renders | Compose UI | ✅ Exists | — |
| 4-Point / Add Box / To Summary buttons render | Compose UI | ✅ Exists | — |
| Box count displays | Compose UI | ✅ Exists | — |
| Zoom controls work | Compose UI | ✅ Exists | — |
| Help dialog opens | Compose UI | ✅ Exists | — |
| 4-Point mode activates on click | Compose UI | ✅ Exists | — |
| Skip button renders in batch mode | Compose UI | ❌ Missing | P2 |

### 6c. Summary Step — `SummaryScreen`

| Test | Type | Status | Priority |
|------|------|--------|----------|
| Title renders | Compose UI | ✅ Exists | — |
| Photo count displays | Compose UI | ✅ Exists | — |
| Export button calls onExport | Compose UI | ✅ Exists | — |
| Back button calls onBack | Compose UI | ✅ Exists | — |
| Export settings card renders | Compose UI | ✅ Exists | — |

### 6d. Refinement Step — `RefinementScreen`

| Test | Type | Status | Priority |
|------|------|--------|----------|
| Title renders | Compose UI | ✅ Exists | — |
| Perspective toggle renders | Compose UI | ✅ Exists | — |
| Rotation controls render | Compose UI | ✅ Exists | — |
| Apply button renders | Compose UI | ✅ Exists | — |
| Zoom controls work | Compose UI | ✅ Exists | — |
| Help dialog opens | Compose UI | ✅ Exists | — |
| Coordinate transforms (imageToScreen, screenToImage) | Unit | ✅ Exists | — |
| Corner hit detection | Unit | ✅ Exists | — |

### 6e. Edit Step — `EditScreen`

| Test | Type | Status | Priority |
|------|------|--------|----------|
| Edit screen renders with metadata panel | Compose UI | ❌ Missing | **P0** |
| Photo sidebar renders with box list | Compose UI | ❌ Missing | P1 |
| Quick edit metadata fields render | Compose UI | ✅ Exists | — |
| Face tagging overlay opens | Compose UI | ❌ Missing | P2 |
| Location picker overlay opens | Compose UI | ❌ Missing | P2 |
| Export triggers processing step | Compose UI | ❌ Missing | P1 |
| Skip button renders in batch mode | Compose UI | ❌ Missing | P2 |

### 6f. Completion Step — `CompletionScreen`

| Test | Type | Status | Priority |
|------|------|--------|----------|
| Title renders | Compose UI | ✅ Exists (in WizardUiFlowTest) | — |
| Photo count displays | Compose UI | ✅ Exists | — |
| Failed count displays | Compose UI | ✅ Exists | — |
| Batch progress displays | Compose UI | ✅ Exists | — |
| Done button calls onDone | Compose UI | ✅ Exists | — |
| Continue/Skip in batch mode | Compose UI | ✅ Exists | — |

### 6g. Processing Step — `ProcessingScreen`

| Test | Type | Status | Priority |
|------|------|--------|----------|
| Progress bar renders | Compose UI | ❌ Missing | P2 |
| Current file name displays | Compose UI | ❌ Missing | P2 |
| Cancel confirm dialog appears | Compose UI | ❌ Missing | P2 |

### 6h. Wizard State (non-UI)

| Test | Type | Status | Priority |
|------|------|--------|----------|
| `PhotoScanWizardState` initialization | Unit | ✅ Exists | — |
| Box add/remove/select | Unit | ✅ Exists | — |
| Zoom in/out/min | Unit | ✅ Exists | — |
| Wizard mode transitions | Unit | ✅ Exists | — |
| Photo configuration CRUD | Unit | ✅ Exists | — |
| Four-point state | Unit | ✅ Exists | — |
| Navigation state | Unit | ✅ Exists | — |
| Image loading/clearing | Unit | ✅ Exists | — |
| `AspectRatioHandler` | Unit | ✅ Exists | — |
| `FaceSelection` | Unit | ✅ Exists | — |
| `FourPointState` | Unit | ✅ Exists | — |
| `ImageBatchState` | Unit | ✅ Exists | — |
| `PhotoScanConstants` | Unit | ✅ Exists | — |
| `PhotoScanWizardFlow` | Unit | ✅ Exists | — |
| `UndoRedoManager` | Unit | ✅ Exists | — |
| `ZoomController` | Unit | ✅ Exists | — |
| `WizardContainerViewModel` | Unit | ✅ Exists | — |

---

## 7. Standalone Screens

### 7a. `ImportProgressScreen`

| Test | Type | Status | Priority |
|------|------|--------|----------|
| Progress bar and percentage render | Compose UI | ❌ Missing | P2 |
| Current file name displays | Compose UI | ❌ Missing | P2 |
| Cancel button calls onCancel | Compose UI | ❌ Missing | P2 |

### 7b. `PreviewStructureScreen`

| Test | Type | Status | Priority |
|------|------|--------|----------|
| File count and folder stats render | Compose UI | ❌ Missing | P2 |
| Source/destination folder trees render | Compose UI | ❌ Missing | P2 |
| Conflict warning renders when conflicts exist | Compose UI | ❌ Missing | P2 |
| Back/Import buttons render | Compose UI | ❌ Missing | P2 |

### 7c. `DuplicateReviewScreen`

| Test | Type | Status | Priority |
|------|------|--------|----------|
| Title renders | Compose UI | ❌ Missing | P2 |
| "No duplicates" state renders | Compose UI | ❌ Missing | P2 |
| Back/Continue buttons render | Compose UI | ❌ Missing | P2 |

### 7d. `ImagePreviewScreen`

| Test | Type | Status | Priority |
|------|------|--------|----------|
| Header with select all/none renders | Compose UI | ❌ Missing | P2 |
| Filter and sort bar renders | Compose UI | ❌ Missing | P2 |
| Selection status bar renders | Compose UI | ❌ Missing | P2 |
| Grid/list view toggles | Compose UI | ❌ Missing | P2 |
| Full-screen overlay opens/closes | Compose UI | ❌ Missing | P2 |

---

## 8. Shared Components

### 8a. Components with NO tests

| Component | Test | Type | Priority |
|-----------|------|------|----------|
| `ChunkyScrollbar` | Renders scrollbar thumb | Compose UI | P2 |
| `ThumbnailImage` | Renders placeholder when no thumbnail | Compose UI | P2 |
| `PathSelectionField` | Text field + browse button render | Compose UI | P2 |
| `RotationBadge` | Renders rotation angle | Compose UI | P3 |
| `SectionLabel` | Renders label text | Compose UI | P3 |
| `SetupScreenKeyboard` | Keyboard event handling | Unit | P3 |
| `ShortcutLabels` | Renders shortcut hints | Compose UI | P3 |
| `CircularSpinner` | Renders spinning indicator | Compose UI | P3 |
| `SettingsToggle` | Toggle renders and calls callback | Compose UI | P2 |

### 8b. Components with existing tests

| Component | Test | Status |
|-----------|------|--------|
| `AppKeyboardShortcuts` | Keyboard shortcut detection | ✅ Exists |
| `DropTarget` | Drag-and-drop handling | ✅ Exists |
| `FileDialogs` | File/folder picker dialogs | ✅ Exists |
| `ImportResultComponent` | Import result display | ✅ Exists |
| `LoadingComponent` / `LoadingIndicator` | Loading states | ✅ Exists |
| `PlaceholderHelpTooltip` | Help tooltip | ✅ Exists |
| `PreviewCache` | Cache behavior | ✅ Exists |
| `ResizableWeightSplitPane` | Split pane resizing | ✅ Exists |
| `SessionPreferencesEffect` | Session persistence | ✅ Exists |
| `SettingsComponent` | Settings UI | ✅ Exists |
| `SharedComponent` | Shared UI elements | ✅ Exists |

---

## 9. Wizard Sub-Components (no tests)

| Component | Test | Type | Priority |
|-----------|------|------|----------|
| `FaceSelectorOverlay` | Renders face tagging UI | Compose UI | P2 |
| `FaceSelectorSidebar` | Region type / face size selectors | Compose UI | P2 |
| `EditDialogs` | Dialog wrapper renders | Compose UI | P2 |
| `EditFaceFlow` | Face name entry flow | Compose UI | P2 |
| `EditLocationPicker` | Location picker integration | Compose UI | P2 |
| `CameraSection` | Camera make/model fields | Compose UI | P3 |
| `LocationSection` | Location fields | Compose UI | P3 |
| `SubjectsSection` | Subject tags | Compose UI | P3 |
| `SourceMetadataSection` | Source EXIF display | Compose UI | P3 |
| `WizardEditMetadataPanel` | Full metadata edit panel | Compose UI | P2 |
| `PhotoSidebar` | Photo list sidebar | Compose UI | P2 |
| `EditScreenChrome` | Edit screen layout wrapper | Compose UI | P2 |
| `OverviewCanvas` | Canvas rendering | Compose UI | P2 |
| `OverviewControlsPanel` | Overview control buttons | Compose UI | P2 |
| `RefinementCanvas` | Refinement canvas | Compose UI | P2 |
| `RefinementControls` | Refinement sliders | Compose UI | P2 |
| `SummaryTopBar` | Summary toolbar | Compose UI | P3 |
| `DetailPreviewPanel` | Detail preview | Compose UI | P2 |
| `PhotoSidebarList` | Sidebar photo list | Compose UI | P2 |
| `ExportBottomBar` | Export action bar | Compose UI | P2 |
| `BulkActionButtons` | Bulk rotate/delete buttons | Compose UI | P3 |
| `AspectRatioDropdown` | Aspect ratio selector | Compose UI | P3 |
| `ExportSettingsCard` | Export settings | Compose UI | P2 |
| `BackImageCanvas` | Back image overlay | Compose UI | P2 |
| `BackImagePickerDialog` | Back image picker | Compose UI | P2 |
| `BatchFileGrid` | Batch file thumbnail grid | Compose UI | P3 |
| `NearbyFilesStrip` | Nearby file navigation | Compose UI | P3 |
| `PhotoScanImportTopBar` | Import top bar | Compose UI | P3 |
| `PhotoScanSettingsSection` | Photo scan settings | Compose UI | P2 |
| `PhotoScanLandingHero` | Landing hero card | Compose UI | P2 |
| `PhotoScanSourceDestSection` | Source/dest fields | Compose UI | P2 |

---

## 10. Map Components (no tests)

| Component | Test | Type | Priority |
|-----------|------|------|----------|
| `OsmMapView` | Renders map tiles | Compose UI | P3 |
| `OsmMapControls` | Zoom/pan controls | Compose UI | P3 |
| `MapCameraState` | Camera state math | Unit | P3 |
| `TileCache` / `DiskTileCache` | Cache behavior | Unit | P3 |
| `LocationPickerDialog` | Search + map integration | Compose UI | P3 |
| `MetadataField` | Field rendering | Compose UI | P3 |
| `OverrideCheckbox` | Override toggle | Compose UI | P3 |

---

## 11. Theme & i18n

| Test | Type | Status | Priority |
|------|------|--------|----------|
| `Strings.regionTypeName()` all types | Unit | ✅ Exists | — |
| `Strings.faceSizeName()` all sizes | Unit | ✅ Exists | — |
| `Strings.t()` convenience accessors | Unit | ✅ Exists | — |
| `UiDensity` scale values | Unit | ✅ Exists | — |
| `LocaleCompletenessTest` | Unit | ✅ Exists | — |
| `UiLocalizationArchitectureTest` | Unit | ✅ Exists | — |
| `UiTextLocalizationInspector` | Unit | ✅ Exists | — |
| Theme color consistency | Compose UI | ❌ Missing | P3 |
| Density scaling applies correctly | Compose UI | ❌ Missing | P3 |

---

## 12. Recent Desktop UI Changes — Regression Tests

These tests specifically verify the four fixes applied in the most recent iteration.

| Test | Type | Priority |
|------|------|----------|
| **Language switching:** MenuBar text changes immediately when locale changes | Compose UI | **P0** |
| **Content width:** `MediaImportScreen` content ≤ 900dp | Compose UI | **P0** |
| **Content width:** `ReorganizeScreen` content ≤ 900dp | Compose UI | **P0** |
| **Content width:** `DuplicateScannerScreen` content ≤ 900dp | Compose UI | **P0** |
| **Content width:** `MetadataEditorScreen` landing ≤ 800dp | Compose UI | **P0** |
| **FlowRow:** `MetadataEditorScreen` action buttons use FlowRow (not full-width Column) | Compose UI | **P0** |
| **Single header:** `MediaImportScreen` has exactly one header Surface | Compose UI | **P0** |
| **Single header:** `ReorganizeScreen` has exactly one header Surface | Compose UI | **P0** |
| **Single header:** `DuplicateScannerScreen` has exactly one header Surface | Compose UI | **P0** |
| **Single header:** `MetadataEditorScreen` landing has exactly one header Surface | Compose UI | **P0** |
| **Single header:** `PhotoScanImportScreen` has exactly one header Surface | Compose UI | **P0** |
| **Metadata editor format:** Landing page uses scrollable card pattern matching other pages | Compose UI | **P0** |

---

## 13. Summary of Gaps by Priority

### P0 — Critical (must have, blocks release)
| # | Test | Screen |
|---|------|--------|
| 1 | Tab navigation renders all 5 tabs | `PetrieFileImporterApp` |
| 2 | Language switching updates MenuBar immediately | `PetrieFileImporterApp` |
| 3 | Content width constrained on all 5 main screens | All tab screens |
| 4 | Single header on all 5 main screens | All tab screens |
| 5 | FlowRow for action buttons on metadata editor | `MetadataEditorScreen` |
| 6 | Title/subtitle render on MediaImportScreen | `MediaImportScreen` |
| 7 | Title/description render on ReorganizeScreen | `ReorganizeScreen` |
| 8 | Title/description render on DuplicateScannerScreen | `DuplicateScannerScreen` |
| 9 | Landing title/description render on MetadataEditorScreen | `MetadataEditorScreen` |
| 10 | EditScreen renders with metadata panel | `EditScreen` |

### P1 — Important (should have)
| # | Test | Screen |
|---|------|--------|
| 11 | Source/destination fields render | `MediaImportScreen` |
| 12 | Settings section expands/collapses | `MediaImportScreen` |
| 13 | Action bar enables when paths valid | `MediaImportScreen` |
| 14 | Move/Copy radio buttons switch | `ReorganizeScreen` |
| 15 | Preview section renders after scan | `ReorganizeScreen` |
| 16 | Detection method toggles render | `DuplicateScannerScreen` |
| 17 | Results view renders with groups | `DuplicateScannerScreen` |
| 18 | "Open" / "Select Images" buttons render | `MetadataEditorScreen` |
| 19 | Include subfolders checkbox renders | `MetadataEditorScreen` |
| 20 | Side rail vs bottom bar based on width | `PetrieFileImporterApp` |
| 21 | Ctrl+1–5 keyboard shortcuts | `PetrieFileImporterApp` |
| 22 | ReorganizeViewModel state transitions | `ReorganizeScreen` |
| 23 | DuplicateScannerViewModel state transitions | `DuplicateScannerScreen` |
| 24 | Export triggers processing step | `EditScreen` |
| 25 | Photo sidebar renders | `EditScreen` |

### P2 — Nice to have
| # | Test | Screen/Component |
|---|------|-----------------|
| 26 | Model download dialog | `PetrieFileImporterApp` |
| 27 | Watch folder status card | `MediaImportScreen` |
| 28 | Import history section | `MediaImportScreen` |
| 29 | Undo section lists journals | `ReorganizeScreen` |
| 30 | Resolve confirm dialog | `DuplicateScannerScreen` |
| 31 | Recent paths list | `MetadataEditorScreen` |
| 32 | Face tagging overlay | `EditScreen` |
| 33 | Location picker overlay | `EditScreen` |
| 34 | Processing screen progress | `WizardContainer` |
| 35 | ImportProgressScreen | Standalone |
| 36 | PreviewStructureScreen | Standalone |
| 37 | DuplicateReviewScreen | Standalone |
| 38 | ImagePreviewScreen | Standalone |
| 39 | FaceSelectorOverlay/Sidebar | Wizard components |
| 40 | EditScreenChrome | Wizard components |
| 41 | OverviewCanvas/ControlsPanel | Wizard components |
| 42 | RefinementCanvas/Controls | Wizard components |
| 43 | DetailPreviewPanel | Wizard components |
| 44 | ExportBottomBar | Wizard components |
| 45 | BackImageCanvas/PickerDialog | Wizard components |
| 46 | PhotoScanSettingsSection | Wizard components |
| 47 | PhotoScanLandingHero | Wizard components |
| 48 | ChunkyScrollbar | Shared |
| 49 | ThumbnailImage | Shared |
| 50 | PathSelectionField | Shared |
| 51 | SettingsToggle | Shared |

### P3 — Low priority
All remaining components listed in sections 8–11 above.

---

## 14. Implementation Order

### Phase 1: Critical Regression Tests (P0, items 1–10)
~10 new test files. Covers the four recent UI fixes plus basic rendering of all tab screens.

### Phase 2: Interaction Tests (P1, items 11–25)
~15 tests across existing screens. Covers user interactions, state transitions, and ViewModel logic.

### Phase 3: Edge Cases & Dialogs (P2, items 26–51)
~26 tests. Covers dialogs, overlays, standalone screens, and shared components.

### Phase 4: Polish (P3)
Remaining components. Low ROI; only implement if time permits.

---

## 15. Test Patterns

### Compose UI Test Template

```kotlin
@DisplayName("ScreenName Component Tests")
@Tag("UiComponentTest")
class ScreenNameTest {

    @get:Rule val composeTestRule = createComposeRule()

    // Setup: create state, mocks, test data

    @Test
    @DisplayName("should display title")
    fun shouldDisplayTitle() {
        composeTestRule.setContent {
            // Wrap in theme + strings provider
            TestStringsProvider {
                ImporterTheme {
                    ScreenName(...)
                }
            }
        }

        composeTestRule.onNodeWithText("Expected Title").assertIsDisplayed()
    }

    @Test
    @DisplayName("should call callback on button click")
    fun shouldCallCallback() {
        var clicked = false
        composeTestRule.setContent {
            TestStringsProvider {
                ImporterTheme {
                    ScreenName(onAction = { clicked = true })
                }
            }
        }

        composeTestRule.onNodeWithText("Action").performClick()
        assertThat(clicked).isTrue()
    }
}
```

### ViewModel Unit Test Template

```kotlin
@DisplayName("ViewModelName")
class ViewModelNameTest {

    private val viewModel = ViewModelName(
        dependency1 = mock(),
        dependency2 = mock(),
    )

    @Test
    fun `should initialize with default state`() {
        assertThat(viewModel.state.value).isEqualTo(InitialState)
    }

    @Test
    fun `should transition state on action`() {
        viewModel.performAction()
        assertThat(viewModel.state.value).isEqualTo(ExpectedState)
    }
}
```

### Running Tests

```bash
# All unit tests (excludes UI component tests)
./gradlew test

# UI component tests only (requires display)
./gradlew uiTest

# Integration tests
./gradlew integrationTest

# All tests
./gradlew test uiTest integrationTest
```
