@file:Suppress("MaxLineLength", "ReturnCount")

package org.kryspetrie.fileimport.infrastructure.photoscan.yolo

import java.awt.image.BufferedImage
import kotlin.math.min
import org.kryspetrie.fileimport.domain.model.DetectedPhoto
import org.kryspetrie.fileimport.domain.model.DetectionMode
import org.kryspetrie.fileimport.domain.model.PhotoCorner
import org.kryspetrie.fileimport.infrastructure.logging.AppLogger

/**
 * Multi-stage YOLO photo detection pipeline matching photocrop.py's default behavior.
 *
 * Pipeline stages (matching `corner_refine` preset):
 * 1. Detection → YOLO detection model → NMS → bounding boxes
 * 2. Pose → Crop + expand → pose model → 4 keypoints (LL/UL/UR/LR + visibility)
 * 3. Pose Refine → Re-derive bbox from keypoints + re-run pose
 * 4. Dedup → Greedy by keypoint-center proximity
 * 5. Warp Recovery → Re-pose with larger crops for warped detections
 * 6. Rescue → Sobel edge detection + line intersection for low-visibility corners (two-pass with
 *    neighbor-anchored projection + strip search)
 * 7. Corner Regression → Per-corner 320×320 crop + corner model → sub-pixel accuracy
 *
 * The pipeline produces DetectedPhoto objects with corners in petrie's TL/TR/BR/BL convention,
 * mapping from YOLO's LL/UL/UR/LR keypoint order.
 *
 * @param detectionService YOLO detection inference
 * @param poseService YOLO pose inference
 * @param cornerRegressionService Corner regression inference
 * @param appLogger Optional logger for diagnostic output
 */
class YoloPhotoScanPipeline(
    private val detectionService: YoloDetectionService,
    private val poseService: YoloPoseService,
    private val cornerRegressionService: YoloCornerRegressionService,
    private val appLogger: AppLogger? = null,
) {
    /** Corner rescue service for CV-based low-visibility corner recovery */
    private val cornerRescueService = CornerRescueService(appLogger)

    /** Warp recovery service for re-posing photos with high warp scores */
    private val warpRecoveryService = WarpRecoveryService(poseService, appLogger)

    /**
     * Run the full detection pipeline on an image.
     *
     * @param image Source image
     * @param config Pipeline configuration
     * @return List of DetectedPhoto objects with corners in TL/TR/BR/BL order
     */
    fun detectPhotos(
        image: BufferedImage,
        config: PipelineConfig = PipelineConfig(),
    ): List<DetectedPhoto> {
        val origW = image.width
        val origH = image.height

        // Stage 1: Detection
        val detections =
            if (config.runDetection) {
                detectionService.detect(
                    image = image,
                    confThreshold = config.detConfThreshold,
                    iouThreshold = config.iouThreshold,
                    imgSize = config.imgSize,
                )
            } else {
                // No detection — treat the whole image as one box
                listOf(
                    YoloDetectionService.Detection(
                        x1 = 0f,
                        y1 = 0f,
                        x2 = origW.toFloat(),
                        y2 = origH.toFloat(),
                        confidence = 1.0f,
                    )
                )
            }

        if (detections.isEmpty()) return emptyList()

        // Compute crop limits to avoid pulling in adjacent photos
        val cropLimitsList = poseService.computeCropLimits(detections, origW, origH)

        // Stage 2: Pose detection on each box
        val poseResults = mutableListOf<PoseResultExt>()
        for ((detIdx, det) in detections.withIndex()) {
            val cropLimits = if (config.runDetection) cropLimitsList[detIdx] else null
            val result =
                poseService.runPoseOnCrop(
                    image = image,
                    box = det,
                    confThreshold = config.poseConfThreshold,
                    imgSize = config.imgSize,
                    expandRatio = config.poseCropExpand,
                    cropLimits = cropLimits,
                ) ?: continue

            // Stage 2b: Pose refinement (re-run on tighter crop from keypoints)
            if (config.poseRefine) {
                val refinedBox = poseService.keypointsToBbox(result.keypoints)
                if (refinedBox != null) {
                    val refinedDetection =
                        YoloDetectionService.Detection(
                            x1 = refinedBox[0],
                            y1 = refinedBox[1],
                            x2 = refinedBox[2],
                            y2 = refinedBox[3],
                            confidence = det.confidence,
                        )
                    val refined =
                        poseService.runPoseOnCrop(
                            image = image,
                            box = refinedDetection,
                            confThreshold = config.poseConfThreshold,
                            imgSize = config.imgSize,
                            expandRatio = config.poseRefineExpand,
                        )
                    if (refined != null) {
                        poseResults.add(PoseResultExt(refined.copy(detection = det), det))
                        continue
                    }
                }
            }

            poseResults.add(PoseResultExt(result.copy(detection = det), det))
        }

        if (poseResults.isEmpty()) return emptyList()

        // Stage 3: Deduplicate by keypoint-center proximity
        if (config.runDetection && poseResults.size > 1) {
            val minDist = min(origW, origH).toFloat() * config.dedupMinDistRatio
            val deduped =
                poseService.dedupPoseResults(
                    results = poseResults.map { it.poseResult }.toMutableList(),
                    minCenterDist = minDist,
                )
            // Re-associate — deduped results already carry their detection
            poseResults.clear()
            for (dedupedResult in deduped) {
                val det = dedupedResult.detection ?: continue
                poseResults.add(PoseResultExt(dedupedResult, det))
            }
        }

        // Stage 3.5: Warp recovery
        if (config.warpRecover && poseResults.isNotEmpty()) {
            warpRecoveryService.warpRecovery(image, poseResults, detections, config)
        }

        // Stage 4: Optional CV refinement on all corners (not in default)
        // cv_refine=False in corner_refine preset

        // Stage 5: Rescue low-visibility corners (always on)
        // Two-pass with neighbor-anchored projection + Sobel edge analysis
        val mutablePoseResults = poseResults.map { it.poseResult }.toMutableList()

        cornerRescueService.rescueLowVisCorners(image, mutablePoseResults, config)

        // Update poseResults from rescue
        for (i in mutablePoseResults.indices) {
            poseResults[i] = poseResults[i].copy(poseResult = mutablePoseResults[i])
        }

        // Stage 6: Corner regression refinement (in corner_refine preset)
        if (config.cornerRefine) {
            for (i in poseResults.indices) {
                val result = poseResults[i].poseResult
                val refinements =
                    cornerRegressionService.refineCorners(
                        image = image,
                        poseResult = result,
                        iterations = config.cornerRefineIterations,
                        confThreshold = config.cornerRefineConf,
                    )
                // Update keypoints with refined positions
                val updatedKeypoints =
                    result.keypoints.map { kp ->
                        val refinement = refinements[kp.name]
                        if (refinement != null) {
                            kp.copy(
                                x = refinement.refinedX,
                                y = refinement.refinedY,
                                visibility = 1.0f, // Corner regression boosts visibility to 1.0
                            )
                        } else {
                            kp
                        }
                    }
                poseResults[i] =
                    poseResults[i].copy(poseResult = result.copy(keypoints = updatedKeypoints))
            }
        }

        // Convert to DetectedPhoto objects
        return poseResults.map { resultExt ->
            keypointsToDetectedPhoto(
                resultExt.poseResult.keypoints,
                resultExt.poseResult.confidence,
                origW,
                origH,
            )
        }
    }

    // -----------------------------------------------------------------------
    // Coordinate mapping
    // -----------------------------------------------------------------------

    /** Convert YOLO keypoints (LL/UL/UR/LR) to petrie's DetectedPhoto (TL/TR/BR/BL). */
    private fun keypointsToDetectedPhoto(
        keypoints: List<YoloPoseService.Keypoint>,
        confidence: Float,
        imageWidth: Int,
        imageHeight: Int,
        detectionMode: DetectionMode = DetectionMode.PERSPECTIVE_CORRECTION,
    ): DetectedPhoto {
        val kpMap = keypoints.associateBy { it.name }

        val topLeft = kpMap["UL"]?.let { PhotoCorner(it.x, it.y) } ?: PhotoCorner()
        val topRight = kpMap["UR"]?.let { PhotoCorner(it.x, it.y) } ?: PhotoCorner()
        val bottomRight = kpMap["LR"]?.let { PhotoCorner(it.x, it.y) } ?: PhotoCorner()
        val bottomLeft = kpMap["LL"]?.let { PhotoCorner(it.x, it.y) } ?: PhotoCorner()

        return DetectedPhoto(
            topLeft = topLeft,
            topRight = topRight,
            bottomRight = bottomRight,
            bottomLeft = bottomLeft,
            applyPerspectiveCorrection = true,
            confidence = confidence,
            detectionMode = detectionMode,
        )
    }

    // -----------------------------------------------------------------------
    // Pipeline configuration
    // -----------------------------------------------------------------------

    /**
     * Pipeline configuration matching photocrop.py's default/CLI behavior. Defaults match the
     * `corner_refine` preset.
     */
    data class PipelineConfig(
        /** Whether to run the detection model (False = use whole image as one box) */
        val runDetection: Boolean = true,
        /** Detection confidence threshold */
        val detConfThreshold: Float = 0.5f,
        /** Pose confidence threshold */
        val poseConfThreshold: Float = 0.5f,
        /** NMS IoU threshold */
        val iouThreshold: Float = 0.45f,
        /** Model input size */
        val imgSize: Int = 640,
        /** Detection box expansion for pose crop (fraction of larger dim) */
        val poseCropExpand: Float = 0.15f,
        /** Whether to run pose refinement */
        val poseRefine: Boolean = true,
        /** Expansion ratio for pose refinement crop */
        val poseRefineExpand: Float = 0.05f,
        /** Dedup minimum distance ratio (fraction of min(w,h)) */
        val dedupMinDistRatio: Float = 0.12f,
        /** Whether to run warp recovery */
        val warpRecover: Boolean = true,
        /** Warp recovery max iterations */
        val warpRecoverMaxIters: Int = 3,
        /** Warp recovery starting expansion ratio */
        val warpRecoverExpandStart: Float = 0.10f,
        /** Warp recovery expansion step per iteration */
        val warpRecoverExpandStep: Float = 0.08f,
        /** Warp recovery absolute threshold */
        val warpRecoverAbsoluteThresh: Float = 1.15f,
        /** Warp recovery outlier ratio */
        val warpRecoverOutlierRatio: Float = 2.0f,
        /** Whether to run CV refinement on all corners (not in default) */
        val cvRefine: Boolean = false,
        /** Whether to run corner regression refinement */
        val cornerRefine: Boolean = true,
        /** Corner regression iterations */
        val cornerRefineIterations: Int = 2,
        /** Corner regression confidence threshold */
        val cornerRefineConf: Float = 0.3f,
        /**
         * Rescue visibility threshold for deciding whether a photo needs rescue. A photo needs
         * rescue if it has fewer than rescueMinVisibleCorners (3) corners with visibility >= this
         * threshold. Set to 0.5 because the ONNX model in this pipeline assigns moderate visibility
         * (0.25-0.5) to corners at wrong positions; using 0.3 (Python's threshold) would not
         * trigger rescue for these corners since the model gives them vis=0.38, which exceeds 0.3.
         */
        val rescueVisThreshold: Float = 0.5f,
        /** Rescue minimum visible corners */
        val rescueMinVisibleCorners: Int = 3,
        /** Search radius for CV refinement (pixels) */
        val rescueRadius: Int = 40,
    )
}
