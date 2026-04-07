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
 * Edge-Line Intersection corner detector.
 *
 * This detector explicitly finds photo edges using:
 * 1. Use detected region centroid as anchor
 * 2. Search for dominant edge lines in each corner region
 * 3. Find line-line intersections to get accurate corner positions
 * 4. Verify corners form a valid quadrilateral
 *
 * @param rectangleDetector Initial contour-based detector for region proposals
 * @param maxImageDimension Maximum dimension for downsampled processing (default 1000)
 */
class EdgeLineIntersectionCornerDetector(
    private val rectangleDetector: RectangleDetector,
    private val maxImageDimension: Int = 1000,
) {

  /** Mutable target count. */
  var targetPhotoCount: Int? = null

  /**
   * Detects photo regions and corners using edge-line intersection.
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
      val refinedCorners = findCornersByLineIntersection(image, quad)
      buildDetectedPhoto(refinedCorners)
    }
  }

  /**
   * Finds corners by detecting edge lines and computing their intersections.
   */
  private fun findCornersByLineIntersection(image: BufferedImage, quad: DetectedQuadrilateral): List<Point> {
    val cx = quad.centroid.x
    val cy = quad.centroid.y

    // Estimate photo dimensions from detected region
    val bounds = quadBounds(quad)
    val detectedWidth = bounds.width
    val detectedHeight = bounds.height

    // Expected photo might be larger than detected region
    val photoWidth = (detectedWidth * 1.1).toInt()
    val photoHeight = (detectedHeight * 1.1).toInt()

    // Expected corner positions based on detected centroid
    val halfW = photoWidth / 2
    val halfH = photoHeight / 2

    // Search regions for each corner
    val searchRadius = max(photoWidth, photoHeight) / 3

    // Find corners
    val topLeft = findCornerByEdgeSearch(image, Point(cx - halfW, cy - halfH), searchRadius, "top-left")
    val topRight = findCornerByEdgeSearch(image, Point(cx + halfW, cy - halfH), searchRadius, "top-right")
    val bottomRight = findCornerByEdgeSearch(image, Point(cx + halfW, cy + halfH), searchRadius, "bottom-right")
    val bottomLeft = findCornerByEdgeSearch(image, Point(cx - halfW, cy + halfH), searchRadius, "bottom-left")

    // Verify corners form a valid shape
    val corners = listOf(topLeft, topRight, bottomRight, bottomLeft)
    return refineCornersByShape(corners, cx, cy, photoWidth, photoHeight)
  }

  /**
   * Finds a corner by searching for edge line intersections.
   */
  private fun findCornerByEdgeSearch(
      image: BufferedImage,
      expected: Point,
      searchRadius: Int,
      cornerType: String
  ): Point {
    // Sample edge directions in the search region
    val houghVotes = Array(180) { FloatArray(600) }  // theta (-90 to 90) x rho (0 to 600)
    val maxRho = 600

    val x1 = (expected.x - searchRadius).coerceIn(0, image.width - 1)
    val y1 = (expected.y - searchRadius).coerceIn(0, image.height - 1)
    val x2 = (expected.x + searchRadius).coerceIn(0, image.width - 1)
    val y2 = (expected.y + searchRadius).coerceIn(0, image.height - 1)

    // Compute gradients and vote in Hough space
    for (y in y1 until y2) {
      for (x in x1 until x2) {
        val gx = luminance(image.getRGB(min(image.width - 1, x + 1), y)) -
            luminance(image.getRGB(max(0, x - 1), y))
        val gy = luminance(image.getRGB(x, min(image.height - 1, y + 1))) -
            luminance(image.getRGB(x, max(0, y - 1)))
        val mag = sqrt(gx * gx + gy * gy)

        if (mag > 40) {
          // Vote for edge line
          val angle = atan2(gy.toDouble(), gx.toDouble())
          val thetaIdx = ((angle * 180 / Math.PI + 90) / 1.0).toInt().coerceIn(0, 179)

          // For this edge pixel, vote for lines perpendicular to the gradient
          val perpAngle = angle + Math.PI / 2
          val rho = (x * cos(perpAngle) + y * sin(perpAngle)).toInt() + maxRho / 2
          val rhoIdx = rho.coerceIn(0, maxRho - 1)

          houghVotes[thetaIdx][rhoIdx] += mag.toFloat()
        }
      }
    }

    // Find dominant lines
    val lines = mutableListOf<Pair<Int, Int>>()  // (thetaIdx, rhoIdx)
    var maxVotes = 0f

    for (thetaIdx in 0 until 180 step 3) {  // Skip for speed
      for (rhoIdx in 0 until maxRho step 3) {
        val votes = houghVotes[thetaIdx][rhoIdx]
        if (votes > maxVotes * 0.3) {  // Keep lines with significant votes
          lines.add(Pair(thetaIdx, rhoIdx))
          if (votes > maxVotes) maxVotes = votes
        }
      }
    }

    // Find horizontal and vertical lines
    val horizontalLines = lines.filter { (thetaIdx, _) ->
      val theta = thetaIdx * 1.0 - 90
      abs(theta) < 30 || abs(theta) > 150  // Near 0 or 180 degrees
    }.sortedByDescending { (t, r) -> houghVotes[t][r] }

    val verticalLines = lines.filter { (thetaIdx, _) ->
      val theta = thetaIdx * 1.0 - 90
      abs(theta - 90) < 30  // Near 90 degrees
    }.sortedByDescending { (t, r) -> houghVotes[t][r] }

    // Compute corner position from line intersections
    var cornerX = expected.x
    var cornerY = expected.y

    // Use the strongest horizontal and vertical lines
    if (horizontalLines.isNotEmpty() && verticalLines.isNotEmpty()) {
      val hLine = horizontalLines.first()
      val vLine = verticalLines.first()

      // Line equation: x*cos(theta) + y*sin(theta) = rho
      val theta1 = (hLine.first * 1.0 - 90) * Math.PI / 180
      val rho1 = hLine.second - maxRho / 2
      val theta2 = (vLine.first * 1.0 - 90) * Math.PI / 180
      val rho2 = vLine.second - maxRho / 2

      // Solve for intersection
      val det = cos(theta1) * sin(theta2) - sin(theta1) * cos(theta2)
      if (abs(det) > 0.01) {
        cornerX = ((rho1 * sin(theta2) - rho2 * sin(theta1)) / det).toInt()
        cornerY = ((rho2 * cos(theta1) - rho1 * cos(theta2)) / det).toInt()
      }
    } else if (horizontalLines.isNotEmpty()) {
      // Only horizontal line found - use gradient to estimate vertical position
      val hLine = horizontalLines.first()
      val theta = (hLine.first * 1.0 - 90) * Math.PI / 180
      val rho = hLine.second - maxRho / 2

      // Find y coordinate where this line crosses expected x
      val sinT = sin(theta)
      if (abs(sinT) > 0.1) {
        cornerY = ((rho - expected.x * cos(theta)) / sinT).toInt()
      }
      cornerX = expected.x
    } else if (verticalLines.isNotEmpty()) {
      // Only vertical line found
      val vLine = verticalLines.first()
      val theta = (vLine.first * 1.0 - 90) * Math.PI / 180
      val rho = vLine.second - maxRho / 2

      // Find x coordinate where this line crosses expected y
      val cosT = cos(theta)
      if (abs(cosT) > 0.1) {
        cornerX = ((rho - expected.y * sin(theta)) / cosT).toInt()
      }
      cornerY = expected.y
    }

    // Clamp to search region with some tolerance
    cornerX = cornerX.coerceIn(x1 - searchRadius / 2, x2 + searchRadius / 2)
    cornerY = cornerY.coerceIn(y1 - searchRadius / 2, y2 + searchRadius / 2)

    return Point(cornerX.coerceIn(0, image.width - 1), cornerY.coerceIn(0, image.height - 1))
  }

  /**
   * Refines corners by enforcing a reasonable quadrilateral shape.
   */
  private fun refineCornersByShape(
      corners: List<Point>,
      cx: Int,
      cy: Int,
      expectedWidth: Int,
      expectedHeight: Int
  ): List<Point> {
    if (corners.size != 4) return corners

    // Sort corners by their angle around the centroid
    val sorted = corners.mapIndexed { idx, p ->
      val angle = atan2((p.y - cy).toDouble(), (p.x - cx).toDouble())
      Pair(idx, Pair(p, angle))
    }.sortedBy { it.second.second }
    .map { it.second.first }

    // Compute the center of detected corners
    val detCenterX = sorted.map { it.x }.average()
    val detCenterY = sorted.map { it.y }.average()

    // Adjust corners toward expected positions
    val halfW = expectedWidth / 2
    val halfH = expectedHeight / 2

    val adjusted = sorted.map { corner ->
      // Compute direction from detected center to corner
      val dirX = corner.x - detCenterX
      val dirY = corner.y - detCenterY
      val dist = sqrt(dirX * dirX + dirY * dirY)

      if (dist > 0) {
        // Move corner toward expected position based on detected direction
        val targetX = cx + (dirX / dist) * halfW
        val targetY = cy + (dirY / dist) * halfH

        // Blend between detected and expected
        val blend = 0.6
        Point(
            (corner.x * (1 - blend) + targetX * blend).toInt(),
            (corner.y * (1 - blend) + targetY * blend).toInt()
        )
      } else {
        corner
      }
    }

    // Reorder to TL, TR, BR, BL
    val sumSorted = adjusted.sortedBy { it.x + it.y }
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