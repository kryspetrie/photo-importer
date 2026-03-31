package org.kryspetrie.fileimport.application

import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.kryspetrie.fileimport.domain.model.*
import org.kryspetrie.fileimport.domain.port.ImageRepositoryPort
import org.kryspetrie.fileimport.domain.port.NamingPort

private val SCAN_CONCURRENCY = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
private val json = Json { prettyPrint = true }

class ReorganizeService(
    private val imageRepository: ImageRepositoryPort,
    private val namingPort: NamingPort
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
  suspend fun scanAndPreview(
      folderPath: String,
      configuration: ImportConfiguration,
      renameOnly: Boolean = false,
      mode: ReorganizeMode = ReorganizeMode.MOVE,
      onProgress: (ReorganizeProgress) -> Unit = {}
  ): ReorganizePreview =
      withContext(Dispatchers.IO) {
        val rootDir = File(folderPath)
        require(rootDir.exists() && rootDir.isDirectory) { "Folder does not exist: $folderPath" }

        onProgress(ReorganizeProgress(phase = ReorganizePhase.SCANNING, operationMode = mode))
        val files = imageRepository.scanDirectory(rootDir, recursive = true)

        if (files.isEmpty()) {
          return@withContext ReorganizePreview(
              mappings = emptyList(),
              totalFiles = 0,
              changedFiles = 0,
              conflictCount = 0,
              newFolderCount = 0,
              operationMode = mode)
        }

        onProgress(
            ReorganizeProgress(
                phase = ReorganizePhase.PREVIEWING, total = files.size, operationMode = mode))

        val semaphore = Semaphore(SCAN_CONCURRENCY)
        val counter = AtomicInteger(0)

        val filesWithMetadata = coroutineScope {
          files
              .map { file ->
                async(Dispatchers.IO) {
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
                              operationMode = mode))
                    }
                    file.copy(metadata = metadata)
                  }
                }
              }
              .awaitAll()
        }

        val destRoot = if (renameOnly) folderPath else folderPath
        val usedPaths = mutableSetOf<String>()
        val mappings = mutableListOf<ReorganizeMapping>()
        val newFolders = mutableSetOf<String>()

        filesWithMetadata.forEachIndexed { index, file ->
          val newFolder = namingPort.generateFolderPath(file, destRoot, configuration)
          val newFileName = namingPort.generateFileName(file, configuration, index + 1)
          var newPath = "$newFolder/$newFileName"

          val wouldConflict =
              newPath in usedPaths ||
                  (File(newPath).exists() && File(newPath).absolutePath != file.file.absolutePath)

          if (wouldConflict && configuration.conflictResolution == ConflictResolution.RENAME) {
            var c = 1
            val base = File(newPath).nameWithoutExtension
            val ext = File(newPath).extension
            val dir = File(newPath).parent
            while ("$dir/${base}_$c.$ext" in usedPaths ||
                (File("$dir/${base}_$c.$ext").exists() &&
                    File("$dir/${base}_$c.$ext").absolutePath != file.file.absolutePath)) {
              c++
            }
            newPath = "$dir/${base}_$c.$ext"
          }

          usedPaths.add(newPath)
          val isChanged = File(newPath).absolutePath != file.file.absolutePath

          if (isChanged) {
            val folder = File(newPath).parent
            if (folder != null && !File(folder).exists()) {
              newFolders.add(folder)
            }
          }

          mappings.add(
              ReorganizeMapping(
                  file = file,
                  currentPath = file.filePath,
                  newPath = newPath,
                  newFileName = newFileName,
                  wouldConflict = wouldConflict,
                  isChanged = isChanged,
                  mode = mode))
        }

        ReorganizePreview(
            mappings = mappings,
            totalFiles = files.size,
            changedFiles = mappings.count { it.isChanged },
            conflictCount = mappings.count { it.wouldConflict },
            newFolderCount = newFolders.size,
            operationMode = mode)
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
      onProgress: (ReorganizeProgress) -> Unit = {}
  ): ReorganizeResult =
      withContext(Dispatchers.IO) {
        val toProcess = preview.mappings.filter { it.isChanged }
        if (toProcess.isEmpty()) {
          return@withContext ReorganizeResult(
              movedCount = 0,
              renamedCount = 0,
              skippedCount = 0,
              errorCount = 0,
              operationMode = preview.operationMode)
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
                  operationMode = mapping.mode))

          try {
            val source = File(mapping.currentPath)
            val dest = File(mapping.newPath)

            if (!source.exists()) {
              errors.add("Source not found: ${mapping.currentPath}")
              continue
            }

            if (dest.exists() && dest.absolutePath != source.absolutePath) {
              skippedCount++
              continue
            }

            dest.parentFile?.mkdirs()

            val sameDir = source.parent == dest.parent
            val changeType =
                when {
                  sameDir && source.name != dest.name -> FileChangeType.RENAME_ONLY
                  !sameDir && source.name == dest.name -> FileChangeType.MOVE_ONLY
                  else -> FileChangeType.BOTH
                }

            when (mapping.mode) {
              ReorganizeMode.MOVE -> {
                if (source.renameTo(dest)) {
                  journalEntries.add(
                      JournalEntry(
                          originalPath = mapping.currentPath,
                          newPath = mapping.newPath,
                          originalFilename = source.name,
                          newFilename = dest.name,
                          originalParent = source.parent ?: "",
                          newParent = dest.parent ?: "",
                          operationType = ReorganizeMode.MOVE,
                          wasSuccessful = true,
                          fileSize = source.length(),
                          patternUsed = "",
                          changeType = changeType))
                  if (sameDir) renamedCount++ else movedCount++
                } else {
                  // renameTo can fail across filesystems — fall back to copy + delete
                  source.copyTo(dest, overwrite = false)
                  source.delete()
                  journalEntries.add(
                      JournalEntry(
                          originalPath = mapping.currentPath,
                          newPath = mapping.newPath,
                          originalFilename = source.name,
                          newFilename = dest.name,
                          originalParent = source.parent ?: "",
                          newParent = dest.parent ?: "",
                          operationType = ReorganizeMode.MOVE,
                          wasSuccessful = true,
                          fileSize = dest.length(),
                          patternUsed = "",
                          changeType = changeType))
                  movedCount++
                }
              }
              ReorganizeMode.COPY -> {
                source.copyTo(dest, overwrite = false)
                journalEntries.add(
                    JournalEntry(
                        originalPath = mapping.currentPath,
                        newPath = mapping.newPath,
                        originalFilename = source.name,
                        newFilename = dest.name,
                        originalParent = source.parent ?: "",
                        newParent = dest.parent ?: "",
                        operationType = ReorganizeMode.COPY,
                        wasSuccessful = true,
                        fileSize = dest.length(),
                        patternUsed = "",
                        changeType = changeType))
                copiedCount++
              }
            }
          } catch (e: Exception) {
            errors.add("${mapping.file.fileName}: ${e.message}")
          }
        }

        // Clean up empty directories left behind (only for MOVE operations)
        if (preview.operationMode == ReorganizeMode.MOVE) {
          cleanEmptyDirs(
              File(
                  preview.mappings.first().file.file.parentFile?.parent
                      ?: return@withContext ReorganizeResult(
                          movedCount = movedCount,
                          renamedCount = renamedCount,
                          copiedCount = copiedCount,
                          skippedCount = skippedCount,
                          errorCount = errors.size,
                          errors = errors,
                          operationMode = preview.operationMode)))
        }

        // Save undo journal
        var journalPath: String? = null
        if (journalEntries.isNotEmpty()) {
          val rootFolder =
              preview.mappings.firstOrNull()?.file?.file?.parentFile?.absolutePath ?: ""
          val journal =
              ReorganizeJournal(
                  rootFolder = rootFolder,
                  operationMode = preview.operationMode,
                  folderPattern = "",
                  filenamePattern = "",
                  totalFiles = preview.totalFiles,
                  changedFiles = preview.changedFiles,
                  entries = journalEntries)
          val journalDir = File(System.getProperty("user.home"), ".petrie-importer/journals")
          journalDir.mkdirs()
          val journalFile = File(journalDir, "reorg_${System.currentTimeMillis()}.json")
          journalFile.writeText(json.encodeToString(journal))
          journalPath = journalFile.absolutePath
        }

        onProgress(
            ReorganizeProgress(
                current = toProcess.size,
                total = toProcess.size,
                phase = ReorganizePhase.COMPLETE,
                operationMode = preview.operationMode))

        ReorganizeResult(
            movedCount = movedCount,
            renamedCount = renamedCount,
            copiedCount = copiedCount,
            skippedCount = skippedCount,
            errorCount = errors.size,
            errors = errors,
            journalPath = journalPath,
            operationMode = preview.operationMode)
      }

  /**
   * Undoes a reorganization operation by restoring files to their original locations.
   *
   * For MOVE operations: moves files back to original paths For COPY operations: deletes the copied
   * files (originals were preserved)
   *
   * @param journalPath Path to journal file from reorganization
   * @param onProgress Progress callback
   * @return Result with counts and any errors
   */
  suspend fun undo(
      journalPath: String,
      onProgress: (ReorganizeProgress) -> Unit = {}
  ): ReorganizeResult =
      withContext(Dispatchers.IO) {
        val journalFile = File(journalPath)
        require(journalFile.exists()) { "Journal file not found: $journalPath" }

        val journal = json.decodeFromString<ReorganizeJournal>(journalFile.readText())
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
                  currentFile = File(entry.newPath).name,
                  phase = ReorganizePhase.UNDOING))

          try {
            when (entry.operationType) {
              ReorganizeMode.MOVE -> {
                // Move file back to original location
                val current = File(entry.newPath)
                val original = File(entry.originalPath)

                if (!current.exists()) {
                  errors.add("File not found for undo: ${entry.newPath}")
                  continue
                }

                original.parentFile?.mkdirs()
                if (current.renameTo(original)) {
                  restoredCount++
                } else {
                  current.copyTo(original, overwrite = false)
                  current.delete()
                  restoredCount++
                }
              }
              ReorganizeMode.COPY -> {
                // For copy operations, just delete the copied file (originals preserved)
                val copied = File(entry.newPath)
                if (copied.exists()) {
                  copied.delete()
                  deletedCount++
                }
              }
            }
          } catch (e: Exception) {
            errors.add("Undo failed for ${entry.newPath}: ${e.message}")
          }
        }

        // Clean up empty directories (only for MOVE undos)
        if (journal.operationMode == ReorganizeMode.MOVE) {
          cleanEmptyDirs(File(journal.rootFolder))
        }

        // Mark journal as undone
        if (errors.isEmpty()) {
          val updatedJournal = journal.copy(undone = true)
          journalFile.writeText(json.encodeToString(updatedJournal))
        }

        ReorganizeResult(
            movedCount = restoredCount,
            renamedCount = 0,
            copiedCount = 0,
            skippedCount = 0,
            errorCount = errors.size,
            errors = errors)
      }

  /**
   * Lists all reorganization journals with summaries.
   *
   * @return List of journal summaries sorted by date (newest first)
   */
  fun listJournals(): List<ReorganizeJournalSummary> {
    val dir = File(System.getProperty("user.home"), ".petrie-importer/journals")
    val files =
        dir.listFiles()?.filter { it.extension == "json" }?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    return files.mapNotNull { file ->
      try {
        val journal = json.decodeFromString<ReorganizeJournal>(file.readText())
        ReorganizeJournalSummary(
            id = journal.id,
            timestamp = journal.timestamp,
            timestampString = ReorganizeJournal.createTimestampString(journal.timestamp),
            rootFolder = journal.rootFolder,
            operationMode = journal.operationMode,
            totalFiles = journal.totalFiles,
            changedFiles = journal.changedFiles,
            undone = journal.undone)
      } catch (e: Exception) {
        null
      }
    }
  }

  /**
   * Gets full journal details by path.
   *
   * @param journalPath Path to journal file
   * @return Full journal object or null if not found/invalid
   */
  fun getJournal(journalPath: String): ReorganizeJournal? {
    val file = File(journalPath)
    if (!file.exists()) return null
    return try {
      json.decodeFromString<ReorganizeJournal>(file.readText())
    } catch (e: Exception) {
      null
    }
  }

  private fun cleanEmptyDirs(root: File) {
    if (!root.isDirectory) return
    root.walkBottomUp().forEach { dir ->
      if (dir.isDirectory && dir != root && (dir.listFiles()?.isEmpty() == true)) {
        dir.delete()
      }
    }
  }
}
