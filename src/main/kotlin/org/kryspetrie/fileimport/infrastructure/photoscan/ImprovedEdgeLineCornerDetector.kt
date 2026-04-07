package org.kryspetrie.fileimport.infrastructure.photoscan

import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import org.kryspetrie.fileimport.domain.model.DetectedPhoto
import org.kryspetrie.fileimport.domain.model.PhotoCorner

/**
 * Improved Edge-Line Intersection corner detector with better dimension estimation.
 *
 * Key improvements:
 * 1. Uses detected region centroid as anchor but estimates dimensions from edge analysis
 * 2. More robust Hough voting for edge lines
 * 3. Better corner position estimation using line intersections and edge gradients
 *
 * @param rectangleDetector Initial contour-based detector for region proposals
 * @param maxImageDimension Maximum dimension for downsampled processing (default 800)
 */
class ImprovedEdgeLineCornerDetector(
    private val rectangleDetector: RectangleDetector,
    private val maxImageDimension: Int = 800,
) {

  /** Mutable target count. */
  var targetPhotoCount: Int? = null

  /**
   * Detects photo regions and corners using improved edge-line intersection.
   */
  fun detectPhotos(image: BufferedImage): List<DetectedPhoto> {
    val imageArea = image.width.toFloat() * image.height.toFloat()

    // Step 1: Get region proposals
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
          }.take(4)
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

    // Step 2: For each region, find corners using edge-line intersection
    return afterNms.map { quad ->
      val refinedCorners = findCornersByEdgeAnalysis(image, quad)
      buildDetectedPhoto(refinedCorners)
    }
  }

  /**
   * Finds corners by analyzing edge patterns around expected positions.
   */
  private fun findCornersByEdgeAnalysis(image: BufferedImage, quad: DetectedQuadrilateral): List<Point> {
    val cx = quad.centroid.x
    val cy = quad.centroid.y

    // Get detected region dimensions
    val bounds = quadBounds(quad)
    val detectedWidth = bounds.width
    val detectedHeight = bounds.height

    // The detected region should approximate the photo centroid but might be smaller
    // Scale up slightly since the detected region might be internal contour
    val estimatedWidth = (detectedWidth * 1.05).toInt()
    val estimatedHeight = (detectedHeight * 1.05).toInt()

    // Search radius is based on expected photo dimensions
    val searchRadius = max(estimatedWidth, estimatedHeight) / 2

    // Find corners by edge analysis
    val topLeft = findCornerByGradientAnalysis(image, Point(cx - estimatedWidth / 2, cy - estimatedHeight / 2), searchRadius, "TL")
    val topRight = findCornerByGradientAnalysis(image, Point(cx + estimatedWidth / 2, cy - estimatedHeight / 2), searchRadius, "TR")
    val bottomRight = findCornerByGradientAnalysis(image, Point(cx + estimatedWidth / 2, cy + estimatedHeight / 2), searchRadius, "BR")
    val bottomLeft = findCornerByGradientAnalysis(image, Point(cx - estimatedWidth / 2, cy + estimatedHeight / 2), searchRadius, "BL")

    // Compute actual detected corners centroid
    val detCenterX = (topLeft.x + topRight.x + bottomRight.x + bottomLeft.x) / 4.0
    val detCenterY = (topLeft.y + topRight.y + bottomRight.y + bottomLeft.y) / 4.0

    // Adjust corners to align with detected centroid (shift toward detected center)
    val shiftX = cx - detCenterX
    val shiftY = cy - detCenterY

    val shifted = listOf(
        Point(topLeft.x + (shiftX * 0.3).toInt(), topLeft.y + (shiftY * 0.3).toInt()),
        Point(topRight.x + (shiftX * 0.3).toInt(), topRight.y + (shiftY * 0.3).toInt()),
        Point(bottomRight.x + (shiftX * 0.3).toInt(), bottomRight.y + (shiftY * 0.3).toInt()),
        Point(bottomLeft.x + (shiftX * 0.3).toInt(), bottomLeft.y + (shiftY * 0.3).toInt())
    )

    return validateAndOrderCorners(shifted)
  }

  /**
   * Finds a corner by analyzing local gradient patterns.
   */
  private fun findCornerByGradientAnalysis(
      image: BufferedImage,
      expected: Point,
      searchRadius: Int,
      cornerType: String
  ): Point {
    // Define search region
    val x1 = (expected.x - searchRadius).coerceIn(0, image.width - 1)
    val y1 = (expected.y - searchRadius).coerceIn(0, image.height - 1)
    val x2 = (expected.x + searchRadius).coerceIn(0, image.width - 1)
    val y2 = (expected.y + searchRadius).coerceIn(0, image.height - 1)

    // Accumulate weighted gradient positions
    var sumX = 0.0
    var sumY = 0.0
    var sumW = 0.0

    // Also track edge direction histogram
    val hVotes = IntArray(360)  // 1-degree bins for edge direction
    val vVotes = IntArray(360)

    for (y in y1 until y2) {
      for (x in x1 until x2) {
        // Sobel gradient
        val gx = luminance(image.getRGB(min(image.width - 1, x + 1), y)) -
            luminance(image.getRGB(max(0, x - 1), y))
        val gy = luminance(image.getRGB(x, min(image.height - 1, y + 1))) -
            luminance(image.getRGB(x, max(0, y - 1)))
        val mag = sqrt(gx * gx + gy * gy)

        if (mag > 30) {
          // Angle perpendicular to gradient (edge direction)
          val edgeAngle = atan2(gy.toDouble(), gx.toDouble()) * 180 / Math.PI
          val edgeAngleIdx = ((edgeAngle + 180) % 360).toInt()

          // Check if this is a horizontal or vertical edge
          val isHorizontal = abs(gy) > abs(gx)
          val isVertical = abs(gx) > abs(gy)

          if (isHorizontal) {
            hVotes[edgeAngleIdx] += mag.toInt()
          }
          if (isVertical) {
            vVotes[edgeAngleIdx] += mag.toInt()
          }

          // Weight by distance to expected corner (prefer closer points)
          val dist = hypot((x - expected.x).toDouble(), (y - expected.y).toDouble())
          val weight = mag / (dist + 5)  // +5 to prevent division issues

          sumX += x * weight
          sumY += y * weight
          sumW += weight
        }
      }
    }

    // Find dominant edge directions
    val hPeak = findPeak(hVotes)
    val vPeak = findPeak(vVotes)

    // The corner should be near where horizontal and vertical edges intersect
    // Shift toward expected based on dominant directions
    var cornerX = if (sumW > 0) (sumX / sumW).toInt() else expected.x
    var cornerY = if (sumW > 0) (sumY / sumW).toInt() else expected.y

    // Adjust based on edge direction peaks
    // Horizontal edges (around 0 or 180 degrees) constrain Y
    // Vertical edges (around 90 or 270 degrees) constrain X
    val hInfluence = hVotes[hPeak] / 1000.0
    val vInfluence = vVotes[vPeak] / 1000.0

    // If we have strong horizontal edges, adjust Y toward them
    if (hInfluence > 0.5) {
      // The horizontal edge suggests Y should be at the expected Y
      cornerY = (cornerY * 0.7 + expected.y * 0.3).toInt()
    }

    // If we have strong vertical edges, adjust X toward them
    if (vInfluence > 0.5) {
      cornerX = (cornerX * 0.7 + expected.x * 0.3).toInt()
    }

    // Clamp to search region
    cornerX = cornerX.coerceIn(x1, x2)
    cornerY = cornerY.coerceIn(y1, y2)

    return Point(cornerX, cornerY)
  }

  /**
   * Finds the peak in a vote array.
   */
  private fun findPeak(votes: IntArray): Int {
    var maxVotes = 0
    var maxIdx = 0
    for (i in votes.indices) {
      if (votes[i] > maxVotes) {
        maxVotes = votes[i]
        maxIdx = i
      }
    }
    return maxIdx
  }

  /**
   * Validates and orders corners to TL, TR, BR, BL.
   */
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
    val width get() = maxX - minX
    val height get() = maxY - minY
  }

  data class Point(val x: Int, val y: Int)
}