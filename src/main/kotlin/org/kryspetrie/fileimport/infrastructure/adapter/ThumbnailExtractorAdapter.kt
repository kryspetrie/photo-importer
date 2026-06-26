package org.kryspetrie.fileimport.infrastructure.adapter

import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.ProcessedImage
import org.kryspetrie.fileimport.domain.port.ThumbnailExtractorPort

/**
 * Adapter implementing [ThumbnailExtractorPort] by delegating to the existing
 * [RawThumbnailExtractor] and [VideoThumbnailAdapter] infrastructure services.
 *
 * Converts between domain [FilePath] and `java.io.File` at the boundary, and between
 * domain [ProcessedImage] and infrastructure [java.awt.image.BufferedImage] at the boundary.
 */
object ThumbnailExtractorAdapter : ThumbnailExtractorPort {

    override fun extractFromRaw(path: FilePath): ProcessedImage? {
        return RawThumbnailExtractor.extractEmbeddedThumbnail(path.toFile())?.toProcessedImage()
    }

    override suspend fun extractFromVideo(path: FilePath, maxPx: Int): ProcessedImage? {
        return VideoThumbnailAdapter.extractThumbnail(path.toFile(), maxPx)?.toProcessedImage()
    }
}