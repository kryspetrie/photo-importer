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
 * Edge-following corner detector that finds photo boundaries by following edge transitions.
 *
 * ## Strategy
 *
 * Instead of relying on contour-based detection (which finds inner contours), this detector:
 * 1. Uses BoofCV Shi-Tomasi corner detector to find strong corner features
 * 2. For each expected photo corner, finds the nearest corner feature
 * 3. Validates using edge direction analysis around the corner
 * 4. Refines position using gradient-based corner refinement
 *
 * @param rectangleDetector Initial contour-based detector for region proposals
 * @param roiSearchRadius Search radius for finding corner features (default 150px)
 * @param edgeSearchRadius Local search radius for edge refinement (default 60px)
 * @param minEdgeStrength Minimum gradient magnitude for edge detection (default 50)
 * @param maxImageDimension Maximum dimension for downsampled processing (default 1200)
 */
class EdgeFollowingCornerDetector(
    private val rectangleDetector: RectangleDetector,
    private val roiSearchRadius: Int = 150,
    private val edgeSearchRadius: Int = 60,
    private val minEdgeStrength: Float = 50f,
    private val maxImageDimension: Int = 1200,
) {

  /** BoofCV Shi-Tomasi corner detector */
  private val featureDetector: GeneralFeatureDetector<GrayF32, GrayS16> by lazy {
    val generalConfig = ConfigGeneralDetector()
    generalConfig.maxFeatures = 500
    generalConfig.threshold = 1f
    generalConfig.radius = 3

    val shiConfig = ConfigShiTomasi()
    shiConfig.radius = 5

    FactoryDetectPoint.createShiTomasi(generalConfig, shiConfig, GrayS16::class.java)
  }

  /** Mutable target count. */
  var targetPhotoCount: Int? = null

  /** Detects photo regions and corners using edge-following strategy. */
  fun detectPhotos(image: BufferedImage): List<DetectedPhoto> {
    val imageArea = image.width.toFloat() * image.height.toFloat()

    // Step 1: Get region proposals to know rough photo locations
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

    // Step 2: Detect corner features in the image
    val cornerFeatures = detectCornerFeatures(image)

    // Step 3: For each detected region, find the best corners using edge-following
    return afterNms.map { quad ->
      val refinedCorners = refineCornersByEdgeFollowing(image, quad, cornerFeatures)
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
      // Convert back to original scale
      val origX = (pt.x.toInt() / scale).toInt()
      val origY = (pt.y.toInt() / scale).toInt()

      // Compute local edge directions around this point
      val (edgeDir1, edgeDir2) = computeEdgeDirections(workImage, pt.x.toInt(), pt.y.toInt())

      features.add(CornerFeature(origX, origY, edgeDir1, edgeDir2, 1.0f))
    }

    return features.sortedByDescending { it.confidence }
  }

  /** Computes dominant edge directions at a point (for corner classification). */
  private fun computeEdgeDirections(image: BufferedImage, x: Int, y: Int): Pair<Float, Float> {
    val radius = 20
    val x1 = (x - radius).coerceIn(0, image.width - 1)
    val y1 = (y - radius).coerceIn(0, image.height - 1)
    val x2 = (x + radius).coerceIn(0, image.width - 1)
    val y2 = (y + radius).coerceIn(0, image.height - 1)

    // Compute gradient histogram in polar coordinates
    val angles = mutableMapOf<Int, Float>()
    for (dy in y1..y2) {
      for (dx in x1..x2) {
        if (dx == x && dy == y) continue

        val gx = luminance(image.getRGB(dx + 1, dy)) - luminance(image.getRGB(dx - 1, dy))
        val gy = luminance(image.getRGB(dx, dy + 1)) - luminance(image.getRGB(dx, dy - 1))
        val mag = sqrt(gx * gx + gy * gy)

        if (mag > minEdgeStrength) {
          val angle = atan2(gy.toDouble(), gx.toDouble())
          val bin = ((angle * 180 / Math.PI + 180) / 22.5).toInt() % 16 // 16 bins of 22.5 degrees
          angles[bin] = (angles[bin] ?: 0f) + mag
        }
      }
    }

    // Find top two dominant directions
    val sorted = angles.entries.sortedByDescending { it.value }
    val dir1 = if (sorted.isNotEmpty()) sorted[0].key * 22.5f - 180f else 0f
    val dir2 = if (sorted.size > 1) sorted[1].key * 22.5f - 180f else dir1

    return Pair(dir1, dir2)
  }

  /** Refines corner positions using edge-following from corner features. */
  private fun refineCornersByEdgeFollowing(
      image: BufferedImage,
      quad: DetectedQuadrilateral,
      cornerFeatures: List<CornerFeature>
  ): List<Point> {
    val corners = quad.corners
    if (corners.size != 4) return corners.map { Point(it.x, it.y) }

    // Sort corners to get consistent ordering
    val sorted = sortCorners(corners)

    // For each rough corner, find the nearest corner feature
    val refined = mutableListOf<Point>()
    for (corner in sorted) {
      val refinedCorner = findNearestCornerFeature(corner, cornerFeatures, image)
      refined.add(refinedCorner)
    }

    // Validate and ensure correct ordering
    return validateAndOrderCorners(refined)
  }

  /** Finds the nearest corner feature that has appropriate edge directions for a photo corner. */
  private fun findNearestCornerFeature(
      roughCorner: Point,
      cornerFeatures: List<CornerFeature>,
      image: BufferedImage
  ): Point {
    // Find corner features within search radius
    val candidates =
        cornerFeatures.filter {
          val dist = hypot((it.x - roughCorner.x).toDouble(), (it.y - roughCorner.y).toDouble())
          dist <= roiSearchRadius
        }

    if (candidates.isEmpty()) {
      // Fall back to direct gradient-based corner finding
      return refineCornerByGradient(image, roughCorner)
    }

    // Score each candidate by:
    // 1. Distance to rough corner (prefer closer)
    // 2. Edge direction perpendicularity (corners should have perpendicular edges)
    // 3. Local contrast around the corner

    var bestScore = -1.0
    var bestPoint = roughCorner

    for (feature in candidates) {
      val dist =
          hypot((feature.x - roughCorner.x).toDouble(), (feature.y - roughCorner.y).toDouble())

      // Compute perpendicular score (edge directions should be ~90 degrees apart)
      val edgeDiff = abs(normalizeAngle(feature.edgeDir1 - feature.edgeDir2))
      val perpendicularScore =
          1.0f - (edgeDiff - 90f).coerceIn(-90f, 90f) / 90f // Max at 90 degrees

      // Compute local contrast score
      val contrast = computeLocalContrast(image, feature.x, feature.y)

      // Combined score (weighted)
      val distanceScore = 1.0 - (dist / roiSearchRadius)
      val score = 0.4 * distanceScore + 0.4 * perpendicularScore + 0.2 * contrast

      if (score > bestScore) {
        bestScore = score
        bestPoint = Point(feature.x, feature.y)
      }
    }

    // Refine the best point using gradient-based refinement
    return refineCornerByGradient(image, bestPoint)
  }

  /** Computes local contrast at a point (for corner quality assessment). */
  private fun computeLocalContrast(image: BufferedImage, x: Int, y: Int): Float {
    val radius = 15
    var sumDiff = 0.0
    var count = 0
    val centerLum = luminance(image.getRGB(x, y))

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

    return if (count > 0) (sumDiff / count / 255.0).toFloat().coerceIn(0f, 1f) else 0f
  }

  /** Refines corner position using gradient-based corner detection. */
  private fun refineCornerByGradient(image: BufferedImage, corner: Point): Point {
    val imgWidth = image.width
    val imgHeight = image.height

    val radius = edgeSearchRadius
    val x1 = (corner.x - radius).coerceIn(0, imgWidth - 1)
    val y1 = (corner.y - radius).coerceIn(0, imgHeight - 1)
    val x2 = (corner.x + radius).coerceIn(0, imgWidth - 1)
    val y2 = (corner.y + radius).coerceIn(0, imgHeight - 1)

    // Find dominant edge directions
    val edgeVotes = Array(36) { 0.0 } // 36 bins of 10 degrees each

    for (y in y1..y2) {
      for (x in x1..x2) {
        val gx =
            luminance(image.getRGB(min(imgWidth - 1, x + 1), y)) -
                luminance(image.getRGB(max(0, x - 1), y))
        val gy =
            luminance(image.getRGB(x, min(imgHeight - 1, y + 1))) -
                luminance(image.getRGB(x, max(0, y - 1)))
        val mag = sqrt(gx * gx + gy * gy)

        if (mag > minEdgeStrength) {
          val angle = atan2(gy.toDouble(), gx.toDouble())
          val bin = ((angle * 180 / Math.PI + 180) / 10).toInt().coerceIn(0, 35)
          edgeVotes[bin] += mag
        }
      }
    }

    // Find two dominant perpendicular edge directions
    val sortedBins = edgeVotes.indices.sortedByDescending { edgeVotes[it] }
    val dir1Bin = sortedBins[0]
    val dir2Bin = (dir1Bin + 9) % 36 // ~90 degrees away

    // Compute weighted center of edges in those directions
    var sumX = 0.0
    var sumY = 0.0
    var sumW = 0.0

    val binRange = 3 // Include neighboring bins

    // Horizontal-ish edges (perpendicular to dir1)
    val horizBin = (dir1Bin + 18) % 36 // Opposite direction
    for (y in y1..y2) {
      val dy = y - corner.y
      val dyNorm = dy.toDouble() / radius
      if (abs(dyNorm) < 1.0) {
        for (b in maxOf(0, horizBin - binRange)..minOf(35, horizBin + binRange)) {
          sumW += edgeVotes[b] * (1.0 - abs(dyNorm))
        }
        sumY += y * edgeVotes[horizBin]
      }
    }

    // Vertical-ish edges (perpendicular to dir1)
    val vertBin = (dir1Bin + 9) % 36
    for (x in x1..x2) {
      val dx = x - corner.x
      val dxNorm = dx.toDouble() / radius
      if (abs(dxNorm) < 1.0) {
        for (b in maxOf(0, vertBin - binRange)..minOf(35, vertBin + binRange)) {
          sumW += edgeVotes[b] * (1.0 - abs(dxNorm))
        }
        sumX += x * edgeVotes[vertBin]
      }
    }

    // Compute refined position
    val refinedX = if (sumW > 0) (sumX / sumW).toInt() else corner.x
    val refinedY = if (sumW > 0) (sumY / sumW).toInt() else corner.y

    // Clamp to reasonable distance from original
    val offsetX = (refinedX - corner.x).coerceIn(-radius / 2, radius / 2)
    val offsetY = (refinedY - corner.y).coerceIn(-radius / 2, radius / 2)

    return Point(
        (corner.x + offsetX).coerceIn(0, imgWidth - 1),
        (corner.y + offsetY).coerceIn(0, imgHeight - 1))
  }

  private fun normalizeAngle(angle: Float): Float {
    var a = angle % 180
    if (a < -90) a += 180
    if (a > 90) a -= 180
    return a
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

  private fun sortCorners(corners: List<RectangleDetector.Point>): List<Point> {
    if (corners.size != 4) return corners.map { Point(it.x, it.y) }
    val sortedList = corners.sortedBy { it.x + it.y }
    val topLeft = sortedList[0]
    val bottomRight = sortedList[3]
    val remaining = listOf(sortedList[1], sortedList[2]).sortedBy { it.x - it.y }
    return listOf(
        Point(topLeft.x, topLeft.y),
        Point(remaining[0].x, remaining[0].y),
        Point(bottomRight.x, bottomRight.y),
        Point(remaining[1].x, remaining[1].y))
  }

  private fun validateAndOrderCorners(corners: List<Point>): List<Point> {
    if (corners.size != 4) return corners

    val sortedBySum = corners.sortedBy { it.x + it.y }
    val topLeft = sortedBySum[0]
    val bottomRight = sortedBySum[3]

    val remaining = listOf(sortedBySum[1], sortedBySum[2])
    val sortedByDiff = remaining.sortedBy { it.y - it.x }
    val topRight = sortedByDiff[0]
    val bottomLeft = sortedByDiff[1]

    return listOf(topLeft, topRight, bottomRight, bottomLeft)
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
      val edgeDir1: Float,
      val edgeDir2: Float,
      val confidence: Float
  )
}
