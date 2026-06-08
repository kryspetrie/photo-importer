package org.kryspetrie.fileimport.infrastructure.adapter

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.Metadata
import com.drew.metadata.exif.ExifThumbnailDirectory
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.io.RandomAccessFile
import javax.imageio.ImageIO
import org.kryspetrie.fileimport.domain.model.ImageFileType

object RawThumbnailExtractor {

    fun extractEmbeddedThumbnail(file: File): BufferedImage? {
        val fileType = ImageFileType.fromExtension(file.extension)
        if (!fileType.isRaw) return null
        return try {
            extractViaMetadataExtractor(file) ?: extractPreviewFromJpegSegmentFallback(file)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Primary approach: use metadata-extractor's ExifThumbnailDirectory to get the thumbnail offset
     * and length from EXIF metadata, then read the embedded JPEG directly.
     *
     * This is far more efficient than scanning 20MB of bytes for JPEG markers. metadata-extractor
     * already parses the file structure and knows where the thumbnail lives.
     */
    private fun extractViaMetadataExtractor(file: File): BufferedImage? {
        val metadata: Metadata
        try {
            metadata = ImageMetadataReader.readMetadata(file)
        } catch (_: Exception) {
            return null
        }

        val thumbnailDir =
            metadata.getFirstDirectoryOfType(ExifThumbnailDirectory::class.java) ?: return null

        val offset =
            thumbnailDir.getInteger(ExifThumbnailDirectory.TAG_THUMBNAIL_OFFSET) ?: return null
        val length =
            thumbnailDir.getInteger(ExifThumbnailDirectory.TAG_THUMBNAIL_LENGTH) ?: return null

        if (offset <= 0 || length <= 0 || length > 20_000_000) return null

        return try {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(offset.toLong())
                val bytes = ByteArray(length)
                raf.readFully(bytes)
                ImageIO.read(ByteArrayInputStream(bytes))
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Fallback: scans a RAW file for an embedded JPEG by finding SOI (0xFFD8) and EOI (0xFFD9)
     * markers.
     *
     * Used when metadata-extractor doesn't find an ExifThumbnailDirectory (some RAW formats embed
     * previews in non-standard locations). This scans up to 20MB but is slower than the
     * metadata-extractor approach.
     */
    @Suppress("NestedBlockDepth")
    private fun extractPreviewFromJpegSegmentFallback(file: File): BufferedImage? {
        if (file.length() < 2048) return null
        val maxScan = minOf(file.length(), 20_000_000L)
        val bytes = ByteArray(maxScan.toInt())
        RandomAccessFile(file, "r").use { raf -> raf.readFully(bytes, 0, bytes.size) }

        val searchStart = minOf(bytes.size, 1024)
        var bestImage: BufferedImage? = null
        var bestSize = 0L

        var pos = searchStart
        while (pos < bytes.size - 1) {
            if (bytes[pos] == 0xFF.toByte() && bytes[pos + 1] == 0xD8.toByte()) {
                val jpegStart = pos
                var jpegEnd = -1
                for (i in jpegStart + 2 until bytes.size - 1) {
                    if (bytes[i] == 0xFF.toByte() && bytes[i + 1] == 0xD9.toByte()) {
                        jpegEnd = i + 2
                    }
                }
                if (jpegEnd > jpegStart && (jpegEnd - jpegStart) > bestSize) {
                    try {
                        val jpegBytes = bytes.copyOfRange(jpegStart, jpegEnd)
                        val img = ImageIO.read(ByteArrayInputStream(jpegBytes))
                        if (img != null) {
                            bestImage?.flush()
                            bestImage = img
                            bestSize = (jpegEnd - jpegStart).toLong()
                        }
                    } catch (_: Exception) {}
                }
                pos = if (jpegEnd > 0) jpegEnd else pos + 2
            } else {
                pos++
            }
        }
        return bestImage
    }
}
