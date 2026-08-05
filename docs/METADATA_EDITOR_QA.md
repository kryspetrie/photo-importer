# Metadata Editor — Manual QA Checklist

Use this checklist before and after major UI changes. Record results at **1440×900** and **1920×1080**, with **Comfortable** and **Compact** density.

## Setup

- [ ] Launch app at minimum window size (1024×768)
- [ ] Switch to Metadata Editor tab
- [ ] Verify side rail navigation at wide width; bottom bar at narrow width (View → Navigation Style → Automatic)

## Load folder

- [ ] Open folder with 10+ images
- [ ] Set output mode (Overwrite / Save new) and output folder on landing before opening
- [ ] Recent paths list updates
- [ ] File browser shows files in default view mode (Icons)
- [ ] Switch view modes: Column, List, Hierarchy, Icons — each renders correctly
- [ ] Arrow keys move selection; Enter opens focused folder in List/Icons
- [ ] Double-click folder enters; Up bar returns to parent

## Single-file edit

- [ ] Select one photo — preview visible, metadata form populated
- [ ] Edit title, keywords, location — undo/redo works
- [ ] Save overwrites or creates copy per output mode
- [ ] Clear resets to loaded config in single-edit mode

## Multi-edit

- [ ] Enable Multi mode
- [ ] Select 1 photo — preview **visible**, Apply available
- [ ] Select 2+ photos — preview placeholder, batch fields shown
- [ ] Apply writes to all selected photos only
- [ ] Toggle selection keeps primary index in sync

## Tagging

- [ ] Tag faces opens full Tag Editor overlay (drag, resize, auto-detect)
- [ ] Face regions visible on preview when overlay closed
- [ ] Face names merge into subjects without wiping manual entries
- [ ] Location picker works in multi-edit (buffer then Apply)

## Keyboard shortcuts

- [ ] ↑/↓/←/→ — file navigation
- [ ] Enter — open focused folder
- [ ] ⌘↵ — Apply multi-edit
- [ ] ⌘L — Location picker
- [ ] ⌘T — Toggle face tagging
- [ ] ⌘F — Focus keywords
- [ ] ⌘B — Toggle browser drawer (narrow layout)

## Layout (desktop)

- [ ] Preview uses ≥45% of editor area at 1440×900
- [ ] Browser pane resizable; width persists after restart
- [ ] Narrow layout (<1100dp): browser drawer with hamburger/⌘B
- [ ] Ultra-wide (≥1600dp): three-pane layout when applicable
- [ ] Scrollbars visible and mouse wheel scrolls file lists

## Regression

- [ ] `./gradlew test` passes
- [ ] Locale completeness test passes
- [ ] No hardcoded GPS hint strings in bulk editor

## Baseline screenshots (Phase 0)

| Size | Density | Screenshot path |
|------|---------|-----------------|
| 1440×900 | Comfortable | _(capture before polish)_ |
| 1920×1080 | Comfortable | _(capture before polish)_ |
