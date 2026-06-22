package org.kryspetrie.fileimport.infrastructure.photoscan.yolo

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.awt.image.BufferedImage
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min

/**
 * YOLOv8-face model inference — detects face bounding boxes and 5 facial keypoints in images.
 *
 * Uses the same letterbox preprocessing pipeline as [YoloDetectionService]. Output parsing handles:
 * - NMS-enabled format: [1, N, 20] — (x1, y1, x2, y2, conf, cls, 5×3 keypoint values)
 * - Legacy transposed format: [1, 20, N] — same values, transposed
 *
 * Each detected face includes:
 * - Bounding box (x1, y1, x2, y2) in original image pixel coordinates
 * - Confidence score (0.0-1.0)
 * - 5 facial keypoints (left eye, right eye, nose, left mouth, right mouth)
 *
 * @param env ONNX Runtime environment (shared singleton)
 * @param session ONNX Runtime session for the face detection model
 */
class YoloFaceDetectionService(private val env: OrtEnvironment, private val session: OrtSession) {

    /** A single facial keypoint detected by the face model. */
    data class FaceKeypoint(
        val x: Float,
        val y: Float,
        val visibility: Float,
    )

    /** Result of a single face detection with bounding box and keypoints. */
    data class FaceDetection(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        val confidence: Float,
        val keypoints: List<FaceKeypoint> = emptyList(),
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
        const val NUM_KEYPOINTS = 5
        const val VALUES_PER_KEYPOINT = 3 // x, y, visibility
        const val FACE_OUTPUT_FEATURES = 20 // 4 bbox + 1 conf + 1 cls + 5*3 keypoints
    }

    /**
     * Detect faces in an image.
     *
     * Uses letterbox preprocessing (matching [YoloDetectionService] exactly),
     * then runs ONNX inference and parses the bounding box + keypoint output.
     *
     * @param image Source image (any size)
     * @param confThreshold Minimum detection confidence (default 0.5)
     * @param iouThreshold NMS IoU threshold (default 0.45)
     * @param imgSize Model input size (default 640)
     * @return List of face detections with bounding boxes in original image coordinates
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
     * - Transposed: [1, FACE_OUTPUT_FEATURES, N] — cx, cy, w, h, conf, cls, kps...
     * - NMS-enabled: [1, N, FACE_OUTPUT_FEATURES] — x1, y1, x2, y2, conf, cls, kps...
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

        // Detect output format from shape
        // Transposed: [1, 20, N] — shape2 == 20 (FACE_OUTPUT_FEATURES)
        // NMS-enabled: [1, N, 20] — shape3 == 20 (FACE_OUTPUT_FEATURES)
        val outputSize = output[0].size
        
        val detections = mutableListOf<FaceDetection>()

        if (outputSize == FACE_OUTPUT_FEATURES) {
            // Transposed format: [1, 20, N] — cx,cy,w,h,conf,cls,kp0x,kp0y,kp0v,...
            val nAnchors = if (output[0].isNotEmpty()) output[0][0].size else 0
            for (i in 0 until nAnchors) {
                val conf = output[0][4][i]
                if (conf < confThreshold) continue

                // Extract bbox in letterboxed 640-space (center format)
                val cx = output[0][0][i]
                val cy = output[0][1][i]
                val w = output[0][2][i]
                val h = output[0][3][i]

                // Convert to xyxy in original image coordinates
                val x1 = ((cx - w / 2 - padW) / ratio).coerceIn(0f, origW.toFloat())
                val y1 = ((cy - h / 2 - padH) / ratio).coerceIn(0f, origH.toFloat())
                val x2 = ((cx + w / 2 - padW) / ratio).coerceIn(0f, origW.toFloat())
                val y2 = ((cy + h / 2 - padH) / ratio).coerceIn(0f, origH.toFloat())

                // Extract keypoints (indices 6-19: kp0x,kp0y,kp0v, kp1x,kp1y,kp1v, ...)
                val keypoints = extractKeypoints(output, i, ratio, padW, padH, origW, origH)

                detections.add(FaceDetection(
                    x1 = x1, y1 = y1, x2 = x2, y2 = y2,
                    confidence = conf,
                    keypoints = keypoints,
                ))
            }
        } else {
            // NMS-enabled format: [1, N, 20] — x1,y1,x2,y2,conf,cls,kp0x,kp0y,kp0v,...
            val nDetections = outputSize
            for (i in 0 until nDetections) {
                val conf = output[0][i][4]
                if (conf < confThreshold) continue

                // Bbox already in xyxy format in letterboxed 640-space
                val x1 = ((output[0][i][0] - padW) / ratio).coerceIn(0f, origW.toFloat())
                val y1 = ((output[0][i][1] - padH) / ratio).coerceIn(0f, origH.toFloat())
                val x2 = ((output[0][i][2] - padW) / ratio).coerceIn(0f, origW.toFloat())
                val y2 = ((output[0][i][3] - padH) / ratio).coerceIn(0f, origH.toFloat())

                // Extract keypoints (indices 6-19 in NMS format)
                val keypoints = extractKeypointsNMS(output, i, ratio, padW, padH, origW, origH)

                detections.add(FaceDetection(
                    x1 = x1, y1 = y1, x2 = x2, y2 = y2,
                    confidence = conf,
                    keypoints = keypoints,
                ))
            }
        }

        // Apply NMS
        if (detections.isEmpty()) return emptyList()
        return applyNMS(detections, iouThreshold)
    }

    /**
     * Extract keypoints from transposed format output.
     * Keypoints start at index 6 in [1, 20, N]: kp0x,kp0y,kp0v, kp1x,kp1y,kp1v, ...
     */
    private fun extractKeypoints(
        output: Array<Array<FloatArray>>,
        anchorIdx: Int,
        ratio: Float,
        padW: Int,
        padH: Int,
        origW: Int,
        origH: Int,
    ): List<FaceKeypoint> {
        val keypoints = mutableListOf<FaceKeypoint>()
        for (kp in 0 until NUM_KEYPOINTS) {
            val baseIdx = 6 + kp * VALUES_PER_KEYPOINT  // 6,9,12,15,18
            if (baseIdx + 2 < output[0].size) {
                val kpx = output[0][baseIdx][anchorIdx]
                val kpy = output[0][baseIdx + 1][anchorIdx]
                val kpv = output[0][baseIdx + 2][anchorIdx]
                // Convert from letterboxed space to original image coordinates
                val origX = ((kpx - padW) / ratio).coerceIn(0f, origW.toFloat())
                val origY = ((kpy - padH) / ratio).coerceIn(0f, origH.toFloat())
                keypoints.add(FaceKeypoint(x = origX, y = origY, visibility = kpv))
            }
        }
        return keypoints
    }

    /**
     * Extract keypoints from NMS-enabled format output.
     * Keypoints start at index 6 in [1, N, 20]: kp0x,kp0y,kp0v, kp1x,kp1y,kp1v, ...
     */
    private fun extractKeypointsNMS(
        output: Array<Array<FloatArray>>,
        detIdx: Int,
        ratio: Float,
        padW: Int,
        padH: Int,
        origW: Int,
        origH: Int,
    ): List<FaceKeypoint> {
        val keypoints = mutableListOf<FaceKeypoint>()
        for (kp in 0 until NUM_KEYPOINTS) {
            val baseIdx = 6 + kp * VALUES_PER_KEYPOINT
            if (baseIdx + 2 < output[0][detIdx].size) {
                val kpx = output[0][detIdx][baseIdx]
                val kpy = output[0][detIdx][baseIdx + 1]
                val kpv = output[0][detIdx][baseIdx + 2]
                val origX = ((kpx - padW) / ratio).coerceIn(0f, origW.toFloat())
                val origY = ((kpy - padH) / ratio).coerceIn(0f, origH.toFloat())
                keypoints.add(FaceKeypoint(x = origX, y = origY, visibility = kpv))
            }
        }
        return keypoints
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