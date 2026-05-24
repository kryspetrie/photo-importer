# Architecture

## Overview

Petrie File Importer follows a **Hexagonal Architecture** (Ports & Adapters) with four layers:

```
┌─────────────────────────────────────────────────────────────┐
│                    UI Layer (Compose Desktop)                 │
│  Screens: MediaImport, Reorganize, Duplicates, PhotoScan    │
│  Theme, Components, Navigation                               │
├─────────────────────────────────────────────────────────────┤
│                    Application Layer                          │
│  ImportService · ReorganizeService · DuplicateScannerService │
│  WatchFolderService · ScanService · PhotoScanExportService   │
├─────────────────────────────────────────────────────────────┤
│                      Domain Layer                             │
│  Models: ImportConfiguration · ImageFile · ReorganizeOp     │
│  Ports: ImageRepositoryPort · SettingsPort · NamingPort      │
│         DeduplicationPort · HashCachePort · DevicePort       │
├─────────────────────────────────────────────────────────────┤
│                   Infrastructure Layer                        │
│  Adapters: ImageRepositoryAdapter · SettingsAdapter          │
│            NamingAdapter · DeduplicationAdapter · etc.       │
│  Platform: OS detection · File dialogs · FFmpeg resolution   │
└─────────────────────────────────────────────────────────────┘
```

## Dependency Graph

```
ImportService
├── ImportScanner
│   ├── ImageRepositoryPort  (ImageRepositoryAdapter)
│   └── HashCachePort         (HashCacheAdapter)
├── ImportExecutor
│   ├── ImageRepositoryPort
│   └── NamingPort            (NamingAdapter)
├── DeduplicationPort         (DeduplicationAdapter)
├── NamingPort                (NamingAdapter)
└── DevicePort                (DeviceAdapter)

ReorganizeService
├── ImageRepositoryPort
└── NamingPort

DuplicateScannerService
├── ImageRepositoryPort
├── DeduplicationPort
└── HashCachePort

WatchFolderService
└── ImportService

ScanService
├── ImageRepositoryPort
└── HybridCornerDetector
    └── RectangleDetector

PhotoScanExportService
└── ImageRepositoryPort
```

## Dependency Injection (Koin)

All dependencies are registered as singletons in `AppModule.kt` and resolved at runtime.

### In Composables
```kotlin
val service = koinImport<ImportService>()
```

### In Test Classes
```kotlin
val service: ImportService by inject()
```

## Key Design Decisions

1. **Domain layer is pure Kotlin** — no framework dependencies (except kotlinx.serialization for
   profile persistence). Infrastructure details live behind port interfaces.

2. **Ports are interfaces in `domain/port/`** — Adapters in `infrastructure/adapter/` implement them.
   This allows swapping implementations (e.g., SQLite → PostgreSQL) without touching domain logic.

3. **UI screens are thin composables** — They render state and delegate logic to application services
   via Koin injection. Business logic should not live in composables.

4. **Cross-platform support via `Platform` object** — All OS-conditional code (file dialogs, device
   ejection, FFmpeg resolution, system viewer) is centralized in `infrastructure/adapter/Platform.kt`.

5. **Settings persistence via `SettingsPort`** — UI screens load/save settings through the port
   interface, not directly. `SettingsAdapter` handles JSON file I/O.

6. **Photo Scan wizard uses `StateFlow`** — The `PhotoScanWizardState` class manages all wizard
   state via Kotlin `StateFlow` properties, enabling reactive UI updates without callbacks.