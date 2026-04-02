package org.kryspetrie.fileimport.infrastructure.photoscan

import java.io.File
import javax.imageio.ImageIO
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Integration tests for [OrientedBoundingBoxDetector].
 *
 * These tests verify the OBB detection pipeline runs without errors. The YOLO26-OBB model was
 * trained on DOTAv1 aerial imagery (planes, ships, harbors) and is not expected to produce
 * meaningful detections on scanned photo documents.
 */
class OrientedBoundingBoxDetectorTest {

  private lateinit var detector: OrientedBoundingBoxDetector
  private lateinit var modelPath: String

  @TempDir lateinit var tempDir: File

  @BeforeEach
  fun setup() {
    modelPath = "ml_models/yolo26n-pose-onnx/yolo26n-obb.onnx"
    // Skip if model file is missing
    assumeTrue(
        javaClass.classLoader.getResourceAsStream(modelPath)?.readAllBytes()?.isNotEmpty() == true,
        "OBB model not available at $modelPath")
    detector = OrientedBoundingBoxDetector(modelPath)
  }

  @Nested
  inner class PipelineTests {
    /**
     * Integration test verifying the OBB model pipeline runs end-to-end without errors.
     *
     * The YOLO26-OBB model was trained on DOTAv1 aerial imagery (planes, ships, harbors). It is NOT
     * trained on photo documents and will not produce meaningful detections on scanned desk photos.
     * This test only verifies the pipeline (preprocessing → inference → parsing → corner
     * derivation) produces structurally valid output.
     */
    @Test
    fun `test pipeline runs without errors and produces valid output`() {
      val image = loadImage("org/kryspetrie/fileimport/application/photo-scan-01.jpg")
      val results = detector.detectRectangles(image, minConfidence = 0.01f)

      for (corners in results) {
        assertEquals(4, corners.size, "Each detection must have exactly 4 corners")
        for (corner in corners) {
          assertTrue(
              corner.x.isFinite() && corner.y.isFinite(), "Corner coordinates must be finite")
          assertTrue(
              corner.x >= 0f &&
                  corner.x <= image.width &&
                  corner.y >= 0f &&
                  corner.y <= image.height,
              "Corner (${corner.x}, ${corner.y}) must be within image bounds [0,${image.width}]x[0,${image.height}]")
        }
      }
    }

    @Test
    fun `test pipeline handles various confidence thresholds without crashing`() {
      val image = loadImage("org/kryspetrie/fileimport/application/photo-scan-01.jpg")

      for (conf in listOf(0.01f, 0.05f, 0.1f, 0.15f, 0.25f, 0.5f)) {
        val results = detector.detectRectangles(image, minConfidence = conf)
        // Pipeline should complete without throwing regardless of threshold
        assertTrue(
            results.size <= 20, "Unreasonably many detections at conf=$conf: ${results.size}")
      }
    }

    @Test
    fun `test detection corners form non-degenerate polygons`() {
      val image = loadImage("org/kryspetrie/fileimport/application/photo-scan-01.jpg")
      val results = detector.detectRectangles(image, minConfidence = 0.01f)

      for (corners in results) {
        assertEquals(4, corners.size, "Must have exactly 4 corners")
        // Verify corners aren't all the same point
        val uniquePoints = corners.distinct()
        assertTrue(
            uniquePoints.size >= 3,
            "Corners should form a polygon, not collapse to a point (got ${uniquePoints.size} unique)")
      }
    }
  }

  @Nested
  inner class RobustnessTests {
    @Test
    fun `test returns empty list when model unavailable`() {
      val badDetector = OrientedBoundingBoxDetector("ml_models/nonexistent.onnx")
      val image = loadImage("org/kryspetrie/fileimport/application/photo-scan-01.jpg")
      val results = badDetector.detectRectangles(image)
      assertTrue(results.isEmpty(), "Should return empty list when model unavailable")
    }

    @Test
    fun `test handles photo-scan-02 without crashing`() {
      val image = loadImage("org/kryspetrie/fileimport/application/photo-scan-02.jpg")
      val results = detector.detectRectangles(image, minConfidence = 0.01f)
      // Just verify no crash — results may or may not be present
      assertTrue(results.size <= 20)
    }
  }

  private fun loadImage(path: String): java.awt.image.BufferedImage {
    val stream = javaClass.classLoader.getResourceAsStream(path)
    return ImageIO.read(stream!!)
  }
}
