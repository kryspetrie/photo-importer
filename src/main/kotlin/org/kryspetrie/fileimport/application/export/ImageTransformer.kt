package org.kryspetrie.fileimport.application.export

import java.awt.image.BufferedImage
import org.kryspetrie.fileimport.domain.model.DetectedPhoto
import org.kryspetrie.fileimport.domain.model.RotationAngle

/**
 * Pure image transformation functions for photo export: axis-aligned cropping and rotation.
 *
 * These are stateless BufferedImage ↔ BufferedImage operations with no I/O or metadata concerns.
 */
object ImageTransformer {

    /** Crops an image using axis-aligned bounding box (when perspective correction is disabled). */
    fun cropAxisAligned(sourceImage: BufferedImage, photo: DetectedPhoto): BufferedImage {
        val bounds = photo.getBounds()
        val cropX = bounds.minX.coerceIn(0, (sourceImage.width - 1).coerceAtLeast(0))
        val cropY = bounds.minY.coerceIn(0, (sourceImage.height - 1).coerceAtLeast(0))
        val cropWidth = bounds.getWidth().coerceIn(1, (sourceImage.width - cropX).coerceAtLeast(1))
        val cropHeight =
            bounds.getHeight().coerceIn(1, (sourceImage.height - cropY).coerceAtLeast(1))

        return try {
            sourceImage.getSubimage(cropX, cropY, cropWidth, cropHeight)
        } catch (_: Exception) {
            // Fallback to manual copy if getSubimage fails
            val cropped = BufferedImage(cropWidth, cropHeight, BufferedImage.TYPE_INT_RGB)
            val g = cropped.createGraphics()
            g.drawImage(
                sourceImage.getSubimage(
                    cropX.coerceAtLeast(0),
                    cropY.coerceAtLeast(0),
                    cropWidth.coerceAtMost(sourceImage.width - cropX),
                    cropHeight.coerceAtMost(sourceImage.height - cropY),
                ),
                0,
                0,
                null,
            )
            g.dispose()
            cropped
        }
    }

    /** Rotates an image by the specified rotation angle. */
    fun rotateImage(image: BufferedImage, rotation: RotationAngle): BufferedImage {
        val radians = rotation.radians

        val cos = kotlin.math.abs(kotlin.math.cos(radians))
        val sin = kotlin.math.abs(kotlin.math.sin(radians))

        val newWidth: Int
        val newHeight: Int

        when (rotation) {
            RotationAngle.CW_90,
            RotationAngle.CCW_90 -> {
                newWidth = image.height
                newHeight = image.width
            }
            else -> {
                newWidth = (image.width * cos + image.height * sin).toInt()
                newHeight = (image.width * sin + image.height * cos).toInt()
            }
        }

        val rotated =
            BufferedImage(
                newWidth.coerceAtLeast(1),
                newHeight.coerceAtLeast(1),
                BufferedImage.TYPE_INT_RGB,
            )

        val graphics = rotated.createGraphics()
        graphics.background = java.awt.Color.BLACK

        when (rotation) {
            RotationAngle.CW_90 -> {
                graphics.translate(newWidth, 0)
                graphics.rotate(Math.PI / 2)
            }
            RotationAngle.CCW_90 -> {
                graphics.translate(0, newHeight)
                graphics.rotate(-Math.PI / 2)
            }
            RotationAngle.CW_180 -> {
                graphics.translate(newWidth / 2.0, newHeight / 2.0)
                graphics.rotate(Math.PI)
                graphics.translate(-image.width / 2.0, -image.height / 2.0)
            }
            RotationAngle.NONE -> {
                // No rotation
            }
        }

        graphics.drawImage(image, 0, 0, null)
        graphics.dispose()

        return rotated
    }
}
