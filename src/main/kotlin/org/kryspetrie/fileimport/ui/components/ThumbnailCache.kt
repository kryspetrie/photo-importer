package org.kryspetrie.fileimport.ui.components

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.imgscalr.Scalr
import org.jetbrains.skia.Image as SkiaImage
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.ImageFileType
import org.kryspetrie.fileimport.domain.port.ThumbnailExtractorPort
import org.kryspetrie.fileimport.infrastructure.adapter.ThumbnailExtractorAdapter
import org.kryspetrie.fileimport.infrastructure.adapter.toBufferedImage

/**
 * Caches thumbnail [ImageBitmap]s keyed by file path and max pixel dimension.
 *
 * Uses [ThumbnailExtractorPort] for RAW and video thumbnail extraction,
 * keeping this UI component free of direct infrastructure adapter imports
 * (except the default adapter initialization). The [thumbnailExtractor] defaults
 * to the infrastructure adapter but can be overridden in tests.
 */
object ThumbnailCache {
    /** IO dispatcher for coroutine context switching. Override in tests. */
    @Suppress("InjectDispatcher") // Object singleton — dispatcher injected via configurable var
    var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    /**
     * Thumbnail extraction port. Defaults to the infrastructure adapter implementation.
     * Override in tests to provide a test double.
     */
    var thumbnailExtractor: ThumbnailExtractorPort = ThumbnailExtractorAdapter

    private val cache = ConcurrentHashMap<String, ImageBitmap>()

    suspend fun load(path: FilePath, maxPx: Int): ImageBitmap? {
        val key = "${path.path}:$maxPx"
        cache[key]?.let {
            return it
        }
        return withContext(ioDispatcher) {
            try {
                val fileType = ImageFileType.fromExtension(path.extension)
                val original: BufferedImage? =
                    if (fileType.isRaw) {
                        thumbnailExtractor.extractFromRaw(path)?.toBufferedImage()
                    } else {
                        ImageIO.read(path.toFile())
                    }
                original ?: return@withContext null
                val scaled = Scalr.resize(original, Scalr.Method.BALANCED, maxPx)
                original.flush()
                val baos = ByteArrayOutputStream()
                ImageIO.write(scaled, "jpg", baos)
                scaled.flush()
                SkiaImage.makeFromEncoded(baos.toByteArray()).toComposeImageBitmap().also {
                    cache[key] = it
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun loadVideo(path: FilePath, maxPx: Int): ImageBitmap? {
        val key = "video:${path.path}:$maxPx"
        cache[key]?.let {
            return it
        }
        return withContext(ioDispatcher) {
            try {
                val processedImage =
                    thumbnailExtractor.extractFromVideo(path, maxPx) ?: return@withContext null
                val frame = processedImage.toBufferedImage()
                val baos = ByteArrayOutputStream()
                ImageIO.write(frame, "jpg", baos)
                frame.flush()
                SkiaImage.makeFromEncoded(baos.toByteArray()).toComposeImageBitmap().also {
                    cache[key] = it
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}