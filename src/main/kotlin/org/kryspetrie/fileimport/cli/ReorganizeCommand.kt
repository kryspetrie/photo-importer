package org.kryspetrie.fileimport.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import kotlinx.coroutines.runBlocking
import org.kryspetrie.fileimport.application.ReorganizeService
import org.kryspetrie.fileimport.domain.model.ConflictResolution
import org.kryspetrie.fileimport.domain.model.ImportConfiguration
import org.kryspetrie.fileimport.domain.model.ReorganizeMode

class ReorganizeCommand(private val reorganizeService: ReorganizeService) :
    CliktCommand(
        name = "reorganize",
        help = "Reorganize existing photo library with new folder/filename patterns",
    ) {
    private val source by argument(help = "Source folder containing images to reorganize")

    private val destination by
        option("--destination", "-d", help = "Destination folder (default: same as source)")

    private val dryRun by
        option("--dry-run", help = "Preview changes without applying").flag(default = false)

    private val renameOnly by
        option("--rename-only", help = "Only rename files, don't move to subfolders")
            .flag(default = false)

    private val mode by
        option("--mode", help = "Operation mode: move or copy")
            .choice("move", "copy")
            .default("move")

    private val folderPattern by
        option("--folder-pattern", help = "Folder hierarchy pattern (default: {yyyy-MM-dd})")
            .default("{yyyy-MM-dd}")

    private val filenamePattern by
        option("--filename-pattern", help = "Filename pattern (default: {original})")
            .default("{original}")

    private val preserveOriginalName by
        option("--preserve-original-name", help = "Preserve original filename")
            .flag(default = false)

    private val conflictResolution by
        option(
                "--conflict-resolution",
                help =
                    "Conflict resolution: rename (add _1, _2, etc.) or replace (overwrite existing)",
            )
            .choice("rename", "replace")
            .default("rename")

    override fun run() = runBlocking {
        echo("=".repeat(50))
        echo("Petrie File Importer - Reorganize")
        echo("=".repeat(50))
        echo("Source: $source")
        val dest = destination ?: source
        echo("Destination: $dest")
        echo("Mode: ${if (dryRun) "DRY RUN (no files will be changed)" else "LIVE REORGANIZE"}")
        echo()

        val config =
            ImportConfiguration(
                folderPattern = folderPattern,
                fileNamePattern = if (preserveOriginalName) "{original}" else filenamePattern,
                preserveOriginalName = preserveOriginalName,
                conflictResolution =
                    when (conflictResolution) {
                        "rename" -> ConflictResolution.RENAME
                        "replace" -> ConflictResolution.REPLACE
                        else -> ConflictResolution.RENAME
                    },
            )

        val operationMode = if (mode == "copy") ReorganizeMode.COPY else ReorganizeMode.MOVE

        if (dryRun) {
            echo("Scanning files...")
            val preview =
                reorganizeService.scanAndPreview(source, config, renameOnly, operationMode) {
                    progress ->
                    if (progress.total > 0) {
                        echo("${progress.phase}: ${progress.current}/${progress.total} files")
                    }
                }

            echo()
            echo("=".repeat(50))
            echo("DRY RUN SUMMARY")
            echo("=".repeat(50))
            echo("Total files: ${preview.totalFiles}")
            echo("Will change: ${preview.changedFiles}")
            echo("New folders: ${preview.newFolderCount}")
            echo("Conflicts: ${preview.conflictCount}")
            echo("Mode: ${preview.operationMode}")
            echo()

            if (preview.changedFiles > 0) {
                echo("Preview of changes (first 10):")
                preview.mappings
                    .filter { it.isChanged }
                    .take(10)
                    .forEach { mapping ->
                        echo(
                            "  ${mapping.currentPath} -> ${mapping.newPath} (${mapping.file.fileName})"
                        )
                    }
                if (preview.mappings.count { it.isChanged } > 10) {
                    echo("  ... and ${preview.mappings.count { it.isChanged } - 10} more")
                }
            } else {
                echo("All files are already organized according to this pattern.")
            }
        } else {
            echo("Starting reorganization...")
            val preview =
                reorganizeService.scanAndPreview(source, config, renameOnly, operationMode) {
                    progress ->
                    if (progress.total > 0) {
                        echo("${progress.phase}: ${progress.current}/${progress.total} files")
                    }
                }

            echo("=".repeat(50))
            echo("Preview Complete")
            echo("=".repeat(50))
            echo("Total files: ${preview.totalFiles}")
            echo("Will change: ${preview.changedFiles}")
            echo("New folders: ${preview.newFolderCount}")
            echo("Conflicts: ${preview.conflictCount}")
            echo()

            if (preview.changedFiles == 0) {
                echo("No changes needed. All files are already organized.")
                return@runBlocking
            }

            echo("Ready to apply changes...")
            val result =
                reorganizeService.execute(preview) { progress ->
                    if (progress.total > 0) {
                        echo(
                            "${progress.phase}: ${progress.current}/${progress.total} - ${progress.currentFile}"
                        )
                    }
                }

            echo()
            echo("=".repeat(50))
            echo("REORGANIZE RESULTS")
            echo("=".repeat(50))
            echo("Total files: ${preview.totalFiles}")
            echo(
                "Changed: ${preview.changedFiles} (${result.movedCount} moved, ${result.renamedCount} renamed)"
            )
            echo("Copied: ${result.copiedCount}")
            echo("Skipped: ${result.skippedCount}")
            echo("Errors: ${result.errorCount}")

            if (result.journalPath != null) {
                echo()
                echo("Undo journal saved to: ${result.journalPath}")
                echo("You can undo this operation with: photo-import undo <journal-path>")
            }

            if (result.errors.isNotEmpty()) {
                echo()
                echo("Errors:")
                result.errors.forEach { error -> echo("  $error") }
            }
        }
    }
}

class UndoReorganizeCommand(private val reorganizeService: ReorganizeService) :
    CliktCommand(name = "undo", help = "Undo a previous reorganization operation") {
    private val journalPath by argument(help = "Journal path from previous reorganization")

    override fun run() = runBlocking {
        echo("=".repeat(50))
        echo("Petrie File Importer - Undo Reorganization")
        echo("=".repeat(50))
        echo("Journal: $journalPath")
        echo()

        val result =
            reorganizeService.undo(journalPath) { progress ->
                echo(
                    "${progress.phase}: ${progress.current}/${progress.total} - ${progress.currentFile}"
                )
            }

        echo()
        echo("=".repeat(50))
        echo("UNDO RESULTS")
        echo("=".repeat(50))
        echo("Restored: ${result.movedCount}")
        echo("Deleted (copy ops): ${result.copiedCount}")
        echo("Errors: ${result.errorCount}")

        if (result.errors.isNotEmpty()) {
            echo()
            echo("Errors:")
            result.errors.forEach { error -> echo("  $error") }
        }
    }
}

class CheckReorganizeJournalsCommand(private val reorganizeService: ReorganizeService) :
    CliktCommand(
        name = "check-journals",
        help = "List available reorganization journals for undo",
    ) {
    override fun run() = runBlocking {
        echo("=".repeat(50))
        echo("Petrie File Importer - Reorganization Journals")
        echo("=".repeat(50))
        echo()

        val journals = reorganizeService.listJournals()

        if (journals.isEmpty()) {
            echo("No reorganization journals found.")
            echo()
            echo("Run 'photo-import reorganize /path/to/library' to create a journal.")
        } else {
            echo("Found ${journals.size} journal(s):")
            echo()

            journals.forEach { journal ->
                val status = if (journal.undone) " [UNDONE]" else ""
                echo("ID: ${journal.id.take(8)}...$status")
                echo("  Date: ${journal.timestampString}")
                echo(" Folder: ${journal.rootFolder}")
                echo("  Mode: ${journal.operationMode}")
                echo("  Files changed: ${journal.changedFiles}")
                echo()
            }
        }
    }
}

fun getReorganizeCommands(reorganizeService: ReorganizeService): List<CliktCommand> =
    listOf(
        ReorganizeCommand(reorganizeService),
        UndoReorganizeCommand(reorganizeService),
        CheckReorganizeJournalsCommand(reorganizeService),
    )
