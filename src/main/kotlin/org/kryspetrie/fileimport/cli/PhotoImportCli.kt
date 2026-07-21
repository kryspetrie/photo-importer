package org.kryspetrie.fileimport.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.versionOption
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.file
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext
import org.kryspetrie.fileimport.application.ImportService
import org.kryspetrie.fileimport.application.ReorganizeService
import org.kryspetrie.fileimport.application.ScanService
import org.kryspetrie.fileimport.application.WatchFolderManager
import org.kryspetrie.fileimport.domain.model.ImportConfiguration
import org.kryspetrie.fileimport.domain.port.ImageProcessingPort
import org.kryspetrie.fileimport.domain.port.PhotoScanExportPort

/**
 * Top-level CLI entry point for PhotoImporter.
 *
 * Provides `--version`, `--verbose`, `--quiet` flags and delegates to subcommands:
 * - **import**: Copy images from source to destination with dedup and naming patterns
 * - **check-duplicates**: Find duplicate images (hash or visual similarity)
 * - **scan**: Detect and extract individual photos from scanned images
 * - **watch**: Monitor a folder and auto-import new images (headless)
 * - **reorganize**: Reorganize an existing library with new naming patterns
 * - **undo**: Undo a previous reorganization
 * - **check-journals**: List reorganization journals
 */
class PhotoImportCli(
    private val importService: ImportService,
    private val reorganizeService: ReorganizeService,
    private val scanService: ScanService,
    private val exportPort: PhotoScanExportPort,
    private val imageProcessing: ImageProcessingPort,
    private val watchFolderManager: WatchFolderManager,
    private val version: String = "1.0.0",
) :
    CliktCommand(
        name = "photo-import",
        help = "PhotoImporter - Command line photo organizer",
        epilog =
            """
            Examples:
              photo-import import /source /destination
              photo-import import /source /destination --dry-run
              photo-import import /source /destination --no-recursive --no-verify-hash
              photo-import check-duplicates /source
              photo-import check-duplicates /source --method visual
              photo-import scan /scans/ -o /crops/ --preset corner_refine
              photo-import scan photo.jpg --coords json --no-image
              photo-import watch ~/Incoming ~/Library --cooldown 3000
              photo-import reorganize /library/path
              photo-import reorganize /library/path --dry-run
            """
                .trimIndent(),
    ) {
    init {
        versionOption(version, names = setOf("--version", "-V"))
    }

    private val verbose by option("-v", "--verbose", help = "Enable verbose output").flag()
    private val quiet by option("-q", "--quiet", help = "Suppress non-error output").flag()

    override fun run() {
        if (verbose && quiet) {
            echo("Cannot use --verbose and --quiet together.", err = true)
            return
        }
    }
}

/**
 * Standalone CLI entry point.
 *
 * Called from [org.kryspetrie.fileimport.main] when CLI mode is detected. Expects Koin to already
 * be initialized so all services are available via DI.
 *
 * @see org.kryspetrie.fileimport.main
 */
@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    val koin = GlobalContext.get()

    val importService: ImportService = koin.get()
    val reorganizeService: ReorganizeService = koin.get()
    val scanService: ScanService = koin.get()
    val exportPort: PhotoScanExportPort = koin.get()
    val imageProcessing: ImageProcessingPort = koin.get()
    val watchFolderManager: WatchFolderManager = koin.get()

    val cli =
        PhotoImportCli(
            importService = importService,
            reorganizeService = reorganizeService,
            scanService = scanService,
            exportPort = exportPort,
            imageProcessing = imageProcessing,
            watchFolderManager = watchFolderManager,
        )
    cli.subcommands(
            ImportCommand(importService),
            CheckDuplicatesCommand(importService),
            ScanCommand(scanService, exportPort, imageProcessing),
            WatchCommand(watchFolderManager),
            *getReorganizeCommands(reorganizeService).toTypedArray(),
        )
        .main(args)
}

class ImportCommand(private val importService: ImportService) :
    CliktCommand(name = "import", help = "Import images from source to destination") {
    private val source by
        argument(help = "Source folder containing images").file(mustExist = true, canBeDir = true)
    private val destination by argument(help = "Destination folder").file(canBeDir = true)
    private val dryRun by
        option("--dry-run", help = "Preview without copying").flag(default = false)
    private val noRecursive by
        option("--no-recursive", help = "Do not scan subdirectories").flag(default = false)
    private val folderPattern by
        option("--folder-pattern", help = "Folder hierarchy pattern (default: {yyyy-MM-dd})")
            .default("{yyyy-MM-dd}")
    private val filenamePattern by
        option("--filename-pattern", help = "Filename pattern (default: {original})")
            .default("{original}")
    private val noVerifyHash by
        option("--no-verify-hash", help = "Skip file verification after copy").flag(default = false)
    private val deleteSource by
        option("--delete-source", help = "Delete source files after copy").flag(default = false)

    override fun run() = runBlocking {
        val recursive = !noRecursive
        val verifyHash = !noVerifyHash

        echo("=".repeat(50))
        echo("PhotoImporter - Import")
        echo("=".repeat(50))
        echo("Source: ${source.absolutePath}")
        echo("Destination: ${destination.absolutePath}")
        echo("Recursive: $recursive")
        echo("Verify: $verifyHash")
        echo("Mode: ${if (dryRun) "DRY RUN (no files will be copied)" else "LIVE IMPORT"}")
        echo()

        echo("Scanning for images...")
        val images =
            try {
                importService.scanSource(source.absolutePath, recursive)
            } catch (e: Exception) {
                echo("Error scanning source: ${e.message}", err = true)
                return@runBlocking
            }

        if (images.isEmpty()) {
            echo("No images found in source directory.", err = true)
            return@runBlocking
        }

        echo("Found ${images.size} image(s)")
        echo()

        val config =
            ImportConfiguration(
                folderPattern = folderPattern,
                fileNamePattern = filenamePattern,
                verifyAfterCopy = verifyHash,
                deleteAfterImport = deleteSource,
            )

        echo("Previewing output structure...")
        val previews = importService.previewStructure(images, destination.absolutePath, config)
        echo("Will create ${previews.map { it.folderPath }.distinct().size} folder(s)")
        echo()

        if (dryRun) {
            echo("=".repeat(50))
            echo("DRY RUN SUMMARY")
            echo("=".repeat(50))
            echo("Total files: ${images.size}")
            echo("No files have been copied.")
        } else {
            echo("Starting import...")
            try {
                val result = importService.executeImport(images, destination.absolutePath, config)

                echo()
                echo("=".repeat(50))
                echo("IMPORT RESULTS")
                echo("=".repeat(50))
                echo("Total files: ${result.totalFiles}")
                echo("Successfully copied: ${result.successCount}")
                echo("Duplicates skipped: ${result.duplicateCount}")
                echo("Errors: ${result.errorCount}")
                echo("Duration: ${result.duration / 1000}s")

                if (result.errors.isNotEmpty()) {
                    echo()
                    echo("Errors:")
                    result.errors.forEach { error ->
                        echo("  ${error.file.fileName}: ${error.message}")
                    }
                }
            } catch (e: Exception) {
                echo("Import failed: ${e.message}", err = true)
            }
        }
    }
}

class CheckDuplicatesCommand(private val importService: ImportService) :
    CliktCommand(
        name = "check-duplicates",
        help = "Check for duplicate images using hash-based or visual similarity detection",
    ) {
    private val source by
        argument(help = "Source folder to check").file(mustExist = true, canBeDir = true)
    private val noRecursive by
        option("--no-recursive", help = "Do not check subdirectories").flag(default = false)
    private val method by
        option(
                "--method",
                help =
                    "Detection method: hash (fast, exact matches) or visual (slower, perceptual matches)",
            )
            .choice("hash", "visual")
            .default("hash")

    override fun run() = runBlocking {
        val recursive = !noRecursive
        val useVisual = method == "visual"

        echo("Scanning for images...")
        val images =
            try {
                importService.scanSource(source.absolutePath, recursive)
            } catch (e: Exception) {
                echo("Error scanning source: ${e.message}", err = true)
                return@runBlocking
            }

        if (images.isEmpty()) {
            echo("No images found")
            return@runBlocking
        }

        echo("Found ${images.size} image(s)")
        echo("Detection method: ${if (useVisual) "visual similarity" else "file hash (exact)"}")

        val config =
            ImportConfiguration(detectVisualDuplicates = useVisual, perceptualHashThreshold = 0.95f)

        echo("Checking for duplicates...")
        val duplicates =
            try {
                importService.findVisualDuplicates(images, config)
            } catch (e: Exception) {
                echo("Error checking duplicates: ${e.message}", err = true)
                return@runBlocking
            }

        if (duplicates.isEmpty()) {
            echo("No duplicates found!")
        } else {
            echo()
            echo("Found ${duplicates.size} duplicate group(s):")
            echo()

            duplicates.forEachIndexed { index, dup ->
                echo("Group ${index + 1}:")
                echo("  Type: ${dup.duplicateType}")
                echo("  Primary: ${dup.primaryImage.fileName}")
                echo("  Size: ${dup.primaryImage.fileSize} bytes")
                dup.primaryImage.hash?.let { echo("  Hash: $it") }
                echo("  Duplicates:")
                dup.duplicateImages.forEach { dupImg ->
                    echo("    - ${dupImg.fileName}")
                    dupImg.hash?.let { echo("      Hash: $it") }
                }
                echo()
            }

            val totalDupes = duplicates.sumOf { it.duplicateImages.size }
            echo("Total duplicate files: $totalDupes")
            echo(
                "Potential space savings: " +
                    "${duplicates.sumOf { dup -> dup.duplicateImages.sumOf { it.fileSize } } / 1024 / 1024} MB"
            )
        }
    }
}
