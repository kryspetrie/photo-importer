# Plan 2: Comprehensive CLI with Photo-Scan Features

> **Status:** ✅ Implemented (Phases 1–4)  
> **Updated:** 2026-07-18

## Scope

Expose major UI features from the terminal. No feature parity with photo-pose-detector required. Metadata editing and complex file renaming are out of scope.

| Feature | In CLI? | Notes |
|---------|---------|-------|
| Import (scan → copy with patterns/dedup) | ✅ | `import` command |
| Check duplicates (hash/visual) | ✅ | `check-duplicates` command |
| Reorganize / Undo | ✅ | `reorganize`, `undo`, `check-journals` commands |
| Photo scan (detect → crop → export) | ✅ | `scan` command with presets |
| Watch folder headless | ✅ | `watch` command |
| Presets (PhotoScanProfile) | ✅ | `--preset` flag on scan |
| Correction strategy | ✅ | `--crop` flag on scan |
| Crop margin | ✅ | `--crop-margin` flag on scan |
| Rotation | ✅ | `--rotation` flag on scan |
| Coordinate output | ✅ | `--coords json` or `--coords text` on scan |
| Smart CLI dispatch | ✅ | Auto-detect subcommands, no `--cli` prefix needed |
| Version flag | ✅ | `--version` / `-V` |
| Verbosity control | ✅ | `--verbose` / `-v`, `--quiet` / `-q` |
| Platform wrapper | ✅ | `bin/photo-import` shell script |

---

## Phase 1: Cross-Platform CLI Entry Point ✅

**Implemented in `PetrieFileImporterApp.kt` and `cli/PhotoImportCli.kt`**

- Smart `main()` dispatch detects CLI subcommands and delegates to CLI mode automatically
- Koin initialized before CLI invocation (no more manual DI fallback)
- `--version` / `-V` flag via Clikt's `versionOption()`
- `--verbose` / `-v` and `--quiet` / `-q` global flags
- Platform wrapper script at `src/main/resources/bin/photo-import`

## Phase 2: Photo Scan Command ✅

**Implemented in `cli/ScanCommand.kt`, `cli/ScanPresets.kt`, `cli/OutputFormatter.kt`**

- `scan` command with source (file or directory), output, recursive options
- Three presets: `fast`, `pose_refine`, `corner_refine`
- Correction strategy via `--crop` (simple/warp/warp-stretch)
- Crop margin via `--crop-margin` (parameterized in `PhotoScanConfiguration.cropMarginFraction`)
- Rotation via `--rotation`
- Coordinate output via `--coords json` or `--coords text` (stdout for piping)
- `--no-image` for detection-only mode
- `--dry-run` for preview mode
- `--limit` for batch size control
- Progress output to stderr; coordinates to stdout

## Phase 3: Watch Command (Headless) ✅

**Implemented in `cli/WatchCommand.kt`**

- `watch SOURCE DESTINATION` with configurable cooldown, recursion, hash verification
- Uses `WatchFolderManager` for lifecycle management
- Status updates printed to stderr
- Graceful shutdown via Ctrl+C (shutdown hook removes config)

## Phase 4: Polish ✅

**Implemented across all CLI files**

- Manual DI fallback removed; Koin always initializes before CLI
- `--verbose` / `-v` and `--quiet` / `-q` on top-level `PhotoImportCli`
- Clean error handling and user-friendly output formatting

---

## Implementation Files

| File | Phase | Purpose |
|------|-------|---------|
| `PetrieFileImporterApp.kt` | 1 | Smart CLI dispatch (auto-detect subcommands) |
| `cli/PhotoImportCli.kt` | 1, 4 | Top-level CLI with --version, --verbose, --quiet |
| `cli/ScanCommand.kt` | 2 | Photo scan command |
| `cli/ScanPresets.kt` | 2 | Preset → PhotoScanConfiguration mapping |
| `cli/OutputFormatter.kt` | 2 | Coordinate output (JSON/text) |
| `cli/WatchCommand.kt` | 3 | Headless watch folder command |
| `cli/ReorganizeCommand.kt` | 1 | Reorganize, undo, check-journals commands |
| `bin/photo-import` | 1 | Cross-platform shell wrapper |
| `PhotoScanConfiguration.kt` | 2 | Added `cropMarginFraction` field |
| `PhotoScanExportService.kt` | 2 | Uses `config.cropMarginFraction` instead of hardcoded 0.02 |