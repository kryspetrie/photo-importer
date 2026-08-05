package org.kryspetrie.fileimport.infrastructure.thumbnails

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.imgscalr.Scalr
import org.kryspetrie.fileimport.application.thumbnails.FolderThumbnailPaths.CACHE_EXTENSION
import org.kryspetrie.fileimport.application.thumbnails.FolderThumbnailPaths.albumRoot
import org.kryspetrie.fileimport.application.thumbnails.FolderThumbnailPaths.cacheFilePath
import org.kryspetrie.fileimport.application.thumbnails.FolderThumbnailPaths.thumbsDirectory
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.ImageFileType
import org.kryspetrie.fileimport.domain.model.ProcessedImage
import org.kryspetrie.fileimport.domain.port.DispatcherProvider
import org.kryspetrie.fileimport.domain.port.FileSystemPort
import org.kryspetrie.fileimport.domain.port.FolderThumbnailCachePort
import org.kryspetrie.fileimport.domain.port.ThumbnailExtractorPort
import org.kryspetrie.fileimport.infrastructure.adapter.toBufferedImage
import org.kryspetrie.fileimport.infrastructure.adapter.toProcessedImage

class FolderThumbnailCacheAdapter(
    private val fileSystem: FileSystemPort,
    private val thumbnailExtractor: ThumbnailExtractorPort,
    private val dispatcherProvider: DispatcherProvider,
) : FolderThumbnailCachePort {

    private val generateSemaphore = Semaphore(GENERATION_CONCURRENCY)

    override suspend fun reconcileSources(
        sourceFiles: List<FilePath>,
        editorSourcePath: String?,
        maxPx: Int,
        diskCacheEnabled: Boolean,
    ) {
        if (!diskCacheEnabled || sourceFiles.isEmpty()) return
        withContext(dispatcherProvider.io) {
            sourceFiles
                .groupBy { albumRoot(it, editorSourcePath) }
                .forEach { (root, files) -> reconcileAlbum(root, files) }
        }
    }

    override suspend fun getThumbnail(
        sourceFile: FilePath,
        editorSourcePath: String?,
        maxPx: Int,
        diskCacheEnabled: Boolean,
    ): ProcessedImage? =
        withContext(dispatcherProvider.io) {
            if (!fileSystem.exists(sourceFile)) return@withContext null
            val root = albumRoot(sourceFile, editorSourcePath)
            val cachePath = cacheFilePath(root, sourceFile)
            if (diskCacheEnabled) {
                readValidCache(sourceFile, cachePath)?.let {
                    return@withContext it.toProcessedImage()
                }
            }

            generateSemaphore.withPermit {
                if (diskCacheEnabled) {
                    readValidCache(sourceFile, cachePath)?.let {
                        return@withPermit it.toProcessedImage()
                    }
                }
                val generated = generateThumbnail(sourceFile, maxPx) ?: return@withPermit null
                if (diskCacheEnabled) {
                    writeCache(cachePath, generated)
                }
                generated.toProcessedImage()
            }
        }

    override suspend fun invalidate(sourceFile: FilePath, editorSourcePath: String?, maxPx: Int) {
        withContext(dispatcherProvider.io) {
            val root = albumRoot(sourceFile, editorSourcePath)
            val cachePath = cacheFilePath(root, sourceFile)
            if (fileSystem.exists(cachePath)) {
                fileSystem.delete(cachePath)
            }
        }
    }

    override suspend fun invalidateSources(sourceFiles: List<FilePath>, libraryRoot: String?) {
        if (sourceFiles.isEmpty()) return
        withContext(dispatcherProvider.io) {
            for (source in sourceFiles) {
                val root = albumRoot(source, libraryRoot)
                val cachePath = cacheFilePath(root, source)
                if (fileSystem.exists(cachePath)) {
                    fileSystem.delete(cachePath)
                }
            }
        }
    }

    override suspend fun deleteThumbsFolder(libraryRoot: FilePath) {
        withContext(dispatcherProvider.io) {
            val thumbsRoot = thumbsDirectory(libraryRoot)
            if (!fileSystem.exists(thumbsRoot)) return@withContext
            for (path in fileSystem.walkBottomUp(thumbsRoot)) {
                if (path.path == thumbsRoot.path) continue
                fileSystem.delete(path)
            }
            fileSystem.delete(thumbsRoot)
        }
    }

    private suspend fun reconcileAlbum(albumRootPath: FilePath, sourceFiles: List<FilePath>) {
        fileSystem.mkdirs(thumbsDirectory(albumRootPath))
        val expectedCaches = sourceFiles.map { cacheFilePath(albumRootPath, it).path }.toSet()

        for (source in sourceFiles) {
            val cachePath = cacheFilePath(albumRootPath, source)
            if (!fileSystem.exists(cachePath)) continue
            if (
                !fileSystem.exists(source) ||
                    fileSystem.lastModified(source) > fileSystem.lastModified(cachePath)
            ) {
                fileSystem.delete(cachePath)
            }
        }

        val thumbsRoot = thumbsDirectory(albumRootPath)
        if (!fileSystem.exists(thumbsRoot)) return
        for (cachePath in fileSystem.walkTopDown(thumbsRoot)) {
            if (!cachePath.path.endsWith(".$CACHE_EXTENSION")) continue
            if (fileSystem.exists(cachePath) && cachePath.path !in expectedCaches) {
                fileSystem.delete(cachePath)
            }
        }
    }

    private suspend fun readValidCache(sourceFile: FilePath, cachePath: FilePath): BufferedImage? {
        if (!fileSystem.exists(cachePath) || !fileSystem.exists(sourceFile)) return null
        if (fileSystem.lastModified(sourceFile) > fileSystem.lastModified(cachePath)) {
            fileSystem.delete(cachePath)
            return null
        }
        return runCatching {
                ByteArrayInputStream(fileSystem.readBytes(cachePath)).use { ImageIO.read(it) }
            }
            .getOrNull()
    }

    private fun generateThumbnail(sourceFile: FilePath, maxPx: Int): BufferedImage? {
        val fileType = ImageFileType.fromExtension(sourceFile.extension)
        val original: BufferedImage? =
            when {
                fileType.isRaw -> thumbnailExtractor.extractFromRaw(sourceFile)?.toBufferedImage()
                else -> ImageIO.read(sourceFile.toFile())
            }
        original ?: return null
        return try {
            if (original.width <= maxPx && original.height <= maxPx) {
                original
            } else {
                val scaled = Scalr.resize(original, Scalr.Method.BALANCED, maxPx)
                if (scaled !== original) original.flush()
                scaled
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun writeCache(cachePath: FilePath, image: BufferedImage) {
        val baos = ByteArrayOutputStream()
        ImageIO.write(image, CACHE_EXTENSION, baos)
        fileSystem.writeBytes(cachePath, baos.toByteArray())
    }

    companion object {
        private const val GENERATION_CONCURRENCY = 4
    }
}
