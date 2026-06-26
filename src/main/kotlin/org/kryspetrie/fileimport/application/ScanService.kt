package org.kryspetrie.fileimport.application

import kotlinx.coroutines.runBlocking
import org.kryspetrie.fileimport.application.export.FilenameResolver
import org.kryspetrie.fileimport.domain.model.DetectedPhoto
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration
import org.kryspetrie.fileimport.domain.model.ProcessedImage
import org.kryspetrie.fileimport.domain.port.FileSystemPort
import org.kryspetrie.fileimport.domain.port.ImageProcessingPort
import org.kryspetrie.fileimport.domain.port.PhotoScanDetectorPort

/**
 * Orchestrates photo scan operations.
 *
 * Detects photos within a scanned image, extracts individual photos via perspective correction, and
 * exports them to a destination folder. Detection uses a hybrid approach combining edge-based
 * classical CV (contour tracing + Douglas-Peucker simplification) with ML-based keypoint refinement
 * for precise corners. Domain constraints (max 4 photos, similar dimensions, near-rectangular
 * corners) are applied to filter false positives.
 *
 * All file operations use [FilePath] via [FileSystemPort], keeping this service free of
 * `java.io.File` imports.
 *
 * @param photoDetector Port for detecting photo regions in scanned images
 * @param fileSystem Port for file system operations
 * @param imageProcessing Port for image I/O and transformation operations
 */
class ScanService(
    private val photoDetector: PhotoScanDetectorPort,
    private val fileSystem: FileSystemPort,
    private val imageProcessing: ImageProcessingPort,
) {

    /**
     * Detects photos within a scanned image.
     *
     * @param filePath Path to the scanned image file
     * @param expectedCount Optional hint for the expected number of photos. Used by the classical
     *   CV detector to tune sensitivity (splitting/merging regions if count doesn't match).
     * @return List of detected photos with corner coordinates, ordered TL→TR→BR→BL.
     */
    fun detectPhotos(filePath: String, expectedCount: Int? = null): List<DetectedPhoto> {
        val path = FilePath(filePath)
        if (!runBlocking { fileSystem.exists(path) }) {
            return emptyList()
        }
        return try {
            val image = imageProcessing.readImage(path) ?: return emptyList()
            photoDetector.detectPhotos(image)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Extracts a detected photo from a scanned image via perspective correction.
     *
     * Applies a perspective warp so the quadrilateral region defined by [detectedPhoto]'s corners
     * becomes a flat rectangle.
     *
     * @param scannedImage The full scanned image containing the photo
     * @param detectedPhoto The detected photo region with corner coordinates
     * @return The perspective-corrected photo as a new image
     */
    fun extractPhoto(
        scannedImage: ProcessedImage,
        detectedPhoto: DetectedPhoto,
    ): ProcessedImage {
        return imageProcessing.cropAxisAligned(scannedImage, detectedPhoto)
    }

    /**
     * Exports a photo image to the destination path with automatic filename incrementing.
     *
     * @param photoImage The extracted photo image
     * @param destinationPath Destination folder
     * @param originalFile Original scanned image file (used for base name and extension)
     * @param photoIndex Index of this photo within the scan (for filename suffix)
     * @param configuration Export configuration
     * @return Absolute path to the exported file
     */
    fun exportPhoto(
        photoImage: ProcessedImage,
        destinationPath: String,
        originalFile: FilePath,
        photoIndex: Int,
        configuration: PhotoScanConfiguration,
    ): String {
        val destDir = FilePath(destinationPath)
        runBlocking { fileSystem.mkdirs(destDir) }

        val baseName = fileSystem.nameWithoutExtension(originalFile)
        val extension = fileSystem.extension(originalFile)
        val fileName = if (photoIndex <= 1) "$baseName.$extension" else "${baseName}_$photoIndex.$extension"

        val resolvedPath = runBlocking {
            FilenameResolver.resolveFilenameConflict(fileSystem, destDir, fileName)
        }

        imageProcessing.writeJpegImage(photoImage, FilePath(resolvedPath))

        return resolvedPath
    }
}