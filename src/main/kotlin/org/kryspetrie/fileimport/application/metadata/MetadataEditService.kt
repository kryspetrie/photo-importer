package org.kryspetrie.fileimport.application.metadata

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.kryspetrie.fileimport.application.export.MetadataWritingService
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.MetadataEditEntry
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration
import org.kryspetrie.fileimport.domain.model.ProcessedImage
import org.kryspetrie.fileimport.domain.port.FileSystemPort
import org.kryspetrie.fileimport.domain.port.ImageProcessingPort

/**
 * Application service for saving metadata edits in the bulk metadata editor.
 *
 * Extracts the save/write logic from the UI layer so that the composable only orchestrates UI state
 * transitions. This service handles:
 * - Creating backups before overwrite
 * - Writing metadata (EXIF/IPTC/XMP) via [MetadataWritingService]
 * - Writing back-of-photo images
 * - Recording journal entries for undo/redo
 *
 * Follows the hexagonal architecture: application layer depends only on domain ports and domain
 * models, never on UI or infrastructure adapters.
 */
class MetadataEditService(
    private val metadataWritingService: MetadataWritingService,
    private val imageProcessing: ImageProcessingPort,
    private val fileSystem: FileSystemPort,
    private val undoService: MetadataEditUndoService,
) {

    /**
     * Result of saving a single file's metadata.
     *
     * @property entry The journal entry recording what was done (for undo support).
     * @property outputFilePath The output file path (non-null for SAVE_NEW mode).
     * @property backImageOutputPath The back image output path, if a back image was written.
     */
    data class SaveResult(
        val entry: MetadataEditEntry,
        val outputFilePath: String? = null,
        val backImageOutputPath: String? = null,
    )

    /**
     * Saves metadata for a single file.
     *
     * Handles both OVERWRITE and SAVE_NEW modes:
     * - OVERWRITE: creates a backup, overwrites the original file.
     * - SAVE_NEW: writes to the output directory.
     *
     * Also handles back-of-photo images when the config specifies one.
     *
     * @param file The source image file.
     * @param config The metadata configuration to apply.
     * @param outputMode "OVERWRITE" or "SAVE_NEW".
     * @param outputDirectory The output directory for SAVE_NEW mode (unused for OVERWRITE).
     * @return The save result with journal entry, or null if the image could not be read.
     */
    suspend fun saveFile(
        file: File,
        config: PhotoScanConfiguration,
        outputMode: String,
        outputDirectory: String,
    ): SaveResult? {
        // Create backup before overwrite (for undo support)
        var backupPath: String? = null
        var backImageBackupPath: String? = null
        var backImageOutputPath: String? = null
        var entryOutputPath: String? = null

        if (outputMode == "OVERWRITE") {
            backupPath = undoService.createBackup(file.absolutePath)
        }

        val processedImage =
            withContext(Dispatchers.IO) { imageProcessing.readImage(FilePath(file.absolutePath)) }
                ?: return null

        when (outputMode) {
            "OVERWRITE" -> {
                val outputPath = FilePath(file.absolutePath)
                // Backup back image before overwrite
                val backFile = File(file.parent, file.nameWithoutExtension + "_back.jpg")
                if (backFile.exists() && backupPath != null) {
                    backImageBackupPath =
                        withContext(Dispatchers.IO) {
                            undoService.createBackup(backFile.absolutePath)
                        }
                }
                metadataWritingService.writeImageWithMetadata(
                    image = processedImage,
                    outputPath = outputPath,
                    config = config,
                    sourcePath = FilePath(file.absolutePath),
                    preRotationWidth = processedImage.width,
                    preRotationHeight = processedImage.height,
                )
                if (config.hasBackImage()) {
                    val backResult =
                        writeBackImage(
                            processedImage,
                            config,
                            file.parent,
                            file.nameWithoutExtension,
                        )
                    backImageOutputPath = backResult
                }
            }
            "SAVE_NEW" -> {
                val outDir = outputDirectory.ifBlank { file.parent }
                val outputFileName = file.nameWithoutExtension + ".jpg"
                val outputPath = FilePath(File(outDir, outputFileName).absolutePath)
                entryOutputPath = outputPath.path
                File(outDir).mkdirs()
                metadataWritingService.writeImageWithMetadata(
                    image = processedImage,
                    outputPath = outputPath,
                    config = config,
                    sourcePath = FilePath(file.absolutePath),
                    preRotationWidth = processedImage.width,
                    preRotationHeight = processedImage.height,
                )
                if (config.hasBackImage()) {
                    val backOutDir =
                        if (outputDirectory.isNotBlank()) outputDirectory else file.parent
                    val backResult =
                        writeBackImage(
                            processedImage,
                            config,
                            backOutDir,
                            file.nameWithoutExtension,
                        )
                    backImageOutputPath = backResult
                }
            }
        }

        val entry =
            MetadataEditEntry(
                filePath = file.absolutePath,
                backupPath = backupPath ?: "",
                configSnapshot = config,
                wasSavedNew = outputMode == "SAVE_NEW",
                outputFilePath = entryOutputPath ?: "",
                backImageBackupPath = backImageBackupPath,
                backImageOutputPath = backImageOutputPath,
            )

        return SaveResult(
            entry = entry,
            outputFilePath = entryOutputPath,
            backImageOutputPath = backImageOutputPath,
        )
    }

    /**
     * Saves a back-of-photo image.
     *
     * @param processedImage The front image (used for max dimensions).
     * @param config The configuration containing back image settings.
     * @param outputDir The directory to write the back image to.
     * @param baseName The base name (without extension) for the output file.
     * @return The output file path, or null if no back image was written.
     */
    private suspend fun writeBackImage(
        processedImage: ProcessedImage,
        config: PhotoScanConfiguration,
        outputDir: String,
        baseName: String,
    ): String? {
        val backImageResult =
            imageProcessing.prepareBackImage(
                config,
                maxWidth = processedImage.width,
                maxHeight = processedImage.height,
            ) ?: return null

        val backFileName = "${baseName}_back.jpg"
        val backOutputFilePath = FilePath(File(outputDir, backFileName).absolutePath)
        File(outputDir).mkdirs()
        imageProcessing.writeJpegImage(backImageResult, backOutputFilePath)
        return backOutputFilePath.path
    }

    /**
     * Saves a batch of journal entries and returns the journal file path.
     *
     * @param sourceFolderPath The source folder/file path being edited.
     * @param outputMode The output mode name ("OVERWRITE" or "SAVE_NEW").
     * @param entries The list of journal entries from individual save operations.
     * @return The journal file path, or null if saving failed.
     */
    fun saveJournal(
        sourceFolderPath: String,
        outputMode: String,
        entries: List<MetadataEditEntry>,
    ): String? {
        return undoService.saveJournalPath(
            sourceFolderPath = sourceFolderPath,
            outputMode = outputMode,
            entries = entries,
        )
    }
}
