package org.kryspetrie.fileimport.domain.model

import kotlin.math.abs
import kotlin.math.atan2
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.kryspetrie.fileimport.domain.model.CorrectionStrategy.CROP
import org.kryspetrie.fileimport.domain.model.CorrectionStrategy.CROP_AND_ROTATE
import org.kryspetrie.fileimport.domain.model.CorrectionStrategy.PERSPECTIVE

/**
 * Strategy for correcting a detected photo's geometry before export.
 *
 * When detected corners form a nearly-rectangular quadrilateral, applying a full perspective
 * transform adds complexity and edge artifacts for minimal benefit. The correction strategy
 * determines the simplest operation that produces a good result:
 * - [CROP]: Simple axis-aligned crop for nearly-rectangular, unrotated photos
 * - [CROP_AND_ROTATE]: Crop + rotation correction for rectangular but slightly rotated photos
 * - [PERSPECTIVE]: Full 4-point perspective transform for skewed/trapezoidal photos
 */
@Serializable
enum class CorrectionStrategy(val displayName: String, val description: String) {
    @SerialName("crop") CROP("Crop Only", "Axis-aligned crop — best for nearly-rectangular photos"),
    @SerialName("crop_and_rotate")
    CROP_AND_ROTATE("Crop & Rotate", "Crop + rotation — best for slightly rotated photos"),
    @SerialName("perspective")
    PERSPECTIVE("Perspective", "Full perspective transform — best for skewed/trapezoidal photos"),
}

/**
 * Determines the best correction strategy based on corner geometry.
 *
 * A photo is considered nearly-rectangular when:
 * - All corner angles are within [skewThresholdDegrees] of 90°
 * - The average rotation is within [rotationThresholdDegrees] of horizontal
 *
 * @param corners The four corners of the detected photo (TL, TR, BR, BL order)
 * @param rotationThresholdDegrees Maximum average rotation angle before using CROP_AND_ROTATE
 *   (default 1.5°)
 * @param skewThresholdDegrees Maximum corner angle deviation from 90° before using PERSPECTIVE
 *   (default 3.0°)
 * @return The recommended [CorrectionStrategy]
 */
fun determineCorrectionStrategy(
    corners: List<PhotoCorner>,
    rotationThresholdDegrees: Double = 1.5,
    skewThresholdDegrees: Double = 3.0,
): CorrectionStrategy {
    require(corners.size == 4) { "Expected 4 corners, got ${corners.size}" }

    val angles = computeCornerAngles(corners)
    val maxAngleDeviation = angles.map { abs(it - 90.0) }.maxOrNull() ?: 0.0
    val rotation = computeAverageRotation(corners)

    return when {
        maxAngleDeviation > skewThresholdDegrees -> PERSPECTIVE
        rotation > rotationThresholdDegrees -> CROP_AND_ROTATE
        else -> CROP
    }
}

/**
 * Compute the interior angle (in degrees) at each corner of a quadrilateral.
 *
 * Corners are expected in TL, TR, BR, BL order (clockwise). Each angle is measured between the two
 * edges meeting at that corner.
 *
 * @param corners Four corners in TL, TR, BR, BL order
 * @return List of four angles in degrees (0°-180° range for convex quads)
 */
fun computeCornerAngles(corners: List<PhotoCorner>): List<Double> {
    require(corners.size == 4) { "Expected 4 corners, got ${corners.size}" }

    return (0..3).map { i ->
        val prev = corners[(i - 1 + 4) % 4]
        val curr = corners[i]
        val next = corners[(i + 1) % 4]

        val v1x = prev.x - curr.x
        val v1y = prev.y - curr.y
        val v2x = next.x - curr.x
        val v2y = next.y - curr.y

        // Using atan2 for signed angle; take absolute value for interior angle
        val dot = v1x * v2x + v1y * v2y
        val cross = v1x * v2y - v1y * v2x
        val angle = atan2(abs(cross.toDouble()), dot.toDouble()) * 180.0 / Math.PI

        // For convex quads with CW ordering, the interior angle is:
        // If cross > 0 (right turn), angle is the interior angle directly
        // If cross < 0 (left turn for CW), angle = 180 - computed angle
        // However, for nearly-rectangular photos, angles are near 90° either way.
        // Simplest: use the angle from atan2 directly (0-180° range)
        angle.coerceIn(0.0, 180.0)
    }
}

/**
 * Compute the average rotation of a quadrilateral from horizontal.
 *
 * Measures the angle of the top and bottom edges from horizontal and averages them. A perfectly
 * horizontal rectangle returns 0.0°.
 *
 * @param corners Four corners in TL, TR, BR, BL order
 * @return Average rotation magnitude in degrees (always positive)
 */
fun computeAverageRotation(corners: List<PhotoCorner>): Double {
    require(corners.size == 4) { "Expected 4 corners, got ${corners.size}" }

    // Corners in TL(0), TR(1), BR(2), BL(3) order
    // Top edge: TL → TR (left-to-right, corners[0]→corners[1])
    // Bottom edge: BL → BR (left-to-right, corners[3]→corners[2])

    val topEdgeAngle =
        atan2((corners[1].y - corners[0].y).toDouble(), (corners[1].x - corners[0].x).toDouble()) *
            180.0 / Math.PI

    val bottomEdgeAngle =
        atan2((corners[2].y - corners[3].y).toDouble(), (corners[2].x - corners[3].x).toDouble()) *
            180.0 / Math.PI

    return abs((topEdgeAngle + bottomEdgeAngle) / 2.0)
}
