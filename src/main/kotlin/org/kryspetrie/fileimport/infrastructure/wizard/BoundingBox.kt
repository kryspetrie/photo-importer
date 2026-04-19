package org.kryspetrie.fileimport.infrastructure.wizard

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Represents a 2D point with floating-point coordinates. Used for precise positioning of bounding
 * box corners.
 */
data class Point(val x: Double, val y: Double) {
  constructor(x: Int, y: Int) : this(x.toDouble(), y.toDouble())

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
 * Represents the four corners of a bounding box. Supports both rectangular (axis-aligned) and
 * quadrilateral (perspective) shapes.
 */
data class BoundingBoxCorners(
    val topLeft: Point,
    val topRight: Point,
    val bottomRight: Point,
    val bottomLeft: Point
) {
  fun toList(): List<Point> = listOf(topLeft, topRight, bottomRight, bottomLeft)

  fun center(): Point {
    return Point(
        (topLeft.x + topRight.x + bottomRight.x + bottomLeft.x) / 4.0,
        (topLeft.y + topRight.y + bottomRight.y + bottomLeft.y) / 4.0)
  }

  fun width(): Double {
    // Average width based on top and bottom edges
    val topWidth = topRight.x - topLeft.x
    val bottomWidth = bottomRight.x - bottomLeft.x
    return (abs(topWidth) + abs(bottomWidth)) / 2.0
  }

  fun height(): Double {
    // Average height based on left and right edges
    val leftHeight = bottomLeft.y - topLeft.y
    val rightHeight = bottomRight.y - topRight.y
    return (abs(leftHeight) + abs(rightHeight)) / 2.0
  }

  fun aspectRatio(): Double = width() / height()

  /**
   * Checks if this shape would create an invalid (self-intersecting/bowtie) quadrilateral. A valid
   * quadrilateral has edges that don't cross each other.
   */
  fun wouldCreateInvalidShape(): Boolean {
    // Get the 4 edges: top, right, bottom, left
    val edges =
        listOf(
            // Top edge: topLeft -> topRight
            Pair(topLeft, topRight),
            // Right edge: topRight -> bottomRight
            Pair(topRight, bottomRight),
            // Bottom edge: bottomRight -> bottomLeft
            Pair(bottomRight, bottomLeft),
            // Left edge: bottomLeft -> topLeft
            Pair(bottomLeft, topLeft))

    // Check if any two non-adjacent edges intersect
    for (i in edges.indices) {
      for (j in (i + 2) until edges.size) {
        // Skip adjacent edges (they share a corner)
        if (i == 0 && j == 3) continue // top and left are adjacent
        if (i == 3 && j == 1)
            continue // left and right are adjacent (not possible with this approach)

        if (edgesIntersect(edges[i].first, edges[i].second, edges[j].first, edges[j].second)) {
          return true
        }
      }
    }
    return false
  }

  /** Checks if line segment AB intersects with line segment CD. */
  private fun edgesIntersect(a: Point, b: Point, c: Point, d: Point): Boolean {
    return doSegmentsIntersect(a.x, a.y, b.x, b.y, c.x, c.y, d.x, d.y)
  }

  /** Uses the cross-product method to determine if two line segments intersect. */
  private fun doSegmentsIntersect(
      ax: Double,
      ay: Double,
      bx: Double,
      by: Double,
      cx: Double,
      cy: Double,
      dx: Double,
      dy: Double
  ): Boolean {
    fun cross(ax: Double, ay: Double, bx: Double, by: Double): Double = ax * by - ay * bx

    val d1 = cross(bx - ax, by - ay, cx - ax, cy - ay)
    val d2 = cross(bx - ax, by - ay, dx - ax, dy - ay)
    val d3 = cross(dx - cx, dy - cy, ax - cx, ay - cy)
    val d4 = cross(dx - cx, dy - cy, bx - cx, by - cy)

    // If d1 and d2 have opposite signs, and d3 and d4 have opposite signs
    return (d1 * d2 < 0) && (d3 * d4 < 0)
  }

  fun isPortrait(): Boolean = height() > width()

  fun isSquare(threshold: Double = 0.1): Boolean {
    val w = width()
    val h = height()
    val smaller = minOf(w, h)
    return abs(w - h) / smaller < threshold
  }

  fun translated(dx: Double, dy: Double): BoundingBoxCorners {
    return BoundingBoxCorners(
        topLeft.translate(dx, dy),
        topRight.translate(dx, dy),
        bottomRight.translate(dx, dy),
        bottomLeft.translate(dx, dy))
  }

  fun withCornerMoved(corner: Corner, newPosition: Point): BoundingBoxCorners {
    return when (corner) {
      Corner.TOP_LEFT -> copy(topLeft = newPosition)
      Corner.TOP_RIGHT -> copy(topRight = newPosition)
      Corner.BOTTOM_LEFT -> copy(bottomLeft = newPosition)
      Corner.BOTTOM_RIGHT -> copy(bottomRight = newPosition)
    }
  }

  fun expanded(scaleFactor: Double, aroundCenter: Point? = null): BoundingBoxCorners {
    val c = aroundCenter ?: center()
    val points = toList()
    val expandedPoints =
        points.map { p ->
          val direction = p - c
          val newPoint = c + direction * scaleFactor
          newPoint
        }
    return BoundingBoxCorners(
        expandedPoints[0], expandedPoints[1], expandedPoints[2], expandedPoints[3])
  }

  fun rotated(angleDegrees: Double, aroundCenter: Point? = null): BoundingBoxCorners {
    val c = aroundCenter ?: center()
    val angleRadians = Math.toRadians(angleDegrees)
    val cosA = cos(angleRadians)
    val sinA = sin(angleRadians)

    val rotate = { p: Point ->
      val translated = p - c
      val rotated =
          Point(
              translated.x * cosA - translated.y * sinA, translated.x * sinA + translated.y * cosA)
      rotated + c
    }

    return BoundingBoxCorners(
        rotate(topLeft), rotate(topRight), rotate(bottomRight), rotate(bottomLeft))
  }

  companion object {
    fun fromCenter(
        centerX: Double,
        centerY: Double,
        width: Double,
        height: Double
    ): BoundingBoxCorners {
      val halfW = width / 2
      val halfH = height / 2
      return BoundingBoxCorners(
          Point(centerX - halfW, centerY - halfH), // topLeft
          Point(centerX + halfW, centerY - halfH), // topRight
          Point(centerX + halfW, centerY + halfH), // bottomRight
          Point(centerX - halfW, centerY + halfH) // bottomLeft
          )
    }

    fun fromRectangular(x: Double, y: Double, width: Double, height: Double): BoundingBoxCorners {
      return BoundingBoxCorners(
          Point(x, y), Point(x + width, y), Point(x + width, y + height), Point(x, y + height))
    }
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
        TOP_LEFT -> Corner.BOTTOM_RIGHT
        TOP_RIGHT -> Corner.BOTTOM_LEFT
        BOTTOM_LEFT -> Corner.TOP_RIGHT
        BOTTOM_RIGHT -> Corner.TOP_LEFT
      }

  fun adjacentClockwise(): Corner =
      when (this) {
        TOP_LEFT -> Corner.TOP_RIGHT
        TOP_RIGHT -> Corner.BOTTOM_RIGHT
        BOTTOM_RIGHT -> Corner.BOTTOM_LEFT
        BOTTOM_LEFT -> Corner.TOP_LEFT
      }

  fun adjacentCounterClockwise(): Corner =
      when (this) {
        TOP_LEFT -> Corner.BOTTOM_LEFT
        TOP_RIGHT -> Corner.TOP_LEFT
        BOTTOM_RIGHT -> Corner.TOP_RIGHT
        BOTTOM_LEFT -> Corner.BOTTOM_RIGHT
      }
}

/** Represents a complete bounding box with corners, selection state, and undo history. */
data class BoundingBox(
    val id: String = java.util.UUID.randomUUID().toString(),
    val corners: BoundingBoxCorners,
    val isSelected: Boolean = false,
    val selectedCorner: Corner? = null
) {
  fun center(): Point = corners.center()

  fun width(): Double = corners.width()

  fun height(): Double = corners.height()

  fun aspectRatio(): Double = corners.aspectRatio()

  fun isPortrait(): Boolean = corners.isPortrait()

  fun isSquare(): Boolean = corners.isSquare()

  fun move(deltaX: Double, deltaY: Double): BoundingBox {
    return copy(corners = corners.translated(deltaX, deltaY))
  }

  fun moveCorner(corner: Corner, newPosition: Point): BoundingBox {
    return copy(corners = corners.withCornerMoved(corner, newPosition))
  }

  fun expand(scaleFactor: Double): BoundingBox {
    return copy(corners = corners.expanded(scaleFactor))
  }

  fun rotate(angleDegrees: Double): BoundingBox {
    return copy(corners = corners.rotated(angleDegrees))
  }

  fun select(): BoundingBox = copy(isSelected = true)

  fun deselect(): BoundingBox = copy(isSelected = false, selectedCorner = null)

  fun selectCorner(corner: Corner): BoundingBox {
    return copy(isSelected = true, selectedCorner = corner)
  }

  fun deselectCorner(): BoundingBox = copy(selectedCorner = null)

  fun withCorners(newCorners: BoundingBoxCorners): BoundingBox = copy(corners = newCorners)

  companion object {
    fun createRectangular(center: Point, width: Double, height: Double): BoundingBox {
      return BoundingBox(corners = BoundingBoxCorners.fromCenter(center.x, center.y, width, height))
    }

    fun createRectangular(
        centerX: Double,
        centerY: Double,
        width: Double,
        height: Double
    ): BoundingBox {
      return BoundingBox(corners = BoundingBoxCorners.fromCenter(centerX, centerY, width, height))
    }

    fun fromQuadrilateral(points: List<Point>): BoundingBox {
      require(points.size == 4) { "Exactly 4 points required for quadrilateral" }
      // Compute convex hull to ensure correct ordering
      val sorted = computeConvexHull(points)
      return BoundingBox(corners = BoundingBoxCorners(sorted[0], sorted[1], sorted[2], sorted[3]))
    }

    /**
     * Creates a rectangular bounding box from two diagonal corners.
     * The corners are automatically ordered as top-left, top-right, bottom-right, bottom-left.
     */
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
                  bottomLeft = Point(minX, maxY)))
    }

    /**
     * Compute convex hull using gift wrapping (Jarvis march) algorithm. This ensures points are
     * ordered correctly regardless of click order.
     */
    private fun computeConvexHull(points: List<Point>): List<Point> {
      if (points.size < 3) return points

      // Remove duplicate points
      val uniquePoints = points.distinct()
      if (uniquePoints.size < 3) return uniquePoints

      // Find the leftmost (and lowest if tie) point
      val start = uniquePoints.minByOrNull { point -> point.x } ?: uniquePoints.first()

      val hull = mutableListOf<Point>()
      var current = start

      do {
        hull.add(current)
        var next = uniquePoints[0]

        for (p in uniquePoints) {
          if (next == current || crossProduct(current, next, p) < 0) {
            next = p
          }
        }

        current = next
      } while (current != start && hull.size < uniquePoints.size)

      // If we have less than 4 points, pad to 4 with sorted order
      while (hull.size < 4 && hull.isNotEmpty()) {
        // For quadrilateral, we need exactly 4 points
        // The convex hull of 4 points should have all 4
        break
      }

      // If hull is complete (4 points for quadrilateral), return
      if (hull.size == 4) return hull

      // Otherwise, sort points by angle from center to get proper quad order
      if (hull.size >= 4) {
        val center = Point(hull.map { it.x }.average(), hull.map { it.y }.average())
        return hull.sortedBy { kotlin.math.atan2(it.y - center.y, it.x - center.x) }.take(4)
      }

      // Fallback: sort by position
      return uniquePoints.sortedBy { it.x }.take(4)
    }

    private fun crossProduct(o: Point, a: Point, b: Point): Double {
      return (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
    }
  }
}
