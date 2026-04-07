package org.kryspetrie.fileimport.infrastructure.photoscan

import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Test to debug corner refinement.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CornerRefinementDebugTest {

  private lateinit var img02: BufferedImage

  @BeforeAll
  fun setup() {
    img02 = loadImage("org/kryspetrie/fileimport/application/photo-scan-02.jpg")
  }

  private fun loadImage(path: String): BufferedImage =
      ImageIO.read(javaClass.classLoader.getResourceAsStream(path))!!

  @Test
  fun `test corner refinement on GT0`() {
    val detector = RectangleDetector()
    
    // GT0 ground truth: TL(270,358), TR(1864,358), BR(1864,1452), BL(270,1452)
    // GT0 detected (from earlier test): (240,344), (1888,352), (1888,1412), (348,1448)
    
    val detectedCorners = listOf(
        RectangleDetector.Point(240, 344),  // TL
        RectangleDetector.Point(1888, 352),   // TR
        RectangleDetector.Point(1888, 1412),  // BR
        RectangleDetector.Point(348, 1448)    // BL
    )
    
    val gt0Corners = listOf(
        RectangleDetector.Point(270, 358),   // TL
        RectangleDetector.Point(1864, 358),  // TR
        RectangleDetector.Point(1864, 1452), // BR
        RectangleDetector.Point(270, 1452)    // BL
    )
    
    println("=== GT0 Corner Refinement Debug ===")
    println("GT0 corners: ${gt0Corners.map { "(${it.x},${it.y})" }.joinToString()}")
    println("Detected:    ${detectedCorners.map { "(${it.x},${it.y})" }.joinToString()}")
    
    // Show errors
    val errors = detectedCorners.mapIndexed { i, det ->
      val gt = gt0Corners[i]
      val err = hypot((det.x - gt.x).toDouble(), (det.y - gt.y).toDouble())
      println("Corner[$i]: detected=(${det.x},${det.y}), gt=(${gt.x},${gt.y}), error=${"%.1f".format(err)}px")
      err
    }
    println("Avg error: ${"%.1f".format(errors.average())}px, Max: ${"%.1f".format(errors.maxOrNull()!!)}px")
    
    // Test edge gradient search
    println("\n=== Testing Edge Gradient Search ===")
    val gradientMagnitude = detector.computeGradientMagnitudeForDebug(img02)
    
    for ((i, corner) in detectedCorners.withIndex()) {
      println("\nCorner[$i] at (${corner.x}, ${corner.y}):")
      
      // Check gradients around this corner
      for (dy in -50..50 step 25) {
        for (dx in -50..50 step 25) {
          val x = (corner.x + dx).coerceIn(0, img02.width - 1)
          val y = (corner.y + dy).coerceIn(0, img02.height - 1)
          val grad = gradientMagnitude[y][x]
          if (grad > 50) {
            println("  At ($x, $y): gradient=$grad")
          }
        }
      }
    }
    
    // Save gradient image
    val gradImage = BufferedImage(img02.width, img02.height, BufferedImage.TYPE_BYTE_GRAY)
    var maxGrad = 0.0
    for (y in 0 until img02.height) {
      for (x in 0 until img02.width) {
        maxGrad = maxOf(maxGrad, gradientMagnitude[y][x])
      }
    }
    for (y in 0 until img02.height) {
      for (x in 0 until img02.width) {
        val normalized = (gradientMagnitude[y][x] / maxGrad * 255).toInt().coerceIn(0, 255)
        gradImage.setRGB(x, y, (normalized shl 16) or (normalized shl 8) or normalized)
      }
    }
    ImageIO.write(gradImage, "PNG", java.io.File("/tmp/gradient_debug.png"))
    println("\nGradient image saved to /tmp/gradient_debug.png")
  }
}
