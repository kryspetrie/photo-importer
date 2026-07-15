# Architecture

## Overview

Petrie File Importer follows **Hexagonal Architecture** (Ports & Adapters) with four layers:

```
┌─────────────────────────────────────────────────────────────────┐
│                     UI Layer (Compose Desktop)                   │
│  Screens · Wizards · Components · Theme                         │
│  State holders: PhotoScanWizardState, MetadataEditState        │
├─────────────────────────────────────────────────────────────────┤
│                     Application Layer                            │
│  ImportService · ReorganizeService · DuplicateScannerService    │
│  WatchFolderService · ScanService · PhotoScanExportService      │
│  LocationSearchService · FaceRegionTransformer                  │
│  PerspectiveCorrectionService · ImportScanner · ImportExecutor  │
├─────────────────────────────────────────────────────────────────┤
│                       Domain Layer                              │
│  Models: data classes, enums, sealed classes                    │
│  Ports: interfaces defining what the application needs           │
│  No framework or I/O dependencies                               │
├─────────────────────────────────────────────────────────────────┤
│                    Infrastructure Layer                          │
│  Adapters: port implementations with real I/O                    │
│  PhotoScan: detection & YOLO pipeline                           │
│  Wizard: UI state management                                     │
│  Platform utilities: OS detection, file dialogs                  │
└─────────────────────────────────────────────────────────────────┘
```

## Dependency Rule

Dependencies point inward only:

```
UI → Application → Domain ← Infrastructure
```

- **Domain** imports nothing from other layers.
- **Application** imports only from Domain (plus `toProcessedImage`/`toBufferedImage` boundary converters from Infrastructure).
- **Infrastructure** implements Domain ports and may import from Application.
- **UI** imports from Application and Domain. It accesses Infrastructure only through Domain ports (via Koin DI) or through documented boundary exceptions (see below).

## Packages

```
org.kryspetrie.fileimport/
├── PetrieFileImporterApp.kt        # Application entry point (main)
│
├── application/                     # Use cases & orchestration
│   ├── ImportService.kt            # Core import workflow
│   ├── ImportScanner.kt            # Source directory scanning
│   ├── ImportExecutor.kt           # File copy & verification
│   ├── ReorganizeService.kt        # Library reorganization
│   ├── DuplicateScannerService.kt  # Standalone duplicate finder
│   ├── WatchFolderService.kt       # Auto-import from watched folder
│   ├── ScanService.kt              # Photo scan orchestration
│   ├── PhotoScanExportService.kt   # Photo export pipeline
│   ├── PerspectiveCorrectionService.kt  # Homography correction
│   ├── FaceRegionTransformer.kt    # Face region coordinate mapping
│   ├── LocationSearchService.kt    # Geocoding search
│   ├── MetadataWritingService.kt   # Standalone metadata writing (bulk editor)
│   ├── metadata/                   # Metadata editor application services
│   │   ├── MetadataEditService.kt        # Save/write orchestration (delegates to MetadataWritingService)
│   │   ├── MetadataEditUndoService.kt    # Undo/redo backup & restore
│   │   └── MetadataEditJournalRepository.kt  # Journal persistence (JSON)
│   └── export/                     # Export sub-functions
│       ├── ExifMetadataWriter.kt
│       ├── FilenameResolver.kt
│       ├── ImageTransformer.kt
│       ├── IptcMetadataWriter.kt
│       └── XmpMetadataWriter.kt
│
├── cli/                            # Command-line interface (Clikt)
│   ├── PhotoImportCli.kt
│   └── ReorganizeCommand.kt
│
├── di/                             # Dependency injection (Koin)
│   └── AppModule.kt               # All service & port registrations
│
├── domain/
│   ├── model/                      # Pure Kotlin data classes & enums
│   │   ├── AppSettings.kt         # User preferences & window state
│   │   ├── ImportConfiguration.kt  # Import settings & naming patterns
│   │   ├── ImageFile.kt            # File model + ImageFileType enum
│   │   ├── PhotoScanConfiguration.kt  # Photo scan metadata overrides
│   │   ├── PhotoScanModels.kt      # DetectedPhoto, PhotoCorner, RotationAngle
│   │   ├── PhotoScanExportModels.kt   # Export result types
│   │   ├── ProcessedImage.kt       # Domain image abstraction (no AWT)
│   │   ├── ScanProgress.kt         # Duplicate scan progress state
│   │   ├── WatchFolderConfig.kt    # Watch folder configuration
│   │   ├── WatchFolderStatus.kt    # Watch folder status
│   │   ├── geometry/               # BoundingBox, Point, Corner, BoundingBoxList
│   │   └── ...                    # ~30 more model files
│   └── port/                       # Interfaces (hexagonal ports)
│       ├── ImageRepositoryPort.kt  # File scanning, copying, hashing
│       ├── SettingsPort.kt         # JSON settings persistence
│       ├── NamingPort.kt          # Path & filename generation
│       ├── DeduplicationPort.kt    # Duplicate detection strategies
│       ├── HashCachePort.kt        # Disk-backed hash cache
│       ├── FileSystemPort.kt       # File system operations
│       ├── DevicePort.kt          # Camera/device detection
│       ├── ImportHistoryPort.kt   # Import history persistence
│       ├── GeocodingPort.kt       # Location search (Nominatim)
│       ├── PhotoScanDetectorPort.kt # Photo detection in scans
│       ├── PhotoScanExportPort.kt  # Photo export with corrections
│       ├── PerspectiveCorrectionPort.kt  # Homography/perspective correction
│       ├── FaceRegionTransformerPort.kt   # Face region coordinate mapping
│       ├── LocationSearchPort.kt  # Geocoding search (live results)
│       ├── ModelResourcePort.kt    # ONNX model loading
│       ├── DispatcherProvider.kt   # Coroutine dispatchers
│       ├── TimeProvider.kt        # Time operations
│       └── IdGenerator.kt         # Unique ID generation
│
├── infrastructure/
│   ├── adapter/                    # Port implementations
│   │   ├── ImageRepositoryAdapter.kt  # File I/O, hashing, sidecars
│   │   ├── SettingsAdapter.kt     # JSON file persistence
│   │   ├── NamingAdapter.kt       # Pattern-based naming
│   │   ├── DeduplicationAdapter.kt # Hash, perceptual, SURF dedup
│   │   ├── HashCacheAdapter.kt    # SQLite-backed hash cache
│   │   ├── FileSystemAdapter.kt   # File system operations
│   │   ├── DeviceAdapter.kt       # OS-specific device detection
│   │   ├── ImportHistoryAdapter.kt # JSON import history (implements ImportHistoryPort)
│   │   ├── NominatimGeocodingAdapter.kt  # OSM Nominatim geocoding
│   │   ├── ClasspathModelResourceAdapter.kt  # ONNX model loading from classpath
│   │   ├── ProcessedImageAdapter.kt  # BufferedImage ↔ ProcessedImage conversion
│   │   ├── Platform.kt            # Cross-platform OS detection & utilities
│   │   └── ...                    # DefaultDispatcherProvider, DefaultTimeProvider, etc.
│   ├── logging/
│   │   └── LoggingConfig.kt       # AppLogger (SLF4J-based)
│   ├── photoscan/                  # Photo detection infrastructure
│   │   ├── HybridCornerDetector.kt # Classical CV + ML hybrid (implements PhotoScanDetectorPort)
│   │   ├── PhotoScanDetectorService.kt  # YOLO/CV detection with fallbacks
│   │   ├── RectangleDetector.kt   # Edge-based rectangle detection
│   │   └── yolo/                  # YOLO neural network pipeline
│   └── wizard/                    # Photo Scan wizard UI state
│       ├── PhotoScanWizardState.kt  # Central wizard state (1534 lines)
│       ├── BoundingBox.kt         # Type aliases to domain.geometry
│       ├── BoundingBoxList.kt     # Type alias to domain.geometry
│       ├── PhotoConfiguration.kt  # Type alias → PhotoScanConfiguration
│       ├── WizardMode.kt          # NORMAL, FOUR_POINT, ADD_BOX, REFINEMENT
│       ├── FourPointState.kt      # 4-point corner placement state
│       ├── ZoomController.kt      # Zoom & pan state
│       ├── UndoRedoManager.kt     # Undo/redo for bounding box changes
│       ├── FaceSize.kt            # Face region preset sizes
│       ├── PreProcessedImage.kt    # Batch detection result
│       ├── SourceExifSummary.kt    # Source file EXIF summary
│       ├── AspectRatioHandler.kt  # Aspect ratio constraint logic
│       ├── RotationTransformer.kt # Rotation calculations
│       └── PhotoScanConstants.kt  # UI tuning constants
│
└── ui/
    ├── PetrieFileImporterApp.kt   # Top-level composable & tab navigation
    ├── AppIcon.kt                 # Programmatic app icon
    ├── theme/Theme.kt             # Material 3 theming
    ├── components/                 # Reusable UI components
    │   ├── ThumbnailImage.kt       # Async thumbnail loader
    │   ├── ThumbnailCache.kt       # In-memory thumbnail cache
    │   ├── DropTarget.kt           # Drag-and-drop utilities
    │   ├── FileDialogs.kt         # Cross-platform file dialogs (pickFolder, pickFile, pickImageFile)
    │   ├── PathSelectionField.kt  # SourcePathField, FolderSelectionField, FileSelectionField
    │   ├── LoadingIndicator.kt    # Native Compose loading spinners (CubeGrid)
    │   ├── ChunkyScrollbar.kt     # Custom scrollbar for content panels
    │   └── ...
    └── screens/                    # Full-screen views & wizards
        ├── MediaImportScreen.kt   # Main import flow
        ├── wizard/                 # Photo Scan import wizard
        │   ├── WizardContainer.kt  # Wizard orchestrator
        │   ├── OverviewScreen.kt   # Photo overview & selection
        │   ├── RefinementScreen.kt # Corner adjustment
        │   ├── SummaryScreen.kt    # Photo summary & export settings
        │   ├── metadata/           # Metadata editing
        │   │   ├── MetadataScreen.kt      # Metadata editing form
        │   │   ├── MetadataEditState.kt   # Compose state holder
        │   │   └── MetadataField.kt      # Reusable metadata field
        │   └── ...
├── metadataeditor/         # Standalone bulk metadata editor tab
│   ├── MetadataEditorScreen.kt  # Main editor orchestrator (source path, preview, dialogs)
│   ├── MetadataEditorSidebar.kt # Thumbnail sidebar with modified indicators
│   ├── MetadataEditorPanel.kt   # Metadata fields panel with override toggles
│   ├── MetadataEditorActions.kt # OverrideToggle helper and field-updater helpers
│   ├── BulkEditState.kt         # Per-file metadata state, UiMessage, OutputMode
│   └── BulkSelectionDialog.kt   # Multi-select thumbnail overlay dialog        ├── duplicatescanner/       # Standalone duplicate scanner tab
        ├── scan/                   # Legacy Photo Scan (simple mode)
        └── ...
```

## Ports & Adapters

Every port in `domain/port/` has a corresponding adapter in `infrastructure/` (or `application/` for application-level services):

| Port (interface) | Adapter (implementation) | Purpose |
|---|---|---|
| `ImageRepositoryPort` | `ImageRepositoryAdapter` | File scanning, copying, hashing |
| `SettingsPort` | `SettingsAdapter` | JSON settings persistence |
| `NamingPort` | `NamingAdapter` | Pattern-based path & filename generation |
| `DeduplicationPort` | `DeduplicationAdapter` | Hash, perceptual, SURF dedup |
| `HashCachePort` | `HashCacheAdapter` | SQLite-backed hash cache |
| `FileSystemPort` | `FileSystemAdapter` | File system operations |
| `DevicePort` | `DeviceAdapter` | Camera/device detection |
| `ImportHistoryPort` | `ImportHistoryAdapter` | Import history JSON persistence |
| `GeocodingPort` | `NominatimGeocodingAdapter` | OSM geocoding API |
| `PhotoScanDetectorPort` | `PhotoScanDetectorService` + `HybridCornerDetector` | Photo detection in scans |
| `PhotoScanExportPort` | `PhotoScanExportService` | Photo export with corrections & EXIF |
| `PerspectiveCorrectionPort` | `PerspectiveCorrectionService` | Homography/perspective correction |
| `FaceRegionTransformerPort` | `FaceRegionTransformer` | Face region coordinate mapping |
| `LocationSearchPort` | `LocationSearchService` | Geocoding with live results |
| `ModelResourcePort` | `ClasspathModelResourceAdapter` | ONNX model loading from classpath |
| `DispatcherProvider` | `DefaultDispatcherProvider` | Coroutine dispatcher factory |
| `TimeProvider` | `DefaultTimeProvider` | System time operations |
| `IdGenerator` | `DefaultIdGenerator` | Unique ID generation |

All registrations are in `AppModule.kt`:

```kotlin
val appModule = module {
    // Domain Ports → Adapters
    single<ImageRepositoryPort> { ImageRepositoryAdapter(...) }
    single<ImportHistoryPort> { ImportHistoryAdapter(...) }
    single<PhotoScanDetectorPort> { get<PhotoScanDetectorService>() }
    single<PerspectiveCorrectionPort> { PerspectiveCorrectionService() }
    single<FaceRegionTransformerPort> { FaceRegionTransformer() }
    single<LocationSearchPort> { LocationSearchService(...) }
    // ... etc
}
```

## Dependency Injection (Koin)

This project uses **Koin** for dependency injection — no annotations, no reflection, no code generation.

### In Composables

```kotlin
// Prefer domain ports for dependency injection
val settingsPort = koinInject<SettingsPort>()
val locationSearchPort = koinInject<LocationSearchPort>()

// Use-case services are acceptable directly
val importService = koinInject<ImportService>()
```

### In Test Classes

```kotlin
val service: ImportService by inject()
```

## Boundary Exceptions

The hexagonal architecture allows no inbound dependencies on Infrastructure from Domain or Application. A few pragmatic exceptions exist:

### UI → Application (Use-Case Services)

The UI directly imports application use-case services for user interaction flows. This is the intended dependency direction in hexagonal architecture:

| Service | Purpose |
|---|---|
| `ImportService` | Core import workflow |
| `ReorganizeService` | Library reorganization |
| `DuplicateScannerService` | Duplicate finding |
| `WatchFolderService` | Auto-import from watched folders |
| `MetadataWritingService` | Write image + EXIF/IPTC/XMP metadata |

### UI → Application (AWT-Coupled Services)

Some application services are tightly coupled to `BufferedImage` and `java.awt`. The UI imports these directly because the wizard composables need pixel-level image manipulation. These should ideally be accessed through their domain ports, but the wizard's tight `BufferedImage` threading makes this impractical:

| Service | Port Available | Rationale |
|---|---|---|
| `PerspectiveCorrectionService` | `PerspectiveCorrectionPort` | Wizard needs `BufferedImage`-level API for preview |
| `FaceRegionTransformer` | `FaceRegionTransformerPort` | Wizard needs `File`-level API for XMP parsing |
| `PhotoScanExportService` | `PhotoScanExportPort` | Wizard needs `BufferedImage`-level export API |
| `ScanService` | N/A (uses `BufferedImage` internally) | Legacy scan screen uses `BufferedImage` directly |

### UI → Application → Infrastructure Boundary Converters

| Crossing | Rationale |
|---|---|
| Application → `toProcessedImage()` | Converts `BufferedImage` → `ProcessedImage` at the application/infrastructure boundary |
| Application → `toBufferedImage()` | Converts `ProcessedImage` → `BufferedImage` at the application/infrastructure boundary |

### UI → Infrastructure (Documented Exceptions)

| Crossing | Rationale |
|---|---|
| UI → `AppPaths` | Simple path constants (`settingsDir`, `defaultDestination`). No logic to test. |
| UI → `Platform` | OS detection utility (`isMac`, `isWindows`, `openFile()`). Single-function calls. |
| UI → `toProcessedImage()` / `toBufferedImage()` | Type bridge converting `BufferedImage` ↔ `ProcessedImage` at the UI boundary. |
| UI → `PhotoScanWizardState` & `infrastructure.wizard.*` | The wizard state package manages `BufferedImage` and `java.io.File`. The UI directly reads/writes this state — extracting it to a port would add complexity with no testability benefit. |
| UI → `ThumbnailCache` → `RawThumbnailExtractor`/`VideoThumbnailAdapter` | Thumbnail generation is infrastructure I/O called from UI display code. |
| UI → `AppLogger` / `OperationType` | Logging at UI boundary. |

### Strictly Forbidden

- **Domain** must never import from `infrastructure` or `ui` ✅
- **Application** must never import from `ui` ✅
- **Application** must not import `java.awt`/`javax.imageio` except in documented AWT-coupled services ✅
- **UI** must not import concrete infrastructure adapters that have domain ports (use `koinInject<ThePort>()` instead) ✅
- **UI** must not import algorithm services directly when domain ports exist (use `koinInject<PerspectiveCorrectionPort>()` etc.) ✅

## Key Design Decisions

1. **Domain layer is pure Kotlin** — No AWT, no framework dependencies. `ProcessedImage` is an interface; `BufferedImage` conversion happens at infrastructure boundaries via `toProcessedImage()`/`toBufferedImage()`.

2. **Ports are interfaces in `domain/port/`** — Adapters implement them. This allows swapping implementations (e.g., SQLite → PostgreSQL) without touching domain or application logic.

3. **UI screens are thin composables** — They render state and delegate logic to application services via Koin injection. Business logic lives in services, not composables.

4. **PhotoScanWizardState** is the central state container for the photo scan wizard. At ~1500 lines it's large, but it manages ~30 interdependent `StateFlow` properties that drive the wizard's reactive UI. Extracting to MVI would be the next evolution step.

5. **MetadataEditState** is a Compose state holder for the 18 metadata fields shared between `MetadataScreen` and `EditPhotoDialog`. Uses `mutableStateOf` for fine-grained recomposition.

6. **PhotoScanDetectorPort** accepts `ProcessedImage` (domain abstraction). `PhotoScanDetectorService` implements the port and converts to `BufferedImage` internally, keeping AWT out of the domain layer.

7. **Application data classes live in domain/model** — `ScanProgress`, `WatchFolderConfig`, and `WatchFolderStatus` are pure data classes used by both application services and UI. They belong in the domain model layer, not in the application service files.

8. **Cross-platform support** — All OS-conditional code (file dialogs, device ejection, FFmpeg resolution) is centralized in `Platform.kt`.

## Technology Stack

| Component | Technology | Purpose |
|---|---|---|
| UI Framework | Jetpack Compose for Desktop | Cross-platform UI |
| Language | Kotlin 2.3 | Primary language |
| DI | Koin 4.0 | Dependency injection (no annotations, no reflection) |
| Metadata | metadata-extractor 2.19 | EXIF & video metadata |
| Image Processing | imgscalr, BoofCV (SURF) | Thumbnails, feature matching |
| ML Inference | ONNX Runtime | YOLO photo detection |
| Caching | SQLite (xerial) | Hash cache |
| CLI | Clikt | Command-line interface |
| Testing | JUnit 5, AssertJ, Mockito | Test framework |
| Formatting | ktfmt | Code formatting |
| Static Analysis | Detekt | Code quality |