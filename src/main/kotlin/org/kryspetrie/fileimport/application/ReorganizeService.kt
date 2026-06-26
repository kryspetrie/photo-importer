package org.kryspetrie.fileimport.application

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.kryspetrie.fileimport.domain.model.ConflictResolution
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.ImportConfiguration
import org.kryspetrie.fileimport.domain.model.JournalEntry
import org.kryspetrie.fileimport.domain.model.ReorganizeJournal
import org.kryspetrie.fileimport.domain.model.ReorganizeMode
import org.kryspetrie.fileimport.domain.model.ReorganizePhase
import org.kryspetrie.fileimport.domain.model.ReorganizePreview
import org.kryspetrie.fileimport.domain.model.ReorganizeProgress
import org.kryspetrie.fileimport.domain.model.ReorganizeResult
import org.kryspetrie.fileimport.domain.port.DispatcherProvider
import org.kryspetrie.fileimport.domain.port.FileSystemPort
import org.kryspetrie.fileimport.domain.port.ImageRepositoryPort
import org.kryspetrie.fileimport.domain.port.NamingPort
import org.kryspetrie.fileimport.domain.port.TimeProvider

private val SCAN_CONCURRENCY = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)

class ReorganizeService(
    private val imageRepository: ImageRepositoryPort,
    private val namingPort: NamingPort,
    private val timeProvider: TimeProvider,
    private val dispatcherProvider: DispatcherProvider,
    private val journalRepository: ReorganizeJournalRepository,
    private val fileOperationExecutor: FileOperationExecutor,
    private val fileSystem: FileSystemPort,
) {

    /**
     * Scans a folder and generates a preview of reorganization changes.
     *
     * @param folderPath Root folder to reorganize
     * @param configuration Import configuration with naming patterns
     * @param renameOnly If true, only rename files without changing folders
     * @param mode Operation mode: MOVE (default) or COPY
     * @param onProgress Progress callback
     */
    @Suppress("NestedBlockDepth")
    suspend fun scanAndPreview(
        folderPath: String,
        configuration: ImportConfiguration,
        renameOnly: Boolean = false,
        mode: ReorganizeMode = ReorganizeMode.MOVE,
        onProgress: (ReorganizeProgress) -> Unit = {},
    ): ReorganizePreview =
        withContext(dispatcherProvider.io) {
            val rootDir = FilePath(folderPath)
            require(fileSystem.exists(rootDir) && fileSystem.isDirectory(rootDir)) {
                "Folder does not exist: $folderPath"
            }

            onProgress(ReorganizeProgress(phase = ReorganizePhase.SCANNING, operationMode = mode))
            val files = imageRepository.scanDirectory(rootDir, recursive = true)

            if (files.isEmpty()) {
                return@withContext ReorganizePreview(
                    mappings = emptyList(),
                    totalFiles = 0,
                    changedFiles = 0,
                    conflictCount = 0,
                    newFolderCount = 0,
                    operationMode = mode,
                )
            }

            onProgress(
                ReorganizeProgress(
                    phase = ReorganizePhase.PREVIEWING,
                    total = files.size,
                    operationMode = mode,
                )
            )

            val semaphore = Semaphore(SCAN_CONCURRENCY)
            val counter = AtomicInteger(0)

            val filesWithMetadata = coroutineScope {
                files
                    .map { file ->
                        async(dispatcherProvider.io) {
                            semaphore.withPermit {
                                val metadata = imageRepository.getMetadata(file)
                                val done = counter.incrementAndGet()
                                if (done % 50 == 0 || done == files.size) {
                                    onProgress(
                                        ReorganizeProgress(
                                            current = done,
                                            total = files.size,
                                            currentFile = file.fileName,
                                            phase = ReorganizePhase.PREVIEWING,
                                            operationMode = mode,
                                        )
                                    )
                                }
                                file.copy(metadata = metadata)
                            }
                        }
                    }
                    .awaitAll()
            }

            val destRoot = if (renameOnly) folderPath else folderPath
            val usedPaths = mutableSetOf<String>()
            val mappings = mutableListOf<org.kryspetrie.fileimport.domain.model.ReorganizeMapping>()
            val newFolders = mutableSetOf<String>()

            filesWithMetadata.forEachIndexed { index, file ->
                val newFolder = namingPort.generateFolderPath(file, destRoot, configuration)
                val newFileName = namingPort.generateFileName(file, configuration, index + 1)
                var newPath = "$newFolder/$newFileName"
                val newPathFilePath = FilePath(newPath)

                val wouldConflict =
                    newPath in usedPaths ||
                        (fileSystem.exists(newPathFilePath) &&
                            fileSystem.absolutePath(newPathFilePath) != file.path.path)

                if (
                    wouldConflict && configuration.conflictResolution == ConflictResolution.RENAME
                ) {
                    var c = 1
                    val base = fileSystem.nameWithoutExtension(newPathFilePath)
                    val ext = fileSystem.extension(newPathFilePath)
                    val dir = newPathFilePath.parent
                    while (
                        "$dir/${base}_$c.$ext" in usedPaths ||
                            (fileSystem.exists(FilePath("$dir/${base}_$c.$ext")) &&
                                fileSystem.absolutePath(FilePath("$dir/${base}_$c.$ext")) != file.path.path)
                    ) {
                        c++
                    }
                    newPath = "$dir/${base}_$c.$ext"
                }

                usedPaths.add(newPath)
                val isChanged = fileSystem.absolutePath(newPathFilePath) != file.path.path

                if (isChanged) {
                    val folder = FilePath(newPath).parent
                    if (folder != null && !fileSystem.exists(FilePath(folder))) {
                        newFolders.add(folder)
                    }
                }

                mappings.add(
                    org.kryspetrie.fileimport.domain.model.ReorganizeMapping(
                        file = file,
                        currentPath = file.filePath,
                        newPath = newPath,
                        newFileName = newFileName,
                        wouldConflict = wouldConflict,
                        isChanged = isChanged,
                        mode = mode,
                    )
                )
            }

            ReorganizePreview(
                mappings = mappings,
                totalFiles = files.size,
                changedFiles = mappings.count { it.isChanged },
                conflictCount = mappings.count { it.wouldConflict },
                newFolderCount = newFolders.size,
                operationMode = mode,
            )
        }

    /**
     * Executes the reorganization operation (move or copy).
     *
     * @param preview Preview from scanAndPreview
     * @param onProgress Progress callback
     * @return Result with counts and journal path for undo
     */
    suspend fun execute(
        preview: ReorganizePreview,
        onProgress: (ReorganizeProgress) -> Unit = {},
    ): ReorganizeResult =
        withContext(dispatcherProvider.io) {
            val toProcess = preview.mappings.filter { it.isChanged }
            if (toProcess.isEmpty()) {
                return@withContext ReorganizeResult(
                    movedCount = 0,
                    renamedCount = 0,
                    skippedCount = 0,
                    errorCount = 0,
                    operationMode = preview.operationMode,
                )
            }

            val journalEntries = mutableListOf<JournalEntry>()
            var movedCount = 0
            var renamedCount = 0
            var copiedCount = 0
            var skippedCount = 0
            val errors = mutableListOf<String>()

            for ((index, mapping) in toProcess.withIndex()) {
                onProgress(
                    ReorganizeProgress(
                        current = index + 1,
                        total = toProcess.size,
                        currentFile = mapping.file.fileName,
                        phase = ReorganizePhase.EXECUTING,
                        operationMode = mapping.mode,
                    )
                )

                val result = fileOperationExecutor.executeOperation(mapping)
                result.journalEntry?.let { journalEntries.add(it) }
                movedCount += result.movedCount
                renamedCount += result.renamedCount
                copiedCount += result.copiedCount
                skippedCount += result.skippedCount
                result.error?.let { errors.add(it) }
            }

            // Clean up empty directories left behind (only for MOVE operations)
            if (preview.operationMode == ReorganizeMode.MOVE) {
                val rootFolderPath =
                    preview.mappings.firstOrNull()?.file?.path?.parent?.let { parentPath ->
                        FilePath(parentPath).parent?.let { FilePath(it) }
                    }
                        ?: return@withContext ReorganizeResult(
                            movedCount = movedCount,
                            renamedCount = renamedCount,
                            copiedCount = copiedCount,
                            skippedCount = skippedCount,
                            errorCount = errors.size,
                            errors = errors,
                            operationMode = preview.operationMode,
                        )
                cleanEmptyDirs(rootFolderPath)
            }

            // Save undo journal
            var journalPath: String? = null
            if (journalEntries.isNotEmpty()) {
                val rootFolder =
                    preview.mappings.firstOrNull()?.file?.path?.parent.orEmpty()
                val journal =
                    ReorganizeJournal(
                        rootFolder = rootFolder,
                        operationMode = preview.operationMode,
                        folderPattern = "",
                        filenamePattern = "",
                        totalFiles = preview.totalFiles,
                        changedFiles = preview.changedFiles,
                        entries = journalEntries,
                    )
                journalPath = journalRepository.saveJournal(journal, timeProvider.currentTimeMillis())
            }

            onProgress(
                ReorganizeProgress(
                    current = toProcess.size,
                    total = toProcess.size,
                    phase = ReorganizePhase.COMPLETE,
                    operationMode = preview.operationMode,
                )
            )

            ReorganizeResult(
                movedCount = movedCount,
                renamedCount = renamedCount,
                copiedCount = copiedCount,
                skippedCount = skippedCount,
                errorCount = errors.size,
                errors = errors,
                journalPath = journalPath,
                operationMode = preview.operationMode,
            )
        }

    /**
     * Undoes a reorganization operation by restoring files to their original locations.
     *
     * For MOVE operations: moves files back to original paths
     * For COPY operations: deletes the copied files (originals were preserved)
     *
     * @param journalPath Path to journal file from reorganization
     * @param onProgress Progress callback
     * @return Result with counts and any errors
     */
    suspend fun undo(
        journalPath: String,
        onProgress: (ReorganizeProgress) -> Unit = {},
    ): ReorganizeResult =
        withContext(dispatcherProvider.io) {
            val journalFilePath = FilePath(journalPath)
            require(fileSystem.exists(journalFilePath)) { "Journal file not found: $journalPath" }

            val journal = journalRepository.getJournal(journalPath)
                ?: throw IllegalArgumentException("Invalid journal file: $journalPath")

            val entries = journal.entries.reversed()
            var restoredCount = 0
            var deletedCount = 0
            val errors = mutableListOf<String>()

            onProgress(ReorganizeProgress(phase = ReorganizePhase.UNDOING, total = entries.size))

            for ((index, entry) in entries.withIndex()) {
                onProgress(
                    ReorganizeProgress(
                        current = index + 1,
                        total = entries.size,
                        currentFile = FilePath(entry.newPath).name,
                        phase = ReorganizePhase.UNDOING,
                    )
                )

                val result = fileOperationExecutor.executeUndo(entry)
                restoredCount += result.restoredCount
                deletedCount += result.deletedCount
                result.error?.let { errors.add(it) }
            }

            // Clean up empty directories (only for MOVE undos)
            if (journal.operationMode == ReorganizeMode.MOVE) {
                fileOperationExecutor.cleanEmptyDirs(FilePath(journal.rootFolder))
            }

            // Mark journal as undone
            if (errors.isEmpty()) {
                val updatedJournal = journal.copy(undone = true)
                journalRepository.markUndone(journalPath, updatedJournal)
            }

            ReorganizeResult(
                movedCount = restoredCount,
                renamedCount = 0,
                copiedCount = 0,
                skippedCount = 0,
                errorCount = errors.size,
                errors = errors,
            )
        }

    /**
     * Lists all reorganization journals with summaries.
     *
     * @return List of journal summaries sorted by date (newest first)
     */
    fun listJournals(): List<org.kryspetrie.fileimport.domain.model.ReorganizeJournalSummary> =
        journalRepository.listJournals()

    /**
     * Gets full journal details by path.
     *
     * @param journalPath Path to journal file
     * @return Full journal object or null if not found/invalid
     */
    fun getJournal(journalPath: String): org.kryspetrie.fileimport.domain.model.ReorganizeJournal? =
        journalRepository.getJournal(journalPath)

    private suspend fun cleanEmptyDirs(root: FilePath) {
        fileOperationExecutor.cleanEmptyDirs(root)
    }
}