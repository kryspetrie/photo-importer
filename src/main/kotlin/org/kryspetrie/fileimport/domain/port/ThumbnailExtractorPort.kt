package org.kryspetrie.fileimport.domain.port

import java.io.File
import org.kryspetrie.fileimport.domain.model.ProcessedImage

/**
 * Port interface for extracting thumbnail images from media files.
 *
 * This port abstracts the infrastructure-specific thumbnail extraction logic
 * (RAW file embedded thumbnails, video frame extraction) behind a clean interface,
 * keeping the UI layer free of direct infrastructure.adapter imports.
 *
 * ## Why?
 *
 * Previously, `ThumbnailCache` directly called `RawThumbnailExtractor` and
 * `VideoThumbnailAdapter` from the infrastructure layer, creating a UI → infrastructure
 * boundary crossing. This port allows the UI to depend only on a domain abstraction.
 *
 * ## Implementations
 *
 * - `ThumbnailExtractorAdapter`: Delegates to `RawThumbnailExtractor` and `VideoThumbnailAdapter`
 *
 * ## Usage
 *
 * ```kotlin
 * val extractor: ThumbnailExtractorPort = koinInject()
 * val thumbnail = extractor.extractFromRaw(rawFile)
 * val videoFrame = extractor.extractFromVideo(videoFile, maxPx)
 * ```
 */
interface ThumbnailExtractorPort {

    /**
     * Extracts an embedded thumbnail image from a RAW photo file.
     *
     * Uses EXIF metadata or JPEG segment scanning to find embedded preview images.
     * Returns `null` if the file is not a RAW format or if no thumbnail can be extracted.
     *
     * @param file The RAW image file to extract a thumbnail from
     * @return The embedded thumbnail as a [ProcessedImage], or null if extraction fails
     */
    fun extractFromRaw(file: File): ProcessedImage?

    /**
     * Extracts a thumbnail frame from a video file.
     *
     * Uses FFmpeg if available, falling back to pure-Java extraction.
     * Returns `null` if the file is not a video or if extraction fails.
     *
     * @param file The video file to extract a frame from
     * @param maxPx Maximum dimension in pixels for the extracted frame
     * @return The video frame as a [ProcessedImage], or null if extraction fails
     */
    suspend fun extractFromVideo(file: File, maxPx: Int): ProcessedImage?
}