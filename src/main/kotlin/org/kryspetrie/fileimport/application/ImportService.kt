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

private val METADATA_CONCURRENCY = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)

class ImportService(
    private val imageRepository: ImageRepositoryPort,
    private val deduplicationPort: DeduplicationPort,
    private val namingPort: NamingPort,
    private val devicePort: DevicePort? = null,
    private val hashCache: HashCachePort? = null
) {
  private val _importProgress = MutableStateFlow(ImportProgress())
  val importProgress: StateFlow<ImportProgress> = _importProgress

  /**
   * Fast scan: discovers files without hashing, then resolves hashes from cache and reads metadata
   * in parallel. Only hashes files not in cache.
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

  suspend fun indexFolder(
      folderPath: String,
      recursive: Boolean = true,
      onProgress: (IndexProgress) -> Unit = {}
  ): FolderIndex {
    return hashCache?.indexFolder(folderPath, recursive, onProgress)
        ?: FolderIndex(folderPath = folderPath)
  }

  fun getDestinationHashes(folderPath: String): Set<String> =
      hashCache?.getDestinationHashes(folderPath) ?: emptySet()

  suspend fun clearIndex(folderPath: String) {
    hashCache?.clearIndex(folderPath)
  }

  suspend fun clearAllIndexes() {
    hashCache?.clearAllIndexes()
  }

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

  suspend fun scanDevices(): List<CameraDevice> = devicePort?.detectDevices() ?: emptyList()

  fun previewStructure(
      images: List<ImageFile>,
      destinationPath: String,
      configuration: ImportConfiguration
  ): List<FileStructurePreview> =
      namingPort.previewFileStructure(images, destinationPath, configuration)

  suspend fun executeImport(
      images: List<ImageFile>,
      destinationPath: String,
      configuration: ImportConfiguration,
      onProgress: (ImportProgress) -> Unit = {}
  ): ImportResult {
    val startTime = System.currentTimeMillis()
    val copiedFiles = mutableListOf<CopiedFile>()
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

        if (destFile.exists()) {
          when (configuration.conflictResolution) {
            ConflictResolution.SKIP -> {
              skippedCount++
              continue
            }
            ConflictResolution.RENAME -> {
              destPath = namingPort.resolveConflict(image, destinationPath, configuration)
              destFile = java.io.File(destPath)
              destFile.parentFile?.mkdirs()
            }
            ConflictResolution.REPLACE -> {}
            ConflictResolution.ASK_USER -> {
              skippedCount++
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
        if (configuration.importSidecars && image.sidecars.isNotEmpty()) {
          for (sidecar in image.sidecars) {
            try {
              val sidecarDest =
                  java.io.File(
                      destFile.parentFile, "${destFile.nameWithoutExtension}.${sidecar.extension}")
              sidecar.copyTo(sidecarDest, overwrite = true)
            } catch (_: Exception) {}
          }
        }

        if (configuration.deleteAfterImport) {
          if (imageRepository.deleteFile(image)) deletedCount++
          if (configuration.importSidecars) {
            image.sidecars.forEach { it.delete() }
          }
        }

        successCount++
        copiedBytes += image.fileSize
        counter++
      } catch (e: Exception) {
        val errorType =
            when {
              e.message?.contains("not found") == true -> ErrorType.FILE_NOT_FOUND
              e.message?.contains("permission") == true -> ErrorType.PERMISSION_DENIED
              else -> ErrorType.UNKNOWN
            }
        errors.add(ImportError(image, errorType, e.message ?: "Unknown error"))
      }
    }

    return ImportResult(
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
