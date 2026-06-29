# Petrie File Importer — Session Progress Notes

**Date**: 2026-06-29
**Git HEAD**: `5d40a96` — feat: improve map tile rendering
**Working directory**: `/Users/krys.petrie/dev/petrie-file-importer`

---

## How to Resume

All 10 refactoring phases are complete. The project is in a stable, clean state.

---

## Project Overview

Kotlin/Compose Desktop photo scanning application using hexagonal architecture (Ports & Adapters). Domain layer has no framework dependencies. UI depends on Application and Domain. Infrastructure implements Domain ports.

---

## Refactoring Plan Status

| Phase | Description | Status |
|-------|-------------|--------|
| 1 | FaceRegion, OverrideState, AspectRatio unification | ✅ Done |
| 2 | DomainDefaults, ScanService, ExifValueResolver, GeometryUtils, pickKeeper | ✅ Done |
| 3 | PhotoConfiguration ↔ PhotoScanConfiguration unification | ✅ Done |
| 4 | Extract shared MetadataEditorPane | ✅ Done |
| 5 | Decompose PhotoScanExportService (export/ subpackage) | ✅ Done |
| 6 | Introduce ImageProcessingPort — eliminate BufferedImage from application | ✅ Done |
| 7 | Decompose PhotoScanWizardState (God Object) | ✅ Done |
| 8a | Move BoundingBox, BoundingBoxList typealiases to domain | ✅ Done |
| 8b | Split ImportProfile.kt | ✅ Done |
| 9 | Create FileSystemPort — abstract java.io.File operations | ✅ Done |
| 10 | Coordinate unification | ✅ Done |

**✅ All 10 phases complete!**

---

## Architecture Audit (Final)

| Layer | java.io.File | java.awt | javax.imageio | Status |
|-------|-------------|----------|---------------|--------|
| Domain | ImageFile.kt only (deprecated property) | None | None | ✅ Clean |
| Application | None | None | None | ✅ Clean |
| Infrastructure | At boundaries (by design) | As needed | As needed | ✅ Acceptable |
| UI | At boundaries (by design) | As needed | As needed | ✅ Acceptable |

---

## Key Architecture Achievements

1. **Domain is pure Kotlin** — no AWT, no I/O frameworks, no infrastructure imports
2. **Application layer is clean** — no `java.io.File`, no `java.awt`, no `javax.imageio`
3. **FileSystemPort** abstracts all file I/O behind a domain interface
4. **ImageProcessingPort** abstracts all image manipulation behind a domain interface
5. **ProcessedImage** is a domain interface — `BufferedImage` stays in infrastructure
6. **Metadata writers** are injectable classes using byte-array APIs instead of File
7. **Coordinate types** unified with conversion methods between PhotoCorner and Point

---

## Key File Locations

- **Domain models**: `src/main/kotlin/org/kryspetrie/fileimport/domain/model/`
- **Domain ports**: `src/main/kotlin/org/kryspetrie/fileimport/domain/port/`
- **Application services**: `src/main/kotlin/org/kryspetrie/fileimport/application/`
- **Infrastructure adapters**: `src/main/kotlin/org/kryspetrie/fileimport/infrastructure/adapter/`
- **DI module**: `src/main/kotlin/org/kryspetrie/fileimport/di/AppModule.kt`
- **ImageProcessingPort**: `src/main/kotlin/org/kryspetrie/fileimport/domain/port/ImageProcessingPort.kt`
- **AwtImageProcessingAdapter**: `src/main/kotlin/org/kryspetrie/fileimport/infrastructure/adapter/AwtImageProcessingAdapter.kt`
- **FileSystemPort**: `src/main/kotlin/org/kryspetrie/fileimport/domain/port/FileSystemPort.kt`
- **JvmFileSystemAdapter**: `src/main/kotlin/org/kryspetrie/fileimport/infrastructure/adapter/JvmFileSystemAdapter.kt`

## Testing

- Run tests: `cd /Users/krys.petrie/dev/petrie-file-importer && ./gradlew test`
- Build check: `cd /Users/krys.petrie/dev/petrie-file-importer && ./gradlew compileKotlin compileTestKotlin`

## Guardrails

- Read files before editing them
- Run tests after changes
- Commit at each checkpoint
- Architecture test enforces zero AWT imports in application layer
- Architecture test enforces zero infrastructure imports in application layer