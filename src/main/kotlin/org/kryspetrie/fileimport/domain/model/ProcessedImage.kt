package org.kryspetrie.fileimport.domain.model

/**
 * Abstraction over a raster image in the domain layer.
 *
 * Removes direct `java.awt.image.BufferedImage` dependencies from domain models and ports, keeping
 * the domain layer free of AWT image infrastructure. Infrastructure and application layers convert
 * to/from `BufferedImage` at boundaries via the `toBufferedImage()` extension function defined in
 * the infrastructure layer.
 *
 * ## Usage
 *
 * ```kotlin
 * // In domain ports:
 * fun detectPhotos(image: ProcessedImage): List<DetectedPhoto>
 *
 * // Creating from a BufferedImage (infrastructure layer):
 * val processed = image.toProcessedImage()
 *
 * // Converting back (application/infrastructure/UI):
 * val awtImage = processed.toBufferedImage()
 * ```
 *
 * @see AwtProcessedImage The default implementation wrapping a `BufferedImage`
 */
interface ProcessedImage {
    /** Image width in pixels. */
    val width: Int

    /** Image height in pixels. */
    val height: Int
}
