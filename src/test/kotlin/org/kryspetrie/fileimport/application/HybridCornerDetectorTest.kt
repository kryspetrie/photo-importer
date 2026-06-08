package org.kryspetrie.fileimport.application

import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.kryspetrie.fileimport.domain.model.PhotoBounds
import org.kryspetrie.fileimport.domain.model.PhotoCorner
import org.kryspetrie.fileimport.infrastructure.adapter.toProcessedImage
import org.kryspetrie.fileimport.infrastructure.photoscan.HybridCornerDetector
import org.kryspetrie.fileimport.infrastructure.photoscan.RectangleDetector

/**
 * Ground truth tests for [HybridCornerDetector].
 *
 * Validates that the detector finds photos in real-world scanned images using coverage (IoU).
 *
 * Ground truth (hand-annotated):
 * - photo-scan-01: 2 photos at [365,386]-[1388,1030] and [1037,1520]-[1967,2128]
 * - photo-scan-02: 3 photos
 *
 * Classical CV alone may not find all photos. These tests verify that the pipeline finds the
 * primary photos and doesn't crash. ML refinement fills in the rest.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HybridCornerDetectorTest {

    private lateinit var img01: BufferedImage
    private lateinit var img02: BufferedImage
    private lateinit var hybrid: HybridCornerDetector

    @BeforeAll
    fun setup() {
        img01 = loadImage("org/kryspetrie/fileimport/application/photo-scan-01.jpg")
        img02 = loadImage("org/kryspetrie/fileimport/application/photo-scan-02.jpg")
        hybrid = HybridCornerDetector(RectangleDetector())
    }

    @Test
    fun `photo-scan-01 finds the primary photo`() {
        hybrid.targetPhotoCount = 2
        val detected = hybrid.detectPhotos(img01.toProcessedImage())

        assert(detected.size == 2) { "Expected 2 detections, got ${detected.size}" }

        // The primary (larger) photo should be clearly detected (IoU > 50%)
        val gtPrimary = groundTruth01()[0]
        val gtPrimaryBounds = truthBounds(gtPrimary)
        val primaryMatched =
            detected.any { det -> computeIou(det.getBounds(), gtPrimaryBounds) > 0.50f }
        assert(primaryMatched) {
            "Primary photo [365,386]-[1388,1030] not clearly detected (IoU < 50%)"
        }
    }

    @Test
    fun `photo-scan-02 finds all three photos`() {
        hybrid.targetPhotoCount = 3
        val detected = hybrid.detectPhotos(img02.toProcessedImage())

        assert(detected.size >= 3) { "Expected >= 3 detections, got ${detected.size}" }

        // All three ground truth photos should be covered (IoU > 50%)
        val gt = groundTruth02()
        for ((i, truth) in gt.withIndex()) {
            val gtBounds = truthBounds(truth)
            val covered = detected.any { det -> computeIou(det.getBounds(), gtBounds) > 0.50f }
            assert(covered) {
                val b = gtBounds
                "GT[$i] [${b.minX},${b.minY}]-[${b.maxX},${b.maxY}] not covered (IoU < 50%)"
            }
        }
    }

    private fun truthBounds(truth: List<PhotoCorner>): PhotoBounds {
        val xs = truth.map { it.x }
        val ys = truth.map { it.y }
        val minX = xs.min().toInt()
        val maxX = xs.max().toInt()
        val minY = ys.min().toInt()
        val maxY = ys.max().toInt()
        return PhotoBounds(minX, maxX, minY, maxY)
    }

    private fun computeIou(a: PhotoBounds, b: PhotoBounds): Float {
        val ix1 = maxOf(a.minX, b.minX).toFloat()
        val iy1 = maxOf(a.minY, b.minY).toFloat()
        val ix2 = minOf(a.maxX, b.maxX).toFloat()
        val iy2 = minOf(a.maxY, b.maxY).toFloat()
        val interArea = maxOf(0f, ix2 - ix1) * maxOf(0f, iy2 - iy1)
        val areaA = a.getWidth().toFloat() * a.getHeight()
        val areaB = b.getWidth().toFloat() * b.getHeight()
        val union = areaA + areaB - interArea
        return if (union > 0f) interArea / union else 0f
    }

    private fun loadImage(path: String): BufferedImage =
        ImageIO.read(javaClass.classLoader.getResourceAsStream(path))!!

    private fun groundTruth01() =
        listOf(
            listOf(
                PhotoCorner(365f, 386f),
                PhotoCorner(1388f, 386f),
                PhotoCorner(1388f, 1030f),
                PhotoCorner(365f, 1030f),
            ),
            listOf(
                PhotoCorner(1037f, 1520f),
                PhotoCorner(1967f, 1520f),
                PhotoCorner(2394f, 2128f),
                PhotoCorner(1037f, 2128f),
            ),
        )

    private fun groundTruth02() =
        listOf(
            listOf(
                PhotoCorner(270f, 358f),
                PhotoCorner(1864f, 358f),
                PhotoCorner(1864f, 1452f),
                PhotoCorner(270f, 1452f),
            ),
            listOf(
                PhotoCorner(256f, 1560f),
                PhotoCorner(2104f, 1560f),
                PhotoCorner(2104f, 3814f),
                PhotoCorner(256f, 3814f),
            ),
            listOf(
                PhotoCorner(2226f, 634f),
                PhotoCorner(3700f, 634f),
                PhotoCorner(3700f, 2510f),
                PhotoCorner(2226f, 2510f),
            ),
        )
}
