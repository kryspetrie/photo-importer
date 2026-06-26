# Petrie File Importer — Session Progress Notes

**Date**: 2026-06-26
**Git HEAD**: `a928eb5 test: add FilenameResolver tests with FileSystemPort`
**Working directory**: `/Users/krys.petrie/dev/petrie-file-importer`

---

## How to Resume

Use the Goose CLI or continue the session.

---

## Project Overview

Kotlin/Compose Desktop photo scanning application using hexagonal architecture (Ports & Adapters). Domain layer has no framework dependencies. UI depends on Application and Domain. Infrastructure implements Domain ports.

---

## Completed This Session

### 1. FilenameResolver → FileSystemPort migration (commit `fd7c759`)
- Replaced `java.io.File` with `FileSystemPort` for `resolveFilenameConflict()` and `generateUniqueFileName()`
- Both methods now `suspend` (use `fileSystem.exists()`)
- `resolveFilenameConflict()` takes `FileSystemPort` + `FilePath` instead of `File`
- Made `PhotoScanExportPort.exportPhotos()` and `exportSinglePhoto()` suspend
- `PhotoScanExportService` injects `FileSystemPort`; uses it for exists, mkdirs, length, and FilenameResolver
- Updated AppModule DI; added `runBlocking` wrappers in test files

### 2. ScanService → FileSystemPort migration (commit `fd7c759`)
- injects `FileSystemPort`; uses `runBlocking { fileSystem.exists() }` instead of `File.exists()`
- Removed `java.io.File` import from `detectPhotos()` path

### 3. BackImageService → FileSystemPort migration (commit `1c92407`)
- Injects `FileSystemPort` via constructor; uses `runBlocking { fileSystem.exists() }` instead of `File.exists()`
- Updated AppModule DI and test files

### 4. WatchFolderService → FileSystemPort migration (commit `141f68a`)
- Injects `FileSystemPort`; uses `fileSystem.exists()` and `fileSystem.listDirectoriesRecursive()` instead of raw NIO File walking
- Added `walkTopDown()` and `listDirectoriesRecursive()` to `FileSystemPort` interface
- Updated AppModule DI and WatchFolderServiceTest

### 5. ScanService.exportPhoto → FilenameResolver (commit `27d4140`)
- Replaced duplicated `getUniqueOutputFile()` with `FilenameResolver.resolveFilenameConflict()`
- Uses `FileSystemPort.mkdirs()` instead of `File.mkdirs()`
- Removed `getUniqueOutputFile()` private method entirely

### 6. FilenameResolver tests (commit `a928eb5`)
- 10 tests covering `resolveFilenameConflict` and `generateUniqueFileName`
- Uses `FileSystemAdapter` and `@TempDir`

---

## Refactoring Plan Status

| Phase | Description | Status |
|-------|-------------|--------|
| 1 | FaceRegion, OverrideState, AspectRatio unification | ✅ Done |
| 2 | DomainDefaults, ScanService, ExifValueResolver, GeometryUtils, pickKeeper | ✅ Done |
| 3 | PhotoConfiguration ↔ PhotoScanConfiguration unification | ✅ Done |
| 4 | Extract shared MetadataEditorPane | ✅ Done |
| 5 | Decompose PhotoScanExportService (export/ subpackage) | ✅ Done |
| 6 | Introduce DomainImage | Not started (high risk, deferred) |
| 7 | Decompose PhotoScanWizardState (God Object) | ✅ Done |
| 8a | Move BoundingBox, BoundingBoxList typealiases to domain | ✅ Done |
| 8b | Split ImportProfile.kt | ✅ Done |
| 9 | Create FileSystemPort — abstract java.io.File operations | ✅ **Complete** |
| 10 | Coordinate unification | ✅ Done |

### Phase 9 — Final Status

**✅ Fully migrated to FileSystemPort:**
- DuplicateScannerService, FileOperationExecutor, ImportExecutor
- ReorganizeService, ReorganizeJournalRepository
- FilenameResolver, PhotoScanExportService
- ScanService (detectPhotos, exportPhoto)
- BackImageService (prepareBackImage)
- WatchFolderService (startWatching)

**⚠️ AWT-exception files (keep java.io.File — inherent AWT/Swing dependency):**
- ScanService (ImageIO.read/write in detectPhotos/exportPhoto)
- PhotoScanExportService (BufferedImage conversions via toBufferedImage/toProcessedImage)
- BackImageService (ImageIO.read)
- JpegImageWriter (ImageIO.write)
- MetadataWritingService (AWT pipeline orchestrator)
- ExifMetadataWriter, IptcMetadataWriter, XmpMetadataWriter (FileOutputStream)
- ImageTransformer (BufferedImage operations)

**⚠️ Documented infrastructure boundary exceptions (allowed in architecture test):**
- `PhotoScanExportService` imports `toBufferedImage`, `toProcessedImage` from infrastructure adapter
- `ScanService` imports `toProcessedImage` from infrastructure adapter

### FileSystemPort Interface (current state)
```kotlin
interface FileSystemPort {
    suspend fun lastModified(path: FilePath): Long
    suspend fun length(path: FilePath): Long
    suspend fun exists(path: FilePath): Boolean
    suspend fun delete(path: FilePath): Boolean
    suspend fun renameTo(source: FilePath, destination: FilePath): Boolean
    suspend fun mkdirs(path: FilePath): Boolean
    suspend fun isDirectory(path: FilePath): Boolean
    suspend fun listFiles(path: FilePath): List<FilePath>
    suspend fun copy(source: FilePath, destination: FilePath): Boolean
    fun name(path: FilePath): String = path.name
    fun nameWithoutExtension(path: FilePath): String = path.nameWithoutExtension
    fun extension(path: FilePath): String = path.extension
    fun canWrite(path: FilePath): Boolean
    fun absolutePath(path: FilePath): String = path.toFile().absolutePath
    fun walkBottomUp(path: FilePath): Sequence<FilePath>
    fun walkTopDown(path: FilePath): Sequence<FilePath>
    fun listDirectoriesRecursive(path: FilePath): List<FilePath>
    fun readText(path: FilePath): String = path.toFile().readText()
    fun writeText(path: FilePath, content: String)
}
```

---

## Remaining Work (Future Sessions)

### Phase 2b/6: Infrastructure import boundary exceptions
- `ScanService` and `PhotoScanExportService` import `toProcessedImage`/`toBufferedImage` from infrastructure adapter — these are documented exceptions for the `BufferedImage ↔ ProcessedImage` boundary. Resolving them requires Phase 6 (DomainImage wrapper).

### Phase 6: DomainImage (deferred — high risk)
- Wrap `BufferedImage` in a `DomainImage` value class so domain ports don't reference `java.awt`
- Would eliminate the `toProcessedImage`/`toBufferedImage` infrastructure adapter imports
- Currently deferred due to high risk and broad impact

---

## Key File Locations
- **Domain models**: `src/main/kotlin/org/kryspetrie/fileimport/domain/model/`
- **Domain ports**: `src/main/kotlin/org/kryspetrie/fileimport/domain/port/`
- **Application services**: `src/main/kotlin/org/kryspetrie/fileimport/application/`
- **Infrastructure adapters**: `src/main/kotlin/org/kryspetrie/fileimport/infrastructure/adapter/`
- **DI module**: `src/main/kotlin/org/kryspetrie/fileimport/di/AppModule.kt`

## Testing
- Run tests: `cd /Users/krys.petrie/dev/petrie-file-importer && ./gradlew test`
- Build check: `cd /Users/krys.petrie/dev/petrie-file-importer && ./gradlew compileKotlin compileTestKotlin`

## Guardrails
- Read files before editing them
- Run tests after changes
- Commit at each checkpoint
- AWT-exception files should NOT be migrated away from java.io.File — they inherently need it
- `toProcessedImage`/`toBufferedImage` are documented infrastructure boundary exceptions