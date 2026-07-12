package org.kryspetrie.fileimport.infrastructure.adapter

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.util.Locale
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.plugins.jpeg.JPEGImageWriteParam
import kotlinx.coroutines.runBlocking
import org.kryspetrie.fileimport.domain.model.DetectedPhoto
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration
import org.kryspetrie.fileimport.domain.model.ProcessedImage
import org.kryspetrie.fileimport.domain.model.RotationAngle
import org.kryspetrie.fileimport.domain.port.FileSystemPort
import org.kryspetrie.fileimport.domain.port.ImageProcessingPort

/**
 * JVM/AWT implementation of [ImageProcessingPort].
 *
 * All pixel-level operations (crop, rotate, composite, read, write JPEG) are performed using
 * `java.awt.image.BufferedImage`. This adapter converts between [ProcessedImage] and
 * `BufferedImage` at the boundary, keeping the application layer free of AWT imports.
 */
class AwtImageProcessingAdapter(private val fileSystem: FileSystemPort) : ImageProcessingPort {

    // ── Image I/O ──────────────────────────────────────────────────────────

    override fun readImage(path: FilePath): ProcessedImage? {
        return try {
            val file = File(path.path)
            val bufferedImage = ImageIO.read(file) ?: return null
            AwtProcessedImage(bufferedImage)
        } catch (_: Exception) {
            null
        }
    }

    override fun writeJpegImage(image: ProcessedImage, outputPath: FilePath, quality: Float) {
        val bufferedImage = image.toBufferedImage()
        val outputFile = File(outputPath.path)
        outputFile.parentFile?.mkdirs()

        val writer = ImageIO.getImageWritersByFormatName("jpg").next()
        val writeParam =
            JPEGImageWriteParam(Locale.US).apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionQuality = quality
            }
        val fileOs = ImageIO.createImageOutputStream(outputFile)
        fileOs.use {
            writer.output = it
            writer.write(null, IIOImage(bufferedImage, null, null), writeParam)
        }
        writer.dispose()
    }

    // ── Transformations ──────────────────────────────────────────────────

    override fun cropAxisAligned(
        sourceImage: ProcessedImage,
        photo: DetectedPhoto,
    ): ProcessedImage {
        val src = sourceImage.toBufferedImage()
        val bounds = photo.getBounds()

        val cropX = bounds.minX.coerceIn(0, (src.width - 1).coerceAtLeast(0))
        val cropY = bounds.minY.coerceIn(0, (src.height - 1).coerceAtLeast(0))
        val cropWidth = bounds.getWidth().coerceIn(1, (src.width - cropX).coerceAtLeast(1))
        val cropHeight = bounds.getHeight().coerceIn(1, (src.height - cropY).coerceAtLeast(1))

        val cropped =
            try {
                src.getSubimage(cropX, cropY, cropWidth, cropHeight)
            } catch (_: Exception) {
                // Fallback to manual copy if getSubimage fails
                val fallback = BufferedImage(cropWidth, cropHeight, BufferedImage.TYPE_INT_RGB)
                val g = fallback.createGraphics()
                g.drawImage(
                    src.getSubimage(
                        cropX.coerceAtLeast(0),
                        cropY.coerceAtLeast(0),
                        cropWidth.coerceAtMost(src.width - cropX),
                        cropHeight.coerceAtMost(src.height - cropY),
                    ),
                    0,
                    0,
                    null,
                )
                g.dispose()
                fallback
            }

        return AwtProcessedImage(cropped)
    }

    override fun rotateImage(image: ProcessedImage, rotation: RotationAngle): ProcessedImage {
        val src = image.toBufferedImage()
        val radians = rotation.radians

        val cos = kotlin.math.abs(kotlin.math.cos(radians))
        val sin = kotlin.math.abs(kotlin.math.sin(radians))

        val newWidth: Int
        val newHeight: Int

        when (rotation) {
            RotationAngle.CW_90,
            RotationAngle.CCW_90 -> {
                newWidth = src.height
                newHeight = src.width
            }
            else -> {
                newWidth = (src.width * cos + src.height * sin).toInt()
                newHeight = (src.width * sin + src.height * cos).toInt()
            }
        }

        val rotated =
            BufferedImage(
                newWidth.coerceAtLeast(1),
                newHeight.coerceAtLeast(1),
                BufferedImage.TYPE_INT_RGB,
            )

        val graphics = rotated.createGraphics()
        graphics.background = Color.BLACK

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
                graphics.translate(-src.width / 2.0, -src.height / 2.0)
            }
            RotationAngle.NONE -> {
                // No rotation
            }
        }

        graphics.drawImage(src, 0, 0, null)
        graphics.dispose()

        return AwtProcessedImage(rotated)
    }

    // ── Composite ─────────────────────────────────────────────────────────

    override fun compositeBackImage(
        frontImage: ProcessedImage,
        config: PhotoScanConfiguration,
    ): ProcessedImage {
        val preparedBack = prepareBackImage(config) ?: return frontImage
        val front = frontImage.toBufferedImage()
        val back = preparedBack.toBufferedImage()

        // Scale back image to match front image width
        val targetWidth = front.width
        val scale = targetWidth.toFloat() / back.width.toFloat()
        val targetHeight = (back.height * scale).toInt()

        val scaledBack = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB)
        val g2d = scaledBack.createGraphics()
        g2d.drawImage(back, 0, 0, targetWidth, targetHeight, null)
        g2d.dispose()

        // Stack front and back vertically with a 2px separator
        val separatorHeight = 2
        val compositeWidth = front.width
        val compositeHeight = front.height + separatorHeight + scaledBack.height

        val composite = BufferedImage(compositeWidth, compositeHeight, BufferedImage.TYPE_INT_RGB)
        val g = composite.createGraphics()
        // Draw front image at top
        g.drawImage(front, 0, 0, null)
        // Draw separator line
        g.color = Color.LIGHT_GRAY
        g.fillRect(0, front.height, compositeWidth, separatorHeight)
        // Draw back image below
        g.drawImage(scaledBack, 0, front.height + separatorHeight, null)
        g.dispose()

        return AwtProcessedImage(composite)
    }

    override fun prepareBackImage(config: PhotoScanConfiguration): ProcessedImage? {
        val sourcePath = config.backImageSourcePath ?: return null
        val filePath = FilePath(sourcePath)
        if (!runBlocking { fileSystem.exists(filePath) }) return null

        val backImage = readImage(filePath) ?: return null
        val backBuffered = backImage.toBufferedImage()

        // Apply rotation first (crop coordinates are in rotated-image space)
        val rotatedBack =
            when (config.backCropRotation) {
                90 -> rotateImage(backImage, RotationAngle.CW_90)
                180 -> rotateImage(backImage, RotationAngle.CW_180)
                270 -> rotateImage(backImage, RotationAngle.CCW_90)
                else -> backImage
            }
        val rotatedBuffered = rotatedBack.toBufferedImage()

        // Apply crop in rotated-image space if normalized crop coordinates are provided
        return if (config.backCropNormalized != null && config.backCropNormalized.size == 4) {
            val cropNorm = config.backCropNormalized
            val left = cropNorm[0]
            val top = cropNorm[1]
            val right = cropNorm[2]
            val bottom = cropNorm[3]
            val cropX = (left * rotatedBuffered.width).toInt().coerceIn(0, rotatedBuffered.width)
            val cropY = (top * rotatedBuffered.height).toInt().coerceIn(0, rotatedBuffered.height)
            val cropW =
                ((right - left) * rotatedBuffered.width)
                    .toInt()
                    .coerceIn(1, rotatedBuffered.width - cropX)
            val cropH =
                ((bottom - top) * rotatedBuffered.height)
                    .toInt()
                    .coerceIn(1, rotatedBuffered.height - cropY)
            AwtProcessedImage(rotatedBuffered.getSubimage(cropX, cropY, cropW, cropH))
        } else {
            rotatedBack
        }
    }
}
