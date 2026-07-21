package org.kryspetrie.fileimport.domain.model.geometry

import org.kryspetrie.fileimport.domain.model.DomainDefaults

/**
 * Represents a complete bounding box with corners, selection state, and undo history.
 *
 * Supports both rectangular and quadrilateral shapes, with operations for moving, expanding,
 * rotating, and selecting boxes.
 */
data class BoundingBox(
    val id: String = DomainDefaults.generateId(),
    val corners: BoundingBoxCorners,
    val isSelected: Boolean = false,
    val selectedCorner: Corner? = null,
) {
    fun center(): Point = corners.center()

    fun width(): Double = corners.width()

    fun height(): Double = corners.height()

    fun aspectRatio(): Double = corners.aspectRatio()

    fun isPortrait(): Boolean = corners.isPortrait()

    fun isSquare(): Boolean = corners.isSquare()

    fun move(deltaX: Double, deltaY: Double): BoundingBox =
        copy(corners = corners.translated(deltaX, deltaY))

    fun moveCorner(corner: Corner, newPosition: Point): BoundingBox =
        copy(corners = corners.withCornerMoved(corner, newPosition))

    fun expand(scaleFactor: Double): BoundingBox = copy(corners = corners.expanded(scaleFactor))

    fun rotate(angleDegrees: Double): BoundingBox = copy(corners = corners.rotated(angleDegrees))

    fun select(): BoundingBox = copy(isSelected = true)

    fun deselect(): BoundingBox = copy(isSelected = false, selectedCorner = null)

    fun selectCorner(corner: Corner): BoundingBox = copy(isSelected = true, selectedCorner = corner)

    fun deselectCorner(): BoundingBox = copy(selectedCorner = null)

    fun withCorners(newCorners: BoundingBoxCorners): BoundingBox = copy(corners = newCorners)

    companion object {
        fun createRectangular(center: Point, width: Double, height: Double): BoundingBox =
            BoundingBox(corners = BoundingBoxCorners.fromCenter(center.x, center.y, width, height))

        fun createRectangular(
            centerX: Double,
            centerY: Double,
            width: Double,
            height: Double,
        ): BoundingBox =
            BoundingBox(corners = BoundingBoxCorners.fromCenter(centerX, centerY, width, height))

        fun fromQuadrilateral(points: List<Point>): BoundingBox {
            require(points.size == 4) { "Exactly 4 points required for quadrilateral" }
            val sorted = computeConvexHull(points)
            return BoundingBox(
                corners = BoundingBoxCorners(sorted[0], sorted[1], sorted[2], sorted[3])
            )
        }

        fun fromRectangle(corner1: Point, corner2: Point): BoundingBox {
            val minX = minOf(corner1.x, corner2.x)
            val maxX = maxOf(corner1.x, corner2.x)
            val minY = minOf(corner1.y, corner2.y)
            val maxY = maxOf(corner1.y, corner2.y)
            return BoundingBox(
                corners =
                    BoundingBoxCorners(
                        topLeft = Point(minX, minY),
                        topRight = Point(maxX, minY),
                        bottomRight = Point(maxX, maxY),
                        bottomLeft = Point(minX, maxY),
                    )
            )
        }

        private fun computeConvexHull(points: List<Point>): List<Point> {
            if (points.size < 3) return points
            val uniquePoints = points.distinct()
            if (uniquePoints.size < 3) return uniquePoints
            val start = uniquePoints.minByOrNull { it.x } ?: uniquePoints.first()
            val hull = mutableListOf<Point>()
            var current = start
            do {
                hull.add(current)
                var next = uniquePoints[0]
                for (p in uniquePoints) {
                    if (next == current || crossProduct(current, next, p) < 0) next = p
                }
                current = next
            } while (current != start && hull.size < uniquePoints.size)
            // Pad hull to 4 points if fewer were found by repeating hull points cyclically.
            // This ensures quadrilateral operations always have 4 corners to work with.
            if (hull.size < 4 && hull.isNotEmpty()) {
                val padded = hull.toMutableList()
                var idx = 0
                while (padded.size < 4) {
                    padded.add(hull[idx % hull.size])
                    idx++
                }
                return padded
            }
            if (hull.size == 4) return hull
            if (hull.size >= 4) {
                val c = Point(hull.map { it.x }.average(), hull.map { it.y }.average())
                return hull.sortedBy { kotlin.math.atan2(it.y - c.y, it.x - c.x) }.take(4)
            }
            return uniquePoints.sortedBy { it.x }.take(4)
        }

        private fun crossProduct(o: Point, a: Point, b: Point): Double =
            (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
    }
}
