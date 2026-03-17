package org.kryspetrie.fileimport.domain.port

import java.io.File
import org.kryspetrie.fileimport.domain.model.ImageFile
import org.kryspetrie.fileimport.domain.model.ImageMetadata

interface ImageRepositoryPort {
  suspend fun scanDirectory(directory: File, recursive: Boolean = true): List<ImageFile>

  suspend fun getMetadata(imageFile: ImageFile): ImageMetadata?

  suspend fun calculateFileHash(imageFile: ImageFile, algorithm: String = "MD5"): String

  suspend fun calculatePerceptualHash(imageFile: ImageFile): Float?

  suspend fun copyFile(
      source: ImageFile,
      destination: File,
      onProgress: (Long, Long) -> Unit = { _, _ -> }
  ): Boolean

  suspend fun verifyCopy(source: ImageFile, destination: File): Boolean

  suspend fun deleteFile(imageFile: ImageFile): Boolean

  suspend fun fileExists(file: File): Boolean

  fun getSupportedExtensions(): Set<String>
}
