@file:Suppress("MaxLineLength", "ReturnCount")

package org.kryspetrie.fileimport.infrastructure.photoscan.yolo

import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
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
            warpRecovery(image, poseResults, detections, config)
        }

        // Stage 4: Optional CV refinement on all corners (not in default)
        // cv_refine=False in corner_refine preset

        // Stage 5: Rescue low-visibility corners (always on)
        // Two-pass with neighbor-anchored projection + Sobel edge analysis
        val mutablePoseResults = poseResults.map { it.poseResult }.toMutableList()

        rescueLowVisCorners(image, mutablePoseResults, config)

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
    // Warp Recovery
    // -----------------------------------------------------------------------

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
    private fun warpRecovery(
        image: BufferedImage,
        results: MutableList<PoseResultExt>,
        detections: List<YoloDetectionService.Detection>,
        config: PipelineConfig,
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
    private fun computeWarpScore(result: YoloPoseService.PoseResult): Float {
        val kpMap = result.keypoints.associateBy { it.name }
        val required = listOf("LL", "UL", "UR", "LR")
        for (name in required) {
            if (name !in kpMap) return Float.POSITIVE_INFINITY
        }

        // Use 0.5 visibility threshold for warp scoring.
        // Corners with vis < 0.5 are likely at wrong positions and should
        // cause infinite warp score, triggering recovery.
        val WARP_VIS_THRESH = 0.5f
        val visCount = result.keypoints.count { it.visibility >= WARP_VIS_THRESH }
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

    private fun angleDeviationFrom90(
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

    private fun distance(a: YoloPoseService.Keypoint, b: YoloPoseService.Keypoint): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        return sqrt(dx * dx + dy * dy)
    }

    // -----------------------------------------------------------------------
    // Rescue low-visibility corners (two-pass + neighbor projection + strip search)
    // -----------------------------------------------------------------------

    /**
     * Rescue low-visibility corners using Sobel edge detection with neighbor-anchored projection
     * and strip search.
     *
     * Matches the Python `rescue_low_vis_corners()` and `refine_corners_cv()`.
     *
     * For each photo with fewer than 3 visible corners (visibility >= 0.3), applies CV edge
     * refinement on ALL low-visibility corners using:
     * 1. Neighbor-anchored projection: The two adjacent corners share edges with this corner and
     *    can project a better search center.
     * 2. Strip search: When one neighbor provides a reliable projection, do 1D gradient profile
     *    analysis perpendicular to the known axis, then confirm along the other axis.
     * 3. Two-pass refinement: Pass 1 refines corners with available neighbors. Their boosted
     *    visibility makes them available for pass 2, allowing corners that initially had no
     *    reliable neighbors to benefit from projection.
     *
     * CRITICAL: The two-pass logic modifies mutable keypoints in-place and carries pass 0
     * improvements into pass 1. This matches the Python behavior where kp["x"] and kp["visibility"]
     * are modified in-place and persist across passes.
     */
    private fun rescueLowVisCorners(
        image: BufferedImage,
        results: MutableList<YoloPoseService.PoseResult>,
        config: PipelineConfig,
    ) {
        // Find results that need rescue — matches Python's rescue_low_vis_corners().
        // A photo needs rescue if it has fewer than minVisibleCorners (3)
        // corners with visibility >= rescueVisThreshold (0.3).
        val needsRescue = mutableListOf<Int>()
        for ((i, res) in results.withIndex()) {
            val visCount = res.keypoints.count { it.visibility >= config.rescueVisThreshold }
            if (visCount < config.rescueMinVisibleCorners) {
                needsRescue.add(i)
            }
        }

        if (needsRescue.isEmpty()) return

        appLogger?.info("Rescue: ${needsRescue.size} photo(s) need CV edge refinement")

        // Compute Sobel gradients for the full image (used by both strip search and 2D search)
        val imgW = image.width
        val imgH = image.height
        val gray = toGrayscaleArray(image)
        val (gradX, gradY, gradMag) = computeSobelGradients(gray, imgW, imgH)

        // Two-pass refinement — CRITICAL: carry improvements across passes
        // Python modifies kps in-place: kp["x"], kp["visibility"], kp["nn_x"] etc.
        // We must convert to mutable keypoints ONCE per photo, then carry through
        // both passes, writing back to results after each pass so pass 1 sees
        // pass 0 improvements.
        val RESCUE_VIS_THRESHOLD = 0.7f

        // Create mutable keypoints for each result that needs rescue.
        // These persist across both passes, exactly like Python's in-place dict modification.
        val mutableKpsMap = mutableMapOf<Int, MutableList<MutableKeypoint>>()
        for (idx in needsRescue) {
            mutableKpsMap[idx] =
                results[idx]
                    .keypoints
                    .map { kp ->
                        MutableKeypoint(
                            name = kp.name,
                            x = kp.x,
                            y = kp.y,
                            visibility = kp.visibility,
                        )
                    }
                    .toMutableList()
        }

        for (passNum in 0 until 2) {
            for (idx in needsRescue) {
                val result = results[idx]
                val mutKps = mutableKpsMap[idx]!!
                val detBox = result.detection

                for (kpIdx in mutKps.indices) {
                    val mkp = mutKps[kpIdx]

                    // Skip corners already refined in a previous pass — matches Python's
                    // `if "nn_x" in kp: continue` which marks refined corners permanently
                    if (mkp.nnX != null) continue

                    // Skip high-visibility corners — matches Python's
                    // `if kp["visibility"] >= vis_threshold: continue`
                    // where vis_threshold = _RESCUE_VIS_THRESHOLD = 0.7
                    if (mkp.visibility >= RESCUE_VIS_THRESHOLD) continue
                    val cx = mkp.x
                    val cy = mkp.y
                    val vis = mkp.visibility
                    val cornerName = mkp.name
                    // --- Enhancement: Neighbor-anchored projection ---
                    // This uses the CURRENT (possibly pass-0-updated) positions of neighbors
                    appLogger?.info(
                        "[RESCUE] $cornerName pass=$passNum vis=${String.format("%.2f", mkp.visibility)} pos=(${mkp.x.toInt()},${mkp.y.toInt()})"
                    )
                    val proj = projectFromNeighbors(mutKps, kpIdx)
                    val projX = proj.projX
                    val projY = proj.projY
                    val projConf = proj.confidence
                    val projectedAxis = proj.projectedAxis

                    val useProjection = projConf >= NEIGHBOR_VIS_THRESHOLD
                    appLogger?.info(
                        "[RESCUE] $cornerName projectedAxis=$projectedAxis projConf=${String.format("%.2f", projConf)} useProjection=$useProjection proj=(${projX?.toInt()},${projY?.toInt()})"
                    )

                    val searchCx: Float
                    val searchCy: Float
                    if (useProjection && projX != null && projY != null) {
                        searchCx = projX
                        searchCy = projY
                    } else {
                        searchCx = cx
                        searchCy = cy
                    }

                    // --- Strip search: preferred method when partial projection is available ---
                    var refined: Triple<Float, Float, Float>? = null

                    if (projectedAxis in listOf("x", "y")) {
                        // Partial projection: strip search is the primary method
                        val projAxisVal: Float
                        val nnOtherAxis: Float?
                        if (projectedAxis == "y") {
                            projAxisVal = projY!!
                            nnOtherAxis = cx // use NN position for unprojected axis
                        } else {
                            projAxisVal = projX!!
                            nnOtherAxis = cy
                        }

                        val boxHintArr = detBox?.let { floatArrayOf(it.x1, it.y1, it.x2, it.y2) }
                        val stripResult =
                            stripSearchCorner(
                                gradX,
                                gradY,
                                gradMag,
                                imgW,
                                imgH,
                                cornerName,
                                projectedAxis,
                                projAxisVal,
                                nnOtherAxis,
                                boxHint = boxHintArr,
                            )

                        if (stripResult != null) {
                            val (sx, sy, sConf) = stripResult
                            if (
                                sx in 0f..imgW.toFloat() &&
                                    sy in 0f..imgH.toFloat() &&
                                    sConf >= 0.5f
                            ) {
                                refined = Triple(sx, sy, sConf)
                            }
                        }

                        // Fallback: try 2D search if strip search failed
                        if (refined == null) {
                            refined =
                                refineCorner2D(
                                    gradMag,
                                    gradX,
                                    gradY,
                                    imgW,
                                    imgH,
                                    cornerName,
                                    searchCx,
                                    searchCy,
                                    cx,
                                    cy,
                                    config.rescueRadius,
                                    RESCUE_EDGE_THRESHOLD,
                                    RESCUE_MAX_SHIFT_RATIO,
                                    useProjection,
                                )
                        }
                    } else if (projectedAxis == "both") {
                        // Full projection: 2D search should work well
                        refined =
                            refineCorner2D(
                                gradMag,
                                gradX,
                                gradY,
                                imgW,
                                imgH,
                                cornerName,
                                searchCx,
                                searchCy,
                                cx,
                                cy,
                                config.rescueRadius,
                                RESCUE_EDGE_THRESHOLD,
                                RESCUE_MAX_SHIFT_RATIO,
                                useProjection,
                            )
                    } else {
                        // No projection: 2D search with strict NN constraint
                        refined =
                            refineCorner2D(
                                gradMag,
                                gradX,
                                gradY,
                                imgW,
                                imgH,
                                cornerName,
                                cx,
                                cy,
                                cx,
                                cy,
                                config.rescueRadius,
                                RESCUE_EDGE_THRESHOLD,
                                RESCUE_MAX_SHIFT_RATIO,
                                false,
                            )
                    }

                    if (refined == null) continue

                    val (ix, iy, _) = refined

                    // Save original position and visibility before boosting
                    // Matches Python: kp["nn_x"] = kp["x"], etc.
                    mkp.nnX = mkp.x
                    mkp.nnY = mkp.y
                    mkp.nnVis = mkp.visibility
                    if (useProjection && projX != null) mkp.projX = projX
                    if (useProjection && projY != null) mkp.projY = projY
                    mkp.x = ix.coerceIn(0f, imgW.toFloat())
                    mkp.y = iy.coerceIn(0f, imgH.toFloat())
                    mkp.visibility = maxOf(vis, 0.5f)
                }

                // Write back to results after each pass, so pass 1 reads
                // pass 0's improved positions and visibilities
                val updatedKeypoints =
                    mutKps.map { mkp ->
                        YoloPoseService.Keypoint(
                            name = mkp.name,
                            x = mkp.x,
                            y = mkp.y,
                            visibility = mkp.visibility,
                        )
                    }
                results[idx] = result.copy(keypoints = updatedKeypoints)

                // Also update the mutableKpsMap results object for next pass
                // (so detection box reference stays valid)
                // Note: mutableKpsMap[idx] still holds the same MutableKeypoint list
                // which already has the updated x, y, visibility, nnX, etc.
            }
        }
    }

    /**
     * Neighbor-anchored projection: use high-vis neighbor corners to project a better search center
     * for a low-visibility corner.
     *
     * For each corner type, two neighbors share edges:
     * - LL: h_neighbor=LR (bottom edge, projects y), v_neighbor=UL (left edge, projects x)
     * - UL: h_neighbor=UR (top edge, projects y), v_neighbor=LL (left edge, projects x)
     * - UR: h_neighbor=UL (top edge, projects y), v_neighbor=LR (right edge, projects x)
     * - LR: h_neighbor=LL (bottom edge, projects y), v_neighbor=UR (right edge, projects x)
     *
     * Only uses neighbors with visibility >= NEIGHBOR_VIS_THRESHOLD (0.5).
     */
    private fun projectFromNeighbors(kps: List<MutableKeypoint>, cornerIdx: Int): ProjectionResult {
        val kp = kps[cornerIdx]
        val cornerName = kp.name
        val neighbors =
            CORNER_NEIGHBORS[cornerName] ?: return ProjectionResult(null, null, 0f, "none")
        val hNeighborName = neighbors["h"]!!
        val vNeighborName = neighbors["v"]!!

        val kpByName = kps.associateBy { it.name }

        var projX: Float? = null
        var projY: Float? = null
        var projYFromH = false
        var projXFromV = false

        // Horizontal-edge neighbor provides y-coordinate projection
        val hKp = kpByName[hNeighborName]
        if (hKp != null && hKp.visibility >= NEIGHBOR_VIS_THRESHOLD) {
            projY = hKp.y
            projYFromH = true
        }

        // Vertical-edge neighbor provides x-coordinate projection
        val vKp = kpByName[vNeighborName]
        if (vKp != null && vKp.visibility >= NEIGHBOR_VIS_THRESHOLD) {
            projX = vKp.x
            projXFromV = true
        }

        if (!projYFromH && !projXFromV) {
            return ProjectionResult(null, null, 0f, "none")
        }

        // If only one neighbor contributed, use the NN position for the missing axis
        if (projX == null) projX = kp.x
        if (projY == null) projY = kp.y

        val contributions = (if (projYFromH) 1 else 0) + (if (projXFromV) 1 else 0)
        val confidence = contributions / 2.0f

        val projectedAxis =
            when {
                projYFromH && projXFromV -> "both"
                projYFromH -> "y"
                else -> "x"
            }

        return ProjectionResult(projX, projY, confidence, projectedAxis)
    }

    private data class ProjectionResult(
        val projX: Float?,
        val projY: Float?,
        val confidence: Float,
        val projectedAxis: String,
    )

    // -----------------------------------------------------------------------
    // Strip Search (1D gradient profile analysis)
    // -----------------------------------------------------------------------

    /**
     * Search for a corner position using 1D strip scans along a projected axis.
     *
     * Matches the Python `_strip_search_corner()` exactly, including:
     * - Using `median(profile)*2` for minimum peak height
     * - Using `max(profile)*0.1` for minimum prominence
     * - Using `distance=5` for minimum peak separation
     * - Selecting the **highest-prominence** peak (not highest value)
     *
     * Real photo boundaries produce sharp, high-prominence spikes; internal content produces
     * broader, lower-prominence structure. Selecting by highest prominence is more reliable than
     * selecting by highest value.
     */
    private fun stripSearchCorner(
        gradX: FloatArray,
        gradY: FloatArray,
        gradMag: FloatArray,
        imgW: Int,
        imgH: Int,
        cornerName: String,
        projAxis: String,
        projVal: Float,
        nnOtherAxis: Float?,
        stripHalfWidth: Int = 15,
        perpendicularRange: Int = 200,
        edgeThreshold: Float = 50f,
        boxHint: FloatArray? = null,
    ): Triple<Float, Float, Float>? {
        val orient = CORNER_ORIENTATION[cornerName] ?: return null

        if (projAxis == "y") {
            // Neighbor projected Y (horizontal edge neighbor).
            // Search 1: horizontal strip at y≈projVal → find strongest vertical edge (gradX)
            // to determine the X coordinate.
            val yCenter = projVal.roundToInt()
            val yLo = max(0, yCenter - stripHalfWidth)
            val yHi = min(imgH, yCenter + stripHalfWidth + 1)

            var xCenter = nnOtherAxis?.roundToInt() ?: (imgW / 2)
            var xLo = max(0, xCenter - perpendicularRange)
            var xHi = min(imgW, xCenter + perpendicularRange)

            // Constrain perpendicular search to detection box if available
            if (boxHint != null) {
                val bx1 = boxHint[0].roundToInt()
                val bx2 = boxHint[2].roundToInt()
                if (orient["v"] == "left") {
                    // Right column corner (LR, UR): edge near box x2
                    xLo = max(xLo, bx2 - 30)
                    xHi = min(xHi, bx2 + 30)
                } else {
                    // Left column corner (LL, UL): edge near box x1
                    xLo = max(xLo, bx1 - 30)
                    xHi = min(xHi, bx1 + 30)
                }
            }

            if (xLo >= xHi || yLo >= yHi) return null

            // Sum absolute grad_x values along horizontal strip to get 1D profile
            val stripHeight = yHi - yLo
            val profile = FloatArray(xHi - xLo)
            for (ey in yLo until yHi) {
                for (ex in xLo until xHi) {
                    profile[ex - xLo] += abs(gradX[ey * imgW + ex])
                }
            }

            // Check minimum profile max threshold (matches Python: edge_threshold * strip_height *
            // 0.3)
            if (
                profile.isEmpty() ||
                    profile.maxOrNull() == null ||
                    profile.max()!! < edgeThreshold * stripHeight * 0.3f
            ) {
                return null
            }

            // Find peaks using scipy-like thresholds: height=median*2, prominence=max*0.1,
            // distance=5
            val bestX = findScipyLikePeak(profile) ?: return null
            val foundX = (xLo + bestX).toFloat()
            appLogger?.info(
                "[STRIP] $cornerName projAxis=y projVal=${projVal.toInt()}: search1 X range=[$xLo..$xHi], peakIdx=$bestX, foundX=${foundX.toInt()}, profileMax=${profile.maxOrNull()?.toInt()}, profileMedian=${median(profile).toInt()}"
            )

            // Search 2: vertical strip at x≈foundX → find strongest horizontal edge
            // to confirm/refine Y coordinate
            val xCenter2 = foundX.roundToInt()
            val xLo2 = max(0, xCenter2 - stripHalfWidth)
            val xHi2 = min(imgW, xCenter2 + stripHalfWidth + 1)
            val yLo2 = max(0, yCenter - perpendicularRange)
            val yHi2 = min(imgH, yCenter + perpendicularRange)

            if (xLo2 >= xHi2 || yLo2 >= yHi2) return Triple(foundX, projVal, 0.5f)

            val stripWidth2 = xHi2 - xLo2
            val profile2 = FloatArray(yHi2 - yLo2)
            for (ey2 in yLo2 until yHi2) {
                for (ex2 in xLo2 until xHi2) {
                    profile2[ey2 - yLo2] += abs(gradY[ey2 * imgW + ex2])
                }
            }

            if (
                profile2.isEmpty() ||
                    profile2.maxOrNull() == null ||
                    profile2.max()!! < edgeThreshold * stripWidth2 * 0.3f
            ) {
                return Triple(foundX, projVal, 0.5f)
            }

            val bestY = findScipyLikePeak(profile2) ?: return Triple(foundX, projVal, 0.5f)
            val foundY = (yLo2 + bestY).toFloat()
            appLogger?.info(
                "[STRIP] $cornerName projAxis=y: search2 Y range=[$yLo2..$yHi2], peakIdx=$bestY, foundY=${foundY.toInt()}, projVal=${projVal.toInt()}"
            )
            return Triple(foundX, foundY, 1.0f)
        } else {
            // projAxis == "x"
            // Neighbor projected X (vertical edge neighbor).
            // Search 1: vertical strip at x≈projVal → find strongest horizontal edge (grad_y)
            // to determine the Y coordinate.
            val xCenter = projVal.roundToInt()
            val xLo = max(0, xCenter - stripHalfWidth)
            val xHi = min(imgW, xCenter + stripHalfWidth + 1)

            var yCenter = nnOtherAxis?.roundToInt() ?: (imgH / 2)
            var yLo = max(0, yCenter - perpendicularRange)
            var yHi = min(imgH, yCenter + perpendicularRange)

            // Constrain perpendicular search to detection box if available
            if (boxHint != null) {
                val by1 = boxHint[1].roundToInt()
                val by2 = boxHint[3].roundToInt()
                if (orient["h"] == "above") {
                    // Bottom-row corner (LL, LR): edge near box y2
                    yLo = max(yLo, by2 - 30)
                    yHi = min(yHi, by2 + 30)
                } else {
                    // Top-row corner (UL, UR): edge near box y1
                    yLo = max(yLo, by1 - 30)
                    yHi = min(yHi, by1 + 30)
                }
            }

            if (xLo >= xHi || yLo >= yHi) return null

            // Sum absolute grad_y values along vertical strip to get 1D profile
            val stripWidth = xHi - xLo
            val profile = FloatArray(yHi - yLo)
            for (ey in yLo until yHi) {
                for (ex in xLo until xHi) {
                    profile[ey - yLo] += abs(gradY[ey * imgW + ex])
                }
            }

            // Check minimum profile max threshold (matches Python: edge_threshold * strip_width *
            // 0.3)
            if (
                profile.isEmpty() ||
                    profile.maxOrNull() == null ||
                    profile.max()!! < edgeThreshold * stripWidth * 0.3f
            ) {
                return null
            }

            val bestY = findScipyLikePeak(profile) ?: return null
            val foundY = (yLo + bestY).toFloat()
            appLogger?.info(
                "[STRIP] $cornerName projAxis=x projVal=${projVal.toInt()}: search1 Y range=[$yLo..$yHi], peakIdx=$bestY, foundY=${foundY.toInt()}, profileMax=${profile.maxOrNull()?.toInt()}"
            )

            // Search 2: horizontal strip at y≈foundY → find strongest vertical edge
            val yCenter2 = foundY.roundToInt()
            val yLo2 = max(0, yCenter2 - stripHalfWidth)
            val yHi2 = min(imgH, yCenter2 + stripHalfWidth + 1)
            val xLo2 = max(0, xCenter - perpendicularRange)
            val xHi2 = min(imgW, xCenter + perpendicularRange)

            if (xLo2 >= xHi2 || yLo2 >= yHi2) return Triple(projVal, foundY, 0.5f)

            val stripHeight2 = yHi2 - yLo2
            val profile2 = FloatArray(xHi2 - xLo2)
            for (ey2 in yLo2 until yHi2) {
                for (ex2 in xLo2 until xHi2) {
                    profile2[ex2 - xLo2] += abs(gradX[ey2 * imgW + ex2])
                }
            }

            if (
                profile2.isEmpty() ||
                    profile2.maxOrNull() == null ||
                    profile2.max()!! < edgeThreshold * stripHeight2 * 0.3f
            ) {
                return Triple(projVal, foundY, 0.5f)
            }

            val bestX = findScipyLikePeak(profile2) ?: return Triple(projVal, foundY, 0.5f)
            val foundX = (xLo2 + bestX).toFloat()
            appLogger?.info(
                "[STRIP] $cornerName projAxis=x: search2 X range=[$xLo2..$xHi2], peakIdx=$bestX, foundX=${foundX.toInt()}, projVal=${projVal.toInt()}"
            )
            return Triple(foundX, foundY, 1.0f)
        }
    }

    /**
     * Find the highest-prominence peak in a 1D profile using scipy-like thresholds.
     *
     * Matches scipy.signal.find_peaks behavior:
     * - height threshold: median(profile) * 2
     * - prominence threshold: max(profile) * 0.1
     * - minimum distance between peaks: 5
     *
     * Returns the index of the peak with highest prominence, or null. Real photo boundaries produce
     * sharp, high-prominence spikes in the gradient profile, while internal content produces
     * broader structure.
     */
    private fun findScipyLikePeak(profile: FloatArray): Int? {
        if (profile.isEmpty()) return null

        // Compute thresholds matching scipy.signal.find_peaks
        val minHeight = median(profile) * 2f
        val minProminence = profile.maxOrNull()?.let { it * 0.1f } ?: return null
        val minDistance = 5

        // Step 1: Find all local maxima
        val peakIndices = mutableListOf<Int>()
        for (i in 1 until profile.size - 1) {
            if (profile[i] > profile[i - 1] && profile[i] >= profile[i + 1]) {
                peakIndices.add(i)
            }
        }
        // Also check endpoints
        if (profile.size == 1 || (profile.size > 1 && profile[0] >= profile[1])) {
            peakIndices.add(0)
        }
        if (profile.size > 1 && profile.last() >= profile[profile.size - 2]) {
            // Avoid duplicate endpoint
            if (peakIndices.isEmpty() || peakIndices.last() != profile.size - 1) {
                peakIndices.add(profile.size - 1)
            }
        }

        if (peakIndices.isEmpty()) return null

        // Step 2: Apply height threshold
        val heightFiltered = peakIndices.filter { profile[it] >= minHeight }
        if (heightFiltered.isEmpty()) return null

        // Step 3: Apply minimum distance filtering (keep highest value in each cluster)
        // This matches scipy's distance-based suppression: within distance d of a
        // higher peak, smaller peaks are removed.
        val sortedByHeight = heightFiltered.sortedByDescending { profile[it] }
        val distanceFiltered = mutableListOf<Int>()
        for (peak in sortedByHeight) {
            val tooClose = distanceFiltered.any { existing -> abs(peak - existing) < minDistance }
            if (!tooClose) distanceFiltered.add(peak)
        }

        if (distanceFiltered.isEmpty()) return null

        // Step 4: Compute prominence for each remaining peak
        // Prominence = peak_height - max(left_base, right_base)
        // where left_base is the minimum value between the peak and the nearest
        // higher peak (or edge) on the left, and similarly for right_base.
        var bestIdx = distanceFiltered[0]
        var bestProminence = -1f

        for (peak in distanceFiltered) {
            val peakVal = profile[peak]
            val prom = computeProminence(profile, peak)
            if (prom >= minProminence && prom > bestProminence) {
                bestProminence = prom
                bestIdx = peak
            }
        }

        // If no peak met the prominence threshold, return null
        return if (bestProminence >= minProminence) bestIdx else null
    }

    /**
     * Compute the topographic prominence of a peak in a 1D profile.
     *
     * Prominence is defined as the height of the peak relative to the highest minimum on either
     * side between the peak and a higher peak (or the edge). This matches the
     * scipy.signal.peak_prominences algorithm:
     * 1. Find the left base: scan left from peak, tracking the running minimum. Stop when we reach
     *    a value >= peak height (higher peak) or the edge.
     * 2. Find the right base: scan right similarly.
     * 3. Left reference = max(running minimum, peak value on the other side of the higher peak).
     *    The reference is the minimum between the peak and the nearest higher peak on each side.
     * 4. Prominence = peak height - max(left base, right base)
     */
    private fun computeProminence(profile: FloatArray, peakIdx: Int): Float {
        val peakVal = profile[peakIdx]

        // Find left base: minimum between peak and nearest higher peak to the left
        var leftMin = Float.MAX_VALUE
        for (i in (peakIdx - 1) downTo 0) {
            leftMin = minOf(leftMin, profile[i])
            if (profile[i] > peakVal) break
        }
        // If we didn't find a higher peak, leftMin goes to the edge minimum
        // (which is what we want — the minimum value between peak and edge)

        // Find right base: minimum between peak and nearest higher peak to the right
        var rightMin = Float.MAX_VALUE
        for (i in (peakIdx + 1) until profile.size) {
            rightMin = minOf(rightMin, profile[i])
            if (profile[i] > peakVal) break
        }

        val referenceLevel = maxOf(leftMin, rightMin)
        return peakVal - referenceLevel
    }

    /** Compute the median of a float array. */
    private fun median(values: FloatArray): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[mid - 1] + sorted[mid]) / 2f
        } else {
            sorted[mid]
        }
    }

    // -----------------------------------------------------------------------
    // 2D Window Search (orientation-aware edge line intersection)
    // -----------------------------------------------------------------------

    /**
     * 2D window search with orientation-aware edge line intersection.
     *
     * Matches the Python `_refine_corner_2d()` exactly, including:
     * - Orientation-aware spatial filtering using `<=`/`>=` boundary conditions
     * - Edge angle classification: edge_dir = angle + π/2, then dir_mod % π
     * - Fallback to angle-only filtering when orientation filtering produces < 2 lines
     * - Weighted line fitting and intersection
     */
    private fun refineCorner2D(
        gradMag: FloatArray,
        gradX: FloatArray,
        gradY: FloatArray,
        imgW: Int,
        imgH: Int,
        cornerName: String,
        searchCx: Float,
        searchCy: Float,
        nnCx: Float,
        nnCy: Float,
        searchRadius: Int,
        edgeThreshold: Float,
        maxShiftRatio: Float,
        useProjection: Boolean,
    ): Triple<Float, Float, Float>? {
        val x1 = max(0, searchCx.toInt() - searchRadius)
        val y1 = max(0, searchCy.toInt() - searchRadius)
        val x2 = min(imgW, searchCx.toInt() + searchRadius)
        val y2 = min(imgH, searchCy.toInt() + searchRadius)

        if (x2 - x1 < 10 || y2 - y1 < 10) return null

        // Collect edge pixels with orientation filtering
        // First pass: orientation-aware filtering (matches Python _orientation_filter_edge_pixels)
        val hEdgesOriented = mutableListOf<Triple<Float, Float, Float>>() // (x, y, weight)
        val vEdgesOriented = mutableListOf<Triple<Float, Float, Float>>() // (x, y, weight)
        // Second pass (fallback): angle-only filtering
        val hEdgesAngleOnly = mutableListOf<Triple<Float, Float, Float>>()
        val vEdgesAngleOnly = mutableListOf<Triple<Float, Float, Float>>()

        val orient = CORNER_ORIENTATION[cornerName] ?: return null

        for (ey in y1 until y2) {
            for (ex in x1 until x2) {
                val idx = ey * imgW + ex
                val mag = gradMag[idx]
                if (mag < edgeThreshold) continue

                val gx = gradX[idx]
                val gy = gradY[idx]
                val angle = atan2(gy.toDouble(), gx.toDouble())
                // Matches Python: edge_dir = angle + pi/2, dir_mod = edge_dir % pi
                val edgeDir = angle + PI / 2
                val dirMod =
                    ((edgeDir % PI.toDouble()) + PI.toDouble()) %
                        PI.toDouble() // proper modulo for negative values
                val isHorizontal = dirMod < PI / 4 || dirMod > 3 * PI / 4
                val isVertical = !isHorizontal

                // Angle-only classification (for fallback)
                if (isHorizontal) {
                    hEdgesAngleOnly.add(Triple(ex.toFloat(), ey.toFloat(), mag))
                }
                if (isVertical) {
                    vEdgesAngleOnly.add(Triple(ex.toFloat(), ey.toFloat(), mag))
                }

                // Orientation-aware spatial filter (matches Python <= / >= boundaries)
                val dx = ex.toFloat() - searchCx
                val dy = ey.toFloat() - searchCy
                // Python uses h_spatial / v_spatial booleans based on corner orientation
                // h: horizontal edge pixels must be on the expected side
                // v: vertical edge pixels must be on the expected side
                val hSpatial: Boolean
                val vSpatial: Boolean
                when (cornerName) {
                    "LL" -> {
                        // h_edge_side = "above" → h pixels at y <= search_cy
                        // v_edge_side = "right" → v pixels at x >= search_cx
                        hSpatial = dy <= 0f
                        vSpatial = dx >= 0f
                    }
                    "UL" -> {
                        // h_edge_side = "below" → h pixels at y >= search_cy
                        // v_edge_side = "right" → v pixels at x >= search_cx
                        hSpatial = dy >= 0f
                        vSpatial = dx >= 0f
                    }
                    "UR" -> {
                        // h_edge_side = "below" → h pixels at y >= search_cy
                        // v_edge_side = "left" → v pixels at x <= search_cx
                        hSpatial = dy >= 0f
                        vSpatial = dx <= 0f
                    }
                    "LR" -> {
                        // h_edge_side = "above" → h pixels at y <= search_cy
                        // v_edge_side = "left" → v pixels at x <= search_cx
                        hSpatial = dy <= 0f
                        vSpatial = dx <= 0f
                    }
                    else -> {
                        hSpatial = true
                        vSpatial = true
                    }
                }

                // Combined: horizontal_mask = h_angle & h_spatial
                val isHorizontalOriented = isHorizontal && hSpatial
                val isVerticalOriented = isVertical && vSpatial

                if (isHorizontalOriented)
                    hEdgesOriented.add(Triple(ex.toFloat(), ey.toFloat(), mag))
                if (isVerticalOriented) vEdgesOriented.add(Triple(ex.toFloat(), ey.toFloat(), mag))
            }
        }

        // Try orientation-aware filtering first, fall back to angle-only if too few lines
        var lines = mutableListOf<FloatArray>()
        if (hEdgesOriented.size >= 3 && vEdgesOriented.size >= 3) {
            val hLine = fitWeightedLine(hEdgesOriented)
            val vLine = fitWeightedLine(vEdgesOriented)
            if (hLine != null) lines.add(hLine)
            if (vLine != null) lines.add(vLine)
        }

        // Fallback: if orientation-aware produced < 2 lines, try angle-only
        // Matches Python: "Fallback: angle-only filtering if orientation-aware was too aggressive"
        if (lines.size < 2 && hEdgesAngleOnly.size >= 3 && vEdgesAngleOnly.size >= 3) {
            lines.clear()
            val hLine = fitWeightedLine(hEdgesAngleOnly)
            val vLine = fitWeightedLine(vEdgesAngleOnly)
            if (hLine != null) lines.add(hLine)
            if (vLine != null) lines.add(vLine)
        }

        if (lines.size < 2) return null

        // Sort by linearity (descending), take best two (matches Python)
        lines.sortByDescending { it[3] }
        val bestTwo = lines.take(2)

        // Intersect lines
        val intersection = intersectLines(bestTwo[0], bestTwo[1]) ?: return null
        val (ix, iy) = intersection

        // Validate
        if (ix < 0 || iy < 0 || ix >= imgW || iy >= imgH) return null
        if (abs(ix - searchCx) > searchRadius || abs(iy - searchCy) > searchRadius) return null
        if (!useProjection) {
            val maxShift = searchRadius * maxShiftRatio
            if (abs(ix - nnCx) > maxShift || abs(iy - nnCy) > maxShift) return null
        }

        return Triple(ix, iy, 1.0f)
    }

    /**
     * Fit a weighted least-squares line (ax + by + c = 0, a²+b²=1) to a set of weighted points.
     * Returns (a, b, c, linearity) or null if fitting fails. Linearity = ratio of larger eigenvalue
     * to smaller (higher = more linear).
     */
    private fun fitWeightedLine(points: List<Triple<Float, Float, Float>>): FloatArray? {
        if (points.size < 3) return null

        val totalWeight = points.sumOf { it.third.toDouble() }.toFloat()
        if (totalWeight < 1e-6f) return null

        val mx = points.sumOf { (it.first * it.third).toDouble() }.toFloat() / totalWeight
        val my = points.sumOf { (it.second * it.third).toDouble() }.toFloat() / totalWeight

        var covXX = 0f
        var covXY = 0f
        var covYY = 0f
        for ((x, y, w) in points) {
            val dx = x - mx
            val dy = y - my
            covXX += w * dx * dx
            covXY += w * dx * dy
            covYY += w * dy * dy
        }
        covXX /= totalWeight
        covXY /= totalWeight
        covYY /= totalWeight

        // Compute eigenvectors of the 2x2 covariance matrix
        val trace = covXX + covYY
        val det = covXX * covYY - covXY * covXY
        if (trace == 0f) return null

        val discriminant = trace * trace - 4 * det
        val eigenVal = (trace - sqrt(maxOf(0f, discriminant))).toFloat() / 2f

        var a = covXY
        var b = eigenVal - covXX
        val len = sqrt(a * a + b * b)
        if (len < 1e-6f) return null
        a /= len
        b /= len
        val c = -(a * mx + b * my)

        val linearity =
            if (abs(det) > 1e-10f)
                maxOf(eigenVal.coerceAtLeast(0.001f), trace / 2f - eigenVal) /
                    maxOf(abs(det).sqrt(), 0.001f)
            else 1000f

        return floatArrayOf(a, b, c, linearity)
    }

    /** Intersect two lines in ax+by+c=0 form. Returns (x, y) or null if lines are parallel. */
    private fun intersectLines(line1: FloatArray, line2: FloatArray): Pair<Float, Float>? {
        val (a1, b1, c1) = Triple(line1[0], line1[1], line1[2])
        val (a2, b2, c2) = Triple(line2[0], line2[1], line2[2])
        val det = a1 * b2 - a2 * b1
        if (abs(det) < 1e-6f) return null
        val x = (b1 * c2 - b2 * c1) / det
        val y = (a2 * c1 - a1 * c2) / det
        return Pair(x, y)
    }

    // -----------------------------------------------------------------------
    // Image preprocessing
    // -----------------------------------------------------------------------

    /** Convert image to grayscale array. */
    private fun toGrayscaleArray(image: BufferedImage): FloatArray {
        val w = image.width
        val h = image.height
        val gray = FloatArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val rgb = image.getRGB(x, y)
                val r = (rgb shr 16) and 0xFF
                val g = (rgb shr 8) and 0xFF
                val b = rgb and 0xFF
                gray[y * w + x] = 0.299f * r + 0.587f * g + 0.114f * b
            }
        }
        return gray
    }

    /** Compute Sobel gradients from grayscale image. */
    private fun computeSobelGradients(
        gray: FloatArray,
        imgW: Int,
        imgH: Int,
    ): Triple<FloatArray, FloatArray, FloatArray> {
        val gradX = FloatArray(imgW * imgH)
        val gradY = FloatArray(imgW * imgH)
        val gradMag = FloatArray(imgW * imgH)

        for (y in 1 until imgH - 1) {
            for (x in 1 until imgW - 1) {
                val gx =
                    -gray[(y - 1) * imgW + (x - 1)] -
                        2f * gray[y * imgW + (x - 1)] -
                        gray[(y + 1) * imgW + (x - 1)] +
                        gray[(y - 1) * imgW + (x + 1)] +
                        2f * gray[y * imgW + (x + 1)] +
                        gray[(y + 1) * imgW + (x + 1)]
                val gy =
                    -gray[(y - 1) * imgW + (x - 1)] -
                        2f * gray[(y - 1) * imgW + x] -
                        gray[(y - 1) * imgW + (x + 1)] +
                        gray[(y + 1) * imgW + (x - 1)] +
                        2f * gray[(y + 1) * imgW + x] +
                        gray[(y + 1) * imgW + (x + 1)]
                val idx = y * imgW + x
                gradX[idx] = gx
                gradY[idx] = gy
                gradMag[idx] = sqrt(gx * gx + gy * gy)
            }
        }
        return Triple(gradX, gradY, gradMag)
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
        detectionMode: DetectionMode = DetectionMode.HYBRID,
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

    private fun Float.formatWarp(): String =
        if (this == Float.POSITIVE_INFINITY) "inf" else "%.3f".format(this)

    private fun Float.sqrt(): Float = sqrt(this)

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

    /** Mutable keypoint for two-pass CV rescue refinement */
    private data class MutableKeypoint(
        var name: String,
        var x: Float,
        var y: Float,
        var visibility: Float,
        var nnX: Float? = null,
        var nnY: Float? = null,
        var nnVis: Float? = null,
        var projX: Float? = null,
        var projY: Float? = null,
    )

    private data class PoseResultExt(
        val poseResult: YoloPoseService.PoseResult,
        val detection: YoloDetectionService.Detection,
    )

    companion object {
        private const val VIS_THRESH_DEDUP = 0.25f
        private const val NEIGHBOR_VIS_THRESHOLD = 0.5f
        private const val RESCUE_EDGE_THRESHOLD = 50f
        private const val RESCUE_MAX_SHIFT_RATIO = 0.3f
        private const val PI = kotlin.math.PI.toFloat()

        /** Corner geometry for orientation-aware edge search */
        private val CORNER_ORIENTATION =
            mapOf(
                "LL" to mapOf("h" to "above", "v" to "right"),
                "UL" to mapOf("h" to "below", "v" to "right"),
                "UR" to mapOf("h" to "below", "v" to "left"),
                "LR" to mapOf("h" to "above", "v" to "left"),
            )

        /** Corner adjacency: which neighbors share edges */
        private val CORNER_NEIGHBORS =
            mapOf(
                "LL" to mapOf("h" to "LR", "v" to "UL"),
                "UL" to mapOf("h" to "UR", "v" to "LL"),
                "UR" to mapOf("h" to "UL", "v" to "LR"),
                "LR" to mapOf("h" to "LL", "v" to "UR"),
            )
    }
}
