package org.kryspetrie.fileimport.infrastructure.photoscan.yolo

import java.awt.image.BufferedImage
import kotlin.math.*

/**
 * Pure utility functions for image processing operations used by YOLO-based photo scanning.
 * Extracted as stateless singletons for reuse across multiple services.
 */
object ImageProcessingUtils {

    /** Compute the median of a float array. */
    fun median(values: FloatArray): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[mid - 1] + sorted[mid]) / 2f
        } else {
            sorted[mid]
        }
    }

    /**
     * Fit a weighted least-squares line (ax + by + c = 0, a²+b²=1) to a set of weighted points.
     * Returns (a, b, c, linearity) or null if fitting fails. Linearity = ratio of larger eigenvalue
     * to smaller (higher = more linear).
     */
    fun fitWeightedLine(points: List<Triple<Float, Float, Float>>): FloatArray? {
        if (points.size < 3) return null

        val totalWeight = points.sumOf { it.third.toDouble() }.toFloat()
        if (totalWeight < 1e-6f) return null

        val mx = points.sumOf { (it.first * it.third).toDouble() }.toFloat() / totalWeight
        val my = points.sumOf { (it.second * it.third).toDouble() }.toFloat() / totalWeight

        var covXX = 0f
        var covXY = 0f
        var covYY = 0f
        for ((x, y, w) in points) {
            val dx = x - mx
            val dy = y - my
            covXX += w * dx * dx
            covXY += w * dx * dy
            covYY += w * dy * dy
        }
        covXX /= totalWeight
        covXY /= totalWeight
        covYY /= totalWeight

        // Compute eigenvectors of the 2x2 covariance matrix
        val trace = covXX + covYY
        val det = covXX * covYY - covXY * covXY
        if (trace == 0f) return null

        val discriminant = trace * trace - 4 * det
        val eigenVal = (trace - sqrt(maxOf(0f, discriminant))).toFloat() / 2f

        var a = covXY
        var b = eigenVal - covXX
        val len = sqrt(a * a + b * b)
        if (len < 1e-6f) return null
        a /= len
        b /= len
        val c = -(a * mx + b * my)

        val linearity =
            if (abs(det) > 1e-10f)
                maxOf(eigenVal.coerceAtLeast(0.001f), trace / 2f - eigenVal) /
                    maxOf(abs(det).sqrt(), 0.001f)
            else 1000f

        return floatArrayOf(a, b, c, linearity)
    }

    /** Intersect two lines in ax+by+c=0 form. Returns (x, y) or null if lines are parallel. */
    fun intersectLines(line1: FloatArray, line2: FloatArray): Pair<Float, Float>? {
        val (a1, b1, c1) = Triple(line1[0], line1[1], line1[2])
        val (a2, b2, c2) = Triple(line2[0], line2[1], line2[2])
        val det = a1 * b2 - a2 * b1
        if (abs(det) < 1e-6f) return null
        val x = (b1 * c2 - b2 * c1) / det
        val y = (a2 * c1 - a1 * c2) / det
        return Pair(x, y)
    }

    /** Convert image to grayscale array. */
    fun toGrayscaleArray(image: BufferedImage): FloatArray {
        val w = image.width
        val h = image.height
        val gray = FloatArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val rgb = image.getRGB(x, y)
                val r = (rgb shr 16) and 0xFF
                val g = (rgb shr 8) and 0xFF
                val b = rgb and 0xFF
                gray[y * w + x] = 0.299f * r + 0.587f * g + 0.114f * b
            }
        }
        return gray
    }

    /** Compute Sobel gradients from grayscale image. */
    fun computeSobelGradients(
        gray: FloatArray,
        imgW: Int,
        imgH: Int,
    ): Triple<FloatArray, FloatArray, FloatArray> {
        val gradX = FloatArray(imgW * imgH)
        val gradY = FloatArray(imgW * imgH)
        val gradMag = FloatArray(imgW * imgH)

        for (y in 1 until imgH - 1) {
            for (x in 1 until imgW - 1) {
                val gx =
                    -gray[(y - 1) * imgW + (x - 1)] -
                        2f * gray[y * imgW + (x - 1)] -
                        gray[(y + 1) * imgW + (x - 1)] +
                        gray[(y - 1) * imgW + (x + 1)] +
                        2f * gray[y * imgW + (x + 1)] +
                        gray[(y + 1) * imgW + (x + 1)]
                val gy =
                    -gray[(y - 1) * imgW + (x - 1)] -
                        2f * gray[(y - 1) * imgW + x] -
                        gray[(y - 1) * imgW + (x + 1)] +
                        gray[(y + 1) * imgW + (x - 1)] +
                        2f * gray[(y + 1) * imgW + x] +
                        gray[(y + 1) * imgW + (x + 1)]
                val idx = y * imgW + x
                gradX[idx] = gx
                gradY[idx] = gy
                gradMag[idx] = sqrt(gx * gx + gy * gy)
            }
        }
        return Triple(gradX, gradY, gradMag)
    }

}

/** Inline sqrt for floats. */
fun Float.sqrt(): Float = sqrt(this)