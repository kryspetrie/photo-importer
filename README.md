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

Native installers bundle a JRE so end users don't need Java installed. `jpackage` does not support cross-compilation, so each platform's installer must be built on that platform.

#### Local Build (current platform only)

```bash
# Build for your current OS
./build-all.sh            # macOS .dmg natively + Linux .deb via Docker
./build-all.sh macos      # macOS .dmg only
./build-all.sh linux      # Linux .deb only (requires Docker)

# Or use Gradle directly
./gradlew packageDmg      # macOS
./gradlew packageDeb      # Linux
./gradlew packageMsi      # Windows

# Build an uber JAR (requires Java on the target machine)
./gradlew packageUberJarForCurrentOS
```

#### CI Build (all platforms)

The included GitHub Actions workflow builds `.dmg`, `.deb`, and `.msi` installers in parallel. See [CI / GitHub Actions](#ci--github-actions) below for setup instructions.

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

## CI / GitHub Actions

A GitHub Actions workflow at `.github/workflows/build.yml` builds and tests all three platform installers. It runs automatically on pushes to `main` and on pull requests.

### Setup

1. **Push to GitHub** — the workflow runs automatically, no configuration needed:
   ```bash
   git remote add origin https://github.com/<you>/petrie-file-importer.git
   git push -u origin main
   ```
2. **Download artifacts** — after a successful run, go to **Actions → Build Installers → (latest run)** and download the installers from the **Artifacts** section at the bottom of the run page.

### Triggers

| Trigger | What happens |
|---------|-------------|
| Push to `main` | Tests run, then all three installers are built and uploaded as artifacts |
| Pull request to `main` | Same — validates the PR builds cleanly on all platforms |
| Push a tag like `v1.0.0` | Builds all installers, then creates a GitHub Release with the .dmg, .deb, and .msi attached |
| Manual (Actions → Run workflow) | On-demand build from any branch |

### Creating a Release

Tag a commit and push it to trigger an automatic release with all installers attached:

```bash
git tag v1.0.0
git push origin v1.0.0
```

The workflow uploads the `.dmg`, `.deb`, and `.msi` to a GitHub Release with auto-generated release notes.

### Costs

- **Public repos**: GitHub Actions is free with generous limits.
- **Private repos**: Uses your account's included Actions minutes. Each full build uses ~10-15 minutes across the three runners. Free-tier accounts get 2,000 minutes/month.

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

## Documentation

### For New Developers (Especially Backend/Spring Developers)

📚 **[DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md)** - Comprehensive guide for developing Compose Multiplatform applications

This guide is specifically written for backend developers transitioning to desktop UI development. It covers:

- **Compose Multiplatform Fundamentals**: Declarative UI, composables, state management
- **Development Setup**: IntelliJ IDEA (no Android Studio needed for desktop!)
- **Testing UI Components**: Unit tests, component tests, and preview system
- **Architecture**: Hexagonal architecture with Koin dependency injection
- **Spring Developer Mappings**: Direct comparison of Spring vs. Compose/Koin concepts
- **Common Patterns**: Best practices, anti-patterns, and debugging tips

### Quick Reference

⚡ **[QUICK_REFERENCE.md](QUICK_REFERENCE.md)** - Fast answers for common tasks

- Running and building the application
- Compose UI patterns and examples
- Testing patterns
- Gradle commands
- Keyboard shortcuts

### Documentation Summary

📋 **[DOCUMENTATION_SUMMARY.md](DOCUMENTATION_SUMMARY.md)** - Overview of all documentation

Complete index of all documentation with explanations of what's covered and how to use it.

### Inline Documentation

💡 **KDoc Comments** - Hover in IntelliJ IDEA

All major source files include extensive KDoc documentation:
- Entry points and main composables
- UI components and screens
- Domain models and configurations
- Services and dependency injection
- Theme and styling

Just hover over any symbol in IntelliJ or press `Cmd/Ctrl + Q` to view documentation.

## Key Documentation Highlights

### Do I Need Android Studio?

**No!** For desktop-only development (Windows, macOS, Linux), **IntelliJ IDEA** is sufficient and recommended:
- IntelliJ IDEA Community Edition (free) works perfectly
- No Android SDK required
- Full support for Compose Multiplatform desktop

See [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md#do-you-need-android-studio) for details.

### How to Test UI Components

The project uses multiple testing strategies:

1. **Unit Tests**: Test pure functions and business logic
   ```bash
   ./gradlew test
   ```

2. **UI Component Previews**: Live preview while developing
   - Add `@Preview` annotation to composables
   - View in IntelliJ preview panel
   - Similar to WidgetBook/Storybook

3. **UI Component Tests**: Test UI interactions
   - Uses Compose UI Test framework
   - See examples in `src/test/kotlin/org/kryspetrie/fileimport/ui/`

See [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md#testing-ui-components) for detailed examples.

### Development Workflow

```bash
# Run with hot reload
./gradlew run

# Run tests
./gradlew test

# Format code
./gradlew ktfmtFormatMain

# Build native installer
./gradlew packageDmg      # macOS
./gradlew packageMsi      # Windows
./gradlew packageDeb      # Linux
```

See [QUICK_REFERENCE.md](QUICK_REFERENCE.md) for complete command reference.

### Component Preview System

Compose provides a built-in preview system similar to WidgetBook:

```kotlin
@Preview(showBackground = true)
@Composable
fun MyComponentPreview() {
    PetrieTheme {
        MyComponent(text = "Preview")
    }
}
```

Click the "Preview" button in IntelliJ to see live preview. Multiple previews appear as tabs.

See [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md#previewing-components-like-widgetbook) for more details.

