package org.kryspetrie.fileimport.application

import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.kryspetrie.fileimport.domain.model.*
import org.kryspetrie.fileimport.domain.port.DeduplicationPort
import org.kryspetrie.fileimport.domain.port.HashCachePort
import org.kryspetrie.fileimport.domain.port.ImageRepositoryPort

private val SCAN_CONCURRENCY = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)

data class ScanProgress(
    val phase: String = "",
    val current: Int = 0,
    val total: Int = 0,
    val currentFile: String = ""
)

enum class DuplicateAction {
  KEEP_HIGHEST_RES,
  KEEP_RAW_OVER_JPEG,
  KEEP_NEWEST,
  KEEP_OLDEST,
  KEEP_LARGEST
}

class DuplicateScannerService(
    private val imageRepository: ImageRepositoryPort,
    private val deduplicationPort: DeduplicationPort,
    private val hashCache: HashCachePort? = null
) {

  suspend fun scanForDuplicates(
      folderPath: String,
      settings: DeduplicationSettings,
      onProgress: (ScanProgress) -> Unit = {}
  ): List<DuplicateInfo> {
    val rootDir = File(folderPath)
    require(rootDir.exists() && rootDir.isDirectory) { "Folder does not exist: $folderPath" }

    // Phase 1: Discover files
    onProgress(ScanProgress(phase = "Discovering files...", current = 0, total = 0))
    val files = imageRepository.scanDirectory(rootDir, recursive = true)
    if (files.size < 2) return emptyList()

    // Phase 2: Hash + metadata
    onProgress(
        ScanProgress(phase = "Hashing and reading metadata...", current = 0, total = files.size))
    val cachedIndex = hashCache?.getIndex(folderPath)
    val cachedEntries = cachedIndex?.entries ?: emptyMap()
    val semaphore = Semaphore(SCAN_CONCURRENCY)
    val counter = AtomicInteger(0)

    val enriched = coroutineScope {
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
                if (done % 100 == 0 || done == files.size) {
                  onProgress(
                      ScanProgress(
                          phase = "Hashing and reading metadata...",
                          current = done,
                          total = files.size,
                          currentFile = file.fileName))
                }
                file.copy(hash = hash, metadata = metadata)
              }
            }
          }
          .awaitAll()
    }

    // Phase 3: Save hashes to cache
    hashCache?.indexFolder(folderPath, true) {}

    // Phase 4: Find duplicates
    onProgress(ScanProgress(phase = "Analyzing duplicates...", current = 0, total = 0))
    return deduplicationPort.findDuplicates(enriched, settings)
  }

  suspend fun resolveGroup(
      group: DuplicateInfo,
      action: DuplicateAction,
      moveToTrashFolder: String? = null
  ): Int =
      withContext(Dispatchers.IO) {
        val allImages = listOf(group.primaryImage) + group.duplicateImages
        val keep = pickKeeper(allImages, action)
        val toRemove = allImages.filter { it.id != keep.id }

        var removed = 0
        for (image in toRemove) {
          try {
            if (moveToTrashFolder != null) {
              val trashDir = File(moveToTrashFolder)
              trashDir.mkdirs()
              val dest = File(trashDir, image.file.name)
              val target =
                  if (dest.exists()) {
                    File(
                        trashDir,
                        "${image.file.nameWithoutExtension}_${System.currentTimeMillis()}.${image.file.extension}")
                  } else dest
              if (image.file.renameTo(target)) removed++
            } else {
              if (image.file.delete()) removed++
            }
          } catch (_: Exception) {}
        }
        removed
      }

  suspend fun resolveAll(
      groups: List<DuplicateInfo>,
      action: DuplicateAction,
      moveToTrashFolder: String? = null,
      onProgress: (Int, Int) -> Unit = { _, _ -> }
  ): Int {
    var totalRemoved = 0
    for ((index, group) in groups.withIndex()) {
      totalRemoved += resolveGroup(group, action, moveToTrashFolder)
      onProgress(index + 1, groups.size)
    }
    return totalRemoved
  }

  private fun pickKeeper(images: List<ImageFile>, action: DuplicateAction): ImageFile =
      when (action) {
        DuplicateAction.KEEP_HIGHEST_RES ->
            images.maxByOrNull {
              (it.metadata?.imageWidth ?: 0).toLong() * (it.metadata?.imageHeight ?: 0).toLong()
            } ?: images.first()
        DuplicateAction.KEEP_RAW_OVER_JPEG ->
            images.sortedByDescending { it.fileType.isRawFormat }.first()
        DuplicateAction.KEEP_NEWEST ->
            images.maxByOrNull { it.file.lastModified() } ?: images.first()
        DuplicateAction.KEEP_OLDEST ->
            images.minByOrNull { it.file.lastModified() } ?: images.first()
        DuplicateAction.KEEP_LARGEST -> images.maxByOrNull { it.fileSize } ?: images.first()
      }
}
