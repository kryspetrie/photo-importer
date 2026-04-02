package org.kryspetrie.fileimport.infrastructure.photoscan

import java.io.File
import javax.imageio.ImageIO
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [YoloKeypointDetector].
 *
 * These tests verify that the detector processes images and returns valid corner sets. They are
 * skipped if the ML model file is not available (empty or missing from resources).
 */
class YoloKeypointDetectorTest {

  private fun isModelAvailable(): Boolean {
    return try {
      val classLoader = javaClass.classLoader
      val modelStream = classLoader.getResourceAsStream("ml_models/yolov8n-pose.onnx")
      modelStream?.use { it.readAllBytes() }?.isNotEmpty() == true
    } catch (_: Exception) {
      false
    }
  }

  @Test
  fun `test detectCorners with real image`() {
    assumeTrue(
        isModelAvailable(),
        "ML model not available — skipping test. " +
            "Ensure ml_models/yolov8n-pose.onnx is present in resources and has non-zero size.")

    val classLoader = javaClass.classLoader
    val file =
        File(
            classLoader.getResource("org/kryspetrie/fileimport/application/photo-scan-01.jpg").file)
    val image = ImageIO.read(file)

    val detector = YoloKeypointDetector("ml_models/yolov8n-pose.onnx", YoloOutputParser())

    val cornerSets = detector.detectCorners(image)

    // A real model may not always find a photo, but if it does, it should have 4 corners.
    // We are mostly testing that the detector doesn't crash.
    cornerSets.forEach { corners -> assertEquals(4, corners.size) }
  }
}
