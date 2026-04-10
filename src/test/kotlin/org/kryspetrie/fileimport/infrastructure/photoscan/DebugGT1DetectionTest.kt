package org.kryspetrie.fileimport.infrastructure.photoscan

import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.math.hypot
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.kryspetrie.fileimport.domain.model.PhotoCorner

/**
 * Debug test to understand what's happening with GT[1] detection.
 *
 * The original detector finds: Photo[0]: (240,1816), (1748,1544), (2028,3100), (584,3804)
 *
 * But GT[1] ground truth is: TL(256,1560), TR(2104,1560), BR(2104,3814), BL(256,3814)
 *
 * These corners are completely different! The detector is finding wrong contours entirely.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DebugGT1DetectionTest {

  private lateinit var img02: BufferedImage
  private lateinit var detector: RectangleDetector

  @BeforeAll
  fun setup() {
    img02 = loadImage("org/kryspetrie/fileimport/application/photo-scan-02.jpg")
    detector = RectangleDetector(minArea = 100000, minQuadRatio = 0.4f)
  }

  private fun loadImage(path: String): BufferedImage =
      ImageIO.read(javaClass.classLoader.getResourceAsStream(path))!!

  @Test
  fun `analyze what contours are found for GT1 region`() {
    println("Image size: ${img02.width}x${img02.height}")
    println("\nGT[1] ground truth:")
    println("  TL: (256, 1560)")
    println("  TR: (2104, 1560)")
    println("  BR: (2104, 3814)")
    println("  BL: (256, 3814)")
    println("  Centroid: (1180, 2687)")
    println("  Width: 1848px, Height: 2254px")

    // Run detector
    val results = detector.detectRectangles(img02, expectedCount = 3)
    println("\nDetected ${results.size} quadrilaterals:")
    for ((i, quad) in results.withIndex()) {
      val sorted = sortCorners(quad.corners)
      val cx = quad.centroid.x.toFloat()
      val cy = quad.centroid.y.toFloat()
      val distToGT1 = hypot(cx - 1180.0, cy - 2687.0)
      println("  Quad[$i]: centroid=(${quad.centroid.x}, ${quad.centroid.y}), area=${quad.area}")
      println("    corners=${sorted.map { "(${it.x},${it.y})" }}")
      println("    dist to GT1 centroid: ${"%.0f".format(distToGT1)}px")
    }

    // Analyze the matched detection
    println("\n=== Matching to GT[1] ===")
    val gt1CentroidX = 1180.0
    val gt1CentroidY = 2687.0

    val sortedResults =
        results.sortedBy { hypot(it.centroid.x - gt1CentroidX, it.centroid.y - gt1CentroidY) }

    if (sortedResults.isNotEmpty()) {
      val best = sortedResults[0]
      println("\nClosest to GT[1]:")
      println("  Detected centroid: (${best.centroid.x}, ${best.centroid.y})")
      println(
          "  Distance: ${"%.0f".format(hypot(best.centroid.x - gt1CentroidX, best.centroid.y - gt1CentroidY))}px")

      val sorted = sortCorners(best.corners)
      println("  Sorted corners: ${sorted.map { "(${it.x},${it.y})" }}")

      val gt1 =
          listOf(
              PhotoCorner(256f, 1560f),
              PhotoCorner(2104f, 1560f),
              PhotoCorner(2104f, 3814f),
              PhotoCorner(256f, 3814f))
      val sortedGT = sortCornersGT(gt1)

      println("\n  Corner-by-corner error analysis:")
      for (i in 0 until 4) {
        val det = sorted[i]
        val gt = sortedGT[i]
        val err = hypot((det.x - gt.x).toDouble(), (det.y - gt.y).toDouble())
        val label =
            when (i) {
              0 -> "TL"
              1 -> "TR"
              2 -> "BR"
              else -> "BL"
            }
        println(
            "    $label: detected (${det.x}, ${det.y}), GT (${gt.x.toInt()}, ${gt.y.toInt()}), error: ${"%.1f".format(err)}px")

        // Is the detected corner inside or outside GT?
        val isInside =
            det.x >= gt.x - 100 && det.x <= gt.x + 100 && det.y >= gt.y - 100 && det.y <= gt.y + 100
        println("      -> ${if (isInside) "WITHIN 100px of GT" else "WRONG LOCATION"}")
      }
    }
  }

  @Test
  fun `compare different gamma values for GT1 detection`() {
    println("\n=== Gamma Comparison for GT[1] Detection ===")

    val gammas = listOf(0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 1.0)

    val gt1 =
        listOf(
            PhotoCorner(256f, 1560f),
            PhotoCorner(2104f, 1560f),
            PhotoCorner(2104f, 3814f),
            PhotoCorner(256f, 3814f))
    val sortedGT = sortCornersGT(gt1)

    for (gamma in gammas) {
      val det = RectangleDetector(minArea = 100000, minQuadRatio = 0.4f)
      val results = det.detectRectangles(img02, expectedCount = 3)

      // Find closest to GT1
      val gt1CentroidX = 1180.0
      val gt1CentroidY = 2687.0

      val best =
          results.minByOrNull { hypot(it.centroid.x - gt1CentroidX, it.centroid.y - gt1CentroidY) }

      if (best != null) {
        val sorted = sortCorners(best.corners)
        val errors =
            sorted.mapIndexed { i, p ->
              hypot((p.x - sortedGT[i].x).toDouble(), (p.y - sortedGT[i].y).toDouble())
            }
        val avgErr = errors.average()
        val maxErr = errors.maxOrNull() ?: 0.0

        println(
            "Gamma=$gamma: avgErr=${"%.1f".format(avgErr)}px, maxErr=${"%.1f".format(maxErr)}px")
        println("  Detected: ${sorted.map { "(${it.x},${it.y})" }}")
      } else {
        println("Gamma=$gamma: NO MATCH")
      }
    }
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
}
