package org.kryspetrie.fileimport.infrastructure.photoscan

import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.math.hypot
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.kryspetrie.fileimport.domain.model.PhotoCorner

/** Debug test to understand why GT[1] is being missed. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DebugGT1MissedTest {

    private lateinit var img02: BufferedImage
    private lateinit var detector: RectangleDetector

    @BeforeAll
    fun setup() {
        img02 = loadImage("org/kryspetrie/fileimport/application/photo-scan-02.jpg")
        detector = RectangleDetector()
    }

    private fun loadImage(path: String): BufferedImage =
        ImageIO.read(javaClass.classLoader.getResourceAsStream(path))!!

    @Test
    fun `debug GT1 detection - show all detected quads with centroids`() {
        println("\n=== Debug GT[1] Detection ===")

        // Ground truth for reference
        val gt0 =
            listOf(
                PhotoCorner(270f, 358f),
                PhotoCorner(1864f, 358f),
                PhotoCorner(1864f, 1452f),
                PhotoCorner(270f, 1452f),
            )
        val gt1 =
            listOf(
                PhotoCorner(256f, 1560f),
                PhotoCorner(2104f, 1560f),
                PhotoCorner(2104f, 3814f),
                PhotoCorner(256f, 3814f),
            )
        val gt2 =
            listOf(
                PhotoCorner(2226f, 634f),
                PhotoCorner(3700f, 634f),
                PhotoCorner(3700f, 2510f),
                PhotoCorner(2226f, 2510f),
            )

        val gtCentroids =
            listOf(
                "GT0" to
                    ((gt0[0].x + gt0[1].x + gt0[2].x + gt0[3].x) / 4 to
                        (gt0[0].y + gt0[1].y + gt0[2].y + gt0[3].y) / 4),
                "GT1" to
                    ((gt1[0].x + gt1[1].x + gt1[2].x + gt1[3].x) / 4 to
                        (gt1[0].y + gt1[1].y + gt1[2].y + gt1[3].y) / 4),
                "GT2" to
                    ((gt2[0].x + gt2[1].x + gt2[2].x + gt2[3].x) / 4 to
                        (gt2[0].y + gt2[1].y + gt2[2].y + gt2[3].y) / 4),
            )

        println("Ground truth centroids:")
        for ((name, centroid) in gtCentroids) {
            println("  $name: (${centroid.first.toInt()}, ${centroid.second.toInt()})")
        }

        // Detect rectangles
        val quads = detector.detectRectangles(img02, expectedCount = 3)

        println("\nDetected ${quads.size} quads:")
        for ((i, quad) in quads.withIndex()) {
            val corners = quad.corners
            val cx = corners.map { it.x }.average()
            val cy = corners.map { it.y }.average()
            val area = corners[0].x * corners[2].y - corners[2].x * corners[0].y

            // Find closest GT
            var closestGT = "None"
            var closestDist = Double.MAX_VALUE
            for ((name, gtCentroid) in gtCentroids) {
                val dist = hypot(cx - gtCentroid.first, cy - gtCentroid.second)
                if (dist < closestDist) {
                    closestDist = dist
                    closestGT = name
                }
            }

            println(
                "  Quad[$i]: centroid=(${cx.toInt()}, ${cy.toInt()}), area=${quad.area}, " +
                    "closest=$closestGT (${"%.0f".format(closestDist)}px away)"
            )
            println("    Corners: ${corners.map { "(${it.x},${it.y})" }.joinToString()}")

            // Calculate errors if this matches a GT
            val gt =
                when (closestGT) {
                    "GT0" -> gt0
                    "GT1" -> gt1
                    "GT2" -> gt2
                    else -> null
                }
            if (gt != null) {
                val sortedCorners =
                    sortCorners(corners.map { PhotoCorner(it.x.toFloat(), it.y.toFloat()) })
                val errors =
                    sortedCorners.mapIndexed { idx, det ->
                        hypot((det.x - gt[idx].x).toDouble(), (det.y - gt[idx].y).toDouble())
                    }
                println(
                    "    Errors: TL=${"%.1f".format(errors[0])}px, TR=${"%.1f".format(errors[1])}px, " +
                        "BR=${"%.1f".format(errors[2])}px, BL=${"%.1f".format(errors[3])}px"
                )
                println(
                    "    Avg=${"%.1f".format(errors.average())}px, Max=${"%.1f".format(errors.maxOrNull()!!)}px"
                )
            }
        }

        // Show what we expect but don't find
        println("\n=== Summary ===")
        println(
            "GT[1] centroid: (${gtCentroids[1].second.first.toInt()}, ${gtCentroids[1].second.second.toInt()})"
        )
        val gt1CenterX = gtCentroids[1].second.first
        val gt1CenterY = gtCentroids[1].second.second

        val closeToGT1 =
            quads.filter { quad ->
                val cx = quad.corners.map { it.x }.average()
                val cy = quad.corners.map { it.y }.average()
                hypot(cx - gt1CenterX, cy - gt1CenterY) < 500
            }
        println("Quads near GT[1] centroid (within 500px): ${closeToGT1.size}")

        // Show areas for context
        println("\nGT areas:")
        println(
            "  GT0 area: approx ${((gt0[1].x - gt0[0].x) * (gt0[3].y - gt0[0].y)).toInt()} pixels"
        )
        println(
            "  GT1 area: approx ${((gt1[1].x - gt1[0].x) * (gt1[3].y - gt1[0].y)).toInt()} pixels"
        )
        println(
            "  GT2 area: approx ${((gt2[1].x - gt2[0].x) * (gt2[3].y - gt2[0].y)).toInt()} pixels"
        )
    }

    private fun sortCorners(corners: List<PhotoCorner>): List<PhotoCorner> {
        if (corners.size != 4) return corners
        val sorted = corners.sortedBy { it.x + it.y }
        val topLeft = sorted[0]
        val bottomRight = sorted[3]
        val remaining = listOf(sorted[1], sorted[2]).sortedBy { it.x - it.y }
        return listOf(topLeft, remaining[0], bottomRight, remaining[1])
    }
}
