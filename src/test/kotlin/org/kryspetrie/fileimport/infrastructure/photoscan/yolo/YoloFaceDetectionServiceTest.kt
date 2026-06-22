package org.kryspetrie.fileimport.infrastructure.photoscan.yolo

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.kryspetrie.fileimport.infrastructure.adapter.ClasspathModelResourceAdapter

/**
 * Unit tests for [YoloFaceDetectionService.parseFaceOutput].
 *
 * Uses a real [YoloFaceDetectionService] instance created from the detection model on the classpath.
 * Only [parseFaceOutput] is tested — not inference — so the model choice doesn't matter; we only
 * need a valid ONNX session to construct the service.
 *
 * Output format summary:
 * - **Transposed**: [1, 20, N] — rows are features (cx, cy, w, h, conf, cls, 5×3 keypoints)
 * - **NMS-enabled**: [1, N, 20] — rows are detections, columns are features
 */
@DisplayName("YoloFaceDetectionService")
@EnabledIf("sessionAvailable")
class YoloFaceDetectionServiceTest {

    companion object {
        private var service: YoloFaceDetectionService? = null
        private var available = false

        @JvmStatic
        @BeforeAll
        fun setUp() {
            val adapter = ClasspathModelResourceAdapter()
            available = adapter.isModelAvailable()
            if (available) {
                val env = OrtEnvironment.getEnvironment()
                val opts = OrtSession.SessionOptions()
                val session = env.createSession(adapter.loadDetectionModel(), opts)
                service = YoloFaceDetectionService(env, session)
            }
        }

        @JvmStatic
        fun sessionAvailable(): Boolean = available
    }

    private fun parseFaceOutput(
        output: Array<Array<FloatArray>>,
        ratio: Float,
        padW: Int,
        padH: Int,
        origW: Int,
        origH: Int,
        confThreshold: Float,
        iouThreshold: Float,
    ): List<YoloFaceDetectionService.FaceDetection> {
        return service!!.parseFaceOutput(
            output, ratio, padW, padH, origW, origH, confThreshold, iouThreshold,
        )
    }

    @Nested
    @DisplayName("parseFaceOutput — empty and edge cases")
    inner class EmptyAndEdgeCases {

        @Test
        @DisplayName("returns empty list for empty output array")
        fun returnsEmptyForEmptyOutput() {
            val output = emptyArray<Array<FloatArray>>()
            val result = parseFaceOutput(
                output, 1f, 0, 0, 640, 480, 0.5f, 0.45f,
            )
            assertThat(result).isEmpty()
        }

        @Test
        @DisplayName("returns empty list when all detections are below confidence threshold")
        fun returnsEmptyWhenBelowThreshold() {
            val output = arrayOf(
                arrayOf(
                    floatArrayOf(320f), floatArrayOf(240f), floatArrayOf(100f), floatArrayOf(100f),
                    floatArrayOf(0.3f), floatArrayOf(0f),
                    floatArrayOf(0f), floatArrayOf(0f), floatArrayOf(0f),
                    floatArrayOf(0f), floatArrayOf(0f), floatArrayOf(0f),
                    floatArrayOf(0f), floatArrayOf(0f), floatArrayOf(0f),
                    floatArrayOf(0f), floatArrayOf(0f), floatArrayOf(0f),
                    floatArrayOf(0f), floatArrayOf(0f), floatArrayOf(0f),
                )
            )
            val result = parseFaceOutput(output, 1f, 0, 0, 640, 480, 0.5f, 0.45f)
            assertThat(result).isEmpty()
        }
    }

    @Nested
    @DisplayName("parseFaceOutput — transposed format [1, 20, N]")
    inner class TransposedFormat {

        @Test
        @DisplayName("parses single detection in transposed format")
        fun parsesSingleDetection() {
            val output = arrayOf(
                arrayOf(
                    floatArrayOf(320f), floatArrayOf(240f), floatArrayOf(100f), floatArrayOf(120f),
                    floatArrayOf(0.9f), floatArrayOf(0f),
                    floatArrayOf(300f), floatArrayOf(225f), floatArrayOf(0.95f),
                    floatArrayOf(340f), floatArrayOf(225f), floatArrayOf(0.93f),
                    floatArrayOf(320f), floatArrayOf(245f), floatArrayOf(0.97f),
                    floatArrayOf(305f), floatArrayOf(260f), floatArrayOf(0.88f),
                    floatArrayOf(335f), floatArrayOf(260f), floatArrayOf(0.86f),
                )
            )
            val result = parseFaceOutput(output, 1f, 0, 0, 640, 480, 0.5f, 0.45f)
            assertThat(result).hasSize(1)
            assertThat(result[0].x1).isCloseTo(270f, within(0.1f))
            assertThat(result[0].y1).isCloseTo(180f, within(0.1f))
            assertThat(result[0].x2).isCloseTo(370f, within(0.1f))
            assertThat(result[0].y2).isCloseTo(300f, within(0.1f))
            assertThat(result[0].confidence).isCloseTo(0.9f, within(0.01f))
            assertThat(result[0].keypoints).hasSize(5)
        }

        @Test
        @DisplayName("parses multiple detections in transposed format")
        fun parsesMultipleDetections() {
            val output = arrayOf(
                arrayOf(
                    floatArrayOf(200f, 450f), floatArrayOf(150f, 350f),
                    floatArrayOf(80f, 90f), floatArrayOf(90f, 100f),
                    floatArrayOf(0.8f, 0.7f), floatArrayOf(0f, 0f),
                    floatArrayOf(0f, 0f), floatArrayOf(0f, 0f), floatArrayOf(0f, 0f),
                    floatArrayOf(0f, 0f), floatArrayOf(0f, 0f), floatArrayOf(0f, 0f),
                    floatArrayOf(0f, 0f), floatArrayOf(0f, 0f), floatArrayOf(0f, 0f),
                    floatArrayOf(0f, 0f), floatArrayOf(0f, 0f), floatArrayOf(0f, 0f),
                    floatArrayOf(0f, 0f), floatArrayOf(0f, 0f), floatArrayOf(0f, 0f),
                )
            )
            val result = parseFaceOutput(output, 1f, 0, 0, 640, 480, 0.5f, 0.45f)
            assertThat(result).hasSize(2)
        }

        @Test
        @DisplayName("applies ratio and padding in transposed format")
        fun appliesRatioAndPadding() {
            val output = arrayOf(
                arrayOf(
                    floatArrayOf(320f), floatArrayOf(240f), floatArrayOf(100f), floatArrayOf(120f),
                    floatArrayOf(0.9f), floatArrayOf(0f),
                    floatArrayOf(300f), floatArrayOf(225f), floatArrayOf(0.9f),
                    floatArrayOf(340f), floatArrayOf(225f), floatArrayOf(0.9f),
                    floatArrayOf(320f), floatArrayOf(245f), floatArrayOf(0.9f),
                    floatArrayOf(305f), floatArrayOf(260f), floatArrayOf(0.8f),
                    floatArrayOf(335f), floatArrayOf(260f), floatArrayOf(0.8f),
                )
            )
            val result = parseFaceOutput(output, 0.5f, 20, 10, 640, 480, 0.5f, 0.45f)
            assertThat(result).hasSize(1)
            assertThat(result[0].x2).isCloseTo(640f, within(1f)) // clamped from 700
            assertThat(result[0].y2).isCloseTo(480f, within(1f)) // clamped from 580
            assertThat(result[0].x1).isCloseTo(500f, within(1f))
            assertThat(result[0].y1).isCloseTo(340f, within(1f))
        }

        @Test
        @DisplayName("extracts keypoints in transposed format with coordinate transform")
        fun extractsKeypointsWithTransform() {
            val output = arrayOf(
                arrayOf(
                    floatArrayOf(320f), floatArrayOf(240f), floatArrayOf(100f), floatArrayOf(120f),
                    floatArrayOf(0.9f), floatArrayOf(0f),
                    floatArrayOf(100f), floatArrayOf(220f), floatArrayOf(0.95f),
                    floatArrayOf(200f), floatArrayOf(220f), floatArrayOf(0.93f),
                    floatArrayOf(150f), floatArrayOf(250f), floatArrayOf(0.97f),
                    floatArrayOf(120f), floatArrayOf(270f), floatArrayOf(0.88f),
                    floatArrayOf(180f), floatArrayOf(270f), floatArrayOf(0.86f),
                )
            )
            val result = parseFaceOutput(output, 2f, 10, 5, 640, 480, 0.5f, 0.45f)
            assertThat(result).hasSize(1)
            val kps = result[0].keypoints
            assertThat(kps).hasSize(5)
            // Keypoint 0 (left eye): x = (100 - 10) / 2 = 45, y = (220 - 5) / 2 = 107.5
            assertThat(kps[0].x).isCloseTo(45f, within(0.1f))
            assertThat(kps[0].y).isCloseTo(107.5f, within(0.1f))
            assertThat(kps[0].visibility).isCloseTo(0.95f, within(0.01f))
            // Keypoint 2 (nose): x = (150 - 10) / 2 = 70, y = (250 - 5) / 2 = 122.5
            assertThat(kps[2].x).isCloseTo(70f, within(0.1f))
            assertThat(kps[2].y).isCloseTo(122.5f, within(0.1f))
        }

        @Test
        @DisplayName("clamps coordinates to image bounds in transposed format")
        fun clampsCoordinatesToImageBounds() {
            val output = arrayOf(
                arrayOf(
                    floatArrayOf(5f), floatArrayOf(5f), floatArrayOf(20f), floatArrayOf(20f),
                    floatArrayOf(0.9f), floatArrayOf(0f),
                    floatArrayOf(0f), floatArrayOf(0f), floatArrayOf(0f),
                    floatArrayOf(0f), floatArrayOf(0f), floatArrayOf(0f),
                    floatArrayOf(0f), floatArrayOf(0f), floatArrayOf(0f),
                    floatArrayOf(0f), floatArrayOf(0f), floatArrayOf(0f),
                    floatArrayOf(0f), floatArrayOf(0f), floatArrayOf(0f),
                )
            )
            val result = parseFaceOutput(output, 1f, 0, 0, 640, 480, 0.5f, 0.45f)
            assertThat(result).hasSize(1)
            assertThat(result[0].x1).isGreaterThanOrEqualTo(0f)
            assertThat(result[0].y1).isGreaterThanOrEqualTo(0f)
            assertThat(result[0].x2).isLessThanOrEqualTo(640f)
            assertThat(result[0].y2).isLessThanOrEqualTo(480f)
        }
    }

    @Nested
    @DisplayName("parseFaceOutput — NMS-enabled format [1, N, 20]")
    inner class NmsEnabledFormat {

        @Test
        @DisplayName("parses single detection in NMS format")
        fun parsesSingleDetection() {
            val detection = floatArrayOf(
                270f, 180f, 370f, 300f, 0.9f, 0f,
                290f, 220f, 0.95f, 340f, 220f, 0.93f,
                315f, 245f, 0.97f, 295f, 265f, 0.88f, 330f, 265f, 0.86f,
            )
            val output = arrayOf(arrayOf(detection))
            val result = parseFaceOutput(output, 1f, 0, 0, 640, 480, 0.5f, 0.45f)
            assertThat(result).hasSize(1)
            assertThat(result[0].x1).isCloseTo(270f, within(0.1f))
            assertThat(result[0].y1).isCloseTo(180f, within(0.1f))
            assertThat(result[0].x2).isCloseTo(370f, within(0.1f))
            assertThat(result[0].y2).isCloseTo(300f, within(0.1f))
            assertThat(result[0].confidence).isCloseTo(0.9f, within(0.01f))
            assertThat(result[0].keypoints).hasSize(5)
        }

        @Test
        @DisplayName("parses multiple detections in NMS format")
        fun parsesMultipleDetections() {
            val det1 = floatArrayOf(
                100f, 80f, 200f, 180f, 0.85f, 0f,
                0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
            )
            val det2 = floatArrayOf(
                300f, 200f, 400f, 300f, 0.75f, 0f,
                0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
            )
            val output = arrayOf(arrayOf(det1, det2))
            val result = parseFaceOutput(output, 1f, 0, 0, 640, 480, 0.5f, 0.45f)
            assertThat(result).hasSize(2)
        }

        @Test
        @DisplayName("applies ratio and padding in NMS format")
        fun appliesRatioAndPadding() {
            val detection = floatArrayOf(
                270f, 180f, 370f, 300f, 0.9f, 0f,
                0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
            )
            val output = arrayOf(arrayOf(detection))
            val result = parseFaceOutput(output, 0.5f, 20, 10, 640, 480, 0.5f, 0.45f)
            assertThat(result).hasSize(1)
            assertThat(result[0].x1).isCloseTo(500f, within(1f))
            assertThat(result[0].y1).isCloseTo(340f, within(1f))
            assertThat(result[0].x2).isCloseTo(640f, within(1f)) // clamped from 700
            assertThat(result[0].y2).isCloseTo(480f, within(1f)) // clamped from 580
        }

        @Test
        @DisplayName("extracts keypoints in NMS format with coordinate transform")
        fun extractsKeypointsWithTransform() {
            val detection = floatArrayOf(
                270f, 180f, 370f, 300f, 0.9f, 0f,
                50f, 70f, 0.95f, 80f, 70f, 0.93f,
                65f, 85f, 0.97f, 55f, 95f, 0.88f, 75f, 95f, 0.86f,
            )
            val output = arrayOf(arrayOf(detection))
            val result = parseFaceOutput(output, 2f, 10, 5, 640, 480, 0.5f, 0.45f)
            assertThat(result).hasSize(1)
            val kps = result[0].keypoints
            assertThat(kps).hasSize(5)
            assertThat(kps[0].x).isCloseTo(20f, within(0.1f))
            assertThat(kps[0].y).isCloseTo(32.5f, within(0.1f))
            assertThat(kps[0].visibility).isCloseTo(0.95f, within(0.01f))
            assertThat(kps[2].x).isCloseTo(27.5f, within(0.1f))
            assertThat(kps[2].y).isCloseTo(40f, within(0.1f))
        }

        @Test
        @DisplayName("filters detections below confidence threshold in NMS format")
        fun filtersBelowThreshold() {
            val det = floatArrayOf(
                100f, 80f, 200f, 180f, 0.3f, 0f,
                0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
            )
            val output = arrayOf(arrayOf(det))
            val result = parseFaceOutput(output, 1f, 0, 0, 640, 480, 0.5f, 0.45f)
            assertThat(result).isEmpty()
        }
    }

    @Nested
    @DisplayName("parseFaceOutput — NMS suppression")
    inner class NmsSuppression {

        @Test
        @DisplayName("suppresses overlapping detections above IoU threshold")
        fun suppressesOverlappingDetections() {
            val det1 = floatArrayOf(
                100f, 80f, 200f, 180f, 0.95f, 0f,
                0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
            )
            val det2 = floatArrayOf(
                105f, 85f, 205f, 185f, 0.8f, 0f,
                0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
            )
            val output = arrayOf(arrayOf(det1, det2))
            val result = parseFaceOutput(output, 1f, 0, 0, 640, 480, 0.5f, 0.45f)
            assertThat(result).hasSize(1)
            assertThat(result[0].confidence).isCloseTo(0.95f, within(0.01f))
        }

        @Test
        @DisplayName("keeps non-overlapping detections")
        fun keepsNonOverlappingDetections() {
            val det1 = floatArrayOf(
                10f, 10f, 100f, 100f, 0.95f, 0f,
                0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
            )
            val det2 = floatArrayOf(
                400f, 300f, 500f, 400f, 0.8f, 0f,
                0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
            )
            val output = arrayOf(arrayOf(det1, det2))
            val result = parseFaceOutput(output, 1f, 0, 0, 640, 480, 0.5f, 0.45f)
            assertThat(result).hasSize(2)
        }

        @Test
        @DisplayName("sorts results by descending confidence after NMS")
        fun sortsByDescendingConfidence() {
            val det1 = floatArrayOf(10f, 10f, 100f, 100f, 0.7f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
            val det2 = floatArrayOf(200f, 200f, 300f, 300f, 0.95f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
            val det3 = floatArrayOf(400f, 10f, 500f, 100f, 0.8f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
            val output = arrayOf(arrayOf(det1, det2, det3))
            val result = parseFaceOutput(output, 1f, 0, 0, 640, 480, 0.5f, 0.45f)
            assertThat(result).hasSize(3)
            assertThat(result[0].confidence).isCloseTo(0.95f, within(0.01f))
        }

        @Test
        @DisplayName("keeps partially overlapping detections below IoU threshold")
        fun keepsPartiallyOverlappingBelowThreshold() {
            val det1 = floatArrayOf(
                100f, 100f, 200f, 200f, 0.9f, 0f,
                0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
            )
            val det2 = floatArrayOf(
                150f, 100f, 250f, 200f, 0.8f, 0f,
                0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
            )
            val output = arrayOf(arrayOf(det1, det2))
            val result = parseFaceOutput(output, 1f, 0, 0, 640, 480, 0.5f, 0.45f)
            // IoU ≈ 0.33 < 0.45 → both kept
            assertThat(result).hasSize(2)
        }
    }

    @Nested
    @DisplayName("FaceDetection data class")
    inner class FaceDetectionDataClass {

        @Test
        @DisplayName("computes center, width, and height from bounding box")
        fun computesDerivedProperties() {
            val det = YoloFaceDetectionService.FaceDetection(
                x1 = 100f, y1 = 50f, x2 = 300f, y2 = 250f, confidence = 0.9f,
            )
            assertThat(det.centerX).isCloseTo(200f, within(0.1f))
            assertThat(det.centerY).isCloseTo(150f, within(0.1f))
            assertThat(det.width).isCloseTo(200f, within(0.1f))
            assertThat(det.height).isCloseTo(200f, within(0.1f))
        }

        @Test
        @DisplayName("FaceKeypoint holds x, y, and visibility")
        fun faceKeypointProperties() {
            val kp = YoloFaceDetectionService.FaceKeypoint(x = 123.4f, y = 456.7f, visibility = 0.88f)
            assertThat(kp.x).isCloseTo(123.4f, within(0.01f))
            assertThat(kp.y).isCloseTo(456.7f, within(0.01f))
            assertThat(kp.visibility).isCloseTo(0.88f, within(0.01f))
        }
    }
}