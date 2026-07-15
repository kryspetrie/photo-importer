package org.kryspetrie.fileimport.domain.model

/**
 * Result of orientation angle detection for an image.
 *
 * @property angleDegrees The detected orientation angle in degrees clockwise (0–359.9°).
 *   To correct the image, it should be rotated by the negation of this angle, or equivalently,
 *   the nearest 90° increment maps to a [RotationAngle] that would correct the orientation.
 * @property confidence Detection confidence in the range [0.0, 1.0]. Values above 0.7 are
 *   generally reliable for 4-class (0/90/180/270) classification.
 * @property nearestRotation The nearest discrete [RotationAngle] that would correct the image
 *   orientation. For example, if [angleDegrees] is 273°, the image is upside-down and
 *   [nearestRotation] is [RotationAngle.CW_180].
 */
data class OrientationResult(
    val angleDegrees: Float,
    val confidence: Float,
    val nearestRotation: RotationAngle,
)