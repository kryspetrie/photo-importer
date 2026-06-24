package org.kryspetrie.fileimport.application.export

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration
import org.kryspetrie.fileimport.domain.model.RotationAngle

/**
 * Handles loading, cropping, rotating, and compositing back-of-photo images.
 *
 * Two modes are supported via [PhotoScanConfiguration.backImageMode]:
 * - `"combine"`: Stitches the back crop below the front photo with a 2px separator.
 * - `"append_back"`: The back crop is exported as a separate `_back.jpg` file (orchestrated by
 *  the caller).
 *
 * @see ImageTransformer for the rotation operations used by this service.
 */
class BackImageService {

    /**
     * Prepares the back-of-photo image: loads it from disk, applies an optional normalized crop, and
     * rotates it according to [PhotoScanConfiguration.backCropRotation].
     *
     * Returns `null` if the back image cannot be loaded or is not configured.
     */
    fun prepareBackImage(config: PhotoScanConfiguration): BufferedImage? {
        val sourcePath = config.backImageSourcePath ?: return null
        val sourceFile = File(sourcePath)
        if (!sourceFile.exists()) return null

        val backImage =
            try {
                ImageIO.read(sourceFile) ?: return null
            } catch (_: Exception) {
                return null
            }

        // Apply crop if normalized crop coordinates are provided
        val croppedBack =
            if (config.backCropNormalized != null && config.backCropNormalized.size == 4) {
                val cropNorm = config.backCropNormalized
                val left = cropNorm[0]
                val top = cropNorm[1]
                val right = cropNorm[2]
                val bottom = cropNorm[3]
                val cropX = (left * backImage.width).toInt().coerceIn(0, backImage.width)
                val cropY = (top * backImage.height).toInt().coerceIn(0, backImage.height)
                val cropW =
                    ((right - left) * backImage.width).toInt().coerceIn(1, backImage.width - cropX)
                val cropH =
                    ((bottom - top) * backImage.height)
                        .toInt()
                        .coerceIn(1, backImage.height - cropY)
                backImage.getSubimage(cropX, cropY, cropW, cropH)
            } else {
                backImage
            }

        // Apply rotation (0, 90, 180, 270 degrees)
        return when (config.backCropRotation) {
            90 -> ImageTransformer.rotateImage(croppedBack, RotationAngle.CW_90)
            180 -> ImageTransformer.rotateImage(croppedBack, RotationAngle.CW_180)
            270 -> ImageTransformer.rotateImage(croppedBack, RotationAngle.CCW_90)
            else -> croppedBack
        }
    }

    /**
     * Composites a back-of-photo image below the front (extracted) photo.
     *
     * The back image is loaded from [PhotoScanConfiguration.backImageSourcePath], optionally cropped
     * using [PhotoScanConfiguration.backCropNormalized] coordinates, and optionally rotated by
     * [PhotoScanConfiguration.backCropRotation]. The front and back images are stacked vertically
     * with the back image scaled to match the front image width, separated by a 2px grey line.
     *
     * Returns [frontImage] unchanged if no back image is available.
     */
    fun compositeBackImage(
        frontImage: BufferedImage,
        config: PhotoScanConfiguration,
    ): BufferedImage {
        val preparedBack = prepareBackImage(config) ?: return frontImage

        // Scale back image to match front image width
        val targetWidth = frontImage.width
        val scale = targetWidth.toFloat() / preparedBack.width.toFloat()
        val targetHeight = (preparedBack.height * scale).toInt()

        val scaledBack =
            BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB)
        val g2d = scaledBack.createGraphics()
        g2d.drawImage(preparedBack, 0, 0, targetWidth, targetHeight, null)
        g2d.dispose()

        // Stack front and back vertically with a 2px separator
        val separatorHeight = 2
        val compositeWidth = frontImage.width
        val compositeHeight = frontImage.height + separatorHeight + scaledBack.height

        val composite =
            BufferedImage(compositeWidth, compositeHeight, BufferedImage.TYPE_INT_RGB)
        val g = composite.createGraphics()
        // Draw front image at top
        g.drawImage(frontImage, 0, 0, null)
        // Draw separator line
        g.color = Color.LIGHT_GRAY
        g.fillRect(0, frontImage.height, compositeWidth, separatorHeight)
        // Draw back image below
        g.drawImage(scaledBack, 0, frontImage.height + separatorHeight, null)
        g.dispose()

        return composite
    }
}