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

  suspend fun scanAndPreview(
      folderPath: String,
      configuration: ImportConfiguration,
      renameOnly: Boolean = false,
      onProgress: (ReorganizeProgress) -> Unit = {}
  ): ReorganizePreview =
      withContext(Dispatchers.IO) {
        val rootDir = File(folderPath)
        require(rootDir.exists() && rootDir.isDirectory) { "Folder does not exist: $folderPath" }

        onProgress(ReorganizeProgress(phase = ReorganizePhase.SCANNING))
        val files = imageRepository.scanDirectory(rootDir, recursive = true)

        if (files.isEmpty()) {
          return@withContext ReorganizePreview(
              mappings = emptyList(),
              totalFiles = 0,
              changedFiles = 0,
              conflictCount = 0,
              newFolderCount = 0)
        }

        onProgress(ReorganizeProgress(phase = ReorganizePhase.PREVIEWING, total = files.size))

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
                              phase = ReorganizePhase.PREVIEWING))
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
                  isChanged = isChanged))
        }

        ReorganizePreview(
            mappings = mappings,
            totalFiles = files.size,
            changedFiles = mappings.count { it.isChanged },
            conflictCount = mappings.count { it.wouldConflict },
            newFolderCount = newFolders.size)
      }

  suspend fun execute(
      preview: ReorganizePreview,
      onProgress: (ReorganizeProgress) -> Unit = {}
  ): ReorganizeResult =
      withContext(Dispatchers.IO) {
        val toMove = preview.mappings.filter { it.isChanged }
        if (toMove.isEmpty()) {
          return@withContext ReorganizeResult(
              movedCount = 0, renamedCount = 0, skippedCount = 0, errorCount = 0)
        }

        val journalEntries = mutableListOf<JournalEntry>()
        var movedCount = 0
        var renamedCount = 0
        var skippedCount = 0
        val errors = mutableListOf<String>()

        for ((index, mapping) in toMove.withIndex()) {
          onProgress(
              ReorganizeProgress(
                  current = index + 1,
                  total = toMove.size,
                  currentFile = mapping.file.fileName,
                  phase = ReorganizePhase.EXECUTING))

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
            if (source.renameTo(dest)) {
              journalEntries.add(
                  JournalEntry(originalPath = mapping.currentPath, newPath = mapping.newPath))
              if (sameDir) renamedCount++ else movedCount++
            } else {
              // renameTo can fail across filesystems — fall back to copy + delete
              source.copyTo(dest, overwrite = false)
              source.delete()
              journalEntries.add(
                  JournalEntry(originalPath = mapping.currentPath, newPath = mapping.newPath))
              movedCount++
            }
          } catch (e: Exception) {
            errors.add("${mapping.file.fileName}: ${e.message}")
          }
        }

        // Clean up empty directories left behind
        cleanEmptyDirs(
            File(
                preview.mappings.first().file.file.parentFile?.parent
                    ?: return@withContext ReorganizeResult(
                        movedCount = movedCount,
                        renamedCount = renamedCount,
                        skippedCount = skippedCount,
                        errorCount = errors.size,
                        errors = errors)))

        // Save undo journal
        var journalPath: String? = null
        if (journalEntries.isNotEmpty()) {
          val rootFolder =
              preview.mappings.firstOrNull()?.file?.file?.parentFile?.absolutePath ?: ""
          val journal = ReorganizeJournal(rootFolder = rootFolder, moves = journalEntries)
          val journalDir = File(System.getProperty("user.home"), ".petrie-importer/journals")
          journalDir.mkdirs()
          val journalFile = File(journalDir, "reorg_${System.currentTimeMillis()}.json")
          journalFile.writeText(json.encodeToString(journal))
          journalPath = journalFile.absolutePath
        }

        onProgress(
            ReorganizeProgress(
                current = toMove.size, total = toMove.size, phase = ReorganizePhase.COMPLETE))

        ReorganizeResult(
            movedCount = movedCount,
            renamedCount = renamedCount,
            skippedCount = skippedCount,
            errorCount = errors.size,
            errors = errors,
            journalPath = journalPath)
      }

  suspend fun undo(
      journalPath: String,
      onProgress: (ReorganizeProgress) -> Unit = {}
  ): ReorganizeResult =
      withContext(Dispatchers.IO) {
        val journalFile = File(journalPath)
        require(journalFile.exists()) { "Journal file not found: $journalPath" }

        val journal = json.decodeFromString<ReorganizeJournal>(journalFile.readText())
        val moves = journal.moves.reversed()
        var movedCount = 0
        val errors = mutableListOf<String>()

        onProgress(ReorganizeProgress(phase = ReorganizePhase.ROLLING_BACK, total = moves.size))

        for ((index, entry) in moves.withIndex()) {
          onProgress(
              ReorganizeProgress(
                  current = index + 1,
                  total = moves.size,
                  currentFile = File(entry.newPath).name,
                  phase = ReorganizePhase.ROLLING_BACK))

          try {
            val current = File(entry.newPath)
            val original = File(entry.originalPath)

            if (!current.exists()) {
              errors.add("File not found for undo: ${entry.newPath}")
              continue
            }

            original.parentFile?.mkdirs()
            if (current.renameTo(original)) {
              movedCount++
            } else {
              current.copyTo(original, overwrite = false)
              current.delete()
              movedCount++
            }
          } catch (e: Exception) {
            errors.add("Undo failed for ${entry.newPath}: ${e.message}")
          }
        }

        cleanEmptyDirs(File(journal.rootFolder))

        ReorganizeResult(
            movedCount = movedCount,
            renamedCount = 0,
            skippedCount = 0,
            errorCount = errors.size,
            errors = errors)
      }

  fun listJournals(): List<File> {
    val dir = File(System.getProperty("user.home"), ".petrie-importer/journals")
    return dir.listFiles()
        ?.filter { it.extension == "json" }
        ?.sortedByDescending { it.lastModified() } ?: emptyList()
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
