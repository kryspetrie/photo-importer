package org.kryspetrie.fileimport.infrastructure.photoscan

import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.math.hypot
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.domain.model.PhotoCorner

class AccuracyCheckTest {

    private fun loadImage(path: String): BufferedImage {
        val stream = javaClass.classLoader.getResourceAsStream(path)!!
        return ImageIO.read(stream)
    }

    private fun matchQuadsToGroundTruth(
        quads: List<DetectedQuadrilateral>,
        groundTruth: List<List<PhotoCorner>>,
    ): List<Pair<DetectedQuadrilateral, List<PhotoCorner>>> {
        val matches = mutableListOf<Pair<DetectedQuadrilateral, List<PhotoCorner>>>()
        val usedGT = mutableSetOf<Int>()

        for ((_, quad) in quads.withIndex()) {
            var bestGTIdx = -1
            var bestDist = Double.MAX_VALUE

            for ((gtIdx, gt) in groundTruth.withIndex()) {
                if (gtIdx in usedGT) continue
                val gtCentroidX = gt.map { it.x }.average()
                val gtCentroidY = gt.map { it.y }.average()
                val quadCentroidX = quad.centroid.x.toDouble()
                val quadCentroidY = quad.centroid.y.toDouble()
                val dist = hypot(quadCentroidX - gtCentroidX, quadCentroidY - gtCentroidY)

                if (dist < bestDist) {
                    bestDist = dist
                    bestGTIdx = gtIdx
                }
            }

            if (bestGTIdx >= 0 && bestDist < 500) {
                matches.add(quad to groundTruth[bestGTIdx])
                usedGT.add(bestGTIdx)
            }
        }

        return matches
    }

    private fun sortCorners(corners: List<RectangleDetector.Point>): List<RectangleDetector.Point> {
        if (corners.size != 4) return corners
        val sorted = corners.sortedBy { it.x + it.y }
        val topLeft = sorted[0]
        val bottomRight = sorted[3]
        val remaining = listOf(sorted[1], sorted[2]).sortedBy { it.x - it.y }
        return listOf(topLeft, remaining[0], bottomRight, remaining[1])
    }

    private fun sortCornersGT(corners: List<PhotoCorner>): List<PhotoCorner> {
        if (corners.size != 4) return corners
        val sorted = corners.sortedBy { it.x + it.y }
        val topLeft = sorted[0]
        val bottomRight = sorted[3]
        val remaining = listOf(sorted[1], sorted[2]).sortedBy { it.x - it.y }
        return listOf(topLeft, remaining[0], bottomRight, remaining[1])
    }

    private fun calculateCornerErrors(
        quad: DetectedQuadrilateral,
        gt: List<PhotoCorner>,
    ): List<Double> {
        val sortedDetected = sortCorners(quad.corners)
        val sortedGT = sortCornersGT(gt)

        return sortedDetected.mapIndexed { i, detected ->
            val gtCorner = sortedGT[i]
            hypot((detected.x - gtCorner.x).toDouble(), (detected.y - gtCorner.y).toDouble())
        }
    }

    @Test
    fun `detect rectangles with contour method`() {
        val image = loadImage("org/kryspetrie/fileimport/application/photo-scan-02.jpg")

        val groundTruth =
            listOf(
                // GT[0]: Top-left photo
                listOf(
                    PhotoCorner(270f, 358f),
                    PhotoCorner(1864f, 358f),
                    PhotoCorner(1864f, 1452f),
                    PhotoCorner(270f, 1452f),
                ),
                // GT[1]: Left side, middle to bottom (LARGEST)
                listOf(
                    PhotoCorner(256f, 1560f),
                    PhotoCorner(2104f, 1560f),
                    PhotoCorner(2104f, 3814f),
                    PhotoCorner(256f, 3814f),
                ),
                // GT[2]: Right side
                listOf(
                    PhotoCorner(2226f, 634f),
                    PhotoCorner(3700f, 634f),
                    PhotoCorner(3700f, 2510f),
                    PhotoCorner(2226f, 2510f),
                ),
            )

        println("Image size: ${image.width}x${image.height}")

        // Print ground truth centroids
        println("\nGround truth centroids:")
        for ((i, gt) in groundTruth.withIndex()) {
            val cx = gt.map { it.x }.average()
            val cy = gt.map { it.y }.average()
            val area =
                kotlin.math.abs(
                    gt.mapIndexed { idx, p ->
                            p.x * gt[(idx + 1) % 4].y - gt[(idx + 1) % 4].x * p.y
                        }
                        .sum() / 2
                )
            println("  GT[$i]: centroid=(${cx.toInt()}, ${cy.toInt()}), area=$area")
        }

        val detector = RectangleDetector(minArea = 100000, minQuadRatio = 0.4f)
        val results = detector.detectRectangles(image, expectedCount = 3)

        println("\n=== Detection Results ===")
        println("Detected ${results.size} photos")

        for ((idx, quad) in results.withIndex()) {
            val sorted = sortCorners(quad.corners)
            println(
                "  Quad[$idx]: centroid=(${quad.centroid.x}, ${quad.centroid.y}), area=${quad.area}"
            )
            println("    corners=${sorted.map { "(${it.x},${it.y})" }}")
        }

        val matches = matchQuadsToGroundTruth(results, groundTruth)

        println("\n=== Matching Results ===")
        var totalError = 0.0
        var maxError = 0.0

        for ((quad, gt) in matches) {
            val errors = calculateCornerErrors(quad, gt)
            val avgError = errors.average()
            totalError += errors.sum()
            maxError = maxOf(maxError, errors.maxOrNull() ?: 0.0)

            val gtIdx = groundTruth.indexOf(gt)
            val gtCx = gt.map { it.x }.average()
            val gtCy = gt.map { it.y }.average()
            println("  GT[$gtIdx] (expected centroid ${gtCx.toInt()}, ${gtCy.toInt()}):")
            println("    Corner errors: ${errors.map { "%.1f".format(it) }}")
            println("    Average error: ${"%.1f".format(avgError)}px")
        }

        // Show unmatched ground truths
        val matchedGTIndices = matches.map { groundTruth.indexOf(it.second) }.toSet()
        for ((i, gt) in groundTruth.withIndex()) {
            if (i !in matchedGTIndices) {
                println("  GT[$i]: NOT MATCHED")
            }
        }

        if (matches.isNotEmpty()) {
            println("\n=== Summary ===")
            println("  Matched: ${matches.size}/${groundTruth.size}")
            println("  Total corner error: ${"%.1f".format(totalError)}px")
            println("  Average per corner: ${"%.1f".format(totalError / (matches.size * 4))}px")
            println("  Max error: ${"%.1f".format(maxError)}px")
        }
    }

    @Test
    fun `compare gamma values`() {
        val image = loadImage("org/kryspetrie/fileimport/application/photo-scan-02.jpg")

        val groundTruth =
            listOf(
                listOf(
                    PhotoCorner(270f, 358f),
                    PhotoCorner(1864f, 358f),
                    PhotoCorner(1864f, 1452f),
                    PhotoCorner(270f, 1452f),
                ),
                listOf(
                    PhotoCorner(256f, 1560f),
                    PhotoCorner(2104f, 1560f),
                    PhotoCorner(2104f, 3814f),
                    PhotoCorner(256f, 3814f),
                ),
                listOf(
                    PhotoCorner(2226f, 634f),
                    PhotoCorner(3700f, 634f),
                    PhotoCorner(3700f, 2510f),
                    PhotoCorner(2226f, 2510f),
                ),
            )

        val gammas = listOf(0.3, 0.4, 0.5, 0.6, 0.7, 0.8)

        println("=== Gamma Comparison ===")

        for (gamma in gammas) {
            val detector = RectangleDetector(minArea = 100000, minQuadRatio = 0.4f)
            val results = detector.detectRectangles(image, expectedCount = 3)
            val matches = matchQuadsToGroundTruth(results, groundTruth)

            var totalError = 0.0
            for ((quad, gt) in matches) {
                val errors = calculateCornerErrors(quad, gt)
                totalError += errors.sum()
            }

            val matchedCount = matches.size
            val avgError = if (matchedCount > 0) totalError / matchedCount / 4 else Double.MAX_VALUE

            println(
                "  Gamma=$gamma: detected=${results.size}, matched=$matchedCount, avgError=${"%.1f".format(avgError)}px"
            )
        }
    }
}
