package org.kryspetrie.fileimport.infrastructure.photoscan

import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/** Test to visualize the binary image and understand what contours are being detected. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BinaryThresholdDebugTest {

    private lateinit var img02: BufferedImage

    @BeforeAll
    fun setup() {
        img02 = loadImage("org/kryspetrie/fileimport/application/photo-scan-02.jpg")
    }

    private fun loadImage(path: String): BufferedImage =
        ImageIO.read(javaClass.classLoader.getResourceAsStream(path))!!

    @Test
    fun `show binary threshold statistics for different regions`() {
        val gray = toGrayscale(img02)
        val binary = adaptiveThreshold(gray, 31, 10)

        println("\n=== Binary Threshold Analysis ===")
        println("Image size: ${img02.width}x${img02.height}")
        println("Binary size: ${binary.width}x${binary.height}")

        // Count white pixels in regions
        val gt0Region = countWhitePixelsInRegion(binary, 270, 358, 1594, 1094) // GT[0] bounding box
        val gt1Region =
            countWhitePixelsInRegion(binary, 256, 1560, 1848, 2254) // GT[1] bounding box
        val gt2Region =
            countWhitePixelsInRegion(binary, 2226, 634, 1474, 1876) // GT[2] bounding box

        println("\nWhite pixel percentages in GT regions:")
        println("  GT[0] (small photo): ${"%.1f".format(gt0Region.first)}%")
        println("  GT[1] (large photo): ${"%.1f".format(gt1Region.first)}%")
        println("  GT[2] (medium photo): ${"%.1f".format(gt2Region.first)}%")

        // Sample grayscale values at corners
        println("\nSample grayscale values at GT corners:")
        for ((name, corners) in
            listOf(
                "GT[0]" to listOf(270 to 358, 1864 to 358, 1864 to 1452, 270 to 1452),
                "GT[1]" to listOf(256 to 1560, 2104 to 1560, 2104 to 3814, 256 to 3814),
                "GT[2]" to listOf(2226 to 634, 3700 to 634, 3700 to 2510, 2226 to 2510),
            )) {
            for ((idx, corner) in corners.withIndex()) {
                val (x, y) = corner
                if (x < img02.width && y < img02.height) {
                    val pixel = gray.getRaster().getSample(x, y, 0)
                    println("  $name corner[$idx] ($x, $y): $pixel")
                }
            }
        }

        // Save binary image for visualization
        val outputPath = "/tmp/binary_debug.png"
        ImageIO.write(binary, "PNG", java.io.File(outputPath))
        println("\nBinary image saved to: $outputPath")
    }

    private fun toGrayscale(image: BufferedImage): BufferedImage {
        if (
            image.type == BufferedImage.TYPE_BYTE_GRAY ||
                image.type == BufferedImage.TYPE_USHORT_GRAY
        ) {
            return image
        }
        val gray = BufferedImage(image.width, image.height, BufferedImage.TYPE_BYTE_GRAY)
        val g = gray.graphics
        g.drawImage(image, 0, 0, null)
        g.dispose()
        return gray
    }

    private fun adaptiveThreshold(gray: BufferedImage, blockSize: Int, c: Int): BufferedImage {
        val width = gray.width
        val height = gray.height
        val binary = BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY)
        val src = gray.getRaster()
        val dst = binary.getRaster()
        val integral = computeIntegralImage(gray)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val (sum, count) = getIntegralMean(integral, x, y, blockSize)
                val threshValue = (sum / count) - c
                val pixel = src.getSample(x, y, 0)
                dst.setSample(x, y, 0, if (pixel <= threshValue) 255 else 0)
            }
        }
        return binary
    }

    private fun computeIntegralImage(gray: BufferedImage): Array<IntArray> {
        val w = gray.width
        val h = gray.height
        val data = gray.getRaster()
        val integral = Array(h + 1) { IntArray(w + 1) }
        for (y in 0 until h) {
            var rowSum = 0
            for (x in 0 until w) {
                rowSum += data.getSample(x, y, 0)
                integral[y + 1][x + 1] = integral[y][x + 1] + rowSum
            }
        }
        return integral
    }

    private fun getIntegralMean(
        integral: Array<IntArray>,
        cx: Int,
        cy: Int,
        blockSize: Int,
    ): Pair<Int, Int> {
        val w = integral[0].size - 1
        val h = integral.size - 1
        val half = blockSize / 2
        val x1 = kotlin.math.max(0, cx - half)
        val y1 = kotlin.math.max(0, cy - half)
        val x2 = kotlin.math.min(w - 1, cx + half)
        val y2 = kotlin.math.min(h - 1, cy + half)
        val count = (x2 - x1 + 1) * (y2 - y1 + 1)
        val sum =
            integral[y2 + 1][x2 + 1] - integral[y1][x2 + 1] - integral[y2 + 1][x1] +
                integral[y1][x1]
        return sum to count
    }

    private fun countWhitePixelsInRegion(
        binary: BufferedImage,
        x1: Int,
        y1: Int,
        width: Int,
        height: Int,
    ): Pair<Float, Int> {
        val x2 = kotlin.math.min(x1 + width, binary.width - 1)
        val y2 = kotlin.math.min(y1 + height, binary.height - 1)
        var whiteCount = 0
        val data = binary.getRaster()
        for (y in x1.coerceAtLeast(0) until x2) {
            for (x in y1.coerceAtLeast(0) until y2) {
                if (data.getSample(x, y, 0) > 0) whiteCount++
            }
        }
        val totalPixels = (x2 - x1.coerceAtLeast(0)) * (y2 - y1.coerceAtLeast(0))
        return (whiteCount.toFloat() / totalPixels * 100) to whiteCount
    }
}
