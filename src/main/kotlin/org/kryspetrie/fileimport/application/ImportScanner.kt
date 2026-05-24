package org.kryspetrie.fileimport.application

import java.io.File
import java.util.concurrent.atomic.AtomicInteger

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.kryspetrie.fileimport.domain.model.FolderIndex
import org.kryspetrie.fileimport.domain.model.ImageFile
import org.kryspetrie.fileimport.domain.model.IndexProgress
import org.kryspetrie.fileimport.domain.port.HashCachePort
import org.kryspetrie.fileimport.domain.port.ImageRepositoryPort
import org.kryspetrie.fileimport.domain.port.DispatcherProvider

/** Maximum concurrency for metadata extraction operations. */
private val METADATA_CONCURRENCY = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)

/**
 * Service responsible for scanning source directories and resolving file metadata/hashes.
 *
 * Extracted from ImportService to follow single-responsibility principle. Handles Phase 1
 * (discovery), Phase 2 (hash cache resolution), and Phase 3 (parallel metadata).
 *
 * @see ImportService Orchestration service that delegates scanning to this service
 */
class ImportScanner(
    private val imageRepository: ImageRepositoryPort,
    private val hashCache: HashCachePort?,
    private val dispatcherProvider: DispatcherProvider,
) {
    /**
     * Scans a source directory for media files with metadata extraction.
     *
     * Three-phase scan optimized for performance:
     * 1. Fast directory walk (filesystem metadata only)
     * 2. Hash resolution from cache
     * 3. Parallel metadata + hash extraction
     */
    suspend fun scanSource(
        sourcePath: String,
        recursive: Boolean = true,
        onProgress: (scanned: Int, total: Int, file: String) -> Unit = { _, _, _ -> },
    ): List<ImageFile> {
        val sourceDir = File(sourcePath)
        require(sourceDir.exists() && sourceDir.isDirectory) {
            "Source directory does not exist: $sourcePath"
        }

        onProgress(0, 0, "Discovering files...")
        val files = imageRepository.scanDirectory(sourceDir, recursive)
        val total = files.size
        if (total == 0) return emptyList()

        val cachedIndex = hashCache?.getIndex(sourcePath)
        val cachedEntries = cachedIndex?.entries ?: emptyMap()

        val semaphore = Semaphore(METADATA_CONCURRENCY)
        val counter = AtomicInteger(0)

        return coroutineScope {
            files
                .map { file ->
                    async(dispatcherProvider.io) {
                        semaphore.withPermit {
                            val cached = cachedEntries[file.filePath]
                            val hash =
                                if (
                                    cached != null &&
                                        cached.fileSize == file.fileSize &&
                                        cached.lastModified == file.file.lastModified()
                                ) {
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
        onProgress: (IndexProgress) -> Unit = {},
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
}
