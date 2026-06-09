package org.kryspetrie.fileimport.infrastructure.photoscan.yolo

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.awt.image.BufferedImage
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * YOLO corner regression model inference — refines corner positions for sub-pixel accuracy.
 *
 * Matches the Python `refine_corners_regression()` and `run_corner_regression()` functions:
 * 1. Crop a 320×320 region around each approximate corner
 * 2. Run the corner regression model (finds tight bbox + single keypoint)
 * 3. Pick the detection closest to the expected corner position
 * 4. Optionally iterate: re-crop around the refined position
 *
 * Enhancement over Python: uses neighbor-anchored projection for the reference point when
 * high-confidence neighbor corners are available. This projects a better search reference from the
 * two adjacent corners (which share edges with this corner) when the pose model's own position may
 * be unreliable. This prevents corner regression from picking a detection from an adjacent photo
 * when two photos share an edge region.
 *
 * @param env ONNX Runtime environment (shared)
 * @param session ONNX Runtime session for the corner regression model
 */
/**
 * Corner adjacency: which two other corners share edges with this one. Each corner shares a
 * horizontal edge with one neighbor and a vertical edge with the other. The neighbor that shares
 * the horizontal edge provides the y-coordinate projection; the neighbor sharing the vertical edge
 * provides the x-coordinate projection.
 *
 * For example, for LR: LL shares the bottom edge (projects y), and UR shares the right edge
 * (projects x). If LL is at (100, 1974) and UR is at (750, 100), the projected center for LR is
 * (750, 1974).
 */
private val CORNER_NEIGHBORS =
    mapOf(
        "LL" to mapOf("h" to "LR", "v" to "UL"), // bottom edge with LR, left edge with UL
        "UL" to mapOf("h" to "UR", "v" to "LL"), // top edge with UR, left edge with LL
        "UR" to mapOf("h" to "UL", "v" to "LR"), // top edge with UL, right edge with LR
        "LR" to mapOf("h" to "LL", "v" to "UR"), // bottom edge with LL, right edge with UR
    )

/** Minimum visibility for a neighbor corner to be used for projection in rescue */
private const val NEIGHBOR_VIS_THRESHOLD = 0.5f

/**
 * Minimum visibility for a neighbor corner to be used for projection in corner regression reference
 * point calculation. Lower than the rescue threshold because we just need an approximate position
 * to select the right detection, not a precise refinement position.
 */
private const val CORNER_REGRESS_NEIGHBOR_VIS_THRESHOLD = 0.15f

class YoloCornerRegressionService(
    private val env: OrtEnvironment,
    private val session: OrtSession,
) {
    /** A single corner regression detection. */
    data class CornerDetection(
        val confidence: Float,
        val keypointX: Float,
        val keypointY: Float,
        val keypointVisibility: Float,
        val boxX1: Float,
        val boxY1: Float,
        val boxX2: Float,
        val boxY2: Float,
    )

    /** Result of corner regression refinement for one corner. */
    data class CornerRefinementResult(
        val cornerName: String,
        val refinedX: Float,
        val refinedY: Float,
        val regressionConfidence: Float?,
    )

    /**
     * Run the corner regression model on a 320×320 crop.
     *
     * @param crop Crop image (ideally 320×320, will be resized if not)
     * @param confThreshold Minimum detection confidence (default 0.05, lower for search)
     * @param imgSize Model input size (default 320)
     * @return List of corner detections sorted by confidence descending
     */
    fun runCornerRegression(
        crop: BufferedImage,
        confThreshold: Float = 0.05f,
        imgSize: Int = CORNER_REGRESSION_SIZE,
    ): List<CornerDetection> {
        // Use manual bilinear interpolation (matches Python's PIL Image.BILINEAR exactly)
        val preprocessed = YoloPreprocessing.preprocessCrop(crop, imgSize)

        // Run inference
        val inputName = session.inputNames.iterator().next()
        val inputTensor =
            OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(preprocessed.flatArray),
                preprocessed.shape,
            )
        val results = session.run(mapOf(inputName to inputTensor))
        val output = results[0].value as Array<Array<FloatArray>>

        // Parse output: [1, 300, 9] — x1, y1, x2, y2, conf, cls, kp_x, kp_y, kp_vis
        val scaleX = preprocessed.cropWidth.toFloat() / imgSize
        val scaleY = preprocessed.cropHeight.toFloat() / imgSize
        val rows = output[0]

        val detections = mutableListOf<CornerDetection>()
        for (row in rows) {
            val conf = row[4]
            if (conf < confThreshold) continue

            detections.add(
                CornerDetection(
                    confidence = conf,
                    keypointX = row[6] * scaleX,
                    keypointY = row[7] * scaleY,
                    keypointVisibility = row[8],
                    boxX1 = row[0] * scaleX,
                    boxY1 = row[1] * scaleY,
                    boxX2 = row[2] * scaleX,
                    boxY2 = row[3] * scaleY,
                )
            )
        }

        detections.sortByDescending { it.confidence }
        return detections
    }

    /**
     * Extract a square crop centered on (x, y) from the source image.
     *
     * If the crop would extend beyond the image boundary, it is shifted to stay within bounds. The
     * returned crop is always cropSize × cropSize, padded with grey (114,114,114) if needed.
     *
     * @return Crop image and (offsetX, offsetY) — the top-left corner of the crop in original image
     *   coords
     */
    fun cornerCrop(
        image: BufferedImage,
        x: Float,
        y: Float,
        cropSize: Int = CORNER_CROP_SIZE_MIN,
    ): Pair<BufferedImage, Pair<Int, Int>> {
        val origW = image.width
        val origH = image.height
        val half = cropSize / 2

        var x1 = x.roundToInt() - half
        var y1 = y.roundToInt() - half

        // Clamp to image bounds
        x1 = max(0, x1)
        y1 = max(0, y1)
        if (x1 + cropSize > origW) x1 = origW - cropSize
        if (y1 + cropSize > origH) y1 = origH - cropSize
        x1 = max(0, x1)
        y1 = max(0, y1)
        val x2 = min(origW, x1 + cropSize)
        val y2 = min(origH, y1 + cropSize)

        val crop = image.getSubimage(x1, y1, x2 - x1, y2 - y1)

        // Pad if needed
        if (crop.width < cropSize || crop.height < cropSize) {
            val padded = BufferedImage(cropSize, cropSize, BufferedImage.TYPE_INT_RGB)
            val g = padded.createGraphics()
            g.paint = java.awt.Color(114, 114, 114)
            g.fillRect(0, 0, cropSize, cropSize)
            g.drawImage(crop, 0, 0, null)
            g.dispose()
            return Pair(padded, Pair(x1, y1))
        }

        return Pair(crop, Pair(x1, y1))
    }

    /**
     * Refine corner positions for all corners in a pose result using the corner regression model.
     *
     * Matches the Python `refine_corners_regression()` function.
     *
     * @param image Full source image
     * @param poseResult The pose detection result to refine
     * @param iterations Number of refinement iterations (default 2)
     * @param confThreshold Confidence threshold for detection (default 0.3)
     * @param cropSize Crop size for corner regression (default 320)
     * @param maxShiftRatio Max allowed shift as ratio of cropSize (default 0.3)
     * @return Map of corner names to their refined positions
     */
    fun refineCorners(
        image: BufferedImage,
        poseResult: YoloPoseService.PoseResult,
        iterations: Int = 2,
        confThreshold: Float = 0.3f,
        cropSize: Int = CORNER_CROP_SIZE_MIN,
        maxShiftRatio: Float = 0.3f,
    ): Map<String, CornerRefinementResult> {
        val kpsByName = poseResult.keypoints.associateBy { it.name }
        val box = poseResult.cropBox
        val maxShift = (cropSize * maxShiftRatio).toFloat()

        // Use pose keypoints as starting points, fall back to bbox corners
        val approxCorners = mutableMapOf<String, Pair<Float, Float>>()
        for (name in YoloPoseService.KEYPOINT_NAMES) {
            val kp = kpsByName[name]
            if (kp != null && kp.visibility >= 0.1f) {
                approxCorners[name] = Pair(kp.x, kp.y)
            } else {
                // Fall back to bbox corner
                val bx =
                    when (name) {
                        "UL",
                        "LL" -> box.x1.toFloat()
                        else -> box.x2.toFloat()
                    }
                val by =
                    when (name) {
                        "UL",
                        "UR" -> box.y1.toFloat()
                        else -> box.y2.toFloat()
                    }
                approxCorners[name] = Pair(bx, by)
            }
        }

        // Compute neighbor-anchored projection for each corner.
        // When neighboring corners have high visibility, they can project a
        // better search center than the pose model's own position. The neighbor
        // that shares the horizontal edge provides y, and the one sharing the
        // vertical edge provides x. This is critical when the pose model places
        // a corner on the wrong photo (e.g., UR at y=1031 near an adjacent
        // photo's LR at y=1025, instead of the true UR at y=1072).
        val projectedRefs =
            projectFromNeighbors(poseResult.keypoints, CORNER_REGRESS_NEIGHBOR_VIS_THRESHOLD)

        val refinedCorners = mutableMapOf<String, CornerRefinementResult>()
        val minConfAccept = 0.7f
        val searchConf = 0.05f
        val maxExtraIters = 3

        for ((cornerName, approxPos) in approxCorners) {
            var ax = approxPos.first
            var ay = approxPos.second
            val origX = ax
            val origY = ay

            // Use neighbor-anchored projection as a secondary reference point
            // for detecting when the pose model has placed this corner on the
            // wrong photo. When two photos share a boundary, the pose model may
            // place a high-visibility corner on the adjacent photo. Neighbor
            // projection (using the corners that share edges with this one) can
            // give a reference position that's closer to the true corner.
            //
            // Strategy: find the closest detection to the pose model position
            // AND the closest detection to the projected position. If they're
            // different detections, prefer the one with higher confidence.
            // If they're the same, use it. This handles the case where the
            // pose model places the corner on the wrong photo but with high
            // confidence — the projected reference guides us to the right photo.
            val proj = projectedRefs[cornerName]
            val hasProjection = proj != null && proj.confidence > 0f
            val projNonNull = proj?.takeIf { it.confidence > 0f }
            val projRefX = projNonNull?.projX
            val projRefY = projNonNull?.projY

            var bestResult: Triple<Float, Float, Float>? = null

            @Suppress("UNUSED_VARIABLE")
            for (iterIdx in 0 until (iterations + maxExtraIters)) {
                val (crop, offsets) = cornerCrop(image, ax, ay, cropSize)
                val offsetX = offsets.first
                val offsetY = offsets.second

                val detections =
                    runCornerRegression(
                        crop,
                        confThreshold = searchConf,
                        imgSize = CORNER_REGRESSION_SIZE,
                    )

                if (detections.isEmpty()) break

                // Find two candidates:
                // 1. Closest detection to the pose model position (original behavior)
                // 2. Closest detection to the neighbor-projected position (if available)
                // If they differ, prefer higher confidence.
                val cropOrigX = origX - offsetX
                val cropOrigY = origY - offsetY

                var bestDetByPose: CornerDetection? = null
                var bestDistByPose = Float.MAX_VALUE
                var bestDetByProj: CornerDetection? = null
                var bestDistByProj = Float.MAX_VALUE

                for (det in detections) {
                    if (det.keypointVisibility < 0.3f) continue
                    val dx = det.keypointX - cropOrigX
                    val dy = det.keypointY - cropOrigY
                    val distToPose = sqrt(dx * dx + dy * dy)
                    if (distToPose < bestDistByPose) {
                        bestDistByPose = distToPose
                        bestDetByPose = det
                    }
                    if (hasProjection && projRefX != null && projRefY != null) {
                        val px = det.keypointX - (projRefX - offsetX)
                        val py = det.keypointY - (projRefY - offsetY)
                        val distToProj = sqrt(px * px + py * py)
                        if (distToProj < bestDistByProj) {
                            bestDistByProj = distToProj
                            bestDetByProj = det
                        }
                    }
                }

                // Pick the best detection: if projection is available and selects
                // a different detection than the pose position, prefer the one
                // with higher confidence. This handles cases where the pose model
                // places the corner on an adjacent photo (wrong but high vis).
                val bestDet: CornerDetection? =
                    when {
                        bestDetByProj != null &&
                            bestDetByPose != null &&
                            bestDetByProj != bestDetByPose -> {
                            // Different detections: prefer higher confidence
                            if (bestDetByProj.confidence >= bestDetByPose.confidence) {
                                bestDetByProj
                            } else {
                                bestDetByPose
                            }
                        }
                        else -> bestDetByPose ?: bestDetByProj
                    }

                if (bestDet == null) break

                // Map keypoint back to original image coordinates
                val newX = bestDet.keypointX + offsetX
                val newY = bestDet.keypointY + offsetY

                // Reject if refinement moved too far from the original pose position.
                // When neighbor projection selected a different detection than the pose
                // position, the detected point can be further from the pose position.
                // Allow additional shift proportional to the distance between the projected
                // reference and the pose position.
                if (hasProjection && projRefX != null && projRefY != null) {
                    val projMaxShift =
                        maxShift + maxOf(abs(projRefX - origX), abs(projRefY - origY))
                    if (abs(newX - origX) > projMaxShift || abs(newY - origY) > projMaxShift) break
                } else {
                    if (abs(newX - origX) > maxShift || abs(newY - origY) > maxShift) break
                }

                val conf = bestDet.confidence
                if (bestResult == null || conf > bestResult.third) {
                    bestResult = Triple(newX, newY, conf)
                }

                // Accept if confident enough
                if (conf >= minConfAccept) {
                    ax = newX
                    ay = newY
                    break
                }

                // Low confidence: re-crop around this detection
                ax = newX
                ay = newY
            }

            if (bestResult != null) {
                refinedCorners[cornerName] =
                    CornerRefinementResult(
                        cornerName = cornerName,
                        refinedX = bestResult.first,
                        refinedY = bestResult.second,
                        regressionConfidence = bestResult.third,
                    )
            }
        }

        return refinedCorners
    }

    /**
     * Project corner positions from high-visibility neighbors.
     *
     * Each corner shares edges with two neighbors:
     * - The horizontal-edge neighbor constrains the y-coordinate
     * - The vertical-edge neighbor constrains the x-coordinate
     *
     * For example, for UR: UL shares the top edge (projects y) and LR shares the right edge
     * (projects x). If UL is at (793, 1065) and LR is at (1410, 1947), the projected UR reference
     * is (1410, 1065).
     *
     * Only uses neighbors with visibility >= NEIGHBOR_VIS_THRESHOLD.
     *
     * @param keypoints The pose model's keypoint results
     * @return Map of corner names to their projected reference positions
     */
    private fun projectFromNeighbors(
        keypoints: List<YoloPoseService.Keypoint>,
        visThreshold: Float = NEIGHBOR_VIS_THRESHOLD,
    ): Map<String, ProjectionRef> {
        val kpByName = keypoints.associateBy { it.name }
        val result = mutableMapOf<String, ProjectionRef>()

        for (cornerName in YoloPoseService.KEYPOINT_NAMES) {
            val neighbors = CORNER_NEIGHBORS[cornerName] ?: continue
            val hNeighborName = neighbors["h"]!!
            val vNeighborName = neighbors["v"]!!

            val kp = kpByName[cornerName]
            var projX: Float? = null
            var projY: Float? = null
            var projYFromH = false
            var projXFromV = false

            // Horizontal-edge neighbor provides y-coordinate projection
            val hKp = kpByName[hNeighborName]
            if (hKp != null && hKp.visibility >= visThreshold) {
                projY = hKp.y
                projYFromH = true
            }

            // Vertical-edge neighbor provides x-coordinate projection
            val vKp = kpByName[vNeighborName]
            if (vKp != null && vKp.visibility >= visThreshold) {
                projX = vKp.x
                projXFromV = true
            }

            if (!projYFromH && !projXFromV) {
                result[cornerName] = ProjectionRef(null, null, 0f, "none")
                continue
            }

            // If only one neighbor contributed, use the pose model position for the missing axis
            if (projX == null) projX = kp?.x
            if (projY == null) projY = kp?.y

            val contributions = (if (projYFromH) 1 else 0) + (if (projXFromV) 1 else 0)
            val confidence = contributions / 2f

            val projectedAxis =
                when {
                    projYFromH && projXFromV -> "both"
                    projYFromH -> "y"
                    else -> "x"
                }

            result[cornerName] = ProjectionRef(projX, projY, confidence, projectedAxis)
        }

        return result
    }

    /** Result of neighbor-anchored projection for a single corner. */
    data class ProjectionRef(
        /** Projected x-coordinate (from vertical-edge neighbor), or null if no reliable neighbor */
        val projX: Float?,
        /**
         * Projected y-coordinate (from horizontal-edge neighbor), or null if no reliable neighbor
         */
        val projY: Float?,
        /** Confidence: 0.5 for one neighbor, 1.0 for both */
        val confidence: Float,
        /** Which axes were projected: "both", "x", "y", or "none" */
        val projectedAxis: String,
    )

    companion object {
        const val CORNER_REGRESSION_SIZE = 320
        const val CORNER_CROP_SIZE_MIN = 320
    }
}
