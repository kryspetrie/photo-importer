package org.kryspetrie.fileimport.infrastructure.photoscan.yolo

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.awt.image.BufferedImage
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * YOLO pose model inference — finds 4-corner keypoints (LL/UL/UR/LR) for detected photos.
 *
 * Matches the Python `run_pose()` and `_run_pose_on_crop()` functions exactly:
 * 1. Crop the image around the detection box with expansion
 * 2. Resize to 640×640 (stretch, no letterboxing — single-photo distribution)
 * 3. Normalize to [0,1] float, transpose to NCHW
 * 4. Run inference on the pose model
 * 5. Parse output: [1, 300, 18] → x1,y1,x2,y2,conf,cls,kp0_x,kp0_y,kp0_vis,...
 * 6. Scale keypoints back from 640-space to crop-pixel space, then to original image space
 *
 * @param env ONNX Runtime environment (shared)
 * @param session ONNX Runtime session for the pose model
 */
class YoloPoseService(private val env: OrtEnvironment, private val session: OrtSession) {
    /** A single keypoint detected by the pose model. */
    data class Keypoint(val name: String, val x: Float, val y: Float, val visibility: Float)

    /** Result of pose detection on a single crop, mapped to original image coordinates. */
    data class PoseResult(
        val confidence: Float,
        val keypoints: List<Keypoint>,
        val centerX: Float,
        val centerY: Float,
        val dedupPriority: Float,
        val cropBox: CropBox,
        val detection: YoloDetectionService.Detection? = null,
    )

    data class CropBox(val x1: Int, val y1: Int, val x2: Int, val y2: Int)

    data class CropLimits(val left: Int, val right: Int, val up: Int, val down: Int)

    /**
     * Run pose on a single detection box crop.
     *
     * Crops the image around the detection box with the specified expansion ratio, runs the pose
     * model, and maps keypoints back to original image coordinates.
     *
     * @param image Full source image
     * @param box Detection bounding box in original image coords
     * @param confThreshold Minimum pose confidence (default 0.5)
     * @param imgSize Model input size (default 640)
     * @param expandRatio Expansion ratio for the crop (default 0.15 = 15% of larger dim)
     * @param cropLimits Optional per-side expansion limits to avoid pulling in adjacent photos
     * @return PoseResult, or null if no pose detection above threshold
     */
    fun runPoseOnCrop(
        image: BufferedImage,
        box: YoloDetectionService.Detection,
        confThreshold: Float = 0.5f,
        imgSize: Int = DEFAULT_IMG_SIZE,
        expandRatio: Float = POSE_CROP_EXPAND,
        cropLimits: CropLimits? = null,
    ): PoseResult? {
        val origW = image.width
        val origH = image.height

        val largerDim = maxOf(box.x2 - box.x1, box.y2 - box.y1)
        val expandPx = (largerDim * expandRatio).toInt()

        var expandLeft = expandPx
        var expandRight = expandPx
        var expandUp = expandPx
        var expandDown = expandPx

        if (cropLimits != null) {
            expandLeft = min(expandPx, cropLimits.left)
            expandRight = min(expandPx, cropLimits.right)
            expandUp = min(expandPx, cropLimits.up)
            expandDown = min(expandPx, cropLimits.down)
        }

        val cropX1 = max(0, box.x1.toInt() - expandLeft)
        val cropY1 = max(0, box.y1.toInt() - expandUp)
        val cropX2 = min(origW, box.x2.toInt() + expandRight)
        val cropY2 = min(origH, box.y2.toInt() + expandDown)

        val cropW = cropX2 - cropX1
        val cropH = cropY2 - cropY1
        if (cropW <= 0 || cropH <= 0) return null

        val crop = image.getSubimage(cropX1, cropY1, cropW, cropH)
        val poseDets = runPose(crop, confThreshold, imgSize)
        if (poseDets.isEmpty()) return null

        val bestPose = poseDets.maxByOrNull { it.confidence } ?: return null

        val mappedKeypoints =
            bestPose.keypoints.map { kp -> kp.copy(x = kp.x + cropX1, y = kp.y + cropY1) }

        val visibleKps = mappedKeypoints.filter { it.visibility >= VIS_THRESH_DEDUP }
        val centerX =
            if (visibleKps.isNotEmpty()) visibleKps.map { it.x }.average().toFloat() else 0f
        val centerY =
            if (visibleKps.isNotEmpty()) visibleKps.map { it.y }.average().toFloat() else 0f
        val visCount = visibleKps.size
        var dedupPriority = bestPose.confidence
        if (visCount < 3) dedupPriority *= 0.5f

        return PoseResult(
            confidence = bestPose.confidence,
            keypoints = mappedKeypoints,
            centerX = centerX,
            centerY = centerY,
            dedupPriority = dedupPriority,
            cropBox = CropBox(cropX1, cropY1, cropX2, cropY2),
        )
    }

    /**
     * Run the pose model on a cropped image region.
     *
     * The crop is stretched to 640×640 (no letterboxing).
     */
    private fun runPose(
        crop: BufferedImage,
        confThreshold: Float = 0.5f,
        imgSize: Int = DEFAULT_IMG_SIZE,
    ): List<RawPoseDetection> {
        val cropW = crop.width
        val cropH = crop.height

        // Use manual bilinear interpolation (matches Python's PIL Image.BILINEAR exactly)
        val preprocessed = YoloPreprocessing.preprocessCrop(crop, imgSize)

        val inputName = session.inputNames.iterator().next()
        val inputTensor =
            OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(preprocessed.flatArray),
                preprocessed.shape,
            )
        val results = session.run(mapOf(inputName to inputTensor))
        val output = results[0].value as Array<Array<FloatArray>>

        val scaleX = preprocessed.cropWidth.toFloat() / imgSize
        val scaleY = preprocessed.cropHeight.toFloat() / imgSize
        val rows = output[0]

        val detections = mutableListOf<RawPoseDetection>()
        for (row in rows) {
            val conf = row[4]
            if (conf < confThreshold) continue

            val keypoints = mutableListOf<Keypoint>()
            for (k in 0 until 4) {
                keypoints.add(
                    Keypoint(
                        name = KEYPOINT_NAMES[k],
                        x = row[6 + k * 3] * scaleX,
                        y = row[6 + k * 3 + 1] * scaleY,
                        visibility = row[6 + k * 3 + 2],
                    )
                )
            }

            detections.add(RawPoseDetection(confidence = conf, keypoints = keypoints))
        }
        return detections
    }

    /**
     * Compute crop limits: cap per-side expansion to avoid pulling in content from adjacent
     * detection boxes. Matches the Python `_compute_crop_limits()`.
     */
    fun computeCropLimits(
        detections: List<YoloDetectionService.Detection>,
        imageWidth: Int,
        imageHeight: Int,
        maxIntrusionRatio: Float = 0.15f,
    ): List<CropLimits> {
        return detections.mapIndexed { i, det ->
            val boxW = det.x2 - det.x1
            val boxH = det.y2 - det.y1

            var leftLimit = det.x1.toInt()
            var rightLimit = imageWidth - det.x2.toInt()
            var upLimit = det.y1.toInt()
            var downLimit = imageHeight - det.y2.toInt()

            for ((j, other) in detections.withIndex()) {
                if (i == j) continue
                val otherW = other.x2 - other.x1
                val otherH = other.y2 - other.y1

                if (other.x2 <= det.x1) {
                    val gap = det.x1 - other.x2
                    val intrusion = max(boxW * maxIntrusionRatio, otherW * maxIntrusionRatio)
                    leftLimit = min(leftLimit, (gap + intrusion).toInt())
                }
                if (other.x1 >= det.x2) {
                    val gap = other.x1 - det.x2
                    val intrusion = max(boxW * maxIntrusionRatio, otherW * maxIntrusionRatio)
                    rightLimit = min(rightLimit, (gap + intrusion).toInt())
                }
                if (other.y2 <= det.y1) {
                    val gap = det.y1 - other.y2
                    val intrusion = max(boxH * maxIntrusionRatio, otherH * maxIntrusionRatio)
                    upLimit = min(upLimit, (gap + intrusion).toInt())
                }
                if (other.y1 >= det.y2) {
                    val gap = other.y1 - det.y2
                    val intrusion = max(boxH * maxIntrusionRatio, otherH * maxIntrusionRatio)
                    downLimit = min(downLimit, (gap + intrusion).toInt())
                }
            }
            CropLimits(left = leftLimit, right = rightLimit, up = upLimit, down = downLimit)
        }
    }

    /** Greedy deduplication by keypoint-center proximity. */
    fun dedupPoseResults(results: MutableList<PoseResult>, minCenterDist: Float): List<PoseResult> {
        results.sortByDescending { it.dedupPriority }
        val kept = mutableListOf<PoseResult>()
        for (r in results) {
            val tooClose =
                kept.any { k ->
                    val dx = r.centerX - k.centerX
                    val dy = r.centerY - k.centerY
                    sqrt(dx * dx + dy * dy) < minCenterDist
                }
            if (!tooClose) kept.add(r)
        }
        return kept
    }

    /**
     * Compute keypoints-derived bounding box for pose refinement. Returns (x1,y1,x2,y2) or null.
     */
    fun keypointsToBbox(keypoints: List<Keypoint>, margin: Float = 0f): FloatArray? {
        val visible = keypoints.filter { it.visibility >= VIS_THRESH_DEDUP }
        if (visible.size < 2) return null
        val xs = visible.map { it.x }
        val ys = visible.map { it.y }
        return floatArrayOf(
            xs.min() - margin,
            ys.min() - margin,
            xs.max() + margin,
            ys.max() + margin,
        )
    }

    private data class RawPoseDetection(val confidence: Float, val keypoints: List<Keypoint>)

    companion object {
        const val DEFAULT_IMG_SIZE = 640
        const val POSE_CROP_EXPAND = 0.15f
        const val POSE_REFINE_EXPAND = 0.05f
        const val DEDUP_MIN_DIST_RATIO = 0.12f
        const val VIS_THRESH_DEDUP = 0.25f
        val KEYPOINT_NAMES = listOf("LL", "UL", "UR", "LR")
    }
}
