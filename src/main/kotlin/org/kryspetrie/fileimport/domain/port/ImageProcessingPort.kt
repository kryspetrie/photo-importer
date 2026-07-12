package org.kryspetrie.fileimport.domain.port

import org.kryspetrie.fileimport.domain.model.DetectedPhoto
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration
import org.kryspetrie.fileimport.domain.model.ProcessedImage
import org.kryspetrie.fileimport.domain.model.RotationAngle

/**
 * Port interface for platform-dependent image processing operations.
 *
 * Abstracts AWT/Swing-dependent operations (crop, rotate, composite, read, write) so that the
 * application layer never touches `java.awt.image.BufferedImage` directly. Infrastructure adapters
 * implement this port using JVM image libraries.
 *
 * ## Usage
 *
 * ```kotlin
 * val processor: ImageProcessingPort = koinInject()
 * val image: ProcessedImage = processor.readImage(FilePath("/path/to/image.jpg"))
 * val cropped = processor.cropAxisAligned(image, detectedPhoto)
 * val rotated = processor.rotateImage(cropped, RotationAngle.CW_90)
 * processor.writeJpegImage(rotated, FilePath("/path/to/output.jpg"), quality = 0.95f)
 * ```
 *
 * @see ProcessedImage The domain image abstraction
 * @see AwtImageProcessingAdapter The default JVM implementation
 */
interface ImageProcessingPort {

    // ── Image I/O ──────────────────────────────────────────────────────────

    /**
     * Reads an image from the given file path.
     *
     * @param path Path to the image file
     * @return The image, or `null` if the file cannot be read or is not a valid image
     */
    fun readImage(path: FilePath): ProcessedImage?

    /**
     * Writes an image to a JPEG file with the specified quality.
     *
     * Creates parent directories if they don't exist.
     *
     * @param image The image to write
     * @param outputPath Destination file path
     * @param quality JPEG compression quality (0.0 – 1.0)
     */
    fun writeJpegImage(image: ProcessedImage, outputPath: FilePath, quality: Float = 0.95f)

    // ── Transformations ──────────────────────────────────────────────────

    /**
     * Crops an image to the axis-aligned bounding box of a detected photo.
     *
     * Used when perspective correction is disabled (simple rectangular crop).
     *
     * @param sourceImage The source image to crop
     * @param photo The detected photo defining the crop region
     * @return The cropped image
     */
    fun cropAxisAligned(sourceImage: ProcessedImage, photo: DetectedPhoto): ProcessedImage

    /**
     * Rotates an image by the specified angle.
     *
     * @param image The image to rotate
     * @param rotation The rotation angle
     * @return The rotated image
     */
    fun rotateImage(image: ProcessedImage, rotation: RotationAngle): ProcessedImage

    // ── Composite ─────────────────────────────────────────────────────────

    /**
     * Composites a back-of-photo image below the front (extracted) photo.
     *
     * The back image is loaded from [PhotoScanConfiguration.backImageSourcePath], optionally
     * cropped using [PhotoScanConfiguration.backCropNormalized], and optionally rotated by
     * [PhotoScanConfiguration.backCropRotation]. The images are stacked vertically with a 2px grey
     * separator, back image scaled to match front image width.
     *
     * Returns [frontImage] unchanged if no back image is configured or if loading fails.
     *
     * @param frontImage The front (extracted) photo
     * @param config Configuration containing back image path, crop, and rotation settings
     * @return The composited image, or [frontImage] if no back image is available
     */
    fun compositeBackImage(
        frontImage: ProcessedImage,
        config: PhotoScanConfiguration,
    ): ProcessedImage

    /**
     * Prepares the back-of-photo image: loads, crops, and rotates it.
     *
     * If [maxWidth] and [maxHeight] are provided, the back image is scaled down proportionally so
     * that it never exceeds the front image in either dimension. If both are null, no scaling
     * constraint is applied.
     *
     * Returns `null` if no back image is configured or if loading fails.
     *
     * @param config Configuration containing back image path, crop, and rotation settings
     * @param maxWidth Maximum width (front image width) to constrain the back image, or null for no
     *   constraint
     * @param maxHeight Maximum height (front image height) to constrain the back image, or null for
     *   no constraint
     * @return The prepared back image, or `null`
     */
    fun prepareBackImage(
        config: PhotoScanConfiguration,
        maxWidth: Int? = null,
        maxHeight: Int? = null,
    ): ProcessedImage?
}
