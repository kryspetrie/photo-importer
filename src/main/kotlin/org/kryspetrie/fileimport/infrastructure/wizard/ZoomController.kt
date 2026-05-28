package org.kryspetrie.fileimport.infrastructure.wizard

import kotlin.math.min

/**
 * Controls zoom and pan operations for the image viewer. Handles zoom around cursor position and
 * bounds checking.
 */
data class ZoomController(
    val zoom: Double = 1.0,
    val panX: Double = 0.0,
    val panY: Double = 0.0,
    val minZoom: Double = 0.1,
    val maxZoom: Double = 10.0,
    val zoomStep: Double = 1.05,
) {
    /** Returns a new controller with zoomed in view. */
    fun zoomIn(cursorX: Double? = null, cursorY: Double? = null): ZoomController {
        return zoomTo(zoom * zoomStep, cursorX, cursorY)
    }

    /** Returns a new controller with zoomed out view. */
    fun zoomOut(cursorX: Double? = null, cursorY: Double? = null): ZoomController {
        return zoomTo(zoom / zoomStep, cursorX, cursorY)
    }

    /**
     * Returns a new controller with the specified zoom level. If cursor position is provided, zoom
     * is applied around that point.
     */
    fun zoomTo(newZoom: Double, cursorX: Double? = null, cursorY: Double? = null): ZoomController {
        val clampedZoom = newZoom.coerceIn(minZoom, maxZoom)

        // Calculate pan adjustment if zooming around cursor
        val newPanX: Double
        val newPanY: Double

        if (cursorX != null && cursorY != null) {
            // Zoom around cursor: keep the point under cursor stationary
            val currentImagePoint = screenToImage(cursorX, cursorY)
            val newImagePoint =
                imageToScreen(currentImagePoint.x, currentImagePoint.y, clampedZoom, panX, panY)

            // Adjust pan to keep cursor over same image point
            newPanX = panX + (cursorX - newImagePoint.x)
            newPanY = panY + (cursorY - newImagePoint.y)
        } else {
            newPanX = panX
            newPanY = panY
        }

        return copy(zoom = clampedZoom, panX = newPanX, panY = newPanY)
    }

    /** Returns a new controller zoomed to fit the entire image within the viewport. */
    fun fitToView(
        imageWidth: Double,
        imageHeight: Double,
        viewportWidth: Double,
        viewportHeight: Double,
    ): ZoomController {
        val scaleX = viewportWidth / imageWidth
        val scaleY = viewportHeight / imageHeight
        // Use min to fit: entire image is visible with possible letterboxing on edges
        val fitZoom = minOf(scaleX, scaleY)

        val clampedZoom = fitZoom.coerceIn(minZoom, maxZoom)

        // Center the image
        val centerX = imageWidth / 2
        val centerY = imageHeight / 2
        val newPanX = viewportWidth / 2 - centerX * clampedZoom
        val newPanY = viewportHeight / 2 - centerY * clampedZoom

        return copy(zoom = clampedZoom, panX = newPanX, panY = newPanY)
    }

    /** Returns a new controller fit to the given bounding box with margin. */
    fun fitToBox(
        corners: BoundingBoxCorners,
        viewportWidth: Double,
        viewportHeight: Double,
        marginFactor: Double = 0.2,
    ): ZoomController {
        // Calculate bounding box dimensions
        val minX = corners.toList().minOf { it.x }
        val maxX = corners.toList().maxOf { it.x }
        val minY = corners.toList().minOf { it.y }
        val maxY = corners.toList().maxOf { it.y }

        // Add margin
        val boxWidth = maxX - minX
        val boxHeight = maxY - minY
        val marginX = boxWidth * marginFactor
        val marginY = boxHeight * marginFactor

        val contentWidth = boxWidth + marginX * 2
        val contentHeight = boxHeight + marginY * 2

        // Calculate zoom to fit
        val scaleX = viewportWidth / contentWidth
        val scaleY = viewportHeight / contentHeight
        val fitZoom = min(scaleX, scaleY)

        val clampedZoom = fitZoom.coerceIn(minZoom, maxZoom)

        // Center on the box
        val centerX = (minX + maxX) / 2
        val centerY = (minY + maxY) / 2
        val newPanX = viewportWidth / 2 - centerX * clampedZoom
        val newPanY = viewportHeight / 2 - centerY * clampedZoom

        return copy(zoom = clampedZoom, panX = newPanX, panY = newPanY)
    }

    /** Returns a new controller with updated pan offset. */
    fun pan(deltaX: Double, deltaY: Double): ZoomController {
        return copy(panX = panX + deltaX, panY = panY + deltaY)
    }

    /** Converts screen coordinates to image coordinates. */
    fun screenToImage(screenX: Double, screenY: Double): Point {
        val imageX = (screenX - panX) / zoom
        val imageY = (screenY - panY) / zoom
        return Point(imageX, imageY)
    }

    /** Converts image coordinates to screen coordinates. */
    fun imageToScreen(imageX: Double, imageY: Double): Point {
        val screenX = imageX * zoom + panX
        val screenY = imageY * zoom + panY
        return Point(screenX, screenY)
    }

    /** Converts image coordinates to screen coordinates using current zoom and pan. */
    fun imageToScreen(imagePoint: Point): Point {
        return imageToScreen(imagePoint.x, imagePoint.y)
    }

    /** Static method to convert image coordinates to screen with given zoom and pan. */
    fun imageToScreen(
        imageX: Double,
        imageY: Double,
        zoom: Double,
        panX: Double,
        panY: Double,
    ): Point {
        val screenX = imageX * zoom + panX
        val screenY = imageY * zoom + panY
        return Point(screenX, screenY)
    }

    /** Returns the zoom as a percentage string. */
    fun zoomPercent(): String {
        return "${(zoom * 100).toInt()}%"
    }

    /** Returns true if zoom is at minimum. */
    fun isAtMinZoom(): Boolean = zoom <= minZoom

    /** Returns true if zoom is at maximum. */
    fun isAtMaxZoom(): Boolean = zoom >= maxZoom

    companion object {
        /** Creates a controller fit to view. */
        fun fit(
            imageWidth: Double,
            imageHeight: Double,
            viewportWidth: Double,
            viewportHeight: Double,
        ): ZoomController {
            return ZoomController()
                .fitToView(imageWidth, imageHeight, viewportWidth, viewportHeight)
        }
    }
}
