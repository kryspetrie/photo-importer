package org.kryspetrie.fileimport.ui.shared.image

/**
 * Maps pointer coordinates within a [androidx.compose.ui.layout.ContentScale.Fit] preview box to
 * normalized image coordinates (0.0–1.0). Returns null when the point falls outside the letterboxed
 * image bounds.
 */
object PreviewImageGeometry {
    data class FitBounds(
        val offsetX: Double,
        val offsetY: Double,
        val displayWidth: Double,
        val displayHeight: Double,
    )

    fun fitBounds(
        containerWidth: Double,
        containerHeight: Double,
        imageWidth: Int,
        imageHeight: Int,
    ): FitBounds {
        require(containerWidth > 0 && containerHeight > 0)
        require(imageWidth > 0 && imageHeight > 0)
        val scale = minOf(containerWidth / imageWidth, containerHeight / imageHeight)
        val displayWidth = imageWidth * scale
        val displayHeight = imageHeight * scale
        return FitBounds(
            offsetX = (containerWidth - displayWidth) / 2.0,
            offsetY = (containerHeight - displayHeight) / 2.0,
            displayWidth = displayWidth,
            displayHeight = displayHeight,
        )
    }

    fun normalizedImageCoordinates(
        tapX: Double,
        tapY: Double,
        containerWidth: Double,
        containerHeight: Double,
        imageWidth: Int,
        imageHeight: Int,
    ): Pair<Double, Double>? {
        if (containerWidth <= 0 || containerHeight <= 0 || imageWidth <= 0 || imageHeight <= 0) {
            return null
        }
        val bounds = fitBounds(containerWidth, containerHeight, imageWidth, imageHeight)
        val relativeX = tapX - bounds.offsetX
        val relativeY = tapY - bounds.offsetY
        if (
            relativeX < 0 ||
                relativeY < 0 ||
                relativeX > bounds.displayWidth ||
                relativeY > bounds.displayHeight
        ) {
            return null
        }
        val normX = (relativeX / bounds.displayWidth).coerceIn(0.0, 1.0)
        val normY = (relativeY / bounds.displayHeight).coerceIn(0.0, 1.0)
        return normX to normY
    }
}
