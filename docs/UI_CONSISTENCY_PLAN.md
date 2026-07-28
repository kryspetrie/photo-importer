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

## Phase 0 — Baseline & measurement

**Goal:** Repeatable before/after baseline. No user-visible changes.

### Work

- Document current behavior: Import config reset, metadata output reset, browser perf, back-image visibility, undo icons, `autoOrientInMetadataEditor`
- Prepare fixtures: ~50, ~500, ~2000+ images
- Usability script v1 (task list below)
- Metrics: time-to-open folder, scroll jank (1–5), settings stick after restart

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
| 1.1 | Import tab persists `importTabSettings.configuration` | ✅ |
| 1.2 | Hide metadata editor back-of-photo UI | ✅ |
| 1.3 | Fix `MetadataField` source hint (`FIELD_SOURCE_VALUE`) | ✅ |

### Exit criteria

- Import org/dedup/auto-orient settings survive restart
- No Add Back / back picker in metadata editor
- Non-GPS fields show generic “Source: …” hint
- `./gradlew test` green

### Usability analysis (Phase 1)

| Task | Success |
|------|---------|
| Import: set auto-orient + dedup, restart | Settings retained |
| Metadata editor: read field hints | Natural wording |
| Preview pane: scan for back controls | None visible |

### Retrospective → Phase 2

- Persistence pattern: reuse `TabSettings` everywhere?
- Output controls: landing vs command bar preference?
- Large-folder size for Phase 2 perf test (500 vs 2000)?

---

## Phase 2 — Metadata editor scale & session UX

**Goal:** Large libraries; session prefs; clearer command bar.

| # | Item |
|---|------|
| 2.1 | Virtualize LIST / COLUMN / HIERARCHY browser views |
| 2.2 | Persist output mode, output directory, include-subfolders |
| 2.3 | Dedupe output controls (landing **or** command bar — pick in Phase 1 retro) |
| 2.4 | Undo/Redo icons (not rotation icons) |
| 2.5 | Wire `autoOrientInMetadataEditor` setting |

### Exit criteria

- 2000-file folder scrollable without multi-second freeze on view switch
- Output prefs survive restart
- Auto-rotate button respects setting

### Usability analysis (Phase 2)

- Large folder: switch LIST → ICONS → COLUMN
- Output prefs after restart
- Undo/redo icon clarity (1–5)
- Auto-orient toggle discoverability

### Retrospective → Phase 3

- COLUMN view worth keeping at scale?
- LRU cap on in-memory thumbnails?
- Keyboard shortcuts: defer or promote?

---

## Phase 3 — Cross-tab parity & i18n

| # | Item |
|---|------|
| 3.1 | Reorganize session persistence (`TabSettings` or dedicated fields) |
| 3.2 | Duplicate Scanner session persistence |
| 3.3 | Localize Photo Scan Import CTA |
| 3.4 | Localize Advanced Settings helper strings |
| 3.5 | Remove dead `onImportModeChange` stub |

### Usability analysis (Phase 3)

- Reorganize/Duplicates: folder + config after restart
- Locale switch on Photo Scan Import CTA
- Cross-tab continuity (Import → Reorganize → Metadata editor)

### Retrospective → Phase 4

- TabSettings schema final shape
- Move thumbnail cache controls to app menu?

---

## Phase 4 — Discoverability, a11y, polish (flexible)

Pick based on Phase 2–3 retros.

| # | Item |
|---|------|
| 4.1 | Global keyboard shortcut help |
| 4.2 | Main tab keyboard basics (optional) |
| 4.3 | Targeted accessibility (`contentDescription`, duplicate Keep chip) |
| 4.4 | Light visual consistency (titles, padding, spinner theme) |
| 4.5 | Remove dead `metadataEditorLayoutMode`; optional `ScanScreen` cleanup |

### Final program retrospective

- Outcomes vs goals table
- Usability severity trend by phase
- Next quarter backlog

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
| 1 | Import config persistence; locale completeness; preview pane no back UI | Restart checklist |
| 2 | Lazy browser; settings round-trip | Large folder scroll |
| 3 | TabSettings serialization | Cross-tab restart |
| 4 | a11y spot checks | VoiceOver sample |

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
| 0 | | | |
| 1 | 2026-07-27 | 2026-07-27 | Import tab uses same `LaunchedEffect` persistence as photo scan; back picker removed from metadata editor screen; `FIELD_SOURCE_VALUE` added to all locales. Manual restart checklist pending. |
| 2 | | | |
| 3 | | | |
| 4 | | | |
