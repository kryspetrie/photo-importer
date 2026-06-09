package org.kryspetrie.fileimport.infrastructure.adapter

import java.awt.image.BufferedImage
import org.kryspetrie.fileimport.domain.model.ProcessedImage

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
