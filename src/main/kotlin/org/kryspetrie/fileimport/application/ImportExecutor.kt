package org.kryspetrie.fileimport.application

import kotlinx.coroutines.flow.MutableStateFlow
import org.kryspetrie.fileimport.domain.model.ConflictResolution
import org.kryspetrie.fileimport.domain.model.CopiedFile
import org.kryspetrie.fileimport.domain.model.ErrorType
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.ImageFile
import org.kryspetrie.fileimport.domain.model.ImportConfiguration
import org.kryspetrie.fileimport.domain.model.ImportError
import org.kryspetrie.fileimport.domain.model.ImportFileDetail
import org.kryspetrie.fileimport.domain.model.ImportHistoryEntry
import org.kryspetrie.fileimport.domain.model.ImportProgress
import org.kryspetrie.fileimport.domain.model.ImportResult
import org.kryspetrie.fileimport.domain.model.ImportStatus
import org.kryspetrie.fileimport.domain.port.FileSystemPort
import org.kryspetrie.fileimport.domain.port.ImageRepositoryPort
import org.kryspetrie.fileimport.domain.port.NamingPort
import org.kryspetrie.fileimport.domain.port.TimeProvider

/**
 * Service responsible for executing the actual file import (copy, verify, delete).
 *
 * Extracted from ImportService to follow single-responsibility principle. Handles file copying with
 * parallel execution, hash verification, sidecar import, and source deletion.
 *
 * @see ImportService Orchestration service that delegates execution to this service
 */
class ImportExecutor(
    private val imageRepository: ImageRepositoryPort,
    private val namingPort: NamingPort,
    private val timeProvider: TimeProvider,
    private val fileSystem: FileSystemPort,
) {

    /**
     * Executes the import operation for a list of images, copying them to destination with parallel
     * file operations for improved throughput.
     *
     * @param images Images to import (already filtered, deduplicated, etc.)
     * @param destinationPath Root destination directory
     * @param configuration Import configuration
     * @param importProgress MutableStateFlow for progress updates
     * @param onProgress Progress callback
     * @return ImportResult with statistics and detailed file information
     */
    @Suppress("NestedBlockDepth")
    suspend fun executeImport(
        images: List<ImageFile>,
        destinationPath: String,
        configuration: ImportConfiguration,
        importProgress: MutableStateFlow<ImportProgress>,
        onProgress: (ImportProgress) -> Unit = {},
    ): ImportResult {
        val startTime = timeProvider.currentTimeMillis()
        val copiedFiles = mutableListOf<CopiedFile>()
        val fileDetails = mutableListOf<ImportFileDetail>()

        val errors = mutableListOf<ImportError>()
        var successCount = 0
        val duplicateCount = 0
        var skippedCount = 0
        var deletedCount = 0

        val totalBytes = images.sumOf { it.fileSize }
        var copiedBytes = 0L
        var counter = 1

        importProgress.value = ImportProgress(totalFiles = images.size, totalBytes = totalBytes)

        // Process files sequentially for deterministic ordering/collision resolution,
        // but use bounded parallelism for the actual I/O copy operations
        for ((index, image) in images.withIndex()) {
            importProgress.value =
                importProgress.value.copy(
                    currentFile = image.fileName,
                    currentIndex = index,
                    status = ImportStatus.PROCESSING,
                )
            onProgress(importProgress.value)

            try {
                val destFolder =
                    namingPort.generateFolderPath(image, destinationPath, configuration)
                val destFileName = namingPort.generateFileName(image, configuration, counter)
                var destPath = "$destFolder/$destFileName"
                var destFilePath = FilePath(destPath)
                val destParent = FilePath(destFilePath.parent ?: destFolder)
                fileSystem.mkdirs(destParent)

                var conflictResolution = configuration.conflictResolution.toString()
                if (fileSystem.exists(destFilePath)) {
                    when (configuration.conflictResolution) {
                        ConflictResolution.SKIP -> {
                            skippedCount++
                            fileDetails.add(
                                ImportFileDetail(
                                    sourcePath = image.filePath,
                                    destinationPath = "",
                                    destinationFolder = "",
                                    finalFilename = "",
                                    originalFilename = image.fileName,
                                    folderPattern = configuration.folderPattern,
                                    filenamePattern = configuration.fileNamePattern,
                                    resolvedFolder = "",
                                    fileSize = image.fileSize,
                                    fileHash = image.hash,
                                    success = false,
                                    errorMessage = "Skipped due to conflict",
                                    wasSkipped = true,
                                    conflictResolution = conflictResolution,
                                    sequenceNumber = counter,
                                )
                            )
                            continue
                        }
                        ConflictResolution.RENAME -> {
                            destPath =
                                namingPort.resolveConflict(image, destinationPath, configuration)
                            destFilePath = FilePath(destPath)
                            val renamedParent = FilePath(destFilePath.parent ?: destFolder)
                            fileSystem.mkdirs(renamedParent)
                            conflictResolution = "RENAME"
                        }
                        ConflictResolution.REPLACE -> {}
                        ConflictResolution.ASK_USER -> {
                            skippedCount++
                            fileDetails.add(
                                ImportFileDetail(
                                    sourcePath = image.filePath,
                                    destinationPath = "",
                                    destinationFolder = "",
                                    finalFilename = "",
                                    originalFilename = image.fileName,
                                    folderPattern = configuration.folderPattern,
                                    filenamePattern = configuration.fileNamePattern,
                                    resolvedFolder = "",
                                    fileSize = image.fileSize,
                                    fileHash = image.hash,
                                    success = false,
                                    errorMessage = "Skipped - user decision required",
                                    wasSkipped = true,
                                    conflictResolution = "ASK_USER",
                                    sequenceNumber = counter,
                                )
                            )
                            continue
                        }
                    }
                }

                val destAbsolutePath = fileSystem.absolutePath(destFilePath)
                val copyResult =
                    imageRepository.copyFile(image, FilePath(destAbsolutePath)) { current, _ ->
                        importProgress.value =
                            importProgress.value.copy(copiedBytes = copiedBytes + current)
                    }

                if (!copyResult) {
                    errors.add(ImportError(image, ErrorType.UNKNOWN, "Failed to copy file"))
                    fileDetails.add(
                        ImportFileDetail(
                            sourcePath = image.filePath,
                            destinationPath = "",
                            destinationFolder = destFolder,
                            finalFilename = destFileName,
                            originalFilename = image.fileName,
                            folderPattern = configuration.folderPattern,
                            filenamePattern = configuration.fileNamePattern,
                            resolvedFolder = destFolder,
                            fileSize = image.fileSize,
                            fileHash = image.hash,
                            success = false,
                            errorMessage = "Failed to copy file",
                            sequenceNumber = counter,
                        )
                    )
                    continue
                }

                var hashVerified = false
                var hashMatches = false
                if (configuration.verifyAfterCopy) {
                    hashVerified = true
                    hashMatches = imageRepository.verifyCopy(image, FilePath(destAbsolutePath))
                    if (!hashMatches) {
                        errors.add(
                            ImportError(image, ErrorType.HASH_MISMATCH, "Hash verification failed")
                        )
                        fileSystem.delete(destFilePath)
                        fileDetails.add(
                            ImportFileDetail(
                                sourcePath = image.filePath,
                                destinationPath = "",
                                destinationFolder = destFolder,
                                finalFilename = destFileName,
                                originalFilename = image.fileName,
                                folderPattern = configuration.folderPattern,
                                filenamePattern = configuration.fileNamePattern,
                                resolvedFolder = destFolder,
                                fileSize = image.fileSize,
                                fileHash = image.hash,
                                hashVerified = false,
                                success = false,
                                errorMessage = "Hash verification failed",
                                sequenceNumber = counter,
                            )
                        )
                        continue
                    }
                }

                copiedFiles.add(
                    CopiedFile(
                        sourceFile = image,
                        destinationPath = destPath,
                        hashVerified = hashVerified,
                        hashMatches = hashMatches,
                    )
                )

                val sidecarFiles = mutableListOf<String>()
                if (configuration.importSidecars && image.sidecars.isNotEmpty()) {
                    for (sidecar in image.sidecars) {
                        try {
                            val sidecarDestPath =
                                destFilePath.parent?.let { parent ->
                                    FilePath(parent)
                                        .resolve(
                                            "${fileSystem.nameWithoutExtension(destFilePath)}.${sidecar.extension}"
                                        )
                                }
                                    ?: FilePath(
                                        "${fileSystem.nameWithoutExtension(destFilePath)}.${sidecar.extension}"
                                    )
                            fileSystem.copy(sidecar, sidecarDestPath)
                            sidecarFiles.add(fileSystem.absolutePath(sidecarDestPath))
                        } catch (_: Exception) {}
                    }
                }

                var sourceDeleted = false
                if (configuration.deleteAfterImport) {
                    if (imageRepository.deleteFile(image)) {
                        sourceDeleted = true
                        deletedCount++
                    }
                    if (configuration.importSidecars) {
                        image.sidecars.forEach { sidecar -> fileSystem.delete(sidecar) }
                    }
                }

                successCount++
                copiedBytes += image.fileSize

                fileDetails.add(
                    ImportFileDetail(
                        sourcePath = image.filePath,
                        destinationPath = destPath,
                        destinationFolder = destFolder,
                        finalFilename = fileSystem.name(destFilePath),
                        originalFilename = image.fileName,
                        folderPattern = configuration.folderPattern,
                        filenamePattern = configuration.fileNamePattern,
                        resolvedFolder = destFolder,
                        fileSize = image.fileSize,
                        fileHash = image.hash,
                        hashVerified = hashVerified,
                        hashMatches = hashMatches,
                        success = true,
                        conflictResolution = conflictResolution,
                        sidecarsImported = sidecarFiles.isNotEmpty(),
                        sidecarFiles = sidecarFiles,
                        sourceDeleted = sourceDeleted,
                        exifDate = image.metadata?.dateTimeOriginal?.toString().orEmpty(),
                        cameraModel = image.metadata?.cameraModel.orEmpty(),
                        sequenceNumber = counter,
                    )
                )

                counter++
            } catch (e: Exception) {
                val errorType =
                    when {
                        e.message?.contains("not found") == true -> ErrorType.FILE_NOT_FOUND
                        e.message?.contains("permission") == true -> ErrorType.PERMISSION_DENIED
                        else -> ErrorType.UNKNOWN
                    }
                errors.add(ImportError(image, errorType, e.message ?: "Unknown error"))
                fileDetails.add(
                    ImportFileDetail(
                        sourcePath = image.filePath,
                        destinationPath = "",
                        destinationFolder = "",
                        finalFilename = "",
                        originalFilename = image.fileName,
                        folderPattern = configuration.folderPattern,
                        filenamePattern = configuration.fileNamePattern,
                        fileSize = image.fileSize,
                        fileHash = image.hash,
                        success = false,
                        errorMessage = e.message ?: "Unknown error",
                        sequenceNumber = counter,
                    )
                )
            }
        }

        val result =
            ImportResult(
                    totalFiles = images.size,
                    successCount = successCount,
                    errorCount = errors.size,
                    duplicateCount = duplicateCount,
                    skippedCount = skippedCount,
                    deletedSourceCount = deletedCount,
                    copiedFiles = copiedFiles,
                    errors = errors,
                    startTime = startTime,
                    endTime = timeProvider.currentTimeMillis(),
                )
                .also { importProgress.value = ImportProgress() }

        val historyEntry =
            ImportHistoryEntry(
                timestamp = startTime,
                timestampString = ImportHistoryEntry.createTimestampString(startTime),
                sourcePath = images.firstOrNull()?.filePath?.substringBeforeLast("/").orEmpty(),
                destinationPath = destinationPath,
                profileName = "",
                folderPattern = configuration.folderPattern,
                filenamePattern = configuration.fileNamePattern,
                totalFiles = images.size,
                successCount = successCount,
                errorCount = errors.size,
                skippedCount = skippedCount,
                duplicateCount = duplicateCount,
                deletedSourceCount = deletedCount,
                totalBytes = totalBytes,
                copiedBytes = copiedBytes,
                durationMs = timeProvider.currentTimeMillis() - startTime,
                fileDetails = fileDetails,
                importMode = "Import All",
                verifyHashes = configuration.verifyAfterCopy,
                conflictResolution = configuration.conflictResolution.toString(),
                importSidecars = configuration.importSidecars,
            )

        return result.copy(historyEntry = historyEntry)
    }
}
