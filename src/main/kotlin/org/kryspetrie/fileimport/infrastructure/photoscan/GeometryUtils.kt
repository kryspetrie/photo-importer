package org.kryspetrie.fileimport.infrastructure.photoscan

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max

/**
 * Pure computational geometry utilities for polygon and quadrilateral operations.
 *
 * All functions are pure (no side effects, no framework dependencies) and operate on
 * [RectangleDetector.Point]. These are extracted from RectangleDetector for testability and reuse.
 */
object GeometryUtils {

    /**
     * Simplify a polyline using the Ramer-Douglas-Peucker algorithm.
     *
     * @param points The polyline to simplify
     * @param epsilon Maximum perpendicular distance tolerance
     * @return Simplified polyline with fewer vertices
     */
    fun douglasPeucker(
        points: List<RectangleDetector.Point>,
        epsilon: Double,
    ): List<RectangleDetector.Point> {
        if (points.size < 3) return points

        var maxDist = 0.0
        var maxIdx = 0
        val first = points.first()
        val last = points.last()

        for (i in 1 until points.size - 1) {
            val dist = perpendicularDistance(points[i], first, last)
            if (dist > maxDist) {
                maxDist = dist
                maxIdx = i
            }
        }

        return if (maxDist > epsilon) {
            val left = douglasPeucker(points.subList(0, maxIdx + 1), epsilon)
            val right = douglasPeucker(points.subList(maxIdx, points.size), epsilon)
            left.dropLast(1) + right
        } else {
            listOf(first, last)
        }
    }

    /** Perpendicular distance from a point to a line segment. */
    fun perpendicularDistance(
        point: RectangleDetector.Point,
        lineStart: RectangleDetector.Point,
        lineEnd: RectangleDetector.Point,
    ): Double {
        val dx = lineEnd.x - lineStart.x.toDouble()
        val dy = lineEnd.y - lineStart.y.toDouble()
        val len = hypot(dx, dy)
        if (len < 1e-9)
            return hypot(point.x - lineStart.x.toDouble(), point.y - lineStart.y.toDouble())
        return abs(
            (dy * point.x - dx * point.y + lineEnd.x * lineStart.y - lineEnd.y * lineStart.x)
        ) / len
    }

    /** Extract the best 4-corner quadrilateral from a set of points. */
    fun extractBestQuad(points: List<RectangleDetector.Point>): List<RectangleDetector.Point> {
        if (points.size == 4) return points

        // Use convex hull if we have more than 4 points
        val hull = convexHull(points)
        if (hull.size <= 4) return hull

        // For convex hull with > 4 points, keep the 4 most "corner-like" points
        return selectMostAcuteCorners(hull)
    }

    /** Compute the convex hull of a set of 2D points using Andrew's monotone chain algorithm. */
    fun convexHull(points: List<RectangleDetector.Point>): List<RectangleDetector.Point> {
        if (points.size < 3) return points
        val sorted = points.sortedWith(compareBy({ it.y }, { it.x }))
        val lower = mutableListOf<RectangleDetector.Point>()
        for (p in sorted) {
            while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), p) <= 0) {
                lower.removeLast()
            }
            lower.add(p)
        }
        val upper = mutableListOf<RectangleDetector.Point>()
        for (p in sorted.reversed()) {
            while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), p) <= 0) {
                upper.removeLast()
            }
            upper.add(p)
        }
        lower.removeLast()
        upper.removeLast()
        return lower + upper
    }

    /**
     * Select the 4 most "corner-like" points from a convex hull by scoring interior angles. Lower
     * angles (more acute) get higher scores.
     */
    fun selectMostAcuteCorners(hull: List<RectangleDetector.Point>): List<RectangleDetector.Point> {
        val n = hull.size
        val scored = mutableListOf<Pair<RectangleDetector.Point, Double>>()
        for (i in hull.indices) {
            val prev = hull[(i - 1 + n) % n]
            val curr = hull[i]
            val next = hull[(i + 1) % n]
            val angle = angleBetween(prev, curr, next)
            // Convert to "corner-ness" score (180° = straight, lower = more acute)
            val cornerScore = 180.0 - abs(angle - 90.0)
            scored.add(curr to cornerScore)
        }
        scored.sortByDescending { it.second }
        return scored.take(4).map { it.first }.toList()
    }

    /** Compute the angle (in degrees) at point [b] formed by rays b→a and b→c. */
    fun angleBetween(
        a: RectangleDetector.Point,
        b: RectangleDetector.Point,
        c: RectangleDetector.Point,
    ): Double {
        val dx1 = a.x - b.x.toDouble()
        val dy1 = a.y - b.y.toDouble()
        val dx2 = c.x - b.x.toDouble()
        val dy2 = c.y - b.y.toDouble()
        val dot = dx1 * dx2 + dy1 * dy2
        val cross = dx1 * dy2 - dy1 * dx2
        return Math.toDegrees(atan2(cross, dot))
    }

    /**
     * Sort 4 corners into canonical order: top-left, top-right, bottom-right, bottom-left. Uses sum
     * (x+y) for TL/BR and difference (x-y) for TR/BL.
     */
    fun sortCorners(corners: List<RectangleDetector.Point>): List<RectangleDetector.Point> {
        if (corners.size != 4) return corners

        // Sort by sum (x+y): smallest = top-left, largest = bottom-right
        val sorted = corners.sortedBy { it.x + it.y }
        val topLeft = sorted[0]
        val bottomRight = sorted[3]

        // Sort remaining two by difference (x-y): smaller = top-right, larger = bottom-left
        val remaining = listOf(sorted[1], sorted[2]).sortedBy { it.x - it.y }
        val topRight = remaining[0]
        val bottomLeft = remaining[1]

        return listOf(topLeft, topRight, bottomRight, bottomLeft)
    }

    /** Compute the area of a polygon using the shoelace formula. */
    fun polygonArea(points: List<RectangleDetector.Point>): Int {
        var area = 0
        val n = points.size
        for (i in points.indices) {
            val j = (i + 1) % n
            area += points[i].x * points[j].y
            area -= points[j].x * points[i].y
        }
        return abs(area / 2)
    }

    /** Compute the centroid (geometric center) of a polygon. */
    fun centroid(points: List<RectangleDetector.Point>): RectangleDetector.Point {
        var cx = 0.0
        var cy = 0.0
        for (p in points) {
            cx += p.x
            cy += p.y
        }
        return RectangleDetector.Point((cx / points.size).toInt(), (cy / points.size).toInt())
    }

    /** Compute the aspect ratio (width/height) of a sorted quadrilateral. */
    fun aspectRatio(sorted: List<RectangleDetector.Point>): Float {
        val tl = sorted[0]
        val tr = sorted[1]
        val bl = sorted[3]
        val width = hypot((tr.x - tl.x).toDouble(), (tr.y - tl.y).toDouble())
        val height = hypot((bl.x - tl.x).toDouble(), (bl.y - tl.y).toDouble())
        return if (height > 0) (width / height).toFloat() else 1f
    }

    /**
     * Compute quadrilateral quality as a float between 0 and 1. 1.0 = perfect rectangle (all angles
     * exactly 90°). Lower = more deviation.
     */
    fun quadrilateralQuality(corners: List<RectangleDetector.Point>): Float {
        if (corners.size != 4) return 0f
        val n = corners.size
        var totalDeviation = 0.0
        for (i in corners.indices) {
            val prev = corners[(i - 1 + n) % n]
            val curr = corners[i]
            val next = corners[(i + 1) % n]
            val angle = abs(angleBetween(prev, curr, next))
            val deviation = abs(angle - 90.0)
            totalDeviation += deviation
        }
        val avgDev = totalDeviation / 4.0
        return max(0.0, 1.0 - avgDev / 90.0).toFloat()
    }

    /**
     * Validate that all four corners of a quadrilateral have angles within the given range.
     *
     * Photos on a flat surface should have corners very close to 90°. Allowing 60-120° range
     * accounts for significant perspective distortion, camera angle, and edge detection imprecision
     * while still filtering out obviously non-rectangular shapes.
     */
    fun hasValidAngles(
        corners: List<RectangleDetector.Point>,
        minAngleDiff: Float = 60f,
        maxAngleDiff: Float = 120f,
    ): Boolean {
        if (corners.size != 4) return false
        for (i in corners.indices) {
            val prev = corners[(i - 1 + 4) % 4]
            val curr = corners[i]
            val next = corners[(i + 1) % 4]
            val angle = abs(angleBetween(prev, curr, next))
            if (angle < minAngleDiff || angle > maxAngleDiff) {
                return false
            }
        }
        return true
    }

    /** 2D cross product: (a - o) × (b - o) */
    fun cross(
        o: RectangleDetector.Point,
        a: RectangleDetector.Point,
        b: RectangleDetector.Point,
    ): Int {
        return (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
    }
}
