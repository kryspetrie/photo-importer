package org.kryspetrie.fileimport.infrastructure.photoscan

import boofcv.abst.feature.detect.interest.ConfigGeneralDetector
import boofcv.abst.feature.detect.interest.ConfigShiTomasi
import boofcv.alg.feature.detect.interest.GeneralFeatureDetector
import boofcv.factory.feature.detect.interest.FactoryDetectPoint
import boofcv.struct.image.GrayF32
import boofcv.struct.image.GrayS16
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import org.kryspetrie.fileimport.domain.model.DetectedPhoto
import org.kryspetrie.fileimport.domain.model.PhotoCorner

/**
 * Hybrid corner detector combining contour-based region proposals with BoofCV corner refinement.
 *
 * ## Approach
 *
 * **Stage 1: Region Proposals** (from [RectangleDetector])
 * - Uses existing contour-based detection to find candidate photo regions
 * - Produces rough quadrilateral approximations
 *
 * **Stage 2: BoofCV Shi-Tomasi Corner Detection**
 * - Uses Shi-Tomasi corner detector from BoofCV via GeneralFeatureDetector
 * - Finds strong corner features in the entire image
 * - Refines rough corner positions by finding nearest strong features
 *
 * **Stage 3: Edge-Based Line Refinement**
 * - For each corner region, analyzes local edge structure
 * - Uses gradient-based methods to find precise corner position
 *
 * @param rectangleDetector Initial contour-based detector for region proposals
 * @param roiSearchRadius Search radius for finding nearest interest points (default 80px)
 * @param maxFeaturesPerCorner Maximum features to consider per corner (default 5)
 */
class HybridEdgeCornerDetector(
    private val rectangleDetector: RectangleDetector,
    private val roiSearchRadius: Int = 80,
    private val maxFeaturesPerCorner: Int = 5,
    private val maxImageDimension: Int = 1000,
) {

  /** BoofCV Shi-Tomasi corner detector */
  private val featureDetector: GeneralFeatureDetector<GrayF32, GrayS16> by lazy {
    val generalConfig = ConfigGeneralDetector()
    generalConfig.maxFeatures = 200
    generalConfig.threshold = 10f
    generalConfig.radius = 3

    val shiConfig = ConfigShiTomasi()
    shiConfig.radius = 5

    FactoryDetectPoint.createShiTomasi(generalConfig, shiConfig, GrayS16::class.java)
  }

  /** Mutable target count. Set by callers who know the expected photo count. */
  var targetPhotoCount: Int? = null

  /**
   * Detects photo regions and corners in a scanned image.
   *
   * @param image The scanned image
   * @return [DetectedPhoto] objects with corners ordered TL→TR→BR→BL.
   */
  fun detectPhotos(image: BufferedImage): List<DetectedPhoto> {
    val imgWidth = image.width.toFloat()
    val imgHeight = image.height.toFloat()
    val imageArea = imgWidth * imgHeight

    // Stage 1: Get region proposals from contour-based detector
    val raw = rectangleDetector.detectRectangles(image, expectedCount = targetPhotoCount ?: 4)
    if (raw.isEmpty()) return emptyList()

    // Filter out whole-image false positives
    val notWholeImage =
        raw.filter { quad: DetectedQuadrilateral ->
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

    // Detect interest points in the full image for corner refinement
    val interestPoints = detectInterestPoints(image)

    // Build DetectedPhoto with refined corners
    return afterNms.map { quad ->
      val refinedCorners = refineCornersWithInterestPoints(image, quad, interestPoints)
      buildDetectedPhoto(refinedCorners)
    }
  }

  /** Detects interest points using BoofCV Shi-Tomasi detector on a downsampled image. */
  private fun detectInterestPoints(image: BufferedImage): List<InterestPoint> {
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

    val points = mutableListOf<InterestPoint>()
    val maximums = featureDetector.getMaximums()

    for (i in 0 until maximums.size()) {
      val pt = maximums.get(i)
      // Scale is not directly available, use a default
      // Convert back to original scale
      points.add(
          InterestPoint((pt.x.toInt() / scale).toInt(), (pt.y.toInt() / scale).toInt(), 5f / scale))
    }

    // Sort by scale (stronger corners have larger scale)
    return points.sortedByDescending { it.scale }
  }

  /** Downsamples image for corner detection to reduce memory usage. */
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

  /** Refines corner positions using detected interest points and local edge analysis. */
  private fun refineCornersWithInterestPoints(
      image: BufferedImage,
      quad: DetectedQuadrilateral,
      interestPoints: List<InterestPoint>
  ): List<Point> {
    val corners = quad.corners
    if (corners.size != 4) return corners.map { Point(it.x, it.y) }

    // Sort corners to get consistent ordering
    val sorted = sortCorners(corners)

    // Refine each corner
    val refined = mutableListOf<Point>()
    for (corner in sorted) {
      val refinedCorner = refineCornerWithInterestPoints(corner, interestPoints, image)
      refined.add(refinedCorner)
    }

    // Validate and ensure correct ordering
    return validateAndOrderCorners(refined)
  }

  /** Refines a corner using nearby interest points and edge analysis. */
  private fun refineCornerWithInterestPoints(
      roughCorner: Point,
      interestPoints: List<InterestPoint>,
      image: BufferedImage
  ): Point {
    // Find interest points within the search radius
    val nearby =
        interestPoints
            .filter {
              val dist = hypot((it.x - roughCorner.x).toDouble(), (it.y - roughCorner.y).toDouble())
              dist <= roiSearchRadius
            }
            .take(maxFeaturesPerCorner)

    if (nearby.isNotEmpty()) {
      // Weight by scale and compute weighted average
      var sumX = 0.0
      var sumY = 0.0
      var sumW = 0.0

      for (point in nearby) {
        val dist = hypot((point.x - roughCorner.x).toDouble(), (point.y - roughCorner.y).toDouble())
        val weight = point.scale / (dist + 1.0) // Scale weighted by inverse distance
        sumX += point.x * weight
        sumY += point.y * weight
        sumW += weight
      }

      if (sumW > 0) {
        val refinedX = (sumX / sumW).toInt()
        val refinedY = (sumY / sumW).toInt()

        // Clamp to reasonable distance from rough corner
        val offsetX = (refinedX - roughCorner.x).coerceIn(-roiSearchRadius / 2, roiSearchRadius / 2)
        val offsetY = (refinedY - roughCorner.y).coerceIn(-roiSearchRadius / 2, roiSearchRadius / 2)

        return Point(
            (roughCorner.x + offsetX).coerceIn(0, image.width - 1),
            (roughCorner.y + offsetY).coerceIn(0, image.height - 1))
      }
    }

    // Fall back to edge-based refinement
    return refineSingleCornerEdges(image, roughCorner)
  }

  /** Refines a single corner using local edge analysis. */
  private fun refineSingleCornerEdges(image: BufferedImage, roughCorner: Point): Point {
    val imgWidth = image.width
    val imgHeight = image.height

    val roiSize = roiSearchRadius / 2
    val x1 = (roughCorner.x - roiSize).coerceIn(0, imgWidth - 1)
    val y1 = (roughCorner.y - roiSize).coerceIn(0, imgHeight - 1)
    val x2 = (roughCorner.x + roiSize).coerceIn(0, imgWidth - 1)
    val y2 = (roughCorner.y + roiSize).coerceIn(0, imgHeight - 1)

    val roiWidth = x2 - x1 + 1
    val roiHeight = y2 - y1 + 1

    // Compute gradients
    val gradX = IntArray(roiWidth * roiHeight)
    val gradY = IntArray(roiWidth * roiHeight)

    for (y in 0 until roiHeight) {
      for (x in 0 until roiWidth) {
        val px = x + x1
        val py = y + y1

        val p00 = luminance(image.getRGB(max(0, px - 1), max(0, py - 1)))
        val p10 = luminance(image.getRGB(px.coerceIn(0, imgWidth - 1), max(0, py - 1)))
        val p20 = luminance(image.getRGB(min(imgWidth - 1, px + 1), max(0, py - 1)))
        val p01 = luminance(image.getRGB(max(0, px - 1), py.coerceIn(0, imgHeight - 1)))
        val p21 = luminance(image.getRGB(min(imgWidth - 1, px + 1), py.coerceIn(0, imgHeight - 1)))
        val p02 = luminance(image.getRGB(max(0, px - 1), min(imgHeight - 1, py + 1)))
        val p12 = luminance(image.getRGB(px.coerceIn(0, imgWidth - 1), min(imgHeight - 1, py + 1)))
        val p22 = luminance(image.getRGB(min(imgWidth - 1, px + 1), min(imgHeight - 1, py + 1)))

        val gx = (-p00 - 2 * p01 - p02 + p20 + 2 * p21 + p22).toInt()
        val gy = (-p00 - 2 * p10 - p20 + p02 + 2 * p12 + p22).toInt()

        gradX[y * roiWidth + x] = gx
        gradY[y * roiWidth + x] = gy
      }
    }

    // Find dominant horizontal and vertical edge directions
    val verticalVotes = IntArray(roiWidth)
    val horizontalVotes = IntArray(roiHeight)

    for (y in 0 until roiHeight) {
      for (x in 0 until roiWidth) {
        val idx = y * roiWidth + x
        val gx = gradX[idx]
        val gy = gradY[idx]
        val mag = sqrt((gx * gx + gy * gy).toDouble()).toInt()

        if (mag > 50) {
          val isHorizontal = abs(gy) > abs(gx)
          if (isHorizontal && y in 2 until roiHeight - 2) {
            horizontalVotes[y] += mag
          } else if (!isHorizontal && x in 2 until roiWidth - 2) {
            verticalVotes[x] += mag
          }
        }
      }
    }

    // Find peak positions
    val vertPeak = findPeak(verticalVotes, roiWidth / 2)
    val horizPeak = findPeak(horizontalVotes, roiHeight / 2)

    // Compute refined position
    val refinedX = x1 + vertPeak
    val refinedY = y1 + horizPeak

    // Clamp to ROI bounds with some tolerance
    val offsetX = (refinedX - roughCorner.x).coerceIn(-roiSize, roiSize)
    val offsetY = (refinedY - roughCorner.y).coerceIn(-roiSize, roiSize)

    return Point(
        (roughCorner.x + offsetX).coerceIn(0, imgWidth - 1),
        (roughCorner.y + offsetY).coerceIn(0, imgHeight - 1))
  }

  /** Finds the peak position in a vote array, starting from the center. */
  private fun findPeak(votes: IntArray, center: Int): Int {
    if (votes.isEmpty()) return center

    var bestPos = center
    var bestVotes = 0

    for (i in votes.indices) {
      if (votes[i] > bestVotes) {
        bestVotes = votes[i]
        bestPos = i
      }
    }

    // If peak is too far from center, use center
    if (abs(bestPos - center) > votes.size / 4) {
      return center
    }

    return bestPos
  }

  private fun luminance(rgb: Int): Float {
    val r = (rgb shr 16) and 255
    val g = (rgb shr 8) and 255
    val b = rgb and 255
    return 0.299f * r + 0.587f * g + 0.114f * b
  }

  /** Validates and reorders corners to ensure TL, TR, BR, BL order. */
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

  /** Sorts corners into TL, TR, BR, BL order. */
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

  data class InterestPoint(val x: Int, val y: Int, val scale: Float)
}
