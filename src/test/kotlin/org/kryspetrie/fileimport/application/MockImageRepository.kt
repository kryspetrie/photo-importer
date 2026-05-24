package org.kryspetrie.fileimport.application

import java.io.File
import org.kryspetrie.fileimport.domain.model.ImageFile
import org.kryspetrie.fileimport.domain.model.ImageMetadata
import org.kryspetrie.fileimport.domain.port.ImageRepositoryPort

/** In-memory mock implementation of [ImageRepositoryPort] for unit testing. */
class MockImageRepository : ImageRepositoryPort {
    private val storage = mutableMapOf<String, ImageFile>()
    private val metadata = mutableMapOf<String, ImageMetadata>()

    override suspend fun scanDirectory(directory: File, recursive: Boolean): List<ImageFile> {
        return storage.values.toList()
    }

    override suspend fun getMetadata(imageFile: ImageFile): ImageMetadata? {
        return metadata[imageFile.file.absolutePath]
    }

    override suspend fun calculateFileHash(imageFile: ImageFile, algorithm: String): String {
        return "mock_hash_${imageFile.file.name}_$algorithm"
    }

    override suspend fun calculatePerceptualHash(imageFile: ImageFile): Float? {
        return null
    }

    override suspend fun copyFile(
        source: ImageFile,
        destination: File,
        onProgress: (Long, Long) -> Unit,
    ): Boolean {
        onProgress(0, source.file.length())
        onProgress(source.file.length(), source.file.length())
        return true
    }

    override suspend fun verifyCopy(source: ImageFile, destination: File): Boolean {
        return true
    }

    override suspend fun deleteFile(imageFile: ImageFile): Boolean {
        storage.remove(imageFile.file.absolutePath)
        metadata.remove(imageFile.file.absolutePath)
        return true
    }

    override suspend fun fileExists(file: File): Boolean {
        return storage.containsKey(file.absolutePath)
    }

    override fun getSupportedExtensions(): Set<String> {
        return setOf("jpg", "jpeg", "png", "tiff", "tif", "bmp", "gif", "webp")
    }

    fun put(imageFile: ImageFile, meta: ImageMetadata? = null) {
        storage[imageFile.file.absolutePath] = imageFile
        if (meta != null) {
            metadata[imageFile.file.absolutePath] = meta
        }
    }
}
