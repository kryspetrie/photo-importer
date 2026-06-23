package org.kryspetrie.fileimport.ui.screens.wizard

import java.io.File

/** Result of a single photo export operation. */
sealed interface ExportResult {
    val originalFile: File
    val correctionsApplied: List<String>

    /** Successful export with the output file path and dimensions. */
    data class Success(
        override val originalFile: File,
        val outputPath: String,
        val dimensions: Pair<Int, Int>,
        override val correctionsApplied: List<String>,
    ) : ExportResult

    /** Failed export with the error message. */
    data class Failure(
        override val originalFile: File,
        val errorMessage: String,
        override val correctionsApplied: List<String>,
    ) : ExportResult
}

/**
 * Legacy data class for backward compatibility during migration.
 * Prefer using [ExportResult] directly.
 */
data class ProcessedPhoto(
    val originalFile: File,
    val outputPath: String,
    val dimensions: Pair<Int, Int>,
    val correctionsApplied: List<String>,
) {
    /** Whether this photo failed to export. */
    val isError: Boolean get() = outputPath.startsWith("ERROR:")

    /** Convert to typed [ExportResult]. */
    fun toExportResult(): ExportResult =
        if (isError) {
            ExportResult.Failure(
                originalFile = originalFile,
                errorMessage = outputPath.removePrefix("ERROR: "),
                correctionsApplied = correctionsApplied,
            )
        } else {
            ExportResult.Success(
                originalFile = originalFile,
                outputPath = outputPath,
                dimensions = dimensions,
                correctionsApplied = correctionsApplied,
            )
        }
}

/** Create a [ProcessedPhoto] from an [ExportResult]. */
fun ExportResult.toProcessedPhoto(): ProcessedPhoto =
    when (this) {
        is ExportResult.Success ->
            ProcessedPhoto(
                originalFile = originalFile,
                outputPath = outputPath,
                dimensions = dimensions,
                correctionsApplied = correctionsApplied,
            )
        is ExportResult.Failure ->
            ProcessedPhoto(
                originalFile = originalFile,
                outputPath = "ERROR: $errorMessage",
                dimensions = 0 to 0,
                correctionsApplied = correctionsApplied,
            )
    }