package org.kryspetrie.fileimport.application

import java.io.File
import kotlin.math.abs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.kryspetrie.fileimport.domain.model.PhotoCorner
import org.kryspetrie.fileimport.infrastructure.photoscan.RectangleDetector
import org.kryspetrie.fileimport.infrastructure.photoscan.YoloKeypointDetector
import org.kryspetrie.fileimport.infrastructure.photoscan.YoloOutputParser

/**
 * Ground truth tests for photo scan detection.
 *
 * These tests verify that the YOLOv8-Pose model correctly detects photo corners in known scanned
 * images. They are skipped if the ML model file is not available (empty or missing from resources).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PhotoScanGroundTruthTest {

  private lateinit var scanService: ScanService

  @BeforeAll
  fun setup() {
    val detector = YoloKeypointDetector("ml_models/yolov8n-pose.onnx", YoloOutputParser())
    scanService = ScanService(MockImageRepository(), detector, RectangleDetector())
  }

  private fun isModelAvailable(): Boolean {
    val detector = YoloKeypointDetector("ml_models/yolov8n-pose.onnx", YoloOutputParser())
    return try {
      // Check by attempting to load the model file
      val classLoader = javaClass.classLoader
      val modelStream = classLoader.getResourceAsStream("ml_models/yolov8n-pose.onnx")
      modelStream?.use { it.readAllBytes() }?.isNotEmpty() == true
    } catch (_: Exception) {
      false
    }
  }

  @Test
  fun `test photo-scan-01 ground truth`() {
    assumeTrue(
        isModelAvailable(),
        "ML model not available — skipping ground truth test. " +
            "Ensure ml_models/yolov8n-pose.onnx is present in resources.")

    val classLoader = javaClass.classLoader
    val file =
        File(
            classLoader.getResource("org/kryspetrie/fileimport/application/photo-scan-01.jpg").file)
    val detectedPhotos = scanService.detectPhotos(file.absolutePath)

    assertEquals(2, detectedPhotos.size)

    val groundTruth1 =
        listOf(
            PhotoCorner(365f, 1030f),
            PhotoCorner(365f, 386f),
            PhotoCorner(1388f, 386f),
            PhotoCorner(1388f, 1030f))
    val groundTruth2 =
        listOf(
            PhotoCorner(1428f, 2777f),
            PhotoCorner(1037f, 2104f),
            PhotoCorner(1967f, 1520f),
            PhotoCorner(2394f, 2128f))

    val detectedCorners1 =
        detectedPhotos[0].let { listOf(it.topLeft, it.topRight, it.bottomRight, it.bottomLeft) }
    val detectedCorners2 =
        detectedPhotos[1].let { listOf(it.topLeft, it.topRight, it.bottomRight, it.bottomLeft) }

    assertTrue(areClose(groundTruth1, detectedCorners1, 10.0f))
    assertTrue(areClose(groundTruth2, detectedCorners2, 10.0f))
  }

  @Test
  fun `test photo-scan-02 ground truth`() {
    assumeTrue(
        isModelAvailable(),
        "ML model not available — skipping ground truth test. " +
            "Ensure ml_models/yolov8n-pose.onnx is present in resources.")

    val classLoader = javaClass.classLoader
    val file =
        File(
            classLoader.getResource("org/kryspetrie/fileimport/application/photo-scan-02.jpg").file)
    val detectedPhotos = scanService.detectPhotos(file.absolutePath)

    assertEquals(3, detectedPhotos.size)

    val groundTruth1 =
        listOf(
            PhotoCorner(376f, 1452f),
            PhotoCorner(270f, 358f),
            PhotoCorner(1864f, 364f),
            PhotoCorner(1862f, 1418f))
    val groundTruth2 =
        listOf(
            PhotoCorner(616f, 3814f),
            PhotoCorner(256f, 1832f),
            PhotoCorner(1746f, 1560f),
            PhotoCorner(2104f, 3548f))
    val groundTruth3 =
        listOf(
            PhotoCorner(2226f, 2394f),
            PhotoCorner(2374f, 634f),
            PhotoCorner(3700f, 744f),
            PhotoCorner(3600f, 2510f))

    val detectedCorners1 =
        detectedPhotos[0].let { listOf(it.topLeft, it.topRight, it.bottomRight, it.bottomLeft) }
    val detectedCorners2 =
        detectedPhotos[1].let { listOf(it.topLeft, it.topRight, it.bottomRight, it.bottomLeft) }
    val detectedCorners3 =
        detectedPhotos[2].let { listOf(it.topLeft, it.topRight, it.bottomRight, it.bottomLeft) }

    assertTrue(areClose(groundTruth1, detectedCorners1, 10.0f))
    assertTrue(areClose(groundTruth2, detectedCorners2, 10.0f))
    assertTrue(areClose(groundTruth3, detectedCorners3, 10.0f))
  }

  private fun areClose(a: List<PhotoCorner>, b: List<PhotoCorner>, tolerance: Float): Boolean {
    if (a.size != b.size) return false
    for (i in a.indices) {
      if (abs(a[i].x - b[i].x) > tolerance || abs(a[i].y - b[i].y) > tolerance) {
        return false
      }
    }
    return true
  }
}
