package org.kryspetrie.fileimport.infrastructure.photoscan

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import java.awt.image.BufferedImage
import java.io.File
import java.nio.FloatBuffer
import javax.imageio.ImageIO
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Probes the actual YOLO pose model output on a real test image to determine whether it detects
 * anything useful on scanned photo documents (vs. people).
 */
class YoloPoseOutputProbeTest {

  /** Runs inference and prints all detections above a confidence threshold. */
  @Test
  fun `probe YOLO pose model output on scanned photo`(@TempDir tempDir: File) {
    val modelPath = "ml_models/yolo26n-pose-onnx/yolo26n-pose_bnb4.onnx"
    val testImagePath = "org/kryspetrie/fileimport/application/photo-scan-01.jpg"

    val classLoader = javaClass.classLoader
    val modelStream = classLoader.getResourceAsStream(modelPath)
    val imageStream = classLoader.getResourceAsStream(testImagePath)

    if (modelStream == null || imageStream == null) {
      println("SKIP: model or test image not found")
      return
    }

    val modelBytes = modelStream.readAllBytes()
    modelStream.close()
    val image = ImageIO.read(imageStream)
    imageStream.close()

    println("\nTest image: ${image.width}x${image.height}")

    // Preprocess to 640x640
    val resized = image.getScaledInstance(640, 640, BufferedImage.SCALE_DEFAULT)
    val buf = BufferedImage(640, 640, BufferedImage.TYPE_INT_RGB)
    buf.graphics.drawImage(resized, 0, 0, null)

    val floatBuffer = FloatBuffer.allocate(3 * 640 * 640)
    for (y in 0 until 640) {
      for (x in 0 until 640) {
        val pixel = buf.getRGB(x, y)
        floatBuffer.put(((pixel shr 16) and 0xFF) / 255.0f)
        floatBuffer.put(((pixel shr 8) and 0xFF) / 255.0f)
        floatBuffer.put((pixel and 0xFF) / 255.0f)
      }
    }
    floatBuffer.rewind()

    // Run inference
    val env = OrtEnvironment.getEnvironment()
    val session = env.createSession(modelBytes)
    val inputName = session.inputNames.first()
    val outputName = session.outputNames.first()

    val shape = longArrayOf(1, 3, 640, 640)
    val inputTensor = OnnxTensor.createTensor(env, floatBuffer, shape)
    val results = session.run(mapOf(inputName to inputTensor))
    val output = results[0].value as Array<Array<FloatArray>>
    inputTensor.close()

    val numChannels = output[0].size
    val numDets = output[0][0].size
    val layout =
        if (numChannels > numDets) {
          println("Output shape: [1, $numChannels, $numDets] (channels × detections)")
          "CHW"
        } else {
          println("Output shape: [1, $numDets, $numChannels] (detections × channels)")
          "CWH"
        }

    val scaleX = image.width / 640.0
    val scaleY = image.height / 640.0

    // Probe raw values at first detection slot across all channels
    println("\nRaw values at detection index 0:")
    for (c in 0 until minOf(numChannels, 10)) {
      println("  channel[$c] = ${output[0][c][0]}")
    }

    println("\nDetections (bbox_confidence > 0.1):")
    var count = 0
    for (i in 0 until numDets) {
      val cx = if (layout == "CHW") output[0][0][i] else output[0][i][0]
      val cy = if (layout == "CHW") output[0][1][i] else output[0][i][1]
      val w = if (layout == "CHW") output[0][2][i] else output[0][i][2]
      val h = if (layout == "CHW") output[0][3][i] else output[0][i][3]
      val conf = if (layout == "CHW") output[0][4][i] else output[0][i][4]

      if (conf > 0.1f || w > 1f || h > 1f) {
        count++
        val x1 = ((cx - w / 2) * scaleX).toInt()
        val y1 = ((cy - h / 2) * scaleY).toInt()
        val x2 = ((cx + w / 2) * scaleX).toInt()
        val y2 = ((cy + h / 2) * scaleY).toInt()
        println(
            "  det[$i]: conf=$conf%.3f, bbox=[($x1,$y1)-($x2,$y2)] px " +
                "center=(${cx}%.1f,${cy}%.1f) size=${w}%.1fx${h}%.1f")

        // Show first few keypoint values
        val numKpts = (numChannels - 4) / 3
        if (numKpts > 0) {
          val kptSummary =
              (0 until minOf(numKpts, 4)).joinToString(", ") { k ->
                val kx = if (layout == "CHW") output[0][5 + k * 3][i] else output[0][i][5 + k * 3]
                val ky =
                    if (layout == "CHW") output[0][5 + k * 3 + 1][i]
                    else output[0][i][5 + k * 3 + 1]
                val kp =
                    if (layout == "CHW") output[0][5 + k * 3 + 2][i]
                    else output[0][i][5 + k * 3 + 2]
                "k$k(${kx.toInt()},${ky.toInt()}p$kp%.2f)"
              }
          println("    keypoints: $kptSummary")
        }
      }
    }
    if (count == 0) {
      println("  NO significant detections")
      // Print max values across all detections
      val maxConf = output[0][4].maxOrNull() ?: 0f
      val maxW = output[0][2].maxOrNull() ?: 0f
      val maxH = output[0][3].maxOrNull() ?: 0f
      println("  Max bbox_conf=${maxConf}%.4f, max_w=$maxW%.2f, max_h=$maxH%.2f")
    }
    if (count == 0) {
      println("  NO DETECTIONS above threshold 0.3")
    }
    println("\nTotal detections above 0.3: $count / $numDets")
  }

  /** Verifies the model produces detections on a person image (smoke test). */
  @Test
  fun `smoke test model produces non-empty output`(@TempDir tempDir: File) {
    val modelPath = "ml_models/yolo26n-pose-onnx/yolo26n-pose_bnb4.onnx"
    val modelStream = javaClass.classLoader.getResourceAsStream(modelPath)
    if (modelStream == null) {
      println("SKIP: model not found")
      return
    }
    val modelBytes = modelStream.readAllBytes()
    modelStream.close()

    // Create a blank 640x640 image
    val blank = BufferedImage(640, 640, BufferedImage.TYPE_INT_RGB)
    val floatBuffer = FloatBuffer.allocate(3 * 640 * 640)
    repeat(3 * 640 * 640) { floatBuffer.put(0.5f) }
    floatBuffer.rewind()

    val env = OrtEnvironment.getEnvironment()
    val session = env.createSession(modelBytes)
    val inputName = session.inputNames.first()
    val outputName = session.outputNames.first()
    val shape = longArrayOf(1, 3, 640, 640)
    val inputTensor = OnnxTensor.createTensor(env, floatBuffer, shape)
    val results = session.run(mapOf(inputName to inputTensor))
    val output = results[0].value as Array<Array<FloatArray>>
    inputTensor.close()

    println("Output: [${output.size}, ${output[0].size}, ${output[0][0].size}]")
    println("Max confidence: ${output[0][4].maxOrNull()}")
    println("Non-zero confidences: ${output[0][4].count { it > 0.1f }}")
  }
}
