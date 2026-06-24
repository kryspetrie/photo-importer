@file:Suppress("MaxLineLength", "ReturnCount")

package org.kryspetrie.fileimport.infrastructure.photoscan.yolo

import java.awt.image.BufferedImage
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import org.kryspetrie.fileimport.infrastructure.logging.AppLogger

/**
 * Extracts the corner rescue/refinement logic from YoloPhotoScanPipeline.
 *
 * Provides CV-based Sobel edge detection, strip search, and 2D orientation-aware
 * line intersection to recover low-visibility corners that the pose model misses.
 */
class CornerRescueService(
    private val appLogger: AppLogger? = null,
) {
    // -----------------------------------------------------------------------
    // Data classes
    // -----------------------------------------------------------------------

    data class ProjectionResult(
        val projX: Float?,
        val projY: Float?,
        val confidence: Float,
        val projectedAxis: String,
    )

    /** Mutable keypoint for two-pass CV rescue refinement */
    data class MutableKeypoint(
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

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------

    companion object {
        internal const val NEIGHBOR_VIS_THRESHOLD = 0.5f
        internal const val RESCUE_EDGE_THRESHOLD = 50f
        internal const val RESCUE_MAX_SHIFT_RATIO = 0.3f
        internal const val PI = kotlin.math.PI.toFloat()

        /** Corner geometry for orientation-aware edge search */
        internal val CORNER_ORIENTATION =
            mapOf(
                "LL" to mapOf("h" to "above", "v" to "right"),
                "UL" to mapOf("h" to "below", "v" to "right"),
                "UR" to mapOf("h" to "below", "v" to "left"),
                "LR" to mapOf("h" to "above", "v" to "left"),
            )

        /** Corner adjacency: which neighbors share edges */
        internal val CORNER_NEIGHBORS =
            mapOf(
                "LL" to mapOf("h" to "LR", "v" to "UL"),
                "UL" to mapOf("h" to "UR", "v" to "LL"),
                "UR" to mapOf("h" to "UL", "v" to "LR"),
                "LR" to mapOf("h" to "LL", "v" to "UR"),
            )
    }

    // -----------------------------------------------------------------------
    // Rescue entry point
    // -----------------------------------------------------------------------

    internal fun rescueLowVisCorners(
        image: BufferedImage,
        results: MutableList<YoloPoseService.PoseResult>,
        config: YoloPhotoScanPipeline.PipelineConfig,
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
        val gray = ImageProcessingUtils.toGrayscaleArray(image)
        val (gradX, gradY, gradMag) = ImageProcessingUtils.computeSobelGradients(gray, imgW, imgH)

        // Two-pass refinement — CRITICAL: carry improvements across passes
        // Python modifies kps in-place: kp["x"], kp["visibility"], kp["nn_x"] etc.
        // We must convert to mutable keypoints ONCE per photo, then carry through
        // both passes, writing back to results after each pass so pass 1 sees
        // pass 0 improvements.
        val rescueVisThreshold = 0.7f

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
                    if (mkp.visibility >= rescueVisThreshold) continue
                    val cx = mkp.x
                    val cy = mkp.y
                    val vis = mkp.visibility
                    val cornerName = mkp.name
                    // --- Enhancement: Neighbor-anchored projection ---
                    // This uses the CURRENT (possibly pass-0-updated) positions of neighbors
                    appLogger?.info(
                        "[RESCUE] $cornerName pass=$passNum vis=${String.format(Locale.US, "%.2f", mkp.visibility)} pos=(${mkp.x.toInt()},${mkp.y.toInt()})"
                    )
                    val proj = projectFromNeighbors(mutKps, kpIdx)
                    val projX = proj.projX
                    val projY = proj.projY
                    val projConf = proj.confidence
                    val projectedAxis = proj.projectedAxis

                    val useProjection = projConf >= NEIGHBOR_VIS_THRESHOLD
                    appLogger?.info(
                        "[RESCUE] $cornerName projectedAxis=$projectedAxis projConf=${String.format(Locale.US, "%.2f", projConf)} useProjection=$useProjection proj=(${projX?.toInt()},${projY?.toInt()})"
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

    // -----------------------------------------------------------------------
    // Neighbor projection
    // -----------------------------------------------------------------------

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
    internal fun projectFromNeighbors(kps: List<MutableKeypoint>, cornerIdx: Int): ProjectionResult {
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
    internal fun stripSearchCorner(
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

            val xCenter = nnOtherAxis?.roundToInt() ?: (imgW / 2)
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
                "[STRIP] $cornerName projAxis=y projVal=${projVal.toInt()}: search1 X range=[$xLo..$xHi], peakIdx=$bestX, foundX=${foundX.toInt()}, profileMax=${profile.maxOrNull()?.toInt()}, profileMedian=${ImageProcessingUtils.median(profile).toInt()}"
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

            val yCenter = nnOtherAxis?.roundToInt() ?: (imgH / 2)
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
    internal fun findScipyLikePeak(profile: FloatArray): Int? {
        if (profile.isEmpty()) return null

        // Compute thresholds matching scipy.signal.find_peaks
        val minHeight = ImageProcessingUtils.median(profile) * 2f
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
    internal fun computeProminence(profile: FloatArray, peakIdx: Int): Float {
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
    internal fun refineCorner2D(
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
        val lines = mutableListOf<FloatArray>()
        if (hEdgesOriented.size >= 3 && vEdgesOriented.size >= 3) {
            val hLine = ImageProcessingUtils.fitWeightedLine(hEdgesOriented)
            val vLine = ImageProcessingUtils.fitWeightedLine(vEdgesOriented)
            if (hLine != null) lines.add(hLine)
            if (vLine != null) lines.add(vLine)
        }

        // Fallback: if orientation-aware produced < 2 lines, try angle-only
        // Matches Python: "Fallback: angle-only filtering if orientation-aware was too aggressive"
        if (lines.size < 2 && hEdgesAngleOnly.size >= 3 && vEdgesAngleOnly.size >= 3) {
            lines.clear()
            val hLine = ImageProcessingUtils.fitWeightedLine(hEdgesAngleOnly)
            val vLine = ImageProcessingUtils.fitWeightedLine(vEdgesAngleOnly)
            if (hLine != null) lines.add(hLine)
            if (vLine != null) lines.add(vLine)
        }

        if (lines.size < 2) return null

        // Sort by linearity (descending), take best two (matches Python)
        lines.sortByDescending { it[3] }
        val bestTwo = lines.take(2)

        // Intersect lines
        val intersection = ImageProcessingUtils.intersectLines(bestTwo[0], bestTwo[1]) ?: return null
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

}
