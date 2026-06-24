package org.kryspetrie.fileimport.application.export

import java.awt.image.BufferedImage
import java.io.File
import java.util.Locale
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.plugins.jpeg.JPEGImageWriteParam

/**
 * Writes [BufferedImage]s to JPEG files with configurable quality.
 *
 * This is a pure I/O utility — it writes pixel data and nothing else. Metadata layering (EXIF, IPTC,
 * XMP) is handled by [MetadataWritingService].
 *
 * @property jpegQuality JPEG compression quality (0.0 – 1.0). Defaults to 0.95.
 */
class JpegImageWriter(var jpegQuality: Float = 0.95f) {

    /**
     * Writes [image] to [outputFile] as a JPEG with the configured [jpegQuality].
     *
     * Creates parent directories if they don't exist. The caller is responsible for any metadata
     * overlay after the file is written.
     */
    fun writeJpegImage(image: BufferedImage, outputFile: File, quality: Float = jpegQuality) {
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
            writer.write(null, IIOImage(image, null, null), writeParam)
        }
        writer.dispose()
    }
}