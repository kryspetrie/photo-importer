package org.kryspetrie.fileimport.infrastructure.photoscan.yolo

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.awt.image.BufferedImage
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min

/**
 * YOLO detection model inference — finds photo bounding boxes in scanned images.
 *
 * Matches the Python `run_detection()` function exactly:
 * 1. Letterbox resize the input image to 640×640 (padding with 114,114,114)
 * 2. Normalize to [0,1] float, transpose to NCHW
 * 3. Run inference on the detection model
 * 4. Parse output (handles both legacy [1,5,N] and NMS-enabled [1,N,6] formats)
 * 5. Map coordinates back from letterboxed space to original image space
 * 6. Apply NMS (for legacy format) or re-apply NMS (for NMS-enabled format)
 * 7. Return list of detection boxes with confidence scores
 *
 * @param env ONNX Runtime environment (shared)
 * @param session ONNX Runtime session for the detection model
 */
class YoloDetectionService(private val env: OrtEnvironment, private val session: OrtSession) {
    /** Result of a single detection. */
    data class Detection(
        /** Bounding box in original image pixel coordinates. */
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        /** Detection confidence (0.0-1.0). */
        val confidence: Float,
    )

    /**
     * Run detection on a full image.
     *
     * @param image Source image (any size)
     * @param confThreshold Minimum detection confidence (default 0.5)
     * @param iouThreshold NMS IoU threshold (default 0.45)
     * @param imgSize Model input size (default 640)
     * @return List of detections with bounding boxes in original image coordinates
     */
    fun detect(
        image: BufferedImage,
        confThreshold: Float = 0.5f,
        iouThreshold: Float = 0.45f,
        imgSize: Int = DEFAULT_IMG_SIZE,
    ): List<Detection> {
        val origW = image.width
        val origH = image.height

        // Step 1: Letterbox preprocess
        val preprocessed = preprocessLetterbox(image, imgSize)

        // Step 2: Run inference
        val inputName = session.inputNames.iterator().next()
        val inputTensor =
            OnnxTensor.createTensor(
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

        return parseDetectionOutput(
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
     * Letterbox resize: preserves aspect ratio, pads with (114,114,114).
     *
     * Uses manual bilinear interpolation that exactly matches Python's PIL Image.BILINEAR, avoiding
     * the per-pixel differences introduced by Java's Graphics2D.drawImage().
     */
    private fun preprocessLetterbox(
        image: BufferedImage,
        targetSize: Int = DEFAULT_IMG_SIZE,
    ): YoloPreprocessing.PreprocessResult {
        return YoloPreprocessing.preprocessLetterbox(image, targetSize)
    }

    /**
     * Parse detection model output into a list of detections.
     *
     * Handles two formats:
     * - Legacy: [1, 5, N] — raw YOLO (cx, cy, w, h, conf), needs NMS
     * - NMS-enabled: [1, N, 6] — (x1, y1, x2, y2, conf, cls), NMS pre-applied
     */
    private fun parseDetectionOutput(
        output: Array<Array<FloatArray>>,
        ratio: Float,
        padW: Int,
        padH: Int,
        origW: Int,
        origH: Int,
        confThreshold: Float,
        iouThreshold: Float,
    ): List<Detection> {
        if (output.isEmpty() || output[0].isEmpty()) return emptyList()

        val detections = mutableListOf<Detection>()

        // Detect output format from shape
        // Legacy: [1, 5, N] — transposed, 5 rows for cx,cy,w,h,conf
        // NMS-enabled: [1, N, 6] — row-oriented, 6 cols for x1,y1,x2,y2,conf,cls
        val shape1 = output.size
        val shape2 = output[0].size
        val shape3 = if (output[0].isNotEmpty()) output[0][0].size else 0

        if (shape2 == 5) {
            // Legacy format: [1, 5, N_anchors] — cx, cy, w, h, conf
            val nAnchors = shape3
            val confs = FloatArray(nAnchors)
            val boxes = FloatArray(nAnchors * 4) // x1, y1, x2, y2

            for (i in 0 until nAnchors) {
                val cx = output[0][0][i]
                val cy = output[0][1][i]
                val w = output[0][2][i]
                val h = output[0][3][i]
                val conf = output[0][4][i]
                confs[i] = conf

                // Convert from letterboxed 640-space to original image coordinates
                val x1 = ((cx - w / 2 - padW) / ratio).coerceIn(0f, origW.toFloat())
                val y1 = ((cy - h / 2 - padH) / ratio).coerceIn(0f, origH.toFloat())
                val x2 = ((cx + w / 2 - padW) / ratio).coerceIn(0f, origW.toFloat())
                val y2 = ((cy + h / 2 - padH) / ratio).coerceIn(0f, origH.toFloat())
                boxes[i * 4] = x1
                boxes[i * 4 + 1] = y1
                boxes[i * 4 + 2] = x2
                boxes[i * 4 + 3] = y2
            }

            // Filter by confidence
            val validIndices = mutableListOf<Int>()
            for (i in 0 until nAnchors) {
                if (confs[i] > confThreshold) validIndices.add(i)
            }

            if (validIndices.isEmpty()) return emptyList()

            // NMS
            val keep = nms(boxes, confs, validIndices, iouThreshold)

            for (idx in keep) {
                detections.add(
                    Detection(
                        x1 = max(0f, boxes[idx * 4]),
                        y1 = max(0f, boxes[idx * 4 + 1]),
                        x2 = min(origW.toFloat(), boxes[idx * 4 + 2]),
                        y2 = min(origH.toFloat(), boxes[idx * 4 + 3]),
                        confidence = confs[idx],
                    )
                )
            }
        } else {
            // NMS-enabled format: [1, N, 6] — x1, y1, x2, y2, conf, cls
            val nDetections = shape2
            val cols = shape3

            for (i in 0 until nDetections) {
                val conf = output[0][i][4]
                if (conf < confThreshold) continue

                // Convert from letterboxed 640-space to original image coordinates
                val x1 = ((output[0][i][0] - padW) / ratio).coerceIn(0f, origW.toFloat())
                val y1 = ((output[0][i][1] - padH) / ratio).coerceIn(0f, origH.toFloat())
                val x2 = ((output[0][i][2] - padW) / ratio).coerceIn(0f, origW.toFloat())
                val y2 = ((output[0][i][3] - padH) / ratio).coerceIn(0f, origH.toFloat())

                detections.add(Detection(x1 = x1, y1 = y1, x2 = x2, y2 = y2, confidence = conf))
            }

            // Re-apply NMS for safety (some models may still produce overlapping detections)
            detections.clear()
            val allBoxes = FloatArray(nDetections * 4)
            val allConfs = FloatArray(nDetections)
            val validIndices = mutableListOf<Int>()

            for (i in 0 until nDetections) {
                val conf = output[0][i][4]
                if (conf < confThreshold) continue
                allConfs[validIndices.size] = conf
                allBoxes[validIndices.size * 4] = (output[0][i][0] - padW) / ratio
                allBoxes[validIndices.size * 4 + 1] = (output[0][i][1] - padH) / ratio
                allBoxes[validIndices.size * 4 + 2] = (output[0][i][2] - padW) / ratio
                allBoxes[validIndices.size * 4 + 3] = (output[0][i][3] - padH) / ratio
                validIndices.add(i)
            }

            if (validIndices.isEmpty()) return emptyList()

            val keep = nms(allBoxes, allConfs, (0 until validIndices.size).toList(), iouThreshold)
            for (idx in keep) {
                val origIdx = validIndices[idx]
                detections.add(
                    Detection(
                        x1 = max(0f, allBoxes[idx * 4]),
                        y1 = max(0f, allBoxes[idx * 4 + 1]),
                        x2 = min(origW.toFloat(), allBoxes[idx * 4 + 2]),
                        y2 = min(origH.toFloat(), allBoxes[idx * 4 + 3]),
                        confidence = allConfs[idx],
                    )
                )
            }
        }

        return detections
    }

    /** Non-maximum suppression on xyxy-format boxes. */
    private fun nms(
        boxes: FloatArray,
        scores: FloatArray,
        indices: List<Int>,
        iouThreshold: Float,
    ): List<Int> {
        if (indices.isEmpty()) return emptyList()

        // Sort by confidence descending
        val sorted = indices.sortedByDescending { scores[it] }
        val suppressed = BooleanArray(boxes.size / 4) { false }
        val kept = mutableListOf<Int>()

        for (i in sorted.indices) {
            val idx = sorted[i]
            if (suppressed[idx]) continue
            kept.add(idx)

            val ix1 = idx * 4
            for (j in i + 1 until sorted.size) {
                val jdx = sorted[j]
                if (suppressed[jdx]) continue
                if (computeIoU(boxes, ix1, jdx * 4) > iouThreshold) {
                    suppressed[jdx] = true
                }
            }
        }
        return kept
    }

    /** Compute IoU between two boxes in xyxy format. */
    private fun computeIoU(boxes: FloatArray, a: Int, b: Int): Float {
        val ax1 = boxes[a]
        val ay1 = boxes[a + 1]
        val ax2 = boxes[a + 2]
        val ay2 = boxes[a + 3]
        val bx1 = boxes[b]
        val by1 = boxes[b + 1]
        val bx2 = boxes[b + 2]
        val by2 = boxes[b + 3]

        val interX1 = max(ax1, bx1)
        val interY1 = max(ay1, by1)
        val interX2 = min(ax2, bx2)
        val interY2 = min(ay2, by2)
        val interArea = max(0f, interX2 - interX1) * max(0f, interY2 - interY1)
        val areaA = (ax2 - ax1) * (ay2 - ay1)
        val areaB = (bx2 - bx1) * (by2 - by1)
        val unionArea = areaA + areaB - interArea
        return if (unionArea > 0) interArea / unionArea else 0f
    }

    companion object {
        const val DEFAULT_IMG_SIZE = 640
    }
}
