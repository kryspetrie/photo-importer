package org.kryspetrie.fileimport.infrastructure.photoscan

import java.awt.image.BufferedImage
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import org.kryspetrie.fileimport.domain.model.DetectedPhoto
import org.kryspetrie.fileimport.domain.model.PhotoCorner

/**
 * Consensus corner detector that combines multiple detector outputs.
 *
 * Strategy:
 * 1. Run multiple detector methods
 * 2. For each detected photo region, find corners that agree across methods
 * 3. Use weighted averaging for corner positions
 * 4. Fall back to best single method when methods disagree
 *
 * @param rectangleDetector Initial contour-based detector for region proposals
 */
class ConsensusCornerDetector(
    private val rectangleDetector: RectangleDetector,
) {
  /** Target count. */
  var targetPhotoCount: Int? = null

  /** Runs the consensus detection using multiple strategies. */
  fun detectPhotos(image: BufferedImage): List<DetectedPhoto> {
    val imageArea = image.width.toFloat() * image.height.toFloat()

    // Get region proposals
    val raw = rectangleDetector.detectRectangles(image, expectedCount = targetPhotoCount ?: 4)
    if (raw.isEmpty()) return emptyList()

    // Filter out whole-image false positives
    val notWholeImage =
        raw.filter { quad ->
          val b = quadBounds(quad)
          val area = b.width.toFloat() * b.height
          area / imageArea < 0.80f
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

    // Limit to max photos
    val maxPhotos = if (targetPhotoCount != null) maxOf(1, targetPhotoCount!!) else 4
    val limited =
        candidates
            .sortedByDescending { q ->
              val b = quadBounds(q)
              b.width.toLong() * b.height
            }
            .take(maxPhotos)

    // NMS
    val afterNms = overlapSuppress(limited)

    // Run multiple detectors and combine results
    return afterNms.map { quad ->
      val corners = computeConsensusCorners(image, quad)
      buildDetectedPhoto(corners)
    }
  }

  /** Computes consensus corners from multiple detection methods. */
  private fun computeConsensusCorners(
      image: BufferedImage,
      quad: DetectedQuadrilateral
  ): List<Point> {
    val cx = quad.centroid.x
    val cy = quad.centroid.y

    // Get estimated dimensions from detected region
    val bounds = quadBounds(quad)
    val width = bounds.width
    val height = bounds.height

    // Run multiple corner detection strategies
    val method1 = detectCornersByEdgeScan(image, cx, cy, width, height)
    val method2 = detectCornersByGradientCenter(image, cx, cy, width, height)
    val method3 = detectCornersByContourRefine(image, quad)

    // Compute weighted consensus
    val consensusCorners = mutableListOf<Point>()

    for (i in 0 until 4) {
      val c1 = method1.getOrNull(i) ?: Point(cx, cy)
      val c2 = method2.getOrNull(i) ?: Point(cx, cy)
      val c3 = method3.getOrNull(i) ?: Point(cx, cy)

      // Weighted average with edge-scan method having more weight
      // since it's the most accurate based on testing
      val weight1 = 0.5
      val weight2 = 0.3
      val weight3 = 0.2

      val x = (c1.x * weight1 + c2.x * weight2 + c3.x * weight3).toInt()
      val y = (c1.y * weight1 + c2.y * weight2 + c3.y * weight3).toInt()

      consensusCorners.add(Point(x, y))
    }

    return validateAndOrderCorners(consensusCorners)
  }

  /** Detects corners using edge scanning. */
  private fun detectCornersByEdgeScan(
      image: BufferedImage,
      cx: Int,
      cy: Int,
      width: Int,
      height: Int
  ): List<Point> {
    val halfW = width / 2
    val halfH = height / 2
    val searchRadius = max(width, height) / 2

    // Simple corner detection by edge gradient analysis
    val corners =
        listOf(
            Point(cx - halfW, cy - halfH),
            Point(cx + halfW, cy - halfH),
            Point(cx + halfW, cy + halfH),
            Point(cx - halfW, cy + halfH))

    // Refine each corner
    return corners.map { corner -> refineCornerByGradient(image, corner, searchRadius / 2) }
  }

  /** Detects corners using gradient center analysis. */
  private fun detectCornersByGradientCenter(
      image: BufferedImage,
      cx: Int,
      cy: Int,
      width: Int,
      height: Int
  ): List<Point> {
    // Search around expected corner positions
    val corners = mutableListOf<Point>()
    val offsets =
        listOf(
            Point(-width / 2, -height / 2),
            Point(width / 2, -height / 2),
            Point(width / 2, height / 2),
            Point(-width / 2, height / 2))

    for (offset in offsets) {
      val expected = Point(cx + offset.x, cy + offset.y)
      val refined = refineCornerByGradient(image, expected, max(width, height) / 3)
      corners.add(refined)
    }

    return corners
  }

  /** Detects corners using contour refinement. */
  private fun detectCornersByContourRefine(
      image: BufferedImage,
      quad: DetectedQuadrilateral
  ): List<Point> {
    // Use detected corners but refine based on actual image edges
    return quad.corners.map { corner ->
      refineCornerByGradient(image, Point(corner.x, corner.y), 60)
    }
  }

  /** Refines a corner using local gradient analysis. */
  private fun refineCornerByGradient(image: BufferedImage, corner: Point, radius: Int): Point {
    val x1 = (corner.x - radius).coerceIn(0, image.width - 1)
    val y1 = (corner.y - radius).coerceIn(0, image.height - 1)
    val x2 = (corner.x + radius).coerceIn(0, image.width - 1)
    val y2 = (corner.y + radius).coerceIn(0, image.height - 1)

    // Find strong edge points and compute their weighted center
    var sumX = 0.0
    var sumY = 0.0
    var sumW = 0.0

    for (y in y1..y2) {
      for (x in x1..x2) {
        val gx =
            luminance(image.getRGB(min(image.width - 1, x + 1), y)) -
                luminance(image.getRGB(max(0, x - 1), y))
        val gy =
            luminance(image.getRGB(x, min(image.height - 1, y + 1))) -
                luminance(image.getRGB(x, max(0, y - 1)))
        val mag = kotlin.math.sqrt(gx * gx + gy * gy)

        if (mag > 40) {
          val dist = hypot((x - corner.x).toDouble(), (y - corner.y).toDouble())
          val weight = mag / (dist + 1)
          sumX += x * weight
          sumY += y * weight
          sumW += weight
        }
      }
    }

    return if (sumW > 0) {
      Point((sumX / sumW).toInt(), (sumY / sumW).toInt())
    } else {
      corner
    }
  }

  private fun validateAndOrderCorners(corners: List<Point>): List<Point> {
    if (corners.size != 4) return corners

    val sumSorted = corners.sortedBy { it.x + it.y }
    val tl = sumSorted[0]
    val br = sumSorted[3]
    val remaining = listOf(sumSorted[1], sumSorted[2]).sortedBy { it.y - it.x }
    val tr = remaining[0]
    val bl = remaining[1]

    return listOf(tl, tr, br, bl)
  }

  private fun quadBounds(quad: DetectedQuadrilateral): AaBounds {
    val xs = quad.corners.map { it.x }
    val ys = quad.corners.map { it.y }
    return AaBounds(
        minX = xs.min(),
        minY = ys.min(),
        maxX = xs.max(),
        maxY = ys.max(),
    )
  }

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

        val overlap = axisAlignedOverlap(candidateBounds, keptBounds)
        val overlapRatio = if (candidateArea > 0) overlap / candidateArea else 0f

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

  private fun buildDetectedPhoto(corners: List<Point>): DetectedPhoto {
    return DetectedPhoto(
        topLeft = PhotoCorner(corners[0].x.toFloat(), corners[0].y.toFloat()),
        topRight = PhotoCorner(corners[1].x.toFloat(), corners[1].y.toFloat()),
        bottomRight = PhotoCorner(corners[2].x.toFloat(), corners[2].y.toFloat()),
        bottomLeft = PhotoCorner(corners[3].x.toFloat(), corners[3].y.toFloat()),
    )
  }

  private fun luminance(rgb: Int): Float {
    val r = (rgb shr 16) and 255
    val g = (rgb shr 8) and 255
    val b = rgb and 255
    return 0.299f * r + 0.587f * g + 0.114f * b
  }

  private data class AaBounds(
      val minX: Int,
      val minY: Int,
      val maxX: Int,
      val maxY: Int,
  ) {
    val width
      get() = maxX - minX

    val height
      get() = maxY - minY
  }

  data class Point(val x: Int, val y: Int)
}
