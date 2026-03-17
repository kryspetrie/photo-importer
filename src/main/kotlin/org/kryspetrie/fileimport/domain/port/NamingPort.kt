package org.kryspetrie.fileimport.domain.port

import org.kryspetrie.fileimport.domain.model.ImageFile
import org.kryspetrie.fileimport.domain.model.ImportConfiguration

interface NamingPort {
  fun generateFilePath(
      imageFile: ImageFile,
      destinationRoot: String,
      configuration: ImportConfiguration,
      counter: Int = 1
  ): String

  fun generateFolderPath(
      imageFile: ImageFile,
      destinationRoot: String,
      configuration: ImportConfiguration
  ): String

  fun generateFileName(
      imageFile: ImageFile,
      configuration: ImportConfiguration,
      counter: Int = 1
  ): String

  fun previewFileStructure(
      images: List<ImageFile>,
      destinationRoot: String,
      configuration: ImportConfiguration
  ): List<FileStructurePreview>

  fun wouldConflict(
      imageFile: ImageFile,
      destinationRoot: String,
      configuration: ImportConfiguration
  ): Boolean

  fun resolveConflict(
      imageFile: ImageFile,
      destinationRoot: String,
      configuration: ImportConfiguration
  ): String
}

data class FileStructurePreview(
    val sourceFile: ImageFile,
    val destinationPath: String,
    val folderPath: String,
    val fileName: String,
    val wouldConflict: Boolean = false,
    val existingFile: Boolean = false
)
