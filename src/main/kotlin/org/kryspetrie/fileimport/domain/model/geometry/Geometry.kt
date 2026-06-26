package org.kryspetrie.fileimport.domain.model.geometry

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Represents a 2D point with floating-point coordinates.
 *
 * Used for precise positioning of bounding box corners and image geometry. Supports arithmetic
 * operations and distance calculations.
 */
data class Point(val x: Double, val y: Double) {
    constructor(x: Int, y: Int) : this(x.toDouble(), y.toDouble())

    /** Convert to a Float-precision PhotoCorner. */
    fun toPhotoCorner(): org.kryspetrie.fileimport.domain.model.PhotoCorner =
        org.kryspetrie.fileimport.domain.model.PhotoCorner(x = x.toFloat(), y = y.toFloat())

    fun translate(dx: Double, dy: Double): Point = Point(x + dx, y + dy)

    fun distanceTo(other: Point): Double {
        val dx = other.x - x
        val dy = other.y - y
        return sqrt(dx * dx + dy * dy)
    }

    operator fun minus(other: Point): Point = Point(x - other.x, y - other.y)

    operator fun plus(other: Point): Point = Point(x + other.x, y + other.y)

    operator fun times(scale: Double): Point = Point(x * scale, y * scale)

    companion object {
        val ZERO = Point(0.0, 0.0)
    }
}

/**
 * Represents the four corners of a bounding box.
 *
 * Supports both rectangular (axis-aligned) and quadrilateral (perspective) shapes. Provides
 * geometric operations: expansion, rotation, intersection testing, etc.
 */
data class BoundingBoxCorners(
    val topLeft: Point,
    val topRight: Point,
    val bottomRight: Point,
    val bottomLeft: Point,
) {
    fun toList(): List<Point> = listOf(topLeft, topRight, bottomRight, bottomLeft)

    /** Returns the [Point] for the given [Corner]. */
    fun forCorner(corner: Corner): Point =
        when (corner) {
            Corner.TOP_LEFT -> topLeft
            Corner.TOP_RIGHT -> topRight
            Corner.BOTTOM_LEFT -> bottomLeft
            Corner.BOTTOM_RIGHT -> bottomRight
        }

    fun center(): Point =
        Point(
            (topLeft.x + topRight.x + bottomRight.x + bottomLeft.x) / 4.0,
            (topLeft.y + topRight.y + bottomRight.y + bottomLeft.y) / 4.0,
        )

    fun width(): Double {
        val topWidth = topRight.x - topLeft.x
        val bottomWidth = bottomRight.x - bottomLeft.x
        return (abs(topWidth) + abs(bottomWidth)) / 2.0
    }

    fun height(): Double {
        val leftHeight = bottomLeft.y - topLeft.y
        val rightHeight = bottomRight.y - topRight.y
        return (abs(leftHeight) + abs(rightHeight)) / 2.0
    }

    fun aspectRatio(): Double = width() / height()

    /** Checks if this shape would create an invalid (self-intersecting/bowtie) quadrilateral. */
    fun wouldCreateInvalidShape(): Boolean {
        val edges =
            listOf(
                Pair(topLeft, topRight),
                Pair(topRight, bottomRight),
                Pair(bottomRight, bottomLeft),
                Pair(bottomLeft, topLeft),
            )
        for (i in edges.indices) {
            for (j in (i + 2) until edges.size) {
                if (i == 0 && j == 3) continue
                if (
                    edgesIntersect(edges[i].first, edges[i].second, edges[j].first, edges[j].second)
                ) {
                    return true
                }
            }
        }
        return false
    }

    private fun edgesIntersect(a: Point, b: Point, c: Point, d: Point): Boolean {
        return doSegmentsIntersect(a.x, a.y, b.x, b.y, c.x, c.y, d.x, d.y)
    }

    private fun doSegmentsIntersect(
        ax: Double,
        ay: Double,
        bx: Double,
        by: Double,
        cx: Double,
        cy: Double,
        dx: Double,
        dy: Double,
    ): Boolean {
        fun cross(ax: Double, ay: Double, bx: Double, by: Double): Double = ax * by - ay * bx
        val d1 = cross(bx - ax, by - ay, cx - ax, cy - ay)
        val d2 = cross(bx - ax, by - ay, dx - ax, dy - ay)
        val d3 = cross(dx - cx, dy - cy, ax - cx, ay - cy)
        val d4 = cross(dx - cx, dy - cy, bx - cx, by - cy)
        return (d1 * d2 < 0) && (d3 * d4 < 0)
    }

    fun isPortrait(): Boolean = height() > width()

    fun isSquare(threshold: Double = 0.1): Boolean {
        val w = width()
        val h = height()
        val smaller = minOf(w, h)
        return abs(w - h) / smaller < threshold
    }

    fun translated(dx: Double, dy: Double): BoundingBoxCorners =
        BoundingBoxCorners(
            topLeft.translate(dx, dy),
            topRight.translate(dx, dy),
            bottomRight.translate(dx, dy),
            bottomLeft.translate(dx, dy),
        )

    fun withCornerMoved(corner: Corner, newPosition: Point): BoundingBoxCorners =
        when (corner) {
            Corner.TOP_LEFT -> copy(topLeft = newPosition)
            Corner.TOP_RIGHT -> copy(topRight = newPosition)
            Corner.BOTTOM_LEFT -> copy(bottomLeft = newPosition)
            Corner.BOTTOM_RIGHT -> copy(bottomRight = newPosition)
        }

    fun expanded(scaleFactor: Double, aroundCenter: Point? = null): BoundingBoxCorners {
        val c = aroundCenter ?: center()
        val expandedPoints = toList().map { p -> c + (p - c) * scaleFactor }
        return BoundingBoxCorners(
            expandedPoints[0],
            expandedPoints[1],
            expandedPoints[2],
            expandedPoints[3],
        )
    }

    fun rotated(angleDegrees: Double, aroundCenter: Point? = null): BoundingBoxCorners {
        val c = aroundCenter ?: center()
        val angleRadians = Math.toRadians(angleDegrees)
        val cosA = cos(angleRadians)
        val sinA = sin(angleRadians)
        val rotate = { p: Point ->
            val t = p - c
            (Point(t.x * cosA - t.y * sinA, t.x * sinA + t.y * cosA)) + c
        }
        return BoundingBoxCorners(
            rotate(topLeft),
            rotate(topRight),
            rotate(bottomRight),
            rotate(bottomLeft),
        )
    }

    companion object {
        fun fromCenter(
            centerX: Double,
            centerY: Double,
            width: Double,
            height: Double,
        ): BoundingBoxCorners {
            val halfW = width / 2
            val halfH = height / 2
            return BoundingBoxCorners(
                Point(centerX - halfW, centerY - halfH),
                Point(centerX + halfW, centerY - halfH),
                Point(centerX + halfW, centerY + halfH),
                Point(centerX - halfW, centerY + halfH),
            )
        }

        fun fromRectangular(
            x: Double,
            y: Double,
            width: Double,
            height: Double,
        ): BoundingBoxCorners =
            BoundingBoxCorners(
                Point(x, y),
                Point(x + width, y),
                Point(x + width, y + height),
                Point(x, y + height),
            )
    }
}

/** Enum representing a corner of a bounding box. */
enum class Corner {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT;

    fun opposite(): Corner =
        when (this) {
            TOP_LEFT -> BOTTOM_RIGHT
            TOP_RIGHT -> BOTTOM_LEFT
            BOTTOM_LEFT -> TOP_RIGHT
            BOTTOM_RIGHT -> TOP_LEFT
        }

    fun adjacentClockwise(): Corner =
        when (this) {
            TOP_LEFT -> TOP_RIGHT
            TOP_RIGHT -> BOTTOM_RIGHT
            BOTTOM_RIGHT -> BOTTOM_LEFT
            BOTTOM_LEFT -> TOP_LEFT
        }

    fun adjacentCounterClockwise(): Corner =
        when (this) {
            TOP_LEFT -> BOTTOM_LEFT
            TOP_RIGHT -> TOP_LEFT
            BOTTOM_RIGHT -> TOP_RIGHT
            BOTTOM_LEFT -> BOTTOM_RIGHT
        }
}
