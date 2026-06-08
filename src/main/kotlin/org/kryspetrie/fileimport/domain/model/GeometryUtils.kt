package org.kryspetrie.fileimport.domain.model

import kotlin.math.sqrt

/**
 * Shared geometry utilities for operating on [DetectedPhoto] corners.
 *
 * These functions centralize margin and distance calculations that are used across multiple
 * application-layer services.
 */
object GeometryUtils {

    /**
     * Applies margin to a detected photo's corners, pushing them outward from the quad center.
     *
     * This mirrors the margin logic in photocrop.py: each corner is expanded outward along the
     * direction from the quad center to that corner. The margin is computed as a fraction of the
     * photo's diagonal length.
     *
     * @param photo The detected photo
     * @param marginFraction Margin as fraction of the photo's diagonal (e.g. 0.02 = 2%)
     * @return New DetectedPhoto with corners pushed outward, or the same photo if margin is 0
     */
    fun applyMargin(photo: DetectedPhoto, marginFraction: Double): DetectedPhoto {
        if (marginFraction <= 0.0) return photo

        val corners =
            listOf(
                photo.topLeft.x.toDouble() to photo.topLeft.y.toDouble(),
                photo.topRight.x.toDouble() to photo.topRight.y.toDouble(),
                photo.bottomRight.x.toDouble() to photo.bottomRight.y.toDouble(),
                photo.bottomLeft.x.toDouble() to photo.bottomLeft.y.toDouble(),
            )

        // Quad center (centroid of the 4 corners)
        val cx = corners.map { it.first }.average()
        val cy = corners.map { it.second }.average()

        // Diagonal length of the quad (max opposite-corner distance)
        val diag1 = distance(corners[0], corners[2]) // TL to BR
        val diag2 = distance(corners[1], corners[3]) // TR to BL
        val diagonal = maxOf(diag1, diag2)

        if (diagonal <= 0.0) return photo

        val marginPx = marginFraction * diagonal

        // Push each corner outward from center
        val expanded =
            corners.map { (x, y) ->
                val dx = x - cx
                val dy = y - cy
                val dist = sqrt(dx * dx + dy * dy)
                if (dist > 0) {
                    (x + (marginPx / dist) * dx) to (y + (marginPx / dist) * dy)
                } else {
                    x to y
                }
            }

        return photo.copy(
            topLeft = PhotoCorner(expanded[0].first.toFloat(), expanded[0].second.toFloat()),
            topRight = PhotoCorner(expanded[1].first.toFloat(), expanded[1].second.toFloat()),
            bottomRight = PhotoCorner(expanded[2].first.toFloat(), expanded[2].second.toFloat()),
            bottomLeft = PhotoCorner(expanded[3].first.toFloat(), expanded[3].second.toFloat()),
        )
    }

    /** Euclidean distance between two points represented as [Pair]<[Double], [Double]>. */
    fun distance(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val dx = b.first - a.first
        val dy = b.second - a.second
        return sqrt(dx * dx + dy * dy)
    }
}
