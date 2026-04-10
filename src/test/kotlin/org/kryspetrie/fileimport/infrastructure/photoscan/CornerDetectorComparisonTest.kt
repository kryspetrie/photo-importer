package org.kryspetrie.fileimport.infrastructure.photoscan

import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.math.hypot
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.kryspetrie.fileimport.domain.model.DetectedPhoto
import org.kryspetrie.fileimport.domain.model.PhotoCorner

/** Comprehensive accuracy test comparing ALL corner detection approaches. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CornerDetectorComparisonTest {

  private lateinit var img02: BufferedImage
  private lateinit var originalDetector: HybridCornerDetector
  private lateinit var edgeDetector: HybridEdgeCornerDetector
  private lateinit var integratedDetector: IntegratedHybridCornerDetector
  private lateinit var edgeFollowingDetector: EdgeFollowingCornerDetector
  private lateinit var regionGuidedDetector: RegionGuidedCornerDetector
  private lateinit var edgeLineDetector: EdgeLineIntersectionCornerDetector
  private lateinit var improvedEdgeLineDetector: ImprovedEdgeLineCornerDetector
  private lateinit var consensusDetector: ConsensusCornerDetector
  private lateinit var refinedDetector: RefinedEdgeLineCornerDetector

  @BeforeAll
  fun setup() {
    img02 = loadImage("org/kryspetrie/fileimport/application/photo-scan-02.jpg")
    originalDetector = HybridCornerDetector(RectangleDetector())
    edgeDetector = HybridEdgeCornerDetector(RectangleDetector())
    integratedDetector = IntegratedHybridCornerDetector(RectangleDetector())
    edgeFollowingDetector = EdgeFollowingCornerDetector(RectangleDetector())
    regionGuidedDetector = RegionGuidedCornerDetector(RectangleDetector())
    edgeLineDetector = EdgeLineIntersectionCornerDetector(RectangleDetector())
    improvedEdgeLineDetector = ImprovedEdgeLineCornerDetector(RectangleDetector())
    consensusDetector = ConsensusCornerDetector(RectangleDetector())
    refinedDetector = RefinedEdgeLineCornerDetector(RectangleDetector())
  }

  private fun loadImage(path: String): BufferedImage =
      ImageIO.read(javaClass.classLoader.getResourceAsStream(path))!!

  private fun groundTruth02(): List<List<PhotoCorner>> =
      listOf(
          listOf(
              PhotoCorner(270f, 358f),
              PhotoCorner(1864f, 358f),
              PhotoCorner(1864f, 1452f),
              PhotoCorner(270f, 1452f)),
          listOf(
              PhotoCorner(256f, 1560f),
              PhotoCorner(2104f, 1560f),
              PhotoCorner(2104f, 3814f),
              PhotoCorner(256f, 3814f)),
          listOf(
              PhotoCorner(2226f, 634f),
              PhotoCorner(3700f, 634f),
              PhotoCorner(3700f, 2510f),
              PhotoCorner(2226f, 2510f)))

  private fun getPhotoCorners(photo: DetectedPhoto): List<PhotoCorner> =
      listOf(photo.topLeft, photo.topRight, photo.bottomRight, photo.bottomLeft)

  private fun sortCorners(corners: List<PhotoCorner>): List<PhotoCorner> {
    if (corners.size != 4) return corners
    val sorted = corners.sortedBy { it.x + it.y }
    val topLeft = sorted[0]
    val bottomRight = sorted[3]
    val remaining = listOf(sorted[1], sorted[2]).sortedBy { it.y - it.x }
    return listOf(topLeft, remaining[0], bottomRight, remaining[1])
  }

  private fun calculateCornerErrors(
      detected: List<PhotoCorner>,
      groundTruth: List<PhotoCorner>
  ): List<Double> {
    val sortedDetected = sortCorners(detected)
    val sortedGT = sortCorners(groundTruth)

    return sortedDetected.mapIndexed { i, det ->
      hypot((det.x - sortedGT[i].x).toDouble(), (det.y - sortedGT[i].y).toDouble())
    }
  }

  private fun matchToGroundTruth(
      detections: List<Pair<String, DetectedPhoto>>,
      groundTruth: List<List<PhotoCorner>>
  ): List<Triple<String, List<PhotoCorner>, List<PhotoCorner>>> {
    val matches = mutableListOf<Triple<String, List<PhotoCorner>, List<PhotoCorner>>>()
    val usedGT = mutableSetOf<Int>()

    for ((name, photo) in detections) {
      var bestGTIdx = -1
      var bestDist = Double.MAX_VALUE

      val detCorners = getPhotoCorners(photo)
      val detCentroidX = detCorners.map { it.x }.average()
      val detCentroidY = detCorners.map { it.y }.average()

      for ((gtIdx, gt) in groundTruth.withIndex()) {
        if (gtIdx in usedGT) continue
        val gtCentroidX = gt.map { it.x }.average()
        val gtCentroidY = gt.map { it.y }.average()
        val dist = hypot(detCentroidX - gtCentroidX, detCentroidY - gtCentroidY)

        if (dist < bestDist) {
          bestDist = dist
          bestGTIdx = gtIdx
        }
      }

      if (bestGTIdx >= 0 && bestDist < 500) {
        matches.add(Triple(name, detCorners, groundTruth[bestGTIdx]))
        usedGT.add(bestGTIdx)
      }
    }

    return matches
  }

  @Test
  fun `compare all four detectors on photo-scan-02`() {
    println("\n=== Photo Scan 02: All Four Detectors ===")
    val gt = groundTruth02()

    // Run all detectors
    originalDetector.targetPhotoCount = 3
    edgeDetector.targetPhotoCount = 3
    integratedDetector.targetPhotoCount = 3
    edgeFollowingDetector.targetPhotoCount = 3
    regionGuidedDetector.targetPhotoCount = 3

    val originalResults = originalDetector.detectPhotos(img02)
    val edgeResults = edgeDetector.detectPhotos(img02)
    val integratedResults = integratedDetector.detectPhotos(img02)
    val edgeFollowingResults = edgeFollowingDetector.detectPhotos(img02)
    val regionGuidedResults = regionGuidedDetector.detectPhotos(img02)
    val edgeLineResults = edgeLineDetector.detectPhotos(img02)

    println("\n--- Detected Corners ---")
    for ((name, results) in
        listOf(
            "Original" to originalResults,
            "Edge" to edgeResults,
            "Integrated" to integratedResults,
            "EdgeFollowing" to edgeFollowingResults,
            "RegionGuided" to regionGuidedResults,
            "EdgeLine" to edgeLineResults)) {
      println("\n$name: ${results.size} photos")
      for ((i, photo) in results.withIndex()) {
        val corners = getPhotoCorners(photo)
        val sorted = sortCorners(corners)
        println("  Photo[$i]: ${sorted.map { "(${it.x.toInt()},${it.y.toInt()})" }.joinToString()}")
      }
    }

    // Compare all methods
    val allDetections = mutableListOf<Pair<String, DetectedPhoto>>()
    originalResults.forEachIndexed { i, p -> allDetections.add("Original[$i]" to p) }
    edgeResults.forEachIndexed { i, p -> allDetections.add("Edge[$i]" to p) }
    integratedResults.forEachIndexed { i, p -> allDetections.add("Integrated[$i]" to p) }
    edgeFollowingResults.forEachIndexed { i, p -> allDetections.add("EdgeFollow[$i]" to p) }
    regionGuidedResults.forEachIndexed { i, p -> allDetections.add("Region[$i]" to p) }
    edgeLineResults.forEachIndexed { i, p -> allDetections.add("EdgeLine[$i]" to p) }

    val matches = matchToGroundTruth(allDetections, gt)

    println("\n=== Matched to Ground Truth ===")
    for ((name, detected, truth) in matches) {
      val errors = calculateCornerErrors(detected, truth)
      val avgError = errors.average()
      val maxError = errors.maxOrNull() ?: 0.0
      println("$name: avg=${"%.1f".format(avgError)}px, max=${"%.1f".format(maxError)}px")
      println("  Errors: ${errors.map { "%.1f".format(it) }.joinToString()}")
    }
  }

  @Test
  fun `per-detector summary for all methods`() {
    println("\n=== Per-Detector Summary ===")

    val gt = groundTruth02()
    originalDetector.targetPhotoCount = 3
    edgeDetector.targetPhotoCount = 3
    integratedDetector.targetPhotoCount = 3
    edgeFollowingDetector.targetPhotoCount = 3
    regionGuidedDetector.targetPhotoCount = 3
    edgeLineDetector.targetPhotoCount = 3
    improvedEdgeLineDetector.targetPhotoCount = 3
    consensusDetector.targetPhotoCount = 3
    refinedDetector.targetPhotoCount = 3

    val results =
        mapOf(
            "Original" to originalDetector.detectPhotos(img02),
            "Edge" to edgeDetector.detectPhotos(img02),
            "Integrated" to integratedDetector.detectPhotos(img02),
            "EdgeFollowing" to edgeFollowingDetector.detectPhotos(img02),
            "RegionGuided" to regionGuidedDetector.detectPhotos(img02),
            "EdgeLine" to edgeLineDetector.detectPhotos(img02),
            "ImprovedEdgeLine" to improvedEdgeLineDetector.detectPhotos(img02),
            "Consensus" to consensusDetector.detectPhotos(img02),
            "Refined" to refinedDetector.detectPhotos(img02))

    for ((name, photos) in results) {
      val detections = photos.mapIndexed { i, p -> "$name[$i]" to p }
      val matches = matchToGroundTruth(detections, gt)

      val allErrors = mutableListOf<Double>()
      for ((_, detected, truth) in matches) {
        allErrors.addAll(calculateCornerErrors(detected, truth))
      }

      if (allErrors.isNotEmpty()) {
        println("\n$name Detector:")
        println("  Matched: ${matches.size}/3")
        println("  Corners: ${allErrors.size}")
        println("  Avg error: ${"%.1f".format(allErrors.average())}px")
        println("  Max error: ${"%.1f".format(allErrors.maxOrNull()!!)}px")
        println("  Within 20px: ${allErrors.count { it <= 20 }}/${allErrors.size}")
        println("  Within 50px: ${allErrors.count { it <= 50 }}/${allErrors.size}")
      } else {
        println("\n$name Detector: NO MATCHES")
      }
    }
  }

  @Test
  fun `GT1 worst case analysis - all methods`() {
    println("\n=== GT[1] Worst Case: Detailed Analysis ===")
    println("GT[1] corners: TL(256,1560), TR(2104,1560), BR(2104,3814), BL(256,3814)")

    val gt1 = groundTruth02()[1]

    originalDetector.targetPhotoCount = 3
    edgeDetector.targetPhotoCount = 3
    integratedDetector.targetPhotoCount = 3
    edgeFollowingDetector.targetPhotoCount = 3
    regionGuidedDetector.targetPhotoCount = 3
    edgeLineDetector.targetPhotoCount = 3
    improvedEdgeLineDetector.targetPhotoCount = 3
    consensusDetector.targetPhotoCount = 3
    refinedDetector.targetPhotoCount = 3

    val results =
        mapOf(
            "Original" to originalDetector.detectPhotos(img02),
            "Edge" to edgeDetector.detectPhotos(img02),
            "Integrated" to integratedDetector.detectPhotos(img02),
            "EdgeFollowing" to edgeFollowingDetector.detectPhotos(img02),
            "RegionGuided" to regionGuidedDetector.detectPhotos(img02),
            "EdgeLine" to edgeLineDetector.detectPhotos(img02),
            "ImprovedEdgeLine" to improvedEdgeLineDetector.detectPhotos(img02),
            "Consensus" to consensusDetector.detectPhotos(img02),
            "Refined" to refinedDetector.detectPhotos(img02))

    for ((name, photos) in results) {
      // Find closest to GT1 centroid
      val gt1CentroidX = 1180.0
      val gt1CentroidY = 2687.0

      val best =
          photos.minByOrNull {
            val corners = getPhotoCorners(it)
            val cx = corners.map { c -> c.x }.average()
            val cy = corners.map { c -> c.y }.average()
            hypot(cx - gt1CentroidX, cy - gt1CentroidY)
          }

      if (best != null) {
        val corners = getPhotoCorners(best)
        val errors = calculateCornerErrors(corners, gt1)
        val sorted = sortCorners(corners)

        println("\n$name:")
        println("  Corners: ${sorted.map { "(${it.x.toInt()},${it.y.toInt()})" }.joinToString()}")
        println(
            "  Errors: TL=${"%.1f".format(errors[0])}px, TR=${"%.1f".format(errors[1])}px, BR=${"%.1f".format(errors[2])}px, BL=${"%.1f".format(errors[3])}px")
        println(
            "  Avg: ${"%.1f".format(errors.average())}px, Max: ${"%.1f".format(errors.maxOrNull()!!)}px")
      }
    }
  }
}
