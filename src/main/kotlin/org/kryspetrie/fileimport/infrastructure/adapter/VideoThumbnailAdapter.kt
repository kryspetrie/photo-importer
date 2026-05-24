package org.kryspetrie.fileimport.infrastructure.adapter

import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Extracts video thumbnails using FFmpeg (bundled or system) or pure-Java fallback.
 *
 * Thumbnail extraction strategy (in order):
 * 1. **FFmpeg** (via [Platform.resolveFfmpegPath]): Checks for a bundled binary first, then
 *    platform-specific locations, then system PATH. FFmpeg produces the highest-quality thumbnails
 *    with frame-accurate seeking and proper scaling.
 * 2. **Pure-Java fallback**: Uses `javax.imageio` to attempt reading the video file directly. This
 *    works for a small number of containers (particularly motion JPEG in AVI) but will return
 *    `null` for most modern codecs (H.264, HEVC, VP9). The fallback ensures the UI always renders
 *    something rather than crashing.
 *
 * ## FFmpeg Resolution Order
 * 1. Bundled binary in app data directory or current working directory
 * 2. Platform-specific known locations (Homebrew on macOS, Program Files on Windows)
 * 3. System PATH (`which`/`where`)
 * 4. Pure-Java fallback if FFmpeg is unavailable
 *
 * ## Bundling FFmpeg (Optional)
 *
 * To bundle FFmpeg binaries for each platform, place them alongside the JAR:
 * - macOS: `ffmpeg` (Universal binary: x86_64 + arm64)
 * - Linux: `ffmpeg` (x86_64)
 * - Windows: `ffmpeg.exe`
 *
 * The application will discover them via [Platform.resolveFfmpegPath].
 */
object VideoThumbnailAdapter {
    /** IO dispatcher for coroutine context switching. Override in tests. */
    @Suppress("InjectDispatcher") // Object singleton — dispatcher injected via configurable var
    var ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    private var ffmpegAvailable: Boolean? = null
    private var ffmpegPath: String? = null

    private val cacheDir: File by lazy {
        val dir = File(Platform.cacheDir, "video-thumbs")
        dir.apply { mkdirs() }
    }

    /**
     * Checks whether FFmpeg is available for thumbnail extraction.
     *
     * Caches the result after the first check. Uses [Platform.resolveFfmpegPath] to find the FFmpeg
     * binary in platform-specific locations.
     *
     * @return `true` if FFmpeg can be executed, `false` otherwise
     */
    fun isFfmpegAvailable(): Boolean {
        if (ffmpegAvailable != null) return ffmpegAvailable!!
        val path = Platform.resolveFfmpegPath()
        if (path != null) {
            ffmpegPath = path
            // Verify the binary actually runs
            ffmpegAvailable =
                try {
                    val process = ProcessBuilder(path, "-version").redirectErrorStream(true).start()
                    val exitCode = process.waitFor()
                    process.inputStream.close()
                    exitCode == 0
                } catch (_: Exception) {
                    false
                }
        } else {
            ffmpegAvailable = false
        }
        return ffmpegAvailable!!
    }

    /**
     * Extracts a thumbnail from a video file.
     *
     * Uses FFmpeg if available, falls back to pure-Java extraction via ImageIO. Results are cached
     * to disk to avoid re-extraction on subsequent requests.
     *
     * @param videoFile The video file to extract a thumbnail from
     * @param maxPx Maximum dimension (width or height) of the thumbnail in pixels
     * @return A [BufferedImage] of the thumbnail, or `null` if extraction fails
     */
    suspend fun extractThumbnail(videoFile: File, maxPx: Int): BufferedImage? =
        withContext(ioDispatcher) {
            if (!videoFile.exists()) return@withContext null

            // Try FFmpeg first (highest quality, supports all codecs)
            if (isFfmpegAvailable() && ffmpegPath != null) {
                extractViaFfmpeg(videoFile, maxPx, ffmpegPath!!)?.let {
                    return@withContext it
                }
            }

            // Pure-Java fallback: try reading with ImageIO (works for motion JPEG, some AVI)
            extractViaJavaFallback(videoFile, maxPx)
        }

    /**
     * Extracts a thumbnail using FFmpeg.
     *
     * Seeks to 1 second into the video and extracts a single frame, scaled to [maxPx]. Falls back
     * to the first frame (0 seconds) for very short videos. Results are cached to [cacheDir] on
     * disk.
     */
    @Suppress("ReturnCount")
    private fun extractViaFfmpeg(
        videoFile: File,
        maxPx: Int,
        ffmpegBinary: String,
    ): BufferedImage? {
        val cacheKey =
            "${videoFile.absolutePath}_${videoFile.lastModified()}_$maxPx"
                .replace('/', '_')
                .replace('\\', '_')
                .replace(':', '_')
                .replace(' ', '_')
        val cachedFile = File(cacheDir, "$cacheKey.jpg")

        // Return cached thumbnail if available
        if (cachedFile.exists()) {
            return try {
                ImageIO.read(cachedFile)
            } catch (_: Exception) {
                null
            }
        }

        val scaleFilter =
            "scale='min($maxPx,iw)':'min($maxPx,ih)':force_original_aspect_ratio=decrease"

        // Try seeking to 1 second first (avoids black frames at start)
        val result =
            runFfmpeg(
                listOf(
                    ffmpegBinary,
                    "-ss",
                    "00:00:01",
                    "-i",
                    videoFile.absolutePath,
                    "-vframes",
                    "1",
                    "-vf",
                    scaleFilter,
                    "-q:v",
                    "3",
                    "-y",
                    cachedFile.absolutePath,
                )
            )

        if (result && cachedFile.exists()) {
            return try {
                ImageIO.read(cachedFile)
            } catch (_: Exception) {
                null
            }
        }

        // Fallback: try frame at 0 seconds (very short videos)
        val fallbackResult =
            runFfmpeg(
                listOf(
                    ffmpegBinary,
                    "-i",
                    videoFile.absolutePath,
                    "-vframes",
                    "1",
                    "-vf",
                    scaleFilter,
                    "-q:v",
                    "3",
                    "-y",
                    cachedFile.absolutePath,
                )
            )

        return if (fallbackResult && cachedFile.exists()) {
            try {
                ImageIO.read(cachedFile)
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }

    /**
     * Runs an FFmpeg command with a 30-second timeout.
     *
     * @return `true` if the process exited with code 0, `false` otherwise
     */
    private fun runFfmpeg(args: List<String>): Boolean {
        return try {
            val process = ProcessBuilder(args).redirectErrorStream(true).start()
            process.inputStream.bufferedReader().readText() // drain output to prevent deadlock
            val finished = process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                false
            } else {
                process.exitValue() == 0
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Pure-Java fallback for video thumbnail extraction.
     *
     * Uses `javax.imageio.ImageIO` to attempt reading the video file. This works for:
     * - Motion JPEG in AVI containers (common in older Canon cameras)
     * - Some QuickTime containers with compatible codecs
     *
     * Returns `null` for modern codecs (H.264, HEVC, VP9) since ImageIO doesn't include video
     * decoders.
     */
    private fun extractViaJavaFallback(videoFile: File, maxPx: Int): BufferedImage? {
        return try {
            // Try reading the first frame with ImageIO
            // This only works for formats ImageIO has readers for (JMF-compatible)
            val image = ImageIO.read(videoFile) ?: return null
            if (image.width <= maxPx && image.height <= maxPx) {
                image
            } else {
                // Scale down using the same approach as ThumbnailImage
                val scaled =
                    org.imgscalr.Scalr.resize(image, org.imgscalr.Scalr.Method.BALANCED, maxPx)
                image.flush()
                scaled
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Clears the on-disk thumbnail cache. */
    fun clearCache() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    /** Returns the total size of the on-disk thumbnail cache in bytes. */
    fun getCacheSize(): Long = cacheDir.listFiles()?.sumOf { it.length() } ?: 0L
}
