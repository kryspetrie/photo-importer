package org.kryspetrie.fileimport.application

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.kryspetrie.fileimport.domain.model.DeduplicationSettings
import org.kryspetrie.fileimport.domain.model.DuplicateAction
import org.kryspetrie.fileimport.domain.model.DuplicateInfo
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.ImageFile
import org.kryspetrie.fileimport.domain.model.ResolvableDuplicate
import org.kryspetrie.fileimport.domain.model.ScanProgress
import org.kryspetrie.fileimport.domain.model.pickKeeper
import org.kryspetrie.fileimport.domain.port.DeduplicationPort
import org.kryspetrie.fileimport.domain.port.DispatcherProvider
import org.kryspetrie.fileimport.domain.port.FileSystemPort
import org.kryspetrie.fileimport.domain.port.HashCachePort
import org.kryspetrie.fileimport.domain.port.ImageRepositoryPort
import org.kryspetrie.fileimport.domain.port.TimeProvider

private val SCAN_CONCURRENCY = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)

class DuplicateScannerService(
    private val imageRepository: ImageRepositoryPort,
    private val deduplicationPort: DeduplicationPort,
    private val fileSystem: FileSystemPort,
    private val hashCache: HashCachePort? = null,
    private val timeProvider: TimeProvider,
    private val dispatcherProvider: DispatcherProvider,
    private val onError: (String) -> Unit = {},
) {

    suspend fun scanForDuplicates(
        folderPath: String,
        settings: DeduplicationSettings,
        onProgress: (ScanProgress) -> Unit = {},
    ): List<DuplicateInfo> {
        val rootDir = FilePath(folderPath)
        require(fileSystem.exists(rootDir) && fileSystem.isDirectory(rootDir)) {
            "Folder does not exist: $folderPath"
        }

        // Phase 1: Discover files
        onProgress(ScanProgress(phase = "Discovering files...", current = 0, total = 0))
        val files = imageRepository.scanDirectory(rootDir, recursive = true)
        if (files.size < 2) return emptyList()

        // Phase 2: Hash + metadata
        onProgress(
            ScanProgress(phase = "Hashing and reading metadata...", current = 0, total = files.size)
        )
        val cachedIndex = hashCache?.getIndex(folderPath)
        val cachedEntries = cachedIndex?.entries ?: emptyMap()
        val semaphore = Semaphore(SCAN_CONCURRENCY)
        val counter = AtomicInteger(0)

        val enriched = coroutineScope {
            files
                .map { file ->
                    async(dispatcherProvider.io) {
                        semaphore.withPermit {
                            val cached = cachedEntries[file.filePath]
                            val cachedLastModified = cached?.lastModified ?: 0L
                            val currentLastModified = fileSystem.lastModified(file.path)
                            val hash =
                                if (
                                    cached != null &&
                                        cached.fileSize == file.fileSize &&
                                        cachedLastModified == currentLastModified
                                ) {
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
                                        currentFile = file.fileName,
                                    )
                                )
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
        moveToTrashFolder: String? = null,
    ): Int =
        withContext(dispatcherProvider.io) {
            val allImages = listOf(group.primaryImage) + group.duplicateImages
            val keepId = pickKeeper(allImages.map { it.toResolvableDuplicate() }, action)
            val toRemove = allImages.filter { it.id != keepId }

            var removed = 0
            var errors = mutableListOf<String>()
            for (image in toRemove) {
                try {
                    if (moveToTrashFolder != null) {
                        val trashDir = FilePath(moveToTrashFolder)
                        fileSystem.mkdirs(trashDir)
                        val destPath = trashDir.resolve(image.path.name)
                        val target =
                            if (fileSystem.exists(destPath)) {
                                trashDir.resolve(
                                    "${image.path.nameWithoutExtension}_" +
                                        "${timeProvider.currentTimeMillis()}.${image.path.extension}"
                                )
                            } else destPath
                        if (fileSystem.renameTo(image.path, target)) removed++
                        else errors.add("Failed to move ${image.path.name}")
                    } else {
                        if (fileSystem.delete(image.path)) removed++
                        else errors.add("Failed to delete ${image.path.name}")
                    }
                } catch (e: Exception) {
                    errors.add("${image.path.name}: ${e.message}")
                }
            }
            if (errors.isNotEmpty()) {
                onError("Duplicate resolution errors: ${errors.joinToString()}")
            }
            removed
        }

    suspend fun resolveAll(
        groups: List<DuplicateInfo>,
        action: DuplicateAction,
        moveToTrashFolder: String? = null,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): Int {
        var totalRemoved = 0
        for ((index, group) in groups.withIndex()) {
            totalRemoved += resolveGroup(group, action, moveToTrashFolder)
            onProgress(index + 1, groups.size)
        }
        return totalRemoved
    }

    private fun ImageFile.toResolvableDuplicate(): ResolvableDuplicate =
        ResolvableDuplicate(
            id = id,
            pixelCount =
                (metadata?.imageWidth ?: 0).toLong() * (metadata?.imageHeight ?: 0).toLong(),
            isRawFormat = fileType.isRawFormat,
            lastModifiedEpochMillis = file.lastModified(),
            fileSize = fileSize,
        )
}
