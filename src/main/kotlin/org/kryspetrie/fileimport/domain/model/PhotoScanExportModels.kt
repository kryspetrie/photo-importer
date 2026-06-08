package org.kryspetrie.fileimport.domain.model

/**
 * Export result containing paths and metadata of exported images.
 *
 * Moved from PhotoScanExportService to domain/model to fix hexagonal architecture violation: domain
 * port (PhotoScanExportPort) must not depend on application layer types.
 */
data class PhotoScanExportResult(
    val success: Boolean,
    val exportedFiles: List<PhotoScanExportedFile> = emptyList(),
    val errors: List<String> = emptyList(),
)

/**
 * Result of exporting a single photo.
 *
 * @property success Whether the export succeeded
 * @property destinationPath Path where the photo was exported
 * @property width Width of the exported image in pixels
 * @property height Height of the exported image in pixels
 * @property error Error message if export failed, null otherwise
 */
data class PhotoScanSingleExportResult(
    val success: Boolean,
    val destinationPath: String,
    val width: Int,
    val height: Int,
    val error: String? = null,
)

/**
 * Information about an exported file.
 *
 * @property sourceFile Original source image file
 * @property destinationPath Path where the file was exported
 * @property photoId Unique identifier of the detected photo
 * @property width Width of the exported image in pixels
 * @property height Height of the exported image in pixels
 * @property fileSize Size of the exported file in bytes
 */
data class PhotoScanExportedFile(
    val sourceFile: FilePath,
    val destinationPath: String,
    val photoId: String,
    val width: Int,
    val height: Int,
    val fileSize: Long,
)
