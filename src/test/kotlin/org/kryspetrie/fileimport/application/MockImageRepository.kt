package org.kryspetrie.fileimport.application

import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.ImageFile
import org.kryspetrie.fileimport.domain.model.ImageMetadata
import org.kryspetrie.fileimport.domain.port.ImageRepositoryPort

/** In-memory mock implementation of [ImageRepositoryPort] for unit testing. */
class MockImageRepository : ImageRepositoryPort {
    private val storage = mutableMapOf<String, ImageFile>()
    private val metadata = mutableMapOf<String, ImageMetadata>()

    override suspend fun scanDirectory(directory: FilePath, recursive: Boolean): List<ImageFile> {
        return storage.values.toList()
    }

    override suspend fun getMetadata(imageFile: ImageFile): ImageMetadata? {
        return metadata[imageFile.path.path]
    }

    override suspend fun calculateFileHash(imageFile: ImageFile, algorithm: String): String {
        return "mock_hash_${imageFile.fileName}_$algorithm"
    }

    override suspend fun calculatePerceptualHash(imageFile: ImageFile): Float? {
        return null
    }

    override suspend fun copyFile(
        source: ImageFile,
        destination: FilePath,
        onProgress: (Long, Long) -> Unit,
    ): Boolean {
        onProgress(0, source.fileSize)
        onProgress(source.fileSize, source.fileSize)
        return true
    }

    override suspend fun verifyCopy(source: ImageFile, destination: FilePath): Boolean {
        return true
    }

    override suspend fun deleteFile(imageFile: ImageFile): Boolean {
        storage.remove(imageFile.path.path)
        metadata.remove(imageFile.path.path)
        return true
    }

    override suspend fun fileExists(file: FilePath): Boolean {
        return storage.containsKey(file.path)
    }

    override fun getSupportedExtensions(): Set<String> {
        return setOf("jpg", "jpeg", "png", "tiff", "tif", "bmp", "gif", "webp")
    }

    fun put(imageFile: ImageFile, meta: ImageMetadata? = null) {
        storage[imageFile.path.path] = imageFile
        if (meta != null) {
            metadata[imageFile.path.path] = meta
        }
    }
}
