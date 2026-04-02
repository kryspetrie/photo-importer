package org.kryspetrie.fileimport.infrastructure.photoscan

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.awt.image.BufferedImage
import java.nio.FloatBuffer
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import org.kryspetrie.fileimport.domain.model.PhotoCorner

/**
 * Oriented Bounding Box (OBB) detector using a YOLOv8-OBB ONNX model.
 *
 * Loads a YOLO OBB model (e.g. YOLO26n-obb.onnx) and runs inference to detect rotated rectangular
 * objects — specifically photographs placed on a scanning surface. The model outputs oriented
 * bounding boxes with angle, from which the 4 corner coordinates are derived.
 *
 * ## Model Requirements
 *
 * The ONNX model must be a YOLOv8 OBB model. Expected I/O:
 * - Input: `[1, 3, 640, 640]` BCHW float32 (0-1 normalized)
 * - Output: `[1, 300, 7]` — 300 detection slots × [cx, cy, w, h, conf, class_id, angle_rad]
 *     - cx, cy: center coordinates in absolute model-space pixels (0-640)
 *     - w, h: width and height in absolute model-space pixels
 *     - conf: confidence score (0-1 sigmoid)
 *     - class_id: highest-scoring DOTAv1 class index (0-15)
 *     - angle_rad: rotation angle in **radians**, counterclockwise positive
 *
 * ## Coordinate Mapping
 *
 * The model operates on a letterbox-preprocessed 640×640 canvas. The coordinate mapping is:
 * ```
 * model_cx = cx_model * (scale_x)
 * model_cy = cy_model * (scale_y)
 * scale = 640.0 / max(imageWidth, imageHeight)
 * ```
 *
 * If the model fails to load or inference errors, [detectRectangles] returns an empty list and
 * prints a diagnostic message. This allows the application to degrade gracefully.
 *
 * @param modelPath Resource path to the ONNX model file (e.g.
 *   "ml_models/yolo26n-pose-onnx/yolo26n-obb.onnx")
 */
class OrientedBoundingBoxDetector(modelPath: String) {

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
            "OBB model resource not found or empty at path: $modelPath. " +
                "Photo corner detection will be disabled.")
        modelAvailable = false
        return
      }
      session = ortEnv.createSession(modelBytes)
      modelAvailable = true
    } catch (e: Exception) {
      System.err.println(
          "Failed to load OBB model at path '$modelPath': ${e.message}. " +
              "Photo corner detection will be disabled.")
      modelAvailable = false
    }
  }

  /**
   * Detects rectangular photo regions in the given scanned image.
   *
   * Runs the OBB model and converts each oriented detection into a [List] of 4 [PhotoCorner]
   * coordinates. Detections with confidence below [minConfidence] are discarded.
   *
   * @param image The scanned image containing photos on a solid background
   * @param minConfidence Minimum confidence threshold (default 0.15). Lower values detect more but
   *   may include false positives.
   * @return A list of detected corner sets; each set has 4 corners (top-left, top-right,
   *   bottom-right, bottom-left). Returns an empty list if the model is unavailable or inference
   *   fails.
   */
  fun detectRectangles(
      image: BufferedImage,
      minConfidence: Float = 0.15f
  ): List<List<PhotoCorner>> {
    val activeSession = session
    if (!modelAvailable || activeSession == null) {
      return emptyList()
    }

    return try {
      val blob = preprocess(image)
      val inputs = mapOf(activeSession.inputNames.first() to blob)
      val results = activeSession.run(inputs)
      val output = results[0].value as Array<Array<FloatArray>>
      blob.close()

      parseOutput(output, image.width, image.height, minConfidence)
    } catch (e: Exception) {
      System.err.println("OBB inference failed: ${e.message}")
      emptyList()
    }
  }

  /**
   * Preprocesses the image into a 640×640 letterbox blob for model input.
   *
   * Uses square padding (letterbox) to preserve aspect ratio. Coordinates are scaled by the same
   * factor so they can be mapped back to the original image space.
   *
   * @return ONNX tensor of shape [1, 3, 640, 640] (BCHW float32, 0-1 normalized)
   */
  private fun preprocess(image: BufferedImage): OnnxTensor {
    // Letterbox resize: scale by min(640/width, 640/height) and pad the remainder
    val scale = 640.0 / max(image.width, image.height)
    val newW = (image.width * scale).toInt()
    val newH = (image.height * scale).toInt()

    val resized = image.getScaledInstance(newW, newH, BufferedImage.SCALE_DEFAULT)
    val canvas = BufferedImage(640, 640, BufferedImage.TYPE_3BYTE_BGR)
    canvas.graphics.drawImage(resized, 0, 0, null)

    // Create CHW float32 blob (BGR order to match YOLO convention)
    val floatBuffer = FloatBuffer.allocate(3 * 640 * 640)
    for (y in 0 until 640) {
      for (x in 0 until 640) {
        val pixel = canvas.getRGB(x, y)
        // BGR order (YOLO expects BGR)
        floatBuffer.put(((pixel shr 16) and 0xFF) / 255.0f) // R → B
        floatBuffer.put(((pixel shr 8) and 0xFF) / 255.0f) // G → G
        floatBuffer.put((pixel and 0xFF) / 255.0f) // B → R
      }
    }
    floatBuffer.rewind()

    return OnnxTensor.createTensor(ortEnv, floatBuffer, longArrayOf(1, 3, 640, 640))
  }

  /**
   * Sigmoid function: converts raw logits to probabilities in (0, 1).
   *
   * The YOLO model outputs confidence as a raw logit (pre-sigmoid value). Large positive values
   * (e.g. 632) saturate to 1.0, large negative values saturate to 0.0.
   */
  private fun sigmoid(x: Float): Float {
    return if (x > 20f) 1.0f
    else if (x < -20f) 0.0f else (1.0f / (1.0f + kotlin.math.exp(-x.toDouble()))).toFloat()
  }

  /**
   * Parses the raw model output into corner coordinates.
   *
   * The raw output is `[batch=1, 300, 7]` where each detection slot contains: `[cx_model, cy_model,
   * w_model, h_model, conf_logit, class_id, angle_rad]`
   *
   * Coordinates are in absolute model-space pixels (0-640), not normalized. Confidence is a **raw
   * logit** — apply [sigmoid] to get probability. Class is an integer class ID in the DOTAv1
   * taxonomy. Angle is in **radians**, counterclockwise positive.
   *
   * This function:
   * 1. Filters by sigmoid(confidence) threshold
   * 2. Scales coordinates from model space → original image space
   * 3. Converts (cx, cy, w, h, angle) → 4 corner points
   * 4. Applies non-maximum suppression
   *
   * @param output Raw model output tensor
   * @param origW Original image width in pixels
   * @param origH Original image height in pixels
   * @param minConfidence Minimum confidence threshold (sigmoid of raw logit)
   * @return List of detected corner sets (4 corners each)
   */
  private fun parseOutput(
      output: Array<Array<FloatArray>>,
      origW: Int,
      origH: Int,
      minConfidence: Float
  ): List<List<PhotoCorner>> {
    val detections = mutableListOf<OdbDetection>()
    val numDets = output[0][0].size
    val scale = 640.0 / max(origW, origH)

    for (i in 0 until numDets) {
      val conf = sigmoid(output[0][4][i])
      if (conf < minConfidence) continue

      val cxModel = output[0][0][i] // absolute model-space x
      val cyModel = output[0][1][i] // absolute model-space y
      val wModel = output[0][2][i] // absolute model-space width
      val hModel = output[0][3][i] // absolute model-space height
      val angleRad = output[0][6][i].toDouble() // radians, CCW positive

      // Scale from model space to original image space
      val cxOrig = cxModel / scale
      val cyOrig = cyModel / scale
      val wOrig = wModel / scale
      val hOrig = hModel / scale

      // Convert oriented bbox to 4 corner points
      // Center (cx, cy), half-width (hw), half-height (hh), angle (radians)
      val hw = wOrig / 2.0
      val hh = hOrig / 2.0
      val cosA = cos(angleRad)
      val sinA = sin(angleRad)

      // 4 corners relative to center: TL(-hw,-hh), TR(+hw,-hh), BR(+hw,+hh), BL(-hw,+hh)
      // Rotation: x' = cx + dx*cos - dy*sin, y' = cy + dx*sin + dy*cos
      val corners =
          listOf(
              doubleArrayOf(
                  cxOrig + (-hw) * cosA - (-hh) * sinA, cyOrig + (-hw) * sinA + (-hh) * cosA), // TL
              doubleArrayOf(
                  cxOrig + (hw) * cosA - (-hh) * sinA, cyOrig + (hw) * sinA + (-hh) * cosA), // TR
              doubleArrayOf(
                  cxOrig + (hw) * cosA - (hh) * sinA, cyOrig + (hw) * sinA + (hh) * cosA), // BR
              doubleArrayOf(
                  cxOrig + (-hw) * cosA - (hh) * sinA, cyOrig + (-hw) * sinA + (hh) * cosA) // BL
              )

      // Clip to image bounds
      val clipped =
          corners.map { c ->
            PhotoCorner(
                x = c[0].toFloat().coerceIn(0f, origW.toFloat()),
                y = c[1].toFloat().coerceIn(0f, origH.toFloat()))
          }

      detections.add(OdbDetection(clipped, conf, wOrig.toFloat(), hOrig.toFloat()))
    }

    // Non-maximum suppression by IoU
    return nms(detections)
  }

  /** Non-maximum suppression — keeps the highest-confidence detection per overlapping group. */
  private fun nms(detections: List<OdbDetection>): List<List<PhotoCorner>> {
    if (detections.isEmpty()) return emptyList()

    val sorted = detections.sortedByDescending { it.confidence }
    val suppressed = BooleanArray(sorted.size)
    val result = mutableListOf<List<PhotoCorner>>()

    for (i in sorted.indices) {
      if (suppressed[i]) continue
      result.add(sorted[i].corners)
      for (j in i + 1 until sorted.size) {
        if (!suppressed[j] && iou(sorted[i], sorted[j]) > 0.4f) {
          suppressed[j] = true
        }
      }
    }
    return result
  }

  /** IoU between two detections using axis-aligned bounding boxes. */
  private fun iou(a: OdbDetection, b: OdbDetection): Float {
    val ax1 = a.corners.minOf { it.x }
    val ay1 = a.corners.minOf { it.y }
    val ax2 = a.corners.maxOf { it.x }
    val ay2 = a.corners.maxOf { it.y }
    val bx1 = b.corners.minOf { it.x }
    val by1 = b.corners.minOf { it.y }
    val bx2 = b.corners.maxOf { it.x }
    val by2 = b.corners.maxOf { it.y }
    val interX1 = max(ax1, bx1)
    val interY1 = max(ay1, by1)
    val interX2 = min(ax2, bx2)
    val interY2 = min(ay2, by2)
    val interArea = max(0f, interX2 - interX1) * max(0f, interY2 - interY1)
    val areaA = (ax2 - ax1) * (ay2 - ay1)
    val areaB = (bx2 - bx1) * (by2 - by1)
    val union = areaA + areaB - interArea
    return if (union > 0) interArea / union else 0f
  }

  private data class OdbDetection(
      val corners: List<PhotoCorner>,
      val confidence: Float,
      val width: Float,
      val height: Float
  )
}
