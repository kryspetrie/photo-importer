package org.kryspetrie.fileimport.cli

import org.kryspetrie.fileimport.domain.model.PhotoCorner

/**
 * Formats scan output for CLI display and coordinate export.
 *
 * All diagnostic/progress output goes to stderr; coordinate data goes to stdout, enabling piping:
 * `photo-import scan --coords json --no-image img.jpg | jq .`
 */
object OutputFormatter {
    /** Format a single scan result for progress display on stderr. */
    fun formatProgress(
        index: Int,
        total: Int,
        fileName: String,
        phase: String,
        extra: String? = null,
    ): String {
        val base = "[$index/$total] $fileName: $phase"
        return if (extra != null) "$base → $extra" else base
    }

    /** Format a final summary for a batch of scans. */
    fun formatSummary(totalImages: Int, totalPhotos: Int, durationMs: Long): String {
        val seconds = String.format("%.1f", durationMs / 1000.0)
        return " RESULTS: $totalPhotos photos from $totalImages images in ${seconds}s "
    }

    /** Format coordinates as JSON for piping to jq. */
    fun formatCoordsJson(sourcePath: String, photos: List<CoordinateOutput>): String {
        if (photos.isEmpty()) {
            return """{"source":"$sourcePath","photos":[]}"""
        }
        val photoEntries =
            photos.joinToString(",\n") { photo ->
                val corners =
                    photo.corners.joinToString(",\n") { corner ->
                        """          {"x":${corner.x},"y":${corner.y}}"""
                    }
                """      {"id":"${photo.id}","corners":[
$corners
          ]}"""
            }
        return """{"source":"$sourcePath","photos":[
$photoEntries
    ]}"""
    }

    /** Format coordinates as human-readable text for stderr. */
    fun formatCoordsText(sourcePath: String, photos: List<CoordinateOutput>): String {
        if (photos.isEmpty()) return "$sourcePath: no photos detected"
        val lines = mutableListOf("$sourcePath:")
        photos.forEachIndexed { index, photo ->
            lines.add("  Photo ${index + 1} (${photo.id}):")
            val labels = listOf("TL", "TR", "BR", "BL")
            photo.corners.forEachIndexed { i, corner ->
                lines.add("    ${labels[i]}: (${corner.x}, ${corner.y})")
            }
        }
        return lines.joinToString("\n")
    }
}

/** A single photo's coordinate output for formatting. */
data class CoordinateOutput(val id: String, val corners: List<PhotoCorner>)
