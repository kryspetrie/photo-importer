package org.kryspetrie.fileimport.infrastructure.photoscan

import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.math.abs
import org.junit.jupiter.api.Test

class DebugTest {

  private fun loadImage(path: String): BufferedImage {
    val stream = javaClass.classLoader.getResourceAsStream(path)!!
    return ImageIO.read(stream)
  }

  private fun toGrayscale(image: BufferedImage): BufferedImage {
    if (image.type == BufferedImage.TYPE_BYTE_GRAY) return image
    val gray = BufferedImage(image.width, image.height, BufferedImage.TYPE_BYTE_GRAY)
    val g = gray.graphics
    g.drawImage(image, 0, 0, null)
    g.dispose()
    return gray
  }

  @Test
  fun `debug edge gradient values`() {
    val image = loadImage("org/kryspetrie/fileimport/application/photo-scan-02.jpg")
    val gray = toGrayscale(image)
    val raster = gray.getRaster()

    println("Image: ${image.width}x${image.height}")

    // Detected corners from debug output
    val detectedCorners =
        listOf(
            RectangleDetector.Point(256, 1772),
            RectangleDetector.Point(580, 3836),
            RectangleDetector.Point(2048, 3504),
            RectangleDetector.Point(1784, 1560))

    println("\nAnalyzing detected corners:")
    for ((i, corner) in detectedCorners.withIndex()) {
      val x = corner.x.coerceIn(0, image.width - 1)
      val y = corner.y.coerceIn(0, image.height - 1)
      val intensity = raster.getSample(x, y, 0)
      println("  Corner[$i] (${corner.x}, ${corner.y}): intensity=$intensity")

      // Scan outward in different directions
      val directions =
          listOf(
              "right" to Pair(+1, 0),
              "down" to Pair(0, +1),
              "left" to Pair(-1, 0),
              "up" to Pair(0, -1))

      for ((name, dir) in directions) {
        var maxGradient = 0
        var bestDist = 0
        for (dist in 1..100) {
          val sx = (corner.x + dir.first * dist).coerceIn(0, image.width - 1)
          val sy = (corner.y + dir.second * dist).coerceIn(0, image.height - 1)
          val sampleIntensity = raster.getSample(sx, sy, 0)
          val gradient = abs(sampleIntensity - intensity)
          if (gradient > maxGradient) {
            maxGradient = gradient
            bestDist = dist
          }
        }
        println("    $name: maxGradient=$maxGradient at dist=$bestDist")
      }
    }

    // Also check background areas
    println("\nBackground samples:")
    val bgPoints =
        listOf(
            Triple(0, 0, "top-left"),
            Triple(4000, 0, "top-right"),
            Triple(0, 4000, "bottom-left"),
            Triple(4000, 4000, "bottom-right"))
    for ((x, y, name) in bgPoints) {
      val sx = x.coerceIn(0, image.width - 1)
      val sy = y.coerceIn(0, image.height - 1)
      val intensity = raster.getSample(sx, sy, 0)
      println("  $name ($x, $y): intensity=$intensity")
    }

    // Check ground truth corners
    println("\nGround truth corners for GT[1]:")
    val gtCorners =
        listOf(
            RectangleDetector.Point(256, 1560),
            RectangleDetector.Point(2104, 1560),
            RectangleDetector.Point(2104, 3814),
            RectangleDetector.Point(256, 3814))
    for ((i, corner) in gtCorners.withIndex()) {
      val x = corner.x.coerceIn(0, image.width - 1)
      val y = corner.y.coerceIn(0, image.height - 1)
      val intensity = raster.getSample(x, y, 0)
      println("  GT[$i] (${corner.x}, ${corner.y}): intensity=$intensity")
    }
  }
}
