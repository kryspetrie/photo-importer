package org.kryspetrie.fileimport.infrastructure.photoscan

import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.domain.model.PhotoCorner
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.math.sqrt

class AccuracyCheckTest {

    private fun loadImage(path: String): BufferedImage {
        val stream = javaClass.classLoader.getResourceAsStream(path)!!
        return ImageIO.read(stream)
    }

    private fun sortCorners(corners: List<RectangleDetector.Point>): List<RectangleDetector.Point> {
        if (corners.size != 4) return corners
        val sortedBySum = corners.sortedBy { it.x + it.y }
        val tlIdx = corners.indexOf(sortedBySum.first())
        val brIdx = corners.indexOf(sortedBySum.last())
        val remaining = (0..3).filter { it != tlIdx && it != brIdx }
        val trIdx = if (corners[remaining[0]].x - corners[remaining[0]].y < corners[remaining[1]].x - corners[remaining[1]].y) remaining[1] else remaining[0]
        val blIdx = if (corners[remaining[0]].x - corners[remaining[0]].y < corners[remaining[1]].x - corners[remaining[1]].y) remaining[0] else remaining[1]
        return listOf(corners[tlIdx], corners[trIdx], corners[brIdx], corners[blIdx])
    }

    private fun sortCornersGT(corners: List<PhotoCorner>): List<PhotoCorner> {
        if (corners.size != 4) return corners
        val sortedBySum = corners.sortedBy { it.x + it.y }
        val tlIdx = corners.indexOf(sortedBySum.first())
        val brIdx = corners.indexOf(sortedBySum.last())
        val remaining = (0..3).filter { it != tlIdx && it != brIdx }
        val trIdx = if (corners[remaining[0]].x - corners[remaining[0]].y < corners[remaining[1]].x - corners[remaining[1]].y) remaining[1] else remaining[0]
        val blIdx = if (corners[remaining[0]].x - corners[remaining[0]].y < corners[remaining[1]].x - corners[remaining[1]].y) remaining[0] else remaining[1]
        return listOf(corners[tlIdx], corners[trIdx], corners[brIdx], corners[blIdx])
    }

    @Test
    fun `check accuracy with different params`() {
        val image = loadImage("org/kryspetrie/fileimport/application/photo-scan-02.jpg")
        
        val groundTruth = listOf(
            listOf(PhotoCorner(270f, 358f), PhotoCorner(1864f, 358f), PhotoCorner(1864f, 1452f), PhotoCorner(270f, 1452f)),
            listOf(PhotoCorner(256f, 1560f), PhotoCorner(2104f, 1560f), PhotoCorner(2104f, 3814f), PhotoCorner(256f, 3814f)),
            listOf(PhotoCorner(2226f, 634f), PhotoCorner(3700f, 634f), PhotoCorner(3700f, 2510f), PhotoCorner(2226f, 2510f))
        )
        
        // Test multi-epsilon vs single epsilon
        val configs = listOf(
            "single_eps3" to RectangleDetector(minArea = 100000, minQuadRatio = 0.3f, gamma = 1.4),
            "multi_eps" to RectangleDetector(minArea = 100000, minQuadRatio = 0.3f, gamma = 1.4)
        )
        
        for ((name, detector) in configs) {
            val results = detector.detectRectangles(image, expectedCount = 3)
            println("\n${"=".repeat(60)}")
            println("Config: $name - detected ${results.size} quads")
            
            var totalError = 0
            var cornersWithin10 = 0
            var matchedGTs = 0
            
            for ((gtIdx, gt) in groundTruth.withIndex()) {
                val gtSorted = sortCornersGT(gt)
                val names = listOf("TL", "TR", "BR", "BL")
                
                val bestMatch = results.minByOrNull { det ->
                    val detSorted = sortCorners(det.corners)
                    var total = 0.0
                    for (i in 0..3) {
                        val dx = detSorted[i].x - gtSorted[i].x
                        val dy = detSorted[i].y - gtSorted[i].y
                        total += sqrt((dx*dx + dy*dy).toDouble())
                    }
                    total
                }
                
                if (bestMatch != null) {
                    matchedGTs++
                    val detSorted = sortCorners(bestMatch.corners)
                    for (i in 0..3) {
                        val dx = detSorted[i].x - gtSorted[i].x
                        val dy = detSorted[i].y - gtSorted[i].y
                        val dist = sqrt((dx*dx + dy*dy).toDouble())
                        totalError += dist.toInt()
                        if (dist <= 10) cornersWithin10++
                    }
                }
            }
            
            println("Matched: $matchedGTs/3 GTs, Total error: ${totalError}px, Corners within 10px: $cornersWithin10/12")
            if (matchedGTs == 3 && cornersWithin10 == 12) {
                println("*** PERFECT! ***")
            }
        }
    }
}
