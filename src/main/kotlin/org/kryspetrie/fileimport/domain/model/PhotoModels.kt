package org.kryspetrie.fileimport.domain.model

import kotlin.math.sqrt
import kotlinx.serialization.Serializable

/**
 * Point represented as percentage of image dimensions (0.0-100.0).
 *
 * This provides resolution-independent coordinate representation.
 *
 * @property x X coordinate as percentage (0.0 to 100.0)
 * @property y Y coordinate as percentage (0.0 to 100.0)
 */
@Serializable
data class PercentPoint(val x: Double, val y: Double) {
    companion object {
        /** Create from pixel coordinates and image dimensions */
        fun fromPixels(x: Float, y: Float, imageWidth: Int, imageHeight: Int): PercentPoint {
            return PercentPoint(
                x = (x / imageWidth * 100.0).coerceIn(0.0, 100.0),
                y = (y / imageHeight * 100.0).coerceIn(0.0, 100.0),
            )
        }

        /** Create from double pixel coordinates */
        fun fromPixels(x: Double, y: Double, imageWidth: Int, imageHeight: Int): PercentPoint {
            return PercentPoint(
                x = (x / imageWidth * 100.0).coerceIn(0.0, 100.0),
                y = (y / imageHeight * 100.0).coerceIn(0.0, 100.0),
            )
        }
    }

    /** Convert to pixel coordinates */
    fun toPixels(imageWidth: Int, imageHeight: Int): PixelPoint {
        return PixelPoint(
            x = (x / 100.0 * imageWidth).toFloat(),
            y = (y / 100.0 * imageHeight).toFloat(),
        )
    }
}

/**
 * Point in pixel coordinates.
 *
 * @property x X coordinate in pixels
 * @property y Y coordinate in pixels
 */
/** Backward-compatible alias — [PhotoCorner] is the canonical pixel-coordinate point. */
typealias PixelPoint = PhotoCorner

/**
 * Bounding box corners as percentages of image dimensions.
 *
 * Provides resolution-independent representation of a quadrilateral boundary.
 *
 * @property topLeft Top-left corner
 * @property topRight Top-right corner
 * @property bottomLeft Bottom-left corner
 * @property bottomRight Bottom-right corner
 */
@Serializable
data class PercentBoundingBox(
    val topLeft: PercentPoint,
    val topRight: PercentPoint,
    val bottomLeft: PercentPoint,
    val bottomRight: PercentPoint,
) {
    /** Get all corners as a list */
    val corners: List<PercentPoint>
        get() = listOf(topLeft, topRight, bottomLeft, bottomRight)

    /** Get the center point of the bounding box */
    val center: PercentPoint
        get() {
            val centerX = (topLeft.x + topRight.x + bottomLeft.x + bottomRight.x) / 4.0
            val centerY = (topLeft.y + topRight.y + bottomLeft.y + bottomRight.y) / 4.0
            return PercentPoint(centerX, centerY)
        }

    /** Calculate axis-aligned bounding box */
    val axisAlignedBounds: PercentBounds
        get() {
            val minX = minOf(topLeft.x, topRight.x, bottomLeft.x, bottomRight.x)
            val maxX = maxOf(topLeft.x, topRight.x, bottomLeft.x, bottomRight.x)
            val minY = minOf(topLeft.y, topRight.y, bottomLeft.y, bottomRight.y)
            val maxY = maxOf(topLeft.y, topRight.y, bottomLeft.y, bottomRight.y)
            return PercentBounds(minX, maxX, minY, maxY)
        }

    /**
     * Find the nearest corner to a given point.
     *
     * @param point The point to test
     * @param tolerance Maximum distance (in percentage points) to still consider a match
     * @return The CornerType of the nearest corner, or null if no corner is within tolerance
     */
    fun findNearestCorner(point: PercentPoint, tolerance: Double = 5.0): CornerType? {
        val distances =
            mapOf(
                CornerType.TOP_LEFT to distance(topLeft, point),
                CornerType.TOP_RIGHT to distance(topRight, point),
                CornerType.BOTTOM_LEFT to distance(bottomLeft, point),
                CornerType.BOTTOM_RIGHT to distance(bottomRight, point),
            )

        val nearest = distances.minByOrNull { it.value }
        return if (nearest != null && nearest.value <= tolerance) nearest.key else null
    }

    /**
     * Check if a point is inside the bounding box.
     *
     * Uses a simple axis-aligned bounding box check.
     */
    fun isPointInside(point: PercentPoint): Boolean {
        val bounds = axisAlignedBounds
        return point.x >= bounds.minX &&
            point.x <= bounds.maxX &&
            point.y >= bounds.minY &&
            point.y <= bounds.maxY
    }

    /** Translate the entire bounding box by a delta. */
    fun translate(deltaX: Double, deltaY: Double): PercentBoundingBox {
        return PercentBoundingBox(
            topLeft = PercentPoint(topLeft.x + deltaX, topLeft.y + deltaY),
            topRight = PercentPoint(topRight.x + deltaX, topRight.y + deltaY),
            bottomLeft = PercentPoint(bottomLeft.x + deltaX, bottomLeft.y + deltaY),
            bottomRight = PercentPoint(bottomRight.x + deltaX, bottomRight.y + deltaY),
        )
    }

    /** Move a specific corner to a new position. */
    fun moveCorner(corner: CornerType, newPosition: PercentPoint): PercentBoundingBox {
        val clampedPosition =
            PercentPoint(
                x = newPosition.x.coerceIn(0.0, 100.0),
                y = newPosition.y.coerceIn(0.0, 100.0),
            )

        return when (corner) {
            CornerType.TOP_LEFT -> copy(topLeft = clampedPosition)
            CornerType.TOP_RIGHT -> copy(topRight = clampedPosition)
            CornerType.BOTTOM_LEFT -> copy(bottomLeft = clampedPosition)
            CornerType.BOTTOM_RIGHT -> copy(bottomRight = clampedPosition)
            CornerType.CENTER -> this // Use translate for center move
        }
    }

    /** Calculate the average length of the longer edges (for aspect ratio determination). */
    fun averageLongEdgeLength(): Double {
        val topEdge = distance(topLeft, topRight)
        val bottomEdge = distance(bottomLeft, bottomRight)
        val leftEdge = distance(topLeft, bottomLeft)
        val rightEdge = distance(topRight, bottomRight)

        // Average of the two longer edges
        val longEdges = listOf(topEdge, bottomEdge, leftEdge, rightEdge).sorted()
        return (longEdges[2] + longEdges[3]) / 2.0
    }

    /** Calculate the average length of the shorter edges. */
    fun averageShortEdgeLength(): Double {
        val topEdge = distance(topLeft, topRight)
        val bottomEdge = distance(bottomLeft, bottomRight)
        val leftEdge = distance(topLeft, bottomLeft)
        val rightEdge = distance(topRight, bottomRight)

        val shortEdges = listOf(topEdge, bottomEdge, leftEdge, rightEdge).sorted()
        return (shortEdges[0] + shortEdges[1]) / 2.0
    }

    private companion object {
        fun distance(a: PercentPoint, b: PercentPoint): Double {
            val dx = b.x - a.x
            val dy = b.y - a.y
            return sqrt(dx * dx + dy * dy)
        }
    }
}

/** Axis-aligned bounds as percentages. */
data class PercentBounds(val minX: Double, val maxX: Double, val minY: Double, val maxY: Double) {
    val width: Double
        get() = maxX - minX

    val height: Double
        get() = maxY - minY

    val aspectRatio: Double
        get() = if (height > 0) width / height else 1.0
}

/** Corner position for editing. */
enum class CornerType {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    CENTER, // For dragging entire bounding box
}

/** Precision level for original date. */
enum class OriginalDatePrecision {
    YEAR,
    YEAR_MONTH,
    YEAR_MONTH_DAY,
}

/** Metadata for a photo. */
@Serializable
data class PhotoMetadata(
    val notes: String? = null,
    val dateTaken: String? = null, // ISO format
    val originalDate: String? = null,
    val originalDatePrecision: OriginalDatePrecision? = null,
    val tags: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
    val photographer: String? = null,
    val subjects: List<String> = emptyList(),
) {
    fun toMap(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        notes?.let { result["notes"] = it }
        dateTaken?.let { result["dateTaken"] = it }
        originalDate?.let { result["originalDate"] = it }
        if (tags.isNotEmpty()) result["tags"] = tags.joinToString(", ")
        if (keywords.isNotEmpty()) result["keywords"] = keywords.joinToString(", ")
        photographer?.let { result["photographer"] = it }
        if (subjects.isNotEmpty()) result["subjects"] = subjects.joinToString(", ")
        return result
    }
}
