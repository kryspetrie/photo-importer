package org.kryspetrie.fileimport.cli

import com.github.ajalt.clikt.core.*
import com.github.ajalt.clikt.parameters.arguments.*
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.*
import kotlinx.coroutines.runBlocking
import org.kryspetrie.fileimport.application.ImportService
import org.kryspetrie.fileimport.domain.model.*
import org.kryspetrie.fileimport.infrastructure.adapter.*

class PhotoImportCli(private val importService: ImportService) :
    CliktCommand(
        name = "photo-import",
        help = "Petrie File Importer - Command line photo organizer",
        epilog =
            """
            Examples:
              photo-import import /source /destination
              photo-import import /source /destination --dry-run
              photo-import check-duplicates /source
            """
                .trimIndent()) {
  private val verbose by
      option("-v", "--verbose", help = "Enable verbose output").flag(default = false)

  override fun run() {
    echo("Petrie File Importer CLI\nUse --help for usage information")
  }
}

class ImportCommand(private val importService: ImportService) :
    CliktCommand(name = "import", help = "Import images from source to destination") {
  private val source by
      argument(help = "Source folder containing images").file(mustExist = true, canBeDir = true)
  private val destination by argument(help = "Destination folder").file(canBeDir = true)
  private val dryRun by option("--dry-run", help = "Preview without copying").flag(default = false)
  private val recursive by
      option("--recursive", "-r", help = "Scan subdirectories").flag(default = true)
  private val folderPattern by
      option("--folder-pattern", help = "Folder hierarchy pattern").default("{yyyy-MM-dd}")
  private val filenamePattern by
      option("--filename-pattern", help = "Filename pattern").default("{original}")
  private val verifyHash by
      option("--verify-hash", help = "Verify files after copy").flag(default = true)
  private val deleteSource by
      option("--delete-source", help = "Delete source after copy").flag(default = false)

  override fun run() = runBlocking {
    echo("=".repeat(50))
    echo("Petrie File Importer - Import")
    echo("=".repeat(50))
    echo("Source: ${source.absolutePath}")
    echo("Destination: ${destination.absolutePath}")
    echo("Mode: ${if (dryRun) "DRY RUN (no files will be copied)" else "LIVE IMPORT"}")
    echo()

    echo("Scanning for images...")
    val images = importService.scanSource(source.absolutePath, recursive)

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
            deleteAfterImport = deleteSource)

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
        result.errors.forEach { error -> echo("  ${error.file.fileName}: ${error.message}") }
      }
    }
  }
}

class CheckDuplicatesCommand(private val importService: ImportService) :
    CliktCommand(name = "check-duplicates", help = "Check for duplicate images") {
  private val source by
      argument(help = "Source folder to check").file(mustExist = true, canBeDir = true)
  private val recursive by
      option("--recursive", "-r", help = "Check subdirectories").flag(default = true)
  private val useVisual by
      option("--visual", help = "Use visual similarity detection").flag(default = false)

  override fun run() = runBlocking {
    echo("Scanning for images...")
    val images = importService.scanSource(source.absolutePath, recursive)

    if (images.isEmpty()) {
      echo("No images found")
      return@runBlocking
    }

    val config =
        ImportConfiguration(detectVisualDuplicates = useVisual, perceptualHashThreshold = 0.95f)

    echo("Checking for duplicates...")
    val duplicates = importService.findVisualDuplicates(images, config)

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
          "Potential space savings: ${duplicates.sumOf { dup -> dup.duplicateImages.sumOf { it.fileSize } } / 1024 / 1024} MB")
    }
  }
}

fun main(args: Array<String>) {
  val imageRepo = ImageRepositoryAdapter()
  val importService = ImportService(imageRepo, DeduplicationAdapter(imageRepo), NamingAdapter())
  PhotoImportCli(importService)
      .subcommands(ImportCommand(importService), CheckDuplicatesCommand(importService))
      .main(args)
}
