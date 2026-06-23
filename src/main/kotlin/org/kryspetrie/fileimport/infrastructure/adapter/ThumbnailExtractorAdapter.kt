package org.kryspetrie.fileimport.infrastructure.adapter

import java.io.File
import org.kryspetrie.fileimport.domain.model.ProcessedImage
import org.kryspetrie.fileimport.domain.port.ThumbnailExtractorPort

/**
 * Adapter implementing [ThumbnailExtractorPort] by delegating to the existing
 * [RawThumbnailExtractor] and [VideoThumbnailAdapter] infrastructure services.
 *
 * Converts between domain [ProcessedImage] and infrastructure [java.awt.image.BufferedImage]
 * at the boundary.
 */
object ThumbnailExtractorAdapter : ThumbnailExtractorPort {

    override fun extractFromRaw(file: File): ProcessedImage? {
        return RawThumbnailExtractor.extractEmbeddedThumbnail(file)?.toProcessedImage()
    }

    override suspend fun extractFromVideo(file: File, maxPx: Int): ProcessedImage? {
        return VideoThumbnailAdapter.extractThumbnail(file, maxPx)?.toProcessedImage()
    }
}