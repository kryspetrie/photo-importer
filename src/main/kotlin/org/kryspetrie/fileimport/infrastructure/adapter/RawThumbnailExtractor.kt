package org.kryspetrie.fileimport.infrastructure.adapter

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
      extractPreviewFromJpegSegment(file)
    } catch (_: Exception) {
      null
    }
  }

  /**
   * Scans a RAW file for an embedded JPEG by finding SOI (0xFFD8) and EOI (0xFFD9) markers. Most
   * RAW formats embed a full-resolution or half-resolution JPEG preview after the format-specific
   * header. We skip the first 1KB to avoid the header, then find the largest embedded JPEG (which
   * is typically the preview, not the tiny EXIF thumb).
   */
  private fun extractPreviewFromJpegSegment(file: File): BufferedImage? {
    if (file.length() < 2048) return null
    val maxScan = minOf(file.length(), 20_000_000L) // Don't scan more than 20MB
    val bytes = ByteArray(maxScan.toInt())
    RandomAccessFile(file, "r").use { raf -> raf.readFully(bytes, 0, bytes.size) }

    // Find all JPEG SOI markers after the initial header
    val searchStart = minOf(bytes.size, 1024)
    var bestImage: BufferedImage? = null
    var bestSize = 0L

    var pos = searchStart
    while (pos < bytes.size - 1) {
      if (bytes[pos] == 0xFF.toByte() && bytes[pos + 1] == 0xD8.toByte()) {
        // Found SOI — look for matching EOI
        val jpegStart = pos
        var jpegEnd = -1
        for (i in jpegStart + 2 until bytes.size - 1) {
          if (bytes[i] == 0xFF.toByte() && bytes[i + 1] == 0xD9.toByte()) {
            jpegEnd = i + 2
            // Keep scanning for a later EOI (the JPEG may contain thumbnails with their own
            // markers)
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
