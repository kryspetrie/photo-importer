# CLI Feature Guide

> **Status:** ✅ Implemented (Phases 1–4)  
> **Updated:** 2026-07-18

## Overview

Petrie File Importer provides a full command-line interface for automation, scripting, and headless operation. The CLI supports all major features available in the GUI: importing, duplicate detection, photo scanning, watch folders, and library reorganization.

## Entry Point

The application detects CLI mode automatically from the first argument:

```bash
# These all work without a --cli prefix:
photo-import import /source /destination
photo-import scan ./scans/ -o ./crops/
photo-import watch ~/Incoming ~/Library
photo-import --version
photo-import --help

# Legacy --cli prefix still works:
photo-import --cli import /source /destination
```

Smart dispatch (in `PetrieFileImporterApp.main()`) recognizes these subcommands: `import`, `check-duplicates`, `reorganize`, `undo`, `check-journals`, `scan`, `watch`. Any argument matching `--version`, `-V`, `--help`, or `-h` also triggers CLI mode.

Koin DI is initialized before CLI invocation, so all services (ONNX models, export pipeline) are injectable. The manual DI fallback has been removed.

## Global Options

| Option | Description |
|--------|-------------|
| `--version`, `-V` | Print version and exit |
| `--verbose`, `-v` | Enable verbose output |
| `--quiet`, `-q` | Suppress non-error output |
| `--help`, `-h` | Show help |

## Commands

### `import` — Import images

```bash
photo-import import /source /destination [OPTIONS]
```

| Option | Default | Description |
|--------|---------|-------------|
| `--dry-run` | false | Preview without copying |
| `--no-recursive` | false | Do not scan subdirectories |
| `--folder-pattern` | `{yyyy-MM-dd}` | Folder hierarchy pattern |
| `--filename-pattern` | `{original}` | Filename pattern |
| `--no-verify-hash` | false | Skip file verification after copy |
| `--delete-source` | false | Delete source files after copy |

### `check-duplicates` — Find duplicates

```bash
photo-import check-duplicates /source [OPTIONS]
```

| Option | Default | Description |
|--------|---------|-------------|
| `--no-recursive` | false | Do not check subdirectories |
| `--method` | `hash` | Detection method: `hash` (exact) or `visual` (perceptual) |

### `scan` — Detect and extract photos

```bash
photo-import scan SOURCE [SOURCE...] [OPTIONS]
```

Detects individual photos in scanned images, applies perspective correction, and exports them.

| Option | Default | Description |
|--------|---------|-------------|
| `-o`, `--output` | `.` | Output directory for extracted photos |
| `-r`, `--recursive` | true | Scan directories recursively |
| `--no-recursive` | false | Do not scan directories recursively |
| `--preset` | `corner_refine` | Scan preset: `fast`, `pose_refine`, or `corner_refine` |
| `--crop` | `warp-stretch` | Correction strategy: `simple`, `warp`, or `warp-stretch` |
| `--crop-margin` | 0.02 | Crop margin fraction (0.0–0.1) |
| `--rotation` | 0 | Output rotation in degrees: 0, 90, 180, 270 |
| `--quality` | 90 | JPEG output quality (1–100) |
| `--coords` | — | Output coordinates: `json` or `text` (goes to stdout) |
| `--no-image` | false | Skip image export; only detect and output coordinates |
| `--dry-run` | false | Preview detection without writing files |
| `-n`, `--limit` | 0 | Maximum number of source images to process (0 = all) |

**Scan Presets:**

| Preset | Detection | Correction | Use Case |
|--------|-----------|------------|----------|
| `fast` | CV (classical) | Crop only | Flat scans with clearly separated photos |
| `pose_refine` | YOLO bounding box | Perspective | Angled photos |
| `corner_refine` | YOLO pose + corner refinement | Perspective | Full pipeline, matches GUI default |

**Coordinate Output:**

The `--coords` flag outputs detected photo corner coordinates to stdout (all other output goes to stderr), enabling piping:

```bash
photo-import scan photo.jpg --coords json --no-image | jq .
photo-import scan photos/ --coords text
```

JSON format:
```json
{
  "source": "photo.jpg",
  "photos": [
    {
      "id": "abc123",
      "corners": [
        {"x": 120.5, "y": 80.2},
        {"x": 890.1, "y": 75.9},
        {"x": 895.3, "y": 640.7},
        {"x": 115.8, "y": 645.1}
      ]
    }
  ]
}
```

**Correction Strategies:**

| CLI Value | Strategy | Description |
|-----------|----------|-------------|
| `simple` | `CROP` | Axis-aligned crop — best for nearly-rectangular photos |
| `warp` | `CROP_AND_ROTATE` | Crop + rotation — best for slightly rotated photos |
| `warp-stretch` | `PERSPECTIVE` | Full perspective transform — best for skewed/trapezoidal photos |

### `watch` — Monitor and auto-import

```bash
photo-import watch SOURCE DESTINATION [OPTIONS]
```

Monitors a directory for new image files and automatically imports them.

| Option | Default | Description |
|--------|---------|-------------|
| `--cooldown` | 5000 | Cooldown in ms between import batches |
| `--no-recursive` | false | Do not watch subdirectories |
| `--no-verify-hash` | false | Skip file verification after copy |
| `--delete-after-import` | false | Delete source files after import |
| `--profile` | — | Import profile name |

Outputs status to stderr. Press Ctrl+C to stop.

### `reorganize` — Reorganize library

```bash
photo-import reorganize /library/path [OPTIONS]
```

| Option | Default | Description |
|--------|---------|-------------|
| `-d`, `--destination` | source | Destination folder |
| `--dry-run` | false | Preview changes without applying |
| `--rename-only` | false | Only rename files, don't move |
| `--mode` | `move` | Operation mode: `move` or `copy` |
| `--folder-pattern` | `{yyyy-MM-dd}` | Folder hierarchy pattern |
| `--filename-pattern` | `{original}` | Filename pattern |
| `--preserve-original-name` | false | Preserve original filename |
| `--conflict-resolution` | `rename` | Conflict resolution: `rename` or `replace` |

### `undo` — Undo reorganization

```bash
photo-import undo JOURNAL_PATH
```

Restores files from a previous `reorganize` operation using the saved journal.

### `check-journals` — List reorganization journals

```bash
photo-import check-journals
```

Lists available undo journals from previous reorganization operations.

## Platform Wrapper

A shell wrapper script (`src/main/resources/bin/photo-import`) is provided for cross-platform CLI access. It detects the OS and forwards arguments to the appropriate binary.

```bash
# Build the distribution
./gradlew packageDistributable

# Set the home directory
export PHOTO_IMPORTER_HOME=/path/to/distribution

# Run CLI commands
photo-import scan ./scans/ -o ./crops/
```

## Implementation Files

| File | Purpose |
|------|---------|
| `PetrieFileImporterApp.kt` | Smart CLI dispatch in `main()` |
| `cli/PhotoImportCli.kt` | Top-level CLI with import, check-duplicates, global options |
| `cli/ScanCommand.kt` | Photo scan command with presets and coordinate output |
| `cli/ScanPresets.kt` | Preset → `PhotoScanConfiguration` mapping |
| `cli/OutputFormatter.kt` | Coordinate output formatting (JSON/text) |
| `cli/WatchCommand.kt` | Headless watch folder command |
| `cli/ReorganizeCommand.kt` | Reorganize, undo, check-journals commands |
| `bin/photo-import` | Cross-platform shell wrapper script |