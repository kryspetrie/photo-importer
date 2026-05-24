package org.kryspetrie.fileimport.domain.model

/**
 * Sidecar file extensions that should be imported with media files.
 *
 * Sidecar files contain additional data associated with media files:
 * - Edit instructions (.xmp, .pp3)
 * - Thumbnails (.thm)
 * - Proxy files (.lrv)
 * - iOS edit metadata (.aae)
 *
 * @see ImportConfiguration.importSidecars
 */
object SidecarExtensions {
    val extensions = setOf("xmp", "thm", "lrv", "aae", "pp3", "dop", "cos", "nks")

    /** Check if a file extension is a known sidecar type. */
    fun isSidecar(extension: String): Boolean = extensions.contains(extension.lowercase())
}
