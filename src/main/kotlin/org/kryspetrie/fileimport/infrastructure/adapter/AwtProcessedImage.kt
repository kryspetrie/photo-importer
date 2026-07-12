package org.kryspetrie.fileimport.infrastructure.adapter

import java.awt.image.BufferedImage
import java.io.File
import org.kryspetrie.fileimport.domain.model.DetectedPhoto
import org.kryspetrie.fileimport.domain.model.FaceRegion
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.ProcessedImage
import org.kryspetrie.fileimport.domain.port.FaceRegionTransformerPort
import org.kryspetrie.fileimport.domain.port.PerspectiveCorrectionPort

/**
 * [ProcessedImage] implementation wrapping a [BufferedImage].
 *
 * This is the standard implementation used throughout the application — any code that has a
 * `BufferedImage` can wrap it with `.toProcessedImage()` to pass it through domain-layer APIs that
 * require [ProcessedImage].
 */
class AwtProcessedImage(val delegate: BufferedImage) : ProcessedImage {
    override val width: Int = delegate.width
    override val height: Int = delegate.height

    override fun equals(other: Any?): Boolean =
        other is AwtProcessedImage && delegate == other.delegate

    override fun hashCode(): Int = delegate.hashCode()

    override fun toString(): String = "ProcessedImage(${width}x${height})"
}

/**
 * Extension to convert a [BufferedImage] to a [ProcessedImage].
 *
 * Used at infrastructure/application boundaries where AWT images enter domain APIs.
 */
fun BufferedImage.toProcessedImage(): ProcessedImage = AwtProcessedImage(this)

/**
 * Extension to convert a [ProcessedImage] back to a [BufferedImage].
 *
 * Used in infrastructure, application, and UI layers that need AWT pixel operations. If the
 * [ProcessedImage] is an [AwtProcessedImage], returns the wrapped delegate directly without
 * copying. Otherwise, throws [IllegalArgumentException].
 */
fun ProcessedImage.toBufferedImage(): BufferedImage =
    when (this) {
        is AwtProcessedImage -> this.delegate
        else ->
            throw IllegalArgumentException(
                "Cannot convert ${this::class.simpleName} to BufferedImage — " +
                    "expected AwtProcessedImage"
            )
    }

/**
 * AWT convenience extension: corrects perspective using [BufferedImage] directly.
 *
 * Wraps the [BufferedImage] as a [ProcessedImage] for the port call and unwraps the result. UI code
 * that works with [BufferedImage] can use this instead of manual conversion.
 */
fun PerspectiveCorrectionPort.correctPerspective(
    sourceImage: BufferedImage,
    detectedPhoto: DetectedPhoto,
): BufferedImage {
    val result = correctPerspective(sourceImage.toProcessedImage(), detectedPhoto)
    return result.toBufferedImage()
}

/**
 * AWT convenience extension: transforms face regions using [File] directly.
 *
 * Converts the [File] to a [FilePath] for the port call. UI code that works with [File] can use
 * this instead of manual conversion.
 */
fun FaceRegionTransformerPort.transformFaceRegionsFromSource(
    sourceFile: File,
    detectedPhoto: DetectedPhoto,
    outputWidth: Int,
    outputHeight: Int,
    sourceWidth: Int,
    sourceHeight: Int,
    marginFraction: Double = 0.02,
): List<FaceRegion> =
    transformFaceRegionsFromSource(
        sourceFile = FilePath(sourceFile.absolutePath),
        detectedPhoto = detectedPhoto,
        outputWidth = outputWidth,
        outputHeight = outputHeight,
        sourceWidth = sourceWidth,
        sourceHeight = sourceHeight,
        marginFraction = marginFraction,
    )

/**
 * AWT convenience extension: reads face regions from XMP using [File] directly.
 *
 * Converts the [File] to a [FilePath] for the port call.
 */
fun FaceRegionTransformerPort.readFaceRegionsFromXmp(file: File): List<FaceRegion> =
    readFaceRegionsFromXmp(FilePath(file.absolutePath))
