package org.kryspetrie.fileimport.infrastructure.photoscan

import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/** Test to understand what contours are being found and why GT[1] isn't detected. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContourDebugTest {

    private lateinit var img02: BufferedImage

    @BeforeAll
    fun setup() {
        img02 = loadImage("org/kryspetrie/fileimport/application/photo-scan-02.jpg")
    }

    private fun loadImage(path: String): BufferedImage =
        ImageIO.read(javaClass.classLoader.getResourceAsStream(path))!!

    @Test
    fun `show all detected contours with statistics`() {
        val detector = RectangleDetector()
        val quads = detector.detectRectangles(img02, expectedCount = 3)

        println("\n=== All Detected Quads ===")
        for ((i, quad) in quads.withIndex()) {
            val corners = quad.corners
            val cx = corners.map { it.x }.average()
            val cy = corners.map { it.y }.average()
            println("Quad[$i]:")
            println("  Centroid: (${cx.toInt()}, ${cy.toInt()})")
            println("  Area: ${quad.area}")
            println("  Aspect ratio: ${"%.2f".format(quad.aspectRatio)}")
            println("  Corners: ${corners.map { "(${it.x},${it.y})" }.joinToString()}")
        }

        // Show expected GT locations
        println("\n=== Ground Truth Locations ===")
        println(
            "GT[0]: centroid ~(1067, 905), corners: TL(270,358), TR(1864,358), BR(1864,1452), BL(270,1452)"
        )
        println(
            "GT[1]: centroid ~(1180, 2687), corners: TL(256,1560), TR(2104,1560), BR(2104,3814), BL(256,3814)"
        )
        println(
            "GT[2]: centroid ~(2963, 1572), corners: TL(2226,634), TR(3700,634), BR(3700,2510), BL(2226,2510)"
        )

        // Check what's closest to GT[1]
        val gt1CenterX = 1180.0
        val gt1CenterY = 2687.0

        println("\n=== Distance to GT[1] Centroid ===")
        for ((i, quad) in quads.withIndex()) {
            val cx = quad.corners.map { it.x }.average()
            val cy = quad.corners.map { it.y }.average()
            val dist = hypot(cx - gt1CenterX, cy - gt1CenterY)
            println("Quad[$i]: ${"%.0f".format(dist)}px away from GT[1]")
        }
    }

    @Test
    fun `check what contours are found with different epsilon values`() {
        println("\n=== Douglas-Peucker Epsilon Comparison ===")

        val gray = toGrayscale(img02)
        val binary = adaptiveThreshold(gray, 31, 10)
        val closed = morphologicalClose(binary, 5)
        val contours = findContours(closed)

        println("Total contours found: ${contours.size}")

        // Show contour statistics
        for ((i, contour) in contours.take(20).withIndex()) {
            val xs = contour.map { it.x }
            val ys = contour.map { it.y }
            val minX = xs.minOrNull() ?: 0
            val maxX = xs.maxOrNull() ?: 0
            val minY = ys.minOrNull() ?: 0
            val maxY = ys.maxOrNull() ?: 0
            val cx = xs.average()
            val cy = ys.average()
            val area = (maxX - minX) * (maxY - minY)

            // Check distance to GT[1]
            val distToGT1 = hypot(cx - 1180, cy - 2687)

            println(
                "Contour[$i]: size=${contour.size}, bbox=${maxX-minX}x${maxY-minY}, " +
                    "centroid=(${cx.toInt()},${cy.toInt()}), area=$area, distToGT1=${"%.0f".format(distToGT1)}px"
            )
        }
    }

    private fun toGrayscale(image: BufferedImage): BufferedImage {
        if (image.type == BufferedImage.TYPE_BYTE_GRAY) return image
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
        val x1 = max(0, cx - half)
        val y1 = max(0, cy - half)
        val x2 = min(w - 1, cx + half)
        val y2 = min(h - 1, cy + half)
        val count = (x2 - x1 + 1) * (y2 - y1 + 1)
        val sum =
            integral[y2 + 1][x2 + 1] - integral[y1][x2 + 1] - integral[y2 + 1][x1] +
                integral[y1][x1]
        return sum to count
    }

    private fun morphologicalClose(binary: BufferedImage, kernelSize: Int): BufferedImage {
        return erode(dilate(binary, kernelSize), kernelSize)
    }

    @Suppress("NestedBlockDepth")
    private fun dilate(binary: BufferedImage, kernelSize: Int): BufferedImage {
        val result = BufferedImage(binary.width, binary.height, BufferedImage.TYPE_BYTE_BINARY)
        val src = binary.getRaster()
        val dst = result.getRaster()
        val radius = kernelSize / 2
        for (y in 0 until binary.height) {
            for (x in 0 until binary.width) {
                var found = false
                outer@ for (dy in -radius..radius) {
                    for (dx in -radius..radius) {
                        val nx = x + dx
                        val ny = y + dy
                        if (nx in 0 until binary.width && ny in 0 until binary.height) {
                            if (src.getSample(nx, ny, 0) > 0) {
                                found = true
                                break@outer
                            }
                        }
                    }
                }
                dst.setSample(x, y, 0, if (found) 255 else 0)
            }
        }
        return result
    }

    @Suppress("NestedBlockDepth")
    private fun erode(binary: BufferedImage, kernelSize: Int): BufferedImage {
        val result = BufferedImage(binary.width, binary.height, BufferedImage.TYPE_BYTE_BINARY)
        val src = binary.getRaster()
        val dst = result.getRaster()
        val radius = kernelSize / 2
        for (y in 0 until binary.height) {
            for (x in 0 until binary.width) {
                var allOn = true
                outer@ for (dy in -radius..radius) {
                    for (dx in -radius..radius) {
                        val nx = x + dx
                        val ny = y + dy
                        if (nx in 0 until binary.width && ny in 0 until binary.height) {
                            if (src.getSample(nx, ny, 0) == 0) {
                                allOn = false
                                break@outer
                            }
                        }
                    }
                }
                dst.setSample(x, y, 0, if (allOn) 255 else 0)
            }
        }
        return result
    }

    @Suppress("NestedBlockDepth")
    private fun findContours(binary: BufferedImage): List<List<RectangleDetector.Point>> {
        val visited = Array(binary.height) { BooleanArray(binary.width) }
        val data = binary.getRaster()
        val contours = mutableListOf<List<RectangleDetector.Point>>()
        for (y in 1 until binary.height - 1) {
            for (x in 1 until binary.width - 1) {
                if (data.getSample(x, y, 0) > 0 && !visited[y][x]) {
                    val contour = traceContour(data, visited, x, y)
                    if (contour.size >= 4) contours.add(contour)
                }
            }
        }
        return contours
    }

    private fun traceContour(
        data: java.awt.image.WritableRaster,
        visited: Array<BooleanArray>,
        startX: Int,
        startY: Int,
    ): List<RectangleDetector.Point> {
        val contour = mutableListOf<RectangleDetector.Point>()
        var x = startX
        var y = startY
        var dir = 0
        val dx = intArrayOf(1, 1, 0, -1, -1, -1, 0, 1)
        val dy = intArrayOf(0, 1, 1, 1, 0, -1, -1, -1)
        do {
            contour.add(RectangleDetector.Point(x, y))
            visited[y][x] = true
            var found = false
            for (i in 0 until 8) {
                val ndir = (dir + i) % 8
                val nx = x + dx[ndir]
                val ny = y + dy[ndir]
                if (
                    nx in 0 until data.width &&
                        ny in 0 until data.height &&
                        data.getSample(nx, ny, 0) > 0
                ) {
                    x = nx
                    y = ny
                    dir = (ndir + 5) % 8
                    found = true
                    break
                }
            }
            if (!found || contour.size > data.width * data.height) break
        } while (!(x == startX && y == startY) && contour.size < 50000)
        return contour
    }
}
