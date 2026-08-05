# Contributing to Petrie Image Importer

Thank you for your interest in contributing! This guide covers everything you need to get started developing, testing, and submitting changes.

## Table of Contents

- [Getting Started](#getting-started)
- [Project Structure](#project-structure)
- [Architecture](#architecture)
- [Development Workflow](#development-workflow)
- [Coding Standards](#coding-standards)
- [Testing](#testing)
- [UI Development](#ui-development)
- [Adding New Features](#adding-new-features)
- [Common Patterns](#common-patterns)
- [Troubleshooting](#troubleshooting)

---

## Getting Started

### Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| JDK | 21+ | Kotlin compilation and runtime |
| Gradle | 9.x (wrapper included) | Build system |
| FFmpeg | any (optional) | Video thumbnail extraction |
| Git | any | Version control |

### Setup

```bash
git clone <repo-url>
cd petrie-file-importer

# Metadata integration uses a sibling composite build (required for compile + integration tests)
git clone git@github.com:kryspetrie/photo-metadata-editor.git ../photo-metadata-editor

# Build the project
./gradlew build

# Download bundled ExifTool (required before run/test/integrationTest)
./gradlew downloadExifTool

# Run the desktop application
./gradlew run

# Run tests
./gradlew test
```

### Verifying Your Setup

A successful setup should:
- Compile without errors: `./gradlew compileKotlin`
- Pass all tests: `./gradlew test`
- Pass formatting checks: `./gradlew ktfmtCheck`

---

## Project Structure

```
petrie-file-importer/
├── build.gradle.kts                 # Build configuration & dependencies
├── build-installers.sh              # Build native installer for current OS
├── photo-import.sh                  # Convenience launcher script
├── CONTRIBUTING.md                  # This file
├── DEVELOPER_GUIDE.md               # Compose for backend developers
├── QUICK_REFERENCE.md               # Fast answers for common tasks
├── README.md                        # User-facing documentation
├── docs/ARCHITECTURE.md             # Full architecture reference
└── src/
    ├── main/kotlin/org/kryspetrie/fileimport/
    │   ├── PetrieFileImporterApp.kt              # Application entry point
    │   ├── application/                           # Use cases & orchestration
    │   │   ├── ImportService.kt                   # Core import workflow
    │   │   ├── ImportScanner.kt                   # Source directory scanning
    │   │   ├── ImportExecutor.kt                   # File copy & verification
    │   │   ├── ReorganizeService.kt               # Library reorganization
    │   │   ├── DuplicateScannerService.kt           # Standalone duplicate scanner
    │   │   ├── WatchFolderService.kt               # Auto-import from watched folder
    │   │   ├── ScanService.kt                      # Photo scan orchestration
    │   │   ├── PhotoScanExportService.kt            # Photo export pipeline
    │   │   ├── PerspectiveCorrectionService.kt      # Homography correction (BoofCV)
    │   │   ├── FaceRegionTransformer.kt            # Face region coordinate mapping
    │   │   ├── LocationSearchService.kt            # Geocoding search
    │   │   ├── metadata/                         # Bulk editor save/undo/journal
    │   │   │   ├── MetadataEditService.kt
    │   │   │   ├── MetadataEditUndoService.kt
    │   │   │   └── MetadataEditJournalRepository.kt
    │   │   └── export/                           # Export & ExifTool metadata writes
    │   │       ├── MetadataWritingService.kt
    │   │       ├── PhotoScanMetadataMapper.kt
    │   │       ├── FileFormatSupport.kt
    │   │       ├── MetadataWriteException.kt
    │   │       └── FilenameResolver.kt
    │   ├── cli/                                    # CLI interface (Clikt)
    │   │   ├── PhotoImportCli.kt
    │   │   └── ReorganizeCommand.kt
    │   ├── di/                                     # Dependency injection (Koin)
    │   │   ├── AppModule.kt                        # Service & port registrations
    │   │   └── MetadataEditorIntegrationModule.kt  # ExifTool / photo-metadata-editor
    │   ├── domain/
    │   │   ├── model/                              # Pure Kotlin data classes & enums
    │   │   │   ├── AppSettings.kt                  # User preferences & window state
    │   │   │   ├── ImportConfiguration.kt           # Import settings & naming patterns
    │   │   │   ├── ImageFile.kt                     # Core file model + ImageFileType
    │   │   │   ├── PhotoScanConfiguration.kt        # Photo scan metadata overrides
    │   │   │   ├── PhotoScanModels.kt               # DetectedPhoto, PhotoCorner
    │   │   │   ├── PhotoScanExportModels.kt          # Export result types
    │   │   │   ├── ProcessedImage.kt                # Domain image abstraction (no AWT)
    │   │   │   ├── geometry/                        # BoundingBox, Point, Corner
    │   │   │   └── ...                              # ~35 more model files
    │   │   └── port/                               # Interfaces (hex architecture)
    │   │       ├── ImageRepositoryPort.kt           # File scanning, copying, hashing
    │   │       ├── SettingsPort.kt                  # JSON settings persistence
    │   │       ├── NamingPort.kt                    # Path & filename generation
    │   │       ├── DeduplicationPort.kt             # Duplicate detection strategies
    │   │       ├── HashCachePort.kt                 # Disk-backed hash cache
    │   │       ├── FileSystemPort.kt                # File system operations
    │   │       ├── DevicePort.kt                    # Camera/device detection
    │   │       ├── ImportHistoryPort.kt              # Import history persistence
    │   │       ├── GeocodingPort.kt                 # Location search (Nominatim)
    │   │       ├── PhotoScanDetectorPort.kt          # Photo detection in scans
    │   │       ├── PhotoScanExportPort.kt             # Photo export with corrections
    │   │       ├── ModelResourcePort.kt               # ONNX model loading
    │   │       ├── DispatcherProvider.kt              # Coroutine dispatchers
    │   │       ├── TimeProvider.kt                    # Time operations
    │   │       └── IdGenerator.kt                     # Unique ID generation
    │   ├── infrastructure/
    │   │   ├── adapter/                            # Port implementations
    │   │   │   ├── ImageRepositoryAdapter.kt         # File I/O, hashing, sidecars
    │   │   │   ├── SettingsAdapter.kt               # JSON file persistence
    │   │   │   ├── NamingAdapter.kt                 # Pattern-based naming
    │   │   │   ├── DeduplicationAdapter.kt           # Hash, perceptual, SURF dedup
    │   │   │   ├── HashCacheAdapter.kt              # SQLite-backed cache
    │   │   │   ├── FileSystemAdapter.kt             # File system operations
    │   │   │   ├── DeviceAdapter.kt                # OS-specific device detection
    │   │   │   ├── ImportHistoryAdapter.kt           # JSON import history
    │   │   │   ├── NominatimGeocodingAdapter.kt       # OSM geocoding API
    │   │   │   ├── ClasspathModelResourceAdapter.kt    # ONNX model loading
    │   │   │   ├── ProcessedImageAdapter.kt          # BufferedImage ↔ ProcessedImage
    │   │   │   ├── Platform.kt                      # Cross-platform OS detection
    │   │   │   └── ...                               # DefaultDispatcherProvider, etc.
    │   │   ├── logging/
    │   │   │   └── LoggingConfig.kt                 # AppLogger (SLF4J-based)
    │   │   ├── photoscan/                            # Photo detection infrastructure
    │   │   │   ├── HybridCornerDetector.kt           # Classical CV + ML hybrid
    │   │   │   ├── PhotoScanDetectorService.kt       # YOLO/CV detection with fallbacks
    │   │   │   ├── RectangleDetector.kt             # Edge-based rectangle detection
    │   │   │   └── yolo/                             # YOLO neural network pipeline
    │   │   └── wizard/                               # Photo Scan wizard UI state
    │   │       ├── PhotoScanWizardState.kt           # Central wizard state
    │   │       ├── BoundingBox.kt                    # Type aliases → domain.geometry
    │   │       ├── PhotoConfiguration.kt             # Type alias → PhotoScanConfiguration
    │   │       └── ...                                # WizardMode, ZoomController, etc.
    │   └── ui/
    │       ├── PetrieFileImporterApp.kt              # Top-level app & tab navigation
    │       ├── AppIcon.kt                           # Programmatic app icon
    │       ├── theme/Theme.kt                       # Material 3 theming
    │       ├── components/                          # Reusable UI components
    │       │   ├── ThumbnailImage.kt                # Async thumbnail loader
    │       │   ├── ThumbnailCache.kt                # In-memory thumbnail cache
    │       │   ├── FileDialogs.kt                  # Cross-platform file dialogs
    │       │   └── ...
    │       └── screens/                              # Full-screen views & wizards
    │           ├── MediaImportScreen.kt             # Main import flow
    │           ├── wizard/                           # Photo Scan import wizard
    │           │   ├── WizardContainer.kt            # Wizard orchestrator
    │           │   ├── OverviewScreen.kt            # Photo overview & selection
    │           │   ├── RefinementScreen.kt          # Corner adjustment
    │           │   ├── SummaryScreen.kt             # Summary & export settings
    │           │   ├── metadata/                    # Metadata editing
    │           │   │   ├── MetadataScreen.kt
    │           │   │   ├── MetadataEditState.kt     # shared/metadata Compose state holder
    │           │   │   └── MetadataField.kt
    │           │   └── ...
    │           └── ...
    └── test/kotlin/org/kryspetrie/fileimport/        # Mirrors main/ structure
        ├── application/                             # Service-layer tests
        ├── domain/model/                            # Model & business rule tests
        ├── infrastructure/                          # Adapter & wizard tests
        └── ui/                                      # UI logic tests
```

---
## Architecture

This project follows **Hexagonal Architecture** (Ports and Adapters). See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the full reference.

```
┌─────────────────────────────────────────────────────────────────┐
│                     UI Layer (Compose Desktop)                   │
│  Screens · Wizards · Components · Theme                        │
├─────────────────────────────────────────────────────────────────┤
│                     Application Layer                            │
│  ImportService · ReorganizeService · ScanService · etc.        │
├─────────────────────────────────────────────────────────────────┤
│                       Domain Layer                              │
│  Models (data classes) · Ports (interfaces) · Business rules     │
├─────────────────────────────────────────────────────────────────┤
│                    Infrastructure Layer                          │
│  Adapters implement ports · PhotoScan detection · Wizard state   │
└─────────────────────────────────────────────────────────────────┘
```

### Key Principles

- **Domain models** have no framework dependencies — they are plain Kotlin data classes. No `java.awt` imports in domain.
- **Ports** are interfaces in `domain/port/` that define what the application needs. Every port has a corresponding adapter.
- **Adapters** in `infrastructure/` implement those ports with real I/O. The UI accesses adapters only through ports (via Koin DI).
- **Application services** orchestrate ports to fulfill use cases. They don't import from the UI layer.
- **UI screens** inject ports and services via `koinInject()`, never concrete adapters directly.

### Dependency Flow

```
UI → Application → Domain (models + ports)
                         ↑
          Infrastructure (adapters) implements ports
```

**Never** import infrastructure classes from the domain layer. The UI layer accesses infrastructure only through domain ports (registered in `AppModule.kt`).

A few pragmatic exceptions exist (e.g., `Platform`, `PhotoScanWizardState`) — see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md#boundary-exceptions) for details.

---

## Development Workflow

### Running the App

```bash
# Desktop GUI
./gradlew run

# Or use the convenience script
./photo-import.sh

# CLI mode
./gradlew run --args="--cli import /source /dest --dry-run"

# CLI via convenience script
./photo-import.sh --cli import /source /dest --dry-run
```

### Building

```bash
# Full build (compile + test + format check)
./gradlew build

# Compile only
./gradlew compileKotlin

# Native installers (DMG/MSI/DEB with bundled JRE — no Java needed for end users)
./gradlew packageDmg       # macOS
./gradlew packageMsi       # Windows
./gradlew packageDeb       # Linux

# Multi-platform local build (macOS native + Linux via Docker)
./build-installers.sh

# Uber JAR (requires Java on the target machine)
./gradlew packageUberJarForCurrentOS

# All three platforms via CI — push to main or open a PR.
# See .github/workflows/build.yml and README.md § "CI / GitHub Actions".
```

### Code Formatting

This project uses [ktfmt](https://github.com/facebook/ktfmt) for consistent formatting. **All code must pass formatting checks before merging.**

```bash
# Check formatting
./gradlew ktfmtCheck

# Auto-format all files
./gradlew ktfmtFormatMain ktfmtFormatTest ktfmtFormatScripts
```

### Branch Strategy

1. Create a feature branch from `main`: `git checkout -b feature/my-feature`
2. Make changes and ensure all checks pass
3. Submit a pull request with a clear description

---

## Coding Standards

### Kotlin Style

- Use `data class` for models; keep them in `domain/model/`
- Prefer `val` over `var`; use immutable collections where possible
- Use `sealed class` or `enum class` for closed type hierarchies
- Name files after the primary class they contain
- Keep functions short and focused — extract helpers for complex logic

### Compose UI

- Compose screens go in `ui/screens/`, reusable components in `ui/components/`
- Mark private composables with `private` — only export the top-level screen function
- Use `MaterialTheme.typography` and `MaterialTheme.colorScheme` consistently
- Never hardcode colors — use the theme
- Keep composable functions stateless when possible; hoist state to the screen level

### Error Handling

- Use `try/catch` with `catch (_: Exception)` for non-critical failures (e.g., thumbnail extraction)
- Use `require()` for precondition checks in service methods
- Propagate meaningful error messages to the UI via state variables
- Never silently swallow errors in core import/copy operations

### Concurrency

- Use `Dispatchers.IO` for all file I/O operations
- Use `Semaphore` to limit parallelism for CPU-intensive work (metadata extraction, hashing)
- Use `coroutineScope { }` with `async/awaitAll` for parallel batch operations
- Report progress via callback lambdas, not shared mutable state

---

## Testing

### Running Tests

```bash
# Unit tests (excludes @Tag("integration") and UI component tests)
./gradlew test

# Integration tests — requires sibling photo-metadata-editor repo + bundled ExifTool
./gradlew downloadExifTool integrationTest

# UI component tests
./gradlew uiTest

# Specific test class
./gradlew test --tests "org.kryspetrie.fileimport.application.ImportServiceTest"

# Force re-run (skip cache)
./gradlew cleanTest test
```

Integration tests load sample RAW/JPEG files from the `metadata-test-fixtures` module (Gradle composite build dependency), not from files checked into this repo.

### Test Structure

Tests mirror the main source tree:

| Source Package | Test Package | Focus |
|---------------|-------------|-------|
| `domain/model/` | `domain/model/` | Default values, derived properties, serialization |
| `application/` | `application/` | Service logic with mocked ports |
| `infrastructure/adapter/` | `infrastructure/adapter/` | Real file I/O with temp directories |
| `ui/` | `ui/` | Pure logic extracted from composables |

### Writing Tests

**Frameworks used:**
- JUnit 5 (Jupiter) for test structure
- AssertJ for fluent assertions
- Mockito Kotlin for mocking ports/interfaces
- kotlinx-coroutines-test for async testing

**Conventions:**
- Use `@DisplayName` for readable test names
- Use `@Nested` inner classes to group related tests
- Use `@TempDir` for tests that need temporary files
- Prefer `runBlocking` over `runTest` for tests involving `Dispatchers.IO`

**Example test:**

```kotlin
@DisplayName("ImportService")
class ImportServiceTest {

  @TempDir lateinit var tempDir: File

  @Nested
  @DisplayName("scanSource")
  inner class ScanSourceTest {

    @Test
    @DisplayName("should discover files in subdirectories")
    fun shouldDiscoverFilesRecursively() = runBlocking {
      // Arrange
      val subDir = File(tempDir, "sub").apply { mkdirs() }
      File(subDir, "photo.jpg").writeBytes(ByteArray(100))

      val repo = mock<ImageRepositoryPort> {
        onBlocking { scanDirectory(any(), eq(true)) } doReturn listOf(/* ... */)
      }
      val service = ImportService(repo, mock(), mock())

      // Act
      val result = service.scanSource(tempDir.absolutePath)

      // Assert
      assertThat(result).isNotEmpty()
    }
  }
}
```

### Test Coverage Goals

- All domain models should have tests for defaults and derived properties
- All application services should have tests for primary flows and edge cases
- All adapters should have integration tests with real (temp) file I/O
- UI logic extracted into pure functions should be unit tested

---

## UI Development

### Preview/Dry-Run Pattern

All destructive operations must support a preview step before execution:

| Feature | Preview Screen | What It Shows |
|---------|---------------|---------------|
| **Import** | `PreviewStructureScreen` | Source → destination file mappings with thumbnails, folder structures, conflicts |
| **Reorganize** | Inline in `ReorganizeScreen` | Current path → new path for each file, conflict counts, new folders |
| **Duplicate Scanner** | `DuplicateReviewScreen` | Grouped duplicates with resolution options before deletion |

When adding a new destructive feature, always implement:
1. A **preview/scan** step that computes what would happen
2. A **review UI** showing the full mapping (old state → new state)
3. A **confirmation action** that executes the changes
4. An **undo mechanism** where feasible (e.g., reorganize journals)

### Navigation Flow

The app uses tab-based navigation with five top-level tabs:
- **Media Import** — Main import flow with profiles, source/dest selection
- **Photo Scan Import** — Extract photos from scanned images with corner detection and metadata
- **Reorganize** — Library file reorganization with undo journals
- **Library Duplicates** — Standalone duplicate scanner
- **Metadata Editor** — Bulk EXIF/IPTC/XMP metadata editing across multiple photos

Within each tab, flow steps are managed via an enum (`FlowStep`, `ReorgStep`, etc.) and dialogs for modal interactions (file selection, duplicate review, import preview).

### Adding a New Screen

1. Create `MyFeatureScreen.kt` in `ui/screens/`
2. Add the composable function: `@Composable fun MyFeatureScreen(...)`
3. Add a tab entry in `PetrieFileImporterApp.kt` if it's a top-level feature
4. Or show it as a dialog within an existing screen flow

---

## Adding New Features

### Adding a New File Format

1. Add the extension to `ImageFileType` in `ImageFile.kt`
2. Set `isRaw`, `isVideo`, and `mimePrefix` appropriately
3. If it needs special thumbnail handling, update `RawThumbnailExtractor` or `VideoThumbnailAdapter`
4. Add tests in `ImageFileTypeTest.kt`

### Adding a New Metadata Placeholder

1. Add the placeholder to `NamePlaceholders.folderPlaceholders` or `filePlaceholders` in `ImportConfiguration.kt`
2. Implement the resolution in `NamingAdapter.kt` (`generateFolderPath` or `generateFileName`)
3. Add a test case in `NamingAdapterTest.kt`

### Adding a New Configuration Option

1. Add the field to `ImportConfiguration` with a sensible default
2. Add the UI control in the appropriate subsection of `ImportScreen.kt`'s `SettingsSection`
3. Wire it into the relevant service method
4. Add a test for the default value in `ImportConfigurationTest.kt`

### Adding a New Application Service

1. Create the service class in `application/`
2. Define any new ports (interfaces) in `domain/port/`
3. Create adapter implementations in `infrastructure/adapter/`
4. Register everything in `AppModule.kt` via Koin:
   ```kotlin
   single { MyService(get(), get()) }
   ```
5. Inject into UI via `val myService = koinInject<MyService>()`

---

## Common Patterns

### Progress Reporting

All long-running operations accept a progress callback:

```kotlin
suspend fun myOperation(
    onProgress: (current: Int, total: Int, file: String) -> Unit = { _, _, _ -> }
): Result {
    items.forEachIndexed { index, item ->
        onProgress(index + 1, items.size, item.name)
        // ... process item ...
    }
}
```

### Cached Background Work

For expensive operations on large datasets:

```kotlin
val semaphore = Semaphore(Runtime.getRuntime().availableProcessors().coerceIn(2, 8))
val results = coroutineScope {
    items.map { item ->
        async(Dispatchers.IO) {
            semaphore.withPermit { processItem(item) }
        }
    }.awaitAll()
}
```

### Settings Persistence

User settings are stored as JSON in `~/.petrie-importer/`:
- `settings.json` — profiles, window state, theme
- `import_history.json` — import log
- `journals/` — reorganize undo journals
- `hash_cache.db` — SQLite file hash cache

Use `kotlinx.serialization` with `@Serializable` for all persisted models.

---

## Troubleshooting

### Common Build Issues

| Issue | Fix |
|-------|-----|
| `Cannot find libskiko-*.dylib` | Run `./gradlew clean build` — Compose native libraries need re-download |
| `ktfmt formatting failures` | Run `./gradlew ktfmtFormatMain ktfmtFormatTest` |
| `Unresolved reference` for Compose icons | Check if the icon moved to `Icons.AutoMirrored.Filled.*` |
| Tests fail with coroutine timing issues | Use `runBlocking` with `SupervisorJob` instead of `runTest` for `Dispatchers.IO` work |
| `application plugin conflicts` | The Compose plugin provides its own `run` task; don't add the `application` plugin |

### Useful Commands

```bash
# See all available Gradle tasks
./gradlew tasks

# Dependency tree
./gradlew dependencies

# Run with debug logging
./gradlew run --debug

# Generate test report
./gradlew test
# Report at: build/reports/tests/test/index.html
```

---

## Data Storage Locations

| Platform | Settings Directory |
|----------|-------------------|
| macOS | `~/.petrie-importer/` |
| Linux | `~/.petrie-importer/` |
| Windows | `%USERPROFILE%\.petrie-importer\` |

To reset all settings, delete this directory.

---

## Technology Reference

| Component | Library | Version | Purpose |
|-----------|---------|---------|---------|
| UI | Jetpack Compose for Desktop | 1.6.11 | Cross-platform UI framework |
| Language | Kotlin | 2.3.10 | Primary language |
| Build | Gradle (Kotlin DSL) | 9.x | Build and dependency management |
| DI | Koin | 3.5.6 / 4.0 | Dependency injection |
| Serialization | kotlinx-serialization | 1.7.3 | JSON persistence |
| Metadata | metadata-extractor | 2.19.0 | EXIF & video metadata |
| Thumbnails | imgscalr | 4.2 | Image scaling |
| Hashing | commons-codec | 1.17.1 | MD5 file hashing |
| Cache | SQLite (xerial) | 3.49.0 | Disk-backed hash cache |
| Dedup | BoofCV | 1.2.2 | SURF visual feature matching |
| CLI | Clikt | 4.2.2 | Command-line interface |
| Testing | JUnit 5 + AssertJ + Mockito | various | Test framework |
| Formatting | ktfmt | 0.25.0 | Code formatting |
