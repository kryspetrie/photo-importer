package org.kryspetrie.fileimport.infrastructure.wizard

import java.awt.image.BufferedImage

/**
 * Applies perspective transformation to map a quadrilateral to a rectangle. Used for correcting
 * trapezoidal distortion in photos.
 */
class PerspectiveTransformer {

  /**
   * Applies perspective transform to the source image.
   *
   * @param source Source image
   * @param sourceCorners The four corners of the quadrilateral in the source
   * @param targetWidth Target output width
   * @param targetHeight Target output height
   * @return Transformed image
   */
  fun apply(
      source: BufferedImage,
      sourceCorners: BoundingBoxCorners,
      targetWidth: Int,
      targetHeight: Int
  ): BufferedImage {
    // Create target image
    val target = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB)

    // Create transform matrix that maps source quadrilateral to target rectangle
    // Using homography transformation approach

    // Source points (in order: TL, TR, BR, BL)
    val srcPts =
        arrayOf(
            doubleArrayOf(sourceCorners.topLeft.x, sourceCorners.topLeft.y),
            doubleArrayOf(sourceCorners.topRight.x, sourceCorners.topRight.y),
            doubleArrayOf(sourceCorners.bottomRight.x, sourceCorners.bottomRight.y),
            doubleArrayOf(sourceCorners.bottomLeft.x, sourceCorners.bottomLeft.y))

    // Target points (unit square mapped to target dimensions)
    val dstPts =
        arrayOf(
            doubleArrayOf(0.0, 0.0),
            doubleArrayOf(targetWidth.toDouble(), 0.0),
            doubleArrayOf(targetWidth.toDouble(), targetHeight.toDouble()),
            doubleArrayOf(0.0, targetHeight.toDouble()))

    // Compute homography matrix (3x3)
    val H = computeHomography(srcPts, dstPts)

    // Apply transformation to each pixel in target
    val graphics = target.createGraphics()

    // For each target pixel, find corresponding source pixel
    for (y in 0 until targetHeight) {
      for (x in 0 until targetWidth) {
        // Apply inverse homography to find source coordinates
        val srcCoord = applyHomographyInverse(H, x.toDouble(), y.toDouble())

        // Sample from source (bilinear interpolation)
        val srcX = srcCoord[0]
        val srcY = srcCoord[1]

        if (srcX >= 0 && srcX < source.width && srcY >= 0 && srcY < source.height) {
          val pixel = bilinearSample(source, srcX, srcY)
          target.setRGB(x, y, pixel)
        }
      }
    }

    graphics.dispose()
    return target
  }

  /** Computes homography matrix using DLT (Direct Linear Transform) algorithm. */
  private fun computeHomography(
      src: Array<DoubleArray>,
      dst: Array<DoubleArray>
  ): Array<DoubleArray> {
    // Set up the 8x8 matrix for solving
    val A = Array(8) { DoubleArray(8) }
    val b = DoubleArray(8)

    for (i in 0 until 4) {
      val sx = src[i][0]
      val sy = src[i][1]
      val dx = dst[i][0]
      val dy = dst[i][1]

      A[i * 2][0] = sx
      A[i * 2][1] = sy
      A[i * 2][2] = 1.0
      A[i * 2][3] = 0.0
      A[i * 2][4] = 0.0
      A[i * 2][5] = 0.0
      A[i * 2][6] = -dx * sx
      A[i * 2][7] = -dx * sy

      A[i * 2 + 1][0] = 0.0
      A[i * 2 + 1][1] = 0.0
      A[i * 2 + 1][2] = 0.0
      A[i * 2 + 1][3] = sx
      A[i * 2 + 1][4] = sy
      A[i * 2 + 1][5] = 1.0
      A[i * 2 + 1][6] = -dy * sx
      A[i * 2 + 1][7] = -dy * sy

      b[i * 2] = dx
      b[i * 2 + 1] = dy
    }

    // Solve using Gaussian elimination
    val h = solveLinearSystem(A, b)

    // Build 3x3 matrix
    return arrayOf(
        doubleArrayOf(h[0], h[1], h[2]),
        doubleArrayOf(h[3], h[4], h[5]),
        doubleArrayOf(h[6], h[7], 1.0))
  }

  /** Solves a linear system Ax = b using Gaussian elimination with partial pivoting. */
  private fun solveLinearSystem(A: Array<DoubleArray>, b: DoubleArray): DoubleArray {
    val n = b.size
    val augmented = Array(n) { i -> DoubleArray(n + 1) { j -> if (j < n) A[i][j] else b[i] } }

    // Forward elimination with partial pivoting
    for (col in 0 until n) {
      // Find pivot
      var maxRow = col
      for (row in col + 1 until n) {
        if (kotlin.math.abs(augmented[row][col]) > kotlin.math.abs(augmented[maxRow][col])) {
          maxRow = row
        }
      }

      // Swap rows
      val temp = augmented[col]
      augmented[col] = augmented[maxRow]
      augmented[maxRow] = temp

      // Eliminate
      for (row in col + 1 until n) {
        val factor = augmented[row][col] / augmented[col][col]
        for (j in col..n) {
          augmented[row][j] -= factor * augmented[col][j]
        }
      }
    }

    // Back substitution
    val x = DoubleArray(n)
    for (i in n - 1 downTo 0) {
      x[i] = augmented[i][n]
      for (j in i + 1 until n) {
        x[i] -= augmented[i][j] * x[j]
      }
      x[i] /= augmented[i][i]
    }

    return x
  }

  /** Applies the inverse of the homography matrix to map target coordinates to source. */
  private fun applyHomographyInverse(H: Array<DoubleArray>, x: Double, y: Double): DoubleArray {
    // Apply H^(-1) to (x, y, 1)
    val w = H[2][0] * x + H[2][1] * y + H[2][2]

    val srcX = (H[0][0] * x + H[0][1] * y + H[0][2]) / w
    val srcY = (H[1][0] * x + H[1][1] * y + H[1][2]) / w

    return doubleArrayOf(srcX, srcY)
  }

  /** Performs bilinear interpolation to sample a pixel. */
  private fun bilinearSample(image: BufferedImage, x: Double, y: Double): Int {
    val x0 = x.toInt()
    val y0 = y.toInt()
    val x1 = x0 + 1
    val y1 = y0 + 1

    // Clamp to image bounds
    val clampedX0 = x0.coerceIn(0, image.width - 1)
    val clampedY0 = y0.coerceIn(0, image.height - 1)
    val clampedX1 = x1.coerceIn(0, image.width - 1)
    val clampedY1 = y1.coerceIn(0, image.height - 1)

    // Get corner pixels
    val p00 = image.getRGB(clampedX0, clampedY0)
    val p10 = image.getRGB(clampedX1, clampedY0)
    val p01 = image.getRGB(clampedX0, clampedY1)
    val p11 = image.getRGB(clampedX1, clampedY1)

    // Interpolation weights
    val fx = x - x0
    val fy = y - y0

    // Bilinear interpolation for each channel
    val r = interpolate(getRed(p00), getRed(p10), getRed(p01), getRed(p11), fx, fy)
    val g = interpolate(getGreen(p00), getGreen(p10), getGreen(p01), getGreen(p11), fx, fy)
    val b = interpolate(getBlue(p00), getBlue(p10), getBlue(p01), getBlue(p11), fx, fy)

    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
  }

  private fun interpolate(c00: Int, c10: Int, c01: Int, c11: Int, fx: Double, fy: Double): Int {
    val c0 = c00 + (c10 - c00) * fx
    val c1 = c01 + (c11 - c01) * fx
    return (c0 + (c1 - c0) * fy).toInt().coerceIn(0, 255)
  }

  private fun getRed(rgb: Int): Int = (rgb shr 16) and 0xFF

  private fun getGreen(rgb: Int): Int = (rgb shr 8) and 0xFF

  private fun getBlue(rgb: Int): Int = rgb and 0xFF

  companion object {
    /** Creates a perspective transformer. */
    fun create(): PerspectiveTransformer = PerspectiveTransformer()
  }
}
