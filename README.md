# Petrie Image Importer

A cross-platform Kotlin desktop application for organizing and importing photos and videos based on EXIF metadata.

## Features

- **Smart Organization**: Automatically organize photos into date-based folders using EXIF metadata
- **Custom Naming**: Flexible filename patterns with EXIF/video metadata placeholders
- **Duplicate Detection**: Multiple strategies — file hash, EXIF matching, perceptual hash, SURF visual matching
- **RAW+JPEG Pair Handling**: Import both, RAW only, or JPEG only when cameras shoot simultaneously
- **Sidecar File Support**: Automatically imports `.xmp`, `.thm`, `.lrv`, `.aae`, and other sidecar files
- **Video Import**: Full support for MP4, MOV, AVI, MKV, and other video formats with metadata extraction
- **Safe Transfers**: Hash verification ensures files are copied correctly
- **Import Profiles**: Save complete import configurations for different cameras or workflows
- **Camera Auto-Detection**: Automatically selects the right profile when a camera is connected
- **Library Reorganization**: Reorganize existing photo libraries with rename previews and undo support
- **Standalone Duplicate Scanner**: Find and resolve duplicates across your entire photo library
- **Watch Folder**: Automatically import new files dropped into a monitored folder
- **Import History**: Track past imports with file counts, sizes, and timestamps
- **CLI Mode**: Scriptable command-line interface with dry-run support
- **Cross-Platform**: Native installers for macOS, Windows, and Linux with bundled JRE

## Quick Start

### Run from Source

```bash
# Clone the repository
git clone <repo-url>
cd petrie-file-importer

# Launch the GUI
./gradlew run

# Or use the convenience script
./photo-import.sh
```

### Build Native Installers

Native installers bundle a JRE so end users don't need Java installed.

```bash
# Build for current platform (DMG on macOS, DEB on Linux, MSI on Windows)
./build-installers.sh

# Or use Gradle directly
./gradlew packageDmg    # macOS
./gradlew packageDeb    # Linux
./gradlew packageMsi    # Windows

# Build an uber JAR (requires Java on the target machine)
./gradlew packageUberJarForCurrentOS
```

### Install from Native Package

#### macOS
```bash
# The DMG is in build/compose/binaries/main/dmg/
open build/compose/binaries/main/dmg/*.dmg
```

#### Linux
```bash
sudo dpkg -i build/compose/binaries/main/deb/*.deb
sudo apt-get install -f  # Install any missing dependencies
```

#### Windows
```bash
# Run the MSI installer from build/compose/binaries/main/msi/
```

### Command Line Interface

```bash
# CLI mode via Gradle
./gradlew run --args="--cli import /source /destination"

# Dry run (preview changes without copying)
./gradlew run --args="--cli import /source /dest --dry-run"

# Check for duplicates
./gradlew run --args="--cli check-duplicates /path/to/photos"

# Or use the convenience script
./photo-import.sh --cli import /source /dest --dry-run
```

## System Requirements

### Building from Source
- JDK 21 or higher
- Gradle (wrapper included)

### Running Native Installers
- No Java required — JRE is bundled
- macOS 10.15+, Windows 10+, or Linux (Ubuntu 20.04+)
- Minimum 4GB RAM recommended for large libraries
- FFmpeg (optional, for video thumbnail extraction)

## Configuration

Settings are stored in `~/.petrie-importer/`:

| File | Purpose |
|------|---------|
| `settings.json` | Application settings, import profiles, window state |
| `import_history.json` | Log of past imports |
| `hash_cache.db` | SQLite cache of file hashes for fast deduplication |
| `journals/` | Undo journals for library reorganization |

To reset all settings, delete the `~/.petrie-importer/` directory.

### Naming Patterns

#### Folder Patterns

| Placeholder | Description | Example |
|-------------|-------------|---------|
| `{yyyy-MM-dd}` | Year-Month-Day | `2024-03-15` |
| `{yyyy-MM}` | Year-Month | `2024-03` |
| `{yyyy}` | Year (4-digit) | `2024` |
| `{MM}` | Month (01-12) | `03` |
| `{dd}` | Day (01-31) | `15` |
| `{camera}` | Camera model | `Canon_EOS_R5` |
| `{make}` | Camera manufacturer | `Canon` |
| `{lens}` | Lens model | `RF_50mm` |
| `{type}` | Media type | `Photos` or `Videos` |

Combine them to create hierarchies: `{yyyy}/{MM}/{dd}` produces `2024/03/15/`.

#### Filename Patterns

| Placeholder | Description | Example |
|-------------|-------------|---------|
| `{original}` | Original filename | `IMG_1234` |
| `{yyyy}{MM}{dd}` | Date components | `20240315` |
| `{HH}{mm}{ss}` | Time components | `143022` |
| `{iso}` | ISO value | `400` |
| `{aperture}` | Aperture | `f1.2` |
| `{shutter}` | Shutter speed | `1_250s` |
| `{focal}` | Focal length | `50mm` |
| `{counter}` | Sequence number | `0001` |
| `{duration}` | Video duration | `1m30s` |
| `{fps}` | Video frame rate | `60` |

## Architecture

This application follows **Hexagonal Architecture** (Ports and Adapters):

```
┌─────────────────────────────────────────────────────────────┐
│                    UI Layer (Compose Desktop)                │
├─────────────────────────────────────────────────────────────┤
│                    Application Layer                         │
│   ImportService · ReorganizeService · DuplicateScannerService│
├─────────────────────────────────────────────────────────────┤
│                      Domain Layer                            │
│         (Models, Ports/Interfaces, Business Rules)           │
├─────────────────────────────────────────────────────────────┤
│                   Infrastructure Layer                       │
│    (Adapters: File I/O, EXIF, Settings, Deduplication)       │
└─────────────────────────────────────────────────────────────┘
```

### Technology Stack

| Component | Technology |
|-----------|------------|
| UI Framework | Jetpack Compose for Desktop 1.6 |
| Language | Kotlin 2.3 |
| DI Framework | Koin 4.0 |
| Metadata | metadata-extractor 2.19 |
| Image Processing | imgscalr, BoofCV (SURF) |
| Caching | SQLite (xerial) |
| CLI | Clikt |
| Testing | JUnit 5, AssertJ, Mockito |
| Build | Gradle (Kotlin DSL) |
| Formatting | ktfmt |

## Development

### Convenience Script

```bash
./photo-import.sh              # Launch GUI
./photo-import.sh build        # Build project
./photo-import.sh test         # Run tests
./photo-import.sh format       # Auto-format code
./photo-import.sh package      # Build native installer
./photo-import.sh clean        # Clean build artifacts
./photo-import.sh --cli [args] # Run CLI mode
./photo-import.sh help         # Show all commands
```

### Gradle Commands

```bash
./gradlew run                  # Launch the application
./gradlew test                 # Run all tests
./gradlew ktfmtCheck           # Check formatting
./gradlew ktfmtFormatMain      # Auto-format main sources
./gradlew build                # Full build (compile + test + format check)
./gradlew packageDmg           # macOS native installer
./gradlew packageUberJarForCurrentOS  # Uber JAR (requires Java)
```

### Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for architecture details, coding standards, testing guidelines, and how to add new features.

## License

MIT License
