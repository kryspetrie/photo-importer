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
import org.kryspetrie.fileimport.domain.model.PhotoCorner
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
        val front = frontImage.toBufferedImage()
        val preparedBack =
            prepareBackImage(config, maxWidth = front.width, maxHeight = front.height)
                ?: return frontImage
        val back = preparedBack.toBufferedImage()

        // Stack front and back vertically with a 2px separator
        val separatorHeight = 2
        val compositeWidth = front.width
        val compositeHeight = front.height + separatorHeight + back.height

        val composite = BufferedImage(compositeWidth, compositeHeight, BufferedImage.TYPE_INT_RGB)
        val g = composite.createGraphics()
        // Draw front image at top
        g.drawImage(front, 0, 0, null)
        // Draw separator line
        g.color = Color.LIGHT_GRAY
        g.fillRect(0, front.height, compositeWidth, separatorHeight)
        // Draw back image below
        g.drawImage(back, 0, front.height + separatorHeight, null)
        g.dispose()

        return AwtProcessedImage(composite)
    }

    /**
     * Scales [backImage] proportionally so that it never exceeds [maxWidth] or [maxHeight] in
     * either dimension. Only scales *down* — if the image already fits, it is returned unchanged.
     *
     * The constraint is:
     * - scaled width ≤ maxWidth
     * - scaled height ≤ maxHeight
     * - scale ≤ 1.0 (never scale up)
     * - Among all scales satisfying the above, choose the largest (to preserve detail).
     */
    private fun scaleToFitFront(
        backImage: BufferedImage,
        maxWidth: Int,
        maxHeight: Int,
    ): BufferedImage {
        val widthScale = maxWidth.toFloat() / backImage.width.toFloat()
        val heightScale = maxHeight.toFloat() / backImage.height.toFloat()
        val scale = minOf(widthScale, heightScale, 1.0f)

        val targetWidth = (backImage.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (backImage.height * scale).toInt().coerceAtLeast(1)

        val scaled = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB)
        val g2d = scaled.createGraphics()
        g2d.drawImage(backImage, 0, 0, targetWidth, targetHeight, null)
        g2d.dispose()
        return scaled
    }

    override fun prepareBackImage(
        config: PhotoScanConfiguration,
        maxWidth: Int?,
        maxHeight: Int?,
    ): ProcessedImage? {
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
        val prepared: BufferedImage =
            if (config.backCropNormalized != null && config.backCropNormalized.size == 8) {
                // 8 values = 4-point perspective quad: [tl_x, tl_y, tr_x, tr_y, br_x, br_y, bl_x,
                // bl_y]
                val n = config.backCropNormalized
                val detectedPhoto =
                    DetectedPhoto(
                        topLeft =
                            PhotoCorner(
                                n[0] * rotatedBuffered.width,
                                n[1] * rotatedBuffered.height,
                            ),
                        topRight =
                            PhotoCorner(
                                n[2] * rotatedBuffered.width,
                                n[3] * rotatedBuffered.height,
                            ),
                        bottomRight =
                            PhotoCorner(
                                n[4] * rotatedBuffered.width,
                                n[5] * rotatedBuffered.height,
                            ),
                        bottomLeft =
                            PhotoCorner(n[6] * rotatedBuffered.width, n[7] * rotatedBuffered.height),
                    )
                val perspectiveService =
                    org.kryspetrie.fileimport.infrastructure.photoscan
                        .PerspectiveCorrectionService()
                perspectiveService.correctPerspective(rotatedBuffered, detectedPhoto)
            } else if (config.backCropNormalized != null && config.backCropNormalized.size == 4) {
                // 4 values = rectangular crop: [left, top, right, bottom]
                val cropNorm = config.backCropNormalized
                val left = cropNorm[0]
                val top = cropNorm[1]
                val right = cropNorm[2]
                val bottom = cropNorm[3]
                val cropX =
                    (left * rotatedBuffered.width).toInt().coerceIn(0, rotatedBuffered.width)
                val cropY =
                    (top * rotatedBuffered.height).toInt().coerceIn(0, rotatedBuffered.height)
                val cropW =
                    ((right - left) * rotatedBuffered.width)
                        .toInt()
                        .coerceIn(1, rotatedBuffered.width - cropX)
                val cropH =
                    ((bottom - top) * rotatedBuffered.height)
                        .toInt()
                        .coerceIn(1, rotatedBuffered.height - cropY)
                rotatedBuffered.getSubimage(cropX, cropY, cropW, cropH)
            } else {
                rotatedBuffered
            }

        // Scale down if the back image would exceed front image dimensions
        val result =
            if (maxWidth != null && maxHeight != null) {
                scaleToFitFront(prepared, maxWidth, maxHeight)
            } else {
                prepared
            }

        return AwtProcessedImage(result)
    }
}
