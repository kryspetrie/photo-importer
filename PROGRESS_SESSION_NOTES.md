# Petrie File Importer — Session Progress Notes

**Date**: 2026-06-26
**Git HEAD**: *(will be updated after commit)*
**Working directory**: `/Users/krys.petrie/dev/petrie-file-importer`

---

## How to Resume

Use the Goose CLI or continue the session.

---

## Project Overview

Kotlin/Compose Desktop photo scanning application using hexagonal architecture (Ports & Adapters). Domain layer has no framework dependencies. UI depends on Application and Domain. Infrastructure implements Domain ports.

---

## Completed This Session

### Phase 6: Introduce ImageProcessingPort — Eliminate BufferedImage from Application Layer

**Goal**: Remove `java.awt.image.BufferedImage` and `javax.imageio` imports from all application services by introducing an `ImageProcessingPort` in domain and implementing it as `AwtImageProcessingAdapter` in infrastructure.

**Key changes:**

1. **Created `ImageProcessingPort`** (`domain/port/ImageProcessingPort.kt`)
   - `readImage(FilePath): ProcessedImage?` — reads image from disk
   - `writeJpegImage(ProcessedImage, FilePath, Float)` — writes JPEG to disk
   - `cropAxisAligned(ProcessedImage, DetectedPhoto): ProcessedImage` — axis-aligned crop
   - `rotateImage(ProcessedImage, RotationAngle): ProcessedImage` — image rotation
   - `compositeBackImage(ProcessedImage, PhotoScanConfiguration): ProcessedImage` — front+back composite
   - `prepareBackImage(PhotoScanConfiguration): ProcessedImage?` — loads, crops, rotates back image

2. **Created `AwtImageProcessingAdapter`** (`infrastructure/adapter/AwtImageProcessingAdapter.kt`)
   - Implements all `ImageProcessingPort` methods using `BufferedImage` internally
   - Injects `FileSystemPort` for file existence checks in `prepareBackImage`
   - Absorbs logic from `ImageTransformer`, `BackImageService`, and `JpegImageWriter`

3. **Refactored `PhotoScanExportService`**
   - Replaced `BufferedImage` parameters with `ProcessedImage`
   - Replaced `ImageTransformer.cropAxisAligned()` / `rotateImage()` calls with `imageProcessing.cropAxisAligned()` / `rotateImage()`
   - Replaced `BackImageService` calls with `imageProcessing.compositeBackImage()` / `prepareBackImage()`
   - Removed `toProcessedImage()`/`toBufferedImage()` infrastructure imports
   - Removed `java.awt.image.BufferedImage` import

4. **Refactored `ScanService`**
   - Replaced `ImageIO.read()` + `toProcessedImage()` with `imageProcessing.readImage()`
   - Replaced `ImageIO.write()` with `imageProcessing.writeJpegImage()`
   - Replaced `BufferedImage` params with `ProcessedImage`
   - Removed all `java.awt` / `javax.imageio` imports
   - Injected `ImageProcessingPort` alongside `FileSystemPort` and `PhotoScanDetectorPort`

5. **Refactored `MetadataWritingService`**
   - Changed `BufferedImage` params to `ProcessedImage`
   - Replaced `JpegImageWriter` with `ImageProcessingPort.writeJpegImage()`
   - Removed `java.awt.image.BufferedImage` import
   - Injected `ImageProcessingPort` instead of using inline `JpegImageWriter`

6. **Deleted dead code**
   - `application/export/ImageTransformer.kt` — logic moved to `AwtImageProcessingAdapter`
   - `application/export/BackImageService.kt` — logic moved to `AwtImageProcessingAdapter`
   - `application/export/JpegImageWriter.kt` — logic moved to `AwtImageProcessingAdapter`

7. **Updated architecture tests**
   - Removed AWT exception list (no application files import `java.awt`/`javax.imageio` anymore)
   - Removed infrastructure boundary converter exception list (no application files import from infrastructure anymore)
   - Application AWT test now has **zero exceptions** — enforces complete AWT freedom
   - Application infrastructure import test now has **zero exceptions** — enforces complete boundary

8. **Updated DI module (`AppModule.kt`)**
   - Registered `ImageProcessingPort` → `AwtImageProcessingAdapter`
   - `ScanService` now injects `ImageProcessingPort`
   - `MetadataWritingService` now injects `ImageProcessingPort`
   - `PhotoScanExportService` now injects `ImageProcessingPort` (was `JpegImageWriter` + `BackImageService`)
   - Removed `JpegImageWriter` and `BackImageService` bean registrations

9. **Fixed `ScanScreen.kt`**
   - `exportPhoto()` now takes `ProcessedImage` — UI uses `.toProcessedImage()` extension

10. **Fixed test files**
    - `PhotoScanExportServiceTest.kt` — constructs `AwtImageProcessingAdapter` instead of `JpegImageWriter` + `BackImageService`
    - `XmpFaceRegionExportTest.kt` — same pattern

---

## Refactoring Plan Status

| Phase | Description | Status |
|-------|-------------|--------|
| 1 | FaceRegion, OverrideState, AspectRatio unification | ✅ Done |
| 2 | DomainDefaults, ScanService, ExifValueResolver, GeometryUtils, pickKeeper | ✅ Done |
| 3 | PhotoConfiguration ↔ PhotoScanConfiguration unification | ✅ Done |
| 4 | Extract shared MetadataEditorPane | ✅ Done |
| 5 | Decompose PhotoScanExportService (export/ subpackage) | ✅ Done |
| 6 | Introduce ImageProcessingPort — eliminate BufferedImage from application | ✅ **Done** |
| 7 | Decompose PhotoScanWizardState (God Object) | ✅ Done |
| 8a | Move BoundingBox, BoundingBoxList typealiases to domain | ✅ Done |
| 8b | Split ImportProfile.kt | ✅ Done |
| 9 | Create FileSystemPort — abstract java.io.File operations | ✅ Done |
| 10 | Coordinate unification | ✅ Done |

### Phase 6 — Final Status

**✅ All 10 phases complete!**

**Application layer is now free of:**
- `java.awt.*` imports
- `javax.imageio.*` imports
- Infrastructure adapter imports (`toProcessedImage`, `toBufferedImage`)

**Remaining `java.io.File` in application (acceptable — metadata writers need raw file I/O):**
- `MetadataWritingService.kt` — delegates JPEG writing to `ImageProcessingPort` but still passes `File` to `ExifMetadataWriter`/`IptcMetadataWriter`/`XmpMetadataWriter`
- `PhotoScanExportService.kt` — `sourceFile?.toFile()` for metadata extraction
- These can be further abstracted with metadata ports (Phase 5 territory) but are low priority

**`ProcessedImage` abstraction:**
- Domain interface `ProcessedImage` with `width` and `height` properties
- `AwtProcessedImage` wraps `BufferedImage` in infrastructure
- `toProcessedImage()`/`toBufferedImage()` extension functions remain in infrastructure adapter (used by UI and adapter layer)
- `ImageProcessingPort` is the clean boundary for all pixel operations

---

## Key File Locations
- **Domain models**: `src/main/kotlin/org/kryspetrie/fileimport/domain/model/`
- **Domain ports**: `src/main/kotlin/org/kryspetrie/fileimport/domain/port/`
- **Application services**: `src/main/kotlin/org/kryspetrie/fileimport/application/`
- **Infrastructure adapters**: `src/main/kotlin/org/kryspetrie/fileimport/infrastructure/adapter/`
- **DI module**: `src/main/kotlin/org/kryspetrie/fileimport/di/AppModule.kt`
- **ImageProcessingPort**: `src/main/kotlin/org/kryspetrie/fileimport/domain/port/ImageProcessingPort.kt`
- **AwtImageProcessingAdapter**: `src/main/kotlin/org/kryspetrie/fileimport/infrastructure/adapter/AwtImageProcessingAdapter.kt`

## Testing
- Run tests: `cd /Users/krys.petrie/dev/petrie-file-importer && ./gradlew test`
- Build check: `cd /Users/krys.petrie/dev/petrie-file-importer && ./gradlew compileKotlin compileTestKotlin`

## Guardrails
- Read files before editing them
- Run tests after changes
- Commit at each checkpoint
- Architecture test enforces zero AWT imports in application layer
- Architecture test enforces zero infrastructure imports in application layer