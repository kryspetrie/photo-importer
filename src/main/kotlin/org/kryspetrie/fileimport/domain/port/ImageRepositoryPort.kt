package org.kryspetrie.fileimport.domain.port

import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.ImageFile
import org.kryspetrie.fileimport.domain.model.ImageMetadata

/**
 * Port interface for image file repository operations.
 *
 * Uses [FilePath] instead of `java.io.File` to keep the domain layer free of JVM I/O
 * infrastructure. Infrastructure adapters convert [FilePath] to `java.io.File` when performing
 * actual I/O.
 */
interface ImageRepositoryPort {
    suspend fun scanDirectory(directory: FilePath, recursive: Boolean = true): List<ImageFile>

    suspend fun getMetadata(imageFile: ImageFile): ImageMetadata?

    suspend fun calculateFileHash(imageFile: ImageFile, algorithm: String = "MD5"): String

    suspend fun calculatePerceptualHash(imageFile: ImageFile): Float?

    suspend fun copyFile(
        source: ImageFile,
        destination: FilePath,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): Boolean

    suspend fun verifyCopy(source: ImageFile, destination: FilePath): Boolean

    suspend fun deleteFile(imageFile: ImageFile): Boolean

    suspend fun fileExists(file: FilePath): Boolean

    fun getSupportedExtensions(): Set<String>
}
