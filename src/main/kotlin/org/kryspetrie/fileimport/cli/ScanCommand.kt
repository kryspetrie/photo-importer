package org.kryspetrie.fileimport.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.float
import com.github.ajalt.clikt.parameters.types.int
import kotlinx.coroutines.runBlocking
import org.kryspetrie.fileimport.application.ScanService
import org.kryspetrie.fileimport.domain.model.CorrectionStrategy
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.port.ImageProcessingPort
import org.kryspetrie.fileimport.domain.port.PhotoScanExportPort

/**
 * CLI command for photo scan: detect and extract individual photos from scanned images.
 *
 * Supports presets for common workflows and fine-grained control over detection, correction, and
 * export parameters. All diagnostic output goes to stderr; coordinate output (--coords) goes to
 * stdout for piping.
 *
 * Usage:
 * ```
 * photo-import scan ./scans/ -o ./crops/
 * photo-import scan ./scans/ -o ./crops/ --preset corner_refine
 * photo-import scan ./scans/ --dry-run
 * photo-import scan photo.jpg --coords json --no-image
 * ```
 */
class ScanCommand(
    private val scanService: ScanService,
    private val exportPort: PhotoScanExportPort,
    private val imageProcessing: ImageProcessingPort,
) :
    CliktCommand(
        name = "scan",
        help = "Detect and extract individual photos from scanned images (Photo Scan mode)",
    ) {

    // ── Input/Output ────────────────────────────────────────────────

    private val sources by
        argument(
                help =
                    "Source image file(s) or directory. A directory scans all image files recursively."
            )
            .file(mustExist = true)
            .multiple()

    private val output by
        option("-o", "--output", help = "Output directory for extracted photos (default: current)")
            .default(".")

    private val recursive by
        option("-r", "--recursive", help = "Scan directories recursively").flag(default = true)

    private val noRecursive by
        option("--no-recursive", help = "Do not scan directories recursively").flag(default = false)

    // ── Detection ───────────────────────────────────────────────────

    private val preset by
        option("--preset", help = "Scan preset: fast, pose_refine, or corner_refine")
            .choice("fast", "pose_refine", "corner_refine")
            .default("corner_refine")

    // ── Correction ───────────────────────────────────────────────────

    private val crop by
        option("--crop", help = "Correction strategy: simple, warp, or warp-stretch")
            .choice("simple", "warp", "warp-stretch")
            .default("warp-stretch")

    private val margin by
        option("--crop-margin", help = "Crop margin fraction (0.0-0.1, default 0.02)")
            .float()
            .default(0.02f)

    private val rotation by
        option("--rotation", help = "Output rotation in degrees: 0, 90, 180, 270").int().default(0)

    // ── Utility ─────────────────────────────────────────────────────

    private val coords by
        option("--coords", help = "Output detected coordinates: json or text")
            .choice("json", "text")

    private val noImage by
        option("--no-image", help = "Skip image export; only detect and output coordinates")
            .flag(default = false)

    private val dryRun by
        option("--dry-run", help = "Preview detection without writing files").flag(default = false)

    private val limit by
        option("-n", "--limit", help = "Maximum number of source images to process (0 = all)")
            .int()
            .default(0)

    override fun run() {
        val startTime = System.currentTimeMillis()

        // Resolve source files
        val sourceFiles = resolveSourceFiles()
        if (sourceFiles.isEmpty()) {
            echo("No image files found in specified sources.", err = true)
            return
        }

        val limitedFiles = if (limit > 0) sourceFiles.take(limit) else sourceFiles

        echo("═══════════════════════════════════════", err = true)
        echo("Photo Scan - CLI", err = true)
        echo("═══════════════════════════════════════", err = true)
        echo("Preset: $preset | Correction: $crop | Margin: $margin", err = true)
        echo("Source files: ${limitedFiles.size}", err = true)
        if (dryRun) echo("Mode: DRY RUN (no files will be written)", err = true)
        echo("", err = true)

        // Build configuration from preset + overrides
        val correctionStrategy =
            when (crop) {
                "simple" -> CorrectionStrategy.CROP
                "warp" -> CorrectionStrategy.CROP_AND_ROTATE
                "warp-stretch" -> CorrectionStrategy.PERSPECTIVE
                else -> CorrectionStrategy.PERSPECTIVE
            }

        val allResults = mutableListOf<ScanResult>()
        val allCoords = mutableListOf<Pair<String, List<CoordinateOutput>>>()

        for ((index, sourceFile) in limitedFiles.withIndex()) {
            val fileName = sourceFile.name
            echo("[${index + 1}/${limitedFiles.size}] $fileName: Detecting...", err = true)

            val detectedPhotos = scanService.detectPhotos(sourceFile.path)
            if (detectedPhotos.isEmpty()) {
                echo(
                    "[${index + 1}/${limitedFiles.size}] $fileName: No photos detected",
                    err = true,
                )
                allResults.add(ScanResult(fileName, 0, emptyList()))
                continue
            }

            echo(
                "[${index + 1}/${limitedFiles.size}] $fileName: Found ${detectedPhotos.size} photo(s)",
                err = true,
            )

            // Apply config overrides to detected photos
            val configuredPhotos =
                detectedPhotos.map { photo ->
                    photo.copy(
                        configuration =
                            photo.configuration.copy(
                                correctionStrategy = correctionStrategy,
                                rotationDegrees = rotation,
                            ),
                        applyPerspectiveCorrection = correctionStrategy != CorrectionStrategy.CROP,
                    )
                }

            // Coordinate output
            val coordOutputs =
                configuredPhotos.map { photo ->
                    CoordinateOutput(
                        id = photo.id,
                        corners =
                            listOf(
                                photo.topLeft,
                                photo.topRight,
                                photo.bottomRight,
                                photo.bottomLeft,
                            ),
                    )
                }
            allCoords.add(fileName to coordOutputs)

            // Export images unless --no-image or --dry-run
            if (!noImage && !dryRun) {
                val image = imageProcessing.readImage(FilePath(sourceFile.path))
                if (image != null) {
                    try {
                        val baseName = sourceFile.nameWithoutExtension
                        val result = runBlocking {
                            exportPort.exportPhotos(
                                sourceFile = FilePath(sourceFile.path),
                                image = image,
                                detectedPhotos = configuredPhotos,
                                destinationPath = output,
                                baseFileName = baseName,
                            )
                        }
                        echo(
                            "[${index + 1}/${limitedFiles.size}] $fileName: Done (${configuredPhotos.size} photos exported)",
                            err = true,
                        )
                        allResults.add(ScanResult(fileName, configuredPhotos.size, result.errors))
                    } catch (e: Exception) {
                        echo(
                            "[${index + 1}/${limitedFiles.size}] $fileName: Error - ${e.message}",
                            err = true,
                        )
                        allResults.add(
                            ScanResult(fileName, 0, listOf(e.message ?: "Unknown error"))
                        )
                    }
                } else {
                    echo(
                        "[${index + 1}/${limitedFiles.size}] $fileName: Error - Could not read image",
                        err = true,
                    )
                    allResults.add(ScanResult(fileName, 0, listOf("Could not read image")))
                }
            } else {
                allResults.add(ScanResult(fileName, configuredPhotos.size, emptyList()))
                echo(
                    "[${index + 1}/${limitedFiles.size}] $fileName: ${if (dryRun) "Preview" else "Detected"} (${configuredPhotos.size} photos)",
                    err = true,
                )
            }
        }

        val durationMs = System.currentTimeMillis() - startTime
        val totalPhotos = allResults.sumOf { it.photoCount }

        // Summary on stderr
        val separator = "═══════════════════════════════════════"
        echo("", err = true)
        echo(separator, err = true)
        echo(OutputFormatter.formatSummary(limitedFiles.size, totalPhotos, durationMs), err = true)
        echo(separator, err = true)

        // Error summary
        val allErrors = allResults.filter { it.errors.isNotEmpty() }.flatMap { it.errors }
        if (allErrors.isNotEmpty()) {
            echo("", err = true)
            echo("Errors:", err = true)
            allErrors.forEach { echo("  - $it", err = true) }
        }

        // Coordinate output on stdout (for piping)
        if (coords != null) {
            for ((sourcePath, photoCoords) in allCoords) {
                val formatted =
                    if (coords == "json") {
                        OutputFormatter.formatCoordsJson(sourcePath, photoCoords)
                    } else {
                        OutputFormatter.formatCoordsText(sourcePath, photoCoords)
                    }
                echo(formatted)
            }
        }
    }

    /** Resolve source arguments into a list of image files. */
    private fun resolveSourceFiles(): List<java.io.File> {
        val extensions =
            setOf("jpg", "jpeg", "png", "tiff", "tif", "bmp", "gif", "webp", "heic", "heif")
        val files = mutableListOf<java.io.File>()

        for (source in sources) {
            if (source.isFile) {
                val ext = source.extension.lowercase()
                if (ext in extensions) {
                    files.add(source)
                }
            } else if (source.isDirectory) {
                val isRecursive = recursive && !noRecursive
                val dirFiles =
                    if (isRecursive) {
                        source.walkTopDown().filter { it.isFile }.toList()
                    } else {
                        source.listFiles()?.toList() ?: emptyList()
                    }
                files.addAll(dirFiles.filter { it.extension.lowercase() in extensions })
            }
        }

        return files.sortedBy { it.name }
    }
}

/** Simple result tracker for summary output. */
private data class ScanResult(val fileName: String, val photoCount: Int, val errors: List<String>)
