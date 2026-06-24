@file:Suppress("MaxLineLength", "ReturnCount")

package org.kryspetrie.fileimport.infrastructure.photoscan.yolo

import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sqrt
import org.kryspetrie.fileimport.infrastructure.logging.AppLogger

data class PoseResultExt(
    val poseResult: YoloPoseService.PoseResult,
    val detection: YoloDetectionService.Detection,
)

/**
 * Warp recovery service: re-runs pose with progressively larger crops for photos with high warp
 * scores.
 *
 * Matches the Python `warp_recovery()`. A photo is flagged when EITHER:
 * - Absolute threshold: warp score exceeds absolute_thresh (default 1.15)
 * - Peer outlier: warp exceeds median × outlier_ratio AND exceeds absolute_thresh
 *
 * For each flagged photo, iteratively expands the crop and re-runs the pose model. Keeps the best
 * result (lowest warp score) across all iterations. Re-deduplicates after any replacements.
 *
 * @param poseService YOLO pose inference (for computeCropLimits and runPoseOnCrop)
 * @param appLogger Optional logger for diagnostic output
 */
class WarpRecoveryService(
    private val poseService: YoloPoseService,
    private val appLogger: AppLogger? = null,
) {
    /**
     * Warp recovery: re-run pose with progressively larger crops for photos with high warp scores.
     *
     * Matches the Python `warp_recovery()`. A photo is flagged when EITHER:
     * - Absolute threshold: warp score exceeds absolute_thresh (default 1.15)
     * - Peer outlier: warp exceeds median × outlier_ratio AND exceeds absolute_thresh
     *
     * For each flagged photo, iteratively expand the crop and re-run the pose model. Keep the best
     * result (lowest warp score) across all iterations. Re-deduplicate after any replacements.
     */
    internal fun warpRecovery(
        image: BufferedImage,
        results: MutableList<PoseResultExt>,
        detections: List<YoloDetectionService.Detection>,
        config: YoloPhotoScanPipeline.PipelineConfig,
    ) {
        if (results.isEmpty()) return

        val origW = image.width
        val origH = image.height

        // Compute warp scores
        val scores = results.map { computeWarpScore(it.poseResult) }
        val finiteScores = scores.filter { it != Float.POSITIVE_INFINITY }
        val medianScore =
            if (finiteScores.isNotEmpty()) finiteScores.sorted()[finiteScores.size / 2]
            else Float.POSITIVE_INFINITY
        val peerThreshold =
            if (finiteScores.isNotEmpty()) medianScore * config.warpRecoverOutlierRatio
            else Float.POSITIVE_INFINITY

        // Identify photos needing recovery
        val outlierIndices = mutableListOf<Int>()
        for ((i, s) in scores.withIndex()) {
            if (s > config.warpRecoverAbsoluteThresh) {
                outlierIndices.add(i)
            } else if (
                finiteScores.isNotEmpty() &&
                    s > peerThreshold &&
                    s > config.warpRecoverAbsoluteThresh
            ) {
                outlierIndices.add(i)
            }
        }

        if (outlierIndices.isEmpty()) return

        appLogger?.info(
            "Warp recovery: ${outlierIndices.size} photos flagged (scores=${scores.map { if (it == Float.POSITIVE_INFINITY) "inf" else "%.3f".format(it) }})"
        )

        // Compute crop limits for detections if available
        val cropLimitsList =
            if (config.runDetection) {
                poseService.computeCropLimits(detections, origW, origH)
            } else {
                null
            }

        for (idx in outlierIndices) {
            val resExt = results[idx]
            val res = resExt.poseResult
            val det = resExt.detection
            var bestWarp = scores[idx]
            var bestResult = res

            // Find which detection index this corresponds to (for crop limits)
            val detIdx = detections.indexOf(det)

            for (iteration in 0 until config.warpRecoverMaxIters) {
                val expandRatio =
                    config.warpRecoverExpandStart + config.warpRecoverExpandStep * iteration

                val cl = if (cropLimitsList != null && detIdx >= 0) cropLimitsList[detIdx] else null

                val retry =
                    poseService.runPoseOnCrop(
                        image = image,
                        box = det,
                        confThreshold = config.poseConfThreshold,
                        imgSize = config.imgSize,
                        expandRatio = expandRatio,
                        cropLimits = cl,
                    ) ?: continue

                val retryWithDet = retry.copy(detection = det)
                val retryWarp = computeWarpScore(retryWithDet)

                if (retryWarp < bestWarp) {
                    bestWarp = retryWarp
                    bestResult = retryWithDet
                    if (bestWarp <= config.warpRecoverAbsoluteThresh) break
                }
            }

            if (bestResult !== res) {
                results[idx] = PoseResultExt(bestResult, det)
                appLogger?.info(
                    "Warp recovery: photo #${idx + 1} improved warp ${scores[idx].formatWarp()} → ${bestWarp.formatWarp()}"
                )
            }
        }

        // Re-deduplicate after any replacements
        if (results.size > 1) {
            val minDist = min(origW, origH).toFloat() * config.dedupMinDistRatio
            val deduped =
                poseService.dedupPoseResults(
                    results = results.map { it.poseResult }.toMutableList(),
                    minCenterDist = minDist,
                )
            // Rebuild results from deduped list
            val dedupedDetMap = mutableMapOf<Int, YoloDetectionService.Detection>()
            for (orig in results) {
                dedupedDetMap[System.identityHashCode(orig.poseResult)] = orig.detection
            }
            results.clear()
            for (dedupedResult in deduped) {
                val det = dedupedResult.detection ?: detections.firstOrNull() ?: continue
                results.add(PoseResultExt(dedupedResult, det))
            }
        }
    }

    /**
     * Compute a warp score for a pose result.
     *
     * Score = max(aspect_ratio_disparity, 1 + max_angle_deviation/45). Perfect rectangle = 1.0.
     * Typical good = 1.0-1.05.
     *
     * Uses a visibility threshold of 0.5 for warp scoring. Corners with visibility below 0.5 are
     * considered unreliable for warp computation — they may be at wrong positions (the model
     * assigns moderate visibility to corners on wrong photo edges). Using a higher threshold than
     * dedup's 0.25 ensures that such corners cause infinite warp score, triggering recovery. This
     * matches the effective behavior of Python's pipeline where the model produces lower visibility
     * for wrong corners (0.007 vs 0.38).
     */
    internal fun computeWarpScore(result: YoloPoseService.PoseResult): Float {
        val kpMap = result.keypoints.associateBy { it.name }
        val required = listOf("LL", "UL", "UR", "LR")
        for (name in required) {
            if (name !in kpMap) return Float.POSITIVE_INFINITY
        }

        // Use 0.5 visibility threshold for warp scoring.
        // Corners with vis < 0.5 are likely at wrong positions and should
        // cause infinite warp score, triggering recovery.
        val visCount = result.keypoints.count { it.visibility >= 0.5f }
        if (visCount < 4) return Float.POSITIVE_INFINITY

        val ul = kpMap["UL"]!!
        val ur = kpMap["UR"]!!
        val lr = kpMap["LR"]!!
        val ll = kpMap["LL"]!!

        // Opposite edge lengths
        val wTop = distance(ur, ul)
        val wBot = distance(lr, ll)
        val hLeft = distance(ll, ul)
        val hRight = distance(lr, ur)

        if (minOf(wTop, wBot) < 1e-6f || minOf(hLeft, hRight) < 1e-6f)
            return Float.POSITIVE_INFINITY

        val wRatio = maxOf(wTop, wBot) / minOf(wTop, wBot)
        val hRatio = maxOf(hLeft, hRight) / minOf(hLeft, hRight)
        val aspectDisparity = maxOf(wRatio, hRatio)

        // Angle deviation from 90° at each corner
        val corners = listOf(ul, ur, lr, ll)
        var maxAngleDev = 0f
        for (i in 0 until 4) {
            val prev = corners[(i - 1 + 4) % 4]
            val curr = corners[i]
            val next = corners[(i + 1) % 4]
            val dev = angleDeviationFrom90(prev, curr, next)
            maxAngleDev = maxOf(maxAngleDev, dev)
        }
        val angleFactor = 1f + maxAngleDev / 45f

        return maxOf(aspectDisparity, angleFactor)
    }

    internal fun angleDeviationFrom90(
        a: YoloPoseService.Keypoint,
        b: YoloPoseService.Keypoint,
        c: YoloPoseService.Keypoint,
    ): Float {
        val v1x = a.x - b.x
        val v1y = a.y - b.y
        val v2x = c.x - b.x
        val v2y = c.y - b.y
        val dot = v1x * v2x + v1y * v2y
        val cross = v1x * v2y - v1y * v2x
        val angleRad = atan2(cross.toDouble(), dot.toDouble())
        val angleDeg = Math.toDegrees(angleRad)
        return abs(angleDeg.toFloat() - 90f)
    }

    internal fun distance(a: YoloPoseService.Keypoint, b: YoloPoseService.Keypoint): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        return sqrt(dx * dx + dy * dy)
    }

    internal fun Float.formatWarp(): String =
        if (this == Float.POSITIVE_INFINITY) "inf" else "%.3f".format(this)
}