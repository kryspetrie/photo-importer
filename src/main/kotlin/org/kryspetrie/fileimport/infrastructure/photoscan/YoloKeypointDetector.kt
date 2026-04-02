package org.kryspetrie.fileimport.infrastructure.photoscan

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.awt.image.BufferedImage
import java.nio.FloatBuffer
import org.kryspetrie.fileimport.domain.model.PhotoCorner

/**
 * YOLOv8-Pose keypoint detector for photo corner detection.
 *
 * Loads a YOLOv8-Pose ONNX model from resources and runs inference to detect photo corners in
 * scanned images. Each detection produces 4 keypoints: top-left, top-right, bottom-right,
 * bottom-left corners.
 *
 * ## Model Requirements
 *
 * The ONNX model must be a YOLOv8-Pose model fine-tuned for document corner detection. Expected
 * tensor shapes:
 * - Input: [1, 3, 640, 640] (batch, channels, height, width)
 * - Output: [1, 17, 8400] (batch, keypoint_dim, num_proposals) where keypoint_dim = 4 * 3 (x, y,
 *   confidence for each of 4 corners) + 4 (bbox cx, cy, w, h)
 *
 * If the model resource cannot be loaded or inference fails, [detectCorners] returns an empty list
 * rather than throwing, allowing the UI to degrade gracefully.
 *
 * @param modelPath Resource path to the ONNX model file (e.g., "ml_models/yolov8n-pose.onnx")
 * @param parser Output parser that converts raw model tensors to [PhotoCorner] lists
 */
class YoloKeypointDetector(modelPath: String, private val parser: YoloOutputParser) {

  private val ortEnv: OrtEnvironment = OrtEnvironment.getEnvironment()
  private var session: OrtSession? = null
  private var modelAvailable = false

  init {
    loadModel(modelPath)
  }

  private fun loadModel(modelPath: String) {
    try {
      val modelBytes = javaClass.classLoader.getResourceAsStream(modelPath)?.readAllBytes()
      if (modelBytes == null || modelBytes.isEmpty()) {
        System.err.println(
            "ML model resource not found or empty at path: $modelPath. " +
                "Photo corner detection will be disabled.")
        modelAvailable = false
      } else {
        session = ortEnv.createSession(modelBytes)
        modelAvailable = true
      }
    } catch (e: Exception) {
      System.err.println(
          "Failed to load ONNX model at path '$modelPath': ${e.message}. " +
              "Photo corner detection will be disabled.")
      modelAvailable = false
    }
  }

  /**
   * Detects photo corners in the given image.
   *
   * Runs the YOLOv8-Pose model and returns a list of corner sets, one per detected photo. Each
   * corner set contains exactly 4 [PhotoCorner] values: top-left, top-right, bottom-right,
   * bottom-left.
   *
   * @param image The scanned image to process
   * @return A list of detected corner sets; empty if no photos detected or model unavailable
   */
  fun detectCorners(image: BufferedImage): List<List<PhotoCorner>> {
    val activeSession = session
    if (!modelAvailable || activeSession == null) {
      return emptyList()
    }

    return try {
      val inputTensor = preprocessImage(image)
      val inputs = mapOf(activeSession.inputNames.first() to inputTensor)
      val results = activeSession.run(inputs)
      val outputTensor = results[0].value as Array<Array<FloatArray>>

      parser.parse(outputTensor, image.width, image.height)
    } catch (e: Exception) {
      System.err.println("Corner detection inference failed: ${e.message}")
      emptyList()
    }
  }

  private fun preprocessImage(image: BufferedImage): OnnxTensor {
    val resizedImage = image.getScaledInstance(640, 640, BufferedImage.SCALE_DEFAULT)
    val bufferedImage = BufferedImage(640, 640, BufferedImage.TYPE_INT_RGB)
    bufferedImage.graphics.drawImage(resizedImage, 0, 0, null)

    val floatBuffer = FloatBuffer.allocate(3 * 640 * 640)

    for (y in 0 until 640) {
      for (x in 0 until 640) {
        val pixel = bufferedImage.getRGB(x, y)
        val r = ((pixel shr 16) and 0xFF) / 255.0f
        val g = ((pixel shr 8) and 0xFF) / 255.0f
        val b = (pixel and 0xFF) / 255.0f
        floatBuffer.put(r)
        floatBuffer.put(g)
        floatBuffer.put(b)
      }
    }
    floatBuffer.rewind()

    val shape = longArrayOf(1, 3, 640, 640)
    return OnnxTensor.createTensor(ortEnv, floatBuffer, shape)
  }
}
