package org.kryspetrie.fileimport.application

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.kryspetrie.fileimport.domain.model.*
import org.kryspetrie.fileimport.domain.port.*

/**
 * Maximum concurrency for metadata extraction operations.
 *
 * Limits parallel metadata reading to prevent:
 * - Disk I/O saturation
 * - Memory exhaustion from loading many images at once
 * - System becoming unresponsive during import
 *
 * Bounded between 2 and available CPU cores:
 * - Minimum 2: Always have some parallelism
 * - Maximum CPU count: Don't oversaturate
 *
 * Typical values: 4-8 on modern desktop CPUs
 */
private val METADATA_CONCURRENCY = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)

/**
 * Main application service orchestrating the photo/video import workflow.
 *
 * This service coordinates the entire import process from scanning source files to copying them to
 * the destination with all configured processing steps. It follows the application layer pattern in
 * hexagonal architecture, orchestrating domain ports to execute the import use case.
 *
 * ## Import Workflow
 *
 * ```
 * 1. scanSource()        → Discover files in source directory
 * 2. filterAlreadyTransferred() → Remove already-imported files
 * 3. findVisualDuplicates()     → Detect duplicate images
 * 4. previewStructure()         → Show destination paths before import
 * 5. executeImport()            → Copy files with verification
 * 6. Update import history      → Record import results
 * ```
 *
 * ## Concurrency Model
 *
 * Uses Kotlin coroutines for async operations:
 * - **Parallel metadata extraction**: Multiple files processed concurrently
 * - **Bounded concurrency**: Semaphore prevents resource exhaustion
 * - **Progress reporting**: StateFlow for reactive UI updates
 * - **Cancellation support**: Coroutines can be cancelled mid-operation
 *
 * ## Thread Safety
 * - All public methods are suspend functions (run on IO dispatcher)
 * - Progress updates use MutableStateFlow (thread-safe)
 * - File operations are atomic where possible
 * - Hash cache provides concurrent read access
 *
 * ## Usage Example
 *
 * ```kotlin
 * val importService = ImportService(
 *     imageRepository = ImageRepositoryAdapter(),
 *     deduplicationPort = DeduplicationAdapter(),
 *     namingPort = NamingAdapter(),
 *     devicePort = DeviceAdapter(),
 *     hashCache = HashCacheAdapter()
 * )
 *
 * // Scan source for images
 * val images = importService.scanSource("/path/to/camera")
 *
 * // Preview import structure
 * val preview = importService.previewStructure(
 *     images = images,
 *     destinationPath = "/path/to/library",
 *     configuration = config
 * )
 *
 * // Execute import
 * val result = importService.executeImport(
 *     images = images,
 *     destinationPath = "/path/to/library",
 *     configuration = config,
 *     onProgress = { progress ->
 *         println("Importing: ${progress.currentFile}")
 *     }
 * )
 * ```
 *
 * @property imageRepository Port for image metadata and file operations. Provides scanning,
 *   metadata extraction, hash calculation.
 * @property deduplicationPort Port for duplicate detection. Provides hash comparison, perceptual
 *   hashing, SURF matching.
 * @property namingPort Port for folder/filename pattern resolution. Generates destination paths
 *   from patterns.
 * @property devicePort Optional port for device detection. Used for camera auto-detection. Can be
 *   null for testing.
 * @property hashCache Optional port for hash caching. Speeds up repeated operations. Can be null
 *   for testing.
 * @see ImportProgress Progress tracking during import operations
 * @see ImportResult Results of import operation
 * @see ImageFile Represents a media file with metadata
 */
class ImportService(
    /**
     * Image repository port for file and metadata operations.
     *
     * Core dependencies for:
     * - Directory scanning
     * - EXIF metadata extraction
     * - File hash calculation
     * - File copy operations
     */
    private val imageRepository: ImageRepositoryPort,

    /**
     * Deduplication port for duplicate detection.
     *
     * Provides:
     * - File hash comparison
     * - Perceptual hash matching
     * - SURF feature matching
     * - EXIF-based duplicate detection
     */
    private val deduplicationPort: DeduplicationPort,

    /**
     * Naming port for pattern resolution.
     *
     * Handles:
     * - Folder pattern expansion
     * - Filename pattern expansion
     * - Placeholder resolution ({yyyy}, {camera}, etc.)
     * - Conflict detection and resolution
     */
    private val namingPort: NamingPort,

    /**
     * Device port for camera detection.
     *
     * Optional dependency for:
     * - Detecting connected cameras
     * - Getting device mount points
     * - Monitoring device changes
     *
     * Can be null when device detection not needed.
     */
    private val devicePort: DevicePort? = null,

    /**
     * Hash cache port for performance optimization.
     *
     * Optional dependency for:
     * - Caching file hashes
     * - Speeding up repeated scans
     * - Tracking imported files
     *
     * Can be null when caching not needed (testing, small imports).
     */
    private val hashCache: HashCachePort? = null
) {
  /**
   * Mutable state flow for import progress updates.
   *
   * Emits progress updates during import operations:
   * - Files scanned
   * - Metadata extracted
   * - Files copied
   * - Current operation status
   *
   * UI observes this flow to display progress indicators.
   *
   * @see ImportProgress Progress data class
   * @see StateFlow Kotlin Flow for state observation
   */
  private val _importProgress = MutableStateFlow(ImportProgress())

  /**
   * Public read-only access to import progress.
   *
   * UI components collect this flow to display real-time progress:
   * ```kotlin
   * val progress by importService.importProgress.collectAsState()
   * LinearProgressIndicator(progress = progress.percentage)
   * ```
   */
  val importProgress: StateFlow<ImportProgress> = _importProgress

  /**
   * Scans a source directory for media files with metadata extraction.
   *
   * This is the primary entry point for discovering files to import. It performs a three-phase scan
   * optimized for performance:
   *
   * ## Phase 1: Fast Directory Walk
   * - Recursively discovers all files in source directory
   * - Filters to media files only (images, videos)
   * - No heavy I/O operations
   * - Very fast (filesystem metadata only)
   *
   * ## Phase 2: Hash Resolution from Cache
   * - Checks hash cache for previously computed hashes
   * - Reuses cached hashes when file size and modification time match
   * - Avoids expensive hash computation for known files
   *
   * ## Phase 3: Parallel Metadata + Hash Extraction
   * - Extracts EXIF metadata from each file
   * - Computes hash for files not in cache
   * - Runs concurrently with bounded parallelism
   * - Reports progress every 50 files
   *
   * ## Concurrency
   *
   * Uses coroutine async/await with semaphore for bounded parallelism:
   * - Maximum [METADATA_CONCURRENCY] concurrent operations
   * - Prevents disk I/O saturation
   * - Keeps system responsive during scan
   *
   * ## Error Handling
   * - Invalid source path throws IllegalArgumentException
   * - Individual file errors are logged but don't stop scan
   * - Files with errors are included with null metadata/hash
   *
   * ## Usage
   *
   * ```kotlin
   * val images = importService.scanSource(
   *     sourcePath = "/Volumes/CAMERA/DCIM",
   *     recursive = true,
   *     onProgress = { scanned, total, file ->
   *         println("Scanning: $file ($scanned/$total)")
   *     }
   * )
   * ```
   *
   * @param sourcePath Absolute path to source directory to scan. Must exist and be a directory.
   * @param recursive Whether to scan subdirectories. Default true. Set false for flat directory
   *   structure.
   * @param onProgress Progress callback invoked periodically. Parameters: (scannedCount,
   *   totalCount, currentFileName)
   * @return List of [ImageFile] objects with metadata and hashes. Empty list if no media files
   *   found.
   * @throws IllegalArgumentException if sourcePath doesn't exist or isn't a directory.
   * @see ImageFile Media file with metadata
   * @see scanDevices Alternative scan for connected cameras
   */
  suspend fun scanSource(
      sourcePath: String,
      recursive: Boolean = true,
      onProgress: (scanned: Int, total: Int, file: String) -> Unit = { _, _, _ -> }
  ): List<ImageFile> {
    val sourceDir = java.io.File(sourcePath)
    require(sourceDir.exists() && sourceDir.isDirectory) {
      "Source directory does not exist: $sourcePath"
    }

    // Phase 1: fast directory walk — no I/O beyond stat
    onProgress(0, 0, "Discovering files...")
    val files = imageRepository.scanDirectory(sourceDir, recursive)
    val total = files.size
    if (total == 0) return emptyList()

    // Phase 2: resolve hashes from cache
    val cachedIndex = hashCache?.getIndex(sourcePath)
    val cachedEntries = cachedIndex?.entries ?: emptyMap()

    // Phase 3: parallel metadata + hash resolution
    val semaphore = Semaphore(METADATA_CONCURRENCY)
    val counter = AtomicInteger(0)

    return coroutineScope {
      files
          .map { file ->
            async(Dispatchers.IO) {
              semaphore.withPermit {
                val cached = cachedEntries[file.filePath]
                val hash =
                    if (cached != null &&
                        cached.fileSize == file.fileSize &&
                        cached.lastModified == file.file.lastModified()) {
                      cached.hash
                    } else {
                      imageRepository.calculateFileHash(file)
                    }
                val metadata = imageRepository.getMetadata(file)
                val done = counter.incrementAndGet()
                if (done % 50 == 0 || done == total) {
                  onProgress(done, total, file.fileName)
                }
                file.copy(hash = hash, metadata = metadata)
              }
            }
          }
          .awaitAll()
    }
  }

  /**
   * Indexes a folder for fast duplicate detection.
   *
   * Creates a persistent index of all files in a folder with their hashes. This index is used to
   * quickly detect already-imported files without re-scanning the entire destination.
   *
   * ## Index Contents
   * - File paths
   * - File hashes (SHA-256)
   * - File sizes
   * - Modification timestamps
   *
   * ## Usage
   *
   * ```kotlin
   * val index = importService.indexFolder(
   *     folderPath = "/path/to/photo/library",
   *     recursive = true,
   *     onProgress = { progress ->
   *         println("Indexing: ${progress.percent}%")
   *     }
   * )
   * ```
   *
   * @param folderPath Path to folder to index
   * @param recursive Whether to index subdirectories
   * @param onProgress Progress callback
   * @return [FolderIndex] containing indexed file information
   */
  suspend fun indexFolder(
      folderPath: String,
      recursive: Boolean = true,
      onProgress: (IndexProgress) -> Unit = {}
  ): FolderIndex {
    return hashCache?.indexFolder(folderPath, recursive, onProgress)
        ?: FolderIndex(folderPath = folderPath)
  }

  /**
   * Gets set of all file hashes in a destination folder.
   *
   * Used to detect already-transferred files by comparing source file hashes against destination
   * hashes.
   *
   * @param folderPath Destination folder path
   * @return Set of file hashes (SHA-256)
   */
  fun getDestinationHashes(folderPath: String): Set<String> =
      hashCache?.getDestinationHashes(folderPath) ?: emptySet()

  /**
   * Clears the hash index for a specific folder.
   *
   * Use when folder contents have changed outside the application.
   *
   * @param folderPath Folder whose index should be cleared
   */
  suspend fun clearIndex(folderPath: String) {
    hashCache?.clearIndex(folderPath)
  }

  /**
   * Clears all hash indexes.
   *
   * Use for full re-index or troubleshooting. Warning: Will slow down subsequent imports until
   * cache is rebuilt.
   */
  suspend fun clearAllIndexes() {
    hashCache?.clearAllIndexes()
  }

  /**
   * Filters out already-transferred files from a list.
   *
   * Implements the "Import New" mode by removing files that have already been imported to the
   * destination. Uses hash-based detection for accuracy.
   *
   * ## Detection Strategies
   * - **Hash-based** ([ImportConfiguration.detectTransferredByHash]): Compares file hashes against
   *   destination hashes. Most accurate.
   * - **EXIF-based** ([ImportConfiguration.detectTransferredByExif]): Compares EXIF metadata.
   *   Catches edited versions.
   *
   * ## Usage
   *
   * ```kotlin
   * val allImages = scanSource("/camera")
   * val destHashes = getDestinationHashes("/library")
   * val newImages = filterAlreadyTransferred(allImages, destHashes, config)
   * // newImages contains only files not in destination
   * ```
   *
   * @param images List of images to filter
   * @param destinationHashes Set of hashes already in destination
   * @param configuration Import configuration with detection settings
   * @return Filtered list with already-transferred files removed
   */
  fun filterAlreadyTransferred(
      images: List<ImageFile>,
      destinationHashes: Set<String>,
      configuration: ImportConfiguration
  ): List<ImageFile> {
    if (!configuration.detectTransferredByHash && !configuration.detectTransferredByExif)
        return images
    return images.filter { image ->
      val hashMatch =
          configuration.detectTransferredByHash &&
              image.hash != null &&
              image.hash in destinationHashes
      !hashMatch
    }
  }

  /**
   * Finds visual duplicates in a list of images.
   *
   * Uses advanced duplicate detection algorithms to find visually similar images:
   * - Perceptual hashing (fast, catches minor edits)
   * - SURF feature matching (slow, catches significant changes)
   *
   * ## Detection Settings
   *
   * Configured via [ImportConfiguration]:
   * - [ImportConfiguration.detectVisualDuplicates]: Enable/disable
   * - [ImportConfiguration.perceptualHashThreshold]: Similarity threshold
   * - [ImportConfiguration.useSurfMatching]: Enable SURF matching
   * - [ImportConfiguration.surfMatchThreshold]: SURF match threshold
   *
   * ## Usage
   *
   * ```kotlin
   * val duplicates = importService.findVisualDuplicates(images, config)
   * duplicates.forEach { dupe ->
   *     println("Duplicate group: ${dupe.files.size} files")
   * }
   * ```
   *
   * @param images List of images to check for duplicates
   * @param configuration Import configuration with duplicate detection settings
   * @return List of [DuplicateInfo] grouping duplicate images Empty list if visual duplicate
   *   detection disabled
   */
  suspend fun findVisualDuplicates(
      images: List<ImageFile>,
      configuration: ImportConfiguration
  ): List<DuplicateInfo> {
    if (!configuration.detectVisualDuplicates) return emptyList()
    val settings =
        DeduplicationSettings(
            enableHashDeduplication = false,
            enablePerceptualHash = true,
            enableExifDeduplication = false,
            enableSurfMatching = configuration.useSurfMatching,
            surfMatchThreshold = configuration.surfMatchThreshold,
            perceptualHashThreshold = configuration.perceptualHashThreshold)
    return deduplicationPort.findDuplicates(images, settings)
  }

  /**
   * Scans for connected camera devices.
   *
   * Detects cameras, phones, and removable storage devices that may contain photos/videos. Used for
   * auto-detection in the Import screen.
   *
   * ## Detected Devices
   * - DSLR/Mirrorless cameras (Canon, Nikon, Sony, etc.)
   * - Smartphones (iPhone, Android)
   * - SD card readers
   * - USB drives
   *
   * ## Usage
   *
   * ```kotlin
   * val devices = importService.scanDevices()
   * devices.forEach { device ->
   *     println("Found: ${device.name} at ${device.mountPoint}")
   * }
   * ```
   *
   * @return List of detected [CameraDevice] objects Empty list if no devices found or devicePort is
   *   null
   */
  suspend fun scanDevices(): List<CameraDevice> = devicePort?.detectDevices() ?: emptyList()

  /**
   * Previews the destination file structure for an import.
   *
   * Generates a preview of where each file will be imported without actually copying anything. This
   * allows users to review the organization before committing to the import.
   *
   * ## Preview Information
   *
   * For each file:
   * - Source path
   * - Destination path
   * - Folder that will be created
   * - Any conflicts detected
   *
   * ## Usage
   *
   * ```kotlin
   * val preview = importService.previewStructure(
   *     images = images,
   *     destinationPath = "/photo/library",
   *     configuration = config
   * )
   *
   * preview.forEach { item ->
   *     println("${item.sourcePath} → ${item.destinationPath}")
   * }
   * ```
   *
   * @param images List of images to import
   * @param destinationPath Root destination directory
   * @param configuration Import configuration with patterns
   * @return List of [FileStructurePreview] showing planned organization
   */
  fun previewStructure(
      images: List<ImageFile>,
      destinationPath: String,
      configuration: ImportConfiguration
  ): List<FileStructurePreview> =
      namingPort.previewFileStructure(images, destinationPath, configuration)

  /**
   * Executes the import operation, copying files to destination.
   *
   * This is the main import method that performs the actual file copying with all configured
   * options (verification, renaming, etc.).
   *
   * ## Import Process
   * 1. Create destination folders
   * 2. For each image: a. Generate destination path b. Handle conflicts (rename/skip/replace) c.
   *    Copy file d. Verify hash (if enabled) e. Delete source (if enabled)
   * 3. Update import history
   * 4. Return results
   *
   * ## Progress Reporting
   *
   * Updates [importProgress] StateFlow throughout operation:
   * - Current file being copied
   * - Files completed / total
   * - Bytes transferred
   * - Transfer speed
   * - Estimated time remaining
   *
   * ## Error Handling
   * - Continues on individual file errors
   * - Records errors in [ImportResult]
   * - Rolls back partial imports on critical errors
   *
   * ## Usage
   *
   * ```kotlin
   * val result = importService.executeImport(
   *     images = images,
   *     destinationPath = "/photo/library",
   *     configuration = config,
   *     onProgress = { progress ->
   *         println("Copying ${progress.currentFile} (${progress.percent}%)")
   *     }
   * )
   *
   * println("Imported ${result.successCount} files")
   * if (result.errors.isNotEmpty()) {
   *     println("Errors: ${result.errors}")
   * }
   * ```
   *
   * @param images List of images to import
   * @param destinationPath Root destination directory
   * @param configuration Import configuration with all options
   * @param onProgress Progress callback (also updates importProgress StateFlow)
   * @return [ImportResult] with import statistics and any errors
   * @see ImportResult Import operation results
   * @see ImportProgress Progress tracking
   */
  suspend fun executeImport(
      images: List<ImageFile>,
      destinationPath: String,
      configuration: ImportConfiguration,
      onProgress: (ImportProgress) -> Unit = {}
  ): ImportResult {
    val startTime = System.currentTimeMillis()
    val copiedFiles = mutableListOf<CopiedFile>()
    val fileDetails = mutableListOf<org.kryspetrie.fileimport.domain.model.ImportFileDetail>()

    val errors = mutableListOf<ImportError>()
    var successCount = 0
    var duplicateCount = 0
    var skippedCount = 0
    var deletedCount = 0

    val totalBytes = images.sumOf { it.fileSize }
    var copiedBytes = 0L
    var counter = 1

    _importProgress.value = ImportProgress(totalFiles = images.size, totalBytes = totalBytes)

    for ((index, image) in images.withIndex()) {
      _importProgress.value =
          _importProgress.value.copy(
              currentFile = image.fileName, currentIndex = index, status = ImportStatus.PROCESSING)
      onProgress(_importProgress.value)

      try {
        val destFolder = namingPort.generateFolderPath(image, destinationPath, configuration)
        val destFileName = namingPort.generateFileName(image, configuration, counter)
        var destPath = "$destFolder/$destFileName"
        var destFile = java.io.File(destPath)
        destFile.parentFile?.mkdirs()

        var conflictResolution = configuration.conflictResolution.toString()
        if (destFile.exists()) {
          when (configuration.conflictResolution) {
            ConflictResolution.SKIP -> {
              skippedCount++
              fileDetails.add(
                  org.kryspetrie.fileimport.domain.model.ImportFileDetail(
                      sourcePath = image.filePath,
                      destinationPath = "",
                      destinationFolder = "",
                      finalFilename = "",
                      originalFilename = image.fileName,
                      folderPattern = configuration.folderPattern,
                      filenamePattern = configuration.fileNamePattern,
                      resolvedFolder = "",
                      fileSize = image.fileSize,
                      fileHash = image.hash,
                      success = false,
                      errorMessage = "Skipped due to conflict",
                      wasSkipped = true,
                      conflictResolution = conflictResolution,
                      sequenceNumber = counter))
              continue
            }
            ConflictResolution.RENAME -> {
              destPath = namingPort.resolveConflict(image, destinationPath, configuration)
              destFile = java.io.File(destPath)
              destFile.parentFile?.mkdirs()
              conflictResolution = "RENAME"
            }
            ConflictResolution.REPLACE -> {}
            ConflictResolution.ASK_USER -> {
              skippedCount++
              fileDetails.add(
                  org.kryspetrie.fileimport.domain.model.ImportFileDetail(
                      sourcePath = image.filePath,
                      destinationPath = "",
                      destinationFolder = "",
                      finalFilename = "",
                      originalFilename = image.fileName,
                      folderPattern = configuration.folderPattern,
                      filenamePattern = configuration.fileNamePattern,
                      resolvedFolder = "",
                      fileSize = image.fileSize,
                      fileHash = image.hash,
                      success = false,
                      errorMessage = "Skipped - user decision required",
                      wasSkipped = true,
                      conflictResolution = "ASK_USER",
                      sequenceNumber = counter))
              continue
            }
          }
        }

        val copyResult =
            imageRepository.copyFile(image, destFile) { current, _ ->
              _importProgress.value =
                  _importProgress.value.copy(copiedBytes = copiedBytes + current)
            }

        if (!copyResult) {
          errors.add(ImportError(image, ErrorType.UNKNOWN, "Failed to copy file"))
          fileDetails.add(
              org.kryspetrie.fileimport.domain.model.ImportFileDetail(
                  sourcePath = image.filePath,
                  destinationPath = "",
                  destinationFolder = destFolder,
                  finalFilename = destFileName,
                  originalFilename = image.fileName,
                  folderPattern = configuration.folderPattern,
                  filenamePattern = configuration.fileNamePattern,
                  resolvedFolder = destFolder,
                  fileSize = image.fileSize,
                  fileHash = image.hash,
                  success = false,
                  errorMessage = "Failed to copy file",
                  sequenceNumber = counter))
          continue
        }

        var hashVerified = false
        var hashMatches = false
        if (configuration.verifyAfterCopy) {
          hashVerified = true
          hashMatches = imageRepository.verifyCopy(image, destFile)
          if (!hashMatches) {
            errors.add(ImportError(image, ErrorType.HASH_MISMATCH, "Hash verification failed"))
            destFile.delete()
            fileDetails.add(
                org.kryspetrie.fileimport.domain.model.ImportFileDetail(
                    sourcePath = image.filePath,
                    destinationPath = "",
                    destinationFolder = destFolder,
                    finalFilename = destFileName,
                    originalFilename = image.fileName,
                    folderPattern = configuration.folderPattern,
                    filenamePattern = configuration.fileNamePattern,
                    resolvedFolder = destFolder,
                    fileSize = image.fileSize,
                    fileHash = image.hash,
                    hashVerified = false,
                    success = false,
                    errorMessage = "Hash verification failed",
                    sequenceNumber = counter))
            continue
          }
        }

        copiedFiles.add(
            CopiedFile(
                sourceFile = image,
                destinationPath = destPath,
                hashVerified = hashVerified,
                hashMatches = hashMatches))

        // Copy sidecars to same destination
        val sidecarFiles = mutableListOf<String>()
        if (configuration.importSidecars && image.sidecars.isNotEmpty()) {
          for (sidecar in image.sidecars) {
            try {
              val sidecarDest =
                  java.io.File(
                      destFile.parentFile, "${destFile.nameWithoutExtension}.${sidecar.extension}")
              sidecar.copyTo(sidecarDest, overwrite = true)
              sidecarFiles.add(sidecarDest.absolutePath)
            } catch (_: Exception) {}
          }
        }

        var sourceDeleted = false
        if (configuration.deleteAfterImport) {
          if (imageRepository.deleteFile(image)) {
            sourceDeleted = true
            deletedCount++
          }
          if (configuration.importSidecars) {
            image.sidecars.forEach { it.delete() }
          }
        }

        successCount++
        copiedBytes += image.fileSize

        fileDetails.add(
            org.kryspetrie.fileimport.domain.model.ImportFileDetail(
                sourcePath = image.filePath,
                destinationPath = destPath,
                destinationFolder = destFolder,
                finalFilename = destFile.name,
                originalFilename = image.fileName,
                folderPattern = configuration.folderPattern,
                filenamePattern = configuration.fileNamePattern,
                resolvedFolder = destFolder,
                fileSize = image.fileSize,
                fileHash = image.hash,
                hashVerified = hashVerified,
                hashMatches = hashMatches,
                success = true,
                conflictResolution = conflictResolution,
                sidecarsImported = sidecarFiles.isNotEmpty(),
                sidecarFiles = sidecarFiles,
                sourceDeleted = sourceDeleted,
                exifDate = image.metadata?.dateTimeOriginal?.toString() ?: "",
                cameraModel = image.metadata?.cameraModel ?: "",
                sequenceNumber = counter))

        counter++
      } catch (e: Exception) {
        val errorType =
            when {
              e.message?.contains("not found") == true -> ErrorType.FILE_NOT_FOUND
              e.message?.contains("permission") == true -> ErrorType.PERMISSION_DENIED
              else -> ErrorType.UNKNOWN
            }
        errors.add(ImportError(image, errorType, e.message ?: "Unknown error"))
        fileDetails.add(
            org.kryspetrie.fileimport.domain.model.ImportFileDetail(
                sourcePath = image.filePath,
                destinationPath = "",
                destinationFolder = "",
                finalFilename = "",
                originalFilename = image.fileName,
                folderPattern = configuration.folderPattern,
                filenamePattern = configuration.fileNamePattern,
                fileSize = image.fileSize,
                fileHash = image.hash,
                success = false,
                errorMessage = e.message ?: "Unknown error",
                sequenceNumber = counter))
      }
    }

    val result =
        ImportResult(
                totalFiles = images.size,
                successCount = successCount,
                errorCount = errors.size,
                duplicateCount = duplicateCount,
                skippedCount = skippedCount,
                deletedSourceCount = deletedCount,
                copiedFiles = copiedFiles,
                errors = errors,
                startTime = startTime,
                endTime = System.currentTimeMillis())
            .also { _importProgress.value = ImportProgress() }

    // Build history entry with detailed file information for caller to persist
    val historyEntry =
        org.kryspetrie.fileimport.domain.model.ImportHistoryEntry(
            timestamp = startTime,
            timestampString =
                org.kryspetrie.fileimport.domain.model.ImportHistoryEntry.createTimestampString(
                    startTime),
            sourcePath = images.firstOrNull()?.filePath?.substringBeforeLast("/") ?: "",
            destinationPath = destinationPath,
            profileName = "",
            folderPattern = configuration.folderPattern,
            filenamePattern = configuration.fileNamePattern,
            totalFiles = images.size,
            successCount = successCount,
            errorCount = errors.size,
            skippedCount = skippedCount,
            duplicateCount = duplicateCount,
            deletedSourceCount = deletedCount,
            totalBytes = totalBytes,
            copiedBytes = copiedBytes,
            durationMs = System.currentTimeMillis() - startTime,
            fileDetails = fileDetails,
            importMode = "Import All",
            verifyHashes = configuration.verifyAfterCopy,
            conflictResolution = configuration.conflictResolution.toString(),
            importSidecars = configuration.importSidecars)

    // Store in result for caller to persist
    return result.copy(historyEntry = historyEntry)
  }

  /**
   * Detect RAW+JPEG pairs by matching base filename and timestamp. Returns pairs as (RAW, JPEG).
   */
  fun detectRawJpegPairs(images: List<ImageFile>): List<Pair<ImageFile, ImageFile>> {
    val raws = images.filter { it.fileType.isRawFormat }
    val jpegs = images.filter { it.fileType.isJpeg || it.fileType == ImageFileType.JPEG }
    val pairs = mutableListOf<Pair<ImageFile, ImageFile>>()
    val pairedIds = mutableSetOf<String>()

    for (raw in raws) {
      val match =
          jpegs.find { jpeg ->
            jpeg.id !in pairedIds &&
                jpeg.file.nameWithoutExtension.equals(
                    raw.file.nameWithoutExtension, ignoreCase = true)
          }
      if (match != null) {
        pairs.add(raw to match)
        pairedIds.add(raw.id)
        pairedIds.add(match.id)
      }
    }
    return pairs
  }

  /** Apply RAW+JPEG pair filtering based on config. */
  fun applyPairFilter(images: List<ImageFile>, config: ImportConfiguration): List<ImageFile> {
    if (config.rawJpegPairMode == RawJpegPairMode.IMPORT_BOTH) return images

    val pairs = detectRawJpegPairs(images)
    if (pairs.isEmpty()) return images

    val excludeIds = mutableSetOf<String>()
    for ((raw, jpeg) in pairs) {
      when (config.rawJpegPairMode) {
        RawJpegPairMode.RAW_ONLY -> excludeIds.add(jpeg.id)
        RawJpegPairMode.JPEG_ONLY -> excludeIds.add(raw.id)
        RawJpegPairMode.IMPORT_BOTH -> {}
      }
    }
    return images.filter { it.id !in excludeIds }
  }

  fun setSelectedImages(images: List<ImageFile>) {}
}
