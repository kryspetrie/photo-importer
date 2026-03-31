package org.kryspetrie.fileimport.di

import org.koin.dsl.module
import org.kryspetrie.fileimport.application.DuplicateScannerService
import org.kryspetrie.fileimport.application.ImportService
import org.kryspetrie.fileimport.application.PerspectiveCorrectionService
import org.kryspetrie.fileimport.application.PhotoScanDetectorService
import org.kryspetrie.fileimport.application.PhotoScanExportService
import org.kryspetrie.fileimport.application.ReorganizeService
import org.kryspetrie.fileimport.application.ScanService
import org.kryspetrie.fileimport.application.WatchFolderService
import org.kryspetrie.fileimport.domain.port.*
import org.kryspetrie.fileimport.infrastructure.adapter.*

/**
 * Koin dependency injection module for the Petrie File Importer application.
 *
 * This module defines all dependencies and their wiring for the application. It follows the
 * Hexagonal Architecture pattern by:
 * - Registering infrastructure adapters as implementations of domain ports
 * - Registering application services that orchestrate use cases
 * - Managing dependency injection throughout the application
 *
 * ## Architecture Overview
 *
 * ```
 * ┌─────────────────────────────────────────────────────────────┐
 * │                    UI Layer (Compose)                        │
 * │  koinInject<ImportService>()                                │
 * │  koinInject<NamingPort>()                                   │
 * ├─────────────────────────────────────────────────────────────┤
 * │                    Application Layer                         │
 * │  ImportService · ReorganizeService · DuplicateScannerService│
 * │  WatchFolderService                                         │
 * ├─────────────────────────────────────────────────────────────┤
 * │                      Domain Layer                            │
 * │  Ports: ImageRepositoryPort, SettingsPort, NamingPort,      │
 * │         DeduplicationPort, HashCachePort, DevicePort        │
 * ├─────────────────────────────────────────────────────────────┤
 * │                   Infrastructure Layer                       │
 * │  Adapters: ImageRepositoryAdapter, SettingsAdapter,         │
 * │            NamingAdapter, DeduplicationAdapter, etc.        │
 * └─────────────────────────────────────────────────────────────┘
 * ```
 *
 * ## Dependency Graph
 *
 * ```
 * ImportService
 * ├── ImageRepositoryPort (ImageRepositoryAdapter)
 * ├── NamingPort (NamingAdapter)
 * ├── DevicePort (DeviceAdapter)
 * ├── HashCachePort (HashCacheAdapter)
 * └── DeduplicationPort (DeduplicationAdapter)
 *     └── ImageRepositoryPort (injected)
 *
 * ReorganizeService
 * ├── ImageRepositoryPort
 * └── NamingPort
 *
 * DuplicateScannerService
 * ├── ImageRepositoryPort
 * ├── DeduplicationPort
 * └── HashCachePort
 *
 * WatchFolderService
 * └── ImportService
 * ```
 *
 * ## Usage
 *
 * ### In Composables
 *
 * ```kotlin
 * @Composable
 * fun ImportScreen() {
 *     // Inject services directly in composable
 *     val importService = koinInject<ImportService>()
 *     val namingPort = koinInject<NamingPort>()
 *
 *     // Use services...
 * }
 * ```
 *
 * ### In Regular Classes
 *
 * ```kotlin
 * class MyViewModel {
 *     private val importService: ImportService by inject()
 *
 *     fun loadData() {
 *         importService.import(...)
 *     }
 * }
 * ```
 *
 * ## Koin vs Spring
 *
 * For Spring developers, Koin is similar to Spring's dependency injection but:
 * - **Compile-time safe**: No reflection, errors caught at compile time
 * - **Simpler**: No annotations, just DSL configuration
 * - **Kotlin-first**: Uses Kotlin DSL instead of XML or annotations
 * - **Lightweight**: Minimal overhead, perfect for desktop/mobile apps | Spring | Koin |
 *   |----------------------------|---------------------------------| | `@Autowired` | `by inject()`
 *   or `koinInject()` | | `@Configuration` + `@Bean` | `module { single { } }` | |
 *   `ApplicationContext` | `startKoin { modules(...) }` | | Component scanning | Explicit
 *   registration |
 *
 * ## Scope Types
 *
 * This module uses `single` scope for all dependencies:
 * - **single**: Singleton - one instance for entire application lifetime
 * - Other options: `factory` (new instance each time), `scoped` (custom scope)
 *
 * All services and adapters are singletons because they:
 * - Are stateless or manage their own state internally
 * - Are expensive to create (database connections, file systems)
 * - Need to share state (e.g., hash cache)
 *
 * @see org.koin.dsl.module Koin module DSL
 * @see org.koin.core.context.startKoin Koin initialization
 * @see ImportService Main import orchestration service
 * @see ImageRepositoryPort Domain port for image operations
 * @see ImageRepositoryAdapter Infrastructure implementation
 */
val appModule = module {
  // ==================== DOMAIN PORTS (Interfaces) ====================

  /**
   * Image repository port implementation.
   *
   * Provides access to the photo library database/storage. Used for:
   * - Storing image metadata
   * - Querying existing images
   * - Tracking import history
   *
   * Implementation: [ImageRepositoryAdapter] (SQLite-backed)
   */
  single<ImageRepositoryPort> { ImageRepositoryAdapter() }

  /**
   * Settings port implementation.
   *
   * Provides access to application settings and preferences. Used for:
   * - Loading/saving import profiles
   * - Persisting theme preference
   * - Storing window state
   *
   * Implementation: [SettingsAdapter] (JSON file storage)
   */
  single<SettingsPort> { SettingsAdapter() }

  /**
   * Naming port implementation.
   *
   * Handles folder and filename pattern resolution. Used for:
   * - Generating folder paths from patterns
   * - Generating filenames from patterns
   * - Resolving placeholders ({yyyy}, {camera}, etc.)
   *
   * Implementation: [NamingAdapter] (pattern engine)
   */
  single<NamingPort> { NamingAdapter() }

  /**
   * Deduplication port implementation.
   *
   * Handles duplicate detection and resolution. Used for:
   * - Computing file hashes
   * - Perceptual hash comparison
   * - SURF feature matching
   * - EXIF-based duplicate detection
   *
   * Implementation: [DeduplicationAdapter] (requires ImageRepositoryPort)
   *
   * @see DeduplicationAdapter Wraps multiple duplicate detection strategies
   */
  single<DeduplicationPort> { DeduplicationAdapter(get()) }

  /**
   * Hash cache port implementation.
   *
   * Provides fast lookup of previously computed file hashes. Used for:
   * - Caching file hashes to avoid recomputation
   * - Speeding up duplicate detection
   * - Tracking imported files
   *
   * Implementation: [HashCacheAdapter] (SQLite-backed)
   */
  single<HashCachePort> { HashCacheAdapter() }

  /**
   * Device port implementation.
   *
   * Handles camera and storage device detection. Used for:
   * - Detecting connected cameras
   * - Monitoring device hot-plug events
   * - Getting device mount points
   *
   * Implementation: [DeviceAdapter] (OS-specific device enumeration)
   */
  single<DevicePort> { DeviceAdapter() }

  /**
   * Import history adapter.
   *
   * Tracks past import operations. Note: Not a port, used directly by UI and services.
   *
   * Used for:
   * - Recording import results
   * - Displaying import history
   * - Detecting already-imported files
   */
  single { ImportHistoryAdapter() }

  // ==================== APPLICATION SERVICES ====================

  /**
   * Import service - orchestrates the photo import workflow.
   *
   * Main application service that coordinates the entire import process:
   * 1. Scan source for media files
   * 2. Read EXIF metadata
   * 3. Detect duplicates
   * 4. Generate destination paths
   * 5. Copy files with verification
   * 6. Update import history
   *
   * Dependencies:
   * - [ImageRepositoryPort]: Store/retrieve image metadata
   * - [NamingPort]: Generate folder/filename patterns
   * - [DevicePort]: Detect cameras
   * - [HashCachePort]: Cache file hashes
   * - [DeduplicationPort]: Detect duplicates
   *
   * @see ImportService Main import orchestration logic
   */
  single {
    ImportService(
        imageRepository = get(),
        deduplicationPort = get(),
        namingPort = get(),
        devicePort = get(),
        hashCache = get())
  }

  /**
   * Reorganize service - orchestrates library reorganization.
   *
   * Handles reorganizing existing photo libraries:
   * 1. Scan existing library
   * 2. Generate new paths based on patterns
   * 3. Preview changes
   * 4. Execute reorganization with undo support
   *
   * Dependencies:
   * - [ImageRepositoryPort]: Access image metadata
   * - [NamingPort]: Generate new paths
   *
   * @see ReorganizeService Reorganization logic
   */
  single { ReorganizeService(get(), get()) }

  /**
   * Duplicate scanner service - orchestrates duplicate detection.
   *
   * Handles scanning libraries for duplicates:
   * 1. Scan library for all images
   * 2. Compute hashes and fingerprints
   * 3. Group duplicates
   * 4. Provide resolution options
   *
   * Dependencies:
   * - [ImageRepositoryPort]: Access image metadata
   * - [DeduplicationPort]: Detect duplicates
   * - [HashCachePort]: Cache hashes
   *
   * @see DuplicateScannerService Duplicate detection logic
   */
  single { DuplicateScannerService(get(), get(), get()) }

  /**
   * Watch folder service - monitors folders for new files.
   *
   * Implements automatic import from watched folders:
   * 1. Monitor configured folders
   * 2. Detect new files
   * 3. Trigger automatic import
   * 4. Report status to UI
   *
   * Dependencies:
   * - [ImportService]: Execute imports
   *
   * @see WatchFolderService Folder monitoring logic
   */
  single { WatchFolderService(get()) }

  /**
   * Scan service - orchestrates photo scan operations.
   *
   * Handles scanning photos from images that contain multiple photos on a solid background:
   * 1. Detect photo corners using edge detection
   * 2. Allow manual corner adjustment via UI
   * 3. Export multiple photos with filename incrementing
   * 4. Support metadata override for each photo
   *
   * Dependencies:
   * - [ImageRepositoryPort]: Store/retrieve image metadata
   * - [NamingPort]: Generate folder/filename patterns
   *
   * @see ScanService Photo scan logic
   */
  single { ScanService(get(), get()) }

  /**
   * Photo scan detector service - detects corners of photos in scanned images.
   *
   * Uses BoofCV edge detection to find rectangular shapes that likely represent photographs on a
   * solid background.
   *
   * @see PhotoScanDetectorService Corner detection using BoofCV
   */
  single { PhotoScanDetectorService() }

  /**
   * Perspective correction service - corrects perspective distortion in photos.
   *
   * Applies projective transformation to extract a quadrilateral region from a scanned image and
   * warp it to a rectangle.
   *
   * @see PerspectiveCorrectionService Perspective correction using Java3D math
   */
  single { PerspectiveCorrectionService() }

  /**
   * Photo scan export service - exports extracted photos with metadata.
   *
   * Handles the complete export pipeline:
   * 1. Perspective correction of extracted photos
   * 2. EXIF metadata preservation and modification
   * 3. Incremental filename generation
   * 4. Writing images with Apache Imaging
   *
   * Dependencies:
   * - [PerspectiveCorrectionService]: For correcting perspective distortion
   *
   * @see PhotoScanExportService Export with EXIF metadata
   */
  single { PhotoScanExportService(get()) }
}
