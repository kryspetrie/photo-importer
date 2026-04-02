package org.kryspetrie.fileimport.infrastructure.photoscan

import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.kryspetrie.fileimport.domain.model.PhotoCorner

/**
 * Unit tests for [RectangleDetector].
 *
 * Verifies the classical computer vision pipeline for detecting rectangular photo regions:
 * grayscale → adaptive threshold → morph close → contour find → quad approx → filter → NMS
 */
class RectangleDetectorTest {

  @TempDir lateinit var tempDir: File

  private fun loadImage(path: String): java.awt.image.BufferedImage {
    val stream = javaClass.classLoader.getResourceAsStream(path)!!
    return ImageIO.read(stream)
  }

  @Test
  fun `detects rectangles on photo-scan-01`() {
    val image = loadImage("org/kryspetrie/fileimport/application/photo-scan-01.jpg")
    val detector = RectangleDetector(minArea = 1000)
    val results = detector.detectRectangles(image, expectedCount = 2)

    assertTrue(results.isNotEmpty(), "Expected at least 1 detection, got ${results.size}")
    for (quad in results) {
      assertEquals(4, quad.corners.size)
    }
  }

  @Test
  fun `detected corners are within image bounds`() {
    val image = loadImage("org/kryspetrie/fileimport/application/photo-scan-01.jpg")
    val detector = RectangleDetector(minArea = 1000)
    val results = detector.detectRectangles(image, expectedCount = 2)

    for (quad in results) {
      for (corner in quad.corners) {
        assertTrue(
            corner.x >= 0 && corner.x <= image.width, "Corner x=${corner.x} out of image bounds")
        assertTrue(
            corner.y >= 0 && corner.y <= image.height, "Corner y=${corner.y} out of image bounds")
      }
    }
  }

  @Test
  fun `corners match ground truth within tolerance`() {
    val image = loadImage("org/kryspetrie/fileimport/application/photo-scan-01.jpg")
    val detector = RectangleDetector(minArea = 1000, minQuadRatio = 0.3f)
    val results = detector.detectRectangles(image, expectedCount = 2)

    // Ground truth corners (from PhotoScanGroundTruthTest):
    val groundTruths =
        listOf(
            listOf(
                PhotoCorner(365f, 386f),
                PhotoCorner(1388f, 386f),
                PhotoCorner(1388f, 1030f),
                PhotoCorner(365f, 1030f)),
            listOf(
                PhotoCorner(1037f, 1520f),
                PhotoCorner(1967f, 1520f),
                PhotoCorner(2394f, 2128f),
                PhotoCorner(1428f, 2128f)))

    assertTrue(results.isNotEmpty(), "Expected detections")
    var matched = 0
    for (detected in results) {
      for (truth in groundTruths) {
        if (cornersMatch(detected.corners, truth, 100f)) {
          matched++
          break
        }
      }
    }
    assertTrue(
        matched >= 1,
        "Expected at least 1 detection to match ground truth. Got $matched / ${groundTruths.size}")
  }

  @Test
  fun `handles empty image gracefully`() {
    val image = java.awt.image.BufferedImage(100, 100, java.awt.image.BufferedImage.TYPE_BYTE_GRAY)
    val detector = RectangleDetector()
    val results = detector.detectRectangles(image)
    assertTrue(results.isEmpty(), "Empty image should produce no detections")
  }

  private fun cornersMatch(
      detected: List<RectangleDetector.Point>,
      truth: List<PhotoCorner>,
      tolerance: Float
  ): Boolean {
    if (detected.size != 4 || truth.size != 4) return false
    val detSorted = detected.sortedBy { it.x + it.y }
    val truthSorted = truth.sortedBy { it.x + it.y }
    for (i in 0 until 4) {
      if (abs(detSorted[i].x - truthSorted[i].x) > tolerance ||
          abs(detSorted[i].y - truthSorted[i].y) > tolerance) {
        return false
      }
    }
    return true
  }
}
