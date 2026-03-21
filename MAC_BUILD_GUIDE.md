# macOS Build Architecture Guide

## Problem: Intel vs Apple Silicon Compatibility

macOS computers come in two architectures:

1. **Intel Macs** (x86_64) - Pre-2020 models
2. **Apple Silicon Macs** (arm64) - M1, M2, M3 chips (2020+)

Applications built for one architecture **do not run natively** on the other:
- Intel builds (x86_64) won't run on Apple Silicon without Rosetta 2
- Apple Silicon builds (arm64) won't run on Intel Macs at all

## Solution: Separate Builds per Architecture

The GitHub Actions workflow now builds **two separate DMG files**:

### GitHub Actions Workflow

The workflow (`.github/workflows/build.yml`) builds macOS installers on two different runners:

```yaml
# Intel Mac build (x86_64)
build-macos-intel:
  runs-on: macos-13  # macOS 13 runs on Intel hardware
  steps:
    - uses: actions/setup-java@v4
      with:
        architecture: x64  # Intel Java
    - run: ./gradlew packageDmg

# Apple Silicon build (arm64)
build-macos-arm:
  runs-on: macos-latest  # Latest macOS runs on Apple Silicon
  steps:
    - uses: actions/setup-java@v4
      with:
        architecture: arm64  # ARM Java
    - run: ./gradlew packageDmg
```

### Downloading from GitHub Releases

After a successful workflow run, you'll find **two separate DMG artifacts**:

1. **installer-dmg-intel** - For Intel Macs (x86_64)
2. **installer-dmg-arm** - For Apple Silicon Macs (arm64)

**Choose the correct version for your Mac:**

```bash
# Check your Mac's architecture
uname -m

# Output:
# x86_64  → Intel Mac → Download installer-dmg-intel
# arm64   → Apple Silicon → Download installer-dmg-arm
```

Or use System Information:
- Click Apple menu → About This Mac
- Look for "Chip" (Apple Silicon) or "Processor" (Intel)

## Why Not Universal Binaries?

You might wonder why we don't create a single universal binary. Here's why:

### Compose Desktop Limitations

JetBrains Compose Desktop uses `jpackage` which:
- Bundles a JRE specific to the build architecture
- Doesn't support creating universal macOS apps automatically
- The bundled JRE is architecture-specific

### Workaround Complexity

Creating universal binaries would require:
1. Building twice (once per architecture)
2. Manually merging the app bundles
3. Creating universal JRE bundles (complex, error-prone)
4. Code signing both architectures properly

**Separate builds are simpler and more reliable.**

## Local Development

When building locally on your Mac:

```bash
# Builds for YOUR Mac's architecture only
./gradlew packageDmg
```

**Intel Mac**: Creates x86_64 binary
- Runs natively on Intel Macs
- Runs under Rosetta 2 on Apple Silicon (slower)

**Apple Silicon Mac**: Creates arm64 binary
- Runs natively on M1/M2/M3 Macs
- **Does not run on Intel Macs**

### Testing on Both Architectures

If you develop on one architecture but need to test on both:

1. **Use GitHub Actions artifacts** - Download both builds and test
2. **Use Rosetta 2** (Intel → Apple Silicon only):
   ```bash
   # On Apple Silicon, run Intel build with Rosetta
   arch -x86_64 /Applications/Petrie\ Image\ Importer.app/Contents/MacOS/Petrie\ Image\ Importer
   ```

## File Naming Convention

The workflow separates artifacts to avoid confusion:

```
GitHub Release Assets:
├── Petrie Image Importer-intel.dmg    # Intel Macs
├── Petrie Image Importer-arm.dmg      # Apple Silicon
├── Petrie Image Importer.msi          # Windows
└── Petrie Image Importer.deb          # Linux
```

## Verification

After downloading, verify the architecture:

```bash
# Mount the DMG
hdiutil attach "Petrie Image Importer-intel.dmg"

# Check the executable architecture
file "/Volumes/Petrie Image Importer/Petrie Image Importer.app/Contents/MacOS/Petrie Image Importer"

# Expected output:
# Intel:    Mach-O 64-bit executable x86_64
# Apple Silicon: Mach-O 64-bit executable arm64
```

## Troubleshooting

### "App can't be opened" on Intel Mac

You downloaded the ARM version. Download the Intel version instead.

### "App can't be opened" on Apple Silicon

You downloaded the Intel version. Download the ARM version for native support, or:
- Right-click → Open → Open (to use Rosetta 2)
- Or download the correct ARM version

### Rosetta 2 Not Installed (Apple Silicon)

If trying to run Intel build on Apple Silicon:
```bash
# Install Rosetta 2
softwareupdate --install-rosetta
```

## Summary

✅ **GitHub Actions** builds both architectures separately
✅ **Download the correct DMG** for your Mac
✅ **Check architecture** with `uname -m` or About This Mac
✅ **Local builds** are for your current Mac only

This approach ensures maximum compatibility and performance for all Mac users.
