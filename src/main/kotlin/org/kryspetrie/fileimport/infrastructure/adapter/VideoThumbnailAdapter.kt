package org.kryspetrie.fileimport.infrastructure.adapter

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracts video thumbnails using FFmpeg (if available on the system). Falls back gracefully if
 * FFmpeg is not installed.
 */
object VideoThumbnailAdapter {
  private var ffmpegAvailable: Boolean? = null
  private val cacheDir: File by lazy {
    File(System.getProperty("user.home"), ".petrie-importer/video-thumbs").also { it.mkdirs() }
  }

  fun isFfmpegAvailable(): Boolean {
    if (ffmpegAvailable != null) return ffmpegAvailable!!
    ffmpegAvailable =
        try {
          val process = ProcessBuilder("ffmpeg", "-version").redirectErrorStream(true).start()
          val exitCode = process.waitFor()
          process.inputStream.close()
          exitCode == 0
        } catch (_: Exception) {
          false
        }
    return ffmpegAvailable!!
  }

  suspend fun extractThumbnail(videoFile: File, maxPx: Int): BufferedImage? =
      withContext(Dispatchers.IO) {
        if (!isFfmpegAvailable()) return@withContext null
        if (!videoFile.exists()) return@withContext null

        val cacheKey = "${videoFile.absolutePath.hashCode()}_${videoFile.lastModified()}_$maxPx"
        val cachedFile = File(cacheDir, "$cacheKey.jpg")

        if (cachedFile.exists()) {
          return@withContext try {
            ImageIO.read(cachedFile)
          } catch (_: Exception) {
            null
          }
        }

        try {
          val process =
              ProcessBuilder(
                      "ffmpeg",
                      "-i",
                      videoFile.absolutePath,
                      "-ss",
                      "00:00:01", // seek to 1 second
                      "-vframes",
                      "1", // extract 1 frame
                      "-vf",
                      "scale='min($maxPx,iw)':'min($maxPx,ih)':force_original_aspect_ratio=decrease",
                      "-q:v",
                      "3", // quality
                      "-y", // overwrite
                      cachedFile.absolutePath)
                  .redirectErrorStream(true)
                  .start()

          // Read process output to prevent hanging
          process.inputStream.bufferedReader().readText()
          val exitCode = process.waitFor()

          if (exitCode == 0 && cachedFile.exists()) {
            ImageIO.read(cachedFile)
          } else {
            // Fallback: try frame at 0 seconds (very short videos)
            val fallback =
                ProcessBuilder(
                        "ffmpeg",
                        "-i",
                        videoFile.absolutePath,
                        "-vframes",
                        "1",
                        "-vf",
                        "scale='min($maxPx,iw)':'min($maxPx,ih)':force_original_aspect_ratio=decrease",
                        "-q:v",
                        "3",
                        "-y",
                        cachedFile.absolutePath)
                    .redirectErrorStream(true)
                    .start()
            fallback.inputStream.bufferedReader().readText()
            fallback.waitFor()

            if (cachedFile.exists()) ImageIO.read(cachedFile) else null
          }
        } catch (_: Exception) {
          null
        }
      }

  fun clearCache() {
    cacheDir.listFiles()?.forEach { it.delete() }
  }

  fun getCacheSize(): Long = cacheDir.listFiles()?.sumOf { it.length() } ?: 0L
}
