package org.kryspetrie.fileimport.ui.wizard.state

import kotlin.math.abs
import org.kryspetrie.fileimport.domain.model.AspectRatio

/**
 * Handles aspect ratio selection and flipping based on detected photo orientation. Ensures aspect
 * ratio is applied in the correct direction for portrait/landscape photos.
 */
object AspectRatioHandler {

    /**
     * Gets the output aspect ratio based on detected orientation and selected ratio. Flips the
     * ratio if detected orientation doesn't match the natural orientation of the selected ratio.
     *
     * @param detectedWidth Width of detected bounding box
     * @param detectedHeight Height of detected bounding box
     * @param selectedRatio The selected aspect ratio value (e.g., 1.5 for 3:2)
     * @param threshold Threshold for square detection (default 0.1)
     * @return The effective output ratio to use
     */
    fun getOutputAspectRatio(
        detectedWidth: Double,
        detectedHeight: Double,
        selectedRatio: Double,
        threshold: Double = 0.1,
    ): Double {
        // Handle "Current" (no snapping)
        if (selectedRatio == 0.0) {
            return detectedWidth / detectedHeight
        }

        // Handle square detection
        val aspectRatio = detectedWidth / detectedHeight
        if (abs(aspectRatio - 1.0) < threshold) {
            return 1.0 // Always 1:1 for square
        }

        // Determine detected orientation
        val isDetectedPortrait = detectedHeight > detectedWidth

        // Determine selected ratio's natural orientation
        val isSelectedPortrait = selectedRatio < 1.0

        // If orientations match, use as-is
        if (isDetectedPortrait == isSelectedPortrait) {
            return selectedRatio
        }

        // Different orientations - flip the ratio
        return 1.0 / selectedRatio
    }

    /** Determines if detected box is portrait orientation. */
    fun isPortrait(
        detectedWidth: Double,
        detectedHeight: Double,
        threshold: Double = 0.1,
    ): Boolean {
        val aspectRatio = detectedWidth / detectedHeight
        if (abs(aspectRatio - 1.0) < threshold) {
            return false // Treat as landscape for square
        }
        return detectedHeight > detectedWidth
    }

    /** Determines if detected box is square (within threshold). */
    fun isSquare(detectedWidth: Double, detectedHeight: Double, threshold: Double = 0.1): Boolean {
        val aspectRatio = detectedWidth / detectedHeight
        return abs(aspectRatio - 1.0) < threshold
    }

    /**
     * Auto-selects the closest standard aspect ratio to the detected ratio.
     *
     * @param detectedAspectRatio The calculated aspect ratio from detected corners
     * @return The closest AspectRatio enum value
     */
    fun autoSelectClosest(detectedAspectRatio: Double): AspectRatio {
        // Handle square
        if (isSquare(1.0, detectedAspectRatio)) {
            return AspectRatio.SQUARE
        }

        // Find closest standard ratio (excluding ORIGINAL)
        return AspectRatio.entries
            .filter { it !== AspectRatio.ORIGINAL }
            .minByOrNull { abs(it.value - detectedAspectRatio) }
            ?: AspectRatio.LANDSCAPE_3_2 // Default to 3:2
    }

    /** Gets the display label for a ratio value. */
    fun getLabelForRatio(ratio: Double): String {
        if (ratio == 0.0) return AspectRatio.ORIGINAL.displayName

        return AspectRatio.entries
            .filter { it !== AspectRatio.ORIGINAL }
            .minByOrNull { abs(it.value - ratio) }
            ?.displayName ?: "${ratio.toInt()}:${(1.0 / ratio * 100).toInt()}"
    }

    /** Returns all available aspect ratio options as pairs of (ratio, label). */
    fun getAvailableRatios(): List<Pair<Double, String>> {
        return AspectRatio.entries.map { Pair(it.value, it.displayName) }
    }
}
