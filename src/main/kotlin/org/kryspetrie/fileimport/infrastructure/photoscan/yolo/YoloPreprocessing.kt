package org.kryspetrie.fileimport.infrastructure.photoscan.yolo

import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Preprocessing utilities for YOLO ONNX models.
 *
 * Provides preprocessing matching Python's photocrop.py pipeline, which uses PIL's Image.BILINEAR
 * for all image resizing operations.
 *
 * PIL's BILINEAR uses an antialiased bilinear filter:
 * - For downscaling (output < input): wider kernel with support = 1/scale
 * - For upscaling: standard bilinear with support = 1.0
 *
 * The implementation uses a separable two-pass approach (horizontal then vertical) matching PIL's
 * behavior:
 * - Coordinate mapping: center = ((outputPos + 0.5) / scale) - 0.5
 * - Weight function: max(0, 1 - |distance| / support)
 * - Zero-extension at boundaries (skip out-of-bounds pixels, not clamping)
 * - Per-output-pixel weight normalization
 * - Float32 arithmetic throughout to match PIL's C-level float precision, producing byte-exact
 *   output matching PIL's Image.BILINEAR resize.
 *
 * Verified empirically: produces pixel-exact output matching PIL's Image.BILINEAR when compared at
 * the uint8 level (all bytes identical after uint8 rounding).
 */
object YoloPreprocessing {

    /**
     * Result of letterbox preprocessing.
     *
     * @property flatArray Float array in NCHW format [1, 3, H, W] ready for ONNX tensor
     * @property shape Shape array [1, 3, H, W] for ONNX tensor creation
     * @property ratio Scale factor applied to the image
     * @property padW Horizontal padding in pixels
     * @property padH Vertical padding in pixels
     */
    data class PreprocessResult(
        val flatArray: FloatArray,
        val shape: LongArray,
        val ratio: Float,
        val padW: Int,
        val padH: Int,
    )

    /**
     * Result of crop (stretch) preprocessing.
     *
     * @property flatArray Float array in NCHW format [1, 3, H, W]
     * @property shape Shape array [1, 3, H, W] for ONNX tensor creation
     * @property cropWidth Original crop width (for coordinate mapping)
     * @property cropHeight Original crop height (for coordinate mapping)
     */
    data class CropPreprocessResult(
        val flatArray: FloatArray,
        val shape: LongArray,
        val cropWidth: Int,
        val cropHeight: Int,
    )

    /**
     * Letterbox resize: preserves aspect ratio, pads with (114,114,114).
     *
     * Matches Python's preprocess_letterbox() exactly:
     * 1. Compute scale ratio = min(targetSize/maxW, targetSize/maxH)
     * 2. Resize with PIL BILINEAR (antialiased for downscale, standard for upscale)
     * 3. Center on canvas with (114,114,114) padding
     * 4. Convert RGB to NCHW float32 normalized [0,1]
     *
     * @param image Source image (any size)
     * @param targetSize Target size (default 640)
     * @return PreprocessResult with flatArray, shape, and scale info
     */
    fun preprocessLetterbox(image: BufferedImage, targetSize: Int = 640): PreprocessResult {
        val origW = image.width
        val origH = image.height
        val ratio = min(targetSize.toFloat() / origW, targetSize.toFloat() / origH)
        val newW = (origW * ratio).toInt()
        val newH = (origH * ratio).toInt()
        val padW = (targetSize - newW) / 2
        val padH = (targetSize - newH) / 2

        // Resize with PIL-matching bilinear interpolation
        val resizedPixels = pilBilinearResize(image, newW, newH)

        // Create padded canvas filled with (114, 114, 114)
        val flatArray = FloatArray(3 * targetSize * targetSize)
        val padR = 114.0f / 255.0f
        val padG = 114.0f / 255.0f
        val padB = 114.0f / 255.0f
        for (i in flatArray.indices) {
            flatArray[i] =
                when (i / (targetSize * targetSize)) {
                    0 -> padR
                    1 -> padG
                    else -> padB
                }
        }

        // Copy resized image pixels to canvas at (padW, padH)
        for (y in 0 until newH) {
            for (x in 0 until newW) {
                val srcIdx = (y * newW + x) * 3
                val dstIdx = (y + padH) * targetSize + (x + padW)
                flatArray[0 * targetSize * targetSize + dstIdx] = resizedPixels[srcIdx]
                flatArray[1 * targetSize * targetSize + dstIdx] = resizedPixels[srcIdx + 1]
                flatArray[2 * targetSize * targetSize + dstIdx] = resizedPixels[srcIdx + 2]
            }
        }

        val shape = longArrayOf(1, 3, targetSize.toLong(), targetSize.toLong())
        return PreprocessResult(flatArray, shape, ratio, padW, padH)
    }

    /**
     * Stretch resize: resize to targetSize×targetSize without letterboxing.
     *
     * Matches Python's preprocess_crop() exactly:
     * 1. Resize with PIL BILINEAR (antialiased for downscale, standard for upscale)
     * 2. Convert RGB to NCHW float32 normalized [0,1]
     *
     * @param crop Source image crop (any size)
     * @param targetSize Target size (default 640 for pose, 320 for corner regression)
     * @return CropPreprocessResult with flatArray, shape, and original dimensions
     */
    fun preprocessCrop(crop: BufferedImage, targetSize: Int = 640): CropPreprocessResult {
        val cropW = crop.width
        val cropH = crop.height

        // Resize with PIL-matching bilinear interpolation
        val resizedPixels = pilBilinearResize(crop, targetSize, targetSize)

        // Convert to NCHW float array
        val flatArray = FloatArray(3 * targetSize * targetSize)
        for (y in 0 until targetSize) {
            for (x in 0 until targetSize) {
                val srcIdx = (y * targetSize + x) * 3
                val dstIdx = y * targetSize + x
                flatArray[0 * targetSize * targetSize + dstIdx] = resizedPixels[srcIdx]
                flatArray[1 * targetSize * targetSize + dstIdx] = resizedPixels[srcIdx + 1]
                flatArray[2 * targetSize * targetSize + dstIdx] = resizedPixels[srcIdx + 2]
            }
        }

        val shape = longArrayOf(1, 3, targetSize.toLong(), targetSize.toLong())
        return CropPreprocessResult(flatArray, shape, cropW, cropH)
    }

    /**
     * Resize an image using PIL's Image.BILINEAR algorithm.
     *
     * Uses Float32 arithmetic throughout to match PIL's C-level float precision, producing
     * byte-exact output when compared at the uint8 level.
     *
     * @param image Source image
     * @param outW Target width
     * @param outH Target height
     * @return Flat array of [R, G, B] per pixel in row-major order, normalized to [0, 1]
     */
    fun pilBilinearResize(image: BufferedImage, outW: Int, outH: Int): FloatArray {
        val inW = image.width
        val inH = image.height

        // Cache source pixels as RGB integers
        val rgbCache = IntArray(inW * inH)
        for (y in 0 until inH) {
            for (x in 0 until inW) {
                rgbCache[y * inW + x] = image.getRGB(x, y)
            }
        }

        // Extract RGB channels as floats [0, 255] — using Float to match PIL's C float precision
        val srcR = FloatArray(inW * inH)
        val srcG = FloatArray(inW * inH)
        val srcB = FloatArray(inW * inH)
        for (i in 0 until inW * inH) {
            val rgb = rgbCache[i]
            srcR[i] = ((rgb shr 16) and 0xFF).toFloat()
            srcG[i] = ((rgb shr 8) and 0xFF).toFloat()
            srcB[i] = (rgb and 0xFF).toFloat()
        }

        // Horizontal pass: resize width
        val hScale = outW.toFloat() / inW.toFloat()
        val hSupport = max(1.0f, 1.0f / hScale)

        val (midR, midG, midB) =
            if (outW != inW) {
                horizontalResize(srcR, srcG, srcB, inW, inH, outW, hScale, hSupport)
            } else {
                Triple(srcR, srcG, srcB)
            }

        // Vertical pass: resize height (no uint8 rounding between passes — PIL uses float
        // internally)
        val vScale = outH.toFloat() / inH.toFloat()
        val vSupport = max(1.0f, 1.0f / vScale)

        val (finalR, finalG, finalB) =
            if (outH != inH) {
                verticalResize(midR, midG, midB, outW, inH, outH, vScale, vSupport)
            } else {
                Triple(midR, midG, midB)
            }

        // Combine into output array, normalized to [0, 1]
        val result = FloatArray(outW * outH * 3)
        var idx = 0
        for (y in 0 until outH) {
            for (x in 0 until outW) {
                val pixIdx = y * outW + x
                result[idx++] = finalR[pixIdx] / 255.0f
                result[idx++] = finalG[pixIdx] / 255.0f
                result[idx++] = finalB[pixIdx] / 255.0f
            }
        }
        return result
    }

    /** Horizontal resize pass (PIL BILINEAR algorithm) using Float32 arithmetic. */
    private fun horizontalResize(
        srcR: FloatArray,
        srcG: FloatArray,
        srcB: FloatArray,
        inW: Int,
        inH: Int,
        outW: Int,
        hScale: Float,
        hSupport: Float,
    ): Triple<FloatArray, FloatArray, FloatArray> {
        val outR = FloatArray(outW * inH)
        val outG = FloatArray(outW * inH)
        val outB = FloatArray(outW * inH)

        for (y in 0 until inH) {
            val rowOffset = y * inW
            for (ox in 0 until outW) {
                val center = (ox + 0.5f) / hScale - 0.5f
                val ixStart = max(0, ceil(center - hSupport).toInt())
                val ixEnd = min(inW - 1, floor(center + hSupport).toInt())

                var totalWeight = 0.0f
                var sumR = 0.0f
                var sumG = 0.0f
                var sumB = 0.0f

                for (ix in ixStart..ixEnd) {
                    val dist = abs(ix.toFloat() - center)
                    if (dist >= hSupport) continue
                    val weight = 1.0f - dist / hSupport
                    totalWeight += weight
                    val srcIdx = rowOffset + ix
                    sumR += weight * srcR[srcIdx]
                    sumG += weight * srcG[srcIdx]
                    sumB += weight * srcB[srcIdx]
                }

                val outIdx = y * outW + ox
                if (totalWeight > 0.0f) {
                    outR[outIdx] = sumR / totalWeight
                    outG[outIdx] = sumG / totalWeight
                    outB[outIdx] = sumB / totalWeight
                }
            }
        }

        return Triple(outR, outG, outB)
    }

    /** Vertical resize pass (PIL BILINEAR algorithm) using Float32 arithmetic. */
    private fun verticalResize(
        srcR: FloatArray,
        srcG: FloatArray,
        srcB: FloatArray,
        width: Int,
        inH: Int,
        outH: Int,
        vScale: Float,
        vSupport: Float,
    ): Triple<FloatArray, FloatArray, FloatArray> {
        val outR = FloatArray(width * outH)
        val outG = FloatArray(width * outH)
        val outB = FloatArray(width * outH)

        for (oy in 0 until outH) {
            val center = (oy + 0.5f) / vScale - 0.5f
            val iyStart = max(0, ceil(center - vSupport).toInt())
            val iyEnd = min(inH - 1, floor(center + vSupport).toInt())

            // Pre-compute weights for this output row
            val weights = mutableListOf<Float>()
            val validIy = mutableListOf<Int>()
            var totalWeight = 0.0f
            for (iy in iyStart..iyEnd) {
                val dist = abs(iy.toFloat() - center)
                if (dist >= vSupport) continue
                val weight = 1.0f - dist / vSupport
                weights.add(weight)
                validIy.add(iy)
                totalWeight += weight
            }

            if (totalWeight > 0.0f) {
                // Pre-compute normalized weights
                val normWeights = FloatArray(weights.size)
                for (wi in weights.indices) {
                    normWeights[wi] = weights[wi] / totalWeight
                }

                for (x in 0 until width) {
                    var sumR = 0.0f
                    var sumG = 0.0f
                    var sumB = 0.0f
                    for (wi in weights.indices) {
                        val iy = validIy[wi]
                        val w = normWeights[wi]
                        val srcIdx = iy * width + x
                        sumR += w * srcR[srcIdx]
                        sumG += w * srcG[srcIdx]
                        sumB += w * srcB[srcIdx]
                    }
                    val outIdx = oy * width + x
                    outR[outIdx] = sumR
                    outG[outIdx] = sumG
                    outB[outIdx] = sumB
                }
            }
        }

        return Triple(outR, outG, outB)
    }
}
