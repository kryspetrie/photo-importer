package org.kryspetrie.fileimport.ui.wizard.state

import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import kotlin.math.cos
import kotlin.math.sin

/**
 * Handles rotation transformation with bounding box expansion. Ensures rotated images don't clip at
 * corners.
 */
class RotationTransformer {

    /**
     * Rotates an image by the specified angle.
     *
     * @param source Source image
     * @param degrees Rotation angle in degrees (positive = clockwise)
     * @return Rotated image with expanded canvas to prevent clipping
     */
    fun rotate(source: BufferedImage, degrees: Double): BufferedImage {
        val radians = Math.toRadians(degrees)

        // Calculate the size of the output image
        val (newWidth, newHeight) =
            calculateExpandedBounds(source.width.toDouble(), source.height.toDouble(), degrees)

        // Create output image
        val result = BufferedImage(newWidth.toInt(), newHeight.toInt(), source.type)

        // Create rotation transform
        val transform = AffineTransform()

        // Translate to center of new image
        transform.translate(newWidth / 2, newHeight / 2)

        // Rotate
        transform.rotate(-radians) // Negative because image y is inverted

        // Translate so original image center is at new image center
        transform.translate(-source.width / 2.0, -source.height / 2.0)

        // Apply transform
        val graphics = result.createGraphics()
        graphics.setRenderingHint(
            java.awt.RenderingHints.KEY_INTERPOLATION,
            java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR,
        )
        graphics.drawImage(source, transform, null)
        graphics.dispose()

        return result
    }

    /**
     * Calculates the expanded bounding box dimensions after rotation. Uses the formula: newW =
     * w*|cos(θ)| + h*|sin(θ)|, newH = w*|sin(θ)| + h*|cos(θ)|
     *
     * @param width Original width
     * @param height Original height
     * @param degrees Rotation angle in degrees
     * @return Pair of (newWidth, newHeight)
     */
    fun calculateExpandedBounds(
        width: Double,
        height: Double,
        degrees: Double,
    ): Pair<Double, Double> {
        val radians = Math.toRadians(kotlin.math.abs(degrees))

        val cosA = cos(radians)
        val sinA = sin(radians)

        val newWidth = width * cosA + height * sinA
        val newHeight = width * sinA + height * cosA

        return Pair(newWidth, newHeight)
    }

    /**
     * Rotates a bounding box's corners by the specified angle around its center.
     *
     * @param corners The original bounding box corners
     * @param degrees Rotation angle in degrees (positive = clockwise)
     * @return New corners after rotation
     */
    fun rotateCorners(corners: BoundingBoxCorners, degrees: Double): BoundingBoxCorners {
        val center = corners.center()
        return corners.rotated(degrees, center)
    }

    /**
     * Detects the rotation angle needed to align a quadrilateral to a rectangle. Calculates the
     * dominant rotation of the quadrilateral's edges.
     *
     * @param corners The bounding box corners
     * @return Recommended rotation angle in degrees
     */
    fun detectRotationAngle(corners: BoundingBoxCorners): Double {
        // Calculate angles of the horizontal edges
        val topEdge = angleOfEdge(corners.topLeft, corners.topRight)
        val bottomEdge = angleOfEdge(corners.bottomRight, corners.bottomLeft)

        // Average horizontal edges — rotation needed to bring them to 0 degrees
        return normalizeAngle((topEdge + bottomEdge) / 2)
    }

    /** Calculates the angle of an edge in degrees from horizontal. */
    private fun angleOfEdge(start: Point, end: Point): Double {
        val dx = end.x - start.x
        val dy = end.y - start.y
        return Math.toDegrees(kotlin.math.atan2(dy, dx))
    }

    /** Normalizes an angle to be between -90 and 90 degrees. */
    private fun normalizeAngle(angle: Double): Double {
        var normalized = angle % 180
        if (normalized > 90) normalized -= 180
        if (normalized < -90) normalized += 180
        return normalized
    }

    /** Rotates the image 90 degrees clockwise. */
    fun rotate90CW(source: BufferedImage): BufferedImage {
        val result = BufferedImage(source.height, source.width, source.type)
        val graphics = result.createGraphics()

        graphics.translate(result.width, 0)
        graphics.rotate(Math.PI / 2)
        graphics.drawImage(source, 0, 0, null)
        graphics.dispose()

        return result
    }

    /** Rotates the image 90 degrees counter-clockwise. */
    fun rotate90CCW(source: BufferedImage): BufferedImage {
        val result = BufferedImage(source.height, source.width, source.type)
        val graphics = result.createGraphics()

        graphics.translate(0, result.height)
        graphics.rotate(-Math.PI / 2)
        graphics.drawImage(source, 0, 0, null)
        graphics.dispose()

        return result
    }

    companion object {
        /** Creates a rotation transformer. */
        fun create(): RotationTransformer = RotationTransformer()
    }
}
