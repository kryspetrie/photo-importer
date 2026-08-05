# UI consistency improvement plan

Phased plan to address trust, metadata-editor scale, cross-tab parity, and polish. Each phase ends with a **retrospective** (gates the next phase) and **usability analysis** (for UI-changing phases).

**Explicitly out of scope**

- Organization settings UI deduplication (shared `ImportConfigurationEditor`)
- Back-of-photo / Add Back in bulk metadata editor (requires pixel overwrite)
- Bulk ↔ wizard override semantics unification (intentional product split)

**Decision log**

| Decision | Rationale |
|----------|-----------|
| Hide back image in metadata editor | Avoid half-built pixel workflows in metadata-only bulk edit |
| Phase 2 before Phase 3 | Primary pain is bulk metadata at scale |
| Retrospectives gate next phase | Prevent carrying wrong assumptions forward |

---

## Phase 0 — Baseline & measurement ✅ complete

**Goal:** Repeatable before/after baseline. No user-visible changes.

### Work

- [x] Document current behavior: Import config reset, metadata output reset, browser perf, back-image visibility, undo icons, `autoOrientInMetadataEditor`
- [x] Prepare fixtures: ~50, ~500, ~2000+ images (hierarchy/column tests use synthetic trees; large-folder scroll deferred to release QA)
- [x] Usability script v1 (task list below)
- [x] Metrics: automated coverage for settings round-trip; scroll jank scored manually in release QA

### Gate to Phase 1

Baseline checklist complete.

### Retrospective template

- Are fixtures realistic (RAW mix, nested folders)?
- Which flows do power users use?
- Any surprise (e.g. back image still saving pixels from bulk save path)?

---

## Phase 1 — Trust fixes ✅ complete

**Goal:** Settings honesty; no misleading UI; correct source hints.

| # | Item | Status |
|---|------|--------|
| 1.1 | Import tab persists `importTabSettings.configuration` | ✅ `SessionPreferencesEffect` |
| 1.2 | Hide metadata editor back-of-photo UI | ✅ |
| 1.3 | Fix `MetadataField` source hint (`FIELD_SOURCE_VALUE`) | ✅ |

### Exit criteria

- Import org/dedup/auto-orient settings survive restart
- No Add Back / back picker in metadata editor
- Non-GPS fields show generic “Source: …” hint
- `./gradlew test` green

### Usability analysis (Phase 1)

| Task | Success | Verified |
|------|---------|----------|
| Import: set auto-orient + dedup, restart | Settings retained | ✅ `TabSettingsTest`, `MediaImportViewModelTest`, `SessionPreferencesEffectTest` |
| Metadata editor: read field hints | Natural wording | ✅ `FIELD_SOURCE_VALUE` in all locales; `LocaleTranslationQualityTest` |
| Preview pane: scan for back controls | None visible | ✅ `MetadataEditorCommandBarComponentTest` |

### Retrospective → Phase 2

- Persistence pattern: reuse `TabSettings` + `SessionPreferencesEffect` everywhere ✅ (Import, Photo Scan Import, Reorganize, Duplicates, Metadata editor)
- Output controls: landing vs command bar preference? → command bar only
- Large-folder size for Phase 2 perf test (500 vs 2000)? → unit tests for hierarchy/column; 2000-file manual scroll in release QA

---

## Phase 2 — Metadata editor scale & session UX ✅ complete

**Goal:** Large libraries; session prefs; clearer command bar.

| # | Item | Status |
|---|------|--------|
| 2.1 | Virtualize LIST / COLUMN / HIERARCHY browser views | ✅ |
| 2.2 | Persist output mode, output directory, include-subfolders | ✅ |
| 2.3 | Dedupe output controls (landing **or** command bar — pick in Phase 1 retro) | ✅ command bar only |
| 2.4 | Undo/Redo icons (not rotation icons) | ✅ |
| 2.5 | Wire `autoOrientInMetadataEditor` setting | ✅ |

### Exit criteria

- 2000-file folder scrollable without multi-second freeze on view switch
- Output prefs survive restart
- Auto-rotate button respects setting

### Usability analysis (Phase 2)

| Task | Verified |
|------|----------|
| Large folder: switch LIST → ICONS → COLUMN | ✅ `MetadataEditorHierarchyListTest`, `MetadataEditorColumnNavigationTest`, lazy browser components |
| Output prefs after restart | ✅ `MetadataEditorSessionPreferencesTest`, `AppSettingsMetadataEditorTest` |
| Undo/redo icon clarity (1–5) | ✅ `MetadataEditorCommandBarComponentTest` (Undo/Redo icons, not rotation) |
| Auto-orient toggle discoverability | ✅ gated by `autoOrientInMetadataEditor` in command bar tests |

### Retrospective → Phase 3

- COLUMN view worth keeping at scale? → yes; navigation helpers tested
- LRU cap on in-memory thumbnails? → `FolderThumbnailCacheServiceTest`
- Keyboard shortcuts: defer or promote? → promoted in Phase 4

---

## Phase 3 — Cross-tab parity & i18n ✅ complete

| # | Item | Status |
|---|------|--------|
| 3.1 | Reorganize session persistence (`TabSettings` or dedicated fields) | ✅ |
| 3.2 | Duplicate Scanner session persistence | ✅ |
| 3.3 | Localize Photo Scan Import CTA | ✅ |
| 3.4 | Localize Advanced Settings helper strings | ✅ |
| 3.5 | Remove dead `onImportModeChange` stub | ✅ |
| 3.6 | Photo Scan Import tab uses `SessionPreferencesEffect` (parity with Import tab) | ✅ |

### Usability analysis (Phase 3)

| Task | Verified |
|------|----------|
| Reorganize/Duplicates: folder + config after restart | ✅ `ReorganizeSessionPreferencesTest`, `DuplicateScannerSessionPreferencesTest`, `SessionPreferencesEffectTest` |
| Locale switch on Photo Scan Import CTA | ✅ `LocaleTranslationQualityTest`; CTA uses `StringKey` |
| Cross-tab continuity (Import → Reorganize → Metadata editor) | ✅ shared `SessionPreferencesEffect` + settings flow on all tabs |

### Retrospective → Phase 4

- TabSettings schema final shape → `TabSettings` + dedicated `*SessionPreferences` for feature tabs
- Move thumbnail cache controls to app menu? → deferred

---

## Phase 4 — Discoverability, a11y, polish ✅ complete

| # | Item | Status |
|---|------|--------|
| 4.1 | Global keyboard shortcut help | ✅ Help menu + Ctrl+/ |
| 4.2 | Main tab keyboard basics (optional) | ✅ Ctrl+1–5 tab switch; Enter on Reorganize/Duplicates |
| 4.3 | Targeted accessibility (`contentDescription`, duplicate Keep chip) | ✅ |
| 4.4 | Light visual consistency (titles, padding, spinner theme) | ✅ Reorganize title; metadata spinner color |
| 4.5 | Remove dead `metadataEditorLayoutMode`; optional `ScanScreen` cleanup | ✅ |

### Usability analysis (Phase 4)

| Task | Verified |
|------|----------|
| Help menu + Ctrl+/ (Ctrl+?) opens shortcut dialog | ✅ `AppKeyboardShortcutsTest`, `ShortcutLabelsTest` |
| Ctrl+1–5 tab switch | ✅ `AppKeyboardShortcutsTest` |
| Enter on Reorganize/Duplicates setup | ✅ `SetupScreenKeyboardTest` |
| Duplicate Keep badge non-interactive | ✅ `DuplicateGroupCardComponentTest` |
| Reorganize title + metadata spinner theme | ✅ component/visual review in PR |

### Final program retrospective

| Goal | Outcome |
|------|---------|
| Trust (honest UI, settings stick) | ✅ All tabs use guarded session persistence |
| Metadata editor at scale | ✅ Virtualized browser + session prefs |
| Cross-tab parity | ✅ TabSettings + session prefs + i18n quality test |
| Discoverability / a11y | ✅ Global shortcuts, platform-aware labels, a11y tests |

**Release QA (human only):** 2000-image folder scroll smoothness; VoiceOver spot-check on Duplicate Scanner and Metadata editor browser.

---

## Photo Scan as primary experience (2026-07-28)

Photo Scan is the product's core workflow. Prioritized improvements:

| Area | Status |
|------|--------|
| App opens on Photo Scan tab (new default + last tab persisted) | ✅ |
| Landing hero: workflow subtitle, steps, Enter hint | ✅ |
| Scan mode toggles + settings expanded persist across restart | ✅ `PhotoScanImportSessionPreferences` |
| Export defaults (perspective, margin, strategy) persist across restart | ✅ |
| Skip crop/rotate + auto-skip back files in session bundle | ✅ |
| Shared org settings with Media Import tab | ✅ `OrganizationSettingsSection` |
| Wizard dependencies via `WizardContainerViewModel` | ✅ |
| Density-aware primary CTA | ✅ |
| Overview delete dialog localized | ✅ `WIZARD_DELETE_PHOTO_MESSAGE` |

**Next:** Media Import composable tests; orientation subsection in Import settings panel.

| Media Import | Status |
|------|--------|
| Landing hero: subtitle, steps, Enter hint | ✅ |
| Enter starts Import All when paths set | ✅ |
| Settings/history expanded persist across restart | ✅ `MediaImportSessionPreferences` |
| Shared org + orientation settings with Photo Scan | ✅ |
| Landing hero composable test | ✅ |
| Duplicate count reported from pre-import detection | ✅ |
| Density-aware spacing | ✅ |

---

## Usability session template

```markdown
## Usability session — Phase N

**Date:**  
**Build:**  
**Participant(s):**  
**Fixture:**

### Tasks (pass/fail + notes)
1. ...

### Severity log
| ID | Severity | Description | Fix phase |
|----|----------|-------------|-----------|

### Scores (1–5)
- Learnability / Efficiency / Error prevention / Cross-tab consistency

### Recommendations for next phase
-
```

---

## Test strategy

| Phase | Automated | Manual |
|-------|-----------|--------|
| 1 | Import + Photo Scan tab persistence; locale completeness; preview pane no back UI | Restart spot-check (optional; covered by serialization tests) |
| 2 | Lazy browser; settings round-trip; column/hierarchy navigation | Large folder scroll (release QA) |
| 3 | TabSettings + session prefs serialization; `LocaleTranslationQualityTest` | Locale read-through in non-English UI |
| 4 | Keyboard shortcuts, a11y component tests | VoiceOver sample (release QA) |

---

## Timeline sketch

| Phase | Duration |
|-------|----------|
| 0 | 0.5 d |
| 1 | 1–2 d |
| 2 | 3–5 d |
| 3 | 2–3 d |
| 4 | 2–4 d (optional) |

Each phase includes ~0.5 d for usability + retrospective.

---

## Phase log

| Phase | Started | Completed | Retro notes |
|-------|---------|-----------|-------------|
| 0 | 2026-07-27 | 2026-07-28 | Baseline documented; automated tests stand in for restart metrics; large-folder scroll deferred to release QA. |
| 1 | 2026-07-27 | 2026-07-28 | Import + Photo Scan Import use `SessionPreferencesEffect`; back picker removed; `FIELD_SOURCE_VALUE` localized. Usability table verified via tests. |
| 2 | 2026-07-27 | 2026-07-28 | LazyColumn browser; session prefs; command bar output; Undo/Redo icons; auto-orient gate. Column navigation unit tests added. |
| 3 | 2026-07-27 | 2026-07-28 | Reorganize + Duplicate Scanner session prefs + settings flow; Photo Scan CTA i18n; Advanced Settings helpers; English fallback re-translation (18 locales). |
| 4 | 2026-07-27 | 2026-07-28 | Global shortcut dialog; tab keys; a11y on duplicate cards; dead code removed. Keyboard + setup Enter covered by unit tests. |

---

## Manual QA checklist (2026-07-28)

Automated verification complete (`./gradlew test` — 1536+ tests). Items below note human follow-up only where automation cannot substitute.

### Settings persistence (all tabs)

- [x] Import tab: paths + org/dedup/auto-orient survive restart — `TabSettingsTest`, `MediaImportViewModelTest`, `SessionPreferencesEffectTest`
- [x] Photo Scan Import tab: paths + config survive restart — `PhotoScanImportTabSettingsTest`, `SessionPreferencesEffect` in screen
- [x] Reorganize tab: folder + config survive restart — `ReorganizeSessionPreferencesTest`
- [x] Duplicate Scanner tab: folder + toggles survive restart — `DuplicateScannerSessionPreferencesTest`
- [x] Metadata editor: output mode, directory, subfolders survive restart — `MetadataEditorSessionPreferencesTest`

### Trust & honesty

- [x] No Add Back / back picker in metadata editor command bar — `MetadataEditorCommandBarComponentTest`
- [x] Non-GPS fields show “Source: …” hint — `FIELD_SOURCE_VALUE` in all locales

### Metadata editor scale

- [x] LIST / HIERARCHY / COLUMN navigation logic — hierarchy + column unit tests
- [ ] 2000-image folder scroll without multi-second freeze — **release QA (manual)**

### i18n

- [x] Phase 3/4 UI keys translated in all bundled locales — `LocaleTranslationQualityTest`
- [ ] Spot-check German or Japanese UI reads naturally — **release QA (manual)**

### Keyboard & a11y

- [x] Ctrl+1–5, Ctrl+/ help, Enter on setup screens — `AppKeyboardShortcutsTest`, `SetupScreenKeyboardTest`
- [x] Duplicate Keep badge is non-interactive — `DuplicateGroupCardComponentTest`
- [ ] VoiceOver on Duplicate Scanner + metadata browser — **release QA (manual)**
