package org.kryspetrie.fileimport.ui.components

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.imgscalr.Scalr
import org.jetbrains.skia.Image as SkiaImage
import org.kryspetrie.fileimport.domain.model.ImageFileType
import org.kryspetrie.fileimport.infrastructure.adapter.RawThumbnailExtractor
import org.kryspetrie.fileimport.infrastructure.adapter.VideoThumbnailAdapter

object ThumbnailCache {
    /** IO dispatcher for coroutine context switching. Override in tests. */
    @Suppress("InjectDispatcher") // Object singleton — dispatcher injected via configurable var
    var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    private val cache = ConcurrentHashMap<String, ImageBitmap>()

    suspend fun load(file: File, maxPx: Int): ImageBitmap? {
        val key = "${file.absolutePath}:$maxPx"
        cache[key]?.let {
            return it
        }
        return withContext(ioDispatcher) {
            try {
                val fileType = ImageFileType.fromExtension(file.extension)
                val original =
                    if (fileType.isRaw) {
                        RawThumbnailExtractor.extractEmbeddedThumbnail(file)
                    } else {
                        ImageIO.read(file)
                    } ?: return@withContext null
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

    suspend fun loadVideo(file: File, maxPx: Int): ImageBitmap? {
        val key = "video:${file.absolutePath}:$maxPx"
        cache[key]?.let {
            return it
        }
        return withContext(ioDispatcher) {
            try {
                val frame =
                    VideoThumbnailAdapter.extractThumbnail(file, maxPx) ?: return@withContext null
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
