# Petrie Image Importer — Suggested Features

A prioritized list of features that would make this a robust, professional-grade media import and management tool.

---

## High Priority — Core Workflow Enhancements

### 1. Reorganize Existing Folders
Allow users to apply folder/filename patterns to an **already-organized** library. This is one of the most requested features in media management tools.

- **Apply patterns retroactively**: Select an existing media folder and reorganize files into a new structure based on the current folder/filename pattern settings (date-based, camera-based, etc.).
- **Rename files in-place**: Rename files using EXIF/date patterns without moving them — useful for standardizing filenames across a library.
- **Dry-run preview**: Show a complete before/after mapping before any files are moved. Highlight conflicts and duplicates.
- **Undo support**: Track moves in a journal file so a reorganization can be fully reversed.
- **Flatten nested folders**: Option to flatten deeply nested folder structures into a uniform layout.

### 2. Sidecar File Handling
Cameras and editing tools produce sidecar files (`.xmp`, `.thm`, `.lrv`, `.aae`) that should travel with their parent file.

- Auto-detect sidecars by matching base filename (e.g. `IMG_0001.xmp` goes with `IMG_0001.arw`).
- Keep sidecars together during import, reorganization, and deduplication.
- Option to skip importing sidecars entirely.

### 3. RAW+JPEG Pair Management
Many photographers shoot RAW+JPEG simultaneously. Treat these as linked pairs.

- Detect RAW+JPEG pairs by filename/timestamp match.
- Import both to the same folder, or import only RAW / only JPEG per user preference.
- When deduplicating, recognize pairs and let the user choose which to keep (or both).

### 4. Import History & Logging
Track every import operation for auditability and safety.

- Persistent log of all imports: source, destination, timestamp, file count, hash.
- "What was imported last?" quick view.
- Export import log to CSV.
- Detect if a source card has already been fully imported (all files match destination hashes).

---

## Medium Priority — User Experience

### 5. Drag-and-Drop Support
Allow users to drag folders or files from the OS file manager onto the application window to set source/destination paths.

### 6. Watch Folder / Auto-Import
Designate a folder (or a mounted volume) as a "watch" folder. When new files appear, automatically trigger an import with a specific profile.

- Useful for always-on workstations (studio environments, photo booths).
- Configurable cooldown to avoid triggering during ongoing file transfers.

### 7. Progress Notification & Sound
- System notification when an import completes (especially for large imports).
- Optional completion sound.
- Estimated time remaining during import.

### 8. Multi-Source Import
Allow importing from multiple source folders in a single operation (e.g. two SD cards at once).

- Merge all sources into a single import queue with unified deduplication.

### 9. Filter & Sort in Selection View
- Filter by file type (photos only, videos only, RAW only).
- Filter by date range, camera model, or file size.
- Sort by date, name, size, type.
- Search by filename.

### 10. Batch Profile Application
Select multiple profiles and run them sequentially — e.g. "import from Camera A to Photos folder, then from Camera B to a different folder."

---

## Medium Priority — Advanced Features

### 11. Duplicate Library Scanner
A standalone mode to scan an **existing** library for duplicates (not just during import).

- Find exact hash duplicates across the entire library.
- Find visual/resolution duplicates using SURF or perceptual hashing.
- Interactive resolution: keep highest resolution, keep RAW over JPEG, keep newest, etc.
- Safe deletion: move duplicates to a "review" folder instead of permanent delete.

### 12. EXIF Editor / Batch Metadata Update
- Set or correct date/time on files with missing or wrong EXIF data (common with scanned photos).
- Batch-set camera model, artist, copyright fields.
- Shift all dates by a fixed offset (e.g. camera clock was wrong by 2 hours).
- GPS geotagging from a GPX track file.

### 13. Thumbnail / Preview Generation for RAW Files
Currently, RAW file thumbnails may not render (ImageIO doesn't support all RAW formats natively).

- Extract embedded JPEG previews from RAW files for faster thumbnail display.
- Use dcraw/LibRaw via JNI or a bundled binary for full RAW rendering.
- Cache generated thumbnails to disk for instant re-display.

### 14. Video Thumbnail Extraction
Currently, videos show a placeholder icon. Extract an actual frame for preview.

- Use FFmpeg (bundled or system-installed) to extract a frame at ~1 second.
- Cache the extracted frame for future display.
- Show video duration overlay on thumbnails (already implemented in placeholder).

### 15. Verify Library Integrity
Scan an existing library and compare stored hashes against current file contents.

- Detect corrupted files (bit rot).
- Detect files that have been modified outside the application.
- Generate an integrity report.

---

## Lower Priority — Polish & Distribution

### 16. Custom Themes & Appearance
- Light/dark/system theme toggle (partially implemented).
- Accent color customization.
- Font size adjustment for accessibility.

### 17. Keyboard Shortcuts
- Global shortcuts for common actions: start import, open settings, select all/none.
- Arrow key navigation in the grid view.
- Spacebar to toggle selection.

### 18. Localization / i18n
- Externalize all user-facing strings.
- Support for multiple languages.

### 19. Native Packaging & Installer
- macOS `.dmg` with drag-to-Applications.
- Windows `.msi` installer.
- Linux `.deb` / `.AppImage`.
- Use `jpackage` or Compose Multiplatform's native distribution support.
- Code signing for macOS/Windows.

### 20. Plugin / Extension System
Allow third-party or user-defined extensions for:

- Custom naming patterns (e.g. pattern functions, not just placeholders).
- Custom deduplication strategies.
- Post-import hooks (e.g. auto-upload to cloud, trigger Lightroom import).

---

## Speculative / Long-Term

### 21. Cloud Storage Integration
- Import directly from or to cloud providers (Google Drive, Dropbox, S3).
- Sync profiles between machines via cloud storage.

### 22. Face / Object Detection Tagging
- Use ML models to auto-tag imported photos by detected faces or objects.
- Organize into "People" or "Scenes" folders.

### 23. Map View
- Display imported photos on a map based on GPS coordinates.
- Cluster nearby photos visually.

### 24. Timeline View
- A chronological timeline view of the entire library with scrubbing.
- Quick-jump to specific dates.

---

## Summary of Quick Wins

| Feature | Effort | Impact |
|---------|--------|--------|
| Reorganize existing folders | Medium | Very High |
| Sidecar file handling | Low | High |
| RAW+JPEG pair management | Low | High |
| Import history log | Low | Medium |
| Drag-and-drop | Low | Medium |
| Filter/sort in selection | Low | High |
| Duplicate library scanner | Medium | High |
| Video thumbnail extraction | Medium | Medium |
| Native packaging | Medium | High |
