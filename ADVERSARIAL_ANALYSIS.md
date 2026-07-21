# 🔍 Adversarial Analysis — Petrie File Importer

This report synthesizes a deep audit across all layers: application services, domain models, infrastructure adapters, and UI.

## 🔴 Critical — Data Loss & Corruption

| # | Issue | File | Impact |
|---|-------|------|--------|
| C1 | Sidecar copy failures silently swallowed | ImportExecutor.kt ~L150 | XMP/face metadata lost on import; no error recorded, file marked success=true |
| C2 | Redo uses already-overwritten file as EXIF source | MetadataEditUndoService.kt ~L156 | Progressive EXIF corruption on every undo→redo cycle |
| C3 | "Clear All Face Data" has NO confirmation dialog | PeopleScreen.kt | Single click destroys all face embeddings across every person |
| C4 | MOVE reorganize can destroy files with no recovery | ReorganizeService.kt | If original location no longer exists during undo, files are orphaned |
| C5 | pendingFiles race — events dropped between check and swap | WatchFolderService.kt ~L132 | Files arriving during swap are silently lost |
| C6 | Non-atomic state transitions in MediaImportViewModel | MediaImportViewModel.kt | COMPLETE with null data during recomposition |

## 🔴 Critical — Security

| # | Issue | File | Impact |
|---|-------|------|--------|
| C7 | Zip path traversal in importDirectory | JsonPersonDirectoryAdapter.kt ~L117 | Malicious ZIP with ../../persons.json |
| C8 | Path traversal via sanitizeFileName | NamingAdapter.kt ~L95 | Metadata can produce paths like target/../../etc/passwd.jpg |
| C9 | Wildcard delete in clearAllIndexes | HashCacheAdapter.kt | Deletes ALL files in index dir, not just .db; symlink attack |
| C10 | SSRF via NominatimGeocodingAdapter | NominatimGeocodingAdapter.kt | Follows redirects to arbitrary hosts; no lat/lon validation |
| C11 | No integrity check on downloaded ONNX models | HuggingFaceModelDownloadAdapter.kt | MITM can inject malicious models → code execution |

## 🔴 Critical — Logic Bugs

| # | Issue | File | Impact |
|---|-------|------|--------|
| C12 | computeConvexHull broken while-break | BoundingBox.kt ~L100 | Loop body is break; hull never padded to 4 points |
| C13 | doSegmentsIntersect misses collinear/endpoint cases | Geometry.kt ~L109 | Bowtie detection broken; invalid quadrilaterations accepted |
| C14 | decimalToGpsRationals fails for negative coordinates | ExifValueResolver.kt | Western/southern hemisphere coordinates produce wrong EXIF GPS |
| C15 | FloatArray in FaceEmbedding data class breaks equality | FaceEmbedding.kt | Two embeddings with identical vector values aren't equals() unless same ref |

## 🟠 Important — Concurrency & Race Conditions

| # | Issue | File | Impact |
|---|-------|------|--------|
| I1 | Settings read-then-write in setAutoDetectEnabled | PersonService.kt ~L268 | Concurrent settings changes can overwrite each other |
| I2 | findMatches/findBestMatch not mutex-protected | PersonService.kt ~L225 | Stale reads during mutations |
| I3 | Config/directory snapshot inconsistency in FaceGroupingService | FaceGroupingService.kt ~L59 | Two StateFlow reads at different points in time |
| I4 | SettingsAdapter TOCTOU — no Mutex on save | SettingsAdapter.kt ~L60 | Concurrent saves interleave |
| I5 | ImportHistoryAdapter TOCTOU + unbounded JSON | ImportHistoryAdapter.kt | Read-modify-write with no lock; concurrent adds lose entries |
| I6 | Settings sync loop in MediaImportScreen | MediaImportScreen.kt | LaunchedEffect writes back on every emission, overwriting user typing |

## 🟠 Important — Error Handling & Silent Failures

| # | Issue | File | Impact |
|---|-------|------|--------|
| I7 | ASK_USER conflict resolution silently skipped | ImportExecutor.kt ~L100 | No mechanism for interactive conflict resolution |
| I8 | DuplicateScannerService.resolveGroup swallows all exceptions | DuplicateScannerService.kt ~L86 | Failed deletions silently ignored |
| I9 | Reorganize partial undo doesn't update journal | ReorganizeService.kt ~L202 | No record of which files succeeded/failed |
| I10 | MetadataEditorViewModel isLoading stuck true on error | MetadataEditorViewModel.kt | Perpetual spinner on error |
| I11 | WatchFolderService hard stop after 3 errors, no backoff | WatchFolderService.kt ~L136 | Auto-import stops silently |

## 🟠 Important — API Design & Validation

| # | Issue | File | Impact |
|---|-------|------|--------|
| I12 | Person default name="" violates non-blank invariant | Person.kt ~L30 | Deserializing JSON with missing name produces invalid person |
| I13 | PhotoScanConfiguration GPS fields accept any string | PhotoScanConfiguration.kt ~L115 | decimalToGpsRationals("hello") produces garbage EXIF |
| I14 | rotationDegrees allows arbitrary ints | PhotoScanConfiguration.kt ~L20 | Only 0/90/180/270 make sense |
| I15 | FaceMatchingConfig no threshold validation | FaceEmbedding.kt ~L262 | Invalid thresholds cause confusing behavior |
| I16 | FileSystemPort default methods leak JVM I/O | FileSystemPort.kt | Tests with mock ports silently call real filesystem |
| I17 | DetectedPhoto getWidth/getHeight truncation error | PhotoScanModels.kt ~L168 | toFloat().toInt() truncates; should round |
| I18 | NamingAdapter doesn't prevent .. path traversal | NamingAdapter.kt | Complements C8 |
| I19 | JsonLocaleAdapter locale code path traversal | JsonLocaleAdapter.kt ~L100 | localeCode with .. reads arbitrary files |

## 🟠 Important — Resource Management & Performance

| # | Issue | File | Impact |
|---|-------|------|--------|
| I20 | ScanService uses runBlocking on suspend operations | ScanService.kt ~L37 | Blocks calling thread; can deadlock |
| I21 | MetadataEditJournalRepository uses runBlocking | MetadataEditJournalRepository.kt | Same issue |
| I22 | Raw thumbnail extraction loads 20MB per file | RawThumbnailExtractor.kt ~L70 | OOM on low-memory systems |
| I23 | ONNX session creation failures leak resources | PhotoScanDetectorService.kt ~L80 | Second/third session failure orphans first sessions |
| I24 | JsonPersonDirectoryAdapter saveDirectory not atomic | JsonPersonDirectoryAdapter.kt ~L67 | Crash mid-write → truncated JSON → total data loss |
| I25 | Tab switching destroys wizard/scanner state | PetrieFileImporterApp.kt | Switching tabs disposes wizard; progress lost |
| I26 | ImageRepositoryAdapter copyFile no fsync | ImageRepositoryAdapter.kt ~L170 | Crash/power-loss → zero-length file |

## 🟡 Minor — Code Quality & Edge Cases

| # | Issue | File |
|---|-------|------|
| M1 | duplicateCount always 0 (dead variable) | ImportExecutor.kt ~L48 |
| M2 | KEEP_SOURCE EXIF strategy behaves identically to OVERRIDE | ExifMetadataWriter.kt |
| M3 | FilenameResolver unbounded conflict loop | FilenameResolver.kt ~L23 |
| M4 | WatchFolderManager coroutine leak from status collect | WatchFolderManager.kt |
| M5 | DomainDefaults uses java.time in domain layer | DomainDefaults.kt |
| M6 | BoundingBoxList canAdd overload inconsistency | BoundingBoxList.kt |
| M7 | NormalizedRect toPixels truncation instead of rounding | FaceEmbedding.kt ~L260 |
| M8 | BoundingBoxList nextFrom doesn't bounds-check index | BoundingBoxList.kt ~L73 |
| M9 | MetadataHistory addValue silently discards misspelled keys | MetadataHistory.kt ~L86 |
| M10 | ReorganizeOperation journalEntry.operationType can diverge | ReorganizeOperation.kt |
| M11 | Video thumbnail cache unbounded on disk | VideoThumbnailAdapter.kt |
| M12 | HuggingFaceModelDownloadAdapter download race condition | HuggingFaceModelDownloadAdapter.kt |
| M13 | AwtImageProcessingAdapter prepareBackImage uses runBlocking | AwtImageProcessingAdapter.kt |
| M14 | DetectedPhoto getBounds truncation (same as I17) | PhotoScanModels.kt |
| M15 | ImportService detectTransferredByExif declared but not implemented | ImportService.kt ~L90 |
| M16 | RAW+JPEG pairing by filename only — cross-directory false positives | ImportService.kt ~L130 |
| M17 | LocationSearchService isSearching stuck true after job cancellation | LocationSearchService.kt |
| M18 | Inconsistent error display across screens | Multiple UI files |
| M19 | Multiple accessibility issues | Multiple UI files |
| M20 | MetadataEditorViewModel destCanCreate false for existing valid directories | MetadataEditorViewModel.kt |