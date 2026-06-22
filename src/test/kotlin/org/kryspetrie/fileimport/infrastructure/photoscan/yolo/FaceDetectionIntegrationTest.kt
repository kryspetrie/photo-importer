package org.kryspetrie.fileimport.infrastructure.photoscan.yolo

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.kryspetrie.fileimport.domain.port.DetectedFace
import org.kryspetrie.fileimport.infrastructure.adapter.AwtProcessedImage
import org.kryspetrie.fileimport.infrastructure.adapter.ClasspathModelResourceAdapter
import org.kryspetrie.fileimport.infrastructure.photoscan.FaceDetectionService

/**
 * Integration tests for face detection using real ONNX model inference.
 *
 * Reference coordinates were captured from [FaceDetectionGoldTest] running the
 * YOLO12n face detection model with confThreshold=0.50, iouThreshold=0.45.
 *
 * Tolerance is ±30px for bounding-box coordinates (out of ~2688px width) and
 * ±0.05 for confidence scores, accounting for floating-point non-determinism
 * across different hardware and ONNX Runtime backends.
 *
 * To regenerate references after model changes, run [FaceDetectionGoldTest].
 */
@DisplayName("Face Detection Integration")
@EnabledIf("sessionAvailable")
class FaceDetectionIntegrationTest {

    companion object {
        private const val COORDINATE_TOLERANCE = 30f // pixels
        private const val CONFIDENCE_TOLERANCE = 0.05f

        private var faceService: FaceDetectionService? = null
        private var available = false

        @JvmStatic
        @BeforeAll
        fun setUp() {
            val adapter = ClasspathModelResourceAdapter()
            available = adapter.isFaceDetectionModelAvailable()
            if (available) {
                faceService = FaceDetectionService(adapter)
            }
        }

        @JvmStatic
        fun sessionAvailable(): Boolean = available
    }

    // ─── Reference data for faces-01.jpg (2688×2016) ───
    // Captured from YOLO12n face model, confThreshold=0.50, iouThreshold=0.45

    private val faces01References = listOf(
        // Face 0: conf=0.852 — rightmost person, large face
        DetectedFace(x1 = 2265.73f, y1 = 1044.25f, x2 = 2557.86f, y2 = 1397.19f, confidence = 0.852f),
        // Face 1: conf=0.833 — left side, medium face
        DetectedFace(x1 = 168.79f, y1 = 860.33f, x2 = 316.77f, y2 = 1050.91f, confidence = 0.833f),
        // Face 2: conf=0.817 — center-left, small face
        DetectedFace(x1 = 418.12f, y1 = 673.89f, x2 = 543.89f, y2 = 817.90f, confidence = 0.817f),
        // Face 3: conf=0.815 — center-right, medium face
        DetectedFace(x1 = 1841.98f, y1 = 725.72f, x2 = 1996.92f, y2 = 893.45f, confidence = 0.815f),
        // Face 4: conf=0.809 — center-left, small face
        DetectedFace(x1 = 652.61f, y1 = 540.43f, x2 = 759.81f, y2 = 659.63f, confidence = 0.809f),
        // Face 5: conf=0.804 — center, small face (upper area)
        DetectedFace(x1 = 1251.56f, y1 = 399.57f, x2 = 1337.92f, y2 = 515.52f, confidence = 0.804f),
        // Face 6: conf=0.794 — center-right, small face
        DetectedFace(x1 = 1614.12f, y1 = 565.62f, x2 = 1728.20f, y2 = 711.11f, confidence = 0.794f),
        // Face 7: conf=0.775 — center, small face (adjacent to Face 6)
        DetectedFace(x1 = 1519.74f, y1 = 458.23f, x2 = 1624.84f, y2 = 586.29f, confidence = 0.775f),
    )

    // ─── Reference data for faces-02.jpg (1512×2016) ───
    // Captured from YOLO12n face model, confThreshold=0.50, iouThreshold=0.45

    private val faces02References = listOf(
        // Face 0: conf=0.898 — left, large face
        DetectedFace(x1 = 125.12f, y1 = 959.77f, x2 = 499.92f, y2 = 1457.89f, confidence = 0.898f),
        // Face 1: conf=0.895 — right, large face
        DetectedFace(x1 = 945.76f, y1 = 637.08f, x2 = 1364.34f, y2 = 1153.84f, confidence = 0.895f),
        // Face 2: conf=0.876 — center, medium face
        DetectedFace(x1 = 523.05f, y1 = 999.75f, x2 = 816.75f, y2 = 1387.77f, confidence = 0.876f),
        // Face 3: conf=0.837 — center-top, small face
        DetectedFace(x1 = 627.79f, y1 = 508.66f, x2 = 841.43f, y2 = 769.21f, confidence = 0.837f),
    )

    @Nested
    @DisplayName("faces-01.jpg — 8 faces")
    inner class Faces01Test {

        @Test
        @DisplayName("detects correct number of faces")
        fun detectsCorrectCount() {
            val image = loadImage("faces-01") ?: return
            val results = faceService!!.detectFaces(AwtProcessedImage(image))

            assertThat(results).hasSize(faces01References.size)
        }

        @Test
        @DisplayName("each detected face matches reference bounding box within tolerance")
        fun matchesReferenceBoundingBoxes() {
            val image = loadImage("faces-01") ?: return
            val results = faceService!!.detectFaces(AwtProcessedImage(image))

            assertThat(results).hasSize(faces01References.size)

            for ((i, ref) in faces01References.withIndex()) {
                val det = results[i]
                assertThat(det.x1)
                    .`as`("Face $i x1")
                    .isCloseTo(ref.x1, within(COORDINATE_TOLERANCE))
                assertThat(det.y1)
                    .`as`("Face $i y1")
                    .isCloseTo(ref.y1, within(COORDINATE_TOLERANCE))
                assertThat(det.x2)
                    .`as`("Face $i x2")
                    .isCloseTo(ref.x2, within(COORDINATE_TOLERANCE))
                assertThat(det.y2)
                    .`as`("Face $i y2")
                    .isCloseTo(ref.y2, within(COORDINATE_TOLERANCE))
                assertThat(det.confidence)
                    .`as`("Face $i confidence")
                    .isCloseTo(ref.confidence, within(CONFIDENCE_TOLERANCE))
            }
        }

        @Test
        @DisplayName("all detections have positive-area bounding boxes")
        fun allBoundingBoxesHavePositiveArea() {
            val image = loadImage("faces-01") ?: return
            val results = faceService!!.detectFaces(AwtProcessedImage(image))

            for ((i, det) in results.withIndex()) {
                assertThat(det.x2 - det.x1)
                    .`as`("Face $i width")
                    .isGreaterThan(0f)
                assertThat(det.y2 - det.y1)
                    .`as`("Face $i height")
                    .isGreaterThan(0f)
            }
        }

        @Test
        @DisplayName("all detections are within image bounds")
        fun allDetectionsWithinImageBounds() {
            val image = loadImage("faces-01") ?: return
            val results = faceService!!.detectFaces(AwtProcessedImage(image))

            for ((i, det) in results.withIndex()) {
                assertThat(det.x1)
                    .`as`("Face $i x1 >= 0")
                    .isGreaterThanOrEqualTo(0f)
                assertThat(det.y1)
                    .`as`("Face $i y1 >= 0")
                    .isGreaterThanOrEqualTo(0f)
                assertThat(det.x2)
                    .`as`("Face $i x2 <= image width")
                    .isLessThanOrEqualTo(image.width.toFloat())
                assertThat(det.y2)
                    .`as`("Face $i y2 <= image height")
                    .isLessThanOrEqualTo(image.height.toFloat())
            }
        }

        @Test
        @DisplayName("detections are sorted by descending confidence")
        fun sortedByDescendingConfidence() {
            val image = loadImage("faces-01") ?: return
            val results = faceService!!.detectFaces(AwtProcessedImage(image))

            for (i in 0 until results.size - 1) {
                assertThat(results[i].confidence)
                    .`as`("Face $i confidence >= Face ${i + 1} confidence")
                    .isGreaterThanOrEqualTo(results[i + 1].confidence)
            }
        }

        @Test
        @DisplayName("all confidence scores are above threshold")
        fun allConfidencesAboveThreshold() {
            val image = loadImage("faces-01") ?: return
            val results = faceService!!.detectFaces(AwtProcessedImage(image))

            for ((i, det) in results.withIndex()) {
                assertThat(det.confidence)
                    .`as`("Face $i confidence above 0.5 threshold")
                    .isGreaterThanOrEqualTo(0.5f)
            }
        }
    }

    @Nested
    @DisplayName("faces-02.jpg — 4 faces")
    inner class Faces02Test {

        @Test
        @DisplayName("detects correct number of faces")
        fun detectsCorrectCount() {
            val image = loadImage("faces-02") ?: return
            val results = faceService!!.detectFaces(AwtProcessedImage(image))

            assertThat(results).hasSize(faces02References.size)
        }

        @Test
        @DisplayName("each detected face matches reference bounding box within tolerance")
        fun matchesReferenceBoundingBoxes() {
            val image = loadImage("faces-02") ?: return
            val results = faceService!!.detectFaces(AwtProcessedImage(image))

            assertThat(results).hasSize(faces02References.size)

            for ((i, ref) in faces02References.withIndex()) {
                val det = results[i]
                assertThat(det.x1)
                    .`as`("Face $i x1")
                    .isCloseTo(ref.x1, within(COORDINATE_TOLERANCE))
                assertThat(det.y1)
                    .`as`("Face $i y1")
                    .isCloseTo(ref.y1, within(COORDINATE_TOLERANCE))
                assertThat(det.x2)
                    .`as`("Face $i x2")
                    .isCloseTo(ref.x2, within(COORDINATE_TOLERANCE))
                assertThat(det.y2)
                    .`as`("Face $i y2")
                    .isCloseTo(ref.y2, within(COORDINATE_TOLERANCE))
                assertThat(det.confidence)
                    .`as`("Face $i confidence")
                    .isCloseTo(ref.confidence, within(CONFIDENCE_TOLERANCE))
            }
        }

        @Test
        @DisplayName("all detections have positive-area bounding boxes")
        fun allBoundingBoxesHavePositiveArea() {
            val image = loadImage("faces-02") ?: return
            val results = faceService!!.detectFaces(AwtProcessedImage(image))

            for ((i, det) in results.withIndex()) {
                assertThat(det.x2 - det.x1)
                    .`as`("Face $i width")
                    .isGreaterThan(0f)
                assertThat(det.y2 - det.y1)
                    .`as`("Face $i height")
                    .isGreaterThan(0f)
            }
        }

        @Test
        @DisplayName("all detections are within image bounds")
        fun allDetectionsWithinImageBounds() {
            val image = loadImage("faces-02") ?: return
            val results = faceService!!.detectFaces(AwtProcessedImage(image))

            for ((i, det) in results.withIndex()) {
                assertThat(det.x1).`as`("Face $i x1 >= 0").isGreaterThanOrEqualTo(0f)
                assertThat(det.y1).`as`("Face $i y1 >= 0").isGreaterThanOrEqualTo(0f)
                assertThat(det.x2).`as`("Face $i x2 <= width").isLessThanOrEqualTo(image.width.toFloat())
                assertThat(det.y2).`as`("Face $i y2 <= height").isLessThanOrEqualTo(image.height.toFloat())
            }
        }

        @Test
        @DisplayName("detections are sorted by descending confidence")
        fun sortedByDescendingConfidence() {
            val image = loadImage("faces-02") ?: return
            val results = faceService!!.detectFaces(AwtProcessedImage(image))

            for (i in 0 until results.size - 1) {
                assertThat(results[i].confidence)
                    .`as`("Face $i confidence >= Face ${i + 1} confidence")
                    .isGreaterThanOrEqualTo(results[i + 1].confidence)
            }
        }
    }

    private fun loadImage(name: String): BufferedImage? {
        val stream = javaClass.classLoader.getResourceAsStream("org/kryspetrie/fileimport/application/$name.jpg")
        if (stream != null) {
            return stream.use { ImageIO.read(it) }
        }
        val file = File("src/test/resources/org/kryspetrie/fileimport/application/$name.jpg")
        if (file.exists()) {
            return ImageIO.read(file)
        }
        println("WARN: Test image $name not found")
        return null
    }
}