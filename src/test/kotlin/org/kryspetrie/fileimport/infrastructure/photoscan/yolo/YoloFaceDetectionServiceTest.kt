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
 * - **NMS-enabled**: `[1, N, 6]` — x1, y1, x2, y2, confidence, class
 * - **Transposed**: `[1, 6, N]` — cx, cy, w, h, confidence, class (center-format bbox)
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
            // NMS format: [1, 1, 6] — single low-confidence detection
            val output = arrayOf(
                arrayOf(
                    floatArrayOf(100f, 80f, 200f, 180f, 0.3f, 0f),
                )
            )
            val result = parseFaceOutput(output, 1f, 0, 0, 640, 480, 0.5f, 0.45f)
            assertThat(result).isEmpty()
        }

        @Test
        @DisplayName("filters zero-area detections (model padding)")
        fun filtersZeroAreaDetections() {
            val output = arrayOf(
                arrayOf(
                    floatArrayOf(100f, 80f, 100f, 80f, 0.9f, 0f),
                )
            )
            val result = parseFaceOutput(output, 1f, 0, 0, 640, 480, 0.5f, 0.45f)
            assertThat(result).isEmpty()
        }
    }

    @Nested
    @DisplayName("parseFaceOutput — NMS format [1, N, 6]")
    inner class NmsFormat {

        @Test
        @DisplayName("parses single detection in NMS format")
        fun parsesSingleDetection() {
            val output = arrayOf(
                arrayOf(
                    floatArrayOf(270f, 180f, 370f, 300f, 0.9f, 0f),
                )
            )
            val result = parseFaceOutput(output, 1f, 0, 0, 640, 480, 0.5f, 0.45f)
            assertThat(result).hasSize(1)
            assertThat(result[0].x1).isCloseTo(270f, within(0.1f))
            assertThat(result[0].y1).isCloseTo(180f, within(0.1f))
            assertThat(result[0].x2).isCloseTo(370f, within(0.1f))
            assertThat(result[0].y2).isCloseTo(300f, within(0.1f))
            assertThat(result[0].confidence).isCloseTo(0.9f, within(0.01f))
        }

        @Test
        @DisplayName("parses multiple detections in NMS format")
        fun parsesMultipleDetections() {
            val output = arrayOf(
                arrayOf(
                    floatArrayOf(100f, 80f, 200f, 180f, 0.85f, 0f),
                    floatArrayOf(300f, 200f, 400f, 300f, 0.75f, 0f),
                    floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f), // zero-area padding
                )
            )
            val result = parseFaceOutput(output, 1f, 0, 0, 640, 480, 0.5f, 0.45f)
            assertThat(result).hasSize(2)
        }

        @Test
        @DisplayName("applies ratio and padding in NMS format")
        fun appliesRatioAndPadding() {
            val output = arrayOf(
                arrayOf(
                    floatArrayOf(270f, 180f, 370f, 300f, 0.9f, 0f),
                )
            )
            val result = parseFaceOutput(output, 0.5f, 20, 10, 640, 480, 0.5f, 0.45f)
            assertThat(result).hasSize(1)
            assertThat(result[0].x1).isCloseTo(500f, within(1f))
            assertThat(result[0].y1).isCloseTo(340f, within(1f))
            assertThat(result[0].x2).isCloseTo(640f, within(1f)) // clamped from 700
            assertThat(result[0].y2).isCloseTo(480f, within(1f)) // clamped from 580
        }

        @Test
        @DisplayName("filters detections below confidence threshold in NMS format")
        fun filtersBelowThreshold() {
            val output = arrayOf(
                arrayOf(
                    floatArrayOf(100f, 80f, 200f, 180f, 0.3f, 0f),
                )
            )
            val result = parseFaceOutput(output, 1f, 0, 0, 640, 480, 0.5f, 0.45f)
            assertThat(result).isEmpty()
        }
    }

    @Nested
    @DisplayName("parseFaceOutput — transposed format [1, 6, N]")
    inner class TransposedFormat {

        @Test
        @DisplayName("parses single detection in transposed format")
        fun parsesSingleDetection() {
            val output = arrayOf(
                arrayOf(
                    floatArrayOf(320f),  // cx
                    floatArrayOf(240f),  // cy
                    floatArrayOf(100f),  // w
                    floatArrayOf(120f),  // h
                    floatArrayOf(0.9f),  // conf
                    floatArrayOf(0f),    // cls
                )
            )
            val result = parseFaceOutput(output, 1f, 0, 0, 640, 480, 0.5f, 0.45f)
            assertThat(result).hasSize(1)
            assertThat(result[0].x1).isCloseTo(270f, within(0.1f))
            assertThat(result[0].y1).isCloseTo(180f, within(0.1f))
            assertThat(result[0].x2).isCloseTo(370f, within(0.1f))
            assertThat(result[0].y2).isCloseTo(300f, within(0.1f))
            assertThat(result[0].confidence).isCloseTo(0.9f, within(0.01f))
        }

        @Test
        @DisplayName("applies ratio and padding in transposed format")
        fun appliesRatioAndPadding() {
            val output = arrayOf(
                arrayOf(
                    floatArrayOf(320f), floatArrayOf(240f), floatArrayOf(100f), floatArrayOf(120f),
                    floatArrayOf(0.9f), floatArrayOf(0f),
                )
            )
            val result = parseFaceOutput(output, 0.5f, 20, 10, 640, 480, 0.5f, 0.45f)
            assertThat(result).hasSize(1)
            assertThat(result[0].x1).isCloseTo(500f, within(1f))
            assertThat(result[0].y1).isCloseTo(340f, within(1f))
        }
    }

    @Nested
    @DisplayName("parseFaceOutput — NMS suppression")
    inner class NmsSuppression {

        @Test
        @DisplayName("suppresses overlapping detections above IoU threshold")
        fun suppressesOverlappingDetections() {
            val output = arrayOf(
                arrayOf(
                    floatArrayOf(100f, 80f, 200f, 180f, 0.95f, 0f),
                    floatArrayOf(105f, 85f, 205f, 185f, 0.8f, 0f),
                )
            )
            val result = parseFaceOutput(output, 1f, 0, 0, 640, 480, 0.5f, 0.45f)
            assertThat(result).hasSize(1)
            assertThat(result[0].confidence).isCloseTo(0.95f, within(0.01f))
        }

        @Test
        @DisplayName("keeps non-overlapping detections")
        fun keepsNonOverlappingDetections() {
            val output = arrayOf(
                arrayOf(
                    floatArrayOf(10f, 10f, 100f, 100f, 0.95f, 0f),
                    floatArrayOf(400f, 300f, 500f, 400f, 0.8f, 0f),
                )
            )
            val result = parseFaceOutput(output, 1f, 0, 0, 640, 480, 0.5f, 0.45f)
            assertThat(result).hasSize(2)
        }

        @Test
        @DisplayName("sorts results by descending confidence after NMS")
        fun sortsByDescendingConfidence() {
            val output = arrayOf(
                arrayOf(
                    floatArrayOf(10f, 10f, 100f, 100f, 0.7f, 0f),
                    floatArrayOf(200f, 200f, 300f, 300f, 0.95f, 0f),
                    floatArrayOf(400f, 10f, 500f, 100f, 0.8f, 0f),
                )
            )
            val result = parseFaceOutput(output, 1f, 0, 0, 640, 480, 0.5f, 0.45f)
            assertThat(result).hasSize(3)
            assertThat(result[0].confidence).isCloseTo(0.95f, within(0.01f))
        }

        @Test
        @DisplayName("keeps partially overlapping detections below IoU threshold")
        fun keepsPartiallyOverlappingBelowThreshold() {
            val output = arrayOf(
                arrayOf(
                    floatArrayOf(100f, 100f, 200f, 200f, 0.9f, 0f),
                    floatArrayOf(150f, 100f, 250f, 200f, 0.8f, 0f),
                )
            )
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
    }
}