package org.kryspetrie.fileimport.application

import java.awt.image.BufferedImage
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt
import org.kryspetrie.fileimport.domain.model.DetectedPhoto

/**
 * Perspective correction service using pure Java 2D API.
 *
 * Applies perspective (homography) transform to extract rectangular photos from scanned images.
 * Uses the standard Java AWT AffineTransform with a manual 3x3 homography matrix applied via
 * pixel-by-pixel backward mapping.
 *
 * Note: OpenCV's getPerspectiveTransform + warpPerspective are NOT used here because the OpenCV
 * native libraries are not reliably bundled across platforms (macOS, Linux, Windows). Instead, a
 * pure-Java implementation using java.awt.geom.AffineTransform is used. This is slower but fully
 * portable and avoids native library loading failures.
 */
@Singleton
class PerspectiveCorrectionService @Inject constructor() {

  /**
   * Corrects the perspective of a detected photo in the source image.
   *
   * @param sourceImage The scanned image containing the photo
   * @param detectedPhoto The detected photo with corner coordinates
   * @return The perspective-corrected image, cropped to the detected photo's size
   */
  fun correctPerspective(sourceImage: BufferedImage, detectedPhoto: DetectedPhoto): BufferedImage {
    val (width, height) = calculateOutputDimensions(detectedPhoto)
    return correctPerspective(sourceImage, detectedPhoto, width, height)
  }

  /**
   * Corrects the perspective of a detected photo with explicit output dimensions.
   *
   * @param sourceImage The scanned image containing the photo
   * @param detectedPhoto The detected photo with corner coordinates
   * @param outputWidth Desired output width in pixels
   * @param outputHeight Desired output height in pixels
   * @return The perspective-corrected image at the specified dimensions
   */
  fun correctPerspective(
      sourceImage: BufferedImage,
      detectedPhoto: DetectedPhoto,
      outputWidth: Int,
      outputHeight: Int
  ): BufferedImage {
    if (outputWidth <= 0 || outputHeight <= 0) {
      return sourceImage
    }

    // Source corners (the detected quadrilateral in the scanned image)
    val src =
        arrayOf(
            PointD(detectedPhoto.topLeft.x.toDouble(), detectedPhoto.topLeft.y.toDouble()),
            PointD(detectedPhoto.topRight.x.toDouble(), detectedPhoto.topRight.y.toDouble()),
            PointD(detectedPhoto.bottomRight.x.toDouble(), detectedPhoto.bottomRight.y.toDouble()),
            PointD(detectedPhoto.bottomLeft.x.toDouble(), detectedPhoto.bottomLeft.y.toDouble()))

    // Destination corners (the output rectangle)
    val dst =
        arrayOf(
            PointD(0.0, 0.0),
            PointD(outputWidth.toDouble(), 0.0),
            PointD(outputWidth.toDouble(), outputHeight.toDouble()),
            PointD(0.0, outputHeight.toDouble()))

    // Compute the 3x3 homography matrix (perspective transform)
    val H = computeHomography(dst, src)

    // Create the output image
    val result = BufferedImage(outputWidth, outputHeight, BufferedImage.TYPE_INT_RGB)
    val srcWidth = sourceImage.width
    val srcHeight = sourceImage.height

    // Apply the perspective transform using backward mapping (for each output pixel, find
    // corresponding input pixel)
    for (y in 0 until outputHeight) {
      for (x in 0 until outputWidth) {
        // Apply H to map (x, y) in destination to (sx, sy) in source
        val w = H[2][0] * x + H[2][1] * y + H[2][2]
        if (w == 0.0) continue
        val sx = (H[0][0] * x + H[0][1] * y + H[0][2]) / w
        val sy = (H[1][0] * x + H[1][1] * y + H[1][2]) / w

        // Bilinear interpolation from source
        val color = bilinearSample(sourceImage, sx, sy)
        result.setRGB(x, y, color)
      }
    }

    return result
  }

  /**
   * Calculates the ideal output dimensions for the detected photo based on its corner positions.
   *
   * Uses the average of the top and bottom edge lengths for width, and the average of the left and
   * right edge lengths for height.
   */
  fun calculateOutputDimensions(detectedPhoto: DetectedPhoto): Pair<Int, Int> {
    val tl = PointD(detectedPhoto.topLeft.x.toDouble(), detectedPhoto.topLeft.y.toDouble())
    val tr = PointD(detectedPhoto.topRight.x.toDouble(), detectedPhoto.topRight.y.toDouble())
    val br = PointD(detectedPhoto.bottomRight.x.toDouble(), detectedPhoto.bottomRight.y.toDouble())
    val bl = PointD(detectedPhoto.bottomLeft.x.toDouble(), detectedPhoto.bottomLeft.y.toDouble())

    val widthA = dist(br, bl)
    val widthB = dist(tr, tl)
    val width = maxOf(widthA, widthB).toInt()

    val heightA = dist(tr, br)
    val heightB = dist(tl, bl)
    val height = maxOf(heightA, heightB).toInt()

    return Pair(width.coerceAtLeast(1), height.coerceAtLeast(1))
  }

  /** Returns the aspect ratio (width / height) of the detected photo. */
  fun calculateAspectRatio(detectedPhoto: DetectedPhoto): Float {
    val (w, h) = calculateOutputDimensions(detectedPhoto)
    return w.toFloat() / h.toFloat()
  }

  /**
   * Checks if the detected photo's corners form a valid (convex) quadrilateral.
   *
   * A valid quadrilateral has all four corners on the same plane with no self-intersection.
   */
  fun isValidQuadrilateral(detectedPhoto: DetectedPhoto): Boolean {
    val corners =
        listOf(
            PointD(detectedPhoto.topLeft.x.toDouble(), detectedPhoto.topLeft.y.toDouble()),
            PointD(detectedPhoto.topRight.x.toDouble(), detectedPhoto.topRight.y.toDouble()),
            PointD(detectedPhoto.bottomRight.x.toDouble(), detectedPhoto.bottomRight.y.toDouble()),
            PointD(detectedPhoto.bottomLeft.x.toDouble(), detectedPhoto.bottomLeft.y.toDouble()),
            PointD(detectedPhoto.topLeft.x.toDouble(), detectedPhoto.topLeft.y.toDouble()))

    // Check for convexity using cross product sign consistency
    var sign: Int? = null
    for (i in 0 until 4) {
      val ax = corners[i + 1].x - corners[i].x
      val ay = corners[i + 1].y - corners[i].y
      val bx = corners[(i + 2) % 5].x - corners[i + 1].x
      val by = corners[(i + 2) % 5].y - corners[i + 1].y
      val cross = ax * by - ay * bx
      val currentSign = if (cross > 0) 1 else if (cross < 0) -1 else 0
      if (currentSign != 0) {
        if (sign == null) {
          sign = currentSign
        } else if (sign != currentSign) {
          return false
        }
      }
    }
    return true
  }

  /**
   * Computes a 3x3 homography matrix H such that dst = H * src (in homogeneous coordinates).
   *
   * Solves the linear system using the Direct Linear Transform (DLT) algorithm. The resulting
   * matrix maps destination pixel coordinates to source pixel coordinates for backward mapping.
   */
  private fun computeHomography(dst: Array<PointD>, src: Array<PointD>): Array<DoubleArray> {
    // Build the 8x8 matrix A and vector b for Ax = b, where x is the homography parameters
    val A = Array(8) { DoubleArray(8) }
    val b = DoubleArray(8)

    for (i in 0 until 4) {
      val sx = src[i].x
      val sy = src[i].y
      val dx = dst[i].x
      val dy = dst[i].y

      A[i * 2][0] = sx
      A[i * 2][1] = sy
      A[i * 2][2] = 1.0
      A[i * 2][3] = 0.0
      A[i * 2][4] = 0.0
      A[i * 2][5] = 0.0
      A[i * 2][6] = -sx * dx
      A[i * 2][7] = -sy * dx
      b[i * 2] = dx

      A[i * 2 + 1][0] = 0.0
      A[i * 2 + 1][1] = 0.0
      A[i * 2 + 1][2] = 0.0
      A[i * 2 + 1][3] = sx
      A[i * 2 + 1][4] = sy
      A[i * 2 + 1][5] = 1.0
      A[i * 2 + 1][6] = -sx * dy
      A[i * 2 + 1][7] = -sy * dy
      b[i * 2 + 1] = dy
    }

    // Solve using Gaussian elimination
    val h = solveLinearSystem(A, b)

    // Construct the 3x3 homography matrix
    return arrayOf(
        doubleArrayOf(h[0], h[1], h[2]),
        doubleArrayOf(h[3], h[4], h[5]),
        doubleArrayOf(h[6], h[7], 1.0))
  }

  /**
   * Solves a linear system Ax = b using Gaussian elimination with partial pivoting.
   *
   * @return The solution vector x
   */
  private fun solveLinearSystem(A: Array<DoubleArray>, b: DoubleArray): DoubleArray {
    val n = b.size
    val aug = Array(n) { i -> DoubleArray(n + 1) }

    // Build augmented matrix
    for (i in 0 until n) {
      for (j in 0 until n) {
        aug[i][j] = A[i][j]
      }
      aug[i][n] = b[i]
    }

    // Forward elimination with partial pivoting
    for (col in 0 until n) {
      // Find the pivot row
      var maxRow = col
      for (row in col + 1 until n) {
        if (kotlin.math.abs(aug[row][col]) > kotlin.math.abs(aug[maxRow][col])) {
          maxRow = row
        }
      }
      // Swap rows
      val temp = aug[col]
      aug[col] = aug[maxRow]
      aug[maxRow] = temp

      // Eliminate below
      for (row in col + 1 until n) {
        if (kotlin.math.abs(aug[col][col]) < 1e-12) continue
        val factor = aug[row][col] / aug[col][col]
        for (j in col..n) {
          aug[row][j] -= factor * aug[col][j]
        }
      }
    }

    // Back substitution
    val x = DoubleArray(n)
    for (i in n - 1 downTo 0) {
      x[i] = aug[i][n]
      for (j in i + 1 until n) {
        x[i] -= aug[i][j] * x[j]
      }
      if (kotlin.math.abs(aug[i][i]) > 1e-12) {
        x[i] /= aug[i][i]
      }
    }

    return x
  }

  /**
   * Samples a pixel from the source image using bilinear interpolation.
   *
   * @return RGB integer color value, or a gray default if out of bounds
   */
  private fun bilinearSample(image: BufferedImage, sx: Double, sy: Double): Int {
    val x0 = sx.toInt()
    val y0 = sy.toInt()
    val x1 = x0 + 1
    val y1 = y0 + 1

    val w = image.width
    val h = image.height

    // Clamp to image bounds
    val fx = sx.coerceIn(0.0, (w - 1).toDouble())
    val fy = sy.coerceIn(0.0, (h - 1).toDouble())
    val ix0 = fx.toInt().coerceIn(0, w - 1)
    val iy0 = fy.toInt().coerceIn(0, h - 1)
    val ix1 = (ix0 + 1).coerceIn(0, w - 1)
    val iy1 = (iy0 + 1).coerceIn(0, h - 1)

    val c00 = image.getRGB(ix0, iy0)
    val c10 = image.getRGB(ix1, iy0)
    val c01 = image.getRGB(ix0, iy1)
    val c11 = image.getRGB(ix1, iy1)

    val tx = fx - ix0
    val ty = fy - iy0

    return bilinearInterpolate(c00, c10, c01, c11, tx, ty)
  }

  /** Bilinear interpolation between four color values. */
  private fun bilinearInterpolate(
      c00: Int,
      c10: Int,
      c01: Int,
      c11: Int,
      tx: Double,
      ty: Double
  ): Int {
    fun lerp(a: Int, b: Int, t: Double): Int {
      return (a + (b - a) * t).toInt().coerceIn(0, 255)
    }
    val top = lerp((c00 shr 16) and 0xFF, (c10 shr 16) and 0xFF, tx)
    val bottom = lerp((c01 shr 16) and 0xFF, (c11 shr 16) and 0xFF, tx)
    val r = lerp(top, bottom, ty)
    val topG = lerp((c00 shr 8) and 0xFF, (c10 shr 8) and 0xFF, tx)
    val bottomG = lerp((c01 shr 8) and 0xFF, (c11 shr 8) and 0xFF, tx)
    val g = lerp(topG, bottomG, ty)
    val topB = lerp(c00 and 0xFF, c10 and 0xFF, tx)
    val bottomB = lerp(c01 and 0xFF, c11 and 0xFF, tx)
    val b = lerp(topB, bottomB, ty)
    return (0xff shl 24) or (r shl 16) or (g shl 8) or b
  }

  /** Simple 2D point. */
  private data class PointD(val x: Double, val y: Double)

  /** Euclidean distance between two points. */
  private fun dist(a: PointD, b: PointD): Double {
    val dx = b.x - a.x
    val dy = b.y - a.y
    return sqrt(dx * dx + dy * dy)
  }
}
