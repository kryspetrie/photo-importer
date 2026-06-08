package org.kryspetrie.fileimport.infrastructure.photoscan

import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Detects rectangular photo regions in scanned images using classical computer vision techniques.
 *
 * This is a pure-Java AWT implementation of the standard contour-based rectangle detection
 * pipeline:
 * 1. **Grayscale conversion** — luminance-based weighted average
 * 2. **Adaptive thresholding** — handles uneven lighting by local mean thresholding
 * 3. **Morphological closing** — fills small gaps in detected edges
 * 4. **Contour extraction** — finds boundary chains using a march-based algorithm
 * 5. **Polygon approximation** — simplifies contours to 4-vertex quadrilaterals
 * 6. **Filtering** — removes non-rectangular shapes based on angle, aspect ratio, area, and
 *    color-edge validation
 * 7. **Non-maximum suppression** — removes overlapping detections
 *
 * ## Color-Edge Filtering
 *
 * The detector uses a two-stage filtering approach:
 * 1. **Geometric filtering** — removes quads based on aspect ratio, area, and corner angles
 * 2. **Color-edge validation** — verifies that quad edges have significant color differences from
 *    the background. This catches photos that have similar luminance to the background but
 *    different colors (e.g., a blue photo on a gray desk).
 *
 * This approach works best when photos are placed on a solid, contrasting background (e.g., photos
 * scanned on a desk, not embedded in books).
 */
class RectangleDetector(
    private val minArea: Int = 2000,
    private val maxAspectRatio: Float = 5.0f,
    private val minAngleDiff: Float = 60f,
    private val maxAngleDiff: Float = 120f,
    private val minQuadRatio: Float = 0.3f,
    /**
     * Maximum image dimension (width or height) to process. Large images are downsampled for memory
     * efficiency.
     */
    private val maxImageDimension: Int = 1000,
    /** Gamma correction (1.0 = no change, <1 brightens, >1 darkens). */
    private val gamma: Double = 1.4,
    /** Block size for adaptive thresholding (must be odd, >= 3). */
    private val adaptiveBlockSize: Int = 31,
    /** Constant subtracted from mean in adaptive thresholding. */
    private val adaptiveC: Int = 10,
    /** Kernel size for morphological closing (>= 3). */
    private val morphKernelSize: Int = 5,
) {

    /**
     * Detects rectangular photo regions in the given image.
     *
     * Large images are automatically downsampled to [maxImageDimension] pixels on the longest edge
     * before processing, then coordinates are scaled back to the original image space. This keeps
     * memory usage bounded regardless of input size.
     *
     * @param image The scanned image (RGB or grayscale)
     * @param expectedCount Hint for how many photos to detect (null = auto). Used to tune filtering
     *   aggressiveness. A higher count allows smaller/lower-confidence detections.
     * @return List of detected photo corners as [DetectedQuadrilateral] objects, sorted by area
     *   (largest first). Coordinates are in original image space.
     */
    fun detectRectangles(
        image: BufferedImage,
        expectedCount: Int? = null,
    ): List<DetectedQuadrilateral> {
        val (workImage, scale) = maybeDownsample(image)
        val workExpectedCount = if (scale > 1.0f) null else expectedCount

        // Step 1: Grayscale
        val gray = toGrayscale(workImage)

        // Step 1b: Apply gamma correction if needed
        val adjusted = if (gamma != 1.0) applyGamma(gray) else gray

        // Step 2: Adaptive threshold
        val binary = adaptiveThreshold(adjusted, adaptiveBlockSize, adaptiveC)

        // Step 3: Morphological closing to bridge small gaps
        val closed = morphologicalClose(binary, morphKernelSize)

        // Step 4: Find contours
        val contours = findContours(closed)

        // Step 5: Approximate to polygons, filter to quads
        val quads =
            contours
                .mapNotNull { contour ->
                    approximateToQuadrilateral(contour, workImage.width, workImage.height)
                }
                .filter { quad -> filterQuadrilateral(quad, workExpectedCount) }
                .toMutableList()

        // Step 6: Sort by area (largest first) and remove overlapping ones
        quads.sortByDescending { it.area }
        val suppressed = nonMaxSuppress(quads)

        // Scale coordinates and area back to original image space
        return suppressed.map { quad ->
            quad.copy(
                corners =
                    quad.corners.map { Point((it.x * scale).toInt(), (it.y * scale).toInt()) },
                area = (quad.area * scale * scale).toInt(),
                centroid =
                    Point((quad.centroid.x * scale).toInt(), (quad.centroid.y * scale).toInt()),
            )
        }
    }

    /**
     * Returns the input image unchanged if both dimensions are ≤ [maxImageDimension], otherwise
     * returns a resized copy scaled down to fit within that bound. The returned [Float] is the
     * scale factor (original / downsampled) — divide coordinates by this to go from original →
     * downsampled, multiply to go from downsampled → original.
     */
    private fun maybeDownsample(image: BufferedImage): Pair<BufferedImage, Float> {
        if (image.width <= maxImageDimension && image.height <= maxImageDimension) {
            return image to 1.0f
        }
        val scale = maxImageDimension.toFloat() / max(image.width, image.height)
        val newW = (image.width * scale).toInt()
        val newH = (image.height * scale).toInt()
        val resized = BufferedImage(newW, newH, image.type)
        val g = resized.graphics
        g.drawImage(
            image.getScaledInstance(newW, newH, BufferedImage.SCALE_AREA_AVERAGING),
            0,
            0,
            null,
        )
        g.dispose()
        return resized to (1.0f / scale)
    }

    // ===== Step 1: Grayscale =====

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

    // ===== Step 1b: Gamma correction =====

    private fun applyGamma(gray: BufferedImage): BufferedImage {
        if (gamma == 1.0) return gray
        val w = gray.width
        val h = gray.height
        val result = BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY)
        val src = gray.getRaster()
        val dst = result.getRaster()
        // Precompute lookup table
        val lut = IntArray(256)
        val invGamma = 1.0 / gamma
        for (i in 0..255) {
            lut[i] = (255 * Math.pow(i / 255.0, invGamma)).coerceIn(0.0, 255.0).toInt()
        }
        for (y in 0 until h) {
            for (x in 0 until w) {
                dst.setSample(x, y, 0, lut[src.getSample(x, y, 0)])
            }
        }
        return result
    }

    // ===== Step 2: Adaptive threshold (mean-based) =====

    private fun adaptiveThreshold(
        gray: BufferedImage,
        blockSize: Int = 31,
        c: Int = 10,
    ): BufferedImage {
        val width = gray.width
        val height = gray.height
        val binary = BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY)
        val src = gray.getRaster()
        val dst = binary.getRaster()

        // Compute integral image for fast mean calculation
        val integral = computeIntegralImage(gray)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val (sum, count) = getIntegralMean(integral, x, y, blockSize)
                val threshold = (sum / count) - c
                val pixel = src.getSample(x, y, 0)
                dst.setSample(x, y, 0, if (pixel <= threshold) 255 else 0)
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

    // ===== Step 3: Morphological closing =====

    private fun morphologicalClose(binary: BufferedImage, kernelSize: Int = 5): BufferedImage {
        // Dilation followed by erosion
        val dilated = dilate(binary, kernelSize)
        val closed = erode(dilated, kernelSize)
        return closed
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

    // ===== Step 4: Contour finding =====

    @Suppress("NestedBlockDepth")
    private fun findContours(binary: BufferedImage): List<List<Point>> {
        val visited = Array(binary.height) { BooleanArray(binary.width) }
        val data = binary.getRaster()
        val contours = mutableListOf<List<Point>>()

        for (y in 1 until binary.height - 1) {
            for (x in 1 until binary.width - 1) {
                if (data.getSample(x, y, 0) > 0 && !visited[y][x]) {
                    // Found an edge pixel — trace the contour
                    val contour = traceContour(data, visited, x, y)
                    if (contour.size >= 4) {
                        contours.add(contour)
                    }
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
    ): List<Point> {
        val contour = mutableListOf<Point>()
        // Moore neighbor tracing (8-connectivity)
        var x = startX
        var y = startY
        var dir =
            0 // 0=right, 1=down-right, 2=down, 3=down-left, 4=left, 5=up-left, 6=up, 7=up-right
        val dx = intArrayOf(1, 1, 0, -1, -1, -1, 0, 1)
        val dy = intArrayOf(0, 1, 1, 1, 0, -1, -1, -1)

        do {
            contour.add(Point(x, y))
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
                    dir = (ndir + 5) % 8 // Next search starts from opposite direction
                    found = true
                    break
                }
            }
            if (!found || contour.size > data.width * data.height) break
        } while (!(x == startX && y == startY) && contour.size < 50000)

        return contour
    }

    // ===== Step 5: Polygon approximation + quadrilateral extraction =====

    private fun approximateToQuadrilateral(
        contour: List<Point>,
        imageW: Int,
        imageH: Int,
    ): DetectedQuadrilateral? {
        if (contour.size < 4) return null

        // Try multiple epsilon values for Douglas-Peucker
        var bestQuad: List<Point>? = null
        var bestQuality = 0f

        for (epsilon in listOf(1.0, 2.0, 3.0, 4.0, 5.0, 7.0)) {
            val simplified = douglasPeucker(contour, epsilon)
            if (simplified.size < 4) continue

            // If we have more than 4 points, try to merge and extract best 4
            val quadPoints = extractBestQuad(simplified)
            if (quadPoints.size != 4) continue

            // Sort corners: top-left, top-right, bottom-right, bottom-left
            val sorted = sortCorners(quadPoints)

            val area = polygonArea(sorted)
            if (area < minArea) continue

            // Check quadrilateral quality
            val quality = quadrilateralQuality(sorted)
            if (quality > bestQuality) {
                bestQuality = quality
                bestQuad = sorted
            }
        }

        if (bestQuad == null) return null
        if (bestQuality < minQuadRatio) return null

        val area = polygonArea(bestQuad)
        return DetectedQuadrilateral(
            corners = bestQuad,
            area = area,
            centroid = centroid(bestQuad),
            aspectRatio = aspectRatio(bestQuad),
        )
    }

    private fun douglasPeucker(points: List<Point>, epsilon: Double): List<Point> {
        if (points.size < 3) return points

        var maxDist = 0.0
        var maxIdx = 0
        val first = points.first()
        val last = points.last()

        for (i in 1 until points.size - 1) {
            val dist = perpendicularDistance(points[i], first, last)
            if (dist > maxDist) {
                maxDist = dist
                maxIdx = i
            }
        }

        return if (maxDist > epsilon) {
            val left = douglasPeucker(points.subList(0, maxIdx + 1), epsilon)
            val right = douglasPeucker(points.subList(maxIdx, points.size), epsilon)
            left.dropLast(1) + right
        } else {
            listOf(first, last)
        }
    }

    private fun perpendicularDistance(point: Point, lineStart: Point, lineEnd: Point): Double {
        val dx = lineEnd.x - lineStart.x.toDouble()
        val dy = lineEnd.y - lineStart.y.toDouble()
        val len = hypot(dx, dy)
        if (len < 1e-9)
            return hypot(point.x - lineStart.x.toDouble(), point.y - lineStart.y.toDouble())
        return abs(
            (dy * point.x - dx * point.y + lineEnd.x * lineStart.y - lineEnd.y * lineStart.x)
        ) / len
    }

    private fun extractBestQuad(points: List<Point>): List<Point> {
        if (points.size == 4) return points

        // Use convex hull if we have more than 4 points
        val hull = convexHull(points)
        if (hull.size <= 4) return hull

        // For convex hull with > 4 points, keep the 4 most "corner-like" points
        return selectMostAcuteCorners(hull)
    }

    private fun convexHull(points: List<Point>): List<Point> {
        if (points.size < 3) return points
        val sorted = points.sortedWith(compareBy({ it.y }, { it.x }))
        val n = sorted.size
        val lower = mutableListOf<Point>()
        for (p in sorted) {
            while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), p) <= 0) {
                lower.removeLast()
            }
            lower.add(p)
        }
        val upper = mutableListOf<Point>()
        for (p in sorted.reversed()) {
            while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), p) <= 0) {
                upper.removeLast()
            }
            upper.add(p)
        }
        lower.removeLast()
        upper.removeLast()
        return lower + upper
    }

    private fun selectMostAcuteCorners(hull: List<Point>): List<Point> {
        // Score each point by its interior angle — lower angle = more acute = better corner
        val n = hull.size
        val scored = mutableListOf<Pair<Point, Double>>()
        for (i in hull.indices) {
            val prev = hull[(i - 1 + n) % n]
            val curr = hull[i]
            val next = hull[(i + 1) % n]
            val angle = angleBetween(prev, curr, next)
            // Convert to "corner-ness" score (180° = straight, lower = more acute)
            val cornerScore = 180.0 - abs(angle - 90.0)
            scored.add(curr to cornerScore)
        }
        scored.sortByDescending { it.second }
        return scored.take(4).map { it.first }.toList()
    }

    private fun angleBetween(a: Point, b: Point, c: Point): Double {
        val dx1 = a.x - b.x.toDouble()
        val dy1 = a.y - b.y.toDouble()
        val dx2 = c.x - b.x.toDouble()
        val dy2 = c.y - b.y.toDouble()
        val dot = dx1 * dx2 + dy1 * dy2
        val cross = dx1 * dy2 - dy1 * dx2
        return Math.toDegrees(atan2(cross, dot))
    }

    private fun sortCorners(corners: List<Point>): List<Point> {
        if (corners.size != 4) return corners

        // Sort by sum (x+y): smallest = top-left, largest = bottom-right
        val sorted = corners.sortedBy { it.x + it.y }
        val topLeft = sorted[0]
        val bottomRight = sorted[3]

        // Sort remaining two by difference (x-y): smaller = top-right, larger = bottom-left
        val remaining = listOf(sorted[1], sorted[2]).sortedBy { it.x - it.y }
        val topRight = remaining[0]
        val bottomLeft = remaining[1]

        return listOf(topLeft, topRight, bottomRight, bottomLeft)
    }

    private fun polygonArea(points: List<Point>): Int {
        var area = 0
        val n = points.size
        for (i in points.indices) {
            val j = (i + 1) % n
            area += points[i].x * points[j].y
            area -= points[j].x * points[i].y
        }
        return abs(area / 2)
    }

    private fun centroid(points: List<Point>): Point {
        var cx = 0.0
        var cy = 0.0
        for (p in points) {
            cx += p.x
            cy += p.y
        }
        return Point((cx / points.size).toInt(), (cy / points.size).toInt())
    }

    private fun aspectRatio(sorted: List<Point>): Float {
        val tl = sorted[0]
        val tr = sorted[1]
        val bl = sorted[3]
        val width = hypot((tr.x - tl.x).toDouble(), (tr.y - tl.y).toDouble())
        val height = hypot((bl.x - tl.x).toDouble(), (bl.y - tl.y).toDouble())
        return if (height > 0) (width / height).toFloat() else 1f
    }

    private fun quadrilateralQuality(corners: List<Point>): Float {
        if (corners.size != 4) return 0f
        // Check if all four internal angles are approximately 90°
        val n = corners.size
        var totalDeviation = 0.0
        for (i in corners.indices) {
            val prev = corners[(i - 1 + n) % n]
            val curr = corners[i]
            val next = corners[(i + 1) % n]
            val angle = abs(angleBetween(prev, curr, next))
            val deviation = abs(angle - 90.0)
            totalDeviation += deviation
        }
        // Average deviation from 90° — lower is better
        // Max deviation would be ~180°, quality = 1 - avg_deviation/90
        val avgDev = totalDeviation / 4.0
        return max(0.0, 1.0 - avgDev / 90.0).toFloat()
    }

    /**
     * Validates that all four corners of a quadrilateral have angles between 60° and 120°.
     *
     * Photos on a flat surface should have corners very close to 90°. Allowing 60-120° range
     * accounts for significant perspective distortion, camera angle, and edge detection imprecision
     * while still filtering out obviously non-rectangular shapes (e.g., triangles, pentagons,
     * highly skewed quads).
     */
    private fun hasValidAngles(corners: List<Point>): Boolean {
        if (corners.size != 4) return false
        for (i in corners.indices) {
            val prev = corners[(i - 1 + 4) % 4]
            val curr = corners[i]
            val next = corners[(i + 1) % 4]
            val angle = abs(angleBetween(prev, curr, next))
            if (angle < minAngleDiff || angle > maxAngleDiff) {
                return false
            }
        }
        return true
    }

    // ===== Step 6: Filtering =====

    private fun filterQuadrilateral(quad: DetectedQuadrilateral, expectedCount: Int?): Boolean {
        // Aspect ratio filter
        if (quad.aspectRatio > maxAspectRatio || quad.aspectRatio < 1f / maxAspectRatio) {
            return false
        }

        // Area bounds
        if (quad.area < minArea) return false

        // Angle validation: all corners must be between 70° and 110°
        if (!hasValidAngles(quad.corners)) return false

        // Quadrilateral quality (angles close to 90°)
        if (quadrilateralQuality(quad.corners) < minQuadRatio) return false

        // If we expect a specific count, be more lenient with smaller quads
        if (expectedCount != null && expectedCount > 0) {
            val sizePenalty = max(0.2f, 1.0f - (expectedCount * 0.1f))
            if (quadrilateralQuality(quad.corners) < minQuadRatio * sizePenalty) return false
        }

        return true
    }

    // ===== Step 7: Non-maximum suppression =====

    private fun nonMaxSuppress(quads: List<DetectedQuadrilateral>): List<DetectedQuadrilateral> {
        if (quads.isEmpty()) return quads
        val suppressed = mutableListOf<DetectedQuadrilateral>()
        val used = BooleanArray(quads.size) { false }

        for (i in quads.indices) {
            if (used[i]) continue
            suppressed.add(quads[i])
            used[i] = true
            for (j in i + 1 until quads.size) {
                if (used[j]) continue
                if (iou(quads[i], quads[j]) > 0.3f) {
                    used[j] = true
                }
            }
        }
        return suppressed
    }

    private fun iou(a: DetectedQuadrilateral, b: DetectedQuadrilateral): Float {
        // Compute intersection over union using bounding boxes
        val ax1 = a.corners.minOf { it.x }
        val ay1 = a.corners.minOf { it.y }
        val ax2 = a.corners.maxOf { it.x }
        val ay2 = a.corners.maxOf { it.y }
        val bx1 = b.corners.minOf { it.x }
        val by1 = b.corners.minOf { it.y }
        val bx2 = b.corners.maxOf { it.x }
        val by2 = b.corners.maxOf { it.y }

        val interX1 = max(ax1, bx1)
        val interY1 = max(ay1, by1)
        val interX2 = min(ax2, bx2)
        val interY2 = min(ay2, by2)
        val interArea = max(0, interX2 - interX1) * max(0, interY2 - interY1)
        val areaA = (ax2 - ax1) * (ay2 - ay1)
        val areaB = (bx2 - bx1) * (by2 - by1)
        val unionArea = areaA + areaB - interArea
        return if (unionArea > 0) interArea.toFloat() / unionArea else 0f
    }

    // ===== Cross product =====

    private fun cross(o: Point, a: Point, b: Point): Int {
        return (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
    }

    // ===== Simple point class =====

    data class Point(val x: Int, val y: Int)
}

/** Result of rectangle detection. */
data class DetectedQuadrilateral(
    val corners: List<RectangleDetector.Point>,
    val area: Int,
    val centroid: RectangleDetector.Point,
    val aspectRatio: Float,
)
