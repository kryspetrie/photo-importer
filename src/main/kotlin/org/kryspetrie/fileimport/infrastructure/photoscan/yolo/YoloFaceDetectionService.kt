package org.kryspetrie.fileimport.infrastructure.photoscan.yolo

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.awt.image.BufferedImage
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min

/**
 * YOLO face detection model inference — detects face bounding boxes in images.
 *
 * Uses the same letterbox preprocessing pipeline as [YoloDetectionService]. The model outputs a
 * NMS-filtered `[1, 300, 6]` tensor where each detection is `(x1, y1, x2, y2, conf, cls)` in
 * 640-space letterboxed coordinates. Low-confidence and zero-area detections are filtered and
 * coordinates are mapped back to the original image pixel space.
 *
 * @param env ONNX Runtime environment (shared singleton)
 * @param session ONNX Runtime session for the face detection model
 */
class YoloFaceDetectionService(private val env: OrtEnvironment, private val session: OrtSession) {

    /** Result of a single face detection with bounding box and confidence. */
    data class FaceDetection(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        val confidence: Float,
    ) {
        /** Center X of the bounding box. */
        val centerX: Float get() = (x1 + x2) / 2f
        /** Center Y of the bounding box. */
        val centerY: Float get() = (y1 + y2) / 2f
        /** Width of the bounding box. */
        val width: Float get() = x2 - x1
        /** Height of the bounding box. */
        val height: Float get() = y2 - y1
    }

    companion object {
        const val DEFAULT_IMG_SIZE = 640

        /** Output feature count: x1, y1, x2, y2, confidence, class. */
        private const val OUTPUT_FEATURES = 6
    }

    /**
     * Detect faces in an image.
     *
     * Uses letterbox preprocessing (matching [YoloDetectionService] exactly),
     * then runs ONNX inference and parses the bounding-box output.
     *
     * @param image Source image (any size)
     * @param confThreshold Minimum detection confidence (default 0.5)
     * @param iouThreshold NMS IoU threshold (default 0.45)
     * @param imgSize Model input size (default 640)
     * @return List of face detections with bounding boxes in original image coordinates,
     *   sorted by descending confidence
     */
    fun detectFaces(
        image: BufferedImage,
        confThreshold: Float = 0.5f,
        iouThreshold: Float = 0.45f,
        imgSize: Int = DEFAULT_IMG_SIZE,
    ): List<FaceDetection> {
        val origW = image.width
        val origH = image.height

        // Step 1: Letterbox preprocess (same as YoloDetectionService)
        val preprocessed = YoloPreprocessing.preprocessLetterbox(image, imgSize)

        // Step 2: Run inference
        val inputName = session.inputNames.iterator().next()
        val inputTensor = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(preprocessed.flatArray),
            preprocessed.shape,
        )
        val results = session.run(mapOf(inputName to inputTensor))
        val output = results[0].value as Array<Array<FloatArray>>

        // Step 3: Parse output
        val ratio = preprocessed.ratio
        val padW = preprocessed.padW
        val padH = preprocessed.padH

        return parseFaceOutput(
            output = output,
            ratio = ratio,
            padW = padW,
            padH = padH,
            origW = origW,
            origH = origH,
            confThreshold = confThreshold,
            iouThreshold = iouThreshold,
        )
    }

    /**
     * Parse face detection model output.
     *
     * Handles two formats:
     * - NMS-enabled (default): `[1, N, 6]` — x1, y1, x2, y2, conf, cls
     * - Transposed: `[1, 6, N]` — cx, cy, w, h, conf, cls (center-format bbox)
     *
     * Detections with confidence below [confThreshold] or zero-area bounding boxes are filtered.
     * Coordinates are mapped from letterboxed 640-space back to original image pixels.
     * A second pass of NMS is applied as a safety net in case the model's internal NMS
     * left overlapping detections.
     */
    internal fun parseFaceOutput(
        output: Array<Array<FloatArray>>,
        ratio: Float,
        padW: Int,
        padH: Int,
        origW: Int,
        origH: Int,
        confThreshold: Float,
        iouThreshold: Float,
    ): List<FaceDetection> {
        if (output.isEmpty() || output[0].isEmpty()) return emptyList()

        val featureCount = output[0][0].size
        val detectionCount = output[0].size

        val detections = mutableListOf<FaceDetection>()

        if (featureCount == OUTPUT_FEATURES) {
            // NMS-enabled format: [1, N, 6] — rows are detections
            for (i in 0 until detectionCount) {
                val row = output[0][i]
                val conf = row[4]
                if (conf < confThreshold) continue

                val x1 = ((row[0] - padW) / ratio).coerceIn(0f, origW.toFloat())
                val y1 = ((row[1] - padH) / ratio).coerceIn(0f, origH.toFloat())
                val x2 = ((row[2] - padW) / ratio).coerceIn(0f, origW.toFloat())
                val y2 = ((row[3] - padH) / ratio).coerceIn(0f, origH.toFloat())

                // Skip zero-area detections (model pads to 300 with zeros)
                if (x2 - x1 < 1f && y2 - y1 < 1f) continue

                detections.add(FaceDetection(
                    x1 = x1, y1 = y1, x2 = x2, y2 = y2,
                    confidence = conf,
                ))
            }
        } else {
            // Transposed format: [1, 6, N] — columns are detections, center-format bbox
            for (i in 0 until featureCount) {
                val cx = output[0][0][i]
                val cy = output[0][1][i]
                val w = output[0][2][i]
                val h = output[0][3][i]
                val conf = output[0][4][i]
                if (conf < confThreshold) continue

                val x1 = ((cx - w / 2 - padW) / ratio).coerceIn(0f, origW.toFloat())
                val y1 = ((cy - h / 2 - padH) / ratio).coerceIn(0f, origH.toFloat())
                val x2 = ((cx + w / 2 - padW) / ratio).coerceIn(0f, origW.toFloat())
                val y2 = ((cy + h / 2 - padH) / ratio).coerceIn(0f, origH.toFloat())

                if (x2 - x1 < 1f && y2 - y1 < 1f) continue

                detections.add(FaceDetection(
                    x1 = x1, y1 = y1, x2 = x2, y2 = y2,
                    confidence = conf,
                ))
            }
        }

        if (detections.isEmpty()) return emptyList()
        return applyNMS(detections, iouThreshold)
    }

    /** Apply Non-Maximum Suppression to filter overlapping detections. */
    private fun applyNMS(
        detections: List<FaceDetection>,
        iouThreshold: Float,
    ): List<FaceDetection> {
        val sorted = detections.sortedByDescending { it.confidence }
        val suppressed = BooleanArray(sorted.size) { false }
        val kept = mutableListOf<FaceDetection>()

        for (i in sorted.indices) {
            if (suppressed[i]) continue
            kept.add(sorted[i])

            for (j in i + 1 until sorted.size) {
                if (suppressed[j]) continue
                if (computeIoU(sorted[i], sorted[j]) > iouThreshold) {
                    suppressed[j] = true
                }
            }
        }
        return kept
    }

    /** Compute IoU between two face detections. */
    private fun computeIoU(a: FaceDetection, b: FaceDetection): Float {
        val interX1 = max(a.x1, b.x1)
        val interY1 = max(a.y1, b.y1)
        val interX2 = min(a.x2, b.x2)
        val interY2 = min(a.y2, b.y2)
        val interArea = max(0f, interX2 - interX1) * max(0f, interY2 - interY1)
        val areaA = (a.x2 - a.x1) * (a.y2 - a.y1)
        val areaB = (b.x2 - b.x1) * (b.y2 - b.y1)
        val unionArea = areaA + areaB - interArea
        return if (unionArea > 0) interArea / unionArea else 0f
    }
}