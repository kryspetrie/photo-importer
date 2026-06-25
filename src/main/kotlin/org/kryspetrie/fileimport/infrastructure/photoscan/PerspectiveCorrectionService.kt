package org.kryspetrie.fileimport.infrastructure.photoscan

import boofcv.abst.geo.h.HomographyDLT_to_Epipolar
import boofcv.factory.geo.FactoryMultiView
import boofcv.struct.geo.AssociatedPair
import georegression.struct.point.Point2D_F64
import java.awt.image.BufferedImage
import kotlin.math.sqrt
import org.ejml.data.DMatrixRMaj
import org.kryspetrie.fileimport.domain.model.DetectedPhoto
import org.kryspetrie.fileimport.domain.model.ProcessedImage
import org.kryspetrie.fileimport.domain.port.PerspectiveCorrectionPort
import org.kryspetrie.fileimport.infrastructure.adapter.toBufferedImage
import org.kryspetrie.fileimport.infrastructure.adapter.toProcessedImage

/**
 * Perspective correction service using BoofCV for homography computation.
 *
 * Applies perspective (homography) transform to extract rectangular photos from scanned images.
 * Uses BoofCV's [FactoryMultiView.homographyDLT] for robust homography estimation with DLT (Direct
 * Linear Transform), replacing the hand-rolled Gaussian elimination solver.
 *
 * The pixel-by-pixel backward mapping with bilinear interpolation is retained for maximum
 * portability across platforms without native library dependencies.
 *
 * ## Why not OpenCV?
 * OpenCV's getPerspectiveTransform + warpPerspective are NOT used because the OpenCV native
 * libraries are not reliably bundled across platforms (macOS, Linux, Windows). BoofCV is pure Java
 * and already a project dependency.
 */
class PerspectiveCorrectionService : PerspectiveCorrectionPort {

    /**
     * Corrects the perspective of a detected photo, using domain-level [ProcessedImage].
     *
     * Converts to/from [BufferedImage] internally, keeping AWT out of the domain layer.
     */
    override fun correctPerspective(
        sourceImage: ProcessedImage,
        detectedPhoto: DetectedPhoto,
    ): ProcessedImage {
        val bufferedSource = sourceImage.toBufferedImage()
        val result = correctPerspective(bufferedSource, detectedPhoto)
        return result.toProcessedImage()
    }

    /**
     * Corrects the perspective of a detected photo in the source image.
     *
     * @param sourceImage The scanned image containing the photo
     * @param detectedPhoto The detected photo with corner coordinates
     * @return The perspective-corrected image, cropped to the detected photo's size
     */
    fun correctPerspective(
        sourceImage: BufferedImage,
        detectedPhoto: DetectedPhoto,
    ): BufferedImage {
        val (width, height) = calculateOutputDimensions(detectedPhoto)
        return correctPerspective(sourceImage, detectedPhoto, width, height)
    }

    /**
     * Corrects the perspective of a detected photo with explicit output dimensions.
     *
     * Uses BoofCV to compute the homography matrix, then applies backward mapping with bilinear
     * interpolation for each output pixel.
     *
     * @param sourceImage The scanned image containing the photo
     * @param detectedPhoto The detected photo with corner coordinates
     * @param outputWidth Desired output width in pixels
     * @param outputHeight Desired output height in pixels
     * @return The perspective-corrected image at the specified dimensions
     */
    @Suppress("VariableNaming")
    fun correctPerspective(
        sourceImage: BufferedImage,
        detectedPhoto: DetectedPhoto,
        outputWidth: Int,
        outputHeight: Int,
    ): BufferedImage {
        if (outputWidth <= 0 || outputHeight <= 0) {
            return sourceImage
        }

        // Source corners (the detected quadrilateral in the scanned image)
        val srcCorners =
            arrayOf(
                Point2D_F64(detectedPhoto.topLeft.x.toDouble(), detectedPhoto.topLeft.y.toDouble()),
                Point2D_F64(
                    detectedPhoto.topRight.x.toDouble(),
                    detectedPhoto.topRight.y.toDouble(),
                ),
                Point2D_F64(
                    detectedPhoto.bottomRight.x.toDouble(),
                    detectedPhoto.bottomRight.y.toDouble(),
                ),
                Point2D_F64(
                    detectedPhoto.bottomLeft.x.toDouble(),
                    detectedPhoto.bottomLeft.y.toDouble(),
                ),
            )

        // Destination corners (the output rectangle)
        val dstCorners =
            arrayOf(
                Point2D_F64(0.0, 0.0),
                Point2D_F64(outputWidth.toDouble(), 0.0),
                Point2D_F64(outputWidth.toDouble(), outputHeight.toDouble()),
                Point2D_F64(0.0, outputHeight.toDouble()),
            )

        // Compute homography using BoofCV DLT
        val H = computeHomography(dstCorners, srcCorners)

        // Create the output image
        val result = BufferedImage(outputWidth, outputHeight, BufferedImage.TYPE_INT_RGB)

        // Apply the perspective transform using backward mapping — for each output pixel,
        // find corresponding input pixel via the inverse homography
        val h00 = H[0, 0]
        val h01 = H[0, 1]
        val h02 = H[0, 2]
        val h10 = H[1, 0]
        val h11 = H[1, 1]
        val h12 = H[1, 2]
        val h20 = H[2, 0]
        val h21 = H[2, 1]
        val h22 = H[2, 2]

        for (y in 0 until outputHeight) {
            for (x in 0 until outputWidth) {
                val xd = x.toDouble()
                val yd = y.toDouble()

                // Apply H to map destination (x,y) → source (sx, sy)
                val w = h20 * xd + h21 * yd + h22
                if (w == 0.0) continue
                val sx = (h00 * xd + h01 * yd + h02) / w
                val sy = (h10 * xd + h11 * yd + h12) / w

                // Bilinear interpolation from source
                val isXInBounds = sx >= 0 && sx < sourceImage.width
                val isYInBounds = sy >= 0 && sy < sourceImage.height
                if (isXInBounds && isYInBounds) {
                    val color = bilinearSample(sourceImage, sx, sy)
                    result.setRGB(x, y, color)
                }
            }
        }

        return result
    }

    /**
     * Calculates the ideal output dimensions for the detected photo based on its corner positions.
     */
    /**
     * Calculates the ideal output dimensions for the detected photo based on its corner positions.
     */
    override fun calculateOutputDimensions(detectedPhoto: DetectedPhoto): Pair<Int, Int> {
        val tl = Point2D_F64(detectedPhoto.topLeft.x.toDouble(), detectedPhoto.topLeft.y.toDouble())
        val tr =
            Point2D_F64(detectedPhoto.topRight.x.toDouble(), detectedPhoto.topRight.y.toDouble())
        val br =
            Point2D_F64(
                detectedPhoto.bottomRight.x.toDouble(),
                detectedPhoto.bottomRight.y.toDouble(),
            )
        val bl =
            Point2D_F64(
                detectedPhoto.bottomLeft.x.toDouble(),
                detectedPhoto.bottomLeft.y.toDouble(),
            )

        val widthA = dist(br, bl)
        val widthB = dist(tr, tl)
        val width = maxOf(widthA, widthB).toInt()

        val heightA = dist(tr, br)
        val heightB = dist(tl, bl)
        val height = maxOf(heightA, heightB).toInt()

        return Pair(width.coerceAtLeast(1), height.coerceAtLeast(1))
    }

    /** Returns the aspect ratio (width / height) of the detected photo. */
    override fun calculateAspectRatio(detectedPhoto: DetectedPhoto): Float {
        val (w, h) = calculateOutputDimensions(detectedPhoto)
        return w.toFloat() / h.toFloat()
    }

    /** Checks if the detected photo's corners form a valid (convex) quadrilateral. */
    override fun isValidQuadrilateral(detectedPhoto: DetectedPhoto): Boolean {
        val corners =
            listOf(
                Point2D_F64(detectedPhoto.topLeft.x.toDouble(), detectedPhoto.topLeft.y.toDouble()),
                Point2D_F64(
                    detectedPhoto.topRight.x.toDouble(),
                    detectedPhoto.topRight.y.toDouble(),
                ),
                Point2D_F64(
                    detectedPhoto.bottomRight.x.toDouble(),
                    detectedPhoto.bottomRight.y.toDouble(),
                ),
                Point2D_F64(
                    detectedPhoto.bottomLeft.x.toDouble(),
                    detectedPhoto.bottomLeft.y.toDouble(),
                ),
                Point2D_F64(detectedPhoto.topLeft.x.toDouble(), detectedPhoto.topLeft.y.toDouble()),
            )

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
     * Computes a 3x3 homography matrix using BoofCV's DLT algorithm.
     *
     * BoofCV's [FactoryMultiView.homographyDLT] uses Direct Linear Transform with proper
     * normalization, which is more numerically stable than the hand-rolled Gaussian elimination it
     * replaces.
     *
     * @param dst Destination points (4 corners of the output rectangle)
     * @param src Source points (4 corners of the detected quadrilateral)
     * @return DMatrixRMaj (3×3 homography matrix) mapping dst → src for backward mapping
     */
    @Suppress("VariableNaming")
    private fun computeHomography(dst: Array<Point2D_F64>, src: Array<Point2D_F64>): DMatrixRMaj {
        // Build associated pairs for BoofCV: dst → src (backward mapping)
        val pairs = ArrayList<AssociatedPair>()
        for (i in dst.indices) {
            pairs.add(AssociatedPair(dst[i].copy(), src[i].copy()))
        }

        // Use BoofCV's DLT homography estimator with normalization for numerical stability
        val estimator: HomographyDLT_to_Epipolar = FactoryMultiView.homographyDLT(true)
        val H = DMatrixRMaj(3, 3)
        val success = estimator.process(pairs, H)

        if (!success) {
            // Fallback: identity transform (no correction)
            H[0, 0] = 1.0
            H[0, 1] = 0.0
            H[0, 2] = 0.0
            H[1, 0] = 0.0
            H[1, 1] = 1.0
            H[1, 2] = 0.0
            H[2, 0] = 0.0
            H[2, 1] = 0.0
            H[2, 2] = 1.0
        }

        return H
    }

    /** Samples a pixel from the source image using bilinear interpolation. */
    private fun bilinearSample(image: BufferedImage, sx: Double, sy: Double): Int {
        val w = image.width
        val h = image.height

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
        ty: Double,
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

    /** Euclidean distance between two points. */
    private fun dist(a: Point2D_F64, b: Point2D_F64): Double {
        val dx = b.x - a.x
        val dy = b.y - a.y
        return sqrt(dx * dx + dy * dy)
    }
}
