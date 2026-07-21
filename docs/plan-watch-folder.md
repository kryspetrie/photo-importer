# Watch Folder Feature

> **Status:** ✅ Implemented  
> **Updated:** 2026-07-18

## Overview

The Watch Folder feature monitors source directories for new image files and automatically imports them using configured import profiles. It supports multiple simultaneous watches, persistent configuration, and full CRUD management from the UI.

## Architecture

### Components

| Component | Layer | Responsibility |
|-----------|-------|----------------|
| `WatchFolderConfig` | Domain | `@Serializable` data class: id, paths, cooldown, recursive, enabled, autoStart |
| `WatchFolderStatus` | Domain | `@Serializable` data class: configId, isWatching, importCount, lastImportTime, errors |
| `WatchFolderService` | Application | Monitors a single folder using Java NIO `WatchService`; detects files, triggers imports |
| `WatchFolderManager` | Application | Orchestrates multiple `WatchFolderService` instances; CRUD on configs; persists via `SettingsPort` |
| `WatchFolderManagement.kt` | UI | Composable list view of all watch configs with status badges |
| `WatchFolderConfigDialog.kt` | UI | Dialog for adding/editing watch folder configurations |

### Data Flow

```
UI (WatchFolderManagement)
  ↕ StateFlow<Map<String, WatchFolderStatus>>
WatchFolderManager
  ↕ manages lifecycle
WatchFolderService (per config)
  ↕ Java NIO WatchService
FileSystem (source directory)
```

### Persistence

Watch configs are stored in `AppSettings.watchConfigs: List<WatchFolderConfig>`, which is automatically serialized to JSON via `SettingsAdapter`. Any config change (add, update, remove) is persisted immediately through `SettingsPort`.

### Auto-Start

`WatchFolderManager.startAllAutoStart()` is called from `PetrieFileImporterApp.main()` after Koin initialization. All configs with `autoStart && enabled` are started automatically.

### Error Recovery

`WatchFolderService` pauses itself after 3 consecutive import errors, surfacing `lastError` in the status. The user can restart the watch from the UI.

## CLI Support

The `watch` subcommand provides headless operation:

```bash
photo-import watch ~/Pictures/Incoming ~/Library/Photos --cooldown 3000
photo-import watch ~/Incoming ~/Library --no-recursive --delete-after-import
```

See [CLI Feature Guide](CLI_FEATURE_GUIDE.md) for full CLI documentation.