package org.kryspetrie.fileimport.domain.model

/**
 * Result of orientation angle detection for an image.
 *
 * @property orientationDegrees How much the image is rotated **clockwise from upright** (0°–359.9°).
 *   For example, if the image was turned 90° clockwise, this value will be near 90°.
 * @property confidence Detection confidence in the range [0.0, 1.0]. Higher values indicate the
 *   model is more certain that the orientation is close to a 90° boundary (0°, 90°, 180°, 270°).
 *   Values above 0.7 are generally reliable.
 * @property nearestRotation The nearest discrete [RotationAngle] that would **correct** the image
 *   orientation. This is derived from [correctionDegrees], not [orientationDegrees]. For example,
 *   if the image is rotated 90° CW, the correction is 270° CW ≡ 90° CCW, so this will be
 *   [RotationAngle.CCW_90].
 * @property correctionDegrees How many degrees clockwise to rotate the image to correct its
 *   orientation. This is the model's raw output (normalized to [0, 360)). For example, if the
 *   image is oriented 90° CW from upright, this value will be near 270° (rotate 270° CW = 90° CCW
 *   to make it upright).
 */
data class OrientationResult(
    val orientationDegrees: Float,
    val confidence: Float,
    val nearestRotation: RotationAngle,
    val correctionDegrees: Float,
)