package org.kryspetrie.fileimport.infrastructure.photoscan

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import java.awt.image.BufferedImage
import java.nio.FloatBuffer
import javax.imageio.ImageIO
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Probes the YOLO OBB model on real scanned photos to verify detection quality.
 *
 * Output: [1, 300, 7] where each detection = [cx_abs, cy_abs, w_abs, h_abs, conf, class, angle_rad]
 * Coordinates are in absolute model-space pixels (0-640), NOT normalized. Angle is in radians.
 */
class YoloObbProbeTest {

  @Test
  fun `probe OBB on photo-scan-01`(@TempDir tempDir: java.io.File) {
    val modelPath = "ml_models/yolo26n-pose-onnx/yolo26n-obb.onnx"
    val imagePath = "org/kryspetrie/fileimport/application/photo-scan-01.jpg"

    val classLoader = javaClass.classLoader
    val modelStream = classLoader.getResourceAsStream(modelPath) ?: return
    val imageStream = classLoader.getResourceAsStream(imagePath) ?: return

    val modelBytes = modelStream.readAllBytes()
    modelStream.close()
    val image = ImageIO.read(imageStream)
    imageStream.close()

    println("\nImage: ${image.width}x${image.height}")

    // Letterbox resize to 640
    val scale = 640.0 / max(image.width, image.height)
    val newW = (image.width * scale).toInt()
    val newH = (image.height * scale).toInt()
    val resized = image.getScaledInstance(newW, newH, BufferedImage.SCALE_DEFAULT)
    val canvas = BufferedImage(640, 640, BufferedImage.TYPE_3BYTE_BGR)
    canvas.graphics.drawImage(resized, 0, 0, null)

    // CHW float32 blob
    val floatBuffer = FloatBuffer.allocate(3 * 640 * 640)
    for (y in 0 until 640) {
      for (x in 0 until 640) {
        val pixel = canvas.getRGB(x, y)
        floatBuffer.put(((pixel shr 16) and 0xFF) / 255.0f) // B
        floatBuffer.put(((pixel shr 8) and 0xFF) / 255.0f) // G
        floatBuffer.put((pixel and 0xFF) / 255.0f) // R
      }
    }
    floatBuffer.rewind()

    val env = OrtEnvironment.getEnvironment()
    val session = env.createSession(modelBytes)
    val inputName = session.inputNames.first()
    val tensor = OnnxTensor.createTensor(env, floatBuffer, longArrayOf(1, 3, 640, 640))
    val results = session.run(mapOf(inputName to tensor))
    val output = results[0].value as Array<Array<FloatArray>>
    tensor.close()

    println("Output shape: [${output.size}, ${output[0].size}, ${output[0][0].size}]")
    println("Ground truth for photo-scan-01: Photo1 TL(365,386), Photo2 TL(1037,1520)")

    println("\nConfidence distribution (all 300 detections):")
    val allConfs = (0 until output[0][0].size).map { output[0][4][it] }
    val sortedConfs = allConfs.map { sigmoid(it) }.sortedDescending()
    println("  Top 20 confidences (sigmoid): ${sortedConfs.take(20)}")
    println("  Min/Max: ${allConfs.minOrNull()}/${allConfs.maxOrNull()}")
    println("  % above 0: ${allConfs.count { it > 0 }} / 300")

    val detections = mutableListOf<Detection>()
    for (i in 0 until output[0][0].size) {
      val conf = sigmoid(output[0][4][i])

      val cxModel = output[0][0][i]
      val cyModel = output[0][1][i]
      val wModel = output[0][2][i]
      val hModel = output[0][3][i]
      val angleRad = output[0][6][i].toDouble()

      val cxOrig = cxModel / scale
      val cyOrig = cyModel / scale
      val wOrig = wModel / scale
      val hOrig = hModel / scale

      val cosA = cos(angleRad)
      val sinA = sin(angleRad)
      val hw = wOrig / 2.0
      val hh = hOrig / 2.0
      val corners =
          listOf(
              doubleArrayOf(
                  cxOrig + (-hw) * cosA - (-hh) * sinA, cyOrig + (-hw) * sinA + (-hh) * cosA),
              doubleArrayOf(
                  cxOrig + (hw) * cosA - (-hh) * sinA, cyOrig + (hw) * sinA + (-hh) * cosA),
              doubleArrayOf(cxOrig + (hw) * cosA - (hh) * sinA, cyOrig + (hw) * sinA + (hh) * cosA),
              doubleArrayOf(
                  cxOrig + (-hw) * cosA - (hh) * sinA, cyOrig + (-hw) * sinA + (hh) * cosA))
    }

    detections.sortByDescending { it.confidence }
    println("\nTop detections (conf > 0.05):")
    for (d in detections.take(10)) {
      val cStr = d.corners.joinToString("") { "(${it[0].toInt()},${it[1].toInt()})" }
      println(
          "  det[${d.index}] conf=${d.confidence} center=(${d.cx.toInt()},${d.cy.toInt()}) " +
              "size=${d.w.toInt()}x${d.h.toInt()} angle=${Math.toDegrees(d.angle).toInt()}deg corners=$cStr")
    }
    println("Total: ${detections.size}")
  }

  private fun sigmoid(x: Float): Float {
    return if (x > 20f) 1.0f
    else if (x < -20f) 0.0f else (1.0f / (1.0f + kotlin.math.exp(-x.toDouble()))).toFloat()
  }

  private data class Detection(
      val index: Int,
      val confidence: Float,
      val cx: Double,
      val cy: Double,
      val w: Double,
      val h: Double,
      val angle: Double,
      val corners: List<DoubleArray>
  )
}
