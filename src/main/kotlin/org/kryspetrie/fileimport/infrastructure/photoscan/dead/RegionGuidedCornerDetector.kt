package org.kryspetrie.fileimport.infrastructure.photoscan

import boofcv.abst.feature.detect.interest.ConfigGeneralDetector
import boofcv.abst.feature.detect.interest.ConfigShiTomasi
import boofcv.alg.feature.detect.interest.GeneralFeatureDetector
import boofcv.factory.feature.detect.interest.FactoryDetectPoint
import boofcv.struct.image.GrayF32
import boofcv.struct.image.GrayS16
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import org.kryspetrie.fileimport.domain.model.DetectedPhoto
import org.kryspetrie.fileimport.domain.model.PhotoCorner

/**
 * Region-guided corner detector that uses detected region centroid to search for actual photo
 * boundaries via edge line detection.
 *
 * The key insight is that the Douglas-Peucker contour detection finds the correct region centroid
 * but produces distorted corners. This detector:
 * 1. Uses the region proposal to find the approximate photo location
 * 2. Searches for edge lines near each expected corner position
 * 3. Computes corner positions as line intersections
 * 4. Validates that corners form a reasonable quadrilateral
 *
 * @param rectangleDetector Initial contour-based detector for region proposals
 * @param maxImageDimension Maximum dimension for downsampled processing (default 1200)
 */
class RegionGuidedCornerDetector(
    private val rectangleDetector: RectangleDetector,
    private val maxImageDimension: Int = 1200,
) {

  /** BoofCV Shi-Tomasi corner detector for finding corner features */
  private val featureDetector: GeneralFeatureDetector<GrayF32, GrayS16> by lazy {
    val generalConfig = ConfigGeneralDetector()
    generalConfig.maxFeatures = 1000
    generalConfig.threshold = 0.5f
    generalConfig.radius = 3

    val shiConfig = ConfigShiTomasi()
    shiConfig.radius = 5

    FactoryDetectPoint.createShiTomasi(generalConfig, shiConfig, GrayS16::class.java)
  }

  /** Mutable target count. */
  var targetPhotoCount: Int? = null

  /** Detects photo regions and corners using region-guided edge detection. */
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

    // Step 2: Detect all corner features in the image
    val cornerFeatures = detectCornerFeatures(image)

    // Step 3: For each region, find corners using guided search
    return afterNms.map { quad ->
      val refinedCorners = findCornersFromRegion(image, quad, cornerFeatures)
      buildDetectedPhoto(refinedCorners)
    }
  }

  /** Detects corner features using BoofCV Shi-Tomasi detector. */
  private fun detectCornerFeatures(image: BufferedImage): List<CornerFeature> {
    val (workImage, scale) = downsampleForDetection(image)
    val width = workImage.width
    val height = workImage.height

    // Convert to BoofCV GrayF32 format
    val gray = GrayF32(width, height)
    for (y in 0 until height) {
      for (x in 0 until width) {
        val rgb = workImage.getRGB(x, y)
        val r = (rgb shr 16) and 255
        val g = (rgb shr 8) and 255
        val b = rgb and 255
        val lum = (0.299f * r + 0.587f * g + 0.114f * b)
        gray.set(x, y, lum)
      }
    }

    // Detect corners
    featureDetector.process(
        gray,
        GrayS16(width, height),
        GrayS16(width, height),
        GrayS16(width, height),
        GrayS16(width, height),
        GrayS16(width, height))

    val features = mutableListOf<CornerFeature>()
    val maximums = featureDetector.getMaximums()

    for (i in 0 until maximums.size()) {
      val pt = maximums.get(i)
      val origX = (pt.x.toInt() / scale).toInt()
      val origY = (pt.y.toInt() / scale).toInt()
      features.add(CornerFeature(origX, origY))
    }

    return features.sortedByDescending { it.x + it.y } // Sort by corner-ness
  }

  /** Finds corners for a region by guided search near expected positions. */
  private fun findCornersFromRegion(
      image: BufferedImage,
      quad: DetectedQuadrilateral,
      cornerFeatures: List<CornerFeature>
  ): List<Point> {
    // Get the rough region bounds
    val bounds = quadBounds(quad)
    val cx = quad.centroid.x
    val cy = quad.centroid.y

    // Estimate photo dimensions from the detected shape
    val roughWidth = bounds.width
    val roughHeight = bounds.height

    // Calculate expected corner positions based on region centroid
    val scale = 0.8f // Photo might be larger than detected region
    val halfW = (roughWidth * scale / 2).toInt()
    val halfH = (roughHeight * scale / 2).toInt()

    // Expected corner positions (relative to centroid)
    val expectedCorners =
        listOf(
            Point(cx - halfW, cy - halfH), // TL
            Point(cx + halfW, cy - halfH), // TR
            Point(cx + halfW, cy + halfH), // BR
            Point(cx - halfW, cy + halfH) // BL
            )

    // For each expected corner, find the nearest strong corner feature
    val searchRadius = max(halfW, halfH)
    val foundCorners = mutableListOf<Point>()

    for (expected in expectedCorners) {
      val nearest = findNearestStrongCorner(expected, cornerFeatures, searchRadius, image)
      foundCorners.add(nearest)
    }

    // Validate and refine corners
    return refineCornersGeometry(foundCorners, image)
  }

  /** Finds the nearest strong corner feature within search radius. */
  private fun findNearestStrongCorner(
      expected: Point,
      cornerFeatures: List<CornerFeature>,
      searchRadius: Int,
      image: BufferedImage
  ): Point {
    // Find corner features within search radius
    val candidates =
        cornerFeatures.filter {
          val dist = hypot((it.x - expected.x).toDouble(), (it.y - expected.y).toDouble())
          dist <= searchRadius
        }

    if (candidates.isEmpty()) {
      // Fall back to edge-based corner finding
      return findCornerByEdgeScan(expected, searchRadius, image)
    }

    // Score candidates by distance, contrast, and corner sharpness
    var bestScore = -1.0
    var bestPoint = expected

    for (feature in candidates) {
      val dist = hypot((feature.x - expected.x).toDouble(), (feature.y - expected.y).toDouble())

      // Compute local edge directions
      val edgeDirections = computeLocalEdges(image, feature.x, feature.y, 25)
      val cornerScore = computeCornerScore(edgeDirections)

      // Compute local contrast
      val contrast = computeLocalContrast(image, feature.x, feature.y)

      // Combined score
      val distanceScore = 1.0 - (dist / searchRadius)
      val score = 0.3 * distanceScore + 0.4 * cornerScore + 0.3 * contrast

      if (score > bestScore) {
        bestScore = score
        bestPoint = Point(feature.x, feature.y)
      }
    }

    return bestPoint
  }

  /** Computes edge directions in a local region around a point. */
  private fun computeLocalEdges(image: BufferedImage, x: Int, y: Int, radius: Int): List<Double> {
    val directions = mutableListOf<Double>()
    val x1 = (x - radius).coerceIn(0, image.width - 1)
    val y1 = (y - radius).coerceIn(0, image.height - 1)
    val x2 = (x + radius).coerceIn(0, image.width - 1)
    val y2 = (y + radius).coerceIn(0, image.height - 1)

    for (dy in y1 until y2 step 5) {
      for (dx in x1 until x2 step 5) {
        if (dx == x && dy == y) continue

        val gx =
            luminance(image.getRGB(min(image.width - 1, dx + 1), dy)) -
                luminance(image.getRGB(max(0, dx - 1), dy))
        val gy =
            luminance(image.getRGB(dx, min(image.height - 1, dy + 1))) -
                luminance(image.getRGB(dx, max(0, dy - 1)))
        val mag = sqrt(gx * gx + gy * gy)

        if (mag > 50.0) {
          val angle = atan2(gy.toDouble(), gx.toDouble())
          directions.add(angle)
        }
      }
    }

    return directions
  }

  /** Computes a corner score from edge directions (how perpendicular are the edges). */
  private fun computeCornerScore(edgeDirections: List<Double>): Double {
    if (edgeDirections.size < 4) return 0.5

    val sorted = edgeDirections.sorted()
    var maxDiff = 0.0

    for (i in sorted.indices) {
      for (j in i + 1 until sorted.size) {
        var diff = abs(sorted[j] - sorted[i])
        if (diff > Math.PI) diff = 2 * Math.PI - diff

        // A good corner has edges ~90 degrees apart
        val score = 1.0 - abs(diff - Math.PI / 2) / (Math.PI / 2)
        if (score > maxDiff) {
          maxDiff = score
        }
      }
    }

    return maxDiff
  }

  /** Computes local contrast at a point. */
  private fun computeLocalContrast(image: BufferedImage, x: Int, y: Int): Double {
    val radius = 20
    val centerLum = luminance(image.getRGB(x, y))
    var sumDiff = 0.0
    var count = 0

    for (dy in -radius..radius) {
      for (dx in -radius..radius) {
        if (dx == 0 && dy == 0) continue
        val px = (x + dx).coerceIn(0, image.width - 1)
        val py = (y + dy).coerceIn(0, image.height - 1)
        val diff = abs(luminance(image.getRGB(px, py)) - centerLum)
        sumDiff += diff
        count++
      }
    }

    return if (count > 0) (sumDiff / count / 255.0).coerceIn(0.0, 1.0) else 0.0
  }

  /** Finds corner position by scanning for edges in the expected direction. */
  private fun findCornerByEdgeScan(
      expected: Point,
      searchRadius: Int,
      image: BufferedImage
  ): Point {
    val step = 5

    // Search for horizontal edge (search up/down from expected x position)
    var bestY = expected.y
    var bestYScore = 0.0

    var y = expected.y - searchRadius
    while (y <= expected.y + searchRadius) {
      val yClamped = y.coerceIn(0, image.height - 1)
      var sumGrad = 0.0
      var x = expected.x - searchRadius
      while (x < expected.x + searchRadius) {
        val xClamped = x.coerceIn(0, image.width - 1)
        val gx =
            luminance(image.getRGB(min(image.width - 1, xClamped + 1), yClamped)) -
                luminance(image.getRGB(max(0, xClamped - 1), yClamped))
        sumGrad += abs(gx)
        x += step
      }
      if (sumGrad > bestYScore) {
        bestYScore = sumGrad
        bestY = yClamped
      }
      y += step
    }

    // Search for vertical edge (search left/right from expected y position)
    var bestX = expected.x
    var bestXScore = 0.0

    var x = expected.x - searchRadius
    while (x <= expected.x + searchRadius) {
      val xClamped = x.coerceIn(0, image.width - 1)
      var sumGrad = 0.0
      var y = expected.y - searchRadius
      while (y < expected.y + searchRadius) {
        val yClamped = y.coerceIn(0, image.height - 1)
        val gy =
            luminance(image.getRGB(xClamped, min(image.height - 1, yClamped + 1))) -
                luminance(image.getRGB(xClamped, max(0, yClamped - 1)))
        sumGrad += abs(gy)
        y += step
      }
      if (sumGrad > bestXScore) {
        bestXScore = sumGrad
        bestX = xClamped
      }
      x += step
    }

    return Point(bestX, bestY)
  }

  /** Refines corners by enforcing geometric consistency. */
  private fun refineCornersGeometry(corners: List<Point>, image: BufferedImage): List<Point> {
    if (corners.size != 4) return corners

    // Order corners: TL, TR, BR, BL
    val sortedBySum = corners.sortedBy { it.x + it.y }
    val topLeft = sortedBySum[0]
    val bottomRight = sortedBySum[3]
    val remaining = listOf(sortedBySum[1], sortedBySum[2]).sortedBy { it.y - it.x }
    val topRight = remaining[0]
    val bottomLeft = remaining[1]

    val ordered = listOf(topLeft, topRight, bottomRight, bottomLeft)

    // Refine each corner using local edge gradient
    val refined = ordered.map { corner -> refineCornerByGradient(image, corner) }

    // Ensure geometric consistency (adjacent edges should be roughly perpendicular)
    return enforcePerpendicularEdges(refined)
  }

  /** Refines corner position using local gradient analysis. */
  private fun refineCornerByGradient(image: BufferedImage, corner: Point): Point {
    val radius = 40
    val x1 = (corner.x - radius).coerceIn(0, image.width - 1)
    val y1 = (corner.y - radius).coerceIn(0, image.height - 1)
    val x2 = (corner.x + radius).coerceIn(0, image.width - 1)
    val y2 = (corner.y + radius).coerceIn(0, image.height - 1)

    // Compute weighted center of strong edge responses
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
        val mag = sqrt(gx * gx + gy * gy)

        if (mag > 30.0) {
          // Weight by distance to corner (prefer closer points)
          val dist = hypot((x - corner.x).toDouble(), (y - corner.y).toDouble())
          val weight = mag / (dist + 1)
          sumX += x * weight
          sumY += y * weight
          sumW += weight
        }
      }
    }

    if (sumW > 0) {
      val refinedX = (sumX / sumW).toInt()
      val refinedY = (sumY / sumW).toInt()

      // Clamp to reasonable distance from original
      val offsetX = (refinedX - corner.x).coerceIn(-radius / 2, radius / 2)
      val offsetY = (refinedY - corner.y).coerceIn(-radius / 2, radius / 2)

      return Point(
          (corner.x + offsetX).coerceIn(0, image.width - 1),
          (corner.y + offsetY).coerceIn(0, image.height - 1))
    }

    return corner
  }

  /** Enforces that adjacent edges are roughly perpendicular. */
  private fun enforcePerpendicularEdges(corners: List<Point>): List<Point> {
    if (corners.size != 4) return corners

    // Check if corners form a reasonable shape
    // Top edge should be roughly horizontal
    val topEdgeAngle =
        atan2((corners[1].y - corners[0].y).toDouble(), (corners[1].x - corners[0].x).toDouble())
    val avgY = (corners[0].y + corners[1].y) / 2.0

    // If top edge is far from horizontal, adjust
    if (abs(topEdgeAngle) > 0.2) { // More than ~11 degrees from horizontal
      val newTopLeft = Point(corners[0].x, corners[0].y + ((avgY - corners[0].y) * 0.5).toInt())
      val newTopRight = Point(corners[1].x, corners[1].y + ((avgY - corners[1].y) * 0.5).toInt())
      return listOf(newTopLeft, newTopRight, corners[2], corners[3])
    }

    return corners
  }

  private fun downsampleForDetection(image: BufferedImage): Pair<BufferedImage, Float> {
    if (image.width <= maxImageDimension && image.height <= maxImageDimension) {
      return image to 1.0f
    }
    val scale = maxImageDimension.toFloat() / max(image.width.toFloat(), image.height.toFloat())
    val newW = (image.width * scale).toInt()
    val newH = (image.height * scale).toInt()
    val resized = BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB)
    resized.graphics.drawImage(
        image.getScaledInstance(newW, newH, BufferedImage.SCALE_AREA_AVERAGING), 0, 0, null)
    return resized to (1.0f / scale)
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

  data class CornerFeature(
      val x: Int,
      val y: Int,
  )
}
