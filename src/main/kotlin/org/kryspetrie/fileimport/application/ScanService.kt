package org.kryspetrie.fileimport.application

import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.runBlocking
import org.kryspetrie.fileimport.application.export.FilenameResolver
import org.kryspetrie.fileimport.domain.model.DetectedPhoto
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.PhotoCorner
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration
import org.kryspetrie.fileimport.domain.port.FileSystemPort
import org.kryspetrie.fileimport.domain.port.PhotoScanDetectorPort
import org.kryspetrie.fileimport.infrastructure.adapter.toProcessedImage

/**
 * Orchestrates photo scan operations.
 *
 * Detects photos within a scanned image, extracts individual photos via perspective correction, and
 * exports them to a destination folder. Detection uses a hybrid approach combining edge-based
 * classical CV (contour tracing + Douglas-Peucker simplification) with ML-based keypoint refinement
 * for precise corners. Domain constraints (max 4 photos, similar dimensions, near-rectangular
 * corners) are applied to filter false positives.
 *
 * @param photoDetector Port for detecting photo regions in scanned images
 * @param fileSystem Port for file system operations
 */
class ScanService(
    private val photoDetector: PhotoScanDetectorPort,
    private val fileSystem: FileSystemPort,
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
            val bufferedImage = ImageIO.read(path.toFile()) ?: return emptyList()
            photoDetector.detectPhotos(bufferedImage.toProcessedImage())
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
    fun extractPhoto(scannedImage: BufferedImage, detectedPhoto: DetectedPhoto): BufferedImage {
        val bounds = detectedPhoto.getBounds()
        val width = bounds.getWidth()
        val height = bounds.getHeight()

        // Source quad (detected corners)
        val srcQuad =
            listOf(
                detectedPhoto.topLeft,
                detectedPhoto.topRight,
                detectedPhoto.bottomLeft,
                detectedPhoto.bottomRight,
            )

        // Destination rectangle
        val dstRect =
            listOf(
                Corner(0f, 0f),
                Corner(width.toFloat(), 0f),
                Corner(0f, height.toFloat()),
                Corner(width.toFloat(), height.toFloat()),
            )

        // Calculate transform
        val transform = calculatePerspectiveTransform(srcQuad, dstRect)

        // Apply transform
        val outputImage = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val op =
            java.awt.image.AffineTransformOp(
                transform,
                java.awt.image.AffineTransformOp.TYPE_BILINEAR,
            )
        op.filter(scannedImage, outputImage)

        return outputImage
    }

    private fun calculatePerspectiveTransform(
        srcQuad: List<PhotoCorner>,
        dstRect: List<Corner>,
    ): AffineTransform {
        val srcTopWidth = distance(srcQuad[0], srcQuad[1])
        val srcLeftHeight = distance(srcQuad[0], srcQuad[2])
        val dstWidth = dstRect[1].x - dstRect[0].x
        val dstHeight = dstRect[2].y - dstRect[0].y

        val scaleX = dstWidth / srcTopWidth
        val scaleY = dstHeight / srcLeftHeight

        val transform = AffineTransform()
        transform.translate(dstRect[0].x.toDouble(), dstRect[0].y.toDouble())
        transform.scale(scaleX.toDouble(), scaleY.toDouble())

        return transform
    }

    private fun distance(c1: PhotoCorner, c2: PhotoCorner): Float {
        val dx = c2.x - c1.x
        val dy = c2.y - c1.y
        return kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
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
        photoImage: BufferedImage,
        destinationPath: String,
        originalFile: File,
        photoIndex: Int,
        configuration: PhotoScanConfiguration,
    ): String {
        val destDir = FilePath(destinationPath)
        runBlocking { fileSystem.mkdirs(destDir) }

        val baseName = originalFile.nameWithoutExtension
        val extension = originalFile.extension
        val fileName = if (photoIndex <= 1) "$baseName.$extension" else "${baseName}_$photoIndex.$extension"

        val resolvedPath = runBlocking {
            FilenameResolver.resolveFilenameConflict(fileSystem, destDir, fileName)
        }
        val outputFile = File(resolvedPath)

        ImageIO.write(photoImage, extension, outputFile)

        return resolvedPath
    }

    data class Corner(val x: Float, val y: Float)
}