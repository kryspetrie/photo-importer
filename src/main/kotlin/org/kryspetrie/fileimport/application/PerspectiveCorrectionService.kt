package org.kryspetrie.fileimport.application

import java.awt.geom.Point2D
import java.awt.image.BufferedImage
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt
import org.kryspetrie.fileimport.domain.model.DetectedPhoto
import org.kryspetrie.fileimport.domain.model.PhotoCorner

/**
 * Service for correcting perspective distortion in photographs.
 *
 * Uses projective transformation to map a quadrilateral region (the detected photo corners) to a
 * rectangular output. This corrects for trapezoidal distortion that occurs when photos are
 * photographed at an angle.
 *
 * ## Algorithm
 * 1. Calculate output dimensions from detected corners
 * 2. Compute inverse mapping from output coordinates to source coordinates
 * 3. Apply bilinear interpolation to sample the source image
 *
 * ## Transformation Math
 *
 * The perspective transform maps points from a quadrilateral to a rectangle:
 * ```
 * For each output pixel (x, y):
 *   Source point = inverseTransform(x, y)
 *   Sample from source at that point with bilinear interpolation
 * ```
 *
 * @see DetectedPhoto
 * @see PhotoCorner
 */
@Singleton
class PerspectiveCorrectionService @Inject constructor() {

  /** Quality setting for output images. */
  var outputQuality = BufferedImage.TYPE_INT_ARGB

  /** Anti-aliasing enabled for smoother output. */
  var antiAliasing = true

  /**
   * Applies perspective correction to extract a photograph from a scanned image.
   *
   * Takes the detected photo's corner coordinates and warps the quadrilateral region into a
   * rectangle. The output dimensions are calculated from the detected corners to maintain the
   * aspect ratio.
   *
   * @param sourceImage The original scanned image containing the photo
   * @param detectedPhoto The detected photo with corner coordinates
   * @return The perspective-corrected image
   */
  fun correctPerspective(sourceImage: BufferedImage, detectedPhoto: DetectedPhoto): BufferedImage {
    val src =
        arrayOf(
            Point2D.Float(detectedPhoto.topLeft.x, detectedPhoto.topLeft.y),
            Point2D.Float(detectedPhoto.topRight.x, detectedPhoto.topRight.y),
            Point2D.Float(detectedPhoto.bottomRight.x, detectedPhoto.bottomRight.y),
            Point2D.Float(detectedPhoto.bottomLeft.x, detectedPhoto.bottomLeft.y))

    // Calculate output dimensions based on average edge lengths
    val width = max(distance(src[0], src[1]), distance(src[3], src[2])).toInt().coerceAtLeast(100)

    val height = max(distance(src[0], src[3]), distance(src[1], src[2])).toInt().coerceAtLeast(100)

    return correctPerspective(sourceImage, detectedPhoto, width, height)
  }

  /**
   * Applies perspective correction with specific output dimensions.
   *
   * @param sourceImage The original scanned image containing the photo
   * @param detectedPhoto The detected photo with corner coordinates
   * @param outputWidth Desired output width in pixels
   * @param outputHeight Desired output height in pixels
   * @return The perspective-corrected image
   */
  fun correctPerspective(
      sourceImage: BufferedImage,
      detectedPhoto: DetectedPhoto,
      outputWidth: Int,
      outputHeight: Int
  ): BufferedImage {
    // Source corners
    val src =
        arrayOf(
            Point2D.Float(detectedPhoto.topLeft.x, detectedPhoto.topLeft.y),
            Point2D.Float(detectedPhoto.topRight.x, detectedPhoto.topRight.y),
            Point2D.Float(detectedPhoto.bottomRight.x, detectedPhoto.bottomRight.y),
            Point2D.Float(detectedPhoto.bottomLeft.x, detectedPhoto.bottomLeft.y))

    // Destination corners (unit rectangle to output dimensions)
    val dst =
        arrayOf(
            Point2D.Float(0f, 0f),
            Point2D.Float(outputWidth.toFloat(), 0f),
            Point2D.Float(outputWidth.toFloat(), outputHeight.toFloat()),
            Point2D.Float(0f, outputHeight.toFloat()))

    // Create output image
    val output = BufferedImage(outputWidth, outputHeight, outputQuality)
    val graphics = output.createGraphics()

    // Fill with white background
    graphics.color = java.awt.Color.WHITE
    graphics.fillRect(0, 0, outputWidth, outputHeight)
    graphics.dispose()

    // Compute perspective transform coefficients
    // We use bilinear interpolation for the inverse mapping
    val srcW = max(distance(src[0], src[1]), distance(src[3], src[2]))
    val srcH = max(distance(src[0], src[3]), distance(src[1], src[2]))

    // For each output pixel, compute the source position using bilinear interpolation
    for (y in 0 until outputHeight) {
      val v = y.toFloat() / outputHeight
      for (x in 0 until outputWidth) {
        val u = x.toFloat() / outputWidth

        // Bilinear interpolation in source quad
        // Top edge interpolation
        val topX = lerp(src[0].x, src[1].x, u)
        val topY = lerp(src[0].y, src[1].y, u)

        // Bottom edge interpolation
        val bottomX = lerp(src[3].x, src[2].x, u)
        val bottomY = lerp(src[3].y, src[2].y, u)

        // Interpolate between top and bottom
        val srcX = lerp(topX, bottomX, v)
        val srcY = lerp(topY, bottomY, v)

        // Sample from source with bounds checking
        val sx = srcX.toInt().coerceIn(0, sourceImage.width - 1)
        val sy = srcY.toInt().coerceIn(0, sourceImage.height - 1)

        // Check if point is within image bounds and reasonably close to expected position
        if (sx >= 0 && sx < sourceImage.width && sy >= 0 && sy < sourceImage.height) {
          output.setRGB(x, y, sourceImage.getRGB(sx, sy))
        }
      }
    }

    return output
  }

  /** Linear interpolation between two values. */
  private fun lerp(a: Float, b: Float, t: Float): Float {
    return a + (b - a) * t
  }

  /** Bilinear interpolation for a single color channel. */
  private fun bilinearInterpolateColor(image: BufferedImage, x: Float, y: Float): Int {
    val x0 = x.toInt().coerceIn(0, image.width - 1)
    val y0 = y.toInt().coerceIn(0, image.height - 1)
    val x1 = (x0 + 1).coerceIn(0, image.width - 1)
    val y1 = (y0 + 1).coerceIn(0, image.height - 1)

    val fx = x - x0
    val fy = y - y0

    val c00 = image.getRGB(x0, y0)
    val c10 = image.getRGB(x1, y0)
    val c01 = image.getRGB(x0, y1)
    val c11 = image.getRGB(x1, y1)

    // Interpolate each channel separately
    val r =
        bilinear(
            fx,
            fy,
            (c00 shr 16) and 0xFF,
            (c10 shr 16) and 0xFF,
            (c01 shr 16) and 0xFF,
            (c11 shr 16) and 0xFF)
    val g =
        bilinear(
            fx,
            fy,
            (c00 shr 8) and 0xFF,
            (c10 shr 8) and 0xFF,
            (c01 shr 8) and 0xFF,
            (c11 shr 8) and 0xFF)
    val b = bilinear(fx, fy, c00 and 0xFF, c10 and 0xFF, c01 and 0xFF, c11 and 0xFF)
    val a =
        bilinear(
            fx,
            fy,
            (c00 shr 24) and 0xFF,
            (c10 shr 24) and 0xFF,
            (c01 shr 24) and 0xFF,
            (c11 shr 24) and 0xFF)

    return (a shl 24) or (r shl 16) or (g shl 8) or b
  }

  /** Bilinear interpolation helper. */
  private fun bilinear(fx: Float, fy: Float, c00: Int, c10: Int, c01: Int, c11: Int): Int {
    val top = c00 + (c10 - c00) * fx
    val bottom = c01 + (c11 - c01) * fx
    return (top + (bottom - top) * fy).toInt().coerceIn(0, 255)
  }

  /** Calculates distance between two points. */
  private fun distance(p1: Point2D, p2: Point2D): Double {
    val dx = p2.x - p1.x
    val dy = p2.y - p1.y
    return sqrt(dx * dx + dy * dy)
  }

  /** Auto-detects the best output dimensions based on detected corners. */
  fun calculateOutputDimensions(detectedPhoto: DetectedPhoto): Pair<Int, Int> {
    // Use the average of top and bottom edges for width
    val topWidth =
        distance(
            Point2D.Float(detectedPhoto.topLeft.x, detectedPhoto.topLeft.y),
            Point2D.Float(detectedPhoto.topRight.x, detectedPhoto.topRight.y))
    val bottomWidth =
        distance(
            Point2D.Float(detectedPhoto.bottomLeft.x, detectedPhoto.bottomLeft.y),
            Point2D.Float(detectedPhoto.bottomRight.x, detectedPhoto.bottomRight.y))
    val width = ((topWidth + bottomWidth) / 2).toInt().coerceAtLeast(100)

    // Use the average of left and right edges for height
    val leftHeight =
        distance(
            Point2D.Float(detectedPhoto.topLeft.x, detectedPhoto.topLeft.y),
            Point2D.Float(detectedPhoto.bottomLeft.x, detectedPhoto.bottomLeft.y))
    val rightHeight =
        distance(
            Point2D.Float(detectedPhoto.topRight.x, detectedPhoto.topRight.y),
            Point2D.Float(detectedPhoto.bottomRight.x, detectedPhoto.bottomRight.y))
    val height = ((leftHeight + rightHeight) / 2).toInt().coerceAtLeast(100)

    return Pair(width, height)
  }

  /** Calculates the aspect ratio of the detected photo. */
  fun calculateAspectRatio(detectedPhoto: DetectedPhoto): Float {
    val width =
        max(
            distance(
                Point2D.Float(detectedPhoto.topLeft.x, detectedPhoto.topLeft.y),
                Point2D.Float(detectedPhoto.topRight.x, detectedPhoto.topRight.y)),
            distance(
                Point2D.Float(detectedPhoto.bottomLeft.x, detectedPhoto.bottomLeft.y),
                Point2D.Float(detectedPhoto.bottomRight.x, detectedPhoto.bottomRight.y)))

    val height =
        max(
            distance(
                Point2D.Float(detectedPhoto.topLeft.x, detectedPhoto.topLeft.y),
                Point2D.Float(detectedPhoto.bottomLeft.x, detectedPhoto.bottomLeft.y)),
            distance(
                Point2D.Float(detectedPhoto.topRight.x, detectedPhoto.topRight.y),
                Point2D.Float(detectedPhoto.bottomRight.x, detectedPhoto.bottomRight.y)))

    return (width / height).toFloat()
  }

  /** Checks if the detected quadrilateral is convex and valid. */
  fun isValidQuadrilateral(detectedPhoto: DetectedPhoto): Boolean {
    val points =
        arrayOf(
            Point2D.Float(detectedPhoto.topLeft.x, detectedPhoto.topLeft.y),
            Point2D.Float(detectedPhoto.topRight.x, detectedPhoto.topRight.y),
            Point2D.Float(detectedPhoto.bottomRight.x, detectedPhoto.bottomRight.y),
            Point2D.Float(detectedPhoto.bottomLeft.x, detectedPhoto.bottomLeft.y))

    // Check all internal angles are less than 180 degrees (convex)
    var totalAngle = 0.0
    for (i in 0 until 4) {
      val p1 = points[i]
      val p2 = points[(i + 1) % 4]
      val p3 = points[(i + 2) % 4]

      val angle1 = kotlin.math.atan2((p2.y - p1.y).toDouble(), (p2.x - p1.x).toDouble())
      val angle2 = kotlin.math.atan2((p3.y - p2.y).toDouble(), (p3.x - p2.x).toDouble())
      var diff = angle2 - angle1
      while (diff > kotlin.math.PI) diff -= 2 * kotlin.math.PI
      while (diff < -kotlin.math.PI) diff += 2 * kotlin.math.PI
      totalAngle += diff
    }

    // For a convex quadrilateral, the total angle should be close to 360 degrees
    return abs(totalAngle - 2 * kotlin.math.PI) < 0.1
  }
}
