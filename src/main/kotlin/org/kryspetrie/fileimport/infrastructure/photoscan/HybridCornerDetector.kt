package org.kryspetrie.fileimport.infrastructure.photoscan

import java.awt.image.BufferedImage
import kotlin.math.max
import kotlin.math.min
import org.kryspetrie.fileimport.domain.model.DetectedPhoto
import org.kryspetrie.fileimport.domain.model.PhotoCorner

/**
 * Corner detector using edge-based classical computer vision.
 *
 * ## Approach
 * 1. **Edge-based detection** — [RectangleDetector] uses adaptive threshold + contour tracing +
 *    Douglas-Peucker simplification + angle filtering. This finds photo boundaries by detecting
 *    edges, even when photos have similar brightness to the background.
 * 2. **Constraint filtering** — applies domain knowledge to eliminate false positives:
 *     - **Whole-image rejection**: regions covering >80% of image area are false positives (the
 *       "entire desk" being detected as a photo)
 *     - **Minimum area filter**: eliminates tiny false positives that are clearly too small to be
 *       real photos
 *     - **Overlap-based suppression**: if a smaller detection overlaps significantly (>40%) with a
 *       much larger (>40% bigger) kept detection, it's likely a fragment of the larger one
 *     - **Count target**: max 4 photos unless user provides explicit count
 *
 * ## Why edge-based over threshold-based?
 *
 * Threshold-based approaches (adaptive background estimation) classify pixels as "content" if they
 * differ from the estimated background. This fails when the photo content spans a wide brightness
 * range (dark B&W photos AND bright white borders on the same photo), causing the bright borders to
 * merge with the bright desk.
 *
 * Edge-based approaches detect boundaries instead of content. They find contours by looking for
 * intensity transitions, which works regardless of whether photos are brighter or darker than the
 * background.
 *
 * @param rectangleDetector Edge-based rectangle detector
 * @param wholeImageThreshold Region covering more than this fraction of image area is rejected
 *   (default 0.80)
 */
class HybridCornerDetector(
    private val rectangleDetector: RectangleDetector,
    private val wholeImageThreshold: Float = 0.80f,
) {

    /** Mutable target count. Set by callers who know the expected photo count. */
    var targetPhotoCount: Int? = null

    /**
     * Detects photo regions and corners in a scanned image.
     *
     * @param image The scanned image
     * @return [DetectedPhoto] objects with corners ordered TL→TR→BR→BL.
     */
    @Suppress("ReturnCount")
    fun detectPhotos(image: BufferedImage): List<DetectedPhoto> {
        val imgWidth = image.width.toFloat()
        val imgHeight = image.height.toFloat()
        val imageArea = imgWidth * imgHeight

        // Step 1: Edge-based region proposals
        val raw = rectangleDetector.detectRectangles(image, expectedCount = targetPhotoCount ?: 4)
        if (raw.isEmpty()) return emptyList()

        // Step 2: Whole-image filter — reject regions covering >80% of image (the "entire desk"
        // false
        // positive)
        val notWholeImage =
            raw.filter { quad: DetectedQuadrilateral ->
                val b = quadBounds(quad)
                val area = b.width.toFloat() * b.height
                area / imageArea < wholeImageThreshold
            }

        val candidates =
            if (notWholeImage.isEmpty()) {
                raw.filter { quad ->
                        val b = quadBounds(quad)
                        b.width > 50 && b.height > 50
                    }
                    .take(4)
            } else {
                notWholeImage
            }

        if (candidates.isEmpty()) return emptyList()

        // Step 3: Limit to max photos (sort by area, largest first)
        val maxPhotos = if (targetPhotoCount != null) maxOf(1, targetPhotoCount!!) else 4
        val limited =
            candidates
                .sortedByDescending { q ->
                    val b = quadBounds(q)
                    b.width.toLong() * b.height
                }
                .take(maxPhotos)

        // Step 4: NMS — suppresses overlapping detections.
        // NMS keeps the largest detection and suppresses smaller overlapping ones.
        val afterNms = overlapSuppress(limited)

        // Step 5: Absolute minimum area filter — on the NMS result.
        // Eliminates tiny false positives that slipped through NMS.
        // Threshold 100K at downsampled scale: cleanly eliminates detections that are clearly
        // too small to be real photos, while keeping all valid detections.
        val minAreaAtDownsampled = 100_000
        val afterAreaFilter =
            afterNms.filter { quad: DetectedQuadrilateral ->
                val b = quadBounds(quad)
                b.width.toLong() * b.height >= minAreaAtDownsampled
            }
        val finalSet = if (afterAreaFilter.isEmpty()) afterNms else afterAreaFilter

        // Step 6: Build DetectedPhoto from each quadrilateral
        return finalSet.map { quad -> buildDetectedPhoto(image, quad) }
    }

    /**
     * Overlap-based suppression: keeps detections that don't significantly overlap with much larger
     * detections.
     *
     * Strategy: sort by area (largest first), keep a candidate only if for every already-kept
     * detection, either (a) the overlap is small, or (b) the kept detection is not much larger.
     * This correctly handles:
     * - photo-scan-01: two similar-size detections (ratio=2.73) → both kept (they don't overlap
     *   much)
     * - photo-scan-02: real photos are different sizes → all kept (each overlaps more with its
     *   neighbor)
     * - Small false positives that sit inside/overlap a much larger detection → suppressed
     */
    private fun overlapSuppress(quads: List<DetectedQuadrilateral>): List<DetectedQuadrilateral> {
        if (quads.size <= 1) return quads

        val sorted =
            quads.sortedByDescending { q ->
                val b = quadBounds(q)
                b.width.toLong() * b.height
            }

        val kept = mutableListOf<DetectedQuadrilateral>()
        for (candidate in sorted) {
            val candidateBounds = quadBounds(candidate)
            val candidateArea = candidateBounds.width.toFloat() * candidateBounds.height

            var suppress = false
            for (keptQuad in kept) {
                val keptBounds = quadBounds(keptQuad)
                val keptArea = keptBounds.width.toFloat() * keptBounds.height

                // How much does the kept detection "cover" the candidate?
                val overlap = axisAlignedOverlap(candidateBounds, keptBounds)
                val overlapRatio = if (candidateArea > 0) overlap / candidateArea else 0f

                // If the candidate overlaps significantly (>40%) with a much larger (>40% bigger)
                // kept
                // detection,
                // suppress the candidate. The candidate is likely a fragment of the larger
                // detection.
                if (overlapRatio > 0.4f && keptArea > candidateArea * 1.4f) {
                    suppress = true
                    break
                }
            }

            if (!suppress) {
                kept.add(candidate)
            }
        }
        return kept
    }

    private fun axisAlignedOverlap(a: AaBounds, b: AaBounds): Float {
        val ix1 = max(a.minX.toFloat(), b.minX.toFloat())
        val iy1 = max(a.minY.toFloat(), b.minY.toFloat())
        val ix2 = min(a.maxX.toFloat(), b.maxX.toFloat())
        val iy2 = min(a.maxY.toFloat(), b.maxY.toFloat())
        return max(0f, ix2 - ix1) * max(0f, iy2 - iy1)
    }

    private fun quadBounds(quad: DetectedQuadrilateral): AaBounds {
        val xs = quad.corners.map { it.x }
        val ys = quad.corners.map { it.y }
        return AaBounds(minX = xs.min(), minY = ys.min(), maxX = xs.max(), maxY = ys.max())
    }

    private data class AaBounds(val minX: Int, val minY: Int, val maxX: Int, val maxY: Int) {
        val width
            get() = maxX - minX

        val height
            get() = maxY - minY
    }

    /** Builds a DetectedPhoto from a quadrilateral. */
    @Suppress("UnusedParameter")
    private fun buildDetectedPhoto(
        image: BufferedImage,
        quad: DetectedQuadrilateral,
    ): DetectedPhoto {
        val corners = quad.corners
        return DetectedPhoto(
            topLeft = PhotoCorner(corners[0].x.toFloat(), corners[0].y.toFloat()),
            topRight = PhotoCorner(corners[1].x.toFloat(), corners[1].y.toFloat()),
            bottomRight = PhotoCorner(corners[2].x.toFloat(), corners[2].y.toFloat()),
            bottomLeft = PhotoCorner(corners[3].x.toFloat(), corners[3].y.toFloat()),
        )
    }
}
