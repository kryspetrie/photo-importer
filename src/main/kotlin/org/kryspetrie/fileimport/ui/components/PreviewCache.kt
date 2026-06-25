package org.kryspetrie.fileimport.ui.components

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.image.BufferedImage
import java.util.concurrent.ConcurrentHashMap
import org.kryspetrie.fileimport.domain.port.PerspectiveCorrectionPort
import org.kryspetrie.fileimport.infrastructure.adapter.correctPerspective
import org.kryspetrie.fileimport.domain.model.geometry.BoundingBox
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration
import org.kryspetrie.fileimport.ui.screens.wizard.cropAndRotateBoundingBox

/**
 * Caches perspective-corrected preview images keyed by bounding box geometry + geometric config
 * (rotation, perspective correction, aspect ratio). Metadata-only changes do NOT invalidate the
 * cache since they don't affect the visual preview.
 *
 * Two levels of caching are provided:
 * - **Thumbnail**: Downsampled to a max dimension of [thumbnailMaxSize] (default 400px) for list
 *   views and cards.
 * - **Full**: Full-resolution result for fullscreen preview dialogs.
 *
 * Thread-safe via [ConcurrentHashMap].
 */
class PreviewCache(
    private val perspectiveService: PerspectiveCorrectionPort,
    private val thumbnailMaxSize: Int = DEFAULT_THUMBNAIL_MAX_SIZE,
) {
    private val fullCache = ConcurrentHashMap<String, BufferedImage>()
    private val thumbnailCache = ConcurrentHashMap<String, ImageBitmap>()

    /**
     * Generates a cache key from only the geometric properties that affect the visual preview:
     * bounding box corners + rotation + perspective correction + aspect ratio.
     * Metadata fields (description, keywords, etc.) are intentionally excluded because they
     * don't change the image appearance.
     */
    fun cacheKey(box: BoundingBox, config: PhotoScanConfiguration): String {
        val c = box.corners
        return buildString {
            append("${c.topLeft.x.toInt()},${c.topLeft.y.toInt()}")
            append("_${c.topRight.x.toInt()},${c.topRight.y.toInt()}")
            append("_${c.bottomRight.x.toInt()},${c.bottomRight.y.toInt()}")
            append("_${c.bottomLeft.x.toInt()},${c.bottomLeft.y.toInt()}")
            append("_r${config.rotationDegrees}")
            append("_p${config.perspectiveCorrectionEnabled}")
            append("_a${config.aspectRatio}")
            // Include correction strategy since it affects which correction is applied
            append("_s${config.correctionStrategy?.name ?: "def"}")
        }
    }

    /**
     * Returns the full-resolution perspective-corrected preview for the given box+config,
     * computing it if not already cached.
     */
    fun getFullPreview(sourceImage: BufferedImage, box: BoundingBox, config: PhotoScanConfiguration): BufferedImage? {
        val key = cacheKey(box, config)
        return fullCache.getOrPut(key) {
            cropAndRotateBoundingBox(sourceImage, box, config, perspectiveService) ?: return null
        }
    }

    /**
     * Returns a downsampled thumbnail [ImageBitmap] for the given box+config.
     * The full preview is computed first (and cached), then downsampled to
     * [thumbnailMaxSize] max dimension for efficient list rendering.
     */
    fun getThumbnail(sourceImage: BufferedImage, box: BoundingBox, config: PhotoScanConfiguration): ImageBitmap? {
        val key = cacheKey(box, config)
        thumbnailCache[key]?.let { return it }

        val fullPreview = getFullPreview(sourceImage, box, config) ?: return null
        val thumbnail = downsampleToBitmap(fullPreview, thumbnailMaxSize)
        thumbnailCache[key] = thumbnail
        return thumbnail
    }

    /**
     * Pre-warms the cache for multiple boxes, computing full previews off-thread.
     * Call this when entering a screen to avoid lag during first render.
     */
    fun preWarm(sourceImage: BufferedImage, boxes: List<BoundingBox>, configs: Map<String, PhotoScanConfiguration>) {
        for (box in boxes) {
            val config = configs[box.id] ?: PhotoScanConfiguration()
            val key = cacheKey(box, config)
            if (!fullCache.containsKey(key)) {
                val result = cropAndRotateBoundingBox(sourceImage, box, config, perspectiveService)
                if (result != null) {
                    fullCache[key] = result
                }
            }
        }
    }

    /** Removes entries for boxes/configs that are no longer in use. */
    fun retainOnly(boxes: List<BoundingBox>, configs: Map<String, PhotoScanConfiguration>) {
        val retainedKeys = boxes.map { box ->
            cacheKey(box, configs[box.id] ?: PhotoScanConfiguration())
        }.toSet()

        fullCache.keys.retainAll(retainedKeys)
        thumbnailCache.keys.retainAll(retainedKeys)
    }

    /** Invalidates all cached entries. Useful when the source image changes. */
    fun clear() {
        fullCache.clear()
        thumbnailCache.clear()
    }

    /** Invalidates cache entries where the geometric config has changed. */
    fun invalidate(box: BoundingBox, config: PhotoScanConfiguration) {
        val key = cacheKey(box, config)
        fullCache.remove(key)
        thumbnailCache.remove(key)
    }

    /** Number of full-resolution entries currently cached. */
    val fullSize: Int get() = fullCache.size

    /** Number of thumbnail entries currently cached. */
    val thumbnailSize: Int get() = thumbnailCache.size

    companion object {
        /** Default maximum dimension for thumbnail images. */
        const val DEFAULT_THUMBNAIL_MAX_SIZE = 400

        /**
         * Downsamples a [BufferedImage] to an [ImageBitmap] with maximum dimension [maxSize].
         * Uses area averaging for good quality at small sizes.
         */
        fun downsampleToBitmap(image: BufferedImage, maxSize: Int): ImageBitmap {
            val srcW = image.width
            val srcH = image.height
            if (srcW <= maxSize && srcH <= maxSize) {
                // No downsample needed
                return image.toComposeImageBitmap()
            }
            val scale = maxSize.toDouble() / maxOf(srcW, srcH)
            val dstW = (srcW * scale).toInt().coerceAtLeast(1)
            val dstH = (srcH * scale).toInt().coerceAtLeast(1)

            val scaled = java.awt.image.BufferedImage(dstW, dstH, java.awt.image.BufferedImage.TYPE_INT_RGB)
            val g = scaled.createGraphics()
            g.setRenderingHint(
                java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR,
            )
            g.drawImage(image, 0, 0, dstW, dstH, null)
            g.dispose()
            return scaled.toComposeImageBitmap()
        }
    }
}
